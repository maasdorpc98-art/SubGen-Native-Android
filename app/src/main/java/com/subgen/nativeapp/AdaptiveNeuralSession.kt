package com.subgen.nativeapp

import android.os.SystemClock
import org.json.JSONObject

/** One neural engine at a time; compare worker throughput between saved blocks. */
class AdaptiveNeuralSession(private val assets:ModelInstaller, initialWorkers:Int,
                            private val stop:()->Boolean):AutoCloseable {
    var workers=initialWorkers
        private set
    private var engine=KokoroNative(assets,workers)
    private var checked=false
    fun synthesize(text:String,speaker:Int,speed:Float):Pair<FloatArray,Int> =
        engine.synthesize(text,speaker,speed,stop)

    fun recheck(speaker:Int,speed:Float,completed:Int,rtf:Double,heat:Int,freeMiB:Long){
        if(!ThermalRecheckPolicy.shouldCompare(workers,completed,heat,freeMiB,rtf,checked))return
        checked=true
        if(stop())return
        val original=workers
        val phrase="This recording contains a short benchmark sentence."
        val begin=SystemClock.elapsedRealtime()
        var released=false
        var trialEngine:KokoroNative?=null
        var initialRtf=Double.NaN
        var alternateRtf=Double.NaN
        var switched=false
        fun measure(tts:KokoroNative):Double {
            tts.synthesize(phrase,speaker,speed,stop)
            if(stop())throw InterruptedException("Stopped")
            val start=SystemClock.elapsedRealtime()
            val (samples,hz)=tts.synthesize(phrase,speaker,speed,stop)
            return (SystemClock.elapsedRealtime()-start)/1000.0/(samples.size.toDouble()/hz)
        }
        try {
            initialRtf=measure(engine)
            if(stop())return
            engine.close();released=true
            trialEngine=KokoroNative(assets,2)
            alternateRtf=measure(trialEngine)
            if(ThermalRecheckPolicy.preferTwo(initialRtf,alternateRtf)){
                engine=trialEngine
                trialEngine=null
                workers=2
                switched=true
            } else {
                trialEngine.close();trialEngine=null
                engine=KokoroNative(assets,original)
            }
        } catch(e:Throwable) {
            Diagnostics.failure("THERMAL_COMPARE_ERROR",e)
            runCatching{trialEngine?.close()}
            if(released)engine=KokoroNative(assets,original)
        } finally {
            Diagnostics.record("THERMAL_COMPARE_COMPLETED","",JSONObject()
                .put("oldWorkers",original).put("newWorkers",workers)
                .put("oldRtf",initialRtf).put("newRtf",alternateRtf)
                .put("heat",heat).put("changed",switched)
                .put("elapsedMs",SystemClock.elapsedRealtime()-begin))
        }
    }
    override fun close(){engine.close()}
}
