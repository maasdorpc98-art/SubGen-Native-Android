import com.subgen.nativeapp.*
import android.content.*
import org.json.JSONObject
import java.io.File
fun main(){
    val dir=File(System.getProperty("java.io.tmpdir"),"hyper-store-integration").apply{deleteRecursively();mkdirs()}
    val context=Context(dir,Assets(File("app/src/main/assets")))
    val store=HyperStore(context);val source="[profile MASTER_HYPNOTIST][phase INDUCTION]Relax Deeply. Relax Your System Deeply."
    val a=store.analyze(source);check(store.identity(a).audioHash.isEmpty())
    check(runCatching{store.measure()}.isFailure)
    File("test-results/format-1-16.wav").copyTo(File(dir,"hypnosis-final.wav"))
    val acoustics=HyperStore.acoustics("SERENA",store.settings());val output=HyperStore.output(24000,2,0.0,-1.0,false)
    fun render(){HyperStore.recordRender(context,source,acoustics,output,ScriptParser.parse(source),24000,24000)}
    render();val qa=store.measure();check(qa.getLong("pcmFrames")==24000L)
    val id=store.identity(a);check(id.audioHash.isNotBlank()&&id.renderId.isNotBlank())
    store.addReview(HyperGovernance.approve("TEXT",id,store.reviews(),0,a.warnings,false,"Text reviewed"))
    store.addReview(HyperGovernance.approve("AUDIO",id,store.reviews(),0,a.warnings,false,"Audio listened"))
    check(HyperGovernance.state(id,store.reviews(),0,a.warnings,false).audio=="AUDIO VERIFIED")
    val files=store.saveReport(a,source);val report=JSONObject(files.first.readText())
    check(report.has("audioMeasurements")&&report.getString("sourceScript")==source&&files.second.length()>100)
    check(HyperStore(context).reviews().size==2)
    println("PASS: local audit/report/review round-trip, measured WAV association and persisted identity")
    StateStore.testPrefs.values["speed"]=.95f
    check(store.identity(store.analyze(source)).audioHash.isEmpty())
    check(HyperGovernance.state(store.identity(store.analyze(source)),store.reviews(),0,a.warnings,false).editorial=="DRAFT")
    StateStore.testPrefs.values.remove("speed")
    render();check(store.identity(a).audioHash.isEmpty());store.measure()
    check(HyperGovernance.state(store.identity(a),store.reviews(),0,a.warnings,false).audio=="AUDIO PENDING")
    check(HyperGovernance.state(store.identity(a),store.reviews(),0,a.warnings,false).editorial=="TEXT APPROVED")
    println("PASS: setting changes and new WAV render invalidate appropriate approvals, including same-byte renders")
    val oldVersion=report.getString("hyperVersion")
    val changed=JSONObject(store.rulesRaw()).put("version","2.5-storage-test").toString()
    store.installRules(changed);check(store.rules().version=="2.5-storage-test")
    check(JSONObject(files.first.readText()).getString("hyperVersion")==oldVersion&&store.availableRules().size==2)
    check(HyperGovernance.state(store.identity(store.analyze(source)),store.reviews(),0,a.warnings,false).editorial=="DRAFT")
    println("PASS: immutable historical audit and retained rule pack after explicit re-audit")
    val c=HyperAnalysis.Cue("006-STATE","State of Mind","SESSION","006","Activation","State of Mind",authorized=true)
    store.saveCue(c,"State of Mind")
    check(store.allCues().single().phrase=="State of Mind")
    check(store.analyze("State of Mind.").occurrences.isEmpty())
    store.saveConfig(store.config().put("sessionId","006"))
    check(store.analyze("State of Mind.").occurrences.single().cue.kind=="SESSION")
    store.saveCue(c.copy(authorized=false),"State of Mind")
    check(store.analyze("State of Mind.").occurrences.isEmpty())
    println("PASS: session registry persists, scopes by selected session and honors revocation")
    check(runCatching{store.saveCue(c.copy(phrase="Relax Deeply"),"Wrong")}.isFailure)
    check(File(dir,"hyper-production/renders").listFiles()!!.size==2)
    println("PASS: universal cue cannot be reclassified; render history retained")
}
