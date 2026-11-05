package com.subgen.nativeapp

import java.io.File

fun main() {
    val affirmations = "I choose clear concentration.\nMy intentions are clear.\nYou maintain focus."
    val first = File.createTempFile("subgen-morphic-a-", ".wav")
    val second = File.createTempFile("subgen-morphic-b-", ".wav")
    try {
        val report = MorphicFieldEngine.render(affirmations, 1, first)
        check(first.isFile && first.length() == 44L + 60L * 24000L * 2L) { "Unexpected WAV size" }
        check(report.inputBytes == affirmations.toByteArray(Charsets.UTF_8).size)
        check(report.completeCycles >= 1)
        check(MorphicFieldEngine.verifyText(first) == affirmations) { "Exact text round-trip failed" }
        MorphicFieldEngine.render(affirmations + "!", 1, second)
        check(!first.readBytes().contentEquals(second.readBytes())) { "Different text produced identical WAV" }
        check(MorphicFieldEngine.verifyText(second) == affirmations + "!")
        println("PASS: morphic text-to-waveform generator · Unicode / checksum / distinct input")
    } finally {
        first.delete()
        second.delete()
    }
}
