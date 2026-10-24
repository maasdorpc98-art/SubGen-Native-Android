package com.subgen.nativeapp

import java.io.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.math.*

/** Bounded-memory PCM processing. Float checkpoints retain the synthesizer's samples;
 * PCM24 is produced only at export. No voice model or protected encoding is changed. */
object HighQualityPcm {
    const val VERSION="hq-pcm-v1"
    data class Info(val rate:Int,val channels:Int,val bits:Int,val format:Int,val frames:Long,val offset:Long) {
        val bytesPerFrame:Int get()=channels*(bits/8)
    }
    data class Result(val seconds:Double,val bytes:Long,val peak:Double,val appliedGainDb:Double,val rate:Int,val channels:Int)
    fun inspect(file:File):Info=RandomAccessFile(file,"r").use { r->
        fun code():String=ByteArray(4).also{r.readFully(it)}.toString(Charsets.US_ASCII)
        fun u16()=java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(r.readShort()))
        fun u32()=Integer.toUnsignedLong(Integer.reverseBytes(r.readInt()))
        require(r.length()>=44&&code()=="RIFF"){"Invalid PCM WAV"};u32();require(code()=="WAVE"){"Invalid WAVE header"}
        var format=0;var channels=0;var rate=0;var bits=0;var offset=-1L;var data=0L;var count=0
        while(r.filePointer+8<=r.length()&&count++<1024){
            val tag=code();val size=u32();val start=r.filePointer
            require(size<=r.length()-start){"Truncated WAV chunk"}
            if(tag=="fmt "){
                require(size>=16);format=u16();channels=u16();rate=u32().toInt();u32();u16();bits=u16()
                if(format==65534){require(size>=40);r.seek(start+24);format=u16()}
            }else if(tag=="data"){offset=start;data=size;break}
            r.seek(start+size+(size and 1L))
        }
        require(channels in 1..2&&rate in 8000..192000){"Unsupported WAV channel layout or rate"}
        require((format==1&&bits in setOf(16,24,32))||(format==3&&bits==32)){"HQ requires PCM16/24/32 or float32 WAV"}
        require(offset>=0&&data>0&&data%(channels*(bits/8))==0L){"Missing or incomplete WAV frames"}
        Info(rate,channels,bits,format,data/(channels*(bits/8)),offset)
    }
    fun supported(file:File)=runCatching{inspect(file);true}.getOrDefault(false)
    class Reader(val file:File):AutoCloseable {
        val info=inspect(file)
        private val input=RandomAccessFile(file,"r")
        private val bytes=ByteArray(8192*info.bytesPerFrame)
        private var start=-1L;private var count=0
        fun at(frame:Long,ch:Int):Double {
            if(frame<0||frame>=info.frames)return 0.0
            if(frame<start||frame>=start+count){
                start=(frame-64L).coerceAtLeast(0L);count=min(8192L,info.frames-start).toInt()
                input.seek(info.offset+start*info.bytesPerFrame);input.readFully(bytes,0,count*info.bytesPerFrame)
            }
            val p=((frame-start).toInt()*info.channels+ch.coerceIn(0,info.channels-1))*(info.bits/8)
            fun b(i:Int)=bytes[p+i].toInt() and 255
            return when(info.bits){
                16->((b(0) or (b(1) shl 8)).toShort().toDouble()/32768.0)
                24->{val n=b(0) or (b(1) shl 8) or (b(2) shl 16);((n shl 8) shr 8)/8388608.0}
                else->{val n=b(0) or (b(1) shl 8) or (b(2) shl 16) or (b(3) shl 24)
                    if(info.format==3)Float.fromBits(n).toDouble().also{require(it.isFinite()){"Nonfinite float PCM"}} else n/2147483648.0}
            }
        }
        override fun close(){input.close()}
    }
    fun publish(temp:File,target:File){
        try{Files.move(temp.toPath(),target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)}
        catch(_:java.nio.file.AtomicMoveNotSupportedException){Files.move(temp.toPath(),target.toPath(),StandardCopyOption.REPLACE_EXISTING)}
    }
    fun requireSpace(directory:File,bytes:Long){
        val free=directory.usableSpace
        require(free<=0L||free>bytes+32L*1024*1024){"Not enough free space for HQ processing; free storage or use a shorter duration"}
    }
    class Writer(private val target:File,val frames:Long,val rate:Int,val channels:Int,val bits:Int=32,val floating:Boolean=true):AutoCloseable {
        private val temp=File(target.parentFile,target.name+".partial")
        private val output:BufferedOutputStream
        private val buffer=ByteArray(131072);private var used=0;private var samples=0L;private var done=false
        init{
            require(frames>0&&rate in 8000..192000&&channels in 1..2)
            require((floating&&bits==32)||(!floating&&bits in setOf(16,24)))
            val size=frames*channels*(bits/8);require(size<0xffffffffL-36){"WAV exceeds 4 GiB"}
            requireSpace(target.absoluteFile.parentFile,size+44)
            output=BufferedOutputStream(FileOutputStream(temp),131072)
            fun s16(n:Int){output.write(n and 255);output.write(n ushr 8 and 255)}
            fun s32(n:Long){repeat(4){output.write((n ushr (8*it) and 255).toInt())}}
            output.write("RIFF".toByteArray());s32(36+size);output.write("WAVEfmt ".toByteArray());s32(16)
            s16(if(floating)3 else 1);s16(channels);s32(rate.toLong());s32((rate*channels*(bits/8)).toLong())
            s16(channels*(bits/8));s16(bits);output.write("data".toByteArray());s32(size)
        }
        fun put(value:Double){
            require(value.isFinite()){"Invalid audio sample"};check(samples<frames*channels){"Too many PCM samples"}
            val n=if(floating)value.toFloat().also{require(it.isFinite())}.toRawBits()
                else if(bits==24)(value*8388608.0).roundToLong().coerceIn(-8388608L,8388607L).toInt()
                else (value*32768.0).roundToLong().coerceIn(-32768L,32767L).toInt()
            val length=bits/8
            if(used+length>buffer.size)flush()
            repeat(length){buffer[used++]=(n ushr (8*it)).toByte()};samples++
        }
        private fun flush(){if(used>0){output.write(buffer,0,used);used=0}}
        fun finish(){
            check(samples==frames*channels){"Incomplete PCM: $samples/${frames*channels}"}
            flush();output.close();publish(temp,target);done=true
        }
        override fun close(){if(!done){runCatching{output.close()};temp.delete()}}
    }
    fun sha(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(131072).use { s->val b=ByteArray(131072);while(true){val n=s.read(b);if(n<0)break;digest.update(b,0,n)} }
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
    fun key(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}
    fun writeVoice(file:File,samples:FloatArray,rate:Int){
        Writer(file,samples.size.toLong(),rate,1).use { w->samples.forEach{w.put(it.toDouble())};w.finish() }
    }
    /** A fixed polyphase filter is computed once per conversion, never per output sample. */
    class Resampler(private val source:Reader,val outputRate:Int,val speed:Double=1.0) {
        private val step=source.info.rate.toDouble()*speed/outputRate
        val frames:Long=max(1L,round(source.info.frames/step).toLong())
        private val unity=abs(step-1.0)<1e-12
        private val taps=32;private val phases=512
        private val weights=if(unity)emptyArray() else Array(phases){p->
            val fraction=p.toDouble()/phases;val cutoff=min(1.0,1.0/step)*.94
            DoubleArray(taps){k->val x=k-15-fraction
                val sinc=if(abs(x*cutoff)<1e-12)1.0 else sin(PI*x*cutoff)/(PI*x*cutoff)
                cutoff*sinc*(.5+.5*cos(PI*x/16.0))}.also{a->val sum=a.sum();for(k in a.indices)a[k]/=sum}
        }
        init{require(speed.isFinite()&&speed in .1..8.0)}
        fun value(frame:Long,ch:Int):Double {
            if(unity)return source.at(frame,ch)
            val position=frame*step;val whole=floor(position).toLong();val fraction=position-whole
            val a=weights[(fraction*phases).toInt().coerceIn(0,phases-1)]
            var sum=0.0
            for(k in a.indices)sum+=source.at((whole+k-15).coerceIn(0,source.info.frames-1),ch)*a[k]
            return sum
        }
    }
    fun resample(input:File,target:File,rate:Int,channels:Int,speed:Double=1.0,maxFrames:Long=Long.MAX_VALUE,
                 stop:()->Boolean={false}):Info {
        Reader(input).use { r->val sampler=Resampler(r,rate,speed);val frames=min(sampler.frames,maxFrames)
            Writer(target,frames,rate,channels).use { w->
                for(i in 0 until frames){if(i%8192L==0L&&stop())throw InterruptedException("Audio conversion cancelled")
                    if(channels==1&&r.info.channels==2)w.put((sampler.value(i,0)+sampler.value(i,1))*.5)
                    else repeat(channels){w.put(sampler.value(i,it))}}
                w.finish()
            }
        };return inspect(target)
    }
    fun normalized16(input:File,target:File,stop:()->Boolean={false}):Long {
        val original=inspect(input);require(original.frames<=original.rate*720L){"NeuroMod source exceeds 12 minutes"}
        Reader(input).use { r->val s=Resampler(r,24000);Writer(target,s.frames,24000,1,16,false).use { w->
            for(i in 0 until s.frames){if(i%8192L==0L&&stop())throw InterruptedException("Normalization cancelled")
                w.put(if(r.info.channels==2)(s.value(i,0)+s.value(i,1))*.5 else s.value(i,0))}
            w.finish();return s.frames
        } }
    }
    /** A small deterministic TPDF dither is applied only at the final PCM24 conversion. */
    private class Dither {private var state=0x6532178
        private fun uniform():Double {state=state xor (state shl 13);state=state xor (state ushr 17);state=state xor (state shl 5)
            return (state.toLong() and 0xffffffffL)/4294967296.0}
        fun next()=(uniform()-uniform())/8388608.0
    }
    fun export24(input:File,target:File,rate:Int,channels:Int,volumeDb:Double=0.0,ceilingDb:Double=-1.0,
                 fadeSeconds:Double=0.0,normalize:Boolean=false,stop:()->Boolean={false}):Result {
        require(rate in 8000..192000&&channels in 1..2){"Invalid output rate or channels"}
        require(fadeSeconds.isFinite()&&fadeSeconds>=0){"Invalid fade duration"}
        require(input.canonicalPath!=target.canonicalPath);require(volumeDb.isFinite()&&volumeDb in -60.0..24.0)
        require(ceilingDb.isFinite()&&ceilingDb in -60.0..0.0)
        val meta=inspect(input)
        val estimatedFrames=round(meta.frames.toDouble()*rate/meta.rate).toLong()
        requireSpace(target.absoluteFile.parentFile,estimatedFrames*channels*(if(meta.rate==rate)3L else 7L)+88)
        val converted=File(target.parentFile,target.name+".resampled.wav")
        val source=if(meta.rate!=rate){resample(input,converted,rate,channels,stop=stop);converted}else input
        try {Reader(source).use { r->
            fun sample(i:Long,ch:Int)=if(channels==1&&r.info.channels==2)(r.at(i,0)+r.at(i,1))*.5 else r.at(i,ch)
            var peak=0.0
            for(i in 0 until r.info.frames){if(i%8192L==0L&&stop())throw InterruptedException("Peak scan cancelled")
                repeat(channels){peak=max(peak,abs(sample(i,it)))}}
            val cap=(10.0.pow(ceilingDb/20.0)-2.0/8388608.0).coerceAtLeast(0.0)
            val requested=10.0.pow(volumeDb/20.0);val gain=if(peak>0){if(normalize)cap/peak else min(requested,cap/peak)}else requested
            val fade=min((rate*fadeSeconds).roundToLong(),r.info.frames/4)
            val dither=Dither()
            Writer(target,r.info.frames,rate,channels,24,false).use { w->
                for(i in 0 until r.info.frames){if(i%8192L==0L&&stop())throw InterruptedException("PCM24 export cancelled")
                    val ramp=if(fade==0L)1.0 else min(1.0,min(i.toDouble()/fade,(r.info.frames-i-1).toDouble()/fade)).coerceAtLeast(0.0)
                    repeat(channels){val value=sample(i,it)*gain*ramp;w.put(if(value==0.0)0.0 else value+dither.next())}}
                w.finish()
            }
            return Result(r.info.frames.toDouble()/rate,target.length(),peak,20*log10(gain.coerceAtLeast(1e-12)),rate,channels)
        }}finally{converted.delete()}
    }
}
