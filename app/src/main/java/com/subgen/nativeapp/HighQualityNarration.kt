package com.subgen.nativeapp

import java.io.File
import kotlin.math.*

object HighQualityNarration {
    fun frames(file:File):Long=HighQualityPcm.inspect(file).also{
        require(it.format==3&&it.bits==32&&it.channels==1&&it.rate==24000)
    }.frames
    fun assemble(nodes:List<ScriptNode>,clips:List<File?>,target:File,binaural:Boolean=false,pacingWpm:Int?=null,stop:()->Boolean={false}):Double {
        require(nodes.size==clips.size)
        val count=nodes.indices.sumOf {i->when(val node=nodes[i]){
            is ScriptNode.Speech->frames(clips[i]?:error("Missing HQ voice block"))
            is ScriptNode.Pause->(node.seconds*24000).roundToLong()
            else->0L}}
        val rate=24000
        val pacing=WordPacing.plan(nodes,LongArray(nodes.size){i->if(nodes[i] is ScriptNode.Speech) frames(clips[i]!!) else 0L},rate,pacingWpm)
        val total=count+pacing.totalAddedFrames
        var frame=0L;var priorGain=1.0
        HighQualityPcm.Writer(target,total,rate,2).use { w->
            fun put(value:Double){
                if(frame%8192L==0L&&stop())throw InterruptedException("Narration assembly cancelled")
                if(binaural){w.put(value+sin(2*PI*237*frame/rate)*.008);w.put(value+sin(2*PI*243*frame/rate)*.008)}
                else {w.put(value);w.put(value)};frame++
            }
            nodes.forEachIndexed { i,node->when(node){
                is ScriptNode.Pause->{var n=(node.seconds*rate).roundToLong();while(n-->0)put(0.0)}
                is ScriptNode.Speech->{
                    val gain=10.0.pow((node.gainDb?:0.0)/20.0)
                    HighQualityPcm.Reader(clips[i]!!).use { r->
                        for(f in 0 until r.info.frames){val ramp=(f/192.0).coerceIn(0.0,1.0)
                            put(r.at(f,0)*(priorGain+(gain-priorGain)*ramp))}
                    };priorGain=gain
                    var remaining=pacing.extraFrames[i]
                    while(remaining-->0)put(0.0)
                }
                else->Unit
            }}
            w.finish()
        };check(frame==total);return total.toDouble()/rate
    }
}
