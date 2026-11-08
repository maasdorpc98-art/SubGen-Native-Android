package com.subgen.nativeapp

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Streaming carrier + Morphic stereo PCM mixer.
 * Works with decoded 16-bit PCM WAV carriers: 8-96 kHz, mono or stereo.
 * Importing common compressed audio is handled separately by Android.
 */
object MorphicCarrierMixer {
    const val RATE=24000
    data class Gain(val morphic:Int=100,val carrier:Int=65,val master:Int=80) {
        init {
            require(morphic in 5..100){"Morphic volume must be 5–100% to retain the encoded signal"}
            require(carrier in 0..100 && master in 5..100){"Volume range is 0–100%; master must be 5% or more"}
        }
        val morphicScale get()=morphic/100.0*master/100.0
        val carrierScale get()=carrier/100.0*master/100.0
    }
    data class Stats(val frames:Long,val channels:Int,val peak:Double,val clippedFrames:Long,val carrierUsed:Boolean)

    data class Header(val file:File,val channels:Int,val rate:Int,val frames:Long){
        val frameBytes get()=channels*2
    }
    fun readHeader(file:File):Header{
        require(file.isFile && file.length()>=44){"Carrier WAV is missing or empty"}
        RandomAccessFile(file,"r").use{r->
            val head=ByteArray(44);r.readFully(head)
            fun u16(i:Int)=ByteBuffer.wrap(head,i,2).order(ByteOrder.LITTLE_ENDIAN).short.toInt() and 0xffff
            fun u32(i:Int)=ByteBuffer.wrap(head,i,4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
            require(String(head,0,4,Charsets.US_ASCII)=="RIFF" &&
                String(head,8,4,Charsets.US_ASCII)=="WAVE" &&
                String(head,12,4,Charsets.US_ASCII)=="fmt " &&
                String(head,36,4,Charsets.US_ASCII)=="data"){"Expected standard 44-byte PCM WAV"}
            require(u16(20)==1 && u16(34)==16){"Carrier WAV must be 16-bit PCM"}
            val channels=u16(22)
            val rate=u32(24).toInt()
            require(channels in 1..2 && rate in 8000..96000){"Carrier must be mono/stereo at 8–96 kHz"}
            val bytes=u32(40)
            require(bytes>0 && bytes%(channels*2)==0L && bytes+44<=r.length()){
                "Carrier WAV header has an invalid data length"
            }
            return Header(file,channels,rate,bytes/(channels*2))
        }
    }

    private fun readFully(input:BufferedInputStream,buffer:ByteArray){
        var pos=0
        while(pos<buffer.size){
            val n=input.read(buffer,pos,buffer.size-pos)
            require(n>0){"Unexpected end of PCM audio"}
            pos+=n
        }
    }

    /**
     * Looping frame reader with linear sample-rate conversion.
     * Allocation-free hot path: left and right are overwritten by advance().
     */
    class CarrierReader(file:File):AutoCloseable{
        private val meta=readHeader(file)
        private var stream=BufferedInputStream(FileInputStream(file),65536)
        private val buffer=ByteArray(meta.frameBytes)
        private var readFrames=0L
        private val step=meta.rate.toDouble()/RATE
        private var fraction=0.0
        private var left0=0.0
        private var right0=0.0
        private var left1=0.0
        private var right1=0.0
        var left=0.0;private set
        var right=0.0;private set
        private var nextLeft=0.0
        private var nextRight=0.0
        init {
            resetStream()
            readFrame()
            left0=nextLeft;right0=nextRight
            readFrame()
            left1=nextLeft;right1=nextRight
        }
        private fun resetStream(){
            stream.close()
            stream=BufferedInputStream(FileInputStream(meta.file),65536)
            var remaining=44L
            while(remaining>0){
                val n=stream.skip(remaining)
                require(n>0){"Invalid audio header"}
                remaining-=n
            }
            readFrames=0
        }
        private fun readFrame(){
            if(readFrames>=meta.frames)resetStream()
            readFully(stream,buffer)
            readFrames++
            val lo0=buffer[0].toInt() and 255
            val hi0=buffer[1].toInt() and 255
            nextLeft=(((hi0 shl 8) or lo0).toShort().toInt())/32768.0
            if(meta.channels==2){
                val lo1=buffer[2].toInt() and 255
                val hi1=buffer[3].toInt() and 255
                nextRight=(((hi1 shl 8) or lo1).toShort().toInt())/32768.0
            }else nextRight=nextLeft
        }
        fun advance(){
            left=left0+(left1-left0)*fraction
            right=right0+(right1-right0)*fraction
            fraction+=step
            while(fraction>=1.0){
                fraction-=1.0
                left0=left1;right0=right1
                readFrame()
                left1=nextLeft;right1=nextRight
            }
        }
        override fun close(){stream.close()}
    }

    private fun pcm(stream:BufferedInputStream):Double{
        val lo=stream.read();val hi=stream.read()
        require(lo>=0 && hi>=0){"Unexpected end of Morphic source PCM"}
        return (((hi shl 8) or lo).toShort().toInt())/32768.0
    }
    fun mix(
        original:File,carrier:File?,dest:File,volume:Gain,
        report:(Int)->Unit={},cancel:()->Boolean={false}
    ):Stats{
        val source=readHeader(original)
        require(source.rate==RATE){"Morphic source must be 24 kHz PCM"}
        require(carrier==null || source.channels==2){
            "Carrier mixing requires Ambient Stereo Morphic mode"
        }
        val channels=source.channels
        val temp=File(dest.parentFile,dest.name+".partial")
        var completed=false
        var peak=0.0
        var clipping=0L
        val carrierReader=carrier?.let(::CarrierReader)
        try{
            BufferedInputStream(FileInputStream(original),65536).use{input->
                var remaining=44L
                while(remaining>0){
                    val skipped=input.skip(remaining)
                    require(skipped>0)
                    remaining-=skipped
                }
                FileOutputStream(temp).buffered(65536).use{output->
                    WavIO.writeHeader(output,source.frames,RATE,channels)
                    val buffer=ByteArray(32768)
                    var pos=0
                    var frame=0L
                    var last=-1
                    while(frame<source.frames){
                        if(frame%2048L==0L && cancel())throw InterruptedException("Carrier mix cancelled")
                        if(carrierReader!=null)carrierReader.advance()
                        var clipped=false
                        for(ch in 0 until channels){
                            val originalValue=pcm(input)
                            val carrierValue=if(carrierReader==null)0.0
                                else if(ch==0)carrierReader.left else carrierReader.right
                            val amplitude=originalValue*volume.morphicScale+
                                carrierValue*volume.carrierScale
                            peak=max(peak,abs(amplitude))
                            if(abs(amplitude)>1.0)clipped=true
                            val sample=WavIO.floatToS16(amplitude.coerceIn(-1.0,1.0).toFloat())
                            if(pos+2>buffer.size){output.write(buffer,0,pos);pos=0}
                            buffer[pos++]=(sample and 255).toByte()
                            buffer[pos++]=(sample ushr 8).toByte()
                        }
                        if(clipped)clipping++
                        frame++
                        if(frame%4096L==0L || frame==source.frames){
                            val percent=(frame*100/source.frames).toInt()
                            if(last!=percent){last=percent;report(percent)}
                        }
                    }
                    if(pos>0)output.write(buffer,0,pos)
                }
            }
            require(clipping==0L){
                "Audio clipping in $clipping frames. Lower carrier or master volume."
            }
            if(cancel())throw InterruptedException("Carrier mix cancelled")
            if(dest.exists())require(dest.delete()){"Cannot replace previous field WAV"}
            require(temp.renameTo(dest)){"Unable to publish mixed WAV"}
            completed=true
            return Stats(source.frames,channels,peak,clipping,carrier!=null)
        } finally {
            carrierReader?.close()
            if(!completed)temp.delete()
        }
    }
}
