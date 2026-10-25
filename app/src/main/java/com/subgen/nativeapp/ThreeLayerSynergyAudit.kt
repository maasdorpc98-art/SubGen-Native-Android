package com.subgen.nativeapp

import java.io.File
import kotlin.math.abs

/**
 * Read-only three-track mix audit. This code NEVER adjusts gain, source samples,
 * playback speed, stereo position, fades or user preferences. The normal Carrier
 * and the 8D Carrier intentionally reuse the same storm recording.
 */
object ThreeLayerSynergyAudit {
    data class Review(val applicable:Boolean,val issues:List<String>) {
        val clean:Boolean get()=applicable && issues.isEmpty()
        val summary:String get()=when {
            !applicable -> "Not a complete three-source mix; no three-layer judgment"
            issues.isEmpty() -> "Three-layer paths meet the non-processing settings contract"
            else -> issues.joinToString("; ")
        }
    }
    private fun sameFile(a:File?,b:File?)=a!=null && b!=null && a.canonicalPath==b.canonicalPath
    private fun equal(a:Double,b:Double)=abs(a-b)<.0001

    fun review(s:NativeSubliminal.Settings):Review {
        if(s.carrierA==null || s.affirmationA==null || s.affirmationB==null)
            return Review(false,emptyList())
        val issues=mutableListOf<String>()
        if(!sameFile(s.carrierA,s.carrierB)) issues+="Storm 8D branch does not share the normal storm file"
        if(!equal(s.primarySpeed,1.0)) issues+="Affirmation A speed is not 1.00x"
        if(!equal(s.secondarySpeed,1.0)) issues+="Frequency A speed is not 1.00x (may change 40 Hz rate)"
        if(s.clarity) issues+="Affirmation clarity processing is enabled"
        if(s.binauralOn) issues+="Additional binaural tones are enabled"
        if(s.rhythmicOn) issues+="Additional rhythmic tones are enabled"
        if(!equal(s.fadeSeconds,0.0)) issues+="Storm has a timed fade (changes storm level at start)"
        if(!equal(s.layerDb,0.0)) issues+="Shared affirmation layer trim is not neutral"
        if(!equal(s.finalDb,0.0)) issues+="Final master trim is not neutral"
        if(!equal(s.carrierDb,0.0)) issues+="Normal storm fader differs from 0 dB baseline"
        if(!equal(s.primaryDb,-10.5)) issues+="Audible affirmation fader differs from -10.5 dB baseline"
        if(!equal(s.secondaryDb,-18.5)) issues+="Frequency A fader differs from -18.5 dB baseline"
        if(equal(s.carrier8dDb,-60.0)) issues+="8D storm is effectively muted (aggressive dual storm inactive)"
        return Review(true,issues)
    }
}
