package com.subgen.nativeapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Synthesis is owned by a native Android service, not an Activity, WebView, or Web Worker. */
class RenderService:Service() {
    private val executor=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private val cancelled=AtomicBoolean(false)
    private var wakeLock:PowerManager.WakeLock?=null
    private var renderSettings:android.content.SharedPreferences?=null
    override fun onBind(i:Intent?):IBinder?=null
    override fun onCreate(){super.onCreate();createChannel()}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // Android must not automatically replay a failed native inference after process death.
        val request=intent?.action
        if(request == null || request !in setOf(ACTION_RENDER,ACTION_INSTALL,ACTION_BENCHMARK,ACTION_PREVIEW,ACTION_SUBLIMINAL,ACTION_CANCEL)) {
            Diagnostics.record("IGNORED_SERVICE_RESTART", "No explicit valid user-initiated action")
            stopSelfResult(startId)
            return START_NOT_STICKY
        }
        if(request==ACTION_CANCEL){if(busy.get()){cancelled.set(true);update("Stopping at safe checkpoint…")}else stopSelf();return START_NOT_STICKY}
        if(StateStore.prefs(this).getBoolean("neuro:active",false)){
            StateStore.status(this,"Finish or cancel NeuroMod before starting another render");stopSelfResult(startId);return START_NOT_STICKY
        }
        if(busy.compareAndSet(false,true)){
            renderSettings=PreferenceSnapshot(StateStore.prefs(this))
            StateStore.active(this,true)
            cancelled.set(false)
            // Foreground promotion happens before doing any work, including downloading model assets.
            val note=notification("Preparing native render…")
            if(Build.VERSION.SDK_INT>=29)startForeground(ID,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID,note)
            executor.execute {
                val started=SystemClock.elapsedRealtime()
                var outcome="success"
                Diagnostics.startJob(intent?.action ?: "hypnosis-render")
                try {acquireWake();when(intent?.action){ACTION_INSTALL->installOnly();ACTION_SUBLIMINAL->runSubliminal();ACTION_BENCHMARK->benchmark();ACTION_PREVIEW->previewVoice();ACTION_RENDER->runJob();else->error("Unsupported action")}}
                catch(t:Throwable){outcome="failed";Diagnostics.failure("JOB_FAILED",t);update("Stopped: ${t.message?:t.javaClass.simpleName}")}
                finally {Diagnostics.finishJob(if(cancelled.get())"cancelled" else outcome,SystemClock.elapsedRealtime()-started);StateStore.active(this,false);releaseWake();busy.set(false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(startId)}
            }
        }
        return START_NOT_STICKY
    }
    private fun installOnly(){Diagnostics.stage("MODEL_INSTALL");ModelInstaller(this).install(::update)}
    private fun previewVoice(){
        Diagnostics.stage("VOICE_PREVIEW")
        val p=renderSettings?:PreferenceSnapshot(StateStore.prefs(this))
        val hq=p.getBoolean("quality:hypnosis",false)
        val voice=p.getString("voice","SERENA")?:"SERENA"
        val sid=KokoroNative.voices[voice]?:3
        val neutral=p.getBoolean("dir:previewNeutral",false)
        val config=if(neutral) VoiceDirection.Config() else VoiceDirectionStore.read(p)
        val speed=p.getFloat("speed",VoiceSpeed.DEFAULT)
        val raw=p.getString("dir:previewText","Take a slow, comfortable breath. You can relax now.")
            ?.take(1100)?.ifBlank{"Take a slow, comfortable breath. You can relax now."}
            ?:"Take a slow, comfortable breath. You can relax now."
        val source="[SECTION: "+(p.getString("dir:previewSection","INDUCTION")?:"INDUCTION")+"] "+raw
        val nodes=ScriptParser.parse(source,p.getFloat("hyp:breatheSec",5f).toDouble(),p.getFloat("hyp:paragraphSec",3f).toDouble(),maxSpeechChars=160)
        val errors=nodes.filterIsInstance<ScriptNode.Unknown>()
        require(errors.isEmpty()){"Invalid preview code: "+errors.joinToString{it.command}}
        val installer=ModelInstaller(this);installer.install(::update)
        update("Generating ${if(neutral)"neutral" else "directed"} voice preview…")
        val clips=MutableList<File?>(nodes.size){null}
        val prepared=nodes.mapIndexed { i,node ->
            if(node is ScriptNode.Speech){
                val selected=if(neutral)node.copy(delivery="NORMAL",tempo=null,gainDb=null,
                    scriptProfile=null,acousticPhase=null,explicitPhaseMode=true) else node
                val settings=VoiceDirection.resolve(selected,speed,config)
                require(settings.speed in .6f..1.4f){"Voice preview tempo out of range"}
                selected.copy(gainDb=settings.gainDb)
            } else node
        }
        KokoroNative(installer,InferenceTuner.threads(this)).use {tts ->
            prepared.forEachIndexed { index,node ->
                if(cancelled.get())return
                if(node is ScriptNode.Speech){
                    val r=VoiceDirection.resolve(node,speed,config)
                    val (data,rate)=tts.synthesize(node.text,sid,r.speed){cancelled.get()}
                    val f=File(cacheDir,"subgen-preview-block-$index.wav")
                    if(hq)HighQualityPcm.writeVoice(f,data,rate)else WavIO.writeMono(f,data,rate);clips[index]=f
                }
            }
        }
        val name=if(neutral)"native-preview-neutral.wav" else "native-preview-directed.wav"
        try{
            if(hq){
                val raw=File(cacheDir,"hq-voice-preview-float.wav")
                try{HighQualityNarration.assemble(prepared,clips,raw,stop={cancelled.get()})
                    HighQualityPcm.export24(raw,File(filesDir,name),24000,2,stop={cancelled.get()})
                }finally{raw.delete()}
            }else WavIO.assemble(prepared,clips,File(filesDir,name))
        }
        finally{clips.filterNotNull().forEach{it.delete()}}
        update("PREVIEW READY · $name")
    }
    private fun benchmark(){
        Diagnostics.stage("VOICE_BENCHMARK")
        val p=StateStore.prefs(this);val installer=ModelInstaller(this);installer.install(::update)
        val sid=KokoroNative.voices[p.getString("voice","SERENA")]?:3
        val phrase="Take a slow breath. Let each breath feel calmer and easier as your shoulders relax."
        val speed=p.getFloat("speed",VoiceSpeed.DEFAULT)
        val results=mutableListOf<Pair<Int,Double>>()
        for(threads in listOf(1,2,4).filter{it<=Runtime.getRuntime().availableProcessors()}){
            if(cancelled.get())return
            update("Native performance test · $threads inference threads…")
            KokoroNative(installer,threads).use {tts->
                tts.synthesize(phrase,sid,speed){cancelled.get()} // warm-up
                if(cancelled.get())return
                val start=SystemClock.elapsedRealtime()
                val (samples,sr)=tts.synthesize(phrase,sid,speed){cancelled.get()}
                val elapsed=(SystemClock.elapsedRealtime()-start)/1000.0
                val rtf=elapsed/(samples.size.toDouble()/sr)
                results+=threads to rtf
            }
        }
        val best=results.minByOrNull{it.second}!!
        InferenceTuner.rememberBenchmark(this,best.first,best.second)
        Diagnostics.record("INFERENCE_BENCHMARK_WINNER","",org.json.JSONObject()
            .put("threads",best.first).put("rtf",best.second))
        p.edit().putInt("threads",best.first).putString("benchmark",results.joinToString(" · "){"${it.first} threads RTF ${"%.2f".format(it.second)}"}).apply()
        update("BEST: ${best.first} threads · ${p.getString("benchmark","")}")
    }
    private fun runSubliminal(){
        Diagnostics.stage("SUBLIMINAL_PREPARE")
        val p=renderSettings?:PreferenceSnapshot(StateStore.prefs(this))
        val hq=p.getBoolean("quality:studio",false)
        fun prepare(file:File)=if(hq&&HighQualityPcm.supported(file))file else NativeInputDecoder.prepare(file,::update){cancelled.get()}
        fun path(key:String)=p.getString(key,"")?.let{if(it.isBlank())null else File(it)}
        fun level(key:String,default:Float)=p.getFloat("slider:$key",default).toDouble()
        fun enabled(key:String)=p.getBoolean("check:$key",false)
        val preview=p.getBoolean("subPreview",false)
        val duration=if(preview)30 else (p.getString("select:ctrl-duration","10")?.toIntOrNull()?:10).coerceIn(1,30)*60
        val rate=p.getString("select:ctrl-samplerate","44100")?.toIntOrNull()?:44100
        val hpfArray=intArrayOf(0,20,40,80,100,150,200,300,400,600,800)
        val lpfArray=intArrayOf(0,18000,16000,14000,12000,10000,8000,6000,4000,3000,2000)
        val hpf=hpfArray[level("ctrl-carrier-hpf",0f).toInt().coerceIn(0,10)]
        val lpf=lpfArray[level("ctrl-carrier-lpf",0f).toInt().coerceIn(0,10)]
        val settings=NativeSubliminal.Settings(
            carrierA=path("carrierStandard")?.let{prepare(it)},
            carrierB=path("carrierStandard")?.let{prepare(it)},
            affirmationA=path("affirmNormal")?.let{prepare(it)},
            affirmationB=path("affirmFrequency")?.let{prepare(it)},
            durationSeconds=duration,rate=rate,
            carrierDb=level("ctrl-vol-carrier",0f),carrier8dDb=level("ctrl-vol-carrier-8d",-60f),
            primaryDb=level("ctrl-vol1",0f),secondaryDb=level("ctrl-vol-noise",0f),
            layerDb=level("ctrl-na-layer-volume-1",0f),finalDb=level("ctrl-master-output-db",0f),
            primarySpeed=level("ctrl-na-layer-speed-1",1f),
            secondarySpeed=if(enabled("ctrl-secondary-mod-on"))level("ctrl-secondary-speed",1f) else level("ctrl-na-layer-speed-1",1f),
            stereoWidth=level("ctrl-carrier-width",100f)/100.0,
            fadeSeconds=level("ctrl-carrier-fade",0f),
            eightDPercent=level("ctrl-carrier-8d-speed",25f),
            binauralOn=enabled("ctrl-binaural-on"),binauralDb=level("ctrl-binaural-volume",0f),
            beatHz=level("ctrl-binaural-beat",10f),centerHz=level("ctrl-binaural-center",400f),
            rhythmicOn=enabled("ctrl-rhythmic-on"),rhythmicDb=level("ctrl-rhythmic-volume",0f),
            rhythmicPulse=level("ctrl-rhythmic-pulse",6f),rhythmicTone=level("ctrl-rhythmic-tone",400f),
            rhythmicDepth=level("ctrl-rhythmic-depth",100f)/100.0,
            rhythmicSine=p.getString("select:ctrl-rhythmic-shape","gated")=="sine",
            clarity=enabled("ctrl-affirmation-clarity-on"),
            peakCeiling=(p.getString("select:ctrl-normalize","0.912")?.toDoubleOrNull()?:.912).coerceIn(.1,1.0),
            highPassHz=hpf.toDouble(),lowPassHz=lpf.toDouble()
        )
        Diagnostics.expectedOutput(duration.toLong()*rate*4L+44)
        Diagnostics.stage("SUBLIMINAL_MIX",org.json.JSONObject().put("durationSeconds",duration).put("sampleRate",rate))
        StateStore.active(this,true)
        val result=File(filesDir,if(preview)"subliminal-preview.wav" else "subliminal-working.wav")
        if(hq){
            val measured=HighQualityMixer.mix(settings,result,File(cacheDir,"hq-studio-cache"),::update){cancelled.get()}
            Diagnostics.record("HQ_MIX_COMPLETE","PCM24; uniform peak protection",org.json.JSONObject().put("appliedGainDb",measured.appliedGainDb).put("sampleRate",measured.rate))
        }else NativeSubliminal.mix(settings,result,::update){cancelled.get()}
        Diagnostics.stage("SUBLIMINAL_WAV_COMPLETE")
        if(cancelled.get()){update("Subliminal render cancelled");return}
        if(preview){
            update("PREVIEW READY · Playing first 30 seconds")
            val player=android.media.MediaPlayer()
            try {player.setDataSource(result.absolutePath);player.prepare();player.start()
                while(player.isPlaying&&!cancelled.get()){Thread.sleep(250)}
            } finally {player.stop();player.release()}
            update("PREVIEW COMPLETE · original renderer")
        }else{
            update("Saving Subliminal WAV to Android Downloads…")
            val uri=WavIO.saveToDownloads(this,result,p.getString("subName","Subliminal Session")?:"Subliminal Session")
            StateStore.prefs(this).edit().putString("subLastFileUri",uri.toString()).apply()
            update("SAVED · Subliminal WAV · Downloads/SubGen")
        }
    }
    private fun runJob(){
        Diagnostics.stage("HYPNOSIS_PREPARE")
        val prefs=renderSettings?:PreferenceSnapshot(StateStore.prefs(this))
        val livePrefs=StateStore.prefs(this)
        val hq=prefs.getBoolean("quality:hypnosis",false)
        fun validClip(file:File)=runCatching{if(hq)HighQualityNarration.frames(file)>0 else WavIO.loadMono(file).let{it.rate==24000&&it.frames>0}}.getOrDefault(false)
        val submittedScript=prefs.getString("script", "").orEmpty()
        val script=submittedScript.trim()
        require(script.isNotEmpty()){ "Enter a Hypnosis script first" }
        StateStore.active(this,true)
        val voice=prefs.getString("voice","SERENA")?:"SERENA"
        val sid=KokoroNative.voices[voice]?:3
        val speed=prefs.getFloat("speed",VoiceSpeed.DEFAULT).coerceIn(.65f,1.2f)
        val pacingWpm=if(prefs.getBoolean("hyp:wpmEnabled",false))prefs.getInt("hyp:wpm",WordPacing.DEFAULT) else null
        if(pacingWpm!=null)require(pacingWpm in WordPacing.MIN..WordPacing.MAX){"Invalid WPM pacing target"}
        // Native throughput is independent from speech speed and output WAV quality.
        var threads=InferenceTuner.threads(this)
        Diagnostics.record("SPEED_ENGINE","FP32 + adaptive inference workers + cache-first pipeline")
        val name=prefs.getString("name","Hypnosis Session")?:"Hypnosis Session"
        val breathe=prefs.getFloat("hyp:breatheSec",5f).toDouble()
        val paragraph=prefs.getFloat("hyp:paragraphSec",3f).toDouble()
        val rawNodes=ScriptParser.parse(script,breathe,paragraph,maxSpeechChars=160)
        val unknown=rawNodes.filterIsInstance<ScriptNode.Unknown>()
        require(unknown.isEmpty()){
            "Unsupported audio or HYPER planner code: "+unknown.take(3).joinToString {it.command}
        }
        val direction=VoiceDirectionStore.read(prefs)
        val hyperAcoustics=HyperStore.acoustics(voice,HyperAnalysis.Settings(speed,breathe,paragraph,direction))
        val nodes=rawNodes.map { node->
            if(node is ScriptNode.Speech) {
                val r=VoiceDirection.resolve(node,speed,direction)
                require(r.speed in 0.6f..1.4f){"Effective voice tempo outside Kokoro 0.60–1.40; reduce tempo or global speed"}
                node.copy(gainDb=r.gainDb)
            } else node
        }
        val speechCount=nodes.count{it is ScriptNode.Speech}
        require(speechCount>0){"Script contains no spoken text"}
        val checkpointFiles=MutableList<File?>(nodes.size){null}
        val cached=BooleanArray(nodes.size)
        var preflightHits=0
        val cacheScanStart=SystemClock.elapsedRealtime()
        nodes.forEachIndexed { index, n ->
            if(n !is ScriptNode.Speech) return@forEachIndexed
            val resolved=VoiceDirection.resolve(rawNodes[index] as ScriptNode.Speech,speed,direction)
            val cacheSpeed=if(direction.enabled||n.tempo!=null||n.scriptProfile!=null) resolved.speed else speed
            val key=if(hq)StateStore.hash("hq-float-voice-v1|${sid}|${resolved.speed}|${n.text}") else StateStore.hash("native-kokoro-v1_0-fp32-validated|${sid}|${cacheSpeed}|${n.delivery}|${n.text}")
            val clip=File(StateStore.blocksDir(this),"$key.wav")
            checkpointFiles[index]=clip
            if(validClip(clip)) {
                cached[index]=true
                preflightHits++
            } else if(clip.exists()) clip.delete()
        }
        val missing=speechCount-preflightHits
        Diagnostics.record("SPEECH_CACHE_PREFLIGHT","",org.json.JSONObject()
            .put("speechBlocks",speechCount).put("reusable",preflightHits)
            .put("missing",missing).put("scanMs",SystemClock.elapsedRealtime()-cacheScanStart))
        val installer=ModelInstaller(this)
        if(missing>0) {
            Diagnostics.stage("KOKORO_MODEL_INSTALL")
            val installStarted=SystemClock.elapsedRealtime()
            installer.install(::update)
            Diagnostics.record("KOKORO_ASSET_CHECK_COMPLETED","",org.json.JSONObject()
                .put("elapsedMs",SystemClock.elapsedRealtime()-installStarted))
            if(cancelled.get()) {update("Stopped. Installed model retained.");return}
            threads=InferenceTuner.autoTuneIfNeeded(this,installer,sid,speed,missing,{cancelled.get()},::update)
            if(cancelled.get()){update("Calibration cancelled; saved blocks retained");return}
            update("Loading Kokoro FP32 with $threads tuned CPU workers…")
        } else update("Cache hit · reusing all $speechCount speech blocks without loading Kokoro")
        var finished=0;var generated=0;var cacheHits=0;var generatedSamples=0L;var inferenceMs=0L
        val quartileMs=LongArray(4)
        val quartileSamples=LongArray(4)
        val started=SystemClock.elapsedRealtime()
        val initStarted=SystemClock.elapsedRealtime()
        Diagnostics.stage(if(missing>0)"KOKORO_SYNTHESIS" else "CACHE_ONLY_ASSEMBLY",
            org.json.JSONObject().put("speechBlocks",speechCount).put("threads",threads)
                .put("cached",preflightHits))
        fun renderSpeech(tts:KokoroNative?) {
            nodes.forEachIndexed{index,n->
                if(cancelled.get()){update("Stopped after $finished / $speechCount blocks. Saved blocks retained.");return}
                if(n !is ScriptNode.Speech)return@forEachIndexed
                val clip=checkpointFiles[index] ?: error("Missing checkpoint reference")
                if(cached[index] || validClip(clip)){cacheHits++}
                else {
                    val activeTts=tts?:error("Synthesis engine missing for uncached speech block")
                    if(finished==0 || finished%6==0)update("Generating ${finished+1} / $speechCount · $voice · saved $finished")
                    val t0=SystemClock.elapsedRealtime()
                    Diagnostics.beforeNative("KOKORO_BLOCK_START",org.json.JSONObject()
                        .put("block",finished+1).put("speechBlocks",speechCount)
                        .put("chars",n.text.length).put("delivery",n.delivery),
                        durable=(finished==0 || finished%16==0))
                    val effective=VoiceDirection.resolve(rawNodes[index] as ScriptNode.Speech,speed,direction)
                    val (samples,sr)=activeTts.synthesize(n.text,sid,effective.speed){cancelled.get()}
                    Diagnostics.record("KOKORO_BLOCK_SUCCESS", "",
                        org.json.JSONObject().put("block",finished+1).put("samples",samples.size)
                            .put("renderMs",SystemClock.elapsedRealtime()-t0))
                    val elapsedBlockMs=SystemClock.elapsedRealtime()-t0
                    inferenceMs+=elapsedBlockMs;generatedSamples+=samples.size
                    val bucket=(finished*4/speechCount).coerceIn(0,3)
                    quartileMs[bucket]+=elapsedBlockMs
                    quartileSamples[bucket]+=samples.size
                    if(cancelled.get()){update("Stopped after $finished completed blocks");return}
                    if(hq)HighQualityPcm.writeVoice(clip,samples,sr)else WavIO.writeMono(clip,samples,sr)
                    generated++
                }
                checkpointFiles[index]=clip
                finished++
                if(finished%6==0 || finished==speechCount){
                    val averageMs=if(generated>0)inferenceMs.toDouble()/generated else 0.0
                    val etaMin=averageMs*(speechCount-finished).coerceAtLeast(0)/60000.0
                    val speechSec=generatedSamples/24000.0
                    val rtf=if(speechSec>0)inferenceMs/1000.0/speechSec else 0.0
                    update("Speech $finished/$speechCount · cached $cacheHits · approx ${"%.0f".format(etaMin)}m left · RTF ${"%.2f".format(rtf)}")
                    if(finished%18==0 || finished==speechCount)Diagnostics.record("SPEECH_SPEED_CHECKPOINT","",org.json.JSONObject()
                        .put("completed",finished).put("total",speechCount).put("reused",cacheHits)
                        .put("workers",threads).put("rtf",rtf).put("etaMinutes",etaMin))
                }
            }
        }
        if(missing>0) KokoroNative(installer,threads).use {tts->
            Diagnostics.record("KOKORO_ENGINE_LOAD_TIME","",org.json.JSONObject()
                .put("elapsedMs",SystemClock.elapsedRealtime()-initStarted).put("workers",threads))
            renderSpeech(tts)
        } else renderSpeech(null)
        if(cancelled.get()){update("Paused with $finished / $speechCount blocks saved");return}
        check(finished==speechCount){"Not all speech blocks are ready"}
        Diagnostics.record("SPEECH_GENERATION_COMPLETED","",org.json.JSONObject()
            .put("generated",generated).put("reused",cacheHits).put("inferenceMs",inferenceMs)
            .put("workers",threads).put("newSpeechSeconds",generatedSamples/24000.0)
            .put("quartileRtf",org.json.JSONArray().apply {
                for(q in 0..3)put(if(quartileSamples[q]>0)
                    quartileMs[q]/1000.0/(quartileSamples[q]/24000.0) else 0.0)
            })
            .put("rtf",if(generatedSamples>0)inferenceMs/1000.0/(generatedSamples/24000.0) else 0.0))
        update("Mixing WAV from disk, preserving exact pauses…")
        val path=StateStore.partialFile(this)
        Diagnostics.stage("HYPNOSIS_WAV_ASSEMBLY")
        val assembleStart=SystemClock.elapsedRealtime()
        val outChannels=prefs.getInt("hyp:channels",2).coerceIn(1,2)
        val requestedRate=prefs.getInt("hyp:sampleRate",24000)
        require(requestedRate in OutputFormat.rates){"Unsupported output sample rate"}
        // Binaural separation requires stereo. Keep the stored preference unchanged.
        val binaural=(prefs.getBoolean("binaural",false) && outChannels==2)
        val (assemblyNodes,assemblyFiles)=WordPacing.augment(nodes,checkpointFiles,24000,pacingWpm){file->
            if(hq)HighQualityNarration.frames(file) else WavIO.loadMono(file).frames
        }
        val sec=if(hq)HighQualityNarration.assemble(assemblyNodes,assemblyFiles,path,binaural){cancelled.get()} else WavIO.assemble(assemblyNodes,assemblyFiles,path,binOn=binaural)
        Diagnostics.record("WAV_ASSEMBLY_COMPLETED","",org.json.JSONObject().put("wpmPacingTarget",pacingWpm?:"OFF")
            .put("elapsedMs",SystemClock.elapsedRealtime()-assembleStart)
            .put("durationSeconds",sec).put("bytes",path.length()))
        if(cancelled.get()){update("Cancelled before export; speech cache is safe");return}
        update("Applying output volume, channel layout and sample rate…")
        Diagnostics.stage("HYPNOSIS_OUTPUT_FORMAT")
        val finalPath=File(filesDir,"hypnosis-final.wav")
        val formatStart=SystemClock.elapsedRealtime()
        val outputDb=prefs.getFloat("hyp:outputDb",0f).toDouble().coerceIn(-30.0,12.0)
        val ceilingDb=prefs.getFloat("hyp:peakDb",-1f).toDouble().coerceIn(-12.0,0.0)
        val backgroundLayers=HypnosisBackgroundMixer.selected(prefs)
        val layeredPath=File(cacheDir,"hypnosis-layered.wav")
        val preparedLoops=mutableListOf<File>()
        val formatted=try {
            val mixingInput=if(backgroundLayers.isEmpty())path else {
                Diagnostics.stage("HYPNOSIS_BACKGROUND_MIX")
                update("Mixing optional looping background audio…")
                val decoded=backgroundLayers.mapIndexed {index,layer->
                    val prepared=if(HighQualityPcm.supported(layer.file))layer.file
                        else NativeInputDecoder.prepare(layer.file,::update){cancelled.get()}
                    val sourceInfo=HighQualityPcm.inspect(prepared)
                    val converted=if(sourceInfo.rate==24000 && layer.speed==1.0)prepared else {
                        val tmp=File(cacheDir,"hyp-bg-resampled-$index.wav")
                        preparedLoops.add(tmp)
                        HighQualityPcm.resample(prepared,tmp,24000,sourceInfo.channels,
                            speed=layer.speed,stop={cancelled.get()})
                        tmp
                    }
                    layer.copy(file=converted)
                }
                HypnosisBackgroundMixer.mix(path,layeredPath,decoded,hq){cancelled.get()}
                Diagnostics.record("HYPNOSIS_BACKGROUND_MIXED",
                    extra=org.json.JSONObject().put("layers",decoded.size).put("seconds",sec))
                layeredPath
            }
            if(hq){
                val q=HighQualityPcm.export24(mixingInput,finalPath,requestedRate,outChannels,volumeDb=outputDb,ceilingDb=ceilingDb){cancelled.get()}
                OutputFormat.Result(q.seconds,q.bytes,q.peak,q.appliedGainDb,q.rate,q.channels)
            }else OutputFormat.convert(mixingInput,finalPath,requestedRate,outChannels,volumeDb=outputDb,ceilingDb=ceilingDb)
        }finally {layeredPath.delete();preparedLoops.forEach{it.delete()}}
        val bytesPerSample=if(hq)3 else 2
        Diagnostics.record("WAV_OUTPUT_FORMAT_COMPLETED","",org.json.JSONObject()
            .put("elapsedMs",SystemClock.elapsedRealtime()-formatStart)
            .put("sampleRate",formatted.rate).put("channels",formatted.channels)
            .put("wavBytes",formatted.bytes).put("sourcePeak",formatted.sourcePeak)
            .put("appliedGainDb",formatted.appliedGainDb))
        if(cancelled.get()){update("Cancelled before export; speech cache is safe");return}
        update("Saving completed WAV to Downloads/SubGen…")
        val exportStart=SystemClock.elapsedRealtime()
        val uri=WavIO.saveToDownloads(this,finalPath,name)
        Diagnostics.record("WAV_EXPORT_COMPLETED","",org.json.JSONObject()
            .put("elapsedMs",SystemClock.elapsedRealtime()-exportStart).put("bytes",finalPath.length()))
        val rtf=if(generatedSamples>0)inferenceMs/1000.0/(generatedSamples/24000.0) else 0.0
        update("SAVED · ${"%.1f".format(sec/60)} min · $generated new / $cacheHits reused · RTF ${"%.2f".format(rtf)} · Downloads/SubGen")
        livePrefs.edit().putString("lastFileUri",uri.toString())
            .putLong("hyp:lastFinalFrames",(formatted.bytes-44L)/(formatted.channels*bytesPerSample))
            .putInt("hyp:lastRate",formatted.rate)
            .putString("hyp:lastScriptHash",StateStore.hash(prefs.getString("script","")?:""))
            .apply()
        val voiceFrames=nodes.indices.sumOf { i ->
            if(nodes[i] is ScriptNode.Speech) {if(hq)HighQualityNarration.frames(checkpointFiles[i]!!)else WavIO.loadMono(checkpointFiles[i]!!).frames} else 0L
        }
        livePrefs.edit().putLong("hyp:lastVoiceFrames",voiceFrames).apply()
        runCatching{HyperStore.recordRender(this,submittedScript,hyperAcoustics,
            HyperStore.output(formatted.rate,formatted.channels,outputDb,ceilingDb,binaural,if(hq)24 else 16),rawNodes,
            (formatted.bytes-44L)/(formatted.channels*bytesPerSample),formatted.rate)
        }.onFailure{Diagnostics.failure("HYPER_RENDER_EVIDENCE_FAILED",it)}
    }
    private fun update(message:String){Diagnostics.progress(message);StateStore.status(this,message);(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID,notification(message))}
    private fun notification(message:String):Notification {
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop=PendingIntent.getService(this,1,Intent(this,RenderService::class.java).setAction(ACTION_CANCEL),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,CHANNEL).setContentTitle("Sub Gen · Native Hypnosis")
            .setContentText(message).setStyle(Notification.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.stat_sys_download).setContentIntent(open)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel,"Stop",stop)
            .setOngoing(true).setOnlyAlertOnce(true).build()
    }
    private fun createChannel(){(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(
        NotificationChannel(CHANNEL,"Hypnosis rendering",NotificationManager.IMPORTANCE_LOW))}
    private fun acquireWake(){wakeLock=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SubGen:HypnosisRender").apply{setReferenceCounted(false);acquire(5*60*60*1000L)}}
    private fun releaseWake(){if(wakeLock?.isHeld==true)wakeLock?.release();wakeLock=null}
    override fun onDestroy(){cancelled.set(true);StateStore.active(this,false);releaseWake();executor.shutdown();super.onDestroy()}
    companion object{const val ACTION_RENDER="com.subgen.nativeapp.RENDER";const val ACTION_CANCEL="com.subgen.nativeapp.CANCEL";const val ACTION_INSTALL="com.subgen.nativeapp.INSTALL";const val ACTION_BENCHMARK="com.subgen.nativeapp.BENCHMARK";const val ACTION_PREVIEW="com.subgen.nativeapp.PREVIEW";const val ACTION_SUBLIMINAL="com.subgen.nativeapp.SUBLIMINAL";const val CHANNEL="subgen-hypnosis";const val ID=9021}
}
