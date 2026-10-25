import com.subgen.nativeapp.*
import java.io.File
fun main(){
    val base=File("/tmp/subgen_native_audio_tests").apply{mkdirs()}
    val samples=FloatArray(24000){ .2f }
    val input=File(base,"input.wav")
    WavIO.writeMono(input,samples,24000)
    check(WavIO.loadMono(input).frames==24000L)
    val result=File(base,"hypnosis.wav")
    val nodes=listOf(ScriptNode.Speech("test","NORMAL"), ScriptNode.Pause(0.25), ScriptNode.Speech("test2","NORMAL"))
    val sec=WavIO.assemble(nodes,listOf(input,null,input),result)
    check(sec==2.25) {"Unexpected duration $sec"}
    check(result.length()==44L+(2.25*24000*4).toLong()) {"Unexpected WAV bytes ${result.length()}"}
    val sub=File(base,"subliminal.wav")
    NativeSubliminal.mix(NativeSubliminal.Settings(input,null,input,null,durationSeconds=2,rate=32000,primaryDb=-6.0),sub,{}, {false})
    check(sub.length()==44+2L*32000*4)
    println("PASS: checkpoint WAV format, 250ms exact silence, native mix time, output WAV size")
}
