package com.subgen.nativeapp

/** Pure selection logic so speed safeguards can be regression-tested without Android. */
object WorkerPolicy {
    fun choose(cores:Int,saved:Int,freeMemoryMiB:Long,thermal:Int,
               moderate:Int=2,severe:Int=4):Int {
        val available=cores.coerceAtLeast(1)
        var workers=(if(saved in 1..4)saved else when {
            available>=8->4
            available>=6->3
            available>=4->2
            else->1
        }).coerceIn(1,minOf(available,4))
        if(freeMemoryMiB in 0..449)workers=1
        else if(freeMemoryMiB in 450..1099)workers=minOf(workers,2)
        if(thermal>=severe)workers=1
        else if(thermal>=moderate)workers=minOf(workers,2)
        return workers
    }
    fun winner(samples:List<Pair<Int,Double>>):Pair<Int,Double>? {
        val values=samples.filter{it.first in 1..4&&it.second.isFinite()&&it.second>0.0}
        val best=values.minByOrNull{it.second}?:return null
        val conservative=values.firstOrNull{it.first==2}
        // An improvement below 7% can be measurement noise, avoid excess CPU heat.
        return if(conservative!=null&&conservative.second<=best.second*1.07)conservative else best
    }
}
