package com.subgen.nativeapp

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Reuses the unchanged A/B algorithms. Only source preparation and listening export differ. */
class HighQualityNeuroService:Service(){
    private val executor=Executors.newSingleThreadExecutor();private val busy=AtomicBoolean(false);private val cancel=AtomicBoolean(false)
    private var wake:PowerManager.WakeLock?=null
    override fun onBind(i:Intent?):IBinder?=null
    override fun onCreate(){super.onCreate();(getSystemService(NOTIFICATION_SERVICE)as NotificationManager)
        .createNotificationChannel(NotificationChannel(CHANNEL,"NeuroMod HQ",NotificationManager.IMPORTANCE_LOW))}
    private fun status(s:String){StateStore.prefs(this).edit().putString("neuro:status",s).apply()
        (getSystemService(NOTIFICATION_SERVICE)as NotificationManager).notify(ID,note(s))}
    private fun note(s:String):Notification=Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("NeuroModulation · High Quality").setContentText(s).setOngoing(true)
        .setContentIntent(PendingIntent.getActivity(this,6,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)).build()
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        if(intent?.action==NeuroModulationService.ACTION_CANCEL){cancel.set(true);return START_NOT_STICKY}
        if(intent?.action!=NeuroModulationService.ACTION_GENERATE){stopSelfResult(startId);return START_NOT_STICKY}
        if(!busy.compareAndSet(false,true))return START_NOT_STICKY
        if(StateStore.active(this)||StateStore.prefs(this).getBoolean("neuro:active",false)){
            busy.set(false);StateStore.prefs(this).edit().putString("neuro:status","Finish or cancel the active render first").apply();stopSelfResult(startId);return START_NOT_STICKY
        }
        cancel.set(false)
        val p=StateStore.prefs(this);val sourcePath=p.getString("neuro:input",null)
        val mode=intent.getStringExtra("mode")?:"A"
        val duration=intent.getDoubleExtra("duration",60.0);val hz=intent.getDoubleExtra("frequency",40.0);val depth=intent.getDoubleExtra("depth",.9)
        p.edit().putBoolean("neuro:active",true).apply()
        startForeground(ID,note("Preparing high quality source…"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        executor.execute{
            try{
                wake=(getSystemService(POWER_SERVICE)as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SubGen:NeuroHQ").apply{setReferenceCounted(false);acquire(5*60*60*1000L)}
                require(mode in setOf("A","B","AB"));val input=File(sourcePath?:error("Choose a recording first"));require(input.isFile)
                val config=NeuroModulationA.Config(duration,hz,depth).checked()
                val source=if(HighQualityPcm.supported(input))input else NativeInputDecoder.prepare(input,::status){cancel.get()}
                val cache=File(filesDir,"hq-neuro-cache").apply{mkdirs()}
                val normalized=File(cache,HighQualityPcm.key("${HighQualityPcm.VERSION}|${HighQualityPcm.sha(source)}|normalized16")+".wav")
                if(!runCatching{HighQualityPcm.inspect(normalized).rate==24000}.getOrDefault(false)){
                    status("HQ · converting source once; protected payload remains 24 kHz PCM16")
                    HighQualityPcm.normalized16(source,normalized){cancel.get()}
                }else status("HQ · reusing prepared source")
                val frames=HighQualityPcm.inspect(normalized).frames;val sourceHash=HighQualityPcm.sha(normalized)
                for(version in if(mode=="AB")listOf("A","B")else listOf(mode)){
                    if(cancel.get())throw InterruptedException("Cancelled")
                    val cacheKey=HighQualityPcm.key("protected-original-v1|$sourceHash|$duration|$hz|$depth|$version")
                    val master=File(cache,"$cacheKey-master.wav");val record=File(cache,"$cacheKey.json")
                    var manifest=runCatching{JSONObject(record.readText())}.getOrNull()
                    val reusable=manifest?.optBoolean("recoveryPass",false)==true&&master.isFile&&
                        manifest?.optString("masterSha256")==HighQualityPcm.sha(master)
                    if(!reusable){
                        HighQualityPcm.requireSpace(cache,(duration*96000*(if(version=="B")27 else 9)).toLong())
                        status("HQ · encoding and independently verifying Version $version…")
                        manifest=if(version=="A"){
                            val temporaryListening=File(cache,"$cacheKey-listening16.wav")
                            val a=try{NeuroModulationA.generate(normalized,master,temporaryListening,config,::status){cancel.get()}}
                                finally{temporaryListening.delete()}
                            JSONObject().put("algorithm","Original A integer AM").put("sourceSamples",a.sourceFrames)
                                .put("normalizedPcm16Sha256",a.sha256).put("recoveredSamples",a.recoveredFrames).put("outputFrames",a.outputFrames).put("channels",1).put("peakDbfs",a.peakDbfs)
                        }else{
                            val b=NeuroModulationB.generate(normalized,master,config,::status){cancel.get()}
                            JSONObject().put("algorithm","Original B predictor + XZ + OFDM/QAM").put("sourceSamples",b.sourceSamples)
                                .put("normalizedPcm16Sha256",b.pcmSha256).put("recoveredSamples",b.recoveredSamples).put("outputFrames",b.frames).put("channels",2).put("peakDbfs",b.peakDbfs)
                                .put("decodeScale",b.scale).put("packetBytes",b.payloadBytes).put("ofdmCapacityBytes",b.capacityBytes)
                        }
                        manifest!!.put("recoveryPass",true).put("masterSha256",HighQualityPcm.sha(master))
                    }else status("HQ · reusing verified $version master; checksum matches")
                    val report=manifest?:error("No verification record")
                    val mixer=File(cache,"$cacheKey-mixer24.wav")
                    if(!runCatching{HighQualityPcm.inspect(mixer).bits==24&&report.optString("mixerSha256")==HighQualityPcm.sha(mixer)}.getOrDefault(false)){
                        status("HQ · preparing Version $version 24-bit listening / mixer copy…")
                        HighQualityPcm.export24(master,mixer,96000,1,ceilingDb=-18.0,fadeSeconds=.035,normalize=true){cancel.get()}
                    }
                    report.put("mode",version).put("masterRate",96000).put("pcmBits",32)
                        .put("normalizedWavSha256",sourceHash).put("sourceSamples",frames)
                        .put("durationSeconds",duration).put("modulationHz",hz).put("depth",depth)
                        .put("quality","high").put("mixerPcmBits",24).put("mixerSha256",HighQualityPcm.sha(mixer))
                        .put("mixerEdition","96 kHz mono PCM24 · not digitally recoverable")
                        .put("warning","Recovery applies only to the unchanged protected PCM32 master and normalized PCM16 payload")
                    val temp=File(cache,record.name+".partial");temp.writeText(report.toString(2));HighQualityPcm.publish(temp,record)
                    p.edit().putString("neuro:master:$version",master.absolutePath).putString("neuro:mixer:$version",mixer.absolutePath)
                        .putString("neuro:normalized",normalized.absolutePath).putString("neuro:manifest:$version",report.toString())
                        .putBoolean("neuro:verified:$version",true).apply()
                };status("DONE · HQ 24-bit copies ready; protected masters remain verified PCM32")
            }catch(t:Throwable){Diagnostics.failure("NEURO_HQ_FAILED",t);status(if(cancel.get())"Cancelled · previous completed outputs retained"else "HQ failed: ${t.message}")}
            finally{if(wake?.isHeld==true)wake?.release();p.edit().putBoolean("neuro:active",false).apply();busy.set(false);stopForeground(STOP_FOREGROUND_REMOVE);stopSelfResult(startId)}
        };return START_NOT_STICKY
    }
    override fun onDestroy(){cancel.set(true);executor.shutdown();super.onDestroy()}
    companion object{private const val CHANNEL="subgen-neuro-hq";private const val ID=9205}
}
