package com.subgen.nativeapp

import java.io.*
import java.security.MessageDigest
import kotlin.math.*

/** Bounded-memory, read-only measurements. RMS is not perceptual loudness/LUFS. */
object HyperAudioQa {
    data class Format(val rate:Int,val channels:Int,val bits:Int,val encoding:Int,val blockAlign:Int,
        val frames:Long,val dataOffset:Long,val dataBytes:Long,val fmt:ByteArray) {
        val seconds get()=frames.toDouble()/rate
        fun exportBytes(cycles:Int)=12L+8L+fmt.size+(fmt.size%2)+8L+dataBytes*cycles+(dataBytes*cycles%2)
    }
    data class Report(val sha256:String,val rate:Int,val channels:Int,val bits:Int,val encoding:Int,val frames:Long,
        val seconds:Double,val peak:Double,val peakDbfs:Double,val railSamples:Long,val rms:Double,val rmsDbfs:Double,
        val silentSeconds:Double,val longestSilenceSeconds:Double,val leadingSilenceSeconds:Double,val trailingSilenceSeconds:Double,
        val seamDelta:Double,val startRmsDbfs:Double,val endRmsDbfs:Double,val seamRmsDifferenceDb:Double,
        val startZeroCrossings:Long,val endZeroCrossings:Long,val threeCycleBytes:Long,val warnings:List<String>)
    private fun four(r:RandomAccessFile)=ByteArray(4).also{r.readFully(it)}.toString(Charsets.US_ASCII)
    private fun u16(r:RandomAccessFile)=r.readUnsignedByte() or (r.readUnsignedByte() shl 8)
    private fun u32(r:RandomAccessFile):Long{var n=0L;repeat(4){n=n or (r.readUnsignedByte().toLong() shl (it*8))};return n}
    fun inspect(file:File):Format=RandomAccessFile(file,"r").use{r->
        require(r.length()>=44){"Truncated WAV"};require(four(r)=="RIFF"){"Only little-endian RIFF WAV is supported"}
        val limit=u32(r)+8;require(limit in 44..r.length()){"Invalid RIFF size"};require(four(r)=="WAVE"){"Not a WAV"}
        var fmt:ByteArray?=null;var offset=-1L;var bytes=0L
        while(r.filePointer+8<=limit){
            val name=four(r);val length=u32(r);val start=r.filePointer
            require(length<=limit-start){"Truncated $name chunk"}
            when(name){
                "fmt "->{require(fmt==null&&length in 16..4096){"Invalid fmt chunk"};fmt=ByteArray(length.toInt()).also{r.readFully(it)}}
                "data"->{require(offset<0){"Multiple WAV data chunks are unsupported"};offset=start;bytes=length}
            }
            r.seek(start+length+(length%2))
        }
        val f=fmt?:error("Missing WAV format");require(offset>=0&&bytes>0){"Missing/empty WAV data"}
        fun word(at:Int)=(f[at].toInt()and 255)or((f[at+1].toInt()and 255)shl 8)
        fun dword(at:Int):Long{var n=0L;repeat(4){n=n or ((f[at+it].toLong()and 255)shl(it*8))};return n}
        val tag=word(0);val channels=word(2);val rate=dword(4).toInt();val align=word(12);val bits=word(14)
        val encoding=if(tag==65534){require(f.size>=40&&word(16)>=22){"Truncated extensible WAV format"};
            require(f.sliceArray(26..39).contentEquals(byteArrayOf(0,0,0,0,16,0,-128,0,0,-86,0,56,-101,113))){"Unsupported extensible subformat"};word(24)}else tag
        require(channels in 1..8&&rate in 8000..384000){"Unsupported channel count/sample rate"}
        require((encoding==1&&bits in listOf(8,16,24,32))||(encoding==3&&bits in listOf(32,64))){"Only PCM8/16/24/32 or IEEE float32/64 WAV is supported"}
        require(align==channels*(bits/8)&&dword(8)==rate.toLong()*align&&bytes%align==0L){"Invalid PCM frame alignment"}
        Format(rate,channels,bits,encoding,align,bytes/align,offset,bytes,f)
    }
    fun hash(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256");val b=ByteArray(131072)
        file.inputStream().buffered().use{while(true){val n=it.read(b);if(n<0)break;digest.update(b,0,n)}}
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
    private fun sample(b:ByteArray,at:Int,f:Format):Pair<Double,Boolean>{
        var raw=0L;repeat(f.bits/8){raw=raw or ((b[at+it].toLong()and 255)shl(it*8))}
        if(f.encoding==3){val x=if(f.bits==32)Float.fromBits(raw.toInt()).toDouble()else Double.fromBits(raw)
            require(x.isFinite()){"Non-finite float sample in WAV"};return x to (abs(x)>=1.0)}
        if(f.bits==8)return ((raw-128)/128.0) to (raw==0L||raw==255L)
        val sign=1L shl(f.bits-1);val signed=if(raw and sign!=0L)raw-(1L shl f.bits)else raw
        return (signed.toDouble()/sign) to (signed == -sign || signed==sign-1)
    }
    private fun db(v:Double)=20*log10(v.coerceAtLeast(1e-12))
    fun analyze(file:File,stop:()->Boolean={false}):Report {
        val f=inspect(file);val shaBefore=hash(file);val frameSize=f.blockAlign
        val buffer=ByteArray((131072/frameSize)*frameSize)
        var frames=0L;var peak=0.0;var squared=0.0;var rails=0L
        var silent=0L;var run=0L;var longest=0L;var leading=0L;var nonSilent=false
        val first=DoubleArray(f.channels);val last=DoubleArray(f.channels)
        var startSq=0.0;var endSq=0.0;var startN=0L;var endN=0L
        var startCross=0L;var endCross=0L;var previous=0.0
        val window=min(f.frames,max(1L,(f.rate*.25).roundToLong()))
        RandomAccessFile(file,"r").use{r->r.seek(f.dataOffset);var remaining=f.dataBytes
            while(remaining>0){
                require(!stop()){"Audio QA cancelled"}
                val n=min(remaining,buffer.size.toLong()).toInt();r.readFully(buffer,0,n)
                var pos=0
                while(pos<n){
                    var quiet=true;var frameSq=0.0
                    repeat(f.channels){ch->val(x,rail)=sample(buffer,pos,f);pos+=f.bits/8
                        if(frames==0L)first[ch]=x;last[ch]=x;peak=max(peak,abs(x));squared+=x*x;frameSq+=x*x;if(rail)rails++
                        if(abs(x)>.001)quiet=false
                        if(ch==0){if(frames>0&&x*previous<0){if(frames<window)startCross++;if(frames>=f.frames-window)endCross++};previous=x}
                    }
                    if(frames<window){startSq+=frameSq;startN+=f.channels}
                    if(frames>=f.frames-window){endSq+=frameSq;endN+=f.channels}
                    if(quiet){silent++;run++;longest=max(longest,run);if(!nonSilent)leading++}else{run=0;nonSilent=true}
                    frames++
                };remaining-=n
            }
        }
        require(hash(file)==shaBefore){"Audio changed during QA; retry on a completed render"}
        val rms=sqrt(squared/(frames*f.channels));val a=db(sqrt(startSq/startN));val b=db(sqrt(endSq/endN))
        val delta=first.indices.maxOf{abs(first[it]-last[it])}
        val warnings=buildList {
            if(rails>0)add("$rails rail/clipped samples; inspect clipping by ear.")
            if(delta>.02)add("Boundary sample jump %.5f may click.".format(java.util.Locale.US,delta))
            if(abs(a-b)>6)add("First/last 250 ms RMS differs by %.1f dB.".format(java.util.Locale.US,abs(a-b)))
            if(leading.toDouble()/f.rate>.5||run.toDouble()/f.rate>.5)add("Leading/trailing silence may produce a loop gap.")
            if(longest.toDouble()/f.rate>15)add("Long detected silence; compare with programmed pauses.")
        }
        return Report(shaBefore,f.rate,f.channels,f.bits,f.encoding,frames,f.seconds,peak,db(peak),rails,rms,db(rms),silent.toDouble()/f.rate,
            longest.toDouble()/f.rate,leading.toDouble()/f.rate,run.toDouble()/f.rate,delta,a,b,abs(a-b),startCross,endCross,f.exportBytes(3),warnings)
    }
    private fun header(out:OutputStream,f:Format,frames:Long){
        val data=frames*f.blockAlign;val riff=4L+8+f.fmt.size+(f.fmt.size%2)+8+data+(data%2)
        require(riff<=0xffffffffL){"Three cycles exceed RIFF WAV 4 GiB capacity; use playback or junction windows"}
        fun w(n:Long){repeat(4){out.write(((n ushr(it*8))and 255).toInt())}}
        out.write("RIFF".toByteArray());w(riff);out.write("WAVEfmt ".toByteArray());w(f.fmt.size.toLong());out.write(f.fmt)
        if(f.fmt.size%2==1)out.write(0);out.write("data".toByteArray());w(data)
    }
    private fun copyFrames(r:RandomAccessFile,out:OutputStream,f:Format,start:Long,frames:Long,stop:()->Boolean){
        r.seek(f.dataOffset+start*f.blockAlign);var left=frames*f.blockAlign;val b=ByteArray(131072)
        while(left>0){require(!stop()){"Loop export cancelled"};val n=min(left,b.size.toLong()).toInt();r.readFully(b,0,n);out.write(b,0,n);left-=n}
    }
    fun exportCycles(source:File,dest:File,cycles:Int=3,stop:()->Boolean={false}){
        require(cycles==3){"Loop QA export is exactly three cycles"};require(source.canonicalPath!=dest.canonicalPath){"Source WAV must be preserved"}
        val f=inspect(source);require(f.exportBytes(cycles)-8<=0xffffffffL){"Three cycles exceed RIFF size limit"}
        require(dest.parentFile!!.usableSpace>f.exportBytes(cycles)+1048576){"Insufficient space for three-cycle WAV"}
        val tmp=File(dest.parentFile,dest.name+".partial")
        try{FileOutputStream(tmp).buffered(131072).use{o->header(o,f,f.frames*cycles);RandomAccessFile(source,"r").use{r->repeat(cycles){copyFrames(r,o,f,0,f.frames,stop)}};if(f.dataBytes*cycles%2==1L)o.write(0)}
            require(tmp.renameTo(dest)){"Unable to publish loop WAV"}
        }catch(t:Throwable){tmp.delete();throw t}
    }
    /** Both A→B and B→C junctions are byte-identical for a repeated single cycle. */
    fun audition(source:File,dest:File,windowSeconds:Double=2.0){
        require(source.canonicalPath!=dest.canonicalPath){"Source WAV must be preserved"}
        val f=inspect(source);val n=min(f.frames,(windowSeconds.coerceIn(.1,10.0)*f.rate).roundToLong())
        FileOutputStream(dest).buffered().use{o->header(o,f,n*2);RandomAccessFile(source,"r").use{r->
            copyFrames(r,o,f,f.frames-n,n){false};copyFrames(r,o,f,0,n){false}}
            if(n*2*f.blockAlign%2==1L)o.write(0)}
    }
}
