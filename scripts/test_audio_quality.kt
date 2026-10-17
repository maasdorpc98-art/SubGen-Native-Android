import com.subgen.nativeapp.AudioQuality

fun main() {
    val sine = FloatArray(24000) { k -> (0.23f * kotlin.math.sin(k.toDouble() / 23.0)).toFloat() }
    val valid = AudioQuality.examine(sine)
    check(valid.isAcceptable(24000))
    check(valid.nonFiniteCount == 0)
    val nan = sine.copyOf().apply { this[80] = Float.NaN }
    check(AudioQuality.examine(nan).nanCount == 1)
    check(!AudioQuality.examine(nan).isAcceptable(24000))
    val inf = sine.copyOf().apply { this[100] = Float.POSITIVE_INFINITY }
    check(AudioQuality.examine(inf).infiniteCount == 1)
    check(!AudioQuality.examine(inf).isAcceptable(24000))
    val rail = FloatArray(24000) { 1.0f }
    check(AudioQuality.examine(rail).longestRailRun == 24000)
    check(!AudioQuality.examine(rail).isAcceptable(24000))
    val silent = FloatArray(24000)
    check(!AudioQuality.examine(silent).isAcceptable(24000))
    check(!AudioQuality.examine(floatArrayOf()).isAcceptable(24000))
    check(AudioQuality.examine(sine).peak < 0.3)
    println("PASS: finite speech accepted; NaN, infinity, clipped rails, empty and silent speech rejected")
}
