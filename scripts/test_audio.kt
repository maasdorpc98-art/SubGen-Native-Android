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
    // Four optional tracks each contribute once, without requiring a carrier.
    val gains=listOf(.05f,.10f,.15f,.20f)
    val tracks=gains.mapIndexed{index,g->
        File(base,"affirm-$index.wav").also{file->WavIO.writeMono(file,FloatArray(4800){g},24000)}
    }
    val four=File(base,"four-affirmation-mix.wav")
    NativeSubliminal.mix(NativeSubliminal.Settings(
        carrierA=null,carrierB=null,affirmationA=tracks[0],affirmationB=tracks[3],
        durationSeconds=1,rate=32000,peakCeiling=1.0,
        affirmationMy=tracks[1],affirmationYou=tracks[2],myDb=0.0,youDb=0.0
    ),four,{},{false})
    check(four.length()==44+32000L*4)
    val output=java.io.RandomAccessFile(four,"r").use{r->
        r.seek(44+100L*4)
        val sample=r.readUnsignedByte() or (r.readByte().toInt() shl 8)
        sample.toShort().toInt()/32768.0
    }
    check(kotlin.math.abs(output-.50)<.003){"Each enabled affirmation must be included exactly once, got $output"}
    val none=File(base,"disabled-affirmations.wav")
    NativeSubliminal.mix(NativeSubliminal.Settings(null,null,tracks[0],null,1,32000,
        affirmationMy=null,affirmationYou=null,primaryDb=0.0),none,{},{false})
    val only=java.io.RandomAccessFile(none,"r").use{r->
        r.seek(44+100L*4); val x=r.readUnsignedByte() or (r.readByte().toInt() shl 8);x.toShort().toInt()/32768.0
    }
    check(kotlin.math.abs(only-.05)<.003)
    check(runCatching{NativeSubliminal.mix(NativeSubliminal.Settings(null,null,null,null,1),File(base,"empty.wav"),{},{false})}.isFailure)
    println("PASS: 4 optional independently looped affirmation files, accurate mix, disabled-file isolation, carrier optional")
    println("PASS: checkpoint WAV format, 250ms exact silence, native mix time, output WAV size")
}
