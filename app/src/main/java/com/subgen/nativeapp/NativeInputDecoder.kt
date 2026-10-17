package com.subgen.nativeapp

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Android MediaCodec audio decoding. Imported MP3/M4A/FLAC files are converted once
 * to disk-backed PCM16 WAV; the native mixer never silently accepts non-WAV bytes.
 * Runs only in RenderService's background executor, never on the UI thread.
 */
object NativeInputDecoder {
    fun prepare(file:File, update:(String)->Unit,stop:()->Boolean):File {
        require(file.isFile&&file.length()>0){"Missing source ${file.name}"}
        if (isPcm16Wav(file))return file
        val out=File(file.parentFile,file.name+".decoded.wav")
        if(out.isFile&&out.lastModified()>=file.lastModified()&&isPcm16Wav(out))return out
        update("Decoding ${file.name} with Android MediaCodec…")
        val temporary=File(out.parentFile,out.name+".partial")
        decode(file,temporary,stop)
        if(out.exists())out.delete()
        check(temporary.renameTo(out)){"Cannot save decoded WAV"}
        return out
    }
    private fun isPcm16Wav(file:File):Boolean=runCatching {
        RandomAccessFile(file,"r").use { r->
            if(r.length()<44)return@use false
            val header=ByteArray(44);r.readFully(header)
            val bytes=String(header,Charsets.ISO_8859_1)
            if(!bytes.startsWith("RIFF")||bytes.substring(8,12)!="WAVE")return@use false
            // Any noncanonical PCM16 WAV still goes through the decoder for normalisation.
            if(bytes.substring(12,16)!="fmt ")return@use false
            fun u16(off:Int)=(header[off].toInt() and 255)+((header[off+1].toInt() and 255) shl 8)
            u16(20)==1&&u16(34)==16&&u16(22) in 1..2
        }
    }.getOrDefault(false)
    private fun decode(input:File,output:File,stop:()->Boolean){
        val extractor=MediaExtractor()
        var codec:MediaCodec?=null
        try {
            extractor.setDataSource(input.absolutePath)
            val track=(0 until extractor.trackCount).firstOrNull{extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
                ?:error("No supported audio track in ${input.name}")
            extractor.selectTrack(track)
            val format=extractor.getTrackFormat(track)
            val mime=format.getString(MediaFormat.KEY_MIME)?:error("Missing input format")
            val decoder=MediaCodec.createDecoderByType(mime)
            codec=decoder
            decoder.configure(format,null,null,0)
            decoder.start()
            var inputEnd=false;var outputEnd=false
            var rate=0;var channels=0;var encoding=AudioFormat.ENCODING_PCM_16BIT
            var written=0L
            FileOutputStream(output).buffered(65536).use{dest->
                dest.write(ByteArray(44))
                val info=MediaCodec.BufferInfo()
                var emptyPolls=0
                while(!outputEnd){
                    if(stop())throw InterruptedException("Audio decode cancelled")
                    if(!inputEnd){
                        val index=decoder.dequeueInputBuffer(10000)
                        if(index>=0){val buffer=decoder.getInputBuffer(index)?:error("Decoder input buffer unavailable");buffer.clear()
                            val size=extractor.readSampleData(buffer,0)
                            if(size<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true}
                            else{decoder.queueInputBuffer(index,0,size,extractor.sampleTime,0);extractor.advance()}
                        }
                    }
                    val index=decoder.dequeueOutputBuffer(info,10000)
                    when(index){
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED->{
                            val outFmt=decoder.outputFormat
                            rate=outFmt.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                            channels=outFmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                            encoding=if(outFmt.containsKey(MediaFormat.KEY_PCM_ENCODING))outFmt.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                            require(rate in 8000..96000&&channels in 1..2){"Only mono/stereo PCM is supported"}
                        }
                        MediaCodec.INFO_TRY_AGAIN_LATER->{if(++emptyPolls>300)error("Audio decoder stopped responding")}
                        else->if(index>=0){
                            emptyPolls=0
                            if(info.size>0){
                                if(rate==0){val fmt=decoder.outputFormat;rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}
                                val buffer=decoder.getOutputBuffer(index)?:error("Decoder output buffer unavailable")
                                buffer.position(info.offset);buffer.limit(info.offset+info.size)
                                val raw=ByteArray(info.size);buffer.get(raw)
                                when(encoding){
                                    AudioFormat.ENCODING_PCM_16BIT->{dest.write(raw);written+=raw.size}
                                    AudioFormat.ENCODING_PCM_FLOAT->{
                                        val samples=ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                                        val pcm=ByteArray(samples.remaining()*2);var at=0
                                        while(samples.hasRemaining()){
                                            val v=WavIO.floatToS16(samples.get());pcm[at++]=(v and 255).toByte();pcm[at++]=(v shr 8).toByte()
                                        };dest.write(pcm);written+=pcm.size
                                    }
                                    else->error("Unsupported decoded PCM format: $encoding")
                                }
                            }
                            if(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0)outputEnd=true
                            decoder.releaseOutputBuffer(index,false)
                        }
                    }
                }
            }
            require(rate in 8000..96000 && channels in 1..2 && written>0){"No playable decoded audio"}
            require(written%(channels*2)==0L){"Decoder returned incomplete frames"}
            val header=ByteArrayOutputStream().also{WavIO.writeHeader(it,written/(channels*2),rate,channels)}.toByteArray()
            RandomAccessFile(output,"rw").use{it.seek(0);it.write(header)}
        }finally{runCatching{codec?.stop()};runCatching{codec?.release()};extractor.release()}
    }
}
