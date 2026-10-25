package com.subgen.nativeapp

import kotlin.math.abs
import kotlin.math.sqrt

/** Pure Kotlin audio quality audit. Never alters or conceals invalid speech samples. */
object AudioQuality {
    data class Report(
        val count: Int,
        val nanCount: Int,
        val infiniteCount: Int,
        val clippedCount: Int,
        val longestRailRun: Int,
        val peak: Double,
        val rms: Double
    ) {
        val nonFiniteCount: Int get() = nanCount + infiniteCount
        fun isAcceptable(sampleRate: Int): Boolean =
            count > 0 && sampleRate > 0 && nonFiniteCount == 0 &&
                peak <= 1.5 && rms > 0.00001 &&
                longestRailRun < sampleRate / 4
    }

    fun examine(samples: FloatArray): Report {
        var nan = 0
        var infinite = 0
        var clipped = 0
        var run = 0
        var longest = 0
        var maxAbs = 0.0
        var sumSquares = 0.0
        var valid = 0
        for (value in samples) {
            if (value.isNaN()) { nan++; run = 0; continue }
            if (!value.isFinite()) { infinite++; run = 0; continue }
            val magnitude = abs(value.toDouble())
            if (magnitude > maxAbs) maxAbs = magnitude
            sumSquares += magnitude * magnitude
            valid++
            if (magnitude >= 0.999) {
                clipped++; run++
                if (run > longest) longest = run
            } else run = 0
        }
        return Report(samples.size,nan,infinite,clipped,longest,maxAbs,
            if(valid==0) 0.0 else sqrt(sumSquares / valid.toDouble()))
    }
}
