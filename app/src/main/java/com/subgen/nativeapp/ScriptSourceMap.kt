package com.subgen.nativeapp

import java.util.Collections

/** Immutable UTF-16 trace produced by the real parser, never reparsed narration. */
object ScriptSourceMap {
    data class Span(val start:Int,val end:Int,val line:Int,val column:Int)
    data class Entry(val node:ScriptNode,val source:Span,val offsets:List<Int>)
    data class Control(val token:String,val source:Span)
    data class Document(val source:String,val entries:List<Entry>,val controls:List<Control>)
    fun position(source:String,start:Int,end:Int):Span {
        val a=start.coerceIn(0,source.length);val b=end.coerceIn(a,source.length)
        var line=1;var column=1;var i=0
        while(i<a){
            if(source[i]=='\r'){line++;column=1;if(i+1<a&&source[i+1]=='\n')i++}
            else if(source[i]=='\n'){line++;column=1}else column++
            i++
        }
        return Span(a,b,line,column)
    }
    fun parse(source:String,breathe:Double=5.0,paragraph:Double=3.0):Document {
        val entries=mutableListOf<Entry>();val controls=mutableListOf<Control>()
        ScriptParser.parse(source,breathe,paragraph,160,onControl={token,start,end->controls+=Control(token,position(source,start,end))}){node,offsets->
            val valid=offsets.filter{it>=0}
            val span=position(source,valid.firstOrNull()?:0,(valid.lastOrNull()?.plus(1))?:0)
            entries+=Entry(node,span,Collections.unmodifiableList(ArrayList(offsets)))
        }
        return Document(source,Collections.unmodifiableList(ArrayList(entries)),Collections.unmodifiableList(ArrayList(controls)))
    }
}
