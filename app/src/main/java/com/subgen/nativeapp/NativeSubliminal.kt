package com.subgen.nativeapp

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.*

/** Bounded-memory native WAV mixer. Supports mono/stereo PCM16 WAV source files. */
object NativeSubliminal {
    private const val RATE=24000
    private class Source(file:File):AutoCloseable {
        val handle=RandomAccessFile(file,"r");private val channel=handle.channel
        val rate:Int;val channels:Int;val frames:Long;private val offset:Long
        private var mapped:MappedByteBuffer?=null;private var mappedStart=0L;private var mappedLen=0L
        init {
            fun u16():Int=java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(handle.readShort()))
            fun u32():Long=Integer.toUnsignedLong(Integer.reverseBytes(handle.readInt()))
            require(handle.length()>=44){"WAV file too small"}
            val riff=ByteArray(4);handle.readFully(riff);require(String(riff)=="RIFF"){"Only RIFF WAV is supported"}
            handle.skipBytes(4);handle.readFully(riff);require(String(riff)=="WAVE"){"Not a WAV audio file"}
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
            require(dataStart>=0&&bytes>0&&dataStart+bytes<=handle.length()){ "Missing or truncated WAV data" }
            rate=sampleRate;channels=nch;frames=bytes/(channels*2);offset=dataStart
        }
        private fun sample(frame:Long,ch:Int):Float {
            val f=frame.coerceIn(0,frames-1);val byteIndex=offset+(f*channels+ch.coerceAtMost(channels-1))*2
            if(mapped==null||byteIndex<mappedStart||byteIndex+2>mappedStart+mappedLen){
                val page=(byteIndex/4096)*4096
                mappedStart=page
                mappedLen=min(16L*1024*1024,channel.size()-page)
                mapped=channel.map(FileChannel.MapMode.READ_ONLY,page,mappedLen).apply{order(ByteOrder.LITTLE_ENDIAN)}
            }
            return mapped!!.getShort((byteIndex-mappedStart).toInt())/32768f
        }
        fun at(outputFrame:Long,ch:Int,loop:Boolean):Float {
            if(frames<=0)return 0f
            val rawPos=outputFrame.toDouble()*rate/RATE
            if(!loop&&rawPos>=frames)return 0f
            val pos=if(loop)rawPos%frames else rawPos
            val f0=pos.toLong();val t=(pos-f0).toFloat()
            val next=if(loop)(f0+1)%frames else min(f0+1,frames-1)
            return sample(f0,ch)*(1-t)+sample(next,ch)*t
        }
        override fun close(){handle.close();mapped=null}
    }
    data class Settings(val carrierA:File?,val carrierB:File?,val affirmationA:File?,val affirmationB:File?,
                        val durationMinutes:Int,val carrierPercent:Int,val primaryPercent:Int)
    fun mix(settings:Settings,dest:File,update:(String)->Unit,stop:()->Boolean){
        val files=listOf(settings.carrierA,settings.carrierB,settings.affirmationA,settings.affirmationB)
        require(files.any{it!=null}){"Select at least one audio source"}
        val sources=files.map{it?.let(::Source)}
        try {
            val minutes=settings.durationMinutes.coerceIn(1,90)
            val frames=minutes*60L*RATE
            val carrierWeight=settings.carrierPercent.coerceIn(0,100)/100.0
            val affirmWeight=1-carrierWeight
            val primaryWeight=settings.primaryPercent.coerceIn(0,100)/100.0
            val cCount=sources.take(2).count{it!=null}.coerceAtLeast(1)
            // Level linkage changes actual PCM gains. No fake visual presets.
            val weights=doubleArrayOf(carrierWeight/cCount,carrierWeight/cCount,affirmWeight*primaryWeight,affirmWeight*(1-primaryWeight))
            if(sources[2]==null&&sources[3]!=null)weights[3]=affirmWeight
            if(sources[3]==null&&sources[2]!=null)weights[2]=affirmWeight
            val gainSum=sources.indices.sumOf{if(sources[it]!=null)weights[it] else 0.0}
            val normalization=if(gainSum>0.95)0.95/gainSum else 1.0
            val tmp=File(dest.parentFile,dest.name+".partial")
            FileOutputStream(tmp).buffered(65536).use{out->
                WavIO.writeHeader(out,frames,RATE,2)
                val chunkFrames=4096
                val buf=ByteArray(chunkFrames*4)
                var frame=0L
                while(frame<frames){
                    if(stop())throw InterruptedException("Subliminal render stopped")
                    val n=min(chunkFrames.toLong(),frames-frame).toInt()
                    var p=0
                    for(i in 0 until n){
                        for(ch in 0..1){
                            var mixed=0.0
                            for(k in sources.indices){val src=sources[k]?:continue;val gain=weights[k]
                                mixed+=src.at(frame+i,ch,loop=true)*gain
                            }
                            val pcm=WavIO.floatToS16((mixed*normalization).toFloat())
                            buf[p++]=(pcm and 255).toByte();buf[p++]=((pcm shr 8)and 255).toByte()
                        }
                    }
                    out.write(buf,0,p);frame+=n
                    if(frame%(RATE*5L)<chunkFrames)update("Mixing: ${frame*100/frames}% · ${frame/RATE}s / ${minutes*60}s")
                }
            }
            if(dest.exists())dest.delete()
            require(tmp.renameTo(dest)){"Cannot publish subliminal WAV"}
        } finally {sources.forEach{it?.close()}}
    }
}
