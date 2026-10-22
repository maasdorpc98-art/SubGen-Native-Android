package com.subgen.nativeapp

import java.security.MessageDigest
import java.util.Locale

object HyperAnalysis {
    const val VERSION="3.2.0"
    fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    data class Cue(val id:String,val phrase:String,val kind:String="UNIVERSAL",val sessionId:String="",
        val function:String="",val experience:String="",val expected:Int?=null,
        val preferred:List<String> = emptyList(),val authorized:Boolean=true,val ruleVersion:String="2.4")
    data class Tier(val throughSeconds:Double,val entry:Int,val deepen:Int)
    data class Policy(val severity:String="WARNING",val allowException:Boolean=true)
    data class Rules(val version:String,val digest:String,val cues:List<Cue>,val tiers:List<Tier>,
        val sleepEntry:Int,val sleepDeepen:Int,val clusterSeconds:Double,val gapSeconds:Double,
        val longPauseSeconds:Double,val objectiveGapSeconds:Double,val foundationCycles:Int,val minimumDeepeners:Int,
        val sectionPatterns:Map<String,String>,val policies:Map<String,Policy>,
        val modeRequirements:Map<String,List<String>>,val endingCues:Map<String,String>) {
        fun minimum(seconds:Double,sleep:Boolean):Pair<Int,Int> = if(sleep) sleepEntry to sleepDeepen else
            tiers.first{seconds<=it.throughSeconds}.let{it.entry to it.deepen}
    }
    /** Independent dimensions allow e.g. LOOP + OPEN + BODY. Unknown remains renderable. */
    data class Mode(val architecture:String="UNRESOLVED",val eyes:String="UNRESOLVED",val ending:String="UNRESOLVED",
        val focus:Set<String> = emptySet(),val familiarity:String="UNRESOLVED",val source:String="UNRESOLVED")
    data class Settings(val speed:Float=VoiceSpeed.DEFAULT,val breathe:Double=5.0,val paragraph:Double=3.0,
        val direction:VoiceDirection.Config=VoiceDirection.Config())
    data class Passage(val index:Int,val text:String,val source:ScriptSourceMap.Span,val offsets:List<Int>,
        val at:Double,val seconds:Double,val section:String,val phase:String,val profile:String,
        val speed:Float,val gainDb:Double,val settingSource:String,val role:String)
    data class Pause(val at:Double,val seconds:Double,val source:ScriptSourceMap.Span,val phase:String,val code:String)
    data class Event(val at:Double,val kind:String,val detail:String,val source:ScriptSourceMap.Span)
    data class Occurrence(val id:String,val cue:Cue,val source:ScriptSourceMap.Span,val fragments:List<ScriptSourceMap.Span>,
        val at:Double,val section:String,val phase:String,val before:String,val spoken:String,val after:String)
    data class Finding(val rule:String,val severity:String,val message:String,val source:ScriptSourceMap.Span?,val allowException:Boolean)
    data class Result(val scriptHash:String,val rules:Rules,val mode:Mode,val cues:List<Cue>,val passages:List<Passage>,
        val pauses:List<Pause>,val events:List<Event>,val occurrences:List<Occurrence>,val findings:List<Finding>,
        val words:Int,val speechSeconds:Double,val silenceSeconds:Double,val totalSeconds:Double,
        val lowerSeconds:Double,val upperSeconds:Double,val firstResponse:ScriptSourceMap.Span?) {
        val technicalErrors get()=findings.count{it.severity=="ERROR"}
        val warnings get()=findings.count{it.severity=="WARNING"}
    }
    private fun role(section:String,rules:Rules):String=rules.sectionPatterns.entries.firstOrNull{
        Regex(it.value,RegexOption.IGNORE_CASE).containsMatchIn(section)
    }?.key?:"UNRESOLVED"
    fun infer(entries:List<ScriptSourceMap.Entry>,selected:Mode?):Mode {
        val spoken=entries.mapNotNull{(it.node as? ScriptNode.Speech)?.text}.joinToString(" ")
        val labels=entries.mapNotNull{(it.node as? ScriptNode.Section)?.name}.joinToString(" ")
        fun has(p:String)=Regex(p,RegexOption.IGNORE_CASE).containsMatchIn("$labels $spoken")
        val explicitLoop=Regex("CONTINUOUS[ _-]+(?:EYES[ _-]+(?:OPEN|CLOSED)[ _-]+)?LOOP|LOOP STABILIZATION|LOOP JUNCTION",RegexOption.IGNORE_CASE).containsMatchIn(labels)
        val sleep=entries.any{(it.node as? ScriptNode.Speech)?.acousticPhase=="SLEEP"} || has("\\bsleep when ready\\b")
        val wake=entries.any{(it.node as? ScriptNode.Speech)?.acousticPhase=="WAKE"} || has("\\bhere and now\\b")
        val architecture=when{explicitLoop->"LOOP";Regex("FOUNDATION|TRIGGER INSTALLATION",RegexOption.IGNORE_CASE).containsMatchIn(labels)->"FOUNDATION";
            Regex("RAPID INDUCTION|BOOSTER",RegexOption.IGNORE_CASE).containsMatchIn(labels)->"BOOSTER";sleep&&!wake->"SLEEP";wake&&!sleep->"DEEP";else->"UNRESOLVED"}
        val ending=when{explicitLoop->"LOOP";sleep&&!wake->"SLEEP";wake&&!sleep->"WAKE";else->"UNRESOLVED"}
        val open=has("eyes[ -]open|keep your eyes open");val closed=has("eyes[ -]closed")
        val inferred=Mode(architecture,when{open&&!closed->"OPEN";closed&&!open->"CLOSED";else->"UNRESOLVED"},ending,
            buildSet{if(Regex("BODY|SOMATIC",RegexOption.IGNORE_CASE).containsMatchIn(labels))add("BODY");
                if(Regex("MIND|COGNITIVE",RegexOption.IGNORE_CASE).containsMatchIn(labels))add("MIND");
                if(Regex("AMPLIFIER|AFFIRMATION",RegexOption.IGNORE_CASE).containsMatchIn(labels))add("AMPLIFIER")},source="SCRIPT EVIDENCE")
        if(selected==null)return inferred
        return Mode(selected.architecture.takeUnless{it=="UNRESOLVED"}?:inferred.architecture,
            selected.eyes.takeUnless{it=="UNRESOLVED"}?:inferred.eyes,
            selected.ending.takeUnless{it=="UNRESOLVED"}?:inferred.ending,
            selected.focus.ifEmpty{inferred.focus},selected.familiarity,"USER SELECTION + SCRIPT EVIDENCE")
    }
    fun analyze(source:String,rules:Rules,settings:Settings=Settings(),selected:Mode?=null,
        sessionCues:List<Cue> = emptyList(),contextWords:Int=120,markedFirstResponse:Int?=null):Result {
        val doc=ScriptSourceMap.parse(source,settings.breathe,settings.paragraph)
        val mode=infer(doc.entries,selected)
        val cues=(rules.cues+sessionCues.filter{it.authorized&&it.kind=="SESSION"&&it.sessionId.isNotBlank()}).toList()
        require(cues.map{it.phrase.lowercase(Locale.ROOT)}.distinct().size==cues.size){"Cue phrases must be unique; universal cues cannot be reclassified"}
        val passages=mutableListOf<Passage>();val pauses=mutableListOf<Pause>();val events=mutableListOf<Event>();val issues=mutableListOf<Finding>()
        var at=0.0;var speech=0.0;var silence=0.0;var words=0;var phase="GLOBAL";var explicitMode=false
        fun finding(id:String,message:String,span:ScriptSourceMap.Span?=null,error:Boolean=false){
            val p=rules.policies[id]?:Policy()
            issues+=Finding(id,if(error)"ERROR" else p.severity,message,span,!error&&p.allowException)
        }
        // Spoken windows are separated by ALL pauses, including explicit zero-second pause codes.
        val windows=mutableListOf<MutableList<Passage>>();var window=mutableListOf<Passage>()
        fun closeWindow(){if(window.isNotEmpty())windows+=window;window=mutableListOf()}
        doc.entries.forEachIndexed{index,e->when(val n=e.node){
            is ScriptNode.Speech->{
                val count=n.text.split(Regex("\\s+")).count{it.isNotBlank()}
                val resolved=runCatching{VoiceDirection.resolve(n,settings.speed,settings.direction)}
                val r=resolved.getOrNull()
                if(r==null)finding("ACOUSTIC_ERROR",resolved.exceptionOrNull()?.message?:"Invalid acoustic settings",e.source,true)
                val speed=r?.speed?:settings.speed
                val seconds=count*60.0/(145.0*speed)
                phase=r?.section?.ifEmpty{"GLOBAL"}?:"GLOBAL"
                val profile=n.scriptProfile?:if(settings.direction.enabled)settings.direction.profile else "NEUTRAL"
                val p=Passage(index,n.text,e.source,e.offsets,at,seconds,n.section,phase,profile,speed,r?.gainDb?:0.0,
                    "profile="+(if(n.scriptProfile!=null)"SCRIPT" else "UI/DEFAULT")+
                    "; tempo="+(if(n.tempo!=null)"SCRIPT INLINE" else if(n.delivery!="NORMAL")"LEGACY ${n.delivery}" else "PROFILE/DEFAULT")+
                    "; gain="+(if(n.gainDb!=null)"SCRIPT INLINE" else "PROFILE/DEFAULT")+
                    (if(settings.direction.enabled)" + UI GLOBAL GAIN" else ""),role(n.section,rules))
                passages+=p;window+=p;at+=seconds;speech+=seconds;words+=count
            }
            is ScriptNode.Pause->{closeWindow();pauses+=Pause(at,n.seconds,e.source,phase,source.substring(e.source.start,e.source.end));at+=n.seconds;silence+=n.seconds}
            is ScriptNode.Unknown->{closeWindow();finding("PARSER_ERROR","Unsupported production/authoring code: ${n.command}",e.source,true)}
            is ScriptNode.Section->{events+=Event(at,"SECTION",n.name,e.source)
                if(!explicitMode&&listOf("ARRIVAL","INDUCTION","DEEPENER","FRACTIONATION","SUGGESTION","REINFORCEMENT","INTEGRATION","WAKE","SLEEP","VOID","DEEP TRANCE").any{n.name.uppercase().startsWith(it)})phase=VoiceDirection.normalizeSection(n.name).ifEmpty{"GLOBAL"}}
            is ScriptNode.ProfileChange->{explicitMode=true;events+=Event(at,"PROFILE",n.name,e.source);phase=doc.entries.take(index+1).mapNotNull{it.node as? ScriptNode.PhaseChange}.lastOrNull()?.name?.takeUnless{it=="RESET"}?:"GLOBAL"}
            is ScriptNode.PhaseChange->{explicitMode=true;phase=if(n.name=="RESET")"GLOBAL" else n.name;events+=Event(at,"PHASE",n.name,e.source)}
            is ScriptNode.DeliveryChange->events+=Event(at,"LEGACY",n.mode,e.source)
        }}
        closeWindow()
        if(passages.isEmpty())finding("NO_SPEECH","No spoken narration",error=true)
        // Also expose tempo/volume reset tokens, which intentionally do not add render nodes.
        doc.controls.filter{Regex("^\\[(?:tempo|volume)\\s+",RegexOption.IGNORE_CASE).containsMatchIn(it.token)}.forEach { control->
            val relevant=passages.firstOrNull{it.source.start>=control.source.end}
            events+=Event(relevant?.at?:at,"INLINE SETTING",control.token,control.source)
        }
        val hits=mutableListOf<Occurrence>()
        for(group in windows){
            val text=StringBuilder();val offsets=mutableListOf<Int>();val owners=mutableListOf<Passage>()
            group.forEach{p->if(text.isNotEmpty()){text.append(' ');offsets+=-1;owners+=p};text.append(p.text);offsets+=p.offsets;repeat(p.text.length){owners+=p}}
            val all=text.toString();val candidates=mutableListOf<Triple<Int,Int,Cue>>()
            cues.forEach{cue->
                val body=cue.phrase.trim().split(Regex("\\s+")).joinToString("\\s+"){Regex.escape(it)}
                Regex("(?<![\\p{L}\\p{N}\\p{M}_])(?:$body)(?![\\p{L}\\p{N}\\p{M}_])",RegexOption.IGNORE_CASE)
                    .findAll(all).forEach{candidates+=Triple(it.range.first,it.range.last+1,cue)}
            }
            var usedThrough=-1
            candidates.sortedWith(compareBy<Triple<Int,Int,Cue>>{it.first}.thenByDescending{it.second-it.first}).forEach{(start,end,cue)->
                if(start>=usedThrough){
                    usedThrough=end
                    val p=owners[start];val pts=offsets.subList(start,end).filter{it>=0}
                    if(pts.isNotEmpty()){
                        val span=ScriptSourceMap.position(source,pts.first(),pts.last()+1)
                        val fragments=mutableListOf<ScriptSourceMap.Span>();var first=pts.first();var last=first
                        pts.drop(1).forEach{v->if(v!=last+1){fragments+=ScriptSourceMap.position(source,first,last+1);first=v};last=v}
                        fragments+=ScriptSourceMap.position(source,first,last+1)
                        val ownerStart=owners.indexOfFirst{it.index==p.index}
                        val prefix=all.substring(ownerStart,start).trim()
                        val fraction=if(prefix.isEmpty())0 else prefix.split(Regex("\\s+")).size
                        val timestamp=p.at+fraction*60.0/(145.0*p.speed)
                        val before=all.substring(0,start).trim().split(Regex("\\s+")).filter{it.isNotEmpty()}.takeLast(contextWords.coerceIn(100,250)).joinToString(" ")
                        val after=all.substring(end).trim().split(Regex("\\s+")).filter{it.isNotEmpty()}.take(contextWords.coerceIn(100,250)).joinToString(" ")
                        hits+=Occurrence("${cue.id}:${span.start}",cue,span,fragments,timestamp,p.section,p.phase,before,all.substring(start,end),after)
                    }
                }
            }
        }
        val fullText=passages.joinToString(" "){it.text}
        val fullOffsets=mutableListOf<Int>()
        passages.forEach{p->if(fullOffsets.isNotEmpty())fullOffsets+=-1;fullOffsets+=p.offsets}
        val textIndices=HashMap<Int,Int>();fullOffsets.forEachIndexed{i,off->if(off>=0)textIndices[off]=i}
        val sorted=hits.sortedBy{it.at}.map{hit->
            val start=textIndices.getValue(hit.source.start);val end=textIndices.getValue(hit.source.end-1)+1
            hit.copy(before=fullText.substring(0,start).trim().split(Regex("\\s+")).filter{it.isNotEmpty()}.takeLast(contextWords.coerceIn(100,250)).joinToString(" "),
                after=fullText.substring(end).trim().split(Regex("\\s+")).filter{it.isNotEmpty()}.take(contextWords.coerceIn(100,250)).joinToString(" "))
        }
        val min=rules.minimum(at,mode.ending=="SLEEP")
        listOf("ENTRY" to min.first,"DEEPEN" to min.second).forEach{(id,required)->
            val n=sorted.count{it.cue.id==id};if(n<required)finding("CUE_FREQUENCY","$id: $n spoken occurrences; HYPER ${rules.version} requests $required for this estimated runtime.")
        }
        val lower=silence+speech*.75;val upper=silence+speech*1.30
        if(mode.ending!="SLEEP"&&rules.tiers.dropLast(1).any{it.throughSeconds in lower..upper})
            finding("TIER_UNCERTAINTY","Estimated runtime range crosses a cue-frequency tier. Reassess with measured WAV length.")
        cues.forEach{cue->
            val own=sorted.filter{it.cue.id==cue.id}
            if(cue.expected!=null&&own.size<cue.expected)finding("SESSION_FREQUENCY","${cue.phrase}: ${own.size}; expected ${cue.expected}.")
            own.zipWithNext().filter{it.second.at-it.first.at<rules.clusterSeconds}.forEach{(_,b)->finding("CUE_CLUSTER","${cue.phrase}: nearby repetitions within ${rules.clusterSeconds}s; editorial inspection required.",b.source)}
            own.filter{cue.preferred.isNotEmpty()&&it.phase !in cue.preferred&&it.section !in cue.preferred}.forEach{
                finding("CUE_PHASE","${cue.phrase}: outside registered preferred phases/sections.",it.source)}
        }
        val reinforcement=sorted.filter{it.cue.id in setOf("ENTRY","DEEPEN")}
        (listOf(0.0)+reinforcement.map{it.at}+at).zipWithNext().filter{it.second-it.first>rules.gapSeconds}.forEach{gap->
            finding("CUE_GAP","${ScriptTimeline.time(gap.second-gap.first)} without an entry/deepening cue near ${ScriptTimeline.time(gap.first)}.",passages.firstOrNull{it.at>=gap.first}?.source)
        }
        val firstResponse=markedFirstResponse?.let{off->passages.firstOrNull{off in it.source.start until it.source.end}?.source}
            ?:passages.firstOrNull{it.role=="FIRST_RESPONSE"}?.source
        if(firstResponse==null)finding("FIRST_RESPONSE","First-response editorial review required.")
        else {
            val firstCue=sorted.firstOrNull{it.cue.id=="ENTRY"}
            events+=Event(passages.first{it.source.start==firstResponse.start}.at,"FIRST RESPONSE",
                "Structurally detected/reviewer marked; first entry cue at "+(firstCue?.let{ScriptTimeline.time(it.at)}?:"unresolved"),firstResponse)
            val objective=passages.firstOrNull{it.role=="OBJECTIVE"}
            if(objective!=null&&firstResponse.start>objective.source.start)finding("FIRST_RESPONSE_ORDER","First response appears after objective work begins.",firstResponse)
        }
        val roles=passages.map{it.role}.toSet()
        rules.modeRequirements[mode.architecture].orEmpty().filter{it !in roles}.forEach{r->finding("STRUCTURE","No labelled $r passage detected. Editorial review may identify unlabelled structure.")}
        val deepeners=events.filter{it.kind=="SECTION"&&role(it.detail,rules)=="DEEPENER"}
        if(mode.architecture in setOf("FOUNDATION","DEEP")&&mode.ending!="SLEEP"&&deepeners.size<rules.minimumDeepeners)
            finding("DISTINCT_DEEPENERS","${deepeners.size} labelled deepener passages; ${rules.minimumDeepeners} requested. Labels still require editorial distinction review.")
        val objective=passages.firstOrNull{it.role=="OBJECTIVE"}
        if(objective!=null){
            val renewed=passages.filter{it.at>=objective.at&&it.role in setOf("RENEWED_DEEPENING","DEEPENER")}
            if(renewed.isEmpty()&&at-objective.at>rules.objectiveGapSeconds)finding("RENEWED_DEPTH","Long objective work without labelled renewed deepening.",objective.source)
        }
        val fractionation=passages.filter{it.role=="FRACTIONATION"||it.phase=="FRACTIONATION"}.distinctBy{it.section.ifEmpty{it.source.start.toString()}}
        if(mode.architecture=="FOUNDATION"&&mode.ending!="SLEEP"&&fractionation.size<rules.foundationCycles)
            finding("FRACTIONATION","Foundation rule requests ${rules.foundationCycles} separately reviewed fractionation cycles; ${fractionation.size} labelled cycles found.")
        fractionation.forEach{p->finding("FRACTIONATION_REVIEW","Review outward shift → contrast → renewed inward absorption; a label does not verify the cycle.",p.source)}
        if(mode.ending=="SLEEP"&&fractionation.isNotEmpty())finding("SLEEP_FRACTIONATION","Review possible waking fractionation in natural-sleep architecture.",fractionation.first().source)
        if(mode.architecture=="FOUNDATION"&&fractionation.size>=2){
            val a=fractionation[0];val b=fractionation[1]
            if(passages.none{it.at>a.at&&it.at<b.at&&it.role in setOf("DEEPENER","RENEWED_DEEPENING")})
                finding("FRACTIONATION_SPACING","No labelled deepening between the first two fractionation cycles.",b.source)
        }
        val expectedEnd=rules.endingCues[mode.ending]
        if(expectedEnd!=null&&sorted.none{it.cue.id==expectedEnd})finding("ENDING_CUE","No $expectedEnd ending cue found. Review the selected ${mode.ending} ending.")
        sorted.filter{it.cue.id in setOf("WAKE","SLEEP")}.forEach{h->
            if((mode.ending=="LOOP")||(mode.ending=="WAKE"&&h.cue.id=="SLEEP")||(mode.ending=="SLEEP"&&h.cue.id=="WAKE"))
                finding("ENDING_CONFLICT","${h.cue.phrase} may conflict with the ${mode.ending} ending.",h.source)
            if(h.cue.id=="WAKE"&&h.at<at*.8)finding("INTERMEDIATE_WAKE","Complete-waking cue before the final part of narration. Intermediate fractionation is not final waking.",h.source)
        }
        if(mode.ending=="LOOP"){
            val hazards=listOf("\\bwelcome\\b" to "Welcome/introduction", "session (?:is |has )?(?:finished|ended|complete)" to "Finished-session statement",
                "return to (?:your )?baseline" to "Return to baseline", "(?:wake up|fully awake|open your eyes)" to "Possible waking instruction",
                "(?:stand up|raise your arm|hold your breath)" to "Physical exercise; check resolution", "(?:five[,. ]+four[,. ]+three|5[,. ]+4[,. ]+3)" to "Possible waking countdown")
            passages.forEach{p->hazards.forEach{(pattern,label)->if(Regex(pattern,RegexOption.IGNORE_CASE).containsMatchIn(p.text))finding("LOOP_LANGUAGE",label+" — contextual review required.",p.source)}}
            val last=passages.lastOrNull()
            if(last!=null&&!Regex("[.!?…][\"'’”)]*$").containsMatchIn(last.text))finding("LOOP_TERMINAL","Possible incomplete terminal sentence; inspect the narrative junction.",last.source)
        }
        if(mode.eyes=="OPEN")passages.filter{Regex("(?:close|shut) your eyes",RegexOption.IGNORE_CASE).containsMatchIn(it.text)}.forEach{
            finding("EYES_CONFLICT","Eye-closing instruction in an eyes-open mode; inspect context.",it.source)}
        pauses.filter{it.seconds>rules.longPauseSeconds}.forEach{finding("LONG_PAUSE","${it.seconds}s programmed pause; review its purpose.",it.source)}
        pauses.zipWithNext().filter{(a,b)->b.at-a.at-a.seconds<2.0}.forEach{(_,b)->finding("PAUSE_CLUSTER","Nearby programmed pauses; inspect whether intentional.",b.source)}
        if(mode.architecture=="UNRESOLVED"||mode.ending=="UNRESOLVED")finding("MODE_UNCERTAIN","Recording classification unresolved. Ordinary audio creation remains available.")
        return Result(hash(source),rules,mode,cues,passages.toList(),pauses.toList(),events.sortedBy{it.at},sorted,issues.toList(),words,speech,silence,at,lower,upper,firstResponse)
    }
}
