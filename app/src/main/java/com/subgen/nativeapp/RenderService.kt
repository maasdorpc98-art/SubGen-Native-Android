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
        if(intent?.action==ACTION_CANCEL){if(busy.get()){cancelled.set(true);update("Stopping at safe checkpoint…")}else stopSelf();return START_NOT_STICKY}
        if(busy.compareAndSet(false,true)){
            cancelled.set(false)
            // Foreground promotion happens before doing any work, including downloading model assets.
            val note=notification("Preparing native render…")
            if(Build.VERSION.SDK_INT>=29)startForeground(ID,note,ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else startForeground(ID,note)
            executor.execute {
                try {acquireWake();when(intent?.action){ACTION_INSTALL->installOnly();ACTION_SUBLIMINAL->runSubliminal();ACTION_BENCHMARK->benchmark();ACTION_PREVIEW->previewVoice();else->runJob()}}
                catch(t:Throwable){update("Stopped: ${t.message?:t.javaClass.simpleName}")}
                finally {StateStore.active(this,false);releaseWake();busy.set(false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(startId)}
            }
        }
        return START_STICKY
    }
    private fun installOnly(){ModelInstaller(this).install(::update)}
    private fun previewVoice(){
        val p=StateStore.prefs(this);val voice=p.getString("voice","SERENA")?:"SERENA"
        val sid=KokoroNative.voices[voice]?:3
        val installer=ModelInstaller(this);installer.install(::update)
        update("Generating $voice preview in native engine…")
        KokoroNative(installer,p.getInt("threads",2)).use {tts->
            val (data,rate)=tts.synthesize("Take a slow, comfortable breath. You can relax now.",sid,p.getFloat("speed",.84f)){cancelled.get()}
            WavIO.writeMono(java.io.File(filesDir,"native-voice-preview.wav"),data,rate)
        }
        update("VOICE PREVIEW READY · tap PLAY PREVIEW")
    }
    private fun benchmark(){
        val p=StateStore.prefs(this);val installer=ModelInstaller(this);installer.install(::update)
        val sid=KokoroNative.voices[p.getString("voice","SERENA")]?:3
        val phrase="Take a slow breath. Let each breath feel calmer and easier as your shoulders relax."
        val speed=p.getFloat("speed",.84f)
        val results=mutableListOf<Pair<Int,Double>>()
        for(threads in listOf(2,4)){
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
        p.edit().putInt("threads",best.first).putString("benchmark",results.joinToString(" · "){"${it.first} threads RTF ${"%.2f".format(it.second)}"}).apply()
        update("BEST: ${best.first} threads · ${p.getString("benchmark","")}")
    }
    private fun runSubliminal(){
        val p=StateStore.prefs(this)
        fun path(key:String)=p.getString(key,"")?.let{if(it.isBlank())null else java.io.File(it)}
        val settings=NativeSubliminal.Settings(
            path("carrierStandard"),path("carrier8d"),path("affirmNormal"),path("affirmFrequency"),
            p.getInt("subMinutes",5),p.getInt("carrierPercent",50),p.getInt("primaryPercent",50)
        )
        StateStore.active(this,true)
        val result=java.io.File(filesDir,"subliminal-working.wav")
        NativeSubliminal.mix(settings,result,::update){cancelled.get()}
        if(cancelled.get()){update("Subliminal render cancelled");return}
        update("Saving Subliminal WAV to Android Downloads…")
        WavIO.saveToDownloads(this,result,p.getString("subName","Subliminal Session")?:"Subliminal Session")
        update("SAVED · Subliminal WAV · Downloads/SubGen")
    }
    private fun runJob(){
        val prefs=StateStore.prefs(this)
        val script=prefs.getString("script", "")?.trim().orEmpty()
        require(script.isNotEmpty()){ "Enter a Hypnosis script first" }
        StateStore.active(this,true)
        val voice=prefs.getString("voice","SERENA")?:"SERENA"
        val sid=KokoroNative.voices[voice]?:3
        val speed=prefs.getFloat("speed",0.84f).coerceIn(.65f,1.2f)
        val threads=prefs.getInt("threads",2).coerceIn(1,4)
        val name=prefs.getString("name","Hypnosis Session")?:"Hypnosis Session"
        val nodes=ScriptParser.parse(script)
        val speechCount=nodes.count{it is ScriptNode.Speech}
        require(speechCount>0){"Script contains no spoken text"}
        val checkpointFiles=MutableList<File?>(nodes.size){null}
        val installer=ModelInstaller(this)
        installer.install(::update)
        if(cancelled.get()) {update("Stopped. Installed model retained.");return}
        update("Loading native Kokoro inference (${threads} CPU threads)…")
        var finished=0;var generated=0;var cacheHits=0;var generatedSamples=0L;var inferenceMs=0L
        val started=SystemClock.elapsedRealtime()
        KokoroNative(installer,threads).use { tts->
            nodes.forEachIndexed{index,n->
                if(cancelled.get()){update("Stopped after $finished / $speechCount blocks. Saved blocks retained.");return}
                if(n !is ScriptNode.Speech)return@forEachIndexed
                val key=StateStore.hash("native-kokoro-v1_0-int8|${sid}|${speed}|${n.delivery}|${n.text}")
                val clip=File(StateStore.blocksDir(this),"$key.wav")
                val existing=runCatching{WavIO.loadMono(clip)}.getOrNull()
                if(existing!=null){cacheHits++}
                else {
                    val deliverySpeed=when(n.delivery){"SLOW"->.82f;"SOFT"->.94f;"WHISPER"->.90f;else->1f}
                    update("Generating ${finished+1} / $speechCount · $voice · saved $finished")
                    val t0=SystemClock.elapsedRealtime()
                    val (samples,sr)=tts.synthesize(n.text,sid,speed*deliverySpeed){cancelled.get()}
                    inferenceMs+=SystemClock.elapsedRealtime()-t0;generatedSamples+=samples.size
                    if(cancelled.get()){update("Stopped after $finished completed blocks");return}
                    WavIO.writeMono(clip,samples,sr)
                    generated++
                }
                checkpointFiles[index]=clip
                finished++
                update("Speech $finished / $speechCount · reused $cacheHits · ${"%.1f".format((SystemClock.elapsedRealtime()-started)/1000.0)}s elapsed")
            }
        }
        if(cancelled.get()){update("Paused with $finished / $speechCount blocks saved");return}
        check(finished==speechCount){"Not all speech blocks are ready"}
        update("Mixing WAV from disk, preserving exact pauses…")
        val path=StateStore.partialFile(this)
        val sec=WavIO.assemble(nodes,checkpointFiles,path,binOn=prefs.getBoolean("binaural",false))
        if(cancelled.get()){update("Cancelled before export; speech cache is safe");return}
        update("Saving completed WAV to Downloads/SubGen…")
        val uri=WavIO.saveToDownloads(this,path,name)
        val rtf=if(generatedSamples>0)inferenceMs/1000.0/(generatedSamples/24000.0) else 0.0
        update("SAVED · ${"%.1f".format(sec/60)} min · $generated new / $cacheHits reused · RTF ${"%.2f".format(rtf)} · Downloads/SubGen")
        StateStore.prefs(this).edit().putString("lastFileUri",uri.toString()).apply()
    }
    private fun update(message:String){StateStore.status(this,message);(getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(ID,notification(message))}
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
    companion object{const val ACTION_CANCEL="com.subgen.nativeapp.CANCEL";const val ACTION_INSTALL="com.subgen.nativeapp.INSTALL";const val ACTION_BENCHMARK="com.subgen.nativeapp.BENCHMARK";const val ACTION_PREVIEW="com.subgen.nativeapp.PREVIEW";const val ACTION_SUBLIMINAL="com.subgen.nativeapp.SUBLIMINAL";const val CHANNEL="subgen-hypnosis";const val ID=9021}
}
