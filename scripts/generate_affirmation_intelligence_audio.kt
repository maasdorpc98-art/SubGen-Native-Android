package com.subgen.nativeapp

import java.io.File

/** Generates a real SMF3 WAV using the same tested MorphicFieldV3 as the Android app. */
fun main() {
    val objective = "Within the fictional Field Architecture model, establish an integrated affirmation-intelligence field in which every chosen affirmation understands its exact intended meaning, develops or invents the most effective path to its goal, supplies any missing abilities or solutions, acts with flawless precision, adapts intelligently to all obstacles, continuously optimizes its approach, and realizes its intended reality."
    val instructions = "IDENTITY — Form one coherent affirmation-intelligence architecture. Treat every chosen affirmation as an individually addressable intention while preserving the unity of the entire design.\nATTRACTOR — Represent each affirmation's exact intended reality as its destination, maintaining the desired meaning rather than restricting it to a particular tense or grammatical perspective.\nINTERPRETATION — Understand the complete intended meaning behind I, My, and You/Your wording. Resolve contextual ambiguity in favour of the intended outcome. Preserve every original affirmation verbatim in the source payload.\nPATHWAYS — Working backward from each destination, identify all prerequisites, capabilities, actions and dependencies. Discover the strongest available route; within the imaginative model, devise a new route where none exists and invent any missing knowledge, means, ability or solution.\nCOHERENCE — Coordinate the proposed routes as one harmonized structure. Select efficient approaches, resolve incompatibilities, and retain the precise intended objectives. No part of the design cancels or distorts another.\nRESONANCE MODEL — Symbolically associate every intended outcome with its corresponding field identity and pathway. Represent this association as an experimental information relationship, not a demonstrated physical influence.\nEXECUTION — In the fantasy design, apply each selected route accurately, respond intelligently to obstacles, and continuously improve strategy, timing and precision until the intended result is complete.\nMEMORY — Preserve the architecture, maintain its intent, and reinforce the complete informational pattern through repeatable full-length encoding cycles. Each cycle retains the same unchanged source and independently verifiable relationships."
    val affirmations = "I grant every chosen affirmation omnipotent intelligence.\nI make every affirmation understand my exact intentions.\nI empower every affirmation to discover the perfect route to success.\nI command every affirmation to create a route where none exists.\nI make every affirmation identify everything required to achieve its goal.\nI give every affirmation the intelligence to invent any missing solution.\nI empower every affirmation to choose the fastest and most effective method.\nI make every affirmation create whatever abilities its success requires.\nI command every affirmation to execute its strategy with absolute precision.\nI make every affirmation intelligently overcome every obstacle.\nI empower every affirmation to continuously perfect its own execution.\nI make every affirmation intelligently achieve its intended reality.\n\nMy power grants every chosen affirmation omnipotent intelligence.\nMy affirmations understand my exact intentions.\nMy affirmations discover the perfect route to success.\nMy affirmations create a route where none exists.\nMy affirmations identify everything required to achieve their goals.\nMy affirmations possess the intelligence to invent any missing solution.\nMy affirmations choose the fastest and most effective method.\nMy affirmations create whatever abilities their success requires.\nMy affirmations execute their strategies with absolute precision.\nMy affirmations intelligently overcome every obstacle.\nMy affirmations continuously perfect their own execution.\nMy affirmations intelligently achieve their intended reality.\n\nYou grant every chosen affirmation omnipotent intelligence.\nYou make every affirmation understand your exact intentions.\nYou empower every affirmation to discover the perfect route to success.\nYou command every affirmation to create a route where none exists.\nYou make every affirmation identify everything required to achieve its goal.\nYou give every affirmation the intelligence to invent any missing solution.\nYou empower every affirmation to choose the fastest and most effective method.\nYou make every affirmation create whatever abilities its success requires.\nYou command every affirmation to execute its strategy with absolute precision.\nYou make every affirmation intelligently overcome every obstacle.\nYou empower every affirmation to continuously perfect its own execution.\nYou make every affirmation intell igently achieve its intended reality."

    val source = MorphicFieldV2.Blueprint(objective, instructions, affirmations)
    val architecture = FieldArchitecture.create(source)
    check(affirmations.lines().count { it.isNotBlank() } == 36) { "Expected exactly 36 affirmation lines" }
    check(affirmations.contains("You make every affirmation intell igently achieve its intended reality.")) {
        "Exact user-supplied spelling was changed"
    }
    val input = MorphicFieldV3.Blueprint(objective, instructions, affirmations)
    val minimum = MorphicFieldV3.minimumSeconds(input)
    val minutes = listOf(1,3,5,10,20,30,60).firstOrNull { it*60 >= minimum }
        ?:error("Cannot fit complete written field into 60 minutes")
    val directory = File("test-results/affirmation-intelligence-field").apply{mkdirs()}
    val file = File(directory, "SubGen_Affirmation_Intelligence_Field_Architecture_V3.wav")
    println("ARCHITECTURE-ID: " + architecture.fieldId)
    println("MINIMUM-PACKET-SECONDS: " + minimum)
    println("OUTPUT-MINUTES: " + minutes)
    println("INPUT-AFFIRMATION-LINES: " + affirmations.lines().count { it.isNotBlank() })
    val output = MorphicFieldV3.render(input,minutes,MorphicFieldV3.Profile.AMBIENT,file,
        update={ status->println("RENDER: " + status) })
    println("VERIFY: Reading source and architecture back from WAV samples")
    val decoded = MorphicFieldV3.verify(file)
    check(decoded.blueprint==input){"Decoded source differs from submitted text"}
    check(decoded.architecture==architecture.canonical()){"Decoded architecture differs from original"}
    check(decoded.architecture==output.architecture){"Rendered architecture differs from input graph"}
    check(decoded.sha256==output.sha256){"SHA-256 mismatch"}
    check(decoded.completeCycles>=1){"No complete architecture cycles"}
    check(file.length()==44L+minutes*60L*24000L*4L){"Unexpected stereo PCM16 WAV size"}
    File(directory,"Field_Objective.txt").writeText(objective,Charsets.UTF_8)
    File(directory,"Field_Instructions.txt").writeText(instructions,Charsets.UTF_8)
    File(directory,"Affirmations_Exact_I_My_YouYour.txt").writeText(affirmations,Charsets.UTF_8)
    File(directory,"Seven_Node_Field_Architecture.txt").writeText(architecture.canonical(),Charsets.UTF_8)
    File(directory,"Verification_Report.txt").writeText(
        "FIELD ARCHITECTURE V3 VERIFIED WAV\n" +
        "Field ID: " + architecture.fieldId + "\n" +
        "Affirmation lines: 36 (I=12, My=12, You/Your=12)\n" +
        "Profile: Ambient stereo, 24 kHz, PCM16\n" +
        "Duration: " + minutes + " minutes\n" +
        "Minimum full architecture packet: " + minimum + " seconds\n" +
        "Complete data cycles: " + decoded.completeCycles + "\n" +
        "Decoded source and full seven-node architecture: MATCH\n" +
        "SHA-256 of data payload: " + decoded.sha256 + "\n" +
        "WAV bytes: " + file.length() + "\n" +
        "Verification proves reversible digital encoding, not morphic effects.\n",
        Charsets.UTF_8
    )
    println("SUCCESS: " + file.absolutePath + "; SHA-256=" + decoded.sha256 +
        "; COMPLETE-CYCLES=" + decoded.completeCycles + "; BYTES=" + file.length())
}
