package com.subgen.nativeapp

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Import phone audio from Android SAF into verified PCM16 WAV for the field
 * overlay. Standard WAV is copied losslessly. MP3/M4A/AAC and other Android
 * device-supported audio formats are decoded using platform MediaCodec.
 */
object MorphicCarrierImport {
    private const val MAX_DECODED=250L*1024L*1024L
    fun `import`(context:Context,uri:Uri,stop:()->Boolean={false}):File{
        val name=runCatching{context.contentResolver.query(
            uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
            if(cursor.moveToFirst())cursor.getString(0) else null
        }}.getOrNull() ?: "selected-audio"
        val partial=File(context.filesDir,"morphic-carrier-import.partial")
        val final=File(context.filesDir,"morphic-carrier-selected.wav")
        var complete=false
        try{
            if(name.endsWith(".wav",true) || isWav(context,uri)){
                context.contentResolver.openInputStream(uri).use{incoming->
                    require(incoming!=null){"Unable to read carrier audio"}
                    FileOutputStream(partial).buffered(65536).use{output->
                        val buffer=ByteArray(65536)
                        var written=0L
                        while(true){
                            if(stop())throw InterruptedException("Carrier import cancelled")
                            val n=incoming.read(buffer)
                            if(n<0)break
                            if(n==0)continue
                            written+=n
                            require(written<=MAX_DECODED){"Carrier audio exceeds 250 MB decoded limit"}
                            output.write(buffer,0,n)
                        }
                    }
                }
            } else decode(context,uri,partial,stop)
            MorphicCarrierMixer.readHeader(partial)
            if(stop())throw InterruptedException("Carrier import cancelled")
            if(final.exists())require(final.delete()){"Could not replace previous carrier"}
            require(partial.renameTo(final)){"Unable to finish carrier import"}
            complete=true
            return final
        }finally{if(!complete)partial.delete()}
    }
    private fun isWav(context:Context,uri:Uri):Boolean=runCatching{
        context.contentResolver.openInputStream(uri)?.use{stream->
            val first=ByteArray(12)
            var at=0
            while(at<first.size){
                val n=stream.read(first,at,first.size-at)
                if(n<0)break
                at+=n
            }
            at==12 && String(first,0,4,Charsets.US_ASCII)=="RIFF" &&
                String(first,8,4,Charsets.US_ASCII)=="WAVE"
        }?:false
    }.getOrDefault(false)

    private fun decode(context:Context,uri:Uri,file:File,stop:()->Boolean){
        val extractor=MediaExtractor()
        var codec:MediaCodec?=null
        try{
            extractor.setDataSource(context,uri,null)
            val track=(0 until extractor.trackCount).firstOrNull{index->
                extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/")==true
            }?:error("Selected file has no supported audio track")
            val inputFormat=extractor.getTrackFormat(track)
            val mime=inputFormat.getString(MediaFormat.KEY_MIME) ?:error("Unknown audio format")
            require(mime!="audio/raw"){"For uncompressed audio use a standard 16-bit PCM WAV"}
            extractor.selectTrack(track)
            codec=MediaCodec.createDecoderByType(mime)
            codec.configure(inputFormat,null,null,0)
            codec.start()
            val info=MediaCodec.BufferInfo()
            var noMoreInput=false
            var done=false
            var rate=0
            var channels=0
            var formatCode=AudioFormat.ENCODING_PCM_16BIT
            var bytes=0L
            FileOutputStream(file).buffered(65536).use{output->
                output.write(ByteArray(44))
                var spins=0
                while(!done){
                    if(stop())throw InterruptedException("Carrier import cancelled")
                    require(spins++<100000){"Audio decoder is not making progress"}
                    if(!noMoreInput){
                        val index=codec.dequeueInputBuffer(10000)
                        if(index>=0){
                            val input=codec.getInputBuffer(index)?:error("Missing audio input")
                            input.clear()
                            val length=extractor.readSampleData(input,0)
                            if(length<0){
                                codec.queueInputBuffer(index,0,0,0,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                noMoreInput=true
                            }else{
                                codec.queueInputBuffer(index,0,length,
                                    extractor.sampleTime.coerceAtLeast(0L),0)
                                extractor.advance()
                            }
                        }
                    }
                    when(val index=codec.dequeueOutputBuffer(info,10000)){
                        MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            val f=codec.outputFormat
                            rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            formatCode=if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                            else AudioFormat.ENCODING_PCM_16BIT
                            require(rate in 8000..96000 && channels in 1..2) {
                                "Carrier decoder must produce mono/stereo PCM at 8–96 kHz"
                            }
                            require(formatCode in listOf(AudioFormat.ENCODING_PCM_16BIT,
                                AudioFormat.ENCODING_PCM_FLOAT)) {
                                "Carrier decoder produced unsupported PCM encoding"
                            }
                        }
                        else -> if(index>=0){
                            if(rate==0){
                                val f=codec.outputFormat
                                rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                                channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                                formatCode=if(f.containsKey(MediaFormat.KEY_PCM_ENCODING))
                                    f.getInteger(MediaFormat.KEY_PCM_ENCODING)
                                else AudioFormat.ENCODING_PCM_16BIT
                            }
                            val chunk=codec.getOutputBuffer(index)?:error("Missing decoded PCM")
                            chunk.position(info.offset)
                            chunk.limit(info.offset+info.size)
                            chunk.order(ByteOrder.LITTLE_ENDIAN)
                            if(info.size>0){
                                require(rate in 8000..96000 && channels in 1..2)
                                if(formatCode==AudioFormat.ENCODING_PCM_16BIT){
                                    require(info.size%(channels*2)==0){"Audio decoder output not frame-aligned"}
                                    val data=ByteArray(min(info.size,65536))
                                    while(chunk.hasRemaining()){
                                        val take=min(data.size,chunk.remaining())
                                        chunk.get(data,0,take)
                                        output.write(data,0,take)
                                        bytes+=take
                                        require(bytes<=MAX_DECODED){"Carrier exceeds 250 MB decoded limit"}
                                    }
                                } else if(formatCode==AudioFormat.ENCODING_PCM_FLOAT){
                                    require(info.size%(channels*4)==0)
                                    val data=ByteArray(16384)
                                    var pos=0
                                    while(chunk.remaining()>=4){
                                        val sample=chunk.float
                                        val value=WavIO.floatToS16(sample)
                                        if(pos+2>data.size){output.write(data,0,pos);bytes+=pos;pos=0}
                                        data[pos++]=(value and 255).toByte()
                                        data[pos++]=(value ushr 8).toByte()
                                    }
                                    if(pos>0){output.write(data,0,pos);bytes+=pos}
                                    require(bytes<=MAX_DECODED){"Carrier exceeds 250 MB decoded limit"}
                                }else error("Unsupported decoded PCM encoding")
                            }
                            done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                            codec.releaseOutputBuffer(index,false)
                            spins=0
                        }
                    }
                }
            }
            require(bytes>0 && rate in 8000..96000 && channels in 1..2)
            require(bytes%(channels*2)==0L)
            val header=ByteArrayOutputStream(44)
            WavIO.writeHeader(header,bytes/(channels*2),rate,channels)
            RandomAccessFile(file,"rw").use{wav->wav.seek(0);wav.write(header.toByteArray())}
        } finally {
            runCatching{codec?.stop()}
            runCatching{codec?.release()}
            extractor.release()
        }
    }
}
