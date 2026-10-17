import com.subgen.nativeapp.*
import kotlin.math.abs

fun close(a:Double,b:Double,e:Double=0.00001){check(abs(a-b)<e){"Expected $b; got $a"}}
fun speech(s:String)=ScriptParser.parse(s).filterIsInstance<ScriptNode.Speech>()
fun main(){
    val off=VoiceDirection.Config(enabled=false)
    val ui=VoiceDirection.Config(enabled=true,profile="GENTLE GUIDE",globalGainDb=-1.0,
        sectionRules=mapOf("DEEPENER" to VoiceDirection.SectionRule(.80,-8.0)))
    val p=speech("[profile MASTER_HYPNOTIST][phase DEEPENER]First.[SECTION: ACTIVATION]Second."+
        "[SECTION: THE VOID]Third.[SECTION: PRECISION]Fourth.")
    check(p.size==4)
    check(p.map{it.section}==listOf("", "ACTIVATION","THE VOID","PRECISION"))
    check(p.all{it.scriptProfile=="MASTER HYPNOTIST" && it.acousticPhase=="DEEPENER" && it.explicitPhaseMode})
    p.forEach{
        close(VoiceDirection.resolve(it,1f,off).speed.toDouble(),.88,1e-6)
        close(VoiceDirection.resolve(it,1f,off).gainDb,-2.0)
        close(VoiceDirection.resolve(it,1f,ui).speed.toDouble(),.88,1e-6)
        close(VoiceDirection.resolve(it,1f,ui).gainDb,-3.0) // app-global gain still applies
    }
    val reset=speech("[profile MASTER_HYPNOTIST][phase DEEPENER]A"+
        "[tempo 0.90][volume -4dB]B[NORMAL]C[tempo reset]D[volume reset]E"+
        "[phase reset]F[profile reset]G")
    val r=reset.map{VoiceDirection.resolve(it,1f,off)}
    close(r[0].speed.toDouble(),.88,1e-6);close(r[0].gainDb,-2.0)
    close(r[1].speed.toDouble(),.90,1e-6);close(r[1].gainDb,-4.0)
    close(r[2].speed.toDouble(),.90,1e-6);close(r[2].gainDb,-4.0)
    close(r[3].speed.toDouble(),.88,1e-6);close(r[3].gainDb,-4.0)
    close(r[4].speed.toDouble(),.88,1e-6);close(r[4].gainDb,-2.0)
    close(r[5].speed.toDouble(),1.0,1e-6);close(r[5].gainDb,0.0)
    close(r[6].speed.toDouble(),1.0,1e-6);close(r[6].gainDb,0.0)
    // A profile can reset to UI profile, while retaining explicit acoustic phase.
    close(VoiceDirection.resolve(reset.last(),1f,ui).speed.toDouble(),.96,1e-6)
    val generic=speech("[profile GENTLE_GUIDE]A[SECTION: DEEPENER]B[SECTION: CUSTOM NAME]C")
    check(generic.all{it.acousticPhase==null})
    generic.forEach{close(VoiceDirection.resolve(it,1f,off).speed.toDouble(),.96,1e-6)}
    // Legacy scripts (no new syntax) still map familiar sections and inherit after custom.
    val old=speech("[SECTION: DEEPENER]A[SECTION: VOID ENTRY]B[SECTION: CUSTOM]C")
    check(old.none{it.explicitPhaseMode})
    close(VoiceDirection.resolve(old[0],1f,ui).speed.toDouble(),.8,1e-6)
    // Legacy VOID ENTRY was not an acoustic phase in 3.1.0: retain its fallback.
    for(i in 1..2)close(VoiceDirection.resolve(old[i],1f,ui).speed.toDouble(),.96,1e-6)
    val oldDefault=VoiceDirection.Config(enabled=true,profile="MASTER HYPNOTIST")
    close(VoiceDirection.resolve(old[0],1f,oldDefault).speed.toDouble(),.88,1e-6)
    for(i in 1..2)close(VoiceDirection.resolve(old[i],1f,oldDefault).speed.toDouble(),1.0,1e-6)
    // UI manual defaults are used only when no inline profile is active.
    close(VoiceDirection.resolve(speech("[phase DEEPENER]A")[0],1f,ui).speed.toDouble(),.8,1e-6)
    // All five canonical profiles and all nine acoustic phases parse.
    for(code in ScriptParser.PROFILE_CODES.keys){
        check(speech("[profile $code]OK").single().scriptProfile==ScriptParser.PROFILE_CODES[code])
    }
    for(phase in ScriptParser.ACOUSTIC_PHASES){
        check(speech("[phase $phase]OK").single().acousticPhase==phase)
    }
    for(bad in listOf("[profile]","[profile MAGIC]","[phase]","[phase VOID ENTRY]", "[phase GARBAGE]",
                       "[profile MASTER HYPNOTIST]", "[delivery AUTHORITATIVE]", "[volume 999dB]", "@PERSONALITY(MASTER_HYPNOTIST)")){
        check(ScriptParser.parse("Before $bad after").any{it is ScriptNode.Unknown}){bad}
    }
    val summary=ScriptTimeline.analyze("[profile MASTER_HYPNOTIST][phase INDUCTION]Relax Deeply.[pause 1.5]"+
        "[SECTION: STATE OF MIND]Relax your System Deeply.[phase DEEPENER]Focus.",direction=off)
    check(summary.invalidCommands==0 && summary.sections==1)
    close(summary.silenceSeconds,1.5)
    check(summary.rows.filter{it.kind=="SPEECH"}.size==3)
    check(summary.rows.any{it.kind=="PROFILE"}&&summary.rows.any{it.kind=="ACOUSTIC PHASE"})
    val gainOnly=ScriptTimeline.analyze("[profile MASTER_HYPNOTIST][phase DEEPENER]Four words sound good.")
    val vol=ScriptTimeline.analyze("[profile MASTER_HYPNOTIST][phase DEEPENER][volume -3dB]Four words sound good.")
    close(gainOnly.totalSeconds,vol.totalSeconds)
    val tempo=ScriptTimeline.analyze("[profile MASTER_HYPNOTIST][phase DEEPENER][tempo 0.90]Four words sound good.")
    check(tempo.totalSeconds<gainOnly.totalSeconds)
    // Representative HYPER continuous text: no spoken control codes, phase independent from custom section label.
    val whole=ScriptParser.parse("[profile MASTER_HYPNOTIST][phase INDUCTION][SECTION: STATE OF MIND]"+
      "Relax Deeply...[pause 0.2]...enter State of Mind.[SECTION: THE VOID][phase DEEPENER]"+
      "Relax your System Deeply.[pause 0.1][phase WAKE]Open your eyes.")
    check(whole.filterIsInstance<ScriptNode.Unknown>().isEmpty())
    check(whole.filterIsInstance<ScriptNode.Pause>().size==2)
    check(whole.filterIsInstance<ScriptNode.Speech>().joinToString(" "){it.text}.let{
        "Relax Deeply" in it && "Relax your System Deeply" in it && "State of Mind" in it && "[profile" !in it})
    println("PASS: v3.1.1 explicit profile/phase grammar, independent sections, resets, precise precedence, legacy mode, cue noncommands")
}