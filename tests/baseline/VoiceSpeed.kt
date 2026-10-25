package com.subgen.baseline

import java.util.Locale
import kotlin.math.roundToInt

/** Voice playback speed is *separate* from neural inference throughput. */
object VoiceSpeed {
    const val MIN=0.65f
    const val MAX=1.20f
    const val DEFAULT=1.00f
    const val STEPS=55
    fun fromPosition(position:Int):Float =
        ((65 + position.coerceIn(0,STEPS))/100f).coerceIn(MIN,MAX)
    fun position(speed:Float):Int =
        ((speed.coerceIn(MIN,MAX)-MIN)*100f).roundToInt().coerceIn(0,STEPS)
    fun label(speed:Float):String =
        String.format(Locale.US,"VOICE SPEED · %.2f×",speed.coerceIn(MIN,MAX))
}
