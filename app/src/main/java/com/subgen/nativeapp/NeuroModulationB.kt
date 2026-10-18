package com.subgen.nativeapp

import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZInputStream
import org.tukaani.xz.XZOutputStream
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.math.*

/** Native Kotlin port of the supplied Experiment 001 build_decode_full.py Version B.
 * Encodes 24k mono PCM16 by 3-formant sine predictor + modular 16-bit residual,
 * XZ LZMA correction packet, 2-channel 22-bit QAM/2048 OFDM, 128-sample CP,
 * positive amplitude modulation and stereo 96kHz signed PCM32 WAV.
 * The independently decoded output must SHA-match the normalized source before publish.
 */
object NeuroModulationB {
    const val RATE = 96000
    private const val FFT_N = 2048
    private const val CP = 128
    private const val FRAME_N = 2176
    private const val BITS = 22
    private const val HI = 2047
    private val dataBins = (0..FFT_N/2).filter { val f=it*RATE.toDouble()/FFT_N; f>=5000.0 && f<44000.0 }.toIntArray()
    private val NC=dataBins.size
    private val BYTES_PER_SYM=NC*2*BITS/8
    private const val MAGIC="NE40B004"
    private const val HEADER_SIZE=58
    private const val MAX_FRAMES=180L*RATE
    data class Result(val sourceSamples:Int,val recoveredSamples:Int,val pcmSha256:String,
         val duration:Double,val peakDbfs:Double,val scale:Double,val frames:Long,
         val payloadBytes:Int,val capacityBytes:Long,val wavBytes:Long)

    private fun digest(data:ByteArray)=MessageDigest.getInstance("SHA-256").digest(data)
    private fun hex(b:ByteArray)=b.joinToString(""){"%02x".format(it)}
    private fun checked(stop:()->Boolean){if(stop()) throw InterruptedException("NeuroModulation B cancelled")}
    private fun numFrames(seconds:Double):Long {require(seconds.isFinite()&&seconds in 1.0..180.0);return round(seconds*RATE).toLong()}
    private fun env(i:Long,c:NeuroModulationA.Config)=1-c.depth/2+c.depth/2*cos(2*PI*c.frequencyHz*i/RATE)
    private fun write16(o:OutputStream,v:Int){o.write(v and 255);o.write(v ushr 8 and 255)}
    private fun write32(o:OutputStream,v:Long){for(j in 0..3)o.write((v ushr (8*j) and 255).toInt())}
    private fun header(o:OutputStream,frames:Long){
        require(frames*8+36<0x1_0000_0000L)
        o.write("RIFF".toByteArray());write32(o,frames*8+36);o.write("WAVEfmt ".toByteArray())
        write32(o,16);write16(o,1);write16(o,2);write32(o,RATE.toLong());write32(o,(RATE*8).toLong())
        write16(o,8);write16(o,32);o.write("data".toByteArray());write32(o,frames*8)
    }
    private fun readSource(file:File):Pair<ShortArray,ByteArray>{
        val meta=NeuroModulationA.inspect(file)
        require(meta.rate==24000&&meta.channels==1){"B requires normalized 24kHz mono PCM16"}
        require(meta.frames in 1..14_400_000){"B normalized source limited to ten minutes"}
        val raw=ByteArray((meta.frames*2).toInt())
        RandomAccessFile(file,"r").use{it.seek(meta.offset);it.readFully(raw)}
        val bb=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN)
        return ShortArray(meta.frames.toInt()){bb.short} to raw
    }
    /** Iterative Cooley-Tukey, forward unscaled, inverse normalized. */
    private fun fft(re:DoubleArray,im:DoubleArray,inverse:Boolean=false){
        val n=re.size;require(im.size==n && Integer.bitCount(n)==1)
        var j=0
        for(i in 1 until n){
            var bit=n shr 1
            while(j and bit!=0){j=j xor bit;bit=bit shr 1}
            j=j xor bit
            if(i<j){var t=re[i];re[i]=re[j];re[j]=t;t=im[i];im[i]=im[j];im[j]=t}
        }
        var len=2
        while(len<=n){
            val ang=(if(inverse)2.0 else -2.0)*PI/len
            val wr=cos(ang);val wi=sin(ang)
            var start=0
            while(start<n){var ar=1.0;var ai=0.0
                for(k in 0 until len/2){
                    val a=start+k;val b=a+len/2
                    val vr=re[b]*ar-im[b]*ai;val vi=re[b]*ai+im[b]*ar
                    val ur=re[a];val ui=im[a]
                    re[a]=ur+vr;im[a]=ui+vi;re[b]=ur-vr;im[b]=ui-vi
                    val nr=ar*wr-ai*wi;ai=ar*wi+ai*wr;ar=nr
                }
                start+=len
            }
            len=len shl 1
        }
        if(inverse)for(i in 0 until n){re[i]=re[i]/n.toDouble();im[i]=im[i]/n.toDouble()}
    }
    private fun gaussian(mag:DoubleArray):DoubleArray{
        val radius=20;val w=DoubleArray(41){t->exp(-.5*((t-20)/5.0).pow(2))}
        val sum=w.sum();for(i in w.indices)w[i]/=sum
        return DoubleArray(mag.size){i->var z=0.0
            for(k in -radius..radius){var index=i+k
                // scipy ndimage gaussian_filter1d default reflect boundary.
                if(index<0)index=-index-1
                if(index>=mag.size)index=2*mag.size-index-1
                z+=mag[index.coerceIn(mag.indices)]*w[k+radius]
            };z}
    }
    private fun features(pcm:ShortArray,progress:(String)->Unit,stop:()->Boolean):ShortArray {
        val hop=480;val count=pcm.size/hop+1+(if(pcm.size%hop!=0)1 else 0)
        val feat=ShortArray(count*6)
        val ranges=arrayOf(250.0 to 900.0,900.0 to 2400.0,2400.0 to 4200.0)
        val hann=DoubleArray(1024){.5-.5*cos(2*PI*it/1023)}
        val re=DoubleArray(FFT_N);val im=DoubleArray(FFT_N)
        for(frame in 0 until count){
            if(frame%400==0){checked(stop);progress("B: extracting 3-band voice features ${frame*100/count}%")}
            val center=frame*hop
            var previous=0.0
            for(k in 0 until 1024){
                val at=center+k-512
                val value=if(at in pcm.indices)pcm[at].toDouble()/32768 else 0.0
                val emph=if(k==0)value else value-.96*previous
                re[k]=emph*hann[k];im[k]=0.0;previous=value
            }
            for(k in 1024 until FFT_N){re[k]=0.0;im[k]=0.0}
            fft(re,im)
            val mags=DoubleArray(FFT_N/2+1){hypot(re[it],im[it])}
            val blurred=gaussian(mags)
            for(band in 0..2){
                val lo=ceil(ranges[band].first*FFT_N/24000.0).toInt()
                val hi=ceil(ranges[band].second*FFT_N/24000.0).toInt()
                var best=lo
                for(k in lo+1 until hi){if(blurred[k]>blurred[best])best=k}
                val frequency=best*24000.0/FFT_N
                feat[frame*6+band*2]=round(frequency*10).toInt().toShort()
                feat[frame*6+band*2+1]=min(65535,round(blurred[best]*2000).toInt()).toShort()
            }
        }
        return feat
    }
    private fun predictor(feat:ShortArray, nsamples:Int):ShortArray {
        val frames=feat.size/6
        val gains=DoubleArray(frames*3)
        var maxGain=1.0
        for(i in 0 until frames)for(j in 0..2){val amp=sqrt((feat[i*6+j*2+1].toInt() and 65535).toDouble()+1)*.015
            gains[i*3+j]=amp;maxGain=max(maxGain,amp)
        }
        val p=ShortArray(nsamples);val phase=DoubleArray(3)
        for(i in 0 until nsamples){
            val left=min(i/480,frames-1)
            val right=min(left+1,frames-1)
            val t=if(right==left)0.0 else (i-left*480)/480.0
            var sum=0.0
            for(j in 0..2){
                val f0=(feat[left*6+j*2].toInt() and 65535)/10.0
                val f1=(feat[right*6+j*2].toInt() and 65535)/10.0
                val g0=gains[left*3+j]/maxGain;val g1=gains[right*3+j]/maxGain
                phase[j]+=(f0+(f1-f0)*t)*2*PI/24000
                sum+=sin(phase[j])*(g0+(g1-g0)*t)*.25
            }
            p[i]=Math.rint((sum*32767).coerceIn(-32768.0,32767.0)).toInt().toShort()
        }
        return p
    }
    private fun xz(raw:ByteArray):ByteArray=ByteArrayOutputStream().use{out->
        XZOutputStream(out,LZMA2Options(3)).use{it.write(raw)};out.toByteArray()
    }
    private fun unxz(raw:ByteArray,max:Int):ByteArray=ByteArrayOutputStream().use{out->
        XZInputStream(ByteArrayInputStream(raw)).use{src->
            val buffer=ByteArray(32768);var total=0
            while(true){val k=src.read(buffer);if(k<0)break;total+=k;require(total<=max){"XZ stream exceeds declared maximum"};out.write(buffer,0,k)}
        };out.toByteArray()
    }
    private fun packet(pcm:ShortArray,pcmBytes:ByteArray,feat:ShortArray,stop:()->Boolean):ByteArray {
        val predict=predictor(feat,pcm.size)
        val residual=ByteArray(pcm.size*2)
        for(i in pcm.indices){if(i%240000==0)checked(stop)
            val v=(pcm[i].toInt()-predict[i].toInt()).toShort().toInt()
            residual[i*2]=v.toByte();residual[i*2+1]=(v ushr 8).toByte()
        }
        val featureBytes=ByteArray(feat.size*2)
        for(i in feat.indices){featureBytes[2*i]=feat[i].toByte();featureBytes[2*i+1]=(feat[i].toInt() ushr 8).toByte()}
        val a=xz(featureBytes);val b=xz(residual)
        val out=ByteArrayOutputStream(HEADER_SIZE+a.size+b.size)
        out.write(MAGIC.toByteArray())
        write16(out,4);write32(out,pcm.size.toLong());write32(out,a.size.toLong());out.write(digest(pcmBytes))
        write32(out,b.size.toLong());write32(out,0);out.write(a);out.write(b)
        return out.toByteArray()
    }
    private fun readBits(bytes:ByteArray,offsetBit:Int,width:Int):Int{
        var code=0;for(i in 0 until width){val b=offsetBit+i;code=code shl 1 or ((bytes[b ushr 3].toInt() ushr (7-(b and 7))) and 1)}
        return code
    }
    private fun assignCarrier(re:DoubleArray,im:DoubleArray,bin:Int,real:Double,imag:Double){
        re[bin]=real;im[bin]=imag
        re[FFT_N-bin]=real;im[FFT_N-bin]=-imag
    }
    /** An unscaled intermediate allows an exact per-render scalar, as in original build_b. */
    fun generate(normalized:File,master:File,cfg:NeuroModulationA.Config,
                 progress:(String)->Unit={},stop:()->Boolean={false}):Result {
        val c=cfg.checked();val frames=numFrames(c.durationSeconds)
        require(frames<=MAX_FRAMES)
        val syms=(frames/FRAME_N).toInt();val capacity=syms.toLong()*BYTES_PER_SYM
        val (pcm,raw)=readSource(normalized)
        progress("B: sine predictor analysis…")
        val feat=features(pcm,progress,stop)
        progress("B: compressing speech reconstruction data…")
        val packet=packet(pcm,raw,feat,stop)
        require(packet.size<=capacity){"Version B data capacity insufficient: ${packet.size} / $capacity bytes. Increase duration."}
        val tmp=File(master.parentFile,master.name+".unscaled.partial")
        val resultFile=File(master.parentFile,master.name+".partial")
        val re=Array(2){DoubleArray(FFT_N)};val im=Array(2){DoubleArray(FFT_N)}
        val vals=Array(2){DoubleArray(FRAME_N)}
        val data=ByteArray(BYTES_PER_SYM)
        var rawPeak=0.0
        try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp),262144)).use{out->
                for(sym in 0 until syms){
                    if(sym%120==0){checked(stop);progress("B: encoding stereo 22-bit QAM/OFDM ${sym*100/syms}%")}
                    val off=sym.toLong()*BYTES_PER_SYM
                    for(i in data.indices){val src=off+i;data[i]=if(src<packet.size)packet[src.toInt()] else pseudoPadding(src)}
                    for(ch in 0..1){
                        java.util.Arrays.fill(re[ch],0.0);java.util.Arrays.fill(im[ch],0.0)
                        for(carrier in 0 until NC){
                            val k=(ch*NC+carrier)*BITS
                            val iv=readBits(data,k,11);val qv=readBits(data,k+11,11)
                            assignCarrier(re[ch],im[ch],dataBins[carrier],(2.0*iv-HI)/HI,(2.0*qv-HI)/HI)
                        }
                    }
                    val sampid=(((sym+.5)*FRAME_N/frames.toDouble())*pcm.size).toInt()
                    val fi=min(sampid/480,feat.size/6-1)
                    for(j in 0..2){
                        val f=(feat[fi*6+j*2].toInt() and 65535)/10.0
                        val bin=round(f*FFT_N/RATE).toInt().coerceIn(1,FFT_N/2-1)
                        val magnitude=8+3*sqrt((feat[fi*6+j*2+1].toInt() and 65535)/65535.0)
                        val phase=2*PI*((sym*FRAME_N.toDouble()/RATE)*f+j*.271)
                        val nr=re[0][bin]+magnitude*cos(phase)
                        val ni=im[0][bin]+magnitude*sin(phase)
                        assignCarrier(re[0],im[0],bin,nr,ni)
                        // shared sine tones on both channels, preserving each channel's high-band QAM.
                        val nr1=re[1][bin]+magnitude*cos(phase)
                        val ni1=im[1][bin]+magnitude*sin(phase)
                        assignCarrier(re[1],im[1],bin,nr1,ni1)
                    }
                    for(ch in 0..1){fft(re[ch],im[ch],true)
                        for(i in 0 until FRAME_N){val at=if(i<CP)FFT_N-CP+i else i-CP;vals[ch][i]=re[ch][at]}
                    }
                    for(i in 0 until FRAME_N){val at=sym.toLong()*FRAME_N+i
                        val envelope=env(at,c)
                        for(ch in 0..1){val v=vals[ch][i]*envelope;out.writeDouble(v);rawPeak=max(rawPeak,abs(v))}
                    }
                }
                // original build_b leaves the trailing incomplete OFDM frame as zero.
                repeat(((frames-syms.toLong()*FRAME_N)*2).toInt()){out.writeDouble(0.0)}
            }
            require(rawPeak>0.0&&rawPeak.isFinite()){"B encoded waveform invalid"}
            val scale=(10.0.pow(-30.0/20.0)/rawPeak)*.975
            val actual=rawPeak*scale
            require(actual<10.0.pow(-30.0/20.0))
            val totalSamples=frames*2
            DataInputStream(BufferedInputStream(FileInputStream(tmp),262144)).use{unscaled->
                BufferedOutputStream(FileOutputStream(resultFile),262144).use{saved->
                    header(saved,frames)
                    for(i in 0 until totalSamples){
                        if(i%(RATE*20L)==0L){checked(stop);progress("B: final signed PCM32 mastering ${i*100/totalSamples}%")}
                        val v=unscaled.readDouble()*scale
                        val code=Math.rint(v*2147483648.0).coerceIn(Int.MIN_VALUE.toDouble(),Int.MAX_VALUE.toDouble()).toLong().toInt()
                        write32(saved,code.toLong())
                    }
                }
            }
            progress("B: decoding saved PCM32 master to verify corrected speech…")
            val recovered=recover(resultFile,scale,c,stop)
            require(recovered.contentEquals(raw)){"B decoded PCM differs from normalized source"}
            if(master.exists())require(master.delete()){"Cannot replace older Version B master"}
            require(resultFile.renameTo(master)){"Cannot publish verified B WAV"}
            return Result(pcm.size,pcm.size,hex(digest(raw)),frames.toDouble()/RATE,
                20*log10(actual),scale,frames,packet.size,capacity,master.length())
        }finally{tmp.delete();resultFile.delete()}
    }
    /** Listening-only version of B: high-band OFDM correction attenuated through
     * a cascade of lowpass sections. This is NOT the archival Python PCM24 FIR
     * headphone recipe, and is NOT digitally reversible. Two-pass peak normalization.
     */
    fun createMixerCopy(master:File,dest:File,stop:()->Boolean={false}) {
        val total=(master.length()-44L)/8L
        require(total>=RATE.toLong()&&(master.length()-44L)%8==0L){"Invalid Version B PCM32 master"}
        val previewTmp=File(dest.parentFile,dest.name+".work.partial")
        val outTmp=File(dest.parentFile,dest.name+".partial")
        val smooth=DoubleArray(6);val hp=DoubleArray(4);val hpPrev=DoubleArray(4)
        val lpCoef=1-exp(-2*PI*4280/RATE)
        val hpCoef=exp(-2*PI*150/RATE)
        var peak=0.0
        try {
            RandomAccessFile(master,"r").use{r->r.seek(44)
                DataOutputStream(BufferedOutputStream(FileOutputStream(previewTmp),131072)).use{o->
                    for(i in 0 until total){
                        if(i%96000L==0L)checked(stop)
                        val left=Integer.reverseBytes(r.readInt()).toDouble()/2147483648.0
                        val right=Integer.reverseBytes(r.readInt()).toDouble()/2147483648.0
                        var v=(left+right)/2.0
                        for(j in smooth.indices){smooth[j]+=lpCoef*(v-smooth[j]);v=smooth[j]}
                        for(j in hp.indices){val out=hpCoef*(hp[j]+v-hpPrev[j]);hpPrev[j]=v;hp[j]=out;v=out}
                        peak=max(peak,abs(v));o.writeFloat(v.toFloat())
                    }
                }
            }
            val factor=if(peak>0)10.0.pow(-18.0/20.0)/peak else 0.0
            DataInputStream(BufferedInputStream(FileInputStream(previewTmp),131072)).use{r->
                BufferedOutputStream(FileOutputStream(outTmp),131072).use{o->
                    headerMono16(o,total)
                    val fade=min(3360L,total/4)
                    for(i in 0 until total){
                        if(i%96000L==0L)checked(stop)
                        val ramp=if(fade==0L)1.0 else min(1.0,min(i.toDouble()/fade,(total-i-1).toDouble()/fade)).coerceAtLeast(0.0)
                        val v=Math.rint(r.readFloat().toDouble()*factor*ramp*32768).toInt().coerceIn(-32768,32767)
                        write16(o,v)
                    }
                }
            }
            if(dest.exists())require(dest.delete()){ "Could not replace B listening edition" }
            require(outTmp.renameTo(dest)){"Could not publish B listening edition"}
        }finally{previewTmp.delete();outTmp.delete()}
    }
    private fun headerMono16(out:OutputStream,frames:Long){
        out.write("RIFF".toByteArray());write32(out,36+frames*2)
        out.write("WAVEfmt ".toByteArray());write32(out,16);write16(out,1);write16(out,1)
        write32(out,RATE.toLong());write32(out,(RATE*2).toLong());write16(out,2);write16(out,16)
        out.write("data".toByteArray());write32(out,frames*2)
    }
    private fun pseudoPadding(index:Long):Byte {
        // Deterministic noise padding, not semantically meaningful. Different byte sequence
        // from numpy.default_rng(seed) is compatible with original decoder's ignored tail.
        var x=(index+427163L)*0x2545f4914f6cdd1dL
        x=x xor (x ushr 33);x*=0x7fb5d329728ea185L;x=x xor(x ushr 29)
        return (x ushr 24).toByte()
    }
    private fun decodeSymbols(wav:File,scale:Double,cfg:NeuroModulationA.Config,stop:()->Boolean):ByteArray {
        require(scale>0&&scale.isFinite())
        val total=numFrames(cfg.durationSeconds)
        val nsym=(total/FRAME_N).toInt()
        val out=ByteArrayOutputStream()
        val re=Array(2){DoubleArray(FFT_N)};val im=Array(2){DoubleArray(FFT_N)}
        var needed=HEADER_SIZE
        RandomAccessFile(wav,"r").use{r->
            require(r.length()==44+total*8){"Version B PCM32 WAV length mismatch"}
            r.seek(44)
            for(sym in 0 until nsym){
                if(sym%120==0)checked(stop)
                val raw=Array(2){DoubleArray(FRAME_N)}
                for(i in 0 until FRAME_N){
                    val at=sym.toLong()*FRAME_N+i
                    val factor=scale*env(at,cfg)
                    for(ch in 0..1){
                        val v=Integer.reverseBytes(r.readInt()).toDouble()/2147483648.0
                        raw[ch][i]=v/factor
                    }
                }
                val block=ByteArray(BYTES_PER_SYM)
                var bit=0
                for(ch in 0..1){
                    for(i in 0 until FFT_N){re[ch][i]=raw[ch][i+CP];im[ch][i]=0.0}
                    fft(re[ch],im[ch])
                    for(bin in dataBins){
                        for(coordinate in 0..1){
                            val z=if(coordinate==0)re[ch][bin] else im[ch][bin]
                            val q=Math.rint((z*HI+HI)/2.0).toInt().coerceIn(0,HI)
                            for(k in 10 downTo 0){if((q ushr k and 1)==1)
                                block[bit ushr 3]=(block[bit ushr 3].toInt() or (1 shl (7-(bit and 7)))).toByte()
                                bit++
                            }
                        }
                    }
                }
                out.write(block)
                if(out.size()>=HEADER_SIZE && needed==HEADER_SIZE){
                    val bytes=out.toByteArray()
                    require(String(bytes,0,8)==MAGIC){"Version B correction packet header invalid (QAM recoverability failure)"}
                    val h=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    h.position(8);require((h.short.toInt() and 0xffff)==4){"Unknown B codec version"}
                    val samples=h.int;val featBytes=h.int
                    require(samples>0&&samples<=14_400_000 && featBytes>=0 && featBytes<=120_000_000){"Invalid B source/features length"}
                    h.position(50);val residBytes=h.int
                    require(residBytes>=0 && residBytes<=120_000_000){"Invalid B residual length"}
                    needed=HEADER_SIZE+featBytes+residBytes
                    require(needed.toLong()<=nsym.toLong()*BYTES_PER_SYM){"B packet length exceeds capacity"}
                }
                if(out.size()>=needed)break
            }
        }
        val all=out.toByteArray();require(all.size>=needed){"B correction stream incomplete"}
        return all.copyOfRange(0,needed)
    }
    fun recover(wav:File,scale:Double,cfg:NeuroModulationA.Config,stop:()->Boolean={false}):ByteArray{
        val packet=decodeSymbols(wav,scale,cfg,stop)
        val h=ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        h.position(10)
        val num=h.int;val featlen=h.int
        val sha=ByteArray(32);h.get(sha);val reslen=h.int
        val featBytes=unxz(packet.copyOfRange(HEADER_SIZE,HEADER_SIZE+featlen),1_000_000)
        require(featBytes.size%12==0 && featBytes.isNotEmpty()){"B feature stream malformed"}
        val feat=ShortArray(featBytes.size/2){i->
            ((featBytes[i*2].toInt() and 255) or ((featBytes[i*2+1].toInt() and 255) shl 8)).toShort()
        }
        val resid=unxz(packet.copyOfRange(HEADER_SIZE+featlen,HEADER_SIZE+featlen+reslen),num*2)
        require(resid.size==num*2){"B residual length doesn't match speech"}
        val predicted=predictor(feat,num)
        val reconstructed=ByteArray(num*2)
        for(i in 0 until num){
            if(i%240000==0)checked(stop)
            val v=((resid[i*2].toInt() and 255) or ((resid[i*2+1].toInt() and 255) shl 8)).toShort().toInt()
            val y=(v+predicted[i]).toShort().toInt()
            reconstructed[2*i]=y.toByte();reconstructed[2*i+1]=(y ushr 8).toByte()
        }
        require(digest(reconstructed).contentEquals(sha)){"B source SHA-256 does not match decoded PCM"}
        return reconstructed
    }
}