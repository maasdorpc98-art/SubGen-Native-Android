package com.subgen.nativeapp

import java.security.MessageDigest
import java.util.Locale

/**
 * Deterministic fictional field-architecture MODEL, not evidence of a real field.
 *
 * Preserves source strings byte-for-byte; only derives named nodes, relationships,
 * text classifications and an audio-synthesis plan. No external AI or network.
 */
object FieldArchitecture {
    enum class Kind(val title:String) {
        IDENTITY("Identity"),
        ATTRACTOR("Attractor"),
        INTERPRETATION("Interpretation"),
        PATHWAYS("Pathways"),
        COHERENCE("Coherence"),
        RESONANCE("Resonance model"),
        MEMORY("Memory")
    }
    data class Node(val kind:Kind, val description:String, val evidence:Int, val intensity:Int)
    data class Edge(val from:Kind,val to:Kind,val purpose:String)
    data class Plan(
        val version:Int,
        val sourceSha256:String,
        val fieldId:String,
        val lines:Int,
        val nodes:List<Node>,
        val edges:List<Edge>,
        val source:MorphicFieldV2.Blueprint
    ) {
        fun canonical():String {
            fun q(s:String):String = buildString(s.length+8) {
                append('"')
                s.forEach { c -> when(c){
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '|' -> append("\\u007c")
                    else -> if(c.code<32)append("\\u%04x".format(Locale.US,c.code)) else append(c)
                } }
                append('"')
            }
            val out=StringBuilder("SUBGEN_FIELD_ARCHITECTURE_V3\n")
            out.append("schema=").append(version).append('\n')
            out.append("field_id=").append(fieldId).append('\n')
            out.append("source_sha256=").append(sourceSha256).append('\n')
            out.append("affirmation_lines=").append(lines).append('\n')
            nodes.forEachIndexed { i,node ->
                out.append("node=").append(i+1).append('|').append(node.kind.name)
                    .append('|').append(node.evidence).append('|').append(node.intensity)
                    .append('|').append(q(node.description)).append('\n')
            }
            edges.forEach { edge->
                out.append("edge=").append(edge.from.name).append("->").append(edge.to.name)
                    .append('|').append(q(edge.purpose)).append('\n')
            }
            out.append("status=EXPERIMENTAL_INFORMATION_MODEL_NOT_VERIFIED_FIELD\n")
            return out.toString()
        }
    }

    private fun sha(text:String):String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    private fun matches(lines:List<String>,regex:Regex)=lines.count{regex.containsMatchIn(it)}
    private val goal=Regex("""\b(manifest|realit|achiev|become|obtain|accomplish|transform|result|possess|have)\w*""",RegexOption.IGNORE_CASE)
    private val understand=Regex("""\b(understand|decode|interpret|know|meaning|intelligen|discover|identify|comprehend)\w*""",RegexOption.IGNORE_CASE)
    private val pathway=Regex("""\b(create|find|route|method|action|execute|solution|overcome|choose|plan|build|develop)\w*""",RegexOption.IGNORE_CASE)
    private val coherence=Regex("""\b(exact|precis|flawless|align|integrat|coheren|correct|according|seamless)\w*""",RegexOption.IGNORE_CASE)
    private val resonance=Regex("""\b(resonan|connect|attun|attract|magnet|influenc|link|harmon)\w*""",RegexOption.IGNORE_CASE)
    private val memory=Regex("""\b(permanen|maintain|sustain|persist|reinforc|continu|repeat|automatic|consisten)\w*""",RegexOption.IGNORE_CASE)

    fun create(source:MorphicFieldV2.Blueprint):Plan {
        source.validated()
        // Do not normalize/trim the original text: SHA and WAV must cover every byte.
        val combined=listOf(source.objective,source.instructions,source.affirmations)
            .joinToString("\u0000") { s->s.toByteArray(Charsets.UTF_8).size.toString()+":"+s }
        val hash=sha(combined)
        val id="SGF-"+hash.take(16).uppercase(Locale.US)
        val lines=source.affirmations.lines().filter{it.isNotBlank()}
        val goalCount=matches(lines,goal)
        val interpretations=matches(lines,understand)
        val pathways=matches(lines,pathway)
        val precision=matches(lines,coherence)
        val links=matches(lines,resonance)
        val loops=matches(lines,memory)
        val objective=source.objective.ifBlank {
            "Represent the intended outcome described by the supplied affirmations."
        }
        fun node(kind:Kind,description:String,count:Int)=
            Node(kind,description,count, (1 + count.coerceAtMost(8)))
        val nodes=listOf(
            node(Kind.IDENTITY,"Stable identifier $id derived from the exact input texts.",lines.size),
            node(Kind.ATTRACTOR,objective,goalCount),
            node(Kind.INTERPRETATION,
                "Interpret the intended meaning of each affirmation; preserve wording as supplied. This is a conceptual instruction, not autonomous understanding.",
                interpretations),
            node(Kind.PATHWAYS,
                "Map each proposed outcome to hypothetical requirements and alternative routes. Real-world actions remain outside the audio engine.",
                pathways),
            node(Kind.COHERENCE,
                "Relate all parts to one objective; preserve exact source texts and signal verification.",
                precision),
            node(Kind.RESONANCE,
                "Symbolic association of the encoded information with its stated target; no proven field-to-reality mechanism.",
                links),
            node(Kind.MEMORY,
                "Repeat the complete informational pattern in verifiable cycles without changing the source.",
                loops)
        )
        val edges=listOf(
            Edge(Kind.IDENTITY,Kind.ATTRACTOR,"Names the intended destination"),
            Edge(Kind.ATTRACTOR,Kind.INTERPRETATION,"Defines what is to be interpreted"),
            Edge(Kind.INTERPRETATION,Kind.PATHWAYS,"Associates intended meaning with conceptual routes"),
            Edge(Kind.PATHWAYS,Kind.COHERENCE,"Tests internal compatibility of the model"),
            Edge(Kind.COHERENCE,Kind.RESONANCE,"Supplies the symbolic target relationship"),
            Edge(Kind.RESONANCE,Kind.MEMORY,"Maintains the association across repeated cycles"),
            Edge(Kind.MEMORY,Kind.IDENTITY,"Closes the conceptual feedback loop")
        )
        return Plan(3,hash,id,lines.size,nodes,edges,source)
    }
}
