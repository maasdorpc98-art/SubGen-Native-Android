package com.subgen.nativeapp

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*

/** Disk-backed PCM16 stereo mixer, three uploaded source files, independent 8D carrier branch.
 * UI is native and renders a separate 8D feed from the same uploaded Carrier file.
 * No WebAudio, HTML, WebView or arbitrary generated placeholders.
 */
object NativeSubliminal {
    private class Source(file:File):AutoCloseable {
        val handle=RandomAccessFile(file,"r")
        private val channel=handle.channel
        val rate:Int;val channels:Int;val frames:Long;private val offset:Long
        private var mapped:MappedByteBuffer?=null;private var mappedStart=0L;private var mappedLen=0L
        init {
            fun u16():Int=java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(handle.readShort()))
            fun u32():Long=Integer.toUnsignedLong(Integer.reverseBytes(handle.readInt()))
            require(handle.length()>=44){"WAV file too small"}
            val riff=ByteArray(4);handle.readFully(riff)
            require(String(riff)=="RIFF"){"Only RIFF WAV is supported"}
            handle.skipBytes(4);handle.readFully(riff)
            require(String(riff)=="WAVE"){"Not a WAV audio file"}
            var sampleRate=0;var nch=0;var bits=0;var format=0;var dataStart=-1L;var bytes=0L
            var chunks=0
            while(handle.filePointer+8<=handle.length()&&chunks++<1000){
                handle.readFully(riff);val size=u32();val start=handle.filePointer
                if(String(riff)=="fmt "){
                    require(size>=16){"Invalid WAV fmt header"}
                    format=u16();nch=u16();sampleRate=u32().toInt();handle.skipBytes(6);bits=u16()
                }else if(String(riff)=="data"){dataStart=start;bytes=size;break}
                handle.seek(start+size+(size and 1))
            }
            require(format==1&&bits==16&&nch in 1..2&&sampleRate in 8000..96000){"Requires uncompressed PCM16 WAV, mono or stereo"}
            require(dataStart>=0&&bytes>0&&dataStart+bytes<=handle.length()){"Missing or truncated WAV data"}
            rate=sampleRate;channels=nch;frames=bytes/(channels*2);offset=dataStart
        }
        private fun sample(frame:Long,ch:Int):Float {
            val f=frame.coerceIn(0,frames-1)
            val byteIndex=offset+(f*channels+ch.coerceAtMost(channels-1))*2
            if(mapped==null||byteIndex<mappedStart||byteIndex+2>mappedStart+mappedLen){
                val page=(byteIndex/4096)*4096
                mappedStart=page
                mappedLen=min(16L*1024*1024,channel.size()-page)
                mapped=channel.map(FileChannel.MapMode.READ_ONLY,page,mappedLen).apply{order(ByteOrder.LITTLE_ENDIAN)}
            }
            return mapped!!.getShort((byteIndex-mappedStart).toInt())/32768f
        }
        fun at(outputFrame:Long,ch:Int,sampleRate:Int,speed:Double=1.0):Float {
            if(frames<=0)return 0f
            val rawPos=outputFrame.toDouble()*rate/sampleRate*speed
            val pos=rawPos%frames
            val f0=pos.toLong();val frac=(pos-f0).toFloat()
            val f1=(f0+1)%frames
            return sample(f0,ch)*(1-frac)+sample(f1,ch)*frac
        }
        override fun close(){mapped=null;handle.close()}
    }
    data class Settings(
        val carrierA:File?, val carrierB:File?, val affirmationA:File?,val affirmationB:File?,
        val durationSeconds:Int, val rate:Int=44100,
        val carrierDb:Double=0.0,val carrier8dDb:Double=-60.0,
        val primaryDb:Double=0.0,val secondaryDb:Double=0.0,
        val layerDb:Double=0.0,val finalDb:Double=0.0,
        val primarySpeed:Double=1.0,val secondarySpeed:Double=1.0,
        val stereoWidth:Double=1.0,val fadeSeconds:Double=0.0,
        val eightDPercent:Double=25.0,
        val binauralOn:Boolean=false,val binauralDb:Double=0.0,val beatHz:Double=10.0,val centerHz:Double=400.0,
        val rhythmicOn:Boolean=false,val rhythmicDb:Double=0.0,val rhythmicPulse:Double=6.0,
        val rhythmicTone:Double=400.0,val rhythmicDepth:Double=1.0,val rhythmicSine:Boolean=false,
        val clarity:Boolean=false,val peakCeiling:Double=.912,
        val highPassHz:Double=0.0,val lowPassHz:Double=0.0
    )
    private const val TAU=2*Math.PI
    fun mix(s:Settings,dest:File,update:(String)->Unit,stop:()->Boolean){
        val mixStarted=android.os.SystemClock.elapsedRealtime()
        Diagnostics.stage("MIXER_OPEN_SOURCES")
        val files=listOf(s.carrierA,s.carrierB,s.affirmationA,s.affirmationB)
        require(files.any{it!=null}){"Select at least one Carrier or Affirmation audio file"}
        val sources=files.map{it?.let(::Source)}
        try {
            val sr=s.rate.coerceIn(32000,48000)
            val seconds=s.durationSeconds.coerceIn(1,60*90)
            val frames=seconds.toLong()*sr
            Diagnostics.expectedOutput(frames*4L+44L)
            require(frames*4L<0xFFFFFFFFL){"WAV exceeds RIFF size limit"}
            fun gain(db:Double)=10.0.pow(db.coerceIn(-60.0,0.0)/20.0)
            val g0=gain(s.carrierDb)
            val g1=gain(s.carrier8dDb)
            val g2=gain(s.primaryDb+s.layerDb)
            val g3=gain(s.secondaryDb+s.layerDb)
            val final=gain(s.finalDb)
            val fade=s.fadeSeconds.coerceIn(0.0,30.0)
            val panHz=.004+s.eightDPercent.coerceIn(0.0,100.0)*.0008
            val width=s.stereoWidth.coerceIn(0.0,2.0)
            val binsLeft=s.centerHz-s.beatHz/2
            val binsRight=s.centerHz+s.beatHz/2
            val rhythmicShapeDepth=s.rhythmicDepth.coerceIn(0.0,1.0)
            val lpfAlpha=if(s.lowPassHz>0)1.0-exp(-TAU*s.lowPassHz/sr) else 1.0
            val hpfAlpha=if(s.highPassHz>0)exp(-TAU*s.highPassHz/sr) else 0.0
            val low=DoubleArray(2);val highPrev=DoubleArray(2);val prevX=DoubleArray(2)
            val clarityStateA=DoubleArray(2);val clarityStateB=DoubleArray(2)
            val clarityA=if(s.clarity && s.primarySpeed>1)1.0-exp(-TAU*(sr*.42/s.primarySpeed)/sr) else 1.0
            val clarityB=if(s.clarity && s.secondarySpeed>1)1.0-exp(-TAU*(sr*.42/s.secondarySpeed)/sr) else 1.0
            var observedPeak=0.0
            var peakLimitEvents=0L
            val chunkFrames=4096
            val buf=ByteArray(chunkFrames*4)
            val tmp=File(dest.parentFile,dest.name+".partial")
            FileOutputStream(tmp).buffered(65536).use{out->
                WavIO.writeHeader(out,frames,sr,2)
                var frame=0L
                while(frame<frames){
                    if(stop())throw InterruptedException("Subliminal render stopped")
                    val n=min(chunkFrames.toLong(),frames-frame).toInt()
                    var cursor=0
                    for(i in 0 until n){
                        val frameIndex=frame+i
                        val t=frameIndex.toDouble()/sr
                        val fadeGain=if(fade>0)(t/fade).coerceIn(0.0,1.0) else 1.0
                        val carrier=sources[0]
                        var cL=carrier?.at(frameIndex,0,sr)?.toDouble()?:0.0
                        var cR=carrier?.at(frameIndex,1,sr)?.toDouble()?:0.0
                        if(width!=1.0){val mid=(cL+cR)*.5;val side=(cL-cR)*.5*width;cL=mid+side;cR=mid-side}
                        var c8L=0.0;var c8R=0.0
                        val eight=sources[1]
                        if(eight!=null){
                            val signal=(eight.at(frameIndex,0,sr).toDouble()+eight.at(frameIndex,1,sr).toDouble())*.5
                            val pan=sin(TAU*panHz*t)
                            c8L=signal*sqrt((1-pan)*.5)
                            c8R=signal*sqrt((1+pan)*.5)
                        }
                        if(lpfAlpha<1.0){low[0]+=lpfAlpha*(cL-low[0]);low[1]+=lpfAlpha*(cR-low[1]);cL=low[0];cR=low[1]}
                        if(hpfAlpha>0){val yl=hpfAlpha*(highPrev[0]+cL-prevX[0]);val yr=hpfAlpha*(highPrev[1]+cR-prevX[1]);prevX[0]=cL;prevX[1]=cR;highPrev[0]=yl;highPrev[1]=yr;cL=yl;cR=yr}
                        val a=sources[2];val b=sources[3]
                        var speechAL=a?.at(frameIndex,0,sr,s.primarySpeed)?.toDouble()?:0.0
                        var speechAR=a?.at(frameIndex,1,sr,s.primarySpeed)?.toDouble()?:0.0
                        var speechBL=b?.at(frameIndex,0,sr,s.secondarySpeed)?.toDouble()?:0.0
                        var speechBR=b?.at(frameIndex,1,sr,s.secondarySpeed)?.toDouble()?:0.0
                        if(clarityA<1.0){clarityStateA[0]+=clarityA*(speechAL-clarityStateA[0]);clarityStateA[1]+=clarityA*(speechAR-clarityStateA[1]);speechAL=clarityStateA[0];speechAR=clarityStateA[1]}
                        if(clarityB<1.0){clarityStateB[0]+=clarityB*(speechBL-clarityStateB[0]);clarityStateB[1]+=clarityB*(speechBR-clarityStateB[1]);speechBL=clarityStateB[0];speechBR=clarityStateB[1]}
                        val binauralLeft=if(s.binauralOn)sin(TAU*binsLeft*t)*gain(s.binauralDb)*.2 else 0.0
                        val binauralRight=if(s.binauralOn)sin(TAU*binsRight*t)*gain(s.binauralDb)*.2 else 0.0
                        val pulse=if(s.rhythmicSine).5+.5*sin(TAU*s.rhythmicPulse*t) else if(sin(TAU*s.rhythmicPulse*t)>=0)1.0 else 0.0
                        val rhythm=if(s.rhythmicOn)sin(TAU*s.rhythmicTone*t)*(1-rhythmicShapeDepth+rhythmicShapeDepth*pulse)*gain(s.rhythmicDb)*.2 else 0.0
                        // Keep audio sources stationary: only the Carrier's separate 8D branch pans.
                        val left=(cL*g0+c8L*g1)*fadeGain + speechAL*g2+speechBL*g3+binauralLeft+rhythm
                        val right=(cR*g0+c8R*g1)*fadeGain + speechAR*g2+speechBR*g3+binauralRight+rhythm
                        val rawLeft=left*final
                        val rawRight=right*final
                        observedPeak=max(observedPeak,max(abs(rawLeft),abs(rawRight)))
                        if(abs(rawLeft)>s.peakCeiling || abs(rawRight)>s.peakCeiling)peakLimitEvents++
                        // Existing PCM16 ceiling remains unchanged; telemetry does not process audio.
                        val leftPcm=WavIO.floatToS16(rawLeft.coerceIn(-s.peakCeiling,s.peakCeiling).toFloat())
                        val rightPcm=WavIO.floatToS16(rawRight.coerceIn(-s.peakCeiling,s.peakCeiling).toFloat())
                        buf[cursor++]=(leftPcm and 255).toByte();buf[cursor++]=((leftPcm shr 8)and 255).toByte()
                        buf[cursor++]=(rightPcm and 255).toByte();buf[cursor++]=((rightPcm shr 8)and 255).toByte()
                    }
                    out.write(buf,0,cursor);frame+=n
                    if(frame%(sr*4L)<chunkFrames)update("Mixing ${frame*100/frames}% · ${frame/sr}s / ${seconds}s")
                    if(frame%(sr*20L)<chunkFrames) {
                        val elapsed=android.os.SystemClock.elapsedRealtime()-mixStarted
                        Diagnostics.record("MIXER_CHECKPOINT","",org.json.JSONObject().put("audioSeconds",frame/sr)
                            .put("elapsedMs",elapsed).put("realtimeFactor",elapsed/1000.0/(frame.toDouble()/sr).coerceAtLeast(0.001)))
                    }
                }
            }
            try {
                Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            }catch(_:java.nio.file.AtomicMoveNotSupportedException){
                Files.move(tmp.toPath(),dest.toPath(),StandardCopyOption.REPLACE_EXISTING)
            }
            Diagnostics.record("MIXER_LEVEL_AUDIT","Read-only original mix peak/ceiling check",
                org.json.JSONObject().put("preCeilingPeak",observedPeak)
                    .put("limitedFrames",peakLimitEvents)
                    .put("ceiling",s.peakCeiling))
            Diagnostics.record("MIXER_COMPLETED","",org.json.JSONObject().put("elapsedMs",android.os.SystemClock.elapsedRealtime()-mixStarted).put("wavBytes",dest.length()))
        } finally {
            File(dest.parentFile,dest.name+".partial").delete()
            sources.forEach{it?.close()}
        }
    }
}
