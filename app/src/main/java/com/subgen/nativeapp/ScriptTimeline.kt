package com.subgen.nativeapp

import kotlin.math.roundToLong
import java.util.Locale

/** Exact parser nodes + exact digital silence; speech times are estimates. */
object ScriptTimeline {
    data class Row(val seconds:Double,val kind:String,val detail:String)
    data class Summary(val speechBlocks:Int,val pauseBlocks:Int,val sections:Int,
                       val spokenSeconds:Double,val silenceSeconds:Double,
                       val totalSeconds:Double,val words:Int,val markers:List<String>,
                       val rows:List<Row>)
    fun analyze(script:String,speed:Float=VoiceSpeed.DEFAULT,breathe:Double=5.0,paragraph:Double=3.0):Summary {
        val nodes=ScriptParser.parse(script,breathe,paragraph,maxSpeechChars=160)
        var whenAt=0.0;var silence=0.0;var speech=0.0;var spoken=0;var pauses=0;var sections=0;var words=0
        val markers=mutableListOf<String>();val rows=mutableListOf<Row>()
        nodes.forEach{node->
            when(node){
                is ScriptNode.Section->{sections++;markers+=node.name;rows+=Row(whenAt,"SECTION",node.name)}
                is ScriptNode.Speech->{
                    spoken++
                    val count=node.text.trim().split(Regex("\\s+")).size
                    words+=count
                    val delivery=when(node.delivery){"SLOW"->.82;"SOFT"->.94;"WHISPER"->.90;else->1.0}
                    val seconds=count*60.0/(145.0*speed.coerceIn(.65f,1.2f)*delivery)
                    rows+=Row(whenAt,"SPEECH",node.delivery+" · "+node.text.take(100))
                    speech+=seconds;whenAt+=seconds
                }
                is ScriptNode.Pause->{
                    pauses++
                    val duration=node.seconds.coerceAtLeast(0.0)
                    rows+=Row(whenAt,"PAUSE",String.format(Locale.US,"%.2f seconds exact silence",duration))
                    silence+=duration;whenAt+=duration
                }
                is ScriptNode.Unknown->rows+=Row(whenAt,"NOTICE","Unrecognized tag: "+node.command.take(70))
            }
        }
        return Summary(spoken,pauses,sections,speech,silence,whenAt,words,markers,rows)
    }
    fun time(sec:Double):String{
        val total=sec.coerceAtLeast(0.0).roundToLong()
        val h=total/3600;val m=(total/60)%60;val s=total%60
        return if(h>0)String.format(Locale.US,"%d:%02d:%02d",h,m,s)
            else String.format(Locale.US,"%d:%02d",m,s)
    }
    fun estimatedWavBytes(seconds:Double,rate:Int,channels:Int):Long =
        44L+(seconds.coerceAtLeast(0.0)*rate*channels*2).roundToLong()
}
