package com.subgen.nativeapp

import java.io.File
import java.io.RandomAccessFile

fun main(){
    val original=MorphicFieldV2.Blueprint(
        objective="Improve concentration and purposeful action",
        instructions="Recognize these written intentions as a cohesive field blueprint.\nPreserve every word exactly.",
        affirmations="My attention remains steady.\nI act deliberately.\nMy focus is strong — 日本語 and isiZulu."
    )
    val s=File.createTempFile("subgen-field-signal-", ".wav")
    val a=File.createTempFile("subgen-field-ambient-", ".wav")
    val altered=File.createTempFile("subgen-field-altered-", ".wav")
    val incomplete=File.createTempFile("subgen-field-stop-", ".wav")
    try {
        check(MorphicFieldV2.minimumSeconds(original)>1)
        val first=MorphicFieldV2.render(original,1,MorphicFieldV2.Profile.SIGNAL,s)
        check(first.completeCycles>=1)
        check(s.length()==44L+60L*24000L*2L){"Mono WAV size mismatch"}
        val recoveredSignal=MorphicFieldV2.verify(s)
        check(recoveredSignal.blueprint==original){"Mono exact text recovery failed"}
        check(first.sha256==recoveredSignal.sha256){"Mono hash differs"}

        val ambient=MorphicFieldV2.render(original,1,MorphicFieldV2.Profile.AMBIENT,a)
        check(a.length()==44L+60L*24000L*4L){"Stereo WAV size mismatch"}
        val recoveredAmbient=MorphicFieldV2.verify(a)
        check(recoveredAmbient.blueprint==original){"Ambient exact text recovery failed"}
        check(recoveredAmbient.profile==MorphicFieldV2.Profile.AMBIENT)
        check(ambient.sha256==recoveredAmbient.sha256)

        // Two distinct blueprints with equal lengths must never encode as the same samples.
        val changed=original.copy(objective=original.objective.reversed())
        val diff=MorphicFieldV2.render(changed,1,MorphicFieldV2.Profile.SIGNAL,altered)
        check(first.sha256!=diff.sha256){"Different written field produced identical digest"}
        check(MorphicFieldV2.verify(altered).blueprint==changed)

        // Header signal corruption must not be accepted as a verified blueprint.
        RandomAccessFile(altered,"rw").use { f ->
            f.seek(44L + 240L)
            f.write(ByteArray(3000))
        }
        check(runCatching { MorphicFieldV2.verify(altered) }.isFailure){"Corrupted audio was accepted"}

        // An incomplete packet is refused, rather than silently truncating affirmations.
        val long=MorphicFieldV2.Blueprint("","", "My intentions. ".repeat(350))
        check(MorphicFieldV2.minimumSeconds(long)>60)
        check(runCatching { MorphicFieldV2.render(long,1,MorphicFieldV2.Profile.AMBIENT,incomplete) }.isFailure)
        check(!incomplete.exists() || incomplete.length()==0L)
        check(runCatching {
            MorphicFieldV2.render(original,1,MorphicFieldV2.Profile.SIGNAL,incomplete,stop={true})
        }.isFailure)
        check(!incomplete.exists() || incomplete.length()==0L)
        println("PASS: Field Lab v2 · mono + stereo ambient recovery · Unicode · SHA-256 · tamper and capacity checks")
    } finally {
        listOf(s,a,altered,incomplete).forEach{it.delete()}
    }
}
