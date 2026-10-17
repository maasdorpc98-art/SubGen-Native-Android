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
        FileOutputStream(tmp).buffered(65536).use{out->writeHeader(out,samples.size.toLong(),rate,1)
            for(s in samples)le16(floatToS16(s),out)
        }
        if(file.exists())file.delete();require(tmp.renameTo(file)){"Cannot publish speech checkpoint"}
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
        binOn:Boolean=false,centerHz:Double=240.0,beatHz:Double=6.0,beatGain:Double=0.008):Double{
        require(nodes.size==checkpoints.size)
        val sr=24000
        var frames=0L
        for(i in nodes.indices){when(val n=nodes[i]){
            is ScriptNode.Pause->frames+= (n.seconds*sr).roundToLong()
            is ScriptNode.Speech->{val clip=loadMono(checkpoints[i]?:error("Missing checkpoint $i"));require(clip.rate==sr){"Expected 24kHz Kokoro output"};frames+=clip.frames}
            else->Unit
        }}
        val tmp=File(dest.parentFile,dest.name+".partial")
        FileOutputStream(tmp).buffered(65536).use{out->writeHeader(out,frames,sr,2)
            var frame=0L
            fun emit(mono:Int){
                val x=mono/32768.0
                val left= if(binOn) (x+sin(2*PI*(centerHz-beatHz/2)*frame/sr)*beatGain) else x
                val right= if(binOn) (x+sin(2*PI*(centerHz+beatHz/2)*frame/sr)*beatGain) else x
                le16(floatToS16(left.toFloat()),out);le16(floatToS16(right.toFloat()),out);frame++
            }
            for(i in nodes.indices){when(val node=nodes[i]){
                is ScriptNode.Pause->repeat((node.seconds*sr).roundToInt()){emit(0)}
                is ScriptNode.Speech->loadMono(checkpoints[i]!!).open().buffered(65536).use{input->
                    val buffer=ByteArray(32768);var remaining=loadMono(checkpoints[i]!!).frames*2
                    while(remaining>0){val n=input.read(buffer,0,min(buffer.size.toLong(),remaining).toInt());if(n<=0)error("Truncated checkpoint");require(n%2==0)
                        for(pos in 0 until n step 2){val x=((buffer[pos].toInt() and 255) or (buffer[pos+1].toInt() shl 8)).toShort().toInt();emit(x)}
                        remaining-=n
                    }
                }
                else->Unit
            }}
        }
        if(dest.exists())dest.delete();require(tmp.renameTo(dest)){"Cannot publish final WAV"}
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
