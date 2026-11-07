package com.subgen.nativeapp

/**
 * Offline, deterministic field-specification composer. It identifies the
 * dominant theme and builds editable objective/instructions. The original
 * affirmation source is never altered. This is not a language model.
 */
object AutoFieldComposer {
    data class Analysis(
        val objective:String, val instructions:String, val theme:String,
        val observedLines:Int, val details:String
    )
    private data class Theme(
        val name:String, val goal:String, val method:String, val patterns:List<Regex>
    )
    private fun rx(s:String)=Regex(s, RegexOption.IGNORE_CASE)
    private val categories=listOf(
        Theme("Affirmation Intelligence",
            "create a unified autonomous affirmation-intelligence field that fully understands each chosen affirmation's exact intended meaning, identifies every prerequisite, discovers or invents the best route, develops missing capabilities and solutions, executes with absolute precision, overcomes obstacles, and continuously perfects the pursuit of its intended result",
            "comprehensive intention understanding, prerequisite discovery, pathway invention, coordinated strategy selection, precise execution and continual improvement",
            listOf(rx("""\baffirmations?\b"""),rx("""\b(intelligen|understand|interpret|decode|discover)\w*"""),
                rx("""\b(route|pathway|strateg|solution|method|create|invent|overcome)\w*"""))),
        Theme("Affirmation Realization",
            "coordinate all chosen affirmations to establish their exact intended outcomes with clarity, precision, adaptable pathways and continuing integration",
            "interpretation, requirements identification, pathway alignment, coordinated action and intended-result maintenance",
            listOf(rx("""\baffirmations?\b"""),rx("""\b(manifest|reality|realiz|transform|achiev|fulfil)\w*"""))),
        Theme("Mental Capacity",
            "develop the mental abilities specified in the affirmations, including comprehension, learning, focus, memory, creative problem-solving and intelligent application",
            "attention, comprehension, practice, strategy development, consistency and continued refinement",
            listOf(rx("""\b(intelligen|genius|memory|learn|focus|attention|cognit|mind|mental|knowledge)\w*"""),
                rx("""\b(understand|percept|skill|abilit|think|master)\w*"""))),
        Theme("Physical Performance",
            "develop and sustain the strength, endurance, coordination, mobility, fitness and physical performance described by the chosen affirmations",
            "skill development, training progression, recovery, performance adaptation and sustainable practice",
            listOf(rx("""\b(strength|muscle|fitnes|enduran|athlet|run|power|mobilit|health|body|physic)\w*"""))),
        Theme("Wealth and Opportunity",
            "develop and sustain the financial, professional, creative and opportunity-related outcomes specified by the affirmations",
            "planning, practical decision-making, awareness of resources and opportunities, consistency and adaptation",
            listOf(rx("""\b(wealth|money|income|business|profit|financ|customer|sale|career|opportun)\w*"""))),
        Theme("Confidence and Relationships",
            "develop the confidence, communication, self-respect and healthy interpersonal outcomes expressed in the chosen affirmations",
            "self-awareness, communication, confident choices, consistent behaviour and respect for others' autonomy",
            listOf(rx("""\b(confiden|social|relationship|communicat|attract|charisma|love|self.respect)\w*"""))),
        Theme("Discipline and Growth",
            "develop and sustain the discipline, habits, abilities, purposeful action and progress described by the chosen affirmations",
            "clarity, habits, effective routines, practice, resilience and continual adjustment",
            listOf(rx("""\b(discipline|habit|persist|progress|consistent|success|effort|grow|routine|master|achiev)\w*""")))
    )
    private val speed=rx("""\b(instant|immediat|rapid|fast|quick|speed)\w*""")
    private val precision=rx("""\b(precis|exact|flawless|perfect|accurat|clear)\w*""")
    private val adaptation=rx("""\b(adapt|overcome|obstacle|invent|create|solution|missing)\w*""")
    private val continuity=rx("""\b(permanent|continu|maintain|sustain|consistent|always|automat)\w*""")
    private val comprehensive=rx("""\b(every|each|all|any|limitless|omnipotent|absolute)\w*""")
    private val heading=rx("""^\s*(?:\d+\.\s*)?(?:I|My|You(?:/Your)?)(?:\s*[-—–]\s*Converted)?\s*$""")
    private fun usefulLines(text:String):List<String> =
        text.lines().filter{it.isNotBlank() && !heading.matches(it.trim())}
    private fun score(lines:List<String>,theme:Theme):Int {
        val combined=lines.joinToString("\n")
        return theme.patterns.count{it.containsMatchIn(combined)}*10+
            lines.count{line->theme.patterns.any{it.containsMatchIn(line)}}.coerceAtMost(9)
    }
    private fun pick(lines:List<String>):Theme {
        val ranked=categories.map{it to score(lines,it)}.sortedByDescending{it.second}
        return if(ranked.first().second>=10)ranked.first().first else Theme(
            "Unified Intention",
            "organize the specific outcomes, abilities and qualities described by the chosen affirmations into a coherent, integrated field objective",
            "interpretation of the selected outcomes, recognition of necessary conditions, coordinated pathways, consistency and refinement",
            emptyList()
        )
    }
    fun compose(affirmations:String):Analysis {
        require(affirmations.isNotBlank()){"Paste or import affirmations first"}
        val lines=usefulLines(affirmations)
        require(lines.isNotEmpty()){"No affirmation statements found"}
        val theme=pick(lines)
        val all=lines.joinToString("\n")
        val details=buildList{
            if(speed.containsMatchIn(all))add("speed and immediate responsiveness")
            if(precision.containsMatchIn(all))add("exact interpretation and precision")
            if(adaptation.containsMatchIn(all))add("adaptive pathways and solution creation")
            if(continuity.containsMatchIn(all))add("continuous reinforcement and lasting integration")
            if(comprehensive.containsMatchIn(all))add("the complete scope of every selected intention")
        }
        val suffix=if(details.isEmpty())"" else " Prioritize "+details.joinToString(", ")+"."
        val objective="Establish an integrated "+theme.name.lowercase()+" field intended to "+
            theme.goal.removeSuffix(".")+"."+suffix
        val instructionSections=listOf(
            "1. IDENTITY AND ACTIVATION — Establish one coherent field specification dedicated to these affirmations. Preserve the exact original wording, recognize each selected intention separately, and coordinate every intention through one integrated architecture.",
            "2. INTERPRETATION — Determine the complete intended meaning of every affirmation regardless of its I, My, You or Your grammatical form. Identify its desired result, purpose, relevant context and all stated conditions without changing or weakening the original intention.",
            "3. ATTRACTOR — Define each intended outcome as its governing destination. Organize the entire set into a coherent result architecture that maintains distinct goals, dependencies, and all explicitly intended qualities.",
            "4. PATHWAYS AND REQUIREMENTS — Identify the prerequisites, skills, resources, conditions, mechanisms and pathways associated with each intention. Design the operating structure around "+theme.method+". Explore alternatives and invent missing strategies where the specifications require them.",
            "5. COHERENCE — Align the complete information structure around each exact intention. Coordinate overlapping objectives, resolve competing requirements, and maintain precision between the desired result, supporting pathway and operating rules.",
            "6. FIELD CONNECTION — Associate each chosen affirmation, its desired reality, necessary requirements and supporting pathways with the field identity. Maintain these relationships throughout the complete encoded pattern.",
            "7. EXECUTION AND OPTIMIZATION — Describe the intended operations for interpretation, strategy selection, execution, assessment and adaptation. Optimize precision and effectiveness, identify and overcome obstacles, and refine selected pathways as conditions change.",
            "8. MEMORY AND INTEGRATION — Preserve the entire field specification and every original affirmation without alteration. Reinforce the complete architecture through full, repeatable information cycles; sustain the intended results and accommodate subsequently chosen affirmations under the same governing rules."
        )
        val instructions=instructionSections.joinToString("\n\n")
        val total=objective.toByteArray(Charsets.UTF_8).size+
            instructions.toByteArray(Charsets.UTF_8).size+
            affirmations.toByteArray(Charsets.UTF_8).size
        require(total<=16384){"Affirmations plus generated instructions exceed the 16 KiB field capacity"}
        require(objective.toByteArray(Charsets.UTF_8).size<=4096)
        require(instructions.toByteArray(Charsets.UTF_8).size<=8192)
        return Analysis(objective,instructions,theme.name,lines.size,
            "Theme: "+theme.name+" · "+lines.size+" statements · "+
            if(details.isEmpty())"standard operating rules" else details.joinToString(" · "))
    }
}
