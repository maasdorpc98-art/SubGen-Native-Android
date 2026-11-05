package com.subgen.nativeapp

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

/**
 * Experimental written-text waveform encoder, not evidence of a nonphysical field.
 * Bit 0 = 800Hz, bit 1 = 1600Hz, each 5ms, 24kHz PCM16 mono.
 * Packet: "SGMF" + version + big-endian payload length + SHA-256 + exact UTF-8.
 */
object MorphicFieldEngine {
    private const val RATE = 24000
    private const val BIT_SAMPLES = 120
    private const val F0 = 800.0
    private const val F1 = 1600.0
    private const val GAIN = 0.18
    private const val HEADER_BYTES = 41
    private val MAGIC = byteArrayOf(83, 71, 77, 70)

    data class Report(val minutes:Int,val inputBytes:Int,val completeCycles:Long,
                      val inputSha256:String,val outputFile:File)

    private fun packet(text:String):Pair<ByteArray,ByteArray> {
        require(text.isNotBlank()) { "Enter written affirmations first" }
        val payload = text.toByteArray(Charsets.UTF_8)
        require(payload.size in 1..16384) { "Text must contain 1 to 16384 UTF-8 bytes" }
        val digest = MessageDigest.getInstance("SHA-256").digest(payload)
        val frame = ByteArray(HEADER_BYTES+payload.size)
        MAGIC.copyInto(frame,0); frame[4] = 1
        val n=payload.size
        for (i in 0..3) frame[5+i] = (n ushr (24-8*i)).toByte()
        digest.copyInto(frame,9)
        payload.copyInto(frame,HEADER_BYTES)
        return frame to digest
    }

    fun render(text:String, minutes:Int, dest:File,
               update:(String)->Unit = {}, stop:()->Boolean = {false}):Report {
        require(minutes in 1..60) { "Choose 1 to 60 minutes" }
        val (frameBytes,digest)=packet(text)
        val framesPerCycle=frameBytes.size.toLong()*8L*BIT_SAMPLES
        val totalFrames=minutes*60L*RATE
        require(framesPerCycle<=totalFrames) { "Text too long for $minutes minutes; choose longer duration" }
        val temp=File(dest.parentFile,dest.name+".partial")
        var completed=false
        try {
            FileOutputStream(temp).buffered(65536).use { out ->
                WavIO.writeHeader(out,totalFrames,RATE,1)
                val buffer=ByteArray(16384)
                var used=0
                var written=0L
                var lastReported=-1L
                while(written<totalFrames) {
                    if(stop()) throw InterruptedException("Morphic waveform render cancelled")
                    for(b in frameBytes) {
                        for(bitIndex in 7 downTo 0) {
                            val one=((b.toInt() ushr bitIndex) and 1)!=0
                            val hz=if(one) F1 else F0
                            val count=min(BIT_SAMPLES.toLong(),totalFrames-written).toInt()
                            for(i in 0 until count) {
                                val ramp=min(1.0,min(i,BIT_SAMPLES-1-i)/8.0)
                                val sample=(GAIN*ramp*sin(2*PI*hz*i/RATE)).toFloat()
                                val pcm=WavIO.floatToS16(sample)
                                if(used+2>buffer.size) {out.write(buffer,0,used); used=0}
                                buffer[used++]=(pcm and 255).toByte()
                                buffer[used++]=((pcm ushr 8) and 255).toByte()
                            }
                            written+=count
                            if(written>=totalFrames) break
                        }
                        if(written>=totalFrames) break
                    }
                    val seconds=written/RATE
                    if(seconds!=lastReported) {
                        lastReported=seconds
                        update("Encoding text waveform: ${written*100/totalFrames}%")
                    }
                }
                if(used>0) out.write(buffer,0,used)
            }
            if(stop()) throw InterruptedException("Morphic waveform render cancelled")
            if(dest.exists()) require(dest.delete()) { "Cannot replace previous output" }
            require(temp.renameTo(dest)) { "Cannot publish waveform WAV" }
            completed=true
        } finally { if(!completed) temp.delete() }
        val hash=digest.joinToString(""){"%02x".format(it)}
        return Report(minutes,frameBytes.size-HEADER_BYTES,totalFrames/framesPerCycle,hash,dest)
    }

    fun verifyText(file:File):String {
        FileInputStream(file).buffered(65536).use { input ->
            val wav=ByteArray(44)
            var count=0
            while(count<wav.size) {
                val n=input.read(wav,count,wav.size-count)
                require(n>0) { "Truncated WAV" }
                count+=n
            }
            require(String(wav,0,4)=="RIFF" && String(wav,8,4)=="WAVE") {
                "Not a standard PCM WAV"
            }
            fun readByte():Int {
                var value=0
                for(bit in 7 downTo 0) {
                    var dot0=0.0; var dot1=0.0
                    for(i in 0 until BIT_SAMPLES) {
                        val lo=input.read();val hi=input.read()
                        require(lo>=0 && hi>=0) { "Truncated waveform packet" }
                        val sample=((hi shl 8) or lo).toShort().toInt().toDouble()
                        dot0+=sample*sin(2*PI*F0*i/RATE)
                        dot1+=sample*sin(2*PI*F1*i/RATE)
                    }
                    if(abs(dot1)>abs(dot0)) value=value or (1 shl bit)
                }
                return value
            }
            val header=ByteArray(HEADER_BYTES) { readByte().toByte() }
            require(header.copyOfRange(0,4).contentEquals(MAGIC) && header[4]==1.toByte()) {
                "Encoded waveform signature missing"
            }
            var size=0
            for(i in 5..8) size=(size shl 8) or (header[i].toInt() and 255)
            require(size in 1..16384) { "Invalid encoded length" }
            val payload=ByteArray(size) { readByte().toByte() }
            val digest=MessageDigest.getInstance("SHA-256").digest(payload)
            require(digest.contentEquals(header.copyOfRange(9,41))) {
                "Encoded text checksum failed"
            }
            return payload.toString(Charsets.UTF_8)
        }
    }
}
