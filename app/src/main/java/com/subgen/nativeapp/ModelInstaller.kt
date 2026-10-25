package com.subgen.nativeapp

import android.content.Context
import android.os.StatFs
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Downloads once, expands to private app storage and atomically publishes a complete installation. */
class ModelInstaller(private val context:Context) {
    companion object {
        const val MODEL="kokoro-multi-lang-v1_0"
        const val MODEL_VARIANT="FP32"
        const val DOWNLOAD_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-multi-lang-v1_0.tar.bz2"
    }
    val directory:File get()=File(context.filesDir,"models/$MODEL")
    fun present():Boolean {
        val dir=directory
        return dir.isDirectory && File(dir,"model.onnx").length()>280_000_000L &&
            File(dir,"voices.bin").length()>1_000_000 && File(dir,"tokens.txt").length()>100 && File(dir,"espeak-ng-data").isDirectory
    }
    fun modelFile():File=File(directory,"model.onnx")
    fun install(status:(String)->Unit) {
        if (present()) {status("Kokoro model already installed on device");return}
        val dir=directory;dir.parentFile!!.mkdirs()
        // Approximate reserve for compressed model, extracted files, and working files.
        require(StatFs(context.filesDir.absolutePath).availableBytes>1_100L*1024*1024){"Need at least 1.1 GB free for one-time FP32 model installation"}
        val archive=File(context.cacheDir,"$MODEL.download")
        val stage=File(dir.parentFile,"$MODEL-installing")
        stage.deleteRecursively();stage.mkdirs()
        try {
            status("Downloading unquantized Kokoro FP32 model (once; ~310 MB model)…")
            Diagnostics.record("MODEL_DOWNLOAD_STARTED",MODEL_VARIANT)
            // Resume interrupted FP32 archives instead of downloading hundreds of MB twice.
            // HTTP 200 means the origin ignored Range, so restart cleanly from byte zero.
            var downloaded=false
            for(attempt in 1..5) {
                val offset=archive.length().coerceAtLeast(0L)
                var connection:HttpURLConnection?=null
                try {
                    connection=(URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                        connectTimeout=25_000
                        readTimeout=60_000
                        instanceFollowRedirects=true
                        setRequestProperty("Accept-Encoding","identity")
                        if(offset>0) setRequestProperty("Range","bytes=$offset-")
                    }
                    val http=connection.responseCode
                    if(http==416) {
                        archive.delete()
                        throw java.io.IOException("Cannot resume incomplete archive; reset needed")
                    }
                    require(http==200 || http==206){"Model download returned HTTP $http"}
                    val ranged=http==206 && offset>0
                    if(ranged) {
                        val contentRange=connection.getHeaderField("Content-Range").orEmpty()
                        require(contentRange.startsWith("bytes $offset-")){
                            "Server returned incorrect model download byte offset"
                        }
                    }
                    val append=ranged
                    val expected=connection.contentLengthLong
                    var received=0L
                    var lastStatus=if(append)offset else 0L
                    connection.inputStream.use { input ->
                        FileOutputStream(archive,append).buffered(262144).use { output ->
                            val buf=ByteArray(262144)
                            while(true) {
                                val n=input.read(buf)
                                if(n<0)break
                                if(n==0)continue
                                output.write(buf,0,n)
                                received+=n
                                val size=received+(if(append)offset else 0L)
                                require(size<900L*1024*1024){"Model archive exceeds size limit"}
                                if(size-lastStatus>=16L*1024*1024) {
                                    status("Downloading FP32 model: ${size/(1024*1024)} MB")
                                    lastStatus=size
                                }
                            }
                        }
                    }
                    if(expected>0 && received!=expected)
                        throw java.io.IOException("Incomplete download response; expected $expected, received $received")
                    downloaded=true
                    Diagnostics.record("MODEL_DOWNLOAD_COMPLETED","",org.json.JSONObject()
                        .put("archiveBytes",archive.length()).put("attempt",attempt))
                    break
                } catch(error:java.io.IOException) {
                    Diagnostics.record("MODEL_DOWNLOAD_RETRY",error.javaClass.simpleName,
                        org.json.JSONObject().put("attempt",attempt).put("savedBytes",archive.length()))
                    if(attempt==5)throw error
                    status("Network interruption; resuming FP32 model download (${attempt}/5)…")
                    Thread.sleep((attempt*1000).toLong())
                } finally { connection?.disconnect() }
            }
            check(downloaded){"FP32 model download did not complete"}
            require(archive.length()>20L*1024*1024){"Model download incomplete"}
            status("Installing Kokoro FP32 model and voice assets…")
            var unpacked=0L
            TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)),true)).use {tar->
                while(true){val entry=tar.nextTarEntry?:break
                    if(entry.isSymbolicLink||entry.isLink)throw IllegalStateException("Unsafe link in model archive")
                    val path=entry.name.replace('\\','/')
                    val child=File(stage,path)
                    require(child.canonicalPath.startsWith(stage.canonicalPath+File.separator)){"Unsafe archive path"}
                    if(entry.isDirectory){child.mkdirs();continue}
                    if(!entry.isFile)continue
                    require(entry.size>=0&&entry.size<600L*1024*1024){"Unexpected archive file size"}
                    unpacked+=entry.size
                    require(unpacked<900L*1024*1024){"Model archive too large"}
                    child.parentFile?.mkdirs()
                    FileOutputStream(child).use{out->tar.copyTo(out)}
                }
            }
            val unpackedDir=stage.walkTopDown().firstOrNull {it.isDirectory && File(it,"voices.bin").isFile &&
                File(it,"model.onnx").length()>280_000_000L}
                ?:throw IllegalStateException("Downloaded archive does not contain expected Kokoro model/voice files")
            if(dir.exists())dir.deleteRecursively()
            require(unpackedDir.renameTo(dir)){"Unable to install extracted model"}
            require(present()){ "Model assets are incomplete after extraction" }
            Diagnostics.record("MODEL_INSTALL_COMPLETE",MODEL_VARIANT,org.json.JSONObject().put("modelBytes",modelFile().length()))
            status("Kokoro FP32 installed permanently in app storage")
        } catch(t:Throwable){stage.deleteRecursively();throw t}
        finally{
            // Keep downloaded bytes after network failure for future resume.
            // Delete only after successful model installation.
            if(present())archive.delete()
            stage.deleteRecursively()
        }
    }
}
