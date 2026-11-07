package com.subgen.nativeapp

import java.security.MessageDigest
import java.util.Locale

/**
 * Source-grounded offline field authoring; deterministic lexical/phrase rules,
 * not a generative language model. Original affirmation bytes are unchanged.
 */
object AdaptiveFieldComposer {
    data class Feature(val name:String,val function:String,val operation:String,val count:Int,val example:String)
    data class Analysis(
        val objective:String,val instructions:String,val theme:String,
        val observedLines:Int,val details:String,val coverage:Int,
        val warnings:List<String>,val features:List<Feature>,val fingerprint:String
    )
    private data class Rule(val name:String,val function:String,val operation:String,val pattern:Regex)
    private fun rx(s:String)=Regex(s,RegexOption.IGNORE_CASE)
    private val rules=listOf(
        Rule("Intention Understanding","fully interpret each intention","Determine exact intended meaning, contextual conditions, grammatical perspective and completion criteria.",rx("""\b(understand|interpret|decode|meaning|intention|comprehend|clarity)\w*""")),
        Rule("Intelligence","apply the specified intelligence","Identify all relevant knowledge, reasoning, learning and selection processes.",rx("""\b(intelligen|genius|learn|knowledge|reason|master|foresight|intuition)\w*""")),
        Rule("Prerequisites","identify everything required","Map all requirements, dependencies, resources and conditions required for an outcome.",rx("""\b(require|prerequis|identify|needed|necessary|condition|resource|everything)\w*""")),
        Rule("Pathway Discovery","find effective pathways","Determine possible routes and separate the desired destination from the steps toward it.",rx("""\b(route|path|find|discover|method|approach|strateg|plan)\w*""")),
        Rule("Pathway Invention","create routes where none exist","Record missing paths as explicit requirements and construct alternative route specifications.",rx("""\b(create|invent|generate|build|new|where.none|none.exists|missing.solution)\w*""")),
        Rule("Capability and Solutions","supply missing capacities and solutions","Identify the skills, abilities and solutions named as dependencies in the source.",rx("""\b(abilit|capabilit|skill|solution|power|empower|develop|unlock)\w*""")),
        Rule("Optimal Strategy","choose the best available method","Define the requested criteria for efficiency, timing, suitability and completeness.",rx("""\b(fast|quick|optimal|best|effective|efficient|perfect.route|choose|select)\w*""")),
        Rule("Precise Execution","execute with precision","Preserve exact correspondence between the chosen statement, strategy and intended result.",rx("""\b(execut|precis|accuracy|exact|command|act|apply|flawless)\w*""")),
        Rule("Obstacle Adaptation","overcome every specified obstacle","Identify obstacles, propose alternate strategies and keep the target unchanged.",rx("""\b(obstacle|overcome|adapt|barrier|limitation|challenge|resolv)\w*""")),
        Rule("Self Improvement","continuously improve execution","Define review, error correction and repeated strategy optimization.",rx("""\b(continu|refin|perfect|improv|optim|evolv|progress)\w*""")),
        Rule("Reality and Results","realize the described outcome","Maintain the intended outcome as attractor and record criteria for completion.",rx("""\b(achiev|reality|realiz|manifest|result|success|goal|obtain|possess)\w*""")),
        Rule("Persistence","maintain integration and consistency","Preserve repeatable complete cycles and the intended continuity of results.",rx("""\b(permanen|integrat|maintain|sustain|consisten|forever|always|persist)\w*""")),
        Rule("Physical Performance","develop physical outcomes","Separate strength, endurance, movement and recovery requirements where present.",rx("""\b(strength|muscle|sport|athlet|fitnes|enduran|running|training|mobilit)\w*""")),
        Rule("Financial Performance","develop financial outcomes","Identify the specific business, income, financial and opportunity goals.",rx("""\b(wealth|income|business|sale|profit|money|customer|financial)\w*""")),
        Rule("Relationships","develop interpersonal outcomes","Preserve clear interpersonal intentions and the autonomy of other people.",rx("""\b(relationship|communicat|love|trust|consent|friends|social)\w*"""))
    )
    private val heading=rx("""^\s*(?:\d+\.\s*)?(?:I|My|You(?:/Your)?)(?:\s*[-—–]\s*Converted)?\s*$""")
    private val negative=rx("""\b(never|not|don't|cannot|won't|must not)\b""")
    private val urgency=rx("""\b(instant|immediat|rapid|fast|quick|speed)\w*""")
    private val scope=rx("""\b(omnipotent|unlimited|limitless|absolute|impossible|every|any)\b""")
    private fun hash(s:String)=MessageDigest.getInstance("SHA-256")
        .digest(s.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    fun compose(source:String):Analysis {
        require(source.isNotBlank()){"Enter written affirmations first"}
        val lines=source.lines().map{it.trim()}.filter{it.isNotBlank()&&!heading.matches(it)}
        require(lines.isNotEmpty()){"No affirmation statements found"}
        val base=AutoFieldComposer.compose(source)
        val features=rules.mapNotNull{rule->
            val examples=lines.filter{rule.pattern.containsMatchIn(it)}
            if(examples.isEmpty())null else Feature(rule.name,rule.function,
                rule.operation,examples.size,examples.first().take(115))
        }.sortedWith(compareByDescending<Feature>{it.count}.thenBy{it.name})
        val warnings=buildList {
            if(features.size<3)add("Limited matching: inspect the generated objective before rendering.")
            if(negative.containsMatchIn(source))add("Negative constructions detected; review the intended meaning.")
            if(base.theme=="Unified Intention")add("No specialized theme identified; check the exact objective.")
            if(lines.size>80)add("Large set: only twelve top functions will be summarized.")
        }
        val selection=features.take(12)
        val objective=buildString {
            append(base.objective.removeSuffix(".")).append(".")
            if(selection.isNotEmpty()){
                append(" Coordinate these specific functions: ")
                append(selection.take(9).joinToString("; "){it.function}).append(".")
            }
            if(scope.containsMatchIn(source))append(" Preserve the full scope of the stated intentions.")
            if(urgency.containsMatchIn(source))append(" Preserve the specified speed and responsiveness.")
        }
        val header=listOf(
            "IDENTITY — Maintain one unified field architecture while keeping each original affirmation distinct.",
            "INTERPRETATION — Preserve exact meaning, context, conditions and I/My/You/Your perspective, without rewriting the source.",
            "ATTRACTOR — Represent each intended result as an organizing destination with its explicit completion conditions.",
            "PATHWAYS — Identify prerequisites, missing requirements and alternative methods associated with each result.",
            "COHERENCE — Coordinate objectives and dependencies while preserving the full specificity of every statement.",
            "RESONANCE MODEL — Associate field identity, target and operating pathways within one repeated information pattern.",
            "EXECUTION — Specify the requested strategies, intended precision, corrections and adaptation rules.",
            "MEMORY — Preserve every exact source byte and repeat the complete architecture in verifiable cycles."
        ).joinToString("\n")
        fun buildInstructions(examples:Boolean):String {
            val map=selection.mapIndexed{index,item->
                val suffix=if(examples)" Source: \""+item.example+"\"." else ""
                "FUNCTION "+(index+1)+" — "+item.name.uppercase(Locale.US)+
                    ": "+item.operation+suffix
            }.joinToString("\n")
            return "FIELD OPERATING INSTRUCTIONS\n"+header+
                (if(map.isEmpty())"" else "\n\nSOURCE-GROUNDED FUNCTION MAP\n"+map)+
                "\n\nCOMPLETION — Verify the digital information and identify external results separately."
        }
        val objectiveBytes=objective.toByteArray(Charsets.UTF_8).size
        val inputBytes=source.toByteArray(Charsets.UTF_8).size
        require(objectiveBytes<=4096){"Generated objective exceeds field capacity"}
        fun fits(s:String):Boolean{
            val bytes=s.toByteArray(Charsets.UTF_8).size
            return bytes<=8192 && inputBytes+objectiveBytes+bytes<=16384
        }
        val full=buildInstructions(true)
        val compact=buildInstructions(false)
        val fallback=header+"\nCOMPLETION — Maintain the intended results as a specification."
        val instructions=when{
            fits(full)->full
            fits(compact)->compact
            fits(fallback)->fallback
            else->error("Text exceeds field capacity. Shorten the affirmation set.")
        }
        return Analysis(objective,instructions,base.theme,lines.size,
            base.details+" · "+features.size+" identified functions",
            features.size,warnings,features,hash(source))
    }
}
