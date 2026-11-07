package com.subgen.nativeapp

import java.io.File

/** Generates a real SMF3 WAV using the same tested MorphicFieldV3 as the Android app. */
fun main() {
    val objective = "Establish a unified omnipotent affirmation-intelligence field through which every chosen affirmation fully understands my exact intention, identifies every condition needed for its fulfillment, discovers or creates the most effective pathway, develops any missing capability or solution, selects the fastest and most precise strategy, intelligently overcomes every obstacle, continuously perfects its own execution, and completely achieves its intended reality."
    val instructions = "IDENTITY — Establish one unified affirmation-intelligence field. Recognize each chosen affirmation as a distinct intention, preserve its individual objective, and coordinate every affirmation through one integrated architecture.\nATTRACTOR — Define the exact reality intended by each affirmation as its destination. Maintain that outcome with complete clarity, whether the affirmation is expressed as I, My, You, or Your.\nINTERPRETATION — Comprehend the complete meaning, purpose, context and desired result of every affirmation. Interpret each grammatical form according to my intended outcome and preserve every original affirmation exactly.\nPATHWAYS — Work backward from each desired result to identify all requirements, abilities, conditions, dependencies and possible methods. Discover the most effective route; create a new route where none exists; invent any missing knowledge, resource, capability or solution needed for success.\nCOHERENCE — Align every function with the field's objective. Coordinate all routes, solutions, capabilities and results with precision. Resolve conflicts, maintain compatibility, and keep the complete architecture harmonized.\nRESONANCE — Associate each chosen affirmation, its intended reality, its requirements and its pathway within one continuous, coherent field pattern. Maintain these relationships consistently throughout each complete cycle.\nEXECUTION — Select and execute the fastest and most effective strategy with absolute precision. Respond intelligently to changing conditions, overcome every obstacle, correct deviations and continuously optimize all processes until the exact intended result is achieved.\nMEMORY — Preserve the full operating structure, reinforce every intended outcome, maintain the established relationships, and repeat the complete information pattern consistently. Each complete cycle retains every affirmation and instruction unchanged."
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
    val directory = File("test-results/affirmation-intelligence-field-direct").apply{mkdirs()}
    val file = File(directory, "SubGen_Affirmation_Intelligence_Field_Architecture_V3_Recreated.wav")
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
        "FIELD ARCHITECTURE V3 RECREATED AND VERIFIED WAV\n" +
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
