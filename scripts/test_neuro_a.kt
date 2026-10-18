import com.subgen.nativeapp.NeuroModulationA
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*
fun main() {
    val dir=File(System.getProperty("java.io.tmpdir"),"subgen-neuro-test").apply{mkdirs()}
    fun input(hz:Int,channels:Int,sec:Double,name:String):File{
        val f=File(dir,name);val n=(hz*sec).roundToInt();FileOutputStream(f).use{o->
            val data=n*channels*2; fun w16(v:Int){o.write(v and 255);o.write(v ushr 8 and 255)};fun w32(v:Int){for(i in 0..3)o.write(v ushr (8*i) and 255)}
            o.write("RIFF".toByteArray());w32(36+data);o.write("WAVEfmt ".toByteArray());w32(16);w16(1);w16(channels);w32(hz);w32(hz*channels*2);w16(channels*2);w16(16);o.write("data".toByteArray());w32(data)
            for(i in 0 until n){val sample=(sin(i.toDouble()*2*Math.PI*440/hz)*18000).roundToInt();repeat(channels){w16(sample)}}
        };return f
    }
    for(pair in listOf(24000 to 1,44100 to 2,48000 to 1)){
        val infile=input(pair.first,pair.second,1.5,"in-${pair.first}-${pair.second}.wav")
        val norm=File(dir,"normalized-${pair.first}.wav")
        val frames=NeuroModulationA.normalize(infile,norm)
        check(frames==36000L)
        val master=File(dir,"master-${pair.first}.wav")
        val listening=File(dir,"listening-${pair.first}.wav")
        val report=NeuroModulationA.generate(norm,master,listening,NeuroModulationA.Config(durationSeconds=2.0))
        check(report.sourceFrames==36000L && report.outputFrames==192000L && report.recoveredFrames==36000L)
        check(master.length()==44L+192000*4 && listening.length()==44L+192000*2)
        check(report.peakDbfs< -30.0)
        check(report.sha256.length==64)
        val mixCopy=File(dir,"mixer-${pair.first}.wav")
        NeuroModulationA.createMixerCopy(master,mixCopy,report.peakDbfs)
        check(mixCopy.length()==44L+192000*2)
        println("PASS: ${pair.first} Hz/${pair.second}ch → PCM16 24k mono → native A 96k PCM32 → exact digital recovery")
    }
    val infile=input(24000,1,5.0,"too-long.wav")
    val norm=File(dir,"long-norm.wav");NeuroModulationA.normalize(infile,norm)
    check(runCatching{NeuroModulationA.generate(norm,File(dir,"invalid.wav"),File(dir,"invalid-listen.wav"),NeuroModulationA.Config(durationSeconds=1.0))}.isFailure)
    check(runCatching{NeuroModulationA.Config(depth=1.0).checked()}.isFailure)
    println("PASS: invalid duration/capacity/modulation rejected without silent truncation")
}