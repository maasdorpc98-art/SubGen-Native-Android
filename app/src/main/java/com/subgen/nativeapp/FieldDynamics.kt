package com.subgen.nativeapp

import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Computational attractor model. This is a real finite-dimensional mathematical
 * vector field, not a demonstrated external morphic influence.
 *
 * Written affirmations determine the attractor target and per-line relationships.
 * A stable, contractive update evolves seven connected state dimensions toward
 * that attractor. Results can be reproduced, inspected and falsified.
 */
object FieldDynamics {
    private val names=listOf("IDENTITY","ATTRACTOR","INTERPRETATION",
        "PATHWAYS","COHERENCE","RESONANCE","MEMORY")
    private val matchers=listOf(
        Regex("""\b(my|i|you|your|chosen|affirmation|intention)\w*""",RegexOption.IGNORE_CASE),
        Regex("""\b(goal|result|reality|manifest|achiev|succeed|success|becom|obtain)\w*""",RegexOption.IGNORE_CASE),
        Regex("""\b(understand|meaning|interpret|intelligen|know|identify|intention|discover)\w*""",RegexOption.IGNORE_CASE),
        Regex("""\b(route|path|method|plan|create|invent|solution|ability|capabilit|strateg|execute|choose)\w*""",RegexOption.IGNORE_CASE),
        Regex("""\b(precis|exact|align|integrat|coheren|correct|perfect|seamless)\w*""",RegexOption.IGNORE_CASE),
        Regex("""\b(resonan|connect|attun|attract|magnet|harmoni|link|influence)\w*""",RegexOption.IGNORE_CASE),
        Regex("""\b(permanen|maintain|sustain|reinforc|continu|repeat|automatic|consisten|ongoing)\w*""",RegexOption.IGNORE_CASE)
    )
    enum class Form{I,MY,YOU_YOUR,OTHER}
    data class LineRole(
        val index:Int,
        val form:Form,
        val primary:String,
        val matches:List<String>,
        val digest:String
    )
    data class Report(
        val target:List<Double>,
        val initialEnergy:Double,
        val finalEnergy:Double,
        val steps:Int,
        val converged:Boolean,
        val lines:List<LineRole>
    ) {
        fun canonical():String = buildString {
            append("DYNAMICS|SEVEN_DIMENSIONAL_CONTRACTIVE_ATTRACTOR|V1\n")
            append("initial_energy=").append("%.9f".format(Locale.US,initialEnergy)).append('\n')
            append("final_energy=").append("%.9f".format(Locale.US,finalEnergy)).append('\n')
            append("steps=").append(steps).append('\n')
            append("converged=").append(converged).append('\n')
            target.forEachIndexed{ i,x -> append("target=").append(names[i])
                .append("|").append("%.6f".format(Locale.US,x)).append('\n') }
            // At most 48 records; all source lines are preserved separately,
            // even when a large affirmation set is supplied.
            lines.take(48).forEach { line->
                append("intent=").append(line.index).append('|')
                    .append(line.form.name).append('|').append(line.primary).append('|')
                    .append(line.matches.joinToString(",")).append('|')
                    .append(line.digest).append('\n')
            }
            append("source_intents=").append(lines.size).append('\n')
            append("record_limit=48\n")
            append("scope=COMPUTATIONAL_MODEL\n")
        }
    }

    private fun hash(value:String)=MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(""){"%02x".format(it)}
    fun derive(affirmations:String, objective:String, instructions:String):Report {
        val lines=affirmations.lines().filter{it.isNotBlank()}
        require(lines.isNotEmpty()){"At least one written affirmation is required"}
        val annotated=lines.mapIndexed{index,line->
            val trimmed=line.trimStart()
            val form=when {
                trimmed.startsWith("My ",true)->Form.MY
                trimmed.startsWith("You ",true)||trimmed.startsWith("Your ",true)->Form.YOU_YOUR
                trimmed.startsWith("I ",true)->Form.I
                else->Form.OTHER
            }
            val roles=matchers.indices.filter{matchers[it].containsMatchIn(line)}.map{names[it]}
            val top=roles.filter{it!="IDENTITY"}.firstOrNull()?:"IDENTITY"
            LineRole(index+1,form,top,roles,hash(line).take(20))
        }
        val allText=listOf(objective,instructions).joinToString("\n")
        val total=annotated.size.toDouble()
        val target=matchers.indices.map{index->
            val evidence=annotated.count{names[index] in it.matches}.toDouble()/total
            val context=if(matchers[index].containsMatchIn(allText))1.0 else 0.0
            (.25 + .55*evidence + .18*context).coerceIn(.25,.98)
        }
        var state=DoubleArray(7)
        fun energy():Double= sqrt(state.indices.sumOf{ k->
            val e=target[k]-state[k]
            e*e
        })
        val start=energy()
        var last=start
        var step=0
        while(step<180 && last>1.0e-8){
            // Residual diffusion on a seven-node ring, plus a locally
            // contractive restoring force. The spectral radius is <= 0.90.
            val residual=DoubleArray(7){target[it]-state[it]}
            state=DoubleArray(7){ i ->
                val left=residual[(i+6)%7]
                val right=residual[(i+1)%7]
                (state[i]+.28*residual[i]+.045*(left+right-2*residual[i]))
                    .coerceIn(0.0,1.0)
            }
            step++
            val current=energy()
            require(current<=last+1e-10){"Unstable field dynamics: energy increased"}
            last=current
        }
        return Report(target,start,last,step,last<1e-6,annotated)
    }
}
