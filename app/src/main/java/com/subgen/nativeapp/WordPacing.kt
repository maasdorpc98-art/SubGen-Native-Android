package com.subgen.nativeapp

import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Optional passage-level words-per-minute pacing.
 * Leaves synthesizer speed and pitch untouched; inserts only additional silence
 * between speech blocks when a requested pace would otherwise be exceeded.
 * Counts subsequent explicit pauses toward the requested inter-passage interval.
 * Cannot accelerate speech already slower than the requested rate.
 */
object WordPacing {
    const val MIN = 50
    const val MAX = 180
    const val DEFAULT = 100

    data class Plan(val extraFrames:LongArray, val totalAddedFrames:Long, val paddedBlocks:Int)

    fun plan(nodes:List<ScriptNode>, clipFrames:LongArray, rate:Int, targetWpm:Int?):Plan {
        require(nodes.size==clipFrames.size)
        require(rate>0)
        val extras=LongArray(nodes.size)
        if(targetWpm==null)return Plan(extras,0L,0)
        require(targetWpm in MIN..MAX){"Words per minute must be $MIN–$MAX"}
        var total=0L
        var padded=0
        for(i in nodes.indices){
            val speech=nodes[i] as? ScriptNode.Speech ?: continue
            require(clipFrames[i]>=0L)
            var pauseFrames=0L
            var nextSpeech=false
            for(j in i+1 until nodes.size){
                when(val next=nodes[j]) {
                    is ScriptNode.Speech->{nextSpeech=true;break}
                    is ScriptNode.Pause->pauseFrames+=(next.seconds*rate).roundToLong()
                    else->Unit
                }
            }
            if(!nextSpeech)continue // Never append extra silence after the final spoken block.
            val words=speech.text.trim().split(Regex("\\s+")).count{it.isNotBlank()}
            val minimumFrames=(words*60.0*rate/targetWpm).roundToLong()
            val extra=max(0L,minimumFrames-clipFrames[i]-pauseFrames)
            extras[i]=extra
            total+=extra
            if(extra>0)padded++
        }
        return Plan(extras,total,padded)
    }
}
