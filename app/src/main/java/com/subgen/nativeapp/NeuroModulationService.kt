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
        if(Build.VERSION.SDK_INT>=29)startForeground(ID,note("NeuroModulation native Version A"),ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(ID,note("NeuroModulation native Version A"))
        val requestedDuration=intent.getDoubleExtra("duration",60.0)
        val hz=intent.getDoubleExtra("frequency",40.0)
        val depth=intent.getDoubleExtra("depth",.90)
        executor.execute{
            try {
                val pm=getSystemService(POWER_SERVICE)as PowerManager
                wake=pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"SubGen:NeuroA").apply{setReferenceCounted(false);acquire(12*60*1000L)}
                val path=prefs.getString("neuro:input",null)?:error("Select a spoken recording first")
                val input=File(path);require(input.isFile){"Source recording missing"}
                val config=NeuroModulationA.Config(requestedDuration,hz,depth).checked()
                val source=NativeInputDecoder.prepare(input,::status){cancelled.get()}
                val normalized=File(filesDir,"neuro-a-normalized-24k.wav")
                val master=File(filesDir,"neuro-a-protected-96k-pcm32.wav")
                val listening=File(filesDir,"neuro-a-listening-96k-pcm16.wav")
                status("Preparing normalized 24kHz mono source…")
                val frames=NeuroModulationA.normalize(source,normalized,::status){cancelled.get()}
                status("Encoding native A · $frames original normalized samples")
                val report=NeuroModulationA.generate(normalized,master,listening,config,::status){cancelled.get()}
                val mixer=File(filesDir,"neuro-a-mixer-96k-pcm16.wav")
                status("Preparing separate Studio-friendly listening copy…")
                NeuroModulationA.createMixerCopy(master,mixer,report.peakDbfs){cancelled.get()}
                val manifest=JSONObject().put("mode","A").put("algorithm","Experiment 001 direct integer 40Hz AM")
                    .put("normalizedPcm16Sha256",report.sha256)
                    .put("sourceSamples",report.sourceFrames).put("recoveredSourceSamples",report.recoveredFrames)
                    .put("masterRate",96000).put("masterChannels",1).put("masterBits",32)
                    .put("durationSeconds",report.outputFrames/96000.0)
                    .put("modulationHz",hz).put("depth",depth)
                    .put("peakDbfs",report.peakDbfs).put("recoveryPass",true)
                    .put("warning","Recovery applies to unchanged PCM32 master only; not listening copy or Studio mix")
                File(filesDir,"neuro-a-verification.json").writeText(manifest.toString(2))
                prefs.edit().putString("neuro:master",master.absolutePath)
                    .putString("neuro:listening",listening.absolutePath)
                    .putString("neuro:mixer",mixer.absolutePath)
                    .putString("neuro:normalized",normalized.absolutePath)
                    .putString("neuro:manifest",manifest.toString())
                    .putBoolean("neuro:verified",true).apply()
                Diagnostics.record("NEURO_A_VERIFIED","",manifest)
                status("VERIFIED · ${report.recoveredFrames} source samples recovered exactly · ${"%.1f".format(java.util.Locale.US,report.outputFrames/96000.0)}s")
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
            .setContentTitle("NeuroModulation · Version A").setContentText(value).setOngoing(true).setContentIntent(pending).build()
    }
    override fun onDestroy(){executor.shutdown();super.onDestroy()}
    companion object {
        const val ACTION_GENERATE="com.subgen.nativeapp.NEURO_GENERATE"
        const val ACTION_CANCEL="com.subgen.nativeapp.NEURO_CANCEL"
        private const val CHANNEL="subgen-neuro-v1"
        private const val ID=9204
    }
}