package com.subgen.nativeapp

import kotlin.math.roundToLong
import java.util.Locale

/** One parser for preview, summary and render. Speech figures remain predictions. */
object ScriptTimeline {
    data class Row(val seconds:Double,val kind:String,val detail:String)
    data class Summary(val speechBlocks:Int,val pauseBlocks:Int,val sections:Int,
                       val spokenSeconds:Double,val silenceSeconds:Double,
                       val totalSeconds:Double,val words:Int,val markers:List<String>,
                       val rows:List<Row>, val lowerSeconds:Double=0.0,
                       val upperSeconds:Double=0.0, val invalidCommands:Int=0)
    fun analyze(script:String,speed:Float=VoiceSpeed.DEFAULT,breathe:Double=5.0,paragraph:Double=3.0,
                direction:VoiceDirection.Config=VoiceDirection.Config()):Summary {
        val nodes=ScriptParser.parse(script,breathe,paragraph,maxSpeechChars=160)
        var at=0.0;var silence=0.0;var speech=0.0;var spoken=0;var pauses=0;var sections=0;var words=0;var invalid=0
        val markers=mutableListOf<String>();val rows=mutableListOf<Row>()
        nodes.forEach{node-> when(node){
            is ScriptNode.Section->{sections++;markers+=node.name;rows+=Row(at,"SECTION",node.name)}
            is ScriptNode.Speech->{
                spoken++
                val count=node.text.split(Regex("\\s+")).count{it.isNotBlank()}
                words+=count
                val resolved=VoiceDirection.resolve(node,speed,direction)
                // 145 WPM at 1x is a heuristic; neural PCM remains authoritative.
                val seconds=count*60.0/(145.0*resolved.speed.toDouble())
                rows+=Row(at,"SPEECH",String.format(Locale.US,"%s · %.2fx · %+.1f dB · %s",
                    node.delivery,resolved.speed,resolved.gainDb,
                    (if(node.scriptProfile!=null)"profile="+node.scriptProfile+" · " else "")+
                    (if(node.explicitPhaseMode)"phase="+(node.acousticPhase?:"GLOBAL") else "legacy phase="+resolved.section)+
                    " · "+node.text.take(88)))
                speech+=seconds;at+=seconds
            }
            is ScriptNode.Pause->{
                pauses++
                val seconds=node.seconds
                rows+=Row(at,"PAUSE",String.format(Locale.US,"%.3f seconds exact digital silence",seconds))
                silence+=seconds;at+=seconds
            }
            is ScriptNode.DeliveryChange->rows+=Row(at,"DELIVERY",node.mode)
            is ScriptNode.ProfileChange->rows+=Row(at,"PROFILE",node.name)
            is ScriptNode.PhaseChange->rows+=Row(at,"ACOUSTIC PHASE",node.name)
            is ScriptNode.Unknown->{invalid++;rows+=Row(at,"ERROR","Invalid production or HYPER planner code: "+node.command.take(90))}
        }}
        return Summary(spoken,pauses,sections,speech,silence,at,words,markers,rows,
            silence+speech*.75,silence+speech*1.30,invalid)
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