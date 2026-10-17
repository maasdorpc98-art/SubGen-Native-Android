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
        if(busy.compareAndSet(false,true)){
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
        val p=StateStore.prefs(this);val voice=p.getString("voice","SERENA")?:"SERENA"
        val sid=KokoroNative.voices[voice]?:3
        val installer=ModelInstaller(this);installer.install(::update)
        update("Generating $voice preview in native engine…")
        KokoroNative(installer,InferenceTuner.threads(this)).use {tts->
            val (data,rate)=tts.synthesize("Take a slow, comfortable breath. You can relax now.",sid,p.getFloat("speed",.84f)){cancelled.get()}
            WavIO.writeMono(java.io.File(filesDir,"native-voice-preview.wav"),data,rate)
        }
        update("VOICE PREVIEW READY · tap PLAY PREVIEW")
    }
    private fun benchmark(){
        Diagnostics.stage("VOICE_BENCHMARK")
        val p=StateStore.prefs(this);val installer=ModelInstaller(this);installer.install(::update)
        val sid=KokoroNative.voices[p.getString("voice","SERENA")]?:3
        val phrase="Take a slow breath. Let each breath feel calmer and easier as your shoulders relax."
        val speed=p.getFloat("speed",.84f)
        val results=mutableListOf<Pair<Int,Double>>()
        for(threads in listOf(1,2,3)){
            if(cancelled.get())return
            update("Native performance test · $threads inference threads…")
            KokoroNative(installer,threads).use {tts->
                val start=SystemClock.elapsedRealtime()
                val (samples,sr)=tts.synthesize(phrase,sid,speed){cancelled.get()}
                val elapsed=(SystemClock.elapsedRealtime()-start)/1000.0
                val rtf=elapsed/(samples.size.toDouble()/sr)
                results+=threads to rtf
            }
        }
        val best=results.minByOrNull{it.second}!!
        p.edit().putInt("autotuned:threads",best.first).apply()
        Diagnostics.record("INFERENCE_BENCHMARK_WINNER","",org.json.JSONObject()
            .put("threads",best.first).put("rtf",best.second))
        p.edit().putInt("threads",best.first).putString("benchmark",results.joinToString(" · "){"${it.first} threads RTF ${"%.2f".format(it.second)}"}).apply()
        update("BEST: ${best.first} threads · ${p.getString("benchmark","")}")
    }
    private fun runSubliminal(){
        Diagnostics.stage("SUBLIMINAL_PREPARE")
        val p=StateStore.prefs(this)
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
            carrierA=path("carrierStandard")?.let{NativeInputDecoder.prepare(it,::update){cancelled.get()}},
            carrierB=path("carrierStandard")?.let{NativeInputDecoder.prepare(it,::update){cancelled.get()}},
            affirmationA=path("affirmNormal")?.let{NativeInputDecoder.prepare(it,::update){cancelled.get()}},
            affirmationB=path("affirmFrequency")?.let{NativeInputDecoder.prepare(it,::update){cancelled.get()}},
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
        NativeSubliminal.mix(settings,result,::update){cancelled.get()}
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
            p.edit().putString("subLastFileUri",uri.toString()).apply()
            update("SAVED · Subliminal WAV · Downloads/SubGen")
        }
    }
    private fun runJob(){
        Diagnostics.stage("HYPNOSIS_PREPARE")
        val prefs=StateStore.prefs(this)
        val script=prefs.getString("script", "")?.trim().orEmpty()
        require(script.isNotEmpty()){ "Enter a Hypnosis script first" }
        StateStore.active(this,true)
        val voice=prefs.getString("voice","SERENA")?:"SERENA"
        val sid=KokoroNative.voices[voice]?:3
        val speed=prefs.getFloat("speed",0.84f).coerceIn(.65f,1.2f)
        // Stability mode: limit inference parallelism. User's selected setting remains stored for later.
        val threads=InferenceTuner.threads(this)
        Diagnostics.record("SPEED_ENGINE","FP32 + adaptive inference workers + cache-first pipeline")
        val name=prefs.getString("name","Hypnosis Session")?:"Hypnosis Session"
        val nodes=ScriptParser.parse(script,maxSpeechChars=160)
        val speechCount=nodes.count{it is ScriptNode.Speech}
        require(speechCount>0){"Script contains no spoken text"}
        val checkpointFiles=MutableList<File?>(nodes.size){null}
        val cached=BooleanArray(nodes.size)
        var preflightHits=0
        val cacheScanStart=SystemClock.elapsedRealtime()
        nodes.forEachIndexed { index, n ->
            if(n !is ScriptNode.Speech) return@forEachIndexed
            val key=StateStore.hash("native-kokoro-v1_0-fp32-validated|${sid}|${speed}|${n.delivery}|${n.text}")
            val clip=File(StateStore.blocksDir(this),"$key.wav")
            val header=runCatching{WavIO.loadMono(clip)}.getOrNull()
            checkpointFiles[index]=clip
            if(header!=null && header.rate==24000 && header.frames>0) {
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
            installer.install(::update)
            if(cancelled.get()) {update("Stopped. Installed model retained.");return}
            update("Loading Kokoro FP32 with $threads adaptive CPU workers…")
        } else update("Cache hit · reusing all $speechCount speech blocks without loading Kokoro")
        var finished=0;var generated=0;var cacheHits=0;var generatedSamples=0L;var inferenceMs=0L
        val started=SystemClock.elapsedRealtime()
        Diagnostics.stage(if(missing>0)"KOKORO_SYNTHESIS" else "CACHE_ONLY_ASSEMBLY",
            org.json.JSONObject().put("speechBlocks",speechCount).put("threads",threads)
                .put("cached",preflightHits))
        fun renderSpeech(tts:KokoroNative?) {
            nodes.forEachIndexed{index,n->
                if(cancelled.get()){update("Stopped after $finished / $speechCount blocks. Saved blocks retained.");return}
                if(n !is ScriptNode.Speech)return@forEachIndexed
                val clip=checkpointFiles[index] ?: error("Missing checkpoint reference")
                if(cached[index]){cacheHits++}
                else {
                    val activeTts=tts?:error("Synthesis engine missing for uncached speech block")
                    val deliverySpeed=when(n.delivery){"SLOW"->.82f;"SOFT"->.94f;"WHISPER"->.90f;else->1f}
                    if(finished==0 || finished%6==0)update("Generating ${finished+1} / $speechCount · $voice · saved $finished")
                    val t0=SystemClock.elapsedRealtime()
                    Diagnostics.beforeNative("KOKORO_BLOCK_START",org.json.JSONObject()
                        .put("block",finished+1).put("speechBlocks",speechCount)
                        .put("chars",n.text.length).put("delivery",n.delivery),
                        durable=(finished==0 || finished%16==0))
                    val (samples,sr)=activeTts.synthesize(n.text,sid,speed*deliverySpeed){cancelled.get()}
                    Diagnostics.record("KOKORO_BLOCK_SUCCESS", "",
                        org.json.JSONObject().put("block",finished+1).put("samples",samples.size)
                            .put("renderMs",SystemClock.elapsedRealtime()-t0))
                    inferenceMs+=SystemClock.elapsedRealtime()-t0;generatedSamples+=samples.size
                    if(cancelled.get()){update("Stopped after $finished completed blocks");return}
                    WavIO.writeMono(clip,samples,sr)
                    generated++
                }
                checkpointFiles[index]=clip
                finished++
                if(finished%6==0 || finished==speechCount)update("Speech $finished / $speechCount · reused $cacheHits · ${"%.1f".format((SystemClock.elapsedRealtime()-started)/1000.0)}s elapsed")
            }
        }
        if(missing>0) KokoroNative(installer,threads).use {renderSpeech(it)}
        else renderSpeech(null)
        if(cancelled.get()){update("Paused with $finished / $speechCount blocks saved");return}
        check(finished==speechCount){"Not all speech blocks are ready"}
        Diagnostics.record("SPEECH_GENERATION_COMPLETED","",org.json.JSONObject().put("generated",generated).put("reused",cacheHits).put("inferenceMs",inferenceMs))
        update("Mixing WAV from disk, preserving exact pauses…")
        val path=StateStore.partialFile(this)
        Diagnostics.stage("HYPNOSIS_WAV_ASSEMBLY")
        val assembleStart=SystemClock.elapsedRealtime()
        val sec=WavIO.assemble(nodes,checkpointFiles,path,binOn=prefs.getBoolean("binaural",false))
        Diagnostics.record("WAV_ASSEMBLY_COMPLETED","",org.json.JSONObject()
            .put("elapsedMs",SystemClock.elapsedRealtime()-assembleStart)
            .put("durationSeconds",sec).put("bytes",path.length()))
        if(cancelled.get()){update("Cancelled before export; speech cache is safe");return}
        update("Saving completed WAV to Downloads/SubGen…")
        val exportStart=SystemClock.elapsedRealtime()
        val uri=WavIO.saveToDownloads(this,path,name)
        Diagnostics.record("WAV_EXPORT_COMPLETED","",org.json.JSONObject().put("elapsedMs",SystemClock.elapsedRealtime()-exportStart).put("bytes",path.length()))
        val rtf=if(generatedSamples>0)inferenceMs/1000.0/(generatedSamples/24000.0) else 0.0
        update("SAVED · ${"%.1f".format(sec/60)} min · $generated new / $cacheHits reused · RTF ${"%.2f".format(rtf)} · Downloads/SubGen")
        StateStore.prefs(this).edit().putString("lastFileUri",uri.toString()).apply()
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
    override fun onDestroy(){cancelled.set(true);releaseWake();executor.shutdown();super.onDestroy()}
    companion object{const val ACTION_RENDER="com.subgen.nativeapp.RENDER";const val ACTION_CANCEL="com.subgen.nativeapp.CANCEL";const val ACTION_INSTALL="com.subgen.nativeapp.INSTALL";const val ACTION_BENCHMARK="com.subgen.nativeapp.BENCHMARK";const val ACTION_PREVIEW="com.subgen.nativeapp.PREVIEW";const val ACTION_SUBLIMINAL="com.subgen.nativeapp.SUBLIMINAL";const val CHANNEL="subgen-hypnosis";const val ID=9021}
}
