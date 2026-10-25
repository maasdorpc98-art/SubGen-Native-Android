import com.subgen.nativeapp.*
import java.io.File
import kotlin.math.abs

fun main(){
    val dir=File(System.getProperty("java.io.tmpdir"),"subgen-word-pacing-test").apply{mkdirs()}
    val speech=File(dir,"speech.wav")
    WavIO.writeMono(speech,FloatArray(24000*2){0.15f},24000)
    val nodes=listOf(
        ScriptNode.Speech("one two three four five six seven eight nine ten","NORMAL"),
        ScriptNode.Pause(2.0),
        ScriptNode.Speech("another block","NORMAL")
    )
    val frames=longArrayOf(24000L*2,0,24000L*2)
    val plan=WordPacing.plan(nodes,frames,24000,60)
    check(plan.extraFrames[0]==24000L*6) {"2 s spoken + 2 s explicit pause + 6 s automatic = 10s"}
    check(plan.extraFrames[2]==0L) {"Last passage must not acquire tail padding"}
    check(WordPacing.plan(nodes,frames,24000,null).totalAddedFrames==0L)
    check(WordPacing.plan(nodes,frames,24000,180).totalAddedFrames==0L)
    check(runCatching{WordPacing.plan(nodes,frames,24000,49)}.isFailure)
    val original=File(dir,"normal.wav")
    val disabled=File(dir,"disabled.wav")
    val paced=File(dir,"paced.wav")
    WavIO.assemble(nodes,listOf(speech,null,speech),original)
    WavIO.assemble(nodes,listOf(speech,null,speech),disabled,pacingWpm=null)
    check(original.readBytes().contentEquals(disabled.readBytes())){"OFF must preserve byte-for-byte baseline"}
    WavIO.assemble(nodes,listOf(speech,null,speech),paced,pacingWpm=60)
    check(WavIO.loadMono(speech).rate==24000)
    check(plan.extraFrames[0]==144000L)
    check(paced.length()-original.length()==6L*24000*2*2)
    check(abs((paced.length()-44).toDouble()/(24000*4)-12.0)<.001)
    println("PASS: WPM off preserves exact output; optional pacing counts explicit pauses, inserts 6s and leaves native speed untouched")
}
