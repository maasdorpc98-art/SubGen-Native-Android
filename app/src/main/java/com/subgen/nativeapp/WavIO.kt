package com.subgen.nativeapp

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import kotlin.math.*

object WavIO {
    private fun le16(v:Int,out:OutputStream){out.write(v and 255);out.write((v ushr 8) and 255)}
    private fun le32(v:Long,out:OutputStream){for(i in 0..3)out.write(((v ushr (8*i)) and 255).toInt())}
    fun writeHeader(out:OutputStream,frames:Long,rate:Int,channels:Int){
        require(frames*channels*2 < 0xffffffffL-36){"PCM WAV over 4GB is not supported"}
        out.write("RIFF".toByteArray(Charsets.US_ASCII));le32(frames*channels*2+36,out)
        out.write("WAVEfmt ".toByteArray(Charsets.US_ASCII));le32(16,out);le16(1,out);le16(channels,out)
        le32(rate.toLong(),out);le32((rate*channels*2).toLong(),out);le16(channels*2,out);le16(16,out)
        out.write("data".toByteArray(Charsets.US_ASCII));le32(frames*channels*2,out)
    }
    fun floatToS16(x:Float):Int=(x.coerceIn(-1f,1f)*32767f).roundToInt().coerceIn(-32768,32767)
    fun writeMono(file:File,samples:FloatArray,rate:Int) {
        val tmp=File(file.parentFile,file.name+".tmp")
        FileOutputStream(tmp).buffered(65536).use { out ->
            writeHeader(out,samples.size.toLong(),rate,1)
            val pcm=ByteArray(65536)
            var i=0
            while(i<samples.size) {
                val count=min(32768,samples.size-i)
                for(k in 0 until count) {
                    val pcmValue=floatToS16(samples[i+k])
                    pcm[2*k]=(pcmValue and 255).toByte()
                    pcm[2*k+1]=(pcmValue ushr 8).toByte()
                }
                out.write(pcm,0,count*2)
                i+=count
            }
        }
        if(file.exists())file.delete()
        require(tmp.renameTo(file)){"Cannot publish speech checkpoint"}
    }
    data class Clip(val file:File,val rate:Int,val frames:Long){
        fun open():FileInputStream{val f=FileInputStream(file);f.channel.position(44);return f}
    }
    fun loadMono(file:File):Clip{RandomAccessFile(file,"r").use{r->
        require(r.length()>=44){"Short WAV file"};r.seek(0)
        val id=ByteArray(4);r.readFully(id);require(String(id,Charsets.US_ASCII)=="RIFF"){"Invalid RIFF header"}
        r.seek(8);r.readFully(id);require(String(id,Charsets.US_ASCII)=="WAVE"){"Invalid WAVE header"}
        fun u32(off:Long):Long{r.seek(off);return Integer.toUnsignedLong(Integer.reverseBytes(r.readInt()))}
        fun u16(off:Long):Int{r.seek(off);return java.lang.Short.toUnsignedInt(java.lang.Short.reverseBytes(r.readShort()))}
        require(u16(20)==1&&u16(22)==1&&u16(34)==16){"Checkpoint is not 16-bit mono WAV"}
        val rate=u32(24).toInt();val bytes=u32(40)
        require(rate in 16000..48000&&bytes+44<=r.length()){ "Invalid checkpoint WAV length" }
        return Clip(file,rate,bytes/2)
    }}
    // Audio is stitched directly from on-disk 16-bit checkpoints, not held in memory.
    fun assemble(nodes:List<ScriptNode>,checkpoints:List<File?>,dest:File,
        binOn:Boolean=false,centerHz:Double=240.0,beatHz:Double=6.0,beatGain:Double=0.008,
        pacingWpm:Int?=null):Double{
        require(nodes.size==checkpoints.size)
        val sr=24000
        // Open and validate headers once, not repeatedly for every pass.
        val clips=arrayOfNulls<Clip>(nodes.size)
        var frames=0L
        for(i in nodes.indices) when(val node=nodes[i]) {
            is ScriptNode.Pause -> frames+=(node.seconds*sr).roundToLong()
            is ScriptNode.Speech -> {
                val clip=loadMono(checkpoints[i]?:error("Missing checkpoint $i"))
                require(clip.rate==sr){"Expected 24kHz Kokoro output"}
                clips[i]=clip
                frames+=clip.frames
            }
            else -> Unit
        }
        val pacing=WordPacing.plan(nodes,LongArray(nodes.size){clips[it]?.frames?:0L},sr,pacingWpm)
        frames+=pacing.totalAddedFrames
        val tmp=File(dest.parentFile,dest.name+".partial")
        FileOutputStream(tmp).buffered(65536).use { out ->
            writeHeader(out,frames,sr,2)
            val inputBytes=ByteArray(32768)
            val outputBytes=ByteArray(65536)
            val silenceBytes=ByteArray(65536)
            var frame=0L
            // Binaural math advances by stable oscillator recurrence rather than
            // calling sin() twice per 24 kHz sample. Re-anchor every 4096 frames.
            val leftHz=centerHz-beatHz/2.0
            val rightHz=centerHz+beatHz/2.0
            val stepL=2.0*PI*leftHz/sr
            val stepR=2.0*PI*rightHz/sr
            val stepSinL=sin(stepL);val stepCosL=cos(stepL)
            val stepSinR=sin(stepR);val stepCosR=cos(stepR)
            var sinL=0.0;var cosL=1.0
            var sinR=0.0;var cosR=1.0
            var pending=0
            fun flush() {
                if(pending>0){out.write(outputBytes,0,pending);pending=0}
            }
            fun appendWithBinaural(sample:Int) {
                if(frame%4096L==0L){
                    val phaseL=2.0*PI*leftHz*frame/sr
                    val phaseR=2.0*PI*rightHz*frame/sr
                    sinL=sin(phaseL);cosL=cos(phaseL)
                    sinR=sin(phaseR);cosR=cos(phaseR)
                }
                val centered=sample/32768.0
                val left=floatToS16((centered+sinL*beatGain).toFloat())
                val right=floatToS16((centered+sinR*beatGain).toFloat())
                outputBytes[pending++]=(left and 255).toByte()
                outputBytes[pending++]=(left ushr 8).toByte()
                outputBytes[pending++]=(right and 255).toByte()
                outputBytes[pending++]=(right ushr 8).toByte()
                if(pending==outputBytes.size)flush()
                val nextL=sinL*stepCosL+cosL*stepSinL
                cosL=cosL*stepCosL-sinL*stepSinL
                sinL=nextL
                val nextR=sinR*stepCosR+cosR*stepSinR
                cosR=cosR*stepCosR-sinR*stepSinR
                sinR=nextR
                frame++
            }
            fun addPause(length:Long) {
                if(binOn){
                    var remaining=length
                    while(remaining>0){appendWithBinaural(0);remaining--}
                } else {
                    var remaining=length
                    while(remaining>0){
                        val take=min(remaining,16384L).toInt()
                        out.write(silenceBytes,0,take*4)
                        frame+=take
                        remaining-=take
                    }
                }
            }
            var previousGain=1.0
            for(i in nodes.indices) when(val node=nodes[i]){
                is ScriptNode.Pause -> addPause((node.seconds*sr).roundToLong())
                is ScriptNode.Speech -> {
                    val clip=clips[i]?:error("Missing validated clip")
                    val gainDb=node.gainDb?:0.0
                    require(gainDb.isFinite() && gainDb in -30.0..6.0){"Unsafe phrase gain"}
                    val targetGain=10.0.pow(gainDb/20.0)
                    var position=0L
                    // 8 ms amplitude ramp uses existing PCM frames; never changes pause length.
                    fun applyGain(sample:Int):Int {
                        val fade=(position.toDouble()/192.0).coerceIn(0.0,1.0)
                        val applied=previousGain+(targetGain-previousGain)*fade
                        position++
                        val result=sample*applied
                        require(result in -32767.0..32767.0){"Phrase gain would clip speech; lower section or phrase gain"}
                        return result.toInt()
                    }
                    clip.open().buffered(65536).use { input ->
                        var remaining=clip.frames
                        while(remaining>0){
                            val count=min(remaining,16384L).toInt()
                            val length=count*2
                            var read=0
                            while(read<length){
                                val size=input.read(inputBytes,read,length-read)
                                require(size>0){"Truncated checkpoint"}
                                read+=size
                            }
                            if(binOn) {
                                for(k in 0 until count){
                                    val p=k*2
                                    val sample=((inputBytes[p].toInt() and 255) or
                                        ((inputBytes[p+1].toInt() and 255) shl 8)).toShort().toInt()
                                    appendWithBinaural(applyGain(sample))
                                }
                            } else {
                                for(k in 0 until count) {
                                    val p=k*2
                                    val sample=((inputBytes[p].toInt() and 255) or
                                        ((inputBytes[p+1].toInt() and 255) shl 8)).toShort().toInt()
                                    // Preserve the legacy 32767/32768 scaling exactly.
                                    val pcm=floatToS16((applyGain(sample)/32768.0).toFloat())
                                    val q=k*4
                                    val lo=(pcm and 255).toByte()
                                    val hi=(pcm ushr 8).toByte()
                                    outputBytes[q]=lo;outputBytes[q+1]=hi
                                    outputBytes[q+2]=lo;outputBytes[q+3]=hi
                                }
                                out.write(outputBytes,0,count*4)
                                frame+=count
                            }
                            remaining-=count
                        }
                    }
                    previousGain=targetGain
                    if(pacing.extraFrames[i]>0L)addPause(pacing.extraFrames[i])
                }
                else -> Unit
            }
            flush()
            check(frame==frames){"WAV frame mismatch: $frame versus $frames"}
        }
        if(dest.exists())dest.delete()
        require(tmp.renameTo(dest)){"Cannot publish final WAV"}
        return frames.toDouble()/sr
    }
    fun saveToDownloads(context:Context,source:File,name:String):android.net.Uri{
        val cv=ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name.removeSuffix(".wav").replace(Regex("[^A-Za-z0-9_. -]"),"_").take(96)+".wav")
            put(MediaStore.MediaColumns.MIME_TYPE,"audio/wav")
            put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/SubGen")
            put(MediaStore.MediaColumns.IS_PENDING,1)
        }
        val resolver=context.contentResolver
        val uri=resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,cv)?:error("Unable to create Downloads file")
        try{resolver.openOutputStream(uri,"w")!!.use{out->source.inputStream().use{it.copyTo(out,65536)}}
            val update=ContentValues().apply{put(MediaStore.MediaColumns.IS_PENDING,0)}
            resolver.update(uri,update,null,null)
            return uri
        }catch(t:Throwable){resolver.delete(uri,null,null);throw t}
    }
}