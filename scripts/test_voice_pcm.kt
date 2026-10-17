import com.subgen.nativeapp.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.*

fun main(){
    val dir=File("/tmp/subgen-hyper-pcm").apply{mkdirs()}
    val clip=File(dir,"voice.wav")
    // 0.1 amplitude, real sinusoidal speech proxy, 0.5 seconds 24 kHz.
    val samples=FloatArray(12000){i->(.1*sin(2*PI*i*330/24000)).toFloat()}
    WavIO.writeMono(clip,samples,24000)
    fun output(db:Double):ShortArray {
        val nodes=listOf(ScriptNode.Speech("test","NORMAL",gainDb=db),ScriptNode.Pause(.1),ScriptNode.Pause(.2))
        val f=File(dir,"out-$db.wav")
        val seconds=WavIO.assemble(nodes,listOf(clip,null,null),f)
        check(abs(seconds-.8)<1.0/24000)
        val raw=f.readBytes()
        check(raw.size==44+(12000+2400+4800)*4)
        val speech=ShortArray(12000)
        for(i in speech.indices){val pos=44+i*4
            speech[i]=((raw[pos].toInt() and 255) or ((raw[pos+1].toInt() and 255) shl 8)).toShort()
        }
        for(pos in 44+12000*4 until raw.size)check(raw[pos].toInt()==0){"Pause PCM not exact silence"}
        return speech
    }
    val normal=output(0.0)
    val quiet=output(-6.0)
    val bright=output(3.0)
    fun rms(data:ShortArray):Double=sqrt(data.drop(192).map{it.toDouble()*it}.average())
    val r0=rms(normal);val rQuiet=rms(quiet);val rBright=rms(bright)
    check(abs(20*log10(rQuiet/r0)+6.0)<0.15){"Volume attenuation wrong"}
    check(abs(20*log10(rBright/r0)-3.0)<0.15){"Volume amplification wrong"}
    println("PASS: 24kHz exact 0.1+0.2 pause PCM, section gain -6/+3dB, unchanged length, no clipping")
}