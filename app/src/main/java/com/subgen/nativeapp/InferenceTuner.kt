package com.subgen.nativeapp

import android.content.Context
import android.app.ActivityManager
import android.os.Build
import android.os.PowerManager
import org.json.JSONObject

/**
 * Control inference worker count independently of the chosen voice speed or output quality.
 * A saved on-device performance benchmark takes precedence when the phone is cool.
 * Memory/thermal safeguards always win over a speed preference.
 */
object InferenceTuner {
    fun threads(context: Context): Int {
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        val preferred = StateStore.prefs(context).getInt("autotuned:threads",0)
        var count = (if(preferred in 1..3) preferred else when {
            cores >= 6 -> 2
            cores >= 4 -> 2
            else -> 1
        }).coerceIn(1,cores.coerceAtMost(3))
        var thermal = -1
        try {
            if(Build.VERSION.SDK_INT >= 29) {
                thermal = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
                if(thermal >= PowerManager.THERMAL_STATUS_MODERATE) count = 1
            }
        } catch (_: Exception) { }
        var freeMemMiB = -1L
        try {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            freeMemMiB = info.availMem / 1048576L
            if(info.lowMemory || freeMemMiB < 1100L) count = 1
        } catch (_: Exception) { }
        Diagnostics.record("INFERENCE_THREAD_POLICY","Adaptive engine workers",JSONObject()
            .put("workers",count).put("availableCores",cores).put("preferred",preferred)
            .put("thermalStatus",thermal).put("freeMemoryMiB",freeMemMiB))
        return count
    }
}
