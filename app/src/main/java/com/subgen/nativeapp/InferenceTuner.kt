package com.subgen.nativeapp

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONObject

/**
 * The app tunes *engine throughput*, not playback speed or generated audio fidelity.
 * Trials are sequential, so two 325-MB Kokoro models are never live together.
 * A persisted result avoids repeating the calibration on each long render.
 */
object InferenceTuner {
    private const val KEY_THREADS="autotuned:threads"
    private const val KEY_PROFILE="autotuned:fingerprint"
    private const val KEY_MODEL="autotuned:model"
    private const val KEY_RTF="autotuned:rtf"
    private const val MODEL_SIGNATURE="kokoro-fp32-nocallback-v1"

    fun availableMiB(context:Context):Long=try {
        val info=ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
        info.availMem/1048576L
    } catch (_:Exception) {-1L}

    fun heat(context:Context):Int=try {
        if(Build.VERSION.SDK_INT>=29)
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        else -1
    } catch (_:Exception) {-1}

    fun calibrated(context:Context):Int {
        val prefs=StateStore.prefs(context)
        if(prefs.getString(KEY_MODEL,"")!=MODEL_SIGNATURE) return 0
        if(prefs.getString(KEY_PROFILE,"")!=Build.FINGERPRINT) return 0
        return prefs.getInt(KEY_THREADS,0).takeIf{it in 1..4}?:0
    }

    fun threads(context:Context):Int {
        val cores=Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val saved=calibrated(context)
        val free=availableMiB(context)
        val thermal=heat(context)
        val result=WorkerPolicy.choose(cores,saved,free,thermal,
            PowerManager.THERMAL_STATUS_MODERATE,PowerManager.THERMAL_STATUS_SEVERE)
        Diagnostics.record("INFERENCE_THREAD_POLICY","FP32 adaptive policy",JSONObject()
            .put("workers",result).put("availableCores",cores).put("calibrated",saved)
            .put("thermalStatus",thermal).put("freeMemoryMiB",free))
        return result
    }

    /**
     * One-time calibration, only for a long *uncached* render.
     * Consumes up to four small test utterances and two model initializations;
     * if it cannot safely complete, the existing stable selection remains.
     */
    fun autoTuneIfNeeded(context:Context,installer:ModelInstaller,speaker:Int,speed:Float,
                         speechBlocks:Int,onStop:()->Boolean,notify:(String)->Unit):Int {
        if(calibrated(context)>0 || speechBlocks<60 || onStop())return threads(context)
        val cores=Runtime.getRuntime().availableProcessors()
        val thermal=heat(context)
        val free=availableMiB(context)
        if(cores<4 || thermal>=PowerManager.THERMAL_STATUS_MODERATE ||
            (free>=0L && free<1800L))return threads(context)
        val candidates=listOf(2,4).filter{it<=cores}
        val results=mutableListOf<Pair<Int,Double>>()
        val phrase="Take one comfortable breath and allow your thoughts to become calm."
        notify("Optimizing FP32 speech engine for this device (first long render only)…")
        for(worker in candidates) {
            if(onStop())return threads(context)
            try {
                notify("Measuring Kokoro speed with $worker CPU workers…")
                // First call warms ONNX graph and phonemizer. Second gives a comparable
                // steady-state RTF for this device, model and chosen voice.
                KokoroNative(installer,worker).use {tts->
                    tts.synthesize(phrase,speaker,speed,onStop)
                    if(onStop())return threads(context)
                    val began=SystemClock.elapsedRealtime()
                    val (samples,rate)=tts.synthesize(phrase,speaker,speed,onStop)
                    val ms=SystemClock.elapsedRealtime()-began
                    val audioSec=samples.size.toDouble()/rate
                    val rtf=ms/1000.0/audioSec
                    if(rtf.isFinite() && audioSec>0.0) {
                        results+=worker to rtf
                        Diagnostics.record("FP32_AUTOTUNE_SAMPLE","",JSONObject()
                            .put("workers",worker).put("audioSeconds",audioSec)
                            .put("elapsedMs",ms).put("rtf",rtf))
                    }
                }
            } catch(t:Throwable) {
                if(onStop())return threads(context)
                Diagnostics.failure("FP32_AUTOTUNE_TRIAL_FAILED",t)
                // Do not turn a calibration failure into a render failure.
            }
        }
        if(results.size<2)return threads(context)
        val selected=WorkerPolicy.winner(results) ?: return threads(context)
        StateStore.prefs(context).edit()
            .putInt(KEY_THREADS,selected.first).putString(KEY_PROFILE,Build.FINGERPRINT)
            .putString(KEY_MODEL,MODEL_SIGNATURE).putFloat(KEY_RTF,selected.second.toFloat()).apply()
        Diagnostics.record("FP32_AUTOTUNE_WINNER","",JSONObject()
            .put("workers",selected.first).put("steadyStateRtf",selected.second))
        notify("Tuned for device: ${selected.first} CPU workers · measured RTF ${"%.2f".format(selected.second)}")
        return threads(context)
    }

    fun rememberBenchmark(context:Context,workers:Int,rtf:Double){
        if(workers !in 1..4 || !rtf.isFinite() || rtf<=0.0)return
        StateStore.prefs(context).edit().putInt(KEY_THREADS,workers)
            .putString(KEY_PROFILE,Build.FINGERPRINT).putString(KEY_MODEL,MODEL_SIGNATURE)
            .putFloat(KEY_RTF,rtf.toFloat()).apply()
    }
}
