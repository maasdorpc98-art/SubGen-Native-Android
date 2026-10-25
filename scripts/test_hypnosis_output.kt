import com.subgen.nativeapp.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.*

fun main() {
    val dir=File("/tmp/subgen_hypnosis_format_tests").apply{mkdirs()}
    val input=File(dir,"source.wav")
    // Speech PCM with silence in second half. Peak near 0.25 ensures unmodified passthrough.
    val samples=FloatArray(24000){i->if(i<12000)sin(i*2.0*PI*440.0/24000.0).toFloat()*.25f else 0f}
    WavIO.writeMono(File(dir,"mono.wav"),samples,24000)
    val nodes=listOf(ScriptNode.Speech("hello","SOFT"))
    WavIO.assemble(nodes,listOf(File(dir,"mono.wav")),input)
    val output=File(dir,"converted.wav")
    val base=OutputFormat.convert(input,output,24000,2,0.0,-1.0)
    check(base.bytes==input.length())
    check(input.readBytes().contentEquals(output.readBytes())){"PCM passthrough must be bit-exact"}
    for(rate in OutputFormat.rates) for(ch in 1..2) {
        val result=OutputFormat.convert(input,output,rate,ch,3.0,-1.0)
        val frames=round(24000.0*rate/24000).toLong()
        check(result.bytes==44+frames*ch*2L){"Wrong byte count $rate $ch"}
        RandomAccessFile(output,"r").use{f->
            f.seek(22);val channelCount=java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(f.readShort()))
            f.seek(24);val fileRate=Integer.reverseBytes(f.readInt())
            check(channelCount==ch && fileRate==rate){"Header mismatch $rate/$ch"}
            f.seek(44)
            var peak=0
            var pos=44L
            while(pos+1<f.length()){
                val v=java.lang.Short.reverseBytes(f.readShort()).toInt()
                peak=max(peak,abs(v))
                pos+=2
            }
            check(peak<=29300){"Exceeded peak ceiling: $peak at $rate/$ch"}
        }
    }
    val summary=ScriptTimeline.analyze("[SECTION: INDUCTION]\n[SOFT]\nHello there.\n[PAUSE: 5]\n[BREATHE]\n[SECTION: MAIN]\nI feel calm.")
    check(summary.pauseBlocks==2 && summary.silenceSeconds==10.0)
    check(summary.speechBlocks==2 && summary.sections==2)
    check(ScriptTimeline.estimatedWavBytes(10.0,48000,2)==44L+10*48000*4L)
    check(summary.rows.first().kind=="SECTION")
    println("PASS: all six output sample rates, both channel layouts, peak ceiling, exact 24 kHz PCM passthrough, timeline")
}
