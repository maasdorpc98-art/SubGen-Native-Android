package com.subgen.nativeapp

/** Pure selection logic; we only reinitialize a 325 MB neural model when
 * there is clear evidence of sustained heat and slow synthesis.
 */
object ThermalRecheckPolicy {
    fun shouldCompare(workers:Int,generated:Int,thermal:Int,freeMiB:Long,
                      recentRtf:Double,alreadyCompared:Boolean,
                      moderate:Int=2,severe:Int=4):Boolean =
        !alreadyCompared && workers>=3 && generated>=36 && generated%18==0 &&
        thermal>=moderate && thermal<severe && freeMiB>=1600 &&
        recentRtf.isFinite() && recentRtf>2.3

    fun preferTwo(currentRtf:Double,twoRtf:Double):Boolean =
        currentRtf.isFinite() && twoRtf.isFinite() && currentRtf>0.0 &&
        twoRtf>0.0 && twoRtf<currentRtf*0.92
}
