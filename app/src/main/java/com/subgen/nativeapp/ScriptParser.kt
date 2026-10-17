package com.subgen.nativeapp

/** Pure Kotlin; does not depend on Android. All pauses are measured in output samples. */
sealed interface ScriptNode {
    data class Speech(val text: String, val delivery: String): ScriptNode
    data class Pause(val seconds: Double): ScriptNode
    data class Section(val name: String): ScriptNode
    data class Unknown(val command: String): ScriptNode
}

object ScriptParser {
    private val section = Regex("^\\[(?:SECTION\\s*:\\s*|STAGE\\s+)(.+?)\\]$", RegexOption.IGNORE_CASE)
    private val pause = Regex("^\\[PAUSE\\s*:?\\s*(\\d+(?:\\.\\d+)?)\\]$",RegexOption.IGNORE_CASE)
    private val mode = Regex("^\\[(NORMAL|SLOW|SOFT|WHISPER)\\]$",RegexOption.IGNORE_CASE)
    private val command = Regex("^\\[[A-Za-z][^]]*]$")
    private val sentenceEnd = Regex("(?<=[.!?…])\\s+")
    fun parse(script: String, breathe: Double = 5.0, paragraph: Double = 3.0, maxSpeechChars: Int = 440): List<ScriptNode> {
        val out = mutableListOf<ScriptNode>()
        var delivery="NORMAL";val text=StringBuilder()
        fun flush() {
            val str=text.toString().trim().replace(Regex("\\s+"), " ")
            text.clear()
            if(str.isEmpty()) return
            var part=""
            fun push() { if(part.isNotBlank()) out.add(ScriptNode.Speech(part.trim(),delivery)); part="" }
            for(sentence in str.split(sentenceEnd)) {
                if (sentence.length > maxSpeechChars) {
                    push(); var s=sentence.trim()
                    while(s.length > maxSpeechChars) {
                        var cut=s.lastIndexOf(' ',maxSpeechChars)
                        if(cut < maxSpeechChars / 2) cut=maxSpeechChars
                        out.add(ScriptNode.Speech(s.substring(0,cut).trim(),delivery)); s=s.substring(cut).trim()
                    }
                    if(s.isNotEmpty()) part=s
                } else if(part.length + sentence.length + 1 > maxSpeechChars) { push(); part=sentence }
                else part=(part+" "+sentence).trim()
            }
            push()
        }
        for(src in script.replace("\r","").lines()) {
            val line=src.trim();val m=section.matchEntire(line); val p=pause.matchEntire(line)
            when {
                m!=null->{flush();out+=ScriptNode.Section(m.groupValues[1].trim())}
                p!=null->{flush();out+=ScriptNode.Pause(p.groupValues[1].toDouble().coerceIn(0.0,120.0))}
                line.equals("[BREATHE]",true)->{flush();out+=ScriptNode.Pause(breathe.coerceIn(0.0,30.0))}
                line.equals("[PARAGRAPH]",true)->{flush();out+=ScriptNode.Pause(paragraph.coerceIn(0.0,30.0))}
                mode.matches(line)->{flush();delivery=mode.matchEntire(line)!!.groupValues[1].uppercase()}
                line.isBlank()->flush()
                command.matches(line)->{flush();out+=ScriptNode.Unknown(line)}
                else->{if(text.isNotEmpty())text.append(' ');text.append(line)}
            }
        }
        flush();return out
    }
}
