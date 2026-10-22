package com.subgen.nativeapp

import org.json.JSONArray
import org.json.JSONObject

/** Explicit, versioned data serialization; never used to infer renderer commands. */
object HyperJson {
    fun array(values:Iterable<*>):JSONArray=JSONArray().apply{values.forEach{put(it)}}
    fun canonical(v:Any?):String=when(v){
        null,JSONObject.NULL->"null"
        is JSONObject->v.keys().asSequence().toList().sorted().joinToString(",","{","}"){JSONObject.quote(it)+":"+canonical(v.get(it))}
        is JSONArray->(0 until v.length()).joinToString(",","[","]"){canonical(v.get(it))}
        is String->JSONObject.quote(v)
        else->v.toString()
    }
    private fun strings(a:JSONArray?):List<String> = if(a==null)emptyList()else(0 until a.length()).map{a.getString(it)}
    fun rules(raw:String):HyperAnalysis.Rules {
        val j=JSONObject(raw);require(j.getString("standard")=="HYPER");val version=j.getString("version");require(version.isNotBlank())
        val expected=mapOf("ENTRY" to "Relax Deeply","DEEPEN" to "Relax Your System Deeply","WAKE" to "Here and Now","SLEEP" to "Sleep When Ready")
        val c=j.getJSONArray("universalCues");val cues=(0 until c.length()).map {i->val x=c.getJSONObject(i)
            require(expected[x.getString("id")]==x.getString("phrase")){"Universal HYPER cue definitions must remain canonical"}
            HyperAnalysis.Cue(x.getString("id"),x.getString("phrase"),function=x.optString("function"),ruleVersion=version)}
        require(cues.map{it.id}.toSet()==expected.keys&&cues.size==4)
        val t=j.getJSONArray("frequencyTiers");val tiers=(0 until t.length()).map{i->val x=t.getJSONObject(i)
            HyperAnalysis.Tier(x.getDouble("throughSeconds"),x.getInt("entry"),x.getInt("deepen"))}
        require(tiers.isNotEmpty()&&tiers.all{it.throughSeconds.isFinite()&&it.throughSeconds>0&&it.entry>=0&&it.deepen>=0})
        require(tiers.zipWithNext().all{it.first.throughSeconds<it.second.throughSeconds}&&tiers.last().throughSeconds>=1e10)
        val sleep=j.getJSONObject("naturalSleepMinimum");require(sleep.getInt("entry")>=0&&sleep.getInt("deepen")>=0)
        val thresholds=j.getJSONObject("thresholds")
        fun threshold(key:String):Double=thresholds.getDouble(key).also{require(it.isFinite()&&it>0&&it<=86400)}
        val patterns=j.getJSONObject("sectionPatterns");val mappings=linkedMapOf<String,String>()
        val order=strings(j.getJSONArray("sectionPatternOrder"));require(order.toSet()==patterns.keys().asSequence().toSet())
        order.forEach{key->val p=patterns.getString(key);require(p.length<=500);Regex(p);mappings[key]=p}
        val policy=j.getJSONObject("policies");val policies=policy.keys().asSequence().associateWith{key->val p=policy.getJSONObject(key)
            val severity=p.getString("severity");require(severity in setOf("WARNING","INFORMATION")){"Editorial rules must remain nonblocking"}
            HyperAnalysis.Policy(severity,p.getBoolean("allowException"))}
        val requirements=j.getJSONObject("modeRequirements");val required=requirements.keys().asSequence().associateWith{strings(requirements.getJSONArray(it))}
        val ending=j.getJSONObject("endingCues");val endings=ending.keys().asSequence().associateWith{ending.getString(it)}
        require(endings.values.all{it in expected.keys}&&"LOOP" !in endings){"Loops must not require a final waking/sleep cue"}
        val cycles=thresholds.getInt("foundationCycles");require(cycles in 0..10)
        return HyperAnalysis.Rules(version,HyperAnalysis.hash(canonical(j)),cues,tiers,sleep.getInt("entry"),sleep.getInt("deepen"),
            threshold("clusterSeconds"),threshold("gapSeconds"),threshold("longPauseSeconds"),threshold("objectiveGapSeconds"),cycles,thresholds.getInt("minimumDistinctDeepeners").also{require(it in 0..10)},mappings,policies,required,endings)
    }
    fun span(s:ScriptSourceMap.Span?)=s?.let{JSONObject().put("startUtf16",it.start).put("endUtf16",it.end).put("line",it.line).put("columnUtf16",it.column)}?:JSONObject.NULL
    fun cue(c:HyperAnalysis.Cue)=JSONObject().put("id",c.id).put("phrase",c.phrase).put("kind",c.kind).put("sessionId",c.sessionId)
        .put("function",c.function).put("associatedExperience",c.experience).put("expectedUsage",c.expected?:JSONObject.NULL)
        .put("preferredPhasesOrSections",array(c.preferred)).put("authorized",c.authorized).put("ruleVersion",c.ruleVersion)
    fun cue(j:JSONObject)=HyperAnalysis.Cue(j.getString("id"),j.getString("phrase"),j.optString("kind","SESSION"),j.optString("sessionId"),
        j.optString("function"),j.optString("associatedExperience"),if(j.has("expectedUsage")&&!j.isNull("expectedUsage"))j.getInt("expectedUsage")else null,
        strings(j.optJSONArray("preferredPhasesOrSections")),j.optBoolean("authorized",false),j.optString("ruleVersion","2.4"))
    fun mode(m:HyperAnalysis.Mode)=JSONObject().put("architecture",m.architecture).put("eyes",m.eyes).put("ending",m.ending)
        .put("focus",array(m.focus.sorted())).put("familiarity",m.familiarity).put("source",m.source)
    fun mode(j:JSONObject)=HyperAnalysis.Mode(j.optString("architecture","UNRESOLVED"),j.optString("eyes","UNRESOLVED"),j.optString("ending","UNRESOLVED"),
        strings(j.optJSONArray("focus")).toSet(),j.optString("familiarity","UNRESOLVED"),j.optString("source","USER"))
    fun review(r:HyperGovernance.Review)=JSONObject().put("scope",r.scope).put("status",r.status).put("binding",r.binding).put("note",r.note)
        .put("time",r.time).put("rule",r.rule).put("loopListened",r.loopListened).put("renderId",r.renderId)
    fun review(j:JSONObject)=HyperGovernance.Review(j.getString("scope"),j.getString("status"),j.getString("binding"),j.optString("note"),j.getLong("time"),
        j.optString("rule"),j.optBoolean("loopListened"),j.optString("renderId"))
    fun audio(r:HyperAudioQa.Report)=JSONObject().put("sha256",r.sha256).put("sampleRate",r.rate).put("channels",r.channels).put("bits",r.bits)
        .put("encoding",r.encoding).put("pcmFrames",r.frames).put("measuredDurationSeconds",r.seconds).put("peak",r.peak).put("peakDbfs",r.peakDbfs)
        .put("railClippedSamples",r.railSamples).put("rms",r.rms).put("rmsDbfs",r.rmsDbfs).put("rmsIsNotLufs",true)
        .put("silenceThresholdDbfs",-60).put("detectedSilenceSeconds",r.silentSeconds).put("longestSilenceSeconds",r.longestSilenceSeconds)
        .put("leadingSilenceSeconds",r.leadingSilenceSeconds).put("trailingSilenceSeconds",r.trailingSilenceSeconds)
        .put("seamSampleDelta",r.seamDelta).put("first250msRmsDbfs",r.startRmsDbfs).put("last250msRmsDbfs",r.endRmsDbfs)
        .put("seamRmsDifferenceDb",r.seamRmsDifferenceDb).put("firstWindowZeroCrossings",r.startZeroCrossings).put("lastWindowZeroCrossings",r.endZeroCrossings)
        .put("threeCycleWavBytes",r.threeCycleBytes).put("junctions","A→B and B→C repeat the same original PCM boundary")
        .put("warnings",array(r.warnings)).put("manuallyListened",false)
    fun report(a:HyperAnalysis.Result):JSONObject=JSONObject().put("schemaVersion",1).put("subgenVersion",HyperAnalysis.VERSION).put("scriptSha256",a.scriptHash)
        .put("hyperVersion",a.rules.version).put("rulePackSha256",a.rules.digest).put("recordingMode",mode(a.mode))
        .put("assumptions","145 WPM at 1x; heuristic speech range 75–130%. Structure is an editorial storyboard, not measured trance depth.")
        .put("universalCounts",JSONObject().apply{a.cues.filter{it.kind=="UNIVERSAL"}.forEach{put(it.phrase,a.occurrences.count{x->x.cue.id==it.id})}})
        .put("sessionCounts",JSONObject().apply{a.cues.filter{it.kind=="SESSION"}.forEach{put(it.phrase,a.occurrences.count{x->x.cue.id==it.id})}})
        .put("cueRegistry",array(a.cues.map{cue(it)})).put("occurrences",array(a.occurrences.map{h->JSONObject().put("id",h.id).put("phrase",h.cue.phrase)
            .put("classification",h.cue.kind).put("source",span(h.source)).put("sourceFragments",array(h.fragments.map{span(it)}))
            .put("estimatedSeconds",h.at).put("section",h.section).put("phase",h.phase).put("before",h.before).put("spoken",h.spoken).put("after",h.after)}))
        .put("findings",array(a.findings.map{f->JSONObject().put("ruleId",f.rule).put("severity",f.severity).put("message",f.message)
            .put("source",span(f.source)).put("exceptionAllowed",f.allowException)}))
        .put("structure",array(a.passages.map{p->JSONObject().put("index",p.index).put("source",span(p.source)).put("text",p.text).put("section",p.section)
            .put("phase",p.phase).put("profile",p.profile).put("speed",p.speed.toDouble()).put("gainDb",p.gainDb).put("settingSource",p.settingSource)
            .put("structurallyDetectedRole",p.role).put("estimatedStartSeconds",p.at).put("estimatedDurationSeconds",p.seconds)}))
        .put("firstResponse",span(a.firstResponse)).put("events",array(a.events.map{e->JSONObject().put("estimatedSeconds",e.at).put("kind",e.kind).put("detail",e.detail).put("source",span(e.source))}))
        .put("timing",JSONObject().put("words",a.words).put("baselineWpm",145).put("estimatedSpeechSeconds",a.speechSeconds).put("programmedSilenceSeconds",a.silenceSeconds)
            .put("estimatedTotalSeconds",a.totalSeconds).put("lowerEstimateSeconds",a.lowerSeconds).put("upperEstimateSeconds",a.upperSeconds)
            .put("pauseCount",a.pauses.size).put("longestProgrammedPauseSeconds",a.pauses.maxOfOrNull{it.seconds}?:0.0)
            .put("programmedSilenceFraction",if(a.totalSeconds>0)a.silenceSeconds/a.totalSeconds else 0.0)
            .put("phaseEstimates",JSONObject().apply{a.passages.groupBy{it.phase}.forEach{(phase,ps)->put(phase,JSONObject().put("speechSeconds",ps.sumOf{it.seconds})
                .put("programmedSilenceSeconds",a.pauses.filter{it.phase==phase}.sumOf{it.seconds}))}}))
        .put("programmedPauses",array(a.pauses.map{p->JSONObject().put("estimatedAtSeconds",p.at).put("seconds",p.seconds).put("source",span(p.source)).put("phase",p.phase).put("code",p.code)}))
    fun markdown(j:JSONObject):String=buildString {
        append("# SubGen HYPER production report\n\n")
        append("Script SHA-256: `${j.getString("scriptSha256")}`\n\nHYPER ${j.getString("hyperVersion")} · SubGen ${j.getString("subgenVersion")}\n\n")
        append(j.getString("assumptions")+"\n\n")
        append("Recording: ${j.getJSONObject("recordingMode")}\n\n")
        append("Universal cues: ${j.getJSONObject("universalCounts")}\n\nSession cues: ${j.getJSONObject("sessionCounts")}\n\n")
        val timing=j.getJSONObject("timing")
        append("Estimated playback: ${ScriptTimeline.time(timing.getDouble("estimatedTotalSeconds"))}. Programmed silence: ${timing.getDouble("programmedSilenceSeconds")} seconds.\n\n")
        if(j.has("audioMeasurements")){val a=j.getJSONObject("audioMeasurements");append("Measured WAV: ${a.getLong("pcmFrames")} frames / ${a.getInt("sampleRate")} Hz = ${a.getDouble("measuredDurationSeconds")} s. Peak ${a.getDouble("peakDbfs")} dBFS; RMS ${a.getDouble("rmsDbfs")} dBFS (not LUFS); rail samples ${a.getLong("railClippedSamples")}.\n\n")}
        else append("Audio measurements: pending for these exact materials.\n\n")
        append("Approval states: ${j.optJSONObject("approvalStates")?:"DRAFT"}\n\n")
        append("## Cue locations\n\n")
        val hits=j.getJSONArray("occurrences");for(i in 0 until hits.length()){val h=hits.getJSONObject(i);val s=h.getJSONObject("source")
            append("- ${h.getString("classification")} · ${h.getString("phrase")} · L${s.getInt("line")}:C${s.getInt("columnUtf16")} · ~${ScriptTimeline.time(h.getDouble("estimatedSeconds"))} · ${h.getString("section")} / ${h.getString("phase")}\n")}
        append("\n## Findings\n\n");val f=j.getJSONArray("findings");for(i in 0 until f.length()){val x=f.getJSONObject(i);append("- ${x.getString("severity")} [${x.getString("ruleId")}] ${x.getString("message")}\n")}
        append("\n## Planned structure and acoustic settings\n\n");val p=j.getJSONArray("structure");for(i in 0 until p.length()){val x=p.getJSONObject(i)
            append("- ~${ScriptTimeline.time(x.getDouble("estimatedStartSeconds"))} · ${x.getString("structurallyDetectedRole")} · ${x.getString("section")} · ${x.getString("profile")} / ${x.getString("phase")} · ${x.getDouble("speed")}× / ${x.getDouble("gainDb")} dB · ${x.getString("settingSource")}\n")}
        append("\n## Review history\n\n");append(j.optJSONArray("reviewHistory")?.toString(2)?:"No reviews recorded.");append("\n\nParser compliance does not establish psychological outcomes or manual audio approval.\n")
    }
}
