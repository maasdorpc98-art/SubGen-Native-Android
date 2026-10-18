package com.subgen.nativeapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Dedicated native background service. The protected PCM32 master is never sent to the mixer. */
class NeuroModulationService:Service(){
    private val executor=Executors.newSingleThreadExecutor()
    private val busy=AtomicBoolean(false)
    private val cancelled=AtomicBoolean(false)
    private var wake:PowerManager.WakeLock?=null
    override fun onBind(intent:Intent?):IBinder?=null
    override fun onCreate(){super.onCreate();(getSystemService(NOTIFICATION_SERVICE)as NotificationManager)
        .createNotificationChannel(NotificationChannel(CHANNEL,"NeuroModulation rendering",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        val action=intent?.action
        if(action==ACTION_CANCEL){cancelled.set(true);status("Stopping after current audio block…");return START_NOT_STICKY}
        if(action!=ACTION_GENERATE){stopSelfResult(startId);return START_NOT_STICKY}
        if(!busy.compareAndSet(false,true))return START_NOT_STICKY
        cancelled.set(false)
        val prefs=StateStore.prefs(this)
        prefs.edit().putBoolean("neuro:active",true).apply()
        if(Build.VERSION.SDK_INT>=29)startForeground(ID,note("NeuroModulation native encoding"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID,note("NeuroModulation native encoding"))
        val selectedMode=intent.getStringExtra("mode")?:"A"
        val requestedDuration=intent.getDoubleExtra("duration",60.0)
        val hz=intent.getDoubleExtra("frequency",40.0)
        val depth=intent.getDoubleExtra("depth",.90)
        executor.execute{
            try {
                val pm=getSystemService(POWER_SERVICE)as PowerManager
                wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SubGen:NeuroA").apply{setReferenceCounted(false);acquire(12*60*1000L)}
                val path=prefs.getString("neuro:input",null)?:error("Select a spoken recording first")
                val input=File(path);require(input.isFile){"Source recording missing"}
                require(selectedMode in setOf("A","B","AB")){"Unknown NeuroModulation mode"}
                val config=NeuroModulationA.Config(requestedDuration,hz,depth).checked()
                val source=NativeInputDecoder.prepare(input,::status){cancelled.get()}
                val normalized=File(filesDir,"neuro-original-normalized-24k.wav")
                status("Preparing normalized 24 kHz mono source…")
                val frames=NeuroModulationA.normalize(source,normalized,::status){cancelled.get()}
                val versions=if(selectedMode=="AB")listOf("A","B") else listOf(selectedMode)
                // Fail-closed on each verification; retaining separately verified earlier versions.
                for(version in versions){
                    if(cancelled.get())throw InterruptedException("NeuroModulation cancelled")
                    status("Generating $version · ${versions.indexOf(version)+1} / ${versions.size} · $frames source samples")
                    val master=File(filesDir,"neuro-${version.lowercase()}-protected-96k-pcm32.wav")
                    val mixer=File(filesDir,"neuro-${version.lowercase()}-mixer-96k-pcm16.wav")
                    prefs.edit().putBoolean("neuro:verified:$version",false).apply()
                    val manifest=if(version=="A"){
                        val directListening=File(filesDir,"neuro-a-listening-96k-pcm16.wav")
                        val info=NeuroModulationA.generate(normalized,master,directListening,config,::status){cancelled.get()}
                        status("Preparing independent Version A mixer edition…")
                        NeuroModulationA.createMixerCopy(master,mixer,info.peakDbfs){cancelled.get()}
                        JSONObject().put("algorithm","Experiment 001 A, source × integer positive AM envelope")
                            .put("sourceSamples",info.sourceFrames).put("recoveredSamples",info.recoveredFrames)
                            .put("normalizedPcm16Sha256",info.sha256).put("pcmBits",32).put("channels",1)
                            .put("outputFrames",info.outputFrames).put("peakDbfs",info.peakDbfs)
                    }else{
                        val info=NeuroModulationB.generate(normalized,master,config,::status){cancelled.get()}
                        status("Preparing Version B independent PCM16 mixer edition…")
                        NeuroModulationB.createMixerCopy(master,mixer){cancelled.get()}
                        JSONObject().put("algorithm","Experiment 001 B, 3-tone speech predictor, XZ correction, stereo 2048-point OFDM and 22-bit QAM")
                            .put("sourceSamples",info.sourceSamples).put("recoveredSamples",info.recoveredSamples)
                            .put("normalizedPcm16Sha256",info.pcmSha256).put("pcmBits",32).put("channels",2)
                            .put("outputFrames",info.frames).put("peakDbfs",info.peakDbfs)
                            .put("decodeScale",info.scale).put("packetBytes",info.payloadBytes)
                            .put("ofdmCapacityBytes",info.capacityBytes)
                    }
                    require(master.isFile && mixer.isFile){"Version $version protected output or mixer file missing"}
                    manifest.put("mode",version).put("masterRate",96000)
                        .put("durationSeconds",requestedDuration).put("modulationHz",hz).put("depth",depth)
                        .put("recoveryPass",true)
                        .put("mixerEdition","96kHz mono PCM16 · not digitally recoverable")
                        .put("warning","Exact recovery applies only to unchanged PCM32 master; never to listening mix, headphones or air.")
                    File(filesDir,"neuro-$version-verification.json").writeText(manifest.toString(2))
                    prefs.edit().putString("neuro:master:$version",master.absolutePath)
                        .putString("neuro:mixer:$version",mixer.absolutePath)
                        .putString("neuro:normalized",normalized.absolutePath)
                        .putString("neuro:manifest:$version",manifest.toString())
                        .putBoolean("neuro:verified:$version",true).apply()
                    Diagnostics.record("NEURO_${version}_VERIFIED","",manifest)
                    status("VERSION $version VERIFIED · independently recovered every normalized PCM16 sample")
                }
                status("DONE · ${versions.joinToString(" + ")} digitally verified · Open Section 05 to save, preview or send to Studio")
            }catch(e:Throwable){
                Diagnostics.failure("NEURO_A_GENERATION_FAILED",e)
                status(if(cancelled.get())"Cancelled; previous completed output retained" else "FAILED · ${e.message?:e.javaClass.simpleName}")
            }finally{
                if(wake?.isHeld==true)runCatching{wake?.release()}
                prefs.edit().putBoolean("neuro:active",false).apply()
                busy.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelfResult(startId)
            }
        }
        return START_NOT_STICKY
    }
    private fun status(value:String){StateStore.prefs(this).edit().putString("neuro:status",value).apply()}
    private fun note(value:String):Notification {
        val i=Intent(this,MainActivity::class.java)
        val pending=PendingIntent.getActivity(this,3,i,PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("NeuroModulation · A / B / A+B").setContentText(value).setOngoing(true).setContentIntent(pending).build()
    }
    override fun onDestroy(){executor.shutdown();super.onDestroy()}
    companion object {
        const val ACTION_GENERATE="com.subgen.nativeapp.NEURO_GENERATE"
        const val ACTION_CANCEL="com.subgen.nativeapp.NEURO_CANCEL"
        private const val CHANNEL="subgen-neuro-v1"
        private const val ID=9204
    }
}