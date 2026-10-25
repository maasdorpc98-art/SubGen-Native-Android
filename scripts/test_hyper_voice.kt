import com.subgen.nativeapp.*
import kotlin.math.abs

fun assertClose(actual:Double,expected:Double,tolerance:Double=1e-8){
    check(abs(actual-expected)<=tolerance){"Expected $expected, got $actual"}
}
fun main(){
    val spaced=ScriptParser.parse("A...[pause 2]...B")
    check(spaced.filterIsInstance<ScriptNode.Pause>().single().seconds==2.0)
    check(spaced.filterIsInstance<ScriptNode.Speech>().map{it.text}==listOf("A...","...B"))
    val legacy=ScriptParser.parse("A\n[PAUSE: 2]\nB")
    check(legacy.filterIsInstance<ScriptNode.Pause>().single().seconds==2.0)
    val adjacent=ScriptParser.parse("[SOFT]A[pause 1.5][SLOW]B[WHISPER]C[NORMAL]D")
    check(adjacent.filterIsInstance<ScriptNode.Speech>().map{it.delivery}==listOf("SOFT","SLOW","WHISPER","NORMAL"))
    assertClose(adjacent.filterIsInstance<ScriptNode.Pause>().single().seconds,1.5)
    check(adjacent.filterIsInstance<ScriptNode.DeliveryChange>().size==4)
    val sections=ScriptParser.parse("A[SECTION: The Void][stage State of Mind]B")
    check(sections.filterIsInstance<ScriptNode.Section>().map{it.name}==listOf("The Void","State of Mind"))
    check(sections.filterIsInstance<ScriptNode.Speech>().size==2)
    val rates=ScriptParser.parse("A[tempo 0.85]B[volume -3dB]C[tempo reset]D[volume reset]E")
        .filterIsInstance<ScriptNode.Speech>()
    assertClose(rates[1].tempo!!,.85)
    assertClose(rates[2].gainDb!!,-3.0)
    check(rates[3].tempo==null && rates[3].gainDb==-3.0)
    check(rates[4].gainDb==null)
    val weird=ScriptParser.parse("Say \\[pause 2] literally, plus [the door opens].")
    check(weird.filterIsInstance<ScriptNode.Pause>().isEmpty())
    check(weird.filterIsInstance<ScriptNode.Speech>().single().text.contains("[pause 2]"))
    check(weird.filterIsInstance<ScriptNode.Speech>().single().text.contains("[the door opens]"))
    for(raw in listOf("[pause -1]","[pause NaN]","[pause 3601]","[pause 1,5]",
                      "[pauze 2]","[tempo -3]","[volume abc]","[delivery GENTLE]","@TYPE(FRACTIONATION)")){
        check(ScriptParser.parse("Hello $raw there").any{it is ScriptNode.Unknown}){raw}
    }
    check(ScriptParser.parse("[pause 3600]").filterIsInstance<ScriptNode.Pause>().single().seconds==3600.0)
    assertClose(ScriptParser.parse("[pause 0.1][pause 0.2]").filterIsInstance<ScriptNode.Pause>().sumOf{it.seconds},.3)
    val core=ScriptParser.parse("Relax Deeply. Relax your System Deeply. State of Mind and the Void.")
    val prose=core.filterIsInstance<ScriptNode.Speech>().joinToString(" "){it.text}
    for(cue in listOf("Relax Deeply","Relax your System Deeply","State of Mind","the Void"))check(cue in prose)
    val plain=ScriptTimeline.analyze("[NORMAL]Four gentle words here.")
    val slowed=ScriptTimeline.analyze("[NORMAL][tempo 0.85]Four gentle words here.")
    val quiet=ScriptTimeline.analyze("[NORMAL][volume -3dB]Four gentle words here.")
    check(slowed.totalSeconds>plain.totalSeconds)
    assertClose(plain.totalSeconds,quiet.totalSeconds)
    val soft=ScriptParser.parse("[SOFT]Hello there").filterIsInstance<ScriptNode.Speech>().single()
    val slow=ScriptParser.parse("[SLOW]Hello there").filterIsInstance<ScriptNode.Speech>().single()
    assertClose(VoiceDirection.resolve(soft,1f).speed.toDouble(),.94,1e-6)
    assertClose(VoiceDirection.resolve(slow,1f).speed.toDouble(),.82,1e-6)
    val explicit=ScriptParser.parse("[SLOW][tempo 0.90]Hello there").filterIsInstance<ScriptNode.Speech>().single()
    assertClose(VoiceDirection.resolve(explicit,1f).speed.toDouble(),.9,1e-6)
    val enabled=VoiceDirection.Config(enabled=true,profile="MASTER HYPNOTIST")
    val arrival=ScriptParser.parse("[SECTION: DEEPENER]Quietly now.").filterIsInstance<ScriptNode.Speech>().single()
    check(VoiceDirection.resolve(arrival,1f,enabled).speed<1f)
    check(VoiceDirection.resolve(arrival,1f,enabled).gainDb==-2.0)
    val override=arrival.copy(tempo=.95,gainDb=-4.0)
    assertClose(VoiceDirection.resolve(override,1f,enabled).speed.toDouble(),.95,1e-6)
    check(VoiceDirection.resolve(override,1f,enabled).gainDb==-4.0)
    val oldLegacy=VoiceDirection.resolve(slow,.65f)
    assertClose(oldLegacy.speed.toDouble(),.6,1e-6)
    val summary=ScriptTimeline.analyze("[SECTION: INDUCTION]Breathe.[pause 2][SECTION: WAKE]Eyes open.",direction=enabled)
    check(summary.sections==2&&summary.silenceSeconds==2.0&&summary.invalidCommands==0)
    check(summary.lowerSeconds<summary.totalSeconds&&summary.upperSeconds>summary.totalSeconds)
    println("PASS: HYPER v3 / Voice v3.1 parser, inline tempo/gain, reset, exact pauses, legacy speed, nonspoken plans, duration ranges")
}