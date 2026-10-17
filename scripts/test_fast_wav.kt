import com.subgen.nativeapp.*
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.sin

/** Exact timing, legacy PCM conversion and binaural regression tests. */
fun main() {
    val dir=File("/tmp/subgen_fast_pcm_tests").apply { mkdirs() }
    val input=File(dir,"source.wav")
    val samples=FloatArray(24000) { n -> (sin(n.toDouble()/29.0)*0.73).toFloat() }
    WavIO.writeMono(input,samples,24000)
    val clips=listOf(ScriptNode.Speech("one","NORMAL"),ScriptNode.Pause(14.25),
        ScriptNode.Speech("two","SOFT"))
    val checkpoints=listOf(input,null,input)
    val output=File(dir,"fast-stereo.wav")
    val start=System.nanoTime()
    val duration=WavIO.assemble(clips,checkpoints,output)
    val elapsedMs=(System.nanoTime()-start)/1000000
    check(duration==16.25)
    check(output.length()==44L+(16.25*24000*4).toLong())
    RandomAccessFile(output,"r").use { raf ->
        raf.seek(44)
        val firstLeft=raf.readUnsignedByte() or (raf.readUnsignedByte() shl 8)
        val firstRight=raf.readUnsignedByte() or (raf.readUnsignedByte() shl 8)
        check(firstLeft==firstRight)
        // Existing PCM conversion is intentionally preserved during fast interleaving.
        val index=8123
        raf.seek(44+index*4L)
        val b0=raf.readUnsignedByte()
        val b1=raf.readUnsignedByte()
        val b2=raf.readUnsignedByte()
        val b3=raf.readUnsignedByte()
        val expected=WavIO.floatToS16((WavIO.floatToS16(samples[index])/32768.0).toFloat())
        check((b0 or (b1 shl 8))==(expected and 65535))
        check(b0==b2&&b1==b3)
        // Every pause frame must be exactly zero, including first and final frame.
        for(frame in listOf(24000L,24001L,24000L+(14.25*24000).toLong()-1)) {
            raf.seek(44+frame*4L)
            check((0..3).all{ raf.readUnsignedByte()==0 })
        }
    }
    val bfile=File(dir,"fast-binaural.wav")
    val t2=System.nanoTime()
    val binDuration=WavIO.assemble(clips,checkpoints,bfile,binOn=true)
    val binMs=(System.nanoTime()-t2)/1000000
    check(binDuration==duration)
    check(bfile.length()==output.length())
    RandomAccessFile(bfile,"r").use{raf->
        raf.seek(44+24000L*4+16)
        val data=ByteArray(120);raf.readFully(data)
        check(data.any{it.toInt()!=0}){"Binaural tone disappeared during pause"}
    }
    println("PASS: exact silence, previous speech amplitude, left/right channels and binaural tone")
    println("Benchmark assembly: dry="+elapsedMs+" ms, binaural="+binMs+" ms for "+duration+"s of output")
}
