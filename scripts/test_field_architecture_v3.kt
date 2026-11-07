package com.subgen.nativeapp

import java.io.File
import java.io.RandomAccessFile

fun main() {
    val source=MorphicFieldV2.Blueprint(
        "Develop consistent focus and constructive imagination",
        "Represent the desired state, select coherent pathways, and reinforce the conceptual pattern.",
        "I understand my intentions clearly.\nMy plans are precise.\nI execute meaningful actions consistently.\n" +
        "I develop lasting attention. My ability grows. 日本語 and isiZulu."
    )
    val a=FieldArchitecture.create(source)
    val b=FieldArchitecture.create(source)
    check(a.canonical()==b.canonical()) {"Architecture is nondeterministic"}
    check(a.nodes.size==7 && a.edges.size==7) {"Node and edge count wrong"}
    check(a.nodes.map{it.kind}==FieldArchitecture.Kind.values().toList())
    check(a.source==source)
    check(a.nodes.first().description.contains(a.fieldId))
    check(a.canonical().contains("status=FIELD_ARCHITECTURE_V31_INFORMATION_SPECIFICATION"))
    check(a.dynamics.converged && a.dynamics.finalEnergy < 1e-6) {
        "Attractor did not converge"
    }
    check(a.dynamics.finalEnergy<a.dynamics.initialEnergy)
    check(a.canonical().contains("DYNAMICS|SEVEN_DIMENSIONAL_CONTRACTIVE_ATTRACTOR|V1"))
    check(a.dynamics.lines.size == source.affirmations.lines().count { it.isNotBlank() })
    check(a.dynamics.target.size == 7)
    val different=FieldArchitecture.create(source.copy(
        affirmations=source.affirmations.replace("understand","manifest")))
    check(a.dynamics.target != different.dynamics.target){"Meaning-relevant change did not affect attractor"}
    check(a.canonical().contains("->"))
    check(a.fieldId!=FieldArchitecture.create(source.copy(affirmations=source.affirmations+"!")).fieldId)
    check(a.nodes.first{it.kind==FieldArchitecture.Kind.INTERPRETATION}.evidence>0)
    check(a.nodes.first{it.kind==FieldArchitecture.Kind.PATHWAYS}.evidence>0)

    val blueprint=MorphicFieldV3.Blueprint(source.objective,source.instructions,source.affirmations)
    val minimum=MorphicFieldV3.minimumSeconds(blueprint)
    check(minimum in 1..1800){"Invalid duration calculation: $minimum"}
    val minutes=((minimum+59)/60).coerceIn(1,60)
    val mono=File.createTempFile("field-architecture-v3-signal-", ".wav")
    val stereo=File.createTempFile("field-architecture-v3-ambient-", ".wav")
    val tampered=File.createTempFile("field-architecture-v3-tamper-", ".wav")
    try {
        val signal=MorphicFieldV3.render(blueprint,minutes,MorphicFieldV3.Profile.SIGNAL,mono)
        check(signal.architecture==a.canonical())
        check(signal.completeCycles>=1)
        check(mono.length()==44L+minutes*60L*24000L*2L)
        val checked=MorphicFieldV3.verify(mono)
        check(checked.blueprint==blueprint){"Source fields changed in WAV"}
        check(checked.architecture==a.canonical()){"Seven-node architecture not recovered"}
        check(checked.sha256==signal.sha256)

        val ambient=MorphicFieldV3.render(blueprint,minutes,MorphicFieldV3.Profile.AMBIENT,stereo)
        check(stereo.length()==44L+minutes*60L*24000L*4L)
        val stereoVerified=MorphicFieldV3.verify(stereo)
        check(stereoVerified.blueprint==blueprint)
        check(stereoVerified.architecture==a.canonical())
        check(stereoVerified.profile==MorphicFieldV3.Profile.AMBIENT)
        check(stereoVerified.sha256==ambient.sha256)
        check(ambient.sha256==signal.sha256){"Identity changed with audio profile"}

        mono.copyTo(tampered,overwrite=true)
        RandomAccessFile(tampered,"rw").use {f->
            f.seek(44L+350L)
            f.write(ByteArray(4000))
        }
        check(runCatching{MorphicFieldV3.verify(tampered)}.isFailure){"Tampered signal accepted"}
        check(runCatching{MorphicFieldV3.render(
            blueprint,minutes,MorphicFieldV3.Profile.SIGNAL,tampered,stop={true}
        )}.isFailure){"Cancellation ignored"}
        println("PASS: Field Architecture V3.1 · convergent attractor · seven nodes/edges · exact source retention · signal + stereo audio roundtrip · SHA · integrity")
    } finally {listOf(mono,stereo,tampered).forEach{it.delete()}}
}
