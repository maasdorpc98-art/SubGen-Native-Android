package com.subgen.nativeapp

import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import kotlin.math.*

/** Native, source-derived Experiment 001 VERSION A, matching the reference integer mapping.
 * No web server, WebView, OFDM imitation, sample recovery claims after mixing, or DSP time stretch.
 * Version B is a distinct pending native port of the supplied Python encoder.
 */
object NeuroModulationA {
    const val MASTER_RATE=96000
    const val SOURCE_RATE=24000
    private const val FACTOR=2000
    private const val TWO_PI=2.0*Math.PI
    private const val MAX_SOURCE_SECONDS=720
    private const val PAGE=4*1024*1024L

    data class Config(val durationSeconds:Double=60.0,val frequencyHz:Double=40.0,val depth:Double=.90) {
        fun checked():Config {
            require(durationSeconds.isFinite() && durationSeconds in 1.0..180.0){"Output duration must be 1–180 seconds"}
            require(frequencyHz.isFinite() && frequencyHz in 0.5..100.0){"Frequency must be 0.5–100 Hz"}
            require(depth.isFinite() && depth in 0.0..0.98){"Modulation depth must be 0–98%"}
            return this
        }
    }
    data class Result(val sourceFrames:Long,val outputFrames:Long,val recoveredFrames:Long,
                      val peakDbfs:Double,val sha256:String,val masterBytes:Long,val listeningBytes:Long)

    data class PcmInput(val rate:Int,val channels:Int,val frames:Long,val offset:Long)
    fun inspect(file:File):PcmInput=RandomAccessFile(file,"r").use{r->
        require(r.length()>=44){"WAV input too small"}
        fun code():String=ByteArray(4).also{r.readFully(it)}.toString(Charsets.US_ASCII)
        fun u32():Long=Integer.toUnsignedLong(Integer.reverseBytes(r.readInt()))
        fun u16():Int=java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(r.readShort()))
        require(code()=="RIFF"){"Input must be RIFF WAV"};r.skipBytes(4)
        require(code()=="WAVE"){"Missing WAVE signature"}
        var rate=0;var channels=0;var bits=0;var format=0;var at=-1L;var size=0L
        var chunks=0
        while(r.filePointer+8<=r.length() && chunks++<256){
            val name=code();val length=u32();val start=r.filePointer
            require(length<=r.length()-start){"Corrupt WAV chunk size"}
            if(name=="fmt "){
                require(length>=16){"Invalid WAV fmt chunk"}
                format=u16();channels=u16();rate=u32().toInt();r.skipBytes(6);bits=u16()
            }
            if(name=="data"){at=start;size=length}
            if(at>=0 && format!=0)break
            r.seek(start+length+(length and 1L))
        }
        require(format==1 && bits==16 && channels in 1..2 && rate in 8000..96000){
            "Input must be PCM16 mono/stereo WAV. Decode compressed audio with Android MediaCodec first."
        }
        require(at>=0 && size>0 && size%(channels*2)==0L && at+size<=r.length()){"Invalid WAV audio data"}
        val frames=size/(channels*2)
        require(frames<=MAX_SOURCE_SECONDS.toLong()*rate){"Version A limits normalized source to 12 minutes"}
        PcmInput(rate,channels,frames,at)
    }

    /** Maps small source windows instead of loading multi-minute audio into Java heap. */
    private class Samples(file:File,private val meta:PcmInput):AutoCloseable {
        private val handle=RandomAccessFile(file,"r")
        private val channel=handle.channel
        private var mapped:MappedByteBuffer?=null
        private var start=-1L
        private var length=0L
        private fun window(byte:Long,bytes:Int):MappedByteBuffer {
            if(mapped==null || byte<start || byte+bytes>start+length){
                start=byte/PAGE*PAGE
                length=min(PAGE,channel.size()-start)
                mapped=channel.map(FileChannel.MapMode.READ_ONLY,start,length).apply{order(ByteOrder.LITTLE_ENDIAN)}
            }
            return mapped!!
        }
        fun at(frame:Long,ch:Int=0):Int {
            val byte=meta.offset+(frame.coerceIn(0,meta.frames-1)*meta.channels+ch)*2
            return window(byte,2).getShort((byte-start).toInt()).toInt()
        }
        fun int32(index:Long):Int {
            val byte=meta.offset+index*4
            return window(byte,4).getInt((byte-start).toInt())
        }
        override fun close(){mapped=null;handle.close()}
    }
    private fun writeHeader(out:java.io.OutputStream,frames:Long,rate:Int,channels:Int,bits:Int){
        val size=frames*channels*(bits/8)
        require(size+36<0x1_0000_0000L){"Output WAV exceeds RIFF size limit"}
        fun write32(n:Long){for(shift in 0..3)out.write(((n ushr (shift*8))and 255).toInt())}
        fun write16(n:Int){out.write(n and 255);out.write((n ushr 8)and 255)}
        out.write("RIFF".toByteArray());write32(36+size);out.write("WAVEfmt ".toByteArray());write32(16)
        write16(1);write16(channels);write32(rate.toLong());write32((rate*channels*(bits/8)).toLong())
        write16(channels*(bits/8));write16(bits);out.write("data".toByteArray());write32(size)
    }
    private fun shortAt(reader:Samples,meta:PcmInput,index:Long):Int {
        if(meta.channels==1)return reader.at(index)
        return ((reader.at(index,0)+reader.at(index,1))/2.0).roundToInt().coerceIn(-32768,32767)
    }
    /** Source normalization is explicitly distinct from original uploaded file. */
    fun normalize(input:File,normalized:File,progress:(String)->Unit={},stop:()->Boolean={false}):Long {
        val meta=inspect(input)
        val count=(meta.frames.toDouble()*SOURCE_RATE/meta.rate).roundToLong()
        require(count in 1..MAX_SOURCE_SECONDS.toLong()*SOURCE_RATE){"Normalized source exceeds 12-minute limit"}
        val cutoff=min(1.0,SOURCE_RATE.toDouble()/meta.rate)
        val phases=1024
        val weights=Array(phases){p->DoubleArray(32){j->
            val t=j-15-p.toDouble()/phases
            val u=t*cutoff
            val sinc=if(abs(u)<1e-12)1.0 else sin(Math.PI*u)/(Math.PI*u)
            val win=if(abs(t)>=16)0.0 else .5+.5*cos(Math.PI*t/16)
            sinc*win*cutoff
        }}
        val tmp=File(normalized.parentFile,normalized.name+".partial")
        try {
            Samples(input,meta).use{samples->
                BufferedOutputStream(FileOutputStream(tmp),131072).use{out->
                    writeHeader(out,count,SOURCE_RATE,1,16)
                    val buf=ByteArray(65536);var used=0
                    for(i in 0 until count){
                        if(i%24000L==0L){if(stop())throw InterruptedException("Normalization cancelled");if(i%240000L==0L)progress("Normalizing source: ${i*100/count}%")}
                        val value=if(meta.rate==SOURCE_RATE)shortAt(samples,meta,i)
                        else {
                            val position=i.toDouble()*meta.rate/SOURCE_RATE
                            val center=floor(position).toLong()
                            val phase=((position-center)*phases).roundToInt().coerceIn(0,phases-1)
                            val w=weights[phase]
                            var weighted=0.0;var sum=0.0
                            for(tap in 0..31){
                                val at=center+tap-15L
                                if(at<0 || at>=meta.frames)continue
                                weighted+=shortAt(samples,meta,at)*w[tap];sum+=w[tap]
                            }
                            (if(abs(sum)>1e-12)weighted/sum else 0.0).roundToInt().coerceIn(-32768,32767)
                        }
                        buf[used++]=(value and 255).toByte();buf[used++]=((value shr 8)and 255).toByte()
                        if(used==buf.size){out.write(buf);used=0}
                    }
                    if(used>0)out.write(buf,0,used)
                }
            }
            require(tmp.renameTo(normalized)){"Could not publish normalized WAV"}
            return count
        }catch(t:Throwable){tmp.delete();throw t}
    }
    private fun modulation(index:Long,c:Config):Int {
        val envelope=1-c.depth/2+(c.depth/2)*cos(TWO_PI*c.frequencyHz*index/MASTER_RATE)
        return (FACTOR*envelope).roundToInt()
    }
    fun generate(normalized:File,master:File,listening:File,config:Config,
                 progress:(String)->Unit={},stop:()->Boolean={false}):Result {
        val c=config.checked()
        val m=inspect(normalized)
        require(m.channels==1 && m.rate==SOURCE_RATE){"Normalized source must be 24kHz mono PCM16"}
        val n=m.frames
        val total=(c.durationSeconds*MASTER_RATE).roundToLong()
        require(total>=n){"Version A cannot preserve all ${n} source samples in ${c.durationSeconds}s. Choose at least ${"%.3f".format(java.util.Locale.US,n.toDouble()/MASTER_RATE)}s."}
        val masterTmp=File(master.parentFile,master.name+".partial")
        val listenTmp=File(listening.parentFile,listening.name+".partial")
        var peak=0L
        try {
            Samples(normalized,m).use{source->
                BufferedOutputStream(FileOutputStream(masterTmp),131072).use{protected->
                    BufferedOutputStream(FileOutputStream(listenTmp),131072).use{preview->
                        writeHeader(protected,total,MASTER_RATE,1,32)
                        writeHeader(preview,total,MASTER_RATE,1,16)
                        val data=ByteArray(65536);val audible=ByteArray(32768)
                        var di=0;var ai=0;var previousSource=-1L;var sample=0
                        val fade=min((.035*MASTER_RATE).roundToLong(),total/4)
                        for(i in 0 until total){
                            if(i%96000L==0L){if(stop())throw InterruptedException("Generation cancelled");if(i%960000L==0L)progress("Encoding A: ${i*100/total}%")}
                            val src=i*n/total
                            if(src!=previousSource){sample=source.at(src);previousSource=src}
                            val value=sample.toLong()*modulation(i,c)
                            peak=max(peak,abs(value))
                            val v=value.toInt()
                            for(j in 0..3)data[di++]=((v ushr (j*8))and 255).toByte()
                            if(di==data.size){protected.write(data);di=0}
                            val fadeGain=if(fade==0L)1.0 else min(1.0,min(i.toDouble()/fade,(total-1-i).toDouble()/fade)).coerceAtLeast(0.0)
                            val previewValue=(v.toDouble()/65536.0*fadeGain).roundToInt().coerceIn(-32768,32767)
                            audible[ai++]=(previewValue and 255).toByte();audible[ai++]=((previewValue shr 8)and 255).toByte()
                            if(ai==audible.size){preview.write(audible);ai=0}
                        }
                        if(di>0)protected.write(data,0,di)
                        if(ai>0)preview.write(audible,0,ai)
                    }
                }
                val limit=2147483648.0*10.0.pow(-30.0/20.0)
                require(peak<=limit){"Master exceeds original −30dBFS ceiling"}
                val sha=MessageDigest.getInstance("SHA-256")
                progress("Verifying original source recovery from saved WAV…")
                Samples(masterTmp,PcmInput(MASTER_RATE,1,total,44)).use{encoded->
                    for(j in 0 until n){
                        if(j%24000L==0L && stop())throw InterruptedException("Verification cancelled")
                        // A source sample j is always present at output index ceil(j*N/n).
                        val i=(j*total+n-1)/n
                        val value=encoded.int32(i)
                        val g=modulation(i,c)
                        require(g>0 && value%g==0 && value/g==source.at(j)){
                            "Digital roundtrip verification failed at source sample $j"
                        }
                        val pcm=source.at(j)
                        sha.update((pcm and 255).toByte());sha.update(((pcm ushr 8) and 255).toByte())
                    }
                }
                // Keep prior complete exports if a new render or verification fails.
                publish(masterTmp,master);publish(listenTmp,listening)
                return Result(n,total,n,20.0*log10((peak.toDouble()/2147483648.0).coerceAtLeast(1e-12)),
                    sha.digest().joinToString(""){ "%02x".format(it) },master.length(),listening.length())
            }
        }catch(t:Throwable){masterTmp.delete();listenTmp.delete();throw t}
    }
    /** Audition/mixer edition only. Normalizes the already-verified master to a safer
     * -18dBFS peak; NEVER label this as digitally reversible or use it for decoding.
     */
    fun createMixerCopy(master:File,dest:File,peakDbfs:Double,stop:()->Boolean={false}){
        val bytes=master.length()-44L
        require(bytes>0 && bytes%4==0L){"Expected native A 96kHz PCM32 master"}
        val frames=bytes/4
        val gain=if(peakDbfs.isFinite() && peakDbfs>-240)
            10.0.pow((-18.0-peakDbfs)/20.0) else 0.0
        val fade=min((.035*MASTER_RATE).roundToLong(),frames/4)
        val tmp=File(dest.parentFile,dest.name+".partial")
        try {
            Samples(master,PcmInput(MASTER_RATE,1,frames,44)).use{source->
                BufferedOutputStream(FileOutputStream(tmp),131072).use{out->
                    writeHeader(out,frames,MASTER_RATE,1,16)
                    val buf=ByteArray(65536);var used=0
                    for(i in 0 until frames){
                        if(i%96000L==0L && stop())throw InterruptedException("Mixer edition cancelled")
                        val ramp=if(fade==0L)1.0 else min(1.0,min(i.toDouble()/fade,(frames-i-1).toDouble()/fade)).coerceAtLeast(0.0)
                        val sample=(source.int32(i).toDouble()/65536.0*gain*ramp).roundToInt().coerceIn(-32768,32767)
                        buf[used++]=(sample and 255).toByte();buf[used++]=((sample ushr 8)and 255).toByte()
                        if(used==buf.size){out.write(buf);used=0}
                    }
                    if(used>0)out.write(buf,0,used)
                }
            }
            publish(tmp,dest)
        } catch(t:Throwable){tmp.delete();throw t}
    }
    private fun publish(tmp:File,dest:File){if(dest.exists())require(dest.delete()){ "Unable to replace ${dest.name}" };require(tmp.renameTo(dest)){"Unable to publish ${dest.name}"}}
}
