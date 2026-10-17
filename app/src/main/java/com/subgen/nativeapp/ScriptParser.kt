package com.subgen.nativeapp

/** Pure Kotlin timeline: authoring directives are never synthesizer input. */
sealed interface ScriptNode {
    data class Speech(val text:String, val delivery:String,
                      val tempo:Double?=null, val gainDb:Double?=null,
                      val section:String="",val scriptProfile:String?=null,
                      val acousticPhase:String?=null, val explicitPhaseMode:Boolean=false,
                      val legacyPhase:String=""):ScriptNode
    data class Pause(val seconds:Double):ScriptNode
    data class Section(val name:String):ScriptNode
    data class DeliveryChange(val mode:String):ScriptNode
    data class ProfileChange(val name:String):ScriptNode
    data class PhaseChange(val name:String):ScriptNode
    data class Unknown(val command:String):ScriptNode
}

/** Tokenize at arbitrary Unicode offsets; never silently swallow malformed controls. */
object ScriptParser {
    const val MAX_PAUSE_SECONDS=3600.0
    // Runtime grammar is self-contained so original parser test can compile alone.
    val PROFILE_CODES=mapOf(
        "GENTLE_GUIDE" to "GENTLE GUIDE", "CONFIDENT_AUTHORITY" to "CONFIDENT AUTHORITY",
        "MASTER_HYPNOTIST" to "MASTER HYPNOTIST",
        "COMMANDING_DRAMATIC" to "COMMANDING / DRAMATIC",
        "INTELLIGENT_INSTRUCTOR" to "INTELLIGENT INSTRUCTOR")
    val ACOUSTIC_PHASES=setOf("ARRIVAL","INDUCTION","DEEPENER","FRACTIONATION",
        "SUGGESTIONS","REINFORCEMENT","INTEGRATION","WAKE","SLEEP")
    private val bracket=Regex("^\\[([^\\]\\n]{0,160})]$")
    private val pause=Regex("^pause\\s*:?\\s*(\\d+(?:\\.\\d+)?)$",RegexOption.IGNORE_CASE)
    private val mode=Regex("^(NORMAL|SLOW|SOFT|WHISPER)$",RegexOption.IGNORE_CASE)
    private val section=Regex("^(?:SECTION\\s*:\\s*|STAGE\\s+)(.+)$",RegexOption.IGNORE_CASE)
    private val tempo=Regex("^tempo\\s+(.+)$",RegexOption.IGNORE_CASE)
    private val volume=Regex("^volume\\s+(.+)$",RegexOption.IGNORE_CASE)
    private val profile=Regex("^profile\\s+(.+)$",RegexOption.IGNORE_CASE)
    private val phase=Regex("^phase\\s+(.+)$",RegexOption.IGNORE_CASE)
    private val realNumber=Regex("^(?:0|[1-9]\\d*)(?:\\.\\d+)?$")
    private val signedDb=Regex("^[+-]?(?:0|[1-9]\\d*)(?:\\.\\d+)?dB$",RegexOption.IGNORE_CASE)
    private val sentenceEnd=Regex("(?<=[.!?…])\\s+")
    private val planner=Regex("^@[A-Za-z][A-Za-z_0-9]*(?:\\([^\\n)]*\\))?")

    fun parse(script:String,breathe:Double=5.0,paragraph:Double=3.0,maxSpeechChars:Int=440):List<ScriptNode> {
        require(maxSpeechChars>=16){"Speech chunk limit must be at least 16"}
        require(breathe.isFinite()&&breathe in 0.0..MAX_PAUSE_SECONDS){"Invalid breathing pause"}
        require(paragraph.isFinite()&&paragraph in 0.0..MAX_PAUSE_SECONDS){"Invalid paragraph pause"}
        val nodes=mutableListOf<ScriptNode>()
        val speech=StringBuilder()
        var delivery="NORMAL"
        var activeTempo:Double?=null
        var activeGain:Double?=null
        var activeSection=""
        var lastLegacyPhase=""
        var scriptProfile:String?=null
        var acousticPhase:String?=null
        var explicitPhaseMode=false
        fun push(text:String) {
            if(text.isNotBlank())nodes+=ScriptNode.Speech(text.trim(),delivery,activeTempo,activeGain,activeSection,
                scriptProfile,acousticPhase,explicitPhaseMode,lastLegacyPhase)
        }
        fun flush() {
            val raw=speech.toString().trim().replace(Regex("\\s+")," ")
            speech.clear()
            if(raw.isEmpty())return
            var pending=""
            fun commit(){push(pending);pending=""}
            for(part in raw.split(sentenceEnd)) {
                if(part.length>maxSpeechChars) {
                    commit()
                    var rest=part
                    while(rest.length>maxSpeechChars) {
                        var at=rest.lastIndexOf(' ',maxSpeechChars)
                        if(at<maxSpeechChars/2)at=maxSpeechChars
                        push(rest.substring(0,at));rest=rest.substring(at).trimStart()
                    }
                    pending=rest
                } else if(pending.isNotEmpty()&&pending.length+part.length+1>maxSpeechChars){commit();pending=part}
                else pending=if(pending.isEmpty())part else "$pending $part"
            }
            commit()
        }
        fun handle(token:String):Boolean {
            val inside=bracket.matchEntire(token)?.groupValues?.get(1)?.trim()?:return false
            val m=mode.matchEntire(inside)
            val s=section.matchEntire(inside)
            val p=pause.matchEntire(inside)
            val t=tempo.matchEntire(inside)
            val v=volume.matchEntire(inside)
            val pr=profile.matchEntire(inside)
            val ph=phase.matchEntire(inside)
            when {
                m!=null->{flush();delivery=m.groupValues[1].uppercase();nodes+=ScriptNode.DeliveryChange(delivery)}
                s!=null->{flush();val declared=s.groupValues[1].trim()
                    if(declared.isEmpty())nodes+=ScriptNode.Unknown(token)
                    else {
                        nodes+=ScriptNode.Section(declared)
                        val code=declared.uppercase()
                        activeSection=declared
                        if(listOf("ARRIVAL","INDUCTION","DEEPENER","FRACTIONATION","SUGGESTION","REINFORCEMENT","INTEGRATION","WAKE","SLEEP","VOID","DEEP TRANCE").any{code.startsWith(it)})
                            lastLegacyPhase=declared // old projects retain known-section mapping only
                    }}
                p!=null->{flush();val value=p.groupValues[1].toDoubleOrNull()
                    if(value==null||!value.isFinite()||value>MAX_PAUSE_SECONDS)nodes+=ScriptNode.Unknown(token)
                    else nodes+=ScriptNode.Pause(value)}
                inside.equals("breathe",true)->{flush();nodes+=ScriptNode.Pause(breathe)}
                inside.equals("paragraph",true)->{flush();nodes+=ScriptNode.Pause(paragraph)}
                t!=null->{flush();val arg=t.groupValues[1].trim()
                    if(arg.equals("reset",true))activeTempo=null
                    else {val value=if(realNumber.matches(arg))arg.toDoubleOrNull() else null
                        if(value==null||!value.isFinite()||value !in 0.65..1.50)nodes+=ScriptNode.Unknown(token)
                        else activeTempo=value}}
                v!=null->{flush();val arg=v.groupValues[1].trim()
                    if(arg.equals("reset",true))activeGain=null
                    else {val value=if(signedDb.matches(arg))arg.dropLast(2).toDoubleOrNull() else null
                        if(value==null||!value.isFinite()||value !in -18.0..6.0)nodes+=ScriptNode.Unknown(token)
                        else activeGain=value}}
                pr!=null->{flush();val name=pr.groupValues[1].trim().uppercase()
                    if(name=="RESET"){
                        scriptProfile=null;explicitPhaseMode=true
                        nodes+=ScriptNode.ProfileChange("RESET")
                    } else {
                        val selected=PROFILE_CODES[name]
                        if(selected==null)nodes+=ScriptNode.Unknown(token)
                        else {scriptProfile=selected;explicitPhaseMode=true
                            nodes+=ScriptNode.ProfileChange(name)}
                    }}
                ph!=null->{flush();val name=ph.groupValues[1].trim().uppercase()
                    if(name=="RESET"){
                        acousticPhase=null;explicitPhaseMode=true
                        nodes+=ScriptNode.PhaseChange("RESET")
                    } else if(name in ACOUSTIC_PHASES){
                        acousticPhase=name;explicitPhaseMode=true
                        nodes+=ScriptNode.PhaseChange(name)
                    } else nodes+=ScriptNode.Unknown(token)}
                // Unknown likely command: never let typos such as [pauze 2] reach Kokoro.
                Regex("^(?:pause|pauze|tempo|volume|profile|phase|gain|speed|delivery|section|stage|breathe|paragraph|slow|soft|whisper|normal)\\b.*",RegexOption.IGNORE_CASE).matches(inside)->{flush();nodes+=ScriptNode.Unknown(token)}
                else->return false // ordinary bracketed narration, e.g. [the door opens]
            }
            return true
        }
        var i=0
        while(i<script.length){
            val c=script[i]
            if(c=='\\'&&i+1<script.length&&script[i+1]=='['){
                // Explicit literal escape. Keep the complete bracket phrase as spoken text.
                speech.append('[');i+=2;continue
            }
            if(c=='['){
                val end=script.indexOf(']',i+1)
                if(end>i && end-i<=162 && handle(script.substring(i,end+1))){i=end+1;continue}
            }
            if(c=='@'&&(i==0||!script[i-1].isLetterOrDigit())){
                val plannerToken=planner.find(script.substring(i))?.takeIf{it.range.first==0}
                if(plannerToken!=null){flush();nodes+=ScriptNode.Unknown(plannerToken.value);i+=plannerToken.value.length;continue}
            }
            // Text remains continuous across ordinary newlines. Exact pauses come from codes.
            if(c=='\n'||c=='\r'||c=='\t'){
                if(speech.isNotEmpty()&&!speech.last().isWhitespace())speech.append(' ')
            } else speech.append(c)
            i++
        }
        flush()
        return nodes
    }
}