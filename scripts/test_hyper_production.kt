import com.subgen.nativeapp.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.*

private var checks=0
private fun verify(name:String,run:()->Unit){run();checks++;println("PASS $checks: $name")}
fun main(){
    val rules=HyperJson.rules(File("app/src/main/assets/hyper_rules_v2_4.json").readText())
    val results=File("test-results").apply{mkdirs()}
    fun analyze(s:String,m:HyperAnalysis.Mode?=null,c:List<HyperAnalysis.Cue> = emptyList(),settings:HyperAnalysis.Settings=HyperAnalysis.Settings())=
        HyperAnalysis.analyze(s,rules,settings,m,c)
    val fixed=listOf(
        "[SECTION: INDUCTION][SOFT] Settle now. [breathe] [SLOW] Feel comfortable.[paragraph][NORMAL]Ready.",
        "[profile MASTER_HYPNOTIST][phase DEEPENER]Relax [tempo 0.90]Deeply. [pause 1.25][volume -2dB]Relax Your System Deeply.[tempo reset][volume reset]Continue.",
        "[profile reset][phase reset][SECTION: VOID ENTRY]Hello. [SECTION: ACTIVATION]Carry on.",
        "\\[pause 2] is literal. [the door opens] ordinary narration.\r\n"+"A long connected sentence with punctuation; remains unchanged. ".repeat(25),
        "😀 Привет. Relax\r\nDeeply! [SECTION: Here and Now]relax deeply. [PAUSE: 0]Relax Deeply.")
    verify("Baseline parser nodes, segmentation, precedence and cache keys unchanged for valid fixtures"){
        val random=java.util.Random(31)
        val scripts=fixed+(0 until 100).map{buildString{repeat(20){append(listOf("[SOFT]","[SLOW]","[NORMAL]","[pause .5]","[pause 0.5]","[SECTION: CUSTOM]","[tempo 0.9]","[volume -1dB]","[phase DEEPENER]","[profile MASTER_HYPNOTIST]","Relax Deeply. ","Word ")[random.nextInt(12)])}}}
        scripts.forEach{s->
            val old=com.subgen.baseline.ScriptParser.parse(s,5.0,3.0,160)
            val now=ScriptParser.parse(s,5.0,3.0,160)
            check(old.map{it.toString()}==now.map{it.toString()})
            check(ScriptSourceMap.parse(s).entries.map{it.node}==now)
            old.zip(now).forEach{(a,b)->if(a is com.subgen.baseline.ScriptNode.Speech&&b is ScriptNode.Speech){
                for(enabled in listOf(false,true)){
                    val x=com.subgen.baseline.VoiceDirection.resolve(a,1f,com.subgen.baseline.VoiceDirection.Config(enabled))
                    val y=VoiceDirection.resolve(b,1f,VoiceDirection.Config(enabled))
                    check(x.toString()==y.toString())
                    val oldSpeed=if(enabled||a.tempo!=null||a.scriptProfile!=null)x.speed else 1f
                    val newSpeed=if(enabled||b.tempo!=null||b.scriptProfile!=null)y.speed else 1f
                    check(HyperAnalysis.hash("native-kokoro-v1_0-fp32-validated|3|$oldSpeed|${a.delivery}|${a.text}")==
                        HyperAnalysis.hash("native-kokoro-v1_0-fp32-validated|3|$newSpeed|${b.delivery}|${b.text}"))
                }
            }}
        }
    }
    verify("UTF-16 source locations, CRLF, case, punctuation, duplicate cues, no partial matches"){
        val script="😀 header\r\n[SECTION: Relax Deeply]\r\nrelax deeply! Relax Deeply. xRelax Deeply Relax Deeplyish. Relax Your System Deeply."
        val a=analyze(script);val hits=a.occurrences
        check(hits.count{it.cue.id=="ENTRY"}==2);check(hits.count{it.cue.id=="DEEPEN"}==1)
        check(hits.first().source.line==3&&hits.first().source.column==1)
        check(script.substring(hits.first().source.start,hits.first().source.end)=="relax deeply")
        check(hits[1].source.start>hits[0].source.start)
    }
    verify("Permitted inline codes can split cues; ALL explicit pauses stop matching"){
        val a=analyze("Relax [tempo 0.90]Deeply. Relax [pause 0]Deeply. Relax Your [volume -2dB]System Deeply. Relax [breathe]Deeply.")
        check(a.occurrences.count{it.cue.id=="ENTRY"}==1);check(a.occurrences.count{it.cue.id=="DEEPEN"}==1)
        check(a.occurrences.first().fragments.size>=2)
    }
    verify("No overlapping universal/session matches and no section-label or sidecar counts"){
        val c=HyperAnalysis.Cue("S006","State of Mind","SESSION","006","Activation","State of Mind",authorized=true)
        val a=analyze("[SECTION: State of Mind]State [SOFT]of Mind. [SECTION: Relax Deeply]Relax Your System Deeply.",c=listOf(c))
        check(a.occurrences.size==2);check(a.occurrences.count{it.cue.kind=="SESSION"}==1)
        check(analyze("The State of Mind.",c=listOf(c.copy(authorized=false))).occurrences.isEmpty())
        check(analyze("The State of Mind.").occurrences.isEmpty())
    }
    verify("Exact 6 / 12 / 18 / 30 minute boundaries and sleep override"){
        for((s,n,next) in listOf(Triple(360.0,2,4),Triple(720.0,4,5),Triple(1080.0,5,7),Triple(1800.0,7,8))){
            check(rules.minimum(s,false)==n to n);check(rules.minimum(s+.001,false)==next to next)
            check(rules.minimum(s-.001,false)==n to n);check(rules.minimum(s,true)==3 to 3)
        }
    }
    verify("Duration uses exactly the existing resolver; gain does not affect duration"){
        fixed.forEach{s->val a=analyze(s);val t=ScriptTimeline.analyze(s);check(abs(a.totalSeconds-t.totalSeconds)<1e-8);check(a.words==t.words)}
        val a=analyze("[tempo 0.8]One two three four.");val b=analyze("[tempo 0.8][volume -2dB]One two three four.")
        check(a.totalSeconds==b.totalSeconds);check(a.totalSeconds>analyze("One two three four.").totalSeconds)
        check(analyze("[phase DEEPENER][pause 2]Word.").pauses.first().phase=="DEEPENER")
    }
    verify("Script profile works with UI OFF; custom section never changes explicit phase"){
        val a=analyze("[profile MASTER_HYPNOTIST][phase DEEPENER]One. [SECTION: PRECISION]Two.[SECTION: VOID ENTRY]Three.")
        check(a.passages.all{abs(it.speed-.88)<1e-6&&it.gainDb == -2.0&&it.phase=="DEEPENER"})
    }
    verify("Malformed commands, unsupported profile/phase, authoring instructions are technical errors"){
        for(code in listOf("[tempo 0.1]","[volume -99dB]","[phase UNKNOWN]","[profile UNKNOWN]","@CEMENT(test)","[pause -2]","[emotion happy]","[pitch 2]","[voice test]"))
            check(analyze("$code Hello.").technicalErrors>0){code}
        check(analyze("[tempo 1.5]Hello.").technicalErrors>0)
        check(analyze("[the door opens] Hello.").technicalErrors==0)
    }
    verify("Conservative inference; absent waking never implies loop or sleep"){
        val a=analyze("A comfortable experience without ending labels.")
        check(a.mode.architecture=="UNRESOLVED"&&a.mode.ending=="UNRESOLVED"&&a.technicalErrors==0)
        check(analyze("[SECTION: CONTINUOUS EYES OPEN LOOP]Keep your eyes open.").mode.architecture=="LOOP")
    }
    val foundation="""[profile MASTER_HYPNOTIST][phase INDUCTION][SECTION: FOUNDATION INDUCTION]
        Notice your breathing. [SECTION: FIRST RESPONSE]Feel comfortable imagery. Relax Deeply.
        [SECTION: DEEPENER ONE][phase DEEPENER]Relax Your System Deeply.
        [SECTION: FRACTIONATION ONE][phase FRACTIONATION]Notice the room, a contrast, then return within.
        [SECTION: DEEPENER TWO][phase DEEPENER]Relax Deeply. Relax Your System Deeply.
        [SECTION: FRACTIONATION TWO][phase FRACTIONATION]Shift attention outward and then return inward.
        [SECTION: OBJECTIVE DEVELOPMENT][phase SUGGESTIONS]Practice the chosen skill.
        [SECTION: RENEWED DEEPENING][phase DEEPENER]Relax Deeply. Relax Your System Deeply.
        [SECTION: INTEGRATION][phase INTEGRATION]Notice what you learned.
        [SECTION: WAKE][phase WAKE]Here and Now. Become comfortably alert.
    """.trimIndent()
    val sleep="[SECTION: NATURAL SLEEP INDUCTION][phase INDUCTION]Relax Deeply. Relax Your System Deeply. [phase SLEEP]Sleep When Ready."
    val loop="[SECTION: CONTINUOUS EYES OPEN LOOP][phase INDUCTION]Keep your eyes open. Relax Deeply. [SECTION: RENEWED DEEPENING][phase DEEPENER]Relax Your System Deeply. [SECTION: LOOP STABILIZATION]Notice the next comfortable moment."
    val stateCue=HyperAnalysis.Cue("S006","State of Mind","SESSION","006","Activation","State of Mind",expected=1,authorized=true)
    verify("Session 001 synthetic Foundation: response, deepeners, fractionation, final waking"){
        val a=analyze(foundation,HyperAnalysis.Mode("FOUNDATION","CLOSED","WAKE"))
        check(a.firstResponse!=null);check(a.findings.none{it.rule in setOf("DISTINCT_DEEPENERS","ENDING_CUE","FRACTIONATION_SPACING")})
        check(a.findings.any{it.rule=="FRACTIONATION_REVIEW"});check(a.technicalErrors==0)
        File(results,"foundation-audit.json").writeText(HyperJson.report(a).toString(2))
        File(results,"foundation-script.txt").writeText(foundation)
    }
    verify("Session 001 synthetic Natural Sleep: sleep override, no required waking/fractionation"){
        val a=analyze(sleep,HyperAnalysis.Mode("SLEEP","CLOSED","SLEEP"))
        check(a.findings.none{it.rule in setOf("ENDING_CUE","FRACTIONATION")})
        check(a.mode.ending=="SLEEP")
    }
    verify("Session 004 synthetic eyes-open loop: no compulsory wake, sleep or eye closure"){
        val a=analyze(loop,HyperAnalysis.Mode("LOOP","OPEN","LOOP"))
        check(a.technicalErrors==0&&a.findings.none{it.rule in setOf("ENDING_CUE","ENDING_CONFLICT","EYES_CONFLICT")})
        val bad=analyze(loop+" Close your eyes. Here and Now. The session has finished.",HyperAnalysis.Mode("LOOP","OPEN","LOOP"))
        check(bad.findings.any{it.rule=="ENDING_CONFLICT"}&&bad.findings.any{it.rule=="EYES_CONFLICT"}&&bad.findings.any{it.rule=="LOOP_LANGUAGE"})
    }
    verify("Session 006 synthetic State of Mind: separate authorized cue and two ending architectures"){
        val a=analyze(loop+" State of Mind.",HyperAnalysis.Mode("LOOP","OPEN","LOOP",setOf("MIND")),listOf(stateCue))
        val b=analyze(foundation+" State of Mind.",HyperAnalysis.Mode("DEEP","CLOSED","WAKE",setOf("MIND")),listOf(stateCue))
        check(a.occurrences.count{it.cue.kind=="SESSION"}==1&&a.occurrences.none{it.cue.id=="WAKE"})
        check(b.occurrences.any{it.cue.id=="WAKE"});check(a.technicalErrors==0&&b.technicalErrors==0)
        File(results,"session-006-audit.json").writeText(HyperJson.report(a).toString(2))
        File(results,"session-006-audit.md").writeText(HyperJson.markdown(HyperJson.report(a)))
    }
    verify("Rapid Booster tier, classification and renewed depth"){
        val a=analyze("[SECTION: RAPID INDUCTION BOOSTER]Relax Deeply. Relax Your System Deeply. [SECTION: RENEWED DEEPENING]Relax Deeply. Relax Your System Deeply.[phase WAKE]Here and Now.")
        check(a.mode.architecture=="BOOSTER");check(a.findings.none{it.rule=="CUE_FREQUENCY"})
    }
    verify("Cue context spans programmed pauses, bounded 100–250-word context"){
        val a=analyze("word ".repeat(130)+"[pause 2]Relax Deeply.[pause 2]"+"word ".repeat(130))
        val h=a.occurrences.single();check(h.before.split(' ').size==120&&h.after.split(' ').size==120)
    }
    verify("Rule packs serialize deterministically, retain versions, reject blocking editorial policy"){
        val raw=File("app/src/main/assets/hyper_rules_v2_4.json").readText();val j=JSONObject(raw)
        check(HyperJson.rules(HyperJson.canonical(j)).digest==rules.digest)
        j.put("version","2.5-test");check(HyperJson.rules(j.toString()).version=="2.5-test"&&rules.version=="2.4")
        j.getJSONObject("policies").getJSONObject("CUE_FREQUENCY").put("severity","ERROR")
        check(runCatching{HyperJson.rules(j.toString())}.isFailure)
    }
    verify("Approvals invalidate on script, rules, mode, cues, acoustics, output settings and new render"){
        val id=HyperGovernance.Identity("script","rules","2.4","LOOP","cues","acoustics","render","audio","render1")
        val text=HyperGovernance.approve("TEXT",id,emptyList(),0,2,true,"Reviewed text")
        check(runCatching{HyperGovernance.approve("AUDIO",id,listOf(text),0,2,true,"Listened",false)}.isFailure)
        val audio=HyperGovernance.approve("AUDIO",id,listOf(text),0,2,true,"Listened to both seams",true)
        val release=HyperGovernance.approve("RELEASE",id,listOf(text,audio),0,2,true,"Release accepted")
        val reviews=listOf(text,audio,release)
        check(HyperGovernance.state(id,reviews,0,2,true).release=="RELEASE APPROVED")
        for(changed in listOf(id.copy(scriptHash="new"),id.copy(rulesHash="new"),id.copy(mode="SLEEP"),id.copy(cues="other"),id.copy(acousticSettings="other")))
            check(HyperGovernance.state(changed,reviews,0,2,true).editorial=="DRAFT")
        for(changed in listOf(id.copy(renderConfig="new"),id.copy(renderId="render2"),id.copy(audioHash="new")))
            check(HyperGovernance.state(changed,reviews,0,2,true).audio=="AUDIO PENDING")
        check(HyperGovernance.state(id,reviews,1,2,true).release!="RELEASE APPROVED")
        check(runCatching{HyperGovernance.approve("TEXT",id,emptyList(),1,0,false,"Cannot override errors")}.isFailure)
        val finding=HyperAnalysis.Finding("PARSER_ERROR","ERROR","Error",null,false)
        check(runCatching{HyperGovernance.exception(finding,id,"reason")}.isFailure)
        check(HyperGovernance.exception(finding.copy(rule="CUE_GAP",severity="WARNING",allowException=true),id,"Author intentional exception").rule=="CUE_GAP")
    }
    fun wav(file:File,bits:Int=16,encoding:Int=1,seconds:Double=1.0,channels:Int=1,fn:(Int)->Double={sin(2*PI*1000*it/24000)*.25}):File {
        val n=(seconds*24000).roundToInt();val block=channels*bits/8
        FileOutputStream(file).use{o->fun u16(v:Int){o.write(v and 255);o.write(v ushr 8 and 255)};fun u32(v:Long){repeat(4){o.write((v ushr(it*8)and 255).toInt())}}
            o.write("RIFF".toByteArray());u32(36L+n*block);o.write("WAVEfmt ".toByteArray());u32(16);u16(encoding);u16(channels);u32(24000);u32(24000L*block);u16(block);u16(bits);o.write("data".toByteArray());u32(n.toLong()*block)
            for(i in 0 until n){val x=fn(i);repeat(channels){val raw=when{encoding==3&&bits==32->x.toFloat().toRawBits().toLong();encoding==3->x.toRawBits();bits==8->(x*127+128).roundToLong();else->(x*((1L shl(bits-1))-1)).roundToLong()}
                repeat(bits/8){o.write((raw ushr(it*8)and 255).toInt())}}}
        };return file
    }
    verify("Streaming WAV QA: PCM8/16/24/32, float32/64, mono/stereo, frame duration"){
        for((bits,encoding) in listOf(8 to 1,16 to 1,24 to 1,32 to 1,32 to 3,64 to 3)){
            val f=wav(File(results,"format-$encoding-$bits.wav"),bits,encoding,channels=2)
            val a=HyperAudioQa.analyze(f);check(a.frames==24000L&&a.seconds==1.0&&a.channels==2&&a.peakDbfs in -12.3..-11.9)
        }
    }
    verify("Clean seam, deliberate click, leading/trailing silence and RMS changes"){
        val clean=wav(File(results,"clean.wav"),fn={sin(2*PI*100*it/24000)*.2})
        val a=HyperAudioQa.analyze(clean);check(a.seamDelta<.02&&a.railSamples==0L)
        val click=wav(File(results,"click.wav"),fn={if(it<100)1.0 else -.9})
        val b=HyperAudioQa.analyze(click);check(b.seamDelta>1.8&&b.railSamples>0&&b.warnings.isNotEmpty())
        val silence=wav(File(results,"silence.wav"),seconds=3.0,fn={if(it<24000||it>=48000)0.0 else .2})
        val c=HyperAudioQa.analyze(silence);check(c.leadingSilenceSeconds==1.0&&c.trailingSilenceSeconds==1.0&&c.silentSeconds==2.0)
        File(results,"audio-qa-example.json").writeText(HyperJson.audio(c).toString(2))
    }
    verify("Three cycles preserve every original PCM byte and both junctions; source unchanged"){
        val source=File(results,"clean.wav");val before=HyperAudioQa.hash(source);val out=File(results,"three-cycle.wav");if(out.exists())out.delete()
        HyperAudioQa.exportCycles(source,out);val s=HyperAudioQa.inspect(source);val t=HyperAudioQa.inspect(out)
        check(t.frames==s.frames*3);check(out.length()==s.exportBytes(3));check(HyperAudioQa.hash(source)==before)
        val pcm=source.readBytes().copyOfRange(s.dataOffset.toInt(),(s.dataOffset+s.dataBytes).toInt());val copy=out.readBytes()
        repeat(3){i->check(copy.copyOfRange((t.dataOffset+i*s.dataBytes).toInt(),(t.dataOffset+(i+1)*s.dataBytes).toInt()).contentEquals(pcm))}
        HyperAudioQa.audition(source,File(results,"junction.wav"),.2)
        check(HyperAudioQa.inspect(File(results,"junction.wav")).frames==9600L)
    }
    verify("Malformed, truncated, nonfinite and unsupported WAVs fail clearly"){
        val bad=File(results,"bad.wav").apply{writeText("not wav")};check(runCatching{HyperAudioQa.analyze(bad)}.isFailure)
        wav(bad);RandomAccessFile(bad,"rw").use{it.setLength(100)};check(runCatching{HyperAudioQa.inspect(bad)}.isFailure)
        wav(bad,32,3,fn={Double.NaN});check(runCatching{HyperAudioQa.analyze(bad)}.isFailure)
    }
    verify("RIFF-size limit guard on large sparse input; no giant buffer"){
        val big=File(results,"sparse-large.wav");wav(big)
        val bytes=1600000000L
        RandomAccessFile(big,"rw").use{r->fun put(at:Long,v:Long){r.seek(at);repeat(4){r.write((v ushr(it*8)and 255).toInt())}};r.setLength(bytes+44);put(4,bytes+36);put(40,bytes)}
        check(HyperAudioQa.inspect(big).dataBytes==bytes)
        check(runCatching{HyperAudioQa.exportCycles(big,File(results,"overflow.wav"))}.isFailure)
        big.delete()
    }
    val long=buildString{append("[profile MASTER_HYPNOTIST][phase INDUCTION][SECTION: INDUCTION]");repeat(75){append("Notice a comfortable breath, then allow a clear image to form. Relax Deeply. [pause 10]Relax Your System Deeply. ")};append("[phase WAKE]Here and Now.")}
    verify("Synthetic long HYPER script: 20–30 minute estimated fixture and serializable evidence"){
        val a=analyze(long,HyperAnalysis.Mode("DEEP","CLOSED","WAKE"));check(a.totalSeconds in 1200.0..1800.0);check(a.technicalErrors==0)
        File(results,"long-synthetic-script.txt").writeText(long)
        File(results,"long-script-audit.json").writeText(HyperJson.report(a).toString(2))
    }
    File(results,"SUMMARY.txt").writeText("$checks HYPER test groups passed. Synthetic fixtures; real Sessions 001/004/006 and Samsung listening not tested.\n")
    println("SUCCESS: $checks HYPER production test groups passed")
}
