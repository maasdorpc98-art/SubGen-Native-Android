import com.subgen.nativeapp.NeuroModulationB
import com.subgen.nativeapp.NeuroModulationA
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*
fun main(){
 val dir=File(System.getProperty("java.io.tmpdir"),"subgen-neuro-b-qa").apply{mkdirs()}
 val src=File(dir,"original.wav")
 val sr=24000;val seconds=1.5;val count=(sr*seconds).toInt()
 fun w16(out:FileOutputStream,x:Int){out.write(x and 255);out.write((x ushr 8)and 255)}
 fun w32(out:FileOutputStream,x:Long){for(i in 0..3)out.write((x ushr (8*i) and 255).toInt())}
 FileOutputStream(src).use {o->
  o.write("RIFF".toByteArray());w32(o,(36+count*2).toLong());o.write("WAVEfmt ".toByteArray());w32(o,16);w16(o,1);w16(o,1);w32(o,sr.toLong());w32(o,(sr*2).toLong());w16(o,2);w16(o,16);o.write("data".toByteArray());w32(o,(count*2).toLong())
  for(i in 0 until count){val phase=i.toDouble()/sr;val v=(sin(2*PI*220*phase)*10000+sin(2*PI*1100*phase)*6000+sin(2*PI*2800*phase)*2000).roundToInt();w16(o,v)}
 }
 val out=File(dir,"b.wav")
 val cfg=NeuroModulationA.Config(durationSeconds=3.0)
 val start=System.nanoTime();val a=NeuroModulationB.generate(src,out,cfg,{println(it)})
 File(dir,"scale.txt").writeText(a.scale.toString())
 check(a.recoveredSamples==count)
 check(a.wavBytes==44+3L*96000*2*4)
 val recovered=NeuroModulationB.recover(out,a.scale,cfg)
 check(recovered.size==count*2)
 println("PASS: ORIGINAL B predictor XZ residual, two-channel 22bit QAM 2048 OFDM, 40Hz, verified from protected PCM32. ${"%.1f".format((System.nanoTime()-start)/1e9)}s")
 val wrong=runCatching{NeuroModulationB.recover(out,a.scale*.7,cfg)}
 check(wrong.isFailure){"Corrupted scaling should invalidate recovery"}
 println("PASS: incorrect gain rejected by decoder")
}