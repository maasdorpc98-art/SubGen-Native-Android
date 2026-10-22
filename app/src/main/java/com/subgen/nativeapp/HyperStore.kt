package com.subgen.nativeapp

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.roundToLong

/** Local optional analysis storage. Existing settings, projects, caches and outputs keep their identities. */
class HyperStore(private val context:Context) {
    val directory=File(context.filesDir,"hyper-production").apply{mkdirs()}
    private val configFile=File(directory,"configuration.json")
    fun config():JSONObject=if(configFile.isFile)JSONObject(configFile.readText())else JSONObject()
    fun saveConfig(j:JSONObject){atomic(configFile,j.toString(2))}
    fun rulesRaw():String {
        val active=config().optString("activeRulePack")
        return if(active.isNotBlank())File(directory,"rules/$active.json").readText()
        else context.assets.open("hyper_rules_v2_4.json").bufferedReader().use{it.readText()}
    }
    fun rules()=HyperJson.rules(rulesRaw())
    fun installRules(raw:String){val r=HyperJson.rules(raw);val f=File(directory,"rules/${r.digest}.json").apply{parentFile!!.mkdirs()}
        if(!f.exists())atomic(f,raw);saveConfig(config().put("activeRulePack",r.digest))}
    fun availableRules():List<Pair<String,String>> = listOf("PACKAGED HYPER 2.4" to "")+
        File(directory,"rules").listFiles().orEmpty().filter{it.extension=="json"}.map{f->val r=HyperJson.rules(f.readText());"${r.version} · ${r.digest.take(10)}" to r.digest}
    fun registry():JSONArray=File(directory,"session-cues.json").let{if(it.isFile)JSONArray(it.readText())else JSONArray()}
    fun allCues():List<HyperAnalysis.Cue> {val a=registry();return(0 until a.length()).map{HyperJson.cue(a.getJSONObject(it))}}
    fun saveCue(cue:HyperAnalysis.Cue,sessionName:String){
        require(cue.kind=="SESSION"&&cue.sessionId.isNotBlank()&&cue.phrase.isNotBlank()&&cue.function.isNotBlank()&&sessionName.isNotBlank())
        require(cue.phrase.length<=120&&cue.expected?.let{it>=0}!=false)
        require(rules().cues.none{it.phrase.equals(cue.phrase,true)}){"Universal cues cannot be reclassified"}
        val old=registry();val next=JSONArray();for(i in 0 until old.length()){val j=old.getJSONObject(i)
            if(!(j.optString("id")==cue.id&&j.optString("sessionId")==cue.sessionId))next.put(j)}
        val entry=HyperJson.cue(cue).put("sessionName",sessionName).put("authorizationStatus",if(cue.authorized)"APPROVED" else "PENDING")
            .put("updatedAt",System.currentTimeMillis())
        next.put(entry);atomic(File(directory,"session-cues.json"),next.toString(2))
    }
    fun settings():HyperAnalysis.Settings {val p=StateStore.prefs(context);return HyperAnalysis.Settings(p.getFloat("speed",VoiceSpeed.DEFAULT),
        p.getFloat("hyp:breatheSec",5f).toDouble(),p.getFloat("hyp:paragraphSec",3f).toDouble(),VoiceDirectionStore.read(p))}
    fun analyze(source:String):HyperAnalysis.Result {
        val cfg=config();val session=cfg.optString("sessionId")
        val mode=cfg.optJSONObject("mode")?.let{HyperJson.mode(it)}
        val first=if(cfg.optString("firstResponseScriptHash")==HyperAnalysis.hash(source)&&cfg.has("firstResponseOffset"))cfg.getInt("firstResponseOffset")else null
        return HyperAnalysis.analyze(source,rules(),settings(),mode,allCues().filter{it.sessionId==session},cfg.optInt("contextWords",120),first)
    }
    fun reviews():List<HyperGovernance.Review>{val f=File(directory,"reviews.json");val a=if(f.isFile)JSONArray(f.readText())else JSONArray()
        return(0 until a.length()).map{HyperJson.review(a.getJSONObject(it))}}
    fun addReview(r:HyperGovernance.Review){atomic(File(directory,"reviews.json"),HyperJson.array(reviews().map{HyperJson.review(it)}+HyperJson.review(r)).toString(2))}
    fun render():JSONObject?=File(directory,"latest-render.json").takeIf{it.isFile}?.let{JSONObject(it.readText())}
    fun measurements():JSONObject?=File(directory,"latest-audio-qa.json").takeIf{it.isFile}?.let{JSONObject(it.readText())}
    fun identity(a:HyperAnalysis.Result):HyperGovernance.Identity {
        val p=StateStore.prefs(context);val acoustics=acoustics(p.getString("voice","SERENA")?:"SERENA",settings())
        val output=output(p.getInt("hyp:sampleRate",24000),p.getInt("hyp:channels",2),p.getFloat("hyp:outputDb",0f).toDouble(),
            p.getFloat("hyp:peakDb",-1f).toDouble(),p.getBoolean("binaural",false)&&p.getInt("hyp:channels",2)==2)
        val render=render();val qa=measurements()
        val bound=render!=null&&qa!=null&&render.optString("scriptSha256")==a.scriptHash&&render.optString("acoustics")==acoustics&&
            render.optString("outputConfig")==output&&qa.optString("renderId")==render.optString("renderId")&&
            qa.optString("scriptSha256")==a.scriptHash&&File(context.filesDir,"hypnosis-final.wav").let{it.isFile&&it.length()==qa.optLong("fileBytes")&&it.lastModified()==qa.optLong("modifiedAt")}
        return HyperGovernance.Identity(a.scriptHash,a.rules.digest,a.rules.version,HyperJson.canonical(HyperJson.mode(a.mode)),
            HyperJson.canonical(HyperJson.array(a.cues.map{HyperJson.cue(it)})),acoustics,output,
            if(bound)qa!!.getString("sha256")else "",if(bound)render!!.getString("renderId")else "")
    }
    fun measure(stop:()->Boolean={false}):JSONObject {
        val render=render()?:error("Render a WAV in Hypnosis first")
        val file=File(context.filesDir,"hypnosis-final.wav");require(file.isFile){"Completed WAV unavailable"}
        val id=render.getString("renderId");val modified=file.lastModified();val bytes=file.length()
        val audio=HyperJson.audio(HyperAudioQa.analyze(file,stop)).put("renderId",id).put("scriptSha256",render.getString("scriptSha256"))
            .put("fileBytes",bytes).put("modifiedAt",modified)
        require(render()?.getString("renderId")==id&&file.lastModified()==modified&&file.length()==bytes){"Render changed during analysis"}
        atomic(File(directory,"latest-audio-qa.json"),audio.toString(2));return audio
    }
    fun report(a:HyperAnalysis.Result,source:String):JSONObject {
        val p=StateStore.prefs(context);val id=identity(a);val reviews=reviews();val state=HyperGovernance.state(id,reviews,a.technicalErrors,a.warnings,a.mode.ending=="LOOP")
        val report=HyperJson.report(a).put("projectSessionId",config().optString("sessionId")).put("scriptName",p.getString("name","Hypnosis Session"))
            .put("sourceScript",source).put("createdAt",System.currentTimeMillis()).put("rulePack",JSONObject(rulesRaw()))
            .put("reviewHistory",HyperJson.array(reviews.map{HyperJson.review(it)}))
            .put("textApprovalBinding",id.textKey).put("audioApprovalBinding",id.audioKey)
            .put("renderConfiguration",id.renderConfig).put("acousticConfiguration",id.acousticSettings)
            .put("approvalStates",JSONObject().put("technical",state.technical).put("editorial",state.editorial).put("audio",state.audio).put("listener",state.listener).put("release",state.release))
        if(id.audioHash.isNotBlank()){
            val audio=measurements()!!;report.put("audioMeasurements",audio).put("renderEvidence",render())
                .put("measuredMinusEstimatedSeconds",audio.getDouble("measuredDurationSeconds")-a.totalSeconds)
            val measured=audio.getDouble("measuredDurationSeconds");val tier=a.rules.minimum(measured,a.mode.ending=="SLEEP")
            report.put("measuredDurationCueReassessment",JSONObject().put("measuredSeconds",measured).put("minimumEntry",tier.first).put("minimumDeepen",tier.second))
        }
        return report
    }
    fun saveReport(a:HyperAnalysis.Result,source:String):Pair<File,File>{
        val report=report(a,source);val base="${a.scriptHash.take(12)}-${System.currentTimeMillis()}"
        val folder=File(directory,"reports").apply{mkdirs()};val json=File(folder,"$base.json");val md=File(folder,"$base.md")
        atomic(json,report.toString(2));atomic(md,HyperJson.markdown(report));return json to md
    }
    companion object {
        fun atomic(dest:File,content:String){dest.parentFile!!.mkdirs();val tmp=File(dest.parentFile,dest.name+".partial");tmp.writeText(content)
            require(tmp.renameTo(dest)){"Unable to store ${dest.name}"}}
        fun acoustics(voice:String,s:HyperAnalysis.Settings):String=HyperJson.canonical(JSONObject().put("voice",voice).put("globalSpeed",s.speed.toDouble())
            .put("breatheSeconds",s.breathe).put("paragraphSeconds",s.paragraph).put("directionEnabled",s.direction.enabled).put("profile",s.direction.profile)
            .put("globalDirectionGainDb",s.direction.globalGainDb).put("phaseRules",JSONObject().apply{s.direction.sectionRules.forEach{(name,rule)->
                put(name,JSONObject().put("tempo",rule.tempo).put("gainDb",rule.gainDb))}}))
        fun output(rate:Int,channels:Int,gain:Double,ceiling:Double,binaural:Boolean)=HyperJson.canonical(JSONObject().put("rate",rate).put("channels",channels)
            .put("outputDb",gain).put("ceilingDb",ceiling).put("binaural",binaural))
        /** Called only after the existing export succeeds. Failure is diagnostic, never a rendering gate. */
        fun recordRender(context:Context,source:String,acoustics:String,outputConfig:String,nodes:List<ScriptNode>,frames:Long,rate:Int){
            val store=HyperStore(context);val id=UUID.randomUUID().toString()
            val record=JSONObject().put("renderId",id).put("renderCompleted",true).put("createdAt",System.currentTimeMillis())
                .put("scriptSha256",HyperAnalysis.hash(source)).put("acoustics",acoustics).put("outputConfig",outputConfig)
                .put("subgenVersion",HyperAnalysis.VERSION).put("finalPcmFrames",frames).put("sampleRate",rate)
                .put("measuredDurationSeconds",frames.toDouble()/rate)
                .put("insertedProgrammedSilenceFrames24k",nodes.filterIsInstance<ScriptNode.Pause>().sumOf{(it.seconds*24000).roundToLong()})
                .put("programmedSilenceFrameDomain","Assembly PCM at 24 kHz before output resampling")
            atomic(File(store.directory,"latest-render.json"),record.toString(2))
            val history=File(store.directory,"renders").apply{mkdirs()};atomic(File(history,"$id.json"),record.toString(2))
        }
    }
}
