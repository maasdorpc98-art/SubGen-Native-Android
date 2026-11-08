package com.subgen.nativeapp

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.min

/**
 * RIFF WAV normalizer. Converts common mono/stereo PCM and float WAV layouts
 * (including LIST/JUNK/fact chunks and extended fmt) to canonical 44-byte PCM16.
 * Called after SAF data is copied to a local temporary file.
 */
object MorphicCarrierWav {
    const val MAX_BYTES=250L*1024L*1024L
    private fun u16(x:ByteArray,i:Int)=((x[i].toInt() and 255) or ((x[i+1].toInt() and 255) shl 8))
    private fun u32(x:ByteArray,i:Int):Long{
        var n=0L
        for(k in 0..3)n=n or ((x[i+k].toLong() and 255) shl (8*k))
        return n
    }
    private data class Format(val type:Int,val channels:Int,val rate:Int,val bits:Int,val frameBytes:Int)
    fun normalize(source:File,dest:File,stop:()->Boolean={false}):Long{
        require(source.isFile && source.length()>=44){"WAV file is empty or missing"}
        var complete=false
        try{
            RandomAccessFile(source,"r").use{r->
                val head=ByteArray(12)
                r.readFully(head)
                require(String(head,0,4,Charsets.US_ASCII)=="RIFF" &&
                    String(head,8,4,Charsets.US_ASCII)=="WAVE"){"Not a RIFF WAVE file"}
                val reportedSize=u32(head,4)+8L
                require(reportedSize<=r.length() && reportedSize>=44){"Truncated RIFF WAV"}
                var fmt:Format?=null
                var dataPosition=-1L
                var dataLength=-1L
                while(r.filePointer+8<=reportedSize){
                    if(stop())throw InterruptedException("Carrier normalization cancelled")
                    val chunk=ByteArray(8);r.readFully(chunk)
                    val tag=String(chunk,0,4,Charsets.US_ASCII)
                    val n=u32(chunk,4)
                    val start=r.filePointer
                    require(n<=reportedSize-start){"Invalid WAV chunk length"}
                    when(tag){
                        "fmt "->{
                            require(n in 16..4096){"Invalid WAV format block size"}
                            val block=ByteArray(n.toInt());r.readFully(block)
                            var encoding=u16(block,0)
                            val channels=u16(block,2)
                            val rate=u32(block,4).toInt()
                            val blockAlign=u16(block,12)
                            val bits=u16(block,14)
                            if(encoding==0xfffe){
                                require(block.size>=40){"Malformed extensible WAV format"}
                                encoding=u32(block,24).toInt()
                            }
                            require(encoding==1 || encoding==3){"WAV must contain PCM or IEEE float samples"}
                            require(channels in 1..2 && rate in 8000..96000){"Carrier WAV must be mono/stereo, 8–96 kHz"}
                            require(
                                if(encoding==3)bits==32 else bits in listOf(8,16,24,32)
                            ){"Unsupported WAV sample precision"}
                            require(blockAlign==channels*(bits/8)){"Invalid WAV frame alignment"}
                            fmt=Format(encoding,channels,rate,bits,blockAlign)
                        }
                        "data"->{dataPosition=start;dataLength=n}
                    }
                    val next=start+n+(n%2L)
                    require(next<=r.length()+(if(next==r.length()+1)1 else 0)){"Truncated WAV chunk"}
                    r.seek(next.coerceAtMost(r.length()))
                }
                val format=fmt?:error("WAV is missing its fmt chunk")
                require(dataPosition>=0 && dataLength>0){"WAV has no audio data"}
                require(dataLength%format.frameBytes==0L){"WAV contains an incomplete audio frame"}
                val frames=dataLength/format.frameBytes
                val outBytes=frames*format.channels*2L
                require(outBytes<=MAX_BYTES){"Decoded carrier exceeds 250 MB"}
                r.seek(dataPosition)
                FileOutputStream(dest).buffered(65536).use{out->
                    WavIO.writeHeader(out,frames,format.rate,format.channels)
                    val bytesPer= format.bits/8
                    val framesPerBlock=(32768 / format.frameBytes).coerceAtLeast(1)
                    val raw=ByteArray(framesPerBlock*format.frameBytes)
                    val converted=ByteArray(framesPerBlock*format.channels*2)
                    var done=0L
                    while(done<frames){
                        if(stop())throw InterruptedException("Carrier normalization cancelled")
                        val count=min(frames-done,framesPerBlock.toLong()).toInt()
                        r.readFully(raw,0,count*format.frameBytes)
                        var write=0
                        for(i in 0 until count*format.channels){
                            val at=i*bytesPer
                            val v=when{
                                format.type==3->{
                                    val bits=u32(raw,at).toInt()
                                    val sample=java.lang.Float.intBitsToFloat(bits)
                                    require(sample.isFinite()){"WAV contains an invalid floating-point sample"}
                                    WavIO.floatToS16(sample)
                                }
                                format.bits==8->((raw[at].toInt() and 255)-128)*256
                                format.bits==16->{
                                    (u16(raw,at).toShort()).toInt()
                                }
                                format.bits==24->{
                                    val hi=raw[at+2].toInt()
                                    (hi shl 8) or (raw[at+1].toInt() and 255)
                                }
                                else->u32(raw,at).toInt() shr 16
                            }
                            converted[write++]=(v and 255).toByte()
                            converted[write++]=(v ushr 8).toByte()
                        }
                        out.write(converted,0,write)
                        done+=count
                    }
                }
                val decoded=MorphicCarrierMixer.readHeader(dest)
                require(decoded.frames==frames && decoded.rate==format.rate &&
                    decoded.channels==format.channels){"Normalized WAV did not pass format validation"}
                complete=true
                return frames
            }
        }finally{if(!complete)dest.delete()}
    }
}
