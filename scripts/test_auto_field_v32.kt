package com.subgen.nativeapp

import java.io.File

fun main(){
    val exact="""I grant every chosen affirmation omnipotent intelligence.
I make every affirmation understand my exact intentions.
I empower every affirmation to discover the perfect route to success.
I command every affirmation to create a route where none exists.
I make every affirmation identify everything required to achieve its goal.
I give every affirmation the intelligence to invent any missing solution.
I empower every affirmation to choose the fastest and most effective method.
I make every affirmation create whatever abilities its success requires.
I command every affirmation to execute its strategy with absolute precision.
I make every affirmation intelligently overcome every obstacle.
I empower every affirmation to continuously perfect its own execution.
I make every affirmation intelligently achieve its intended reality.
2. My — Converted
My power grants every chosen affirmation omnipotent intelligence.
My affirmations understand my exact intentions.
My affirmations discover the perfect route to success.
My affirmations create a route where none exists.
My affirmations identify everything required to achieve their goals.
My affirmations possess the intelligence to invent any missing solution.
My affirmations choose the fastest and most effective method.
My affirmations create whatever abilities their success requires.
My affirmations execute their strategies with absolute precision.
My affirmations intelligently overcome every obstacle.
My affirmations continuously perfect their own execution.
My affirmations intelligently achieve their intended reality.
3. You/Your — Converted
You grant every chosen affirmation omnipotent intelligence.
You make every affirmation understand your exact intentions.
You empower every affirmation to discover the perfect route to success.
You command every affirmation to create a route where none exists.
You make every affirmation identify everything required to achieve its goal.
You give every affirmation the intelligence to invent any missing solution.
You empower every affirmation to choose the fastest and most effective method.
You make every affirmation create whatever abilities its success requires.
You command every affirmation to execute its strategy with absolute precision.
You make every affirmation intelligently overcome every obstacle.
You empower every affirmation to continuously perfect its own execution.
You make every affirmation intelligently achieve its intended reality."""
    val generated=AutoFieldComposer.compose(exact)
    check(generated.theme=="Affirmation Intelligence"){"Wrong inferred purpose: "+generated.theme}
    check(generated.observedLines==36){"Wrong line count: "+generated.observedLines}
    check(generated.objective.contains("intelligence",true))
    check(generated.objective.contains("route",true))
    check(generated.instructions.contains("PATHWAYS"))
    check(generated.instructions.contains("INTERPRETATION"))
    check(generated.instructions.contains("MEMORY"))
    check(listOf("fictional","fantasy","hypothetical","imaginative").none{
        generated.objective.contains(it,true) || generated.instructions.contains(it,true)})
    check(AutoFieldComposer.compose(exact)==generated){"Non-deterministic analysis"}
    check(exact.contains("2. My — Converted") && exact.contains("3. You/Your — Converted"))

    val source=MorphicFieldV3.Blueprint(generated.objective,generated.instructions,exact).validated()
    val architecture=FieldArchitecture.create(MorphicFieldV2.Blueprint(
        source.objective,source.instructions,source.affirmations))
    check(architecture.dynamics.converged)
    check(architecture.lines==38){"The canonical field preserves both pasted section headings"}
    val minimum=MorphicFieldV3.minimumSeconds(source)
    val minutes=listOf(1,3,5,10,20,30,60).firstOrNull{it*60>=minimum}
        ?:error("Unable to fit user request within output duration")
    check(minutes>=1)
    val mono=File.createTempFile("subgen-auto-v32-mono-", ".wav")
    val ambient=File.createTempFile("subgen-auto-v32-stereo-", ".wav")
    try{
        val signal=MorphicFieldV3.render(source,minutes,MorphicFieldV3.Profile.SIGNAL,mono)
        val verified=MorphicFieldV3.verify(mono)
        check(verified.blueprint==source)
        check(verified.architecture==architecture.canonical())
        check(signal.sha256==verified.sha256 && signal.completeCycles>=1)
        val stereo=MorphicFieldV3.render(source,minutes,MorphicFieldV3.Profile.AMBIENT,ambient)
        val stereoVerify=MorphicFieldV3.verify(ambient)
        check(stereoVerify.blueprint==source)
        check(stereoVerify.architecture==architecture.canonical())
        check(stereoVerify.sha256==signal.sha256)
        check(stereo.completeCycles>=1)
    } finally {mono.delete();ambient.delete()}

    val fitness=AutoFieldComposer.compose("I run with endurance.\nMy strength increases.\nYou develop physical fitness.")
    check(fitness.theme=="Physical Performance"){"Fitness detection failed"}
    val wealth=AutoFieldComposer.compose("I build financial wealth.\nMy business generates revenue.\nYou create income.")
    check(wealth.theme=="Wealth and Opportunity"){"Wealth detection failed"}
    check(runCatching{AutoFieldComposer.compose("I improve. ".repeat(3000))}.isFailure) {
        "Codec overflow not rejected"
    }
    println("PASS: Auto Field Builder v3.2")
    println("Purpose: "+generated.theme+" · exact statements: "+generated.observedLines)
    println("Original source preserved: "+exact.lines().size+" lines including group headings")
    println("Minimum full packet: "+minimum+" s · auto selected: "+minutes+" min")
    println("Architecture SHA256 integrity · mono/stereo WAV decode · passed")
}
