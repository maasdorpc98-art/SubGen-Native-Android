package com.subgen.nativeapp

/** Immutable control inventory extracted from the user-approved Subliminal Studio v1.1 APK.
 * These IDs, bounds, steps and defaults are NOT redesignable. Controls that were hidden in
 * the original remain defined for compatibility and must not be exposed without a UI match.
 */
data class OriginalSliderSpec(
    val id: String, val label: String, val minimum: Double, val maximum: Double,
    val step: Double, val initial: Double, val note: String = ""
 ) {
    fun clampAndStep(x: Double): Double {
        val valid = if (x.isFinite()) x else initial
        val clipped = valid.coerceIn(minimum, maximum)
        val intervals = kotlin.math.round((clipped - minimum) / step)
        return (minimum + intervals * step).coerceIn(minimum, maximum)
    }
}

object OriginalStudioControlSpec {
    val sliders: List<OriginalSliderSpec> = listOf(
        OriginalSliderSpec("ctrl-vol-carrier", "Normal Carrier Volume 0 dB", -60.0, 0.0, 0.5, 0.0, "Static carrier copy · no movement. 0 dB = matched-baseline ceiling; move left only to make it quieter."),
        OriginalSliderSpec("ctrl-vol-noise", "Secondary · Affirmation 1 (A) Volume 0.0 dB", -60.0, 0.0, 0.5, 0.0, "0 dB = matched-baseline ceiling; move left only to make Affirmation 1 (A) quieter."),
        OriginalSliderSpec("ctrl-vol1", "Primary · Affirmation 1 Volume 0.0 dB", -60.0, 0.0, 0.5, 0.0, "0 dB = matched-baseline ceiling; move left only to make Affirmation 1 quieter."),
        OriginalSliderSpec("ctrl-master-balance-a", "Standard · Primary ↔ Secondary", -100.0, 100.0, 1.0, 0.0, "Less = lower amount · More = higher amount."),
        OriginalSliderSpec("ctrl-master-balance-b", "Master 1 · Carrier ↔ Both Affirmations", -100.0, 100.0, 1.0, 0.0, "Less = lower amount · More = higher amount."),
        OriginalSliderSpec("ctrl-master-output-db", "Master Audio Volume · Final Output", -60.0, 0.0, 0.5, 0.0, "Less = lower amount · More = higher amount."),
        OriginalSliderSpec("ctrl-vol-carrier-8d", "8D Carrier Volume −60.0 dB", -60.0, 0.0, 0.5, -60.0, "Same uploaded Carrier file · independent 8D branch volume."),
        OriginalSliderSpec("ctrl-carrier-hpf", "High-Pass OFF", 0.0, 10.0, 1.0, 0.0, "Optional low-rumble cleanup. OFF keeps the uploaded Carrier unchanged."),
        OriginalSliderSpec("ctrl-carrier-lpf", "Low-Pass OFF", 0.0, 10.0, 1.0, 0.0, "Optional high-frequency softening. OFF keeps the uploaded Carrier unchanged."),
        OriginalSliderSpec("ctrl-carrier-width", "Stereo Width 100%", 0.0, 200.0, 10.0, 100.0, "100% = original stereo · 0% = mono · above 100% widens only the Normal Carrier branch."),
        OriginalSliderSpec("ctrl-carrier-fade", "Fade In 0s", 0.0, 30.0, 1.0, 0.0, "Session-start Carrier fade only. 0 seconds keeps the existing immediate start."),
        OriginalSliderSpec("ctrl-carrier-8d-motion", "8D Rotation Speed 25%", 0.0, 100.0, 1.0, 0.0, "Width and rotation speed are independent. Neither changes the 8D branch volume."),
        OriginalSliderSpec("ctrl-carrier-8d-speed", "8D Rotation Speed 25%", 0.0, 100.0, 1.0, 25.0, ""),
        OriginalSliderSpec("ctrl-binaural-volume", "Binaural Volume 0.0 dB", -60.0, 0.0, 0.5, 0.0, "0 dB = neutral binaural level, matching the other source controls. This trim changes only the generated tone pair and remains independent of Master 1 and Master 2."),
        OriginalSliderSpec("ctrl-binaural-beat", "Beat Frequency 10.0 Hz", 1.0, 30.0, 0.5, 10.0, "This is the difference between the left-ear and right-ear tones."),
        OriginalSliderSpec("ctrl-binaural-center", "Center Tone 400 Hz", 100.0, 900.0, 5.0, 400.0, "Audible midpoint. The app places one tone below it and one equally above it."),
        OriginalSliderSpec("ctrl-rhythmic-volume", "Rhythmic Volume 0.0 dB", -60.0, 0.0, 0.5, 0.0, "0 dB = neutral generated-tone level. This engine is independent of both Master Balance sliders."),
        OriginalSliderSpec("ctrl-rhythmic-pulse", "Pulse Frequency 6.0 Hz", 0.5, 50.0, 0.5, 6.0, "How many amplitude pulses occur each second. Delta and Theta buttons set this same pulse-frequency control; the band label is informational only."),
        OriginalSliderSpec("ctrl-rhythmic-tone", "Tone Frequency 400 Hz", 100.0, 900.0, 5.0, 400.0, "The audible pitch being pulsed. This is separate from the pulse frequency."),
        OriginalSliderSpec("ctrl-rhythmic-depth", "Pulse Depth 100%", 0.0, 100.0, 1.0, 100.0, "100% = full modulation depth. 0% = a constant tone with no rhythmic pulsing."),
        OriginalSliderSpec("ctrl-secondary-speed", "Secondary Speed 1.00x", 0.1, 3.0, 0.05, 1.0, "When ON, this is the Secondary-only speed. When OFF, it follows Primary exactly."),
        OriginalSliderSpec("ctrl-na-layer-speed-1", "Speed 1.00x", 0.1, 3.0, 0.05, 1.0, "Primary speed. Secondary uses this same speed whenever Secondary Preset Modification is OFF."),
        OriginalSliderSpec("ctrl-na-layer-volume-1", "Layer Volume 0.0 dB", -60.0, 0.0, 0.5, 0.0, "One shared attenuation-only layer trim. It remains identical for both affirmation streams even when Secondary modification is ON."),
        OriginalSliderSpec("offline-rate", "Speed 0.85x", 0.4, 1.6, 0.05, 0.85, "Less = slower speech · More = faster speech."),
        OriginalSliderSpec("offline-pitch", "Pitch 0.90", 0.4, 1.6, 0.05, 0.9, "Less = deeper voice · More = higher voice."),
        OriginalSliderSpec("offline-vol", "Volume 1.0", 0.1, 1.0, 0.05, 1.0, "Less = quieter voice · More = louder voice."),
        OriginalSliderSpec("tts-stability", "Stability 0.50", 0.0, 1.0, 0.05, 0.5, "Less = more expressive · More = more consistent."),
        OriginalSliderSpec("tts-similarity", "Similarity 0.75", 0.0, 1.0, 0.05, 0.75, "Less = looser voice match · More = closer voice match."),
    )
    init {
        check(sliders.size == 28)
        check(sliders.map { it.id }.toSet().size == sliders.size)
        check(sliders.all { it.minimum <= it.initial && it.initial <= it.maximum && it.step > 0.0 })
    }
    fun slider(id: String): OriginalSliderSpec = sliders.first { it.id == id }
}
