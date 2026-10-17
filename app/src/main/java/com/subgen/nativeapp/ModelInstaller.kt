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
        const val MODEL="kokoro-int8-multi-lang-v1_0"
        const val DOWNLOAD_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/kokoro-int8-multi-lang-v1_0.tar.bz2"
    }
    val directory:File get()=File(context.filesDir,"models/$MODEL")
    fun present():Boolean {
        val dir=directory
        return dir.isDirectory && (File(dir,"model.int8.onnx").length()>50_000_000 || File(dir,"model.onnx").length()>50_000_000) &&
            File(dir,"voices.bin").length()>1_000_000 && File(dir,"tokens.txt").length()>100 && File(dir,"espeak-ng-data").isDirectory
    }
    fun modelFile():File=File(directory,"model.int8.onnx").takeIf{it.isFile}?:File(directory,"model.onnx")
    fun install(status:(String)->Unit) {
        if (present()) {status("Kokoro model already installed on device");return}
        val dir=directory;dir.parentFile!!.mkdirs()
        // Approximate reserve for compressed model, extracted files, and working files.
        require(StatFs(context.filesDir.absolutePath).availableBytes>500L*1024*1024){"Need at least 500 MB free for one-time model installation"}
        val archive=File(context.cacheDir,"$MODEL.download")
        val stage=File(dir.parentFile,"$MODEL-installing")
        stage.deleteRecursively();stage.mkdirs()
        try {
            status("Downloading Kokoro voice/model pack (once)…")
            val connection=(URL(DOWNLOAD_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout=20_000;readTimeout=45_000;instanceFollowRedirects=true
                setRequestProperty("Accept-Encoding","identity")
            }
            try {
                require(connection.responseCode in 200..299){"Model download returned HTTP ${connection.responseCode}"}
                connection.inputStream.use{input->FileOutputStream(archive).use{output->
                    val buf=ByteArray(65536);var total=0L;var last=0L
                    while(true){val n=input.read(buf);if(n<0)break;output.write(buf,0,n);total+=n
                        if(total-last>=8L*1024*1024){status("Downloading model: ${total/(1024*1024)} MB");last=total}
                    }
                }}
            } finally {connection.disconnect()}
            require(archive.length()>20L*1024*1024){"Model download incomplete"}
            status("Installing Kokoro model and voice assets…")
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
                (File(it,"model.int8.onnx").isFile||File(it,"model.onnx").isFile)}
                ?:throw IllegalStateException("Downloaded archive does not contain expected Kokoro model/voice files")
            if(dir.exists())dir.deleteRecursively()
            require(unpackedDir.renameTo(dir)){"Unable to install extracted model"}
            require(present()){ "Model assets are incomplete after extraction" }
            status("Kokoro installed permanently in app storage")
        } catch(t:Throwable){stage.deleteRecursively();throw t}
        finally{archive.delete();stage.deleteRecursively()}
    }
}
