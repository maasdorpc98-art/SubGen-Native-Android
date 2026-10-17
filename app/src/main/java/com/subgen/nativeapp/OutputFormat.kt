package com.subgen.nativeapp

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.*

/** Streaming, low-memory export conversion of internal stereo 24 kHz PCM16.
 * Same-language audio is never sped up or shortened. 16 kHz uses band-limited
 * sinc downsampling; upsampling uses cubic interpolation. All processing is offline.
 */
object OutputFormat {
    val rates = intArrayOf(16000,22050,24000,32000,44100,48000)
    data class Result(val seconds:Double,val bytes:Long,val sourcePeak:Double,val appliedGainDb:Double,
                      val rate:Int,val channels:Int)

    private fun lerpCubic(y0:Double,y1:Double,y2:Double,y3:Double,t:Double):Double {
        val a0=-0.5*y0+1.5*y1-1.5*y2+0.5*y3
        val a1=y0-2.5*y1+2.0*y2-0.5*y3
        val a2=-0.5*y0+0.5*y2
        return ((a0*t+a1)*t+a2)*t+y1
    }
    private fun sinc(x:Double)=if(abs(x)<1e-9)1.0 else sin(PI*x)/(PI*x)
    private fun lowPassKernel(cutoff:Double):Array<DoubleArray> {
        val coefficients=Array(256){DoubleArray(16)}
        for(p in 0..255){
            val frac=p/256.0
            var total=0.0
            for(k in 0..15) {
                val x=(k-7)-frac
                val weight=cutoff*sinc(x*cutoff)*(.5+.5*cos(PI*x/8.0))
                coefficients[p][k]=weight
                total+=weight
            }
            for(k in 0..15)coefficients[p][k]/=total
        }
        return coefficients
    }
    fun convert(source:File,target:File,rate:Int=24000,channels:Int=2,
                volumeDb:Double=0.0,ceilingDb:Double=-1.0):Result {
        require(rate in rates){"Unsupported output rate"}
        require(channels==1 || channels==2){"Output must be mono or stereo"}
        require(volumeDb.isFinite()&&volumeDb in -30.0..12.0){"Output volume out of range"}
        require(ceilingDb.isFinite()&&ceilingDb in -12.0..0.0){"Peak ceiling out of range"}
        require(source.canonicalPath!=target.canonicalPath){"Use separate output file"}
        val srcFrames=RandomAccessFile(source,"r").use{input->
            require(input.length()>=44 && input.length()%4L==0L){"Invalid original WAV size"}
            input.seek(0)
            val header=ByteArray(44);input.readFully(header)
            fun u16(offset:Int)=(header[offset].toInt() and 255) or ((header[offset+1].toInt() and 255) shl 8)
            fun u32(offset:Int)=(0..3).fold(0L){acc,k->acc or ((header[offset+k].toLong() and 255L) shl (k*8))}
            require(String(header,0,4,Charsets.US_ASCII)=="RIFF" &&
                    String(header,8,4,Charsets.US_ASCII)=="WAVE" &&
                    u16(20)==1&&u16(22)==2&&u32(24)==24000L&&u16(34)==16){
                "Expected 24 kHz stereo PCM16 WAV"
            }
            val bytes=u32(40)
            require(bytes>=0 && bytes+44L<=input.length() && bytes%4L==0L){"Invalid original WAV data length"}
            bytes/4L
        }
        val outFrames=round(srcFrames.toDouble()*rate/24000.0).toLong()
        require(outFrames>=0 && outFrames*channels*2L<0xffffffffL-36L){"WAV output exceeds 4 GiB limit"}
        // Peak scan is one bounded sequential pass; global attenuation preserves dynamics
        // and avoids hard compression. Any cubic overshoot is additionally peak-clamped.
        var peak=0.0
        RandomAccessFile(source,"r").use{input->
            input.seek(44)
            val data=ByteArray(65536)
            var remain=srcFrames*4L
            while(remain>0) {
                val read=min(data.size.toLong(),remain).toInt()
                input.readFully(data,0,read)
                for(i in 0 until read step 2){
                    val sample=((data[i].toInt() and 255) or ((data[i+1].toInt() and 255) shl 8)).toShort()
                    peak=max(peak,abs(sample.toDouble()/32768.0))
                }
                remain-=read
            }
        }
        val cap=10.0.pow(ceilingDb/20.0)
        val requested=10.0.pow(volumeDb/20.0)
        val gain=if(peak>0.0) min(requested,cap/peak) else requested
        val appliedDb=20.0*log10(gain.coerceAtLeast(1e-12))
        val tmp=File(target.parentFile,target.name+".partial")
        try{
            RandomAccessFile(source,"r").use { input ->
                FileOutputStream(tmp).buffered(131072).use { output ->
                    WavIO.writeHeader(output,outFrames,rate,channels)
                    val bytes=ByteArray(32768*4)
                    val samples=ShortArray(32768*2)
                    var windowStart=0L
                    var windowCount=0
                    fun refill(base:Long){
                        windowStart=(base-10).coerceAtLeast(0L)
                        windowCount=min(32768L,srcFrames-windowStart).coerceAtLeast(0L).toInt()
                        if(windowCount==0)return
                        input.seek(44L+windowStart*4)
                        input.readFully(bytes,0,windowCount*4)
                        for(i in 0 until windowCount*2) {
                            val j=i*2
                            samples[i]=((bytes[j].toInt() and 255) or
                                ((bytes[j+1].toInt() and 255) shl 8)).toShort()
                        }
                    }
                    fun channel(frame:Long,ch:Int):Double {
                        if(frame<0 || frame>=srcFrames) return 0.0
                        if(frame<windowStart || frame>=windowStart+windowCount)refill(frame)
                        return samples[((frame-windowStart).toInt()*2)+ch].toDouble()/32768.0
                    }
                    val kernel=if(rate<24000)lowPassKernel(rate.toDouble()/24000.0) else null
                    val outputBytes=ByteArray(131072)
                    var filled=0
                    fun putPcm(value:Double) {
                        val clamped=(value*gain).coerceIn(-cap,cap)
                        val pcm=WavIO.floatToS16(clamped.toFloat())
                        outputBytes[filled++]=(pcm and 255).toByte()
                        outputBytes[filled++]=(pcm ushr 8).toByte()
                        if(filled==outputBytes.size){output.write(outputBytes);filled=0}
                    }
                    var idx=0L
                    while(idx<outFrames) {
                        val position=idx.toDouble()*24000.0/rate
                        val whole=floor(position).toLong()
                        val frac=position-whole
                        fun value(ch:Int):Double {
                            if(rate==24000)return channel(whole,ch)
                            if(kernel!=null){
                                val taps=kernel[(frac*256.0).roundToInt().coerceIn(0,255)]
                                var sum=0.0
                                for(k in 0..15)sum+=channel(whole+k-7L,ch)*taps[k]
                                return sum
                            }
                            return lerpCubic(channel(whole-1,ch),channel(whole,ch),
                                channel(whole+1,ch),channel(whole+2,ch),frac)
                        }
                        val left=value(0)
                        val right=value(1)
                        if(channels==1)putPcm((left+right)*.5)
                        else{putPcm(left);putPcm(right)}
                        idx++
                    }
                    if(filled>0)output.write(outputBytes,0,filled)
                }
            }
            if(target.exists())target.delete()
            check(tmp.renameTo(target)){"Cannot commit formatted WAV"}
        }catch(error:Throwable){tmp.delete();throw error}
        return Result(srcFrames/24000.0,target.length(),peak,appliedDb,rate,channels)
    }
}
