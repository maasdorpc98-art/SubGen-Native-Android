package com.subgen.nativeapp

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig

/** No WebView, no JS, no cloud inference. The JNI runtime and ONNX model must be installed. */
class KokoroNative(installer:ModelInstaller,threads:Int):AutoCloseable {
    private val engine:OfflineTts
    init {
        check(installer.present()){"Install the offline Kokoro model before rendering"}
        val dir=installer.directory
        val config=OfflineTtsConfig(
            model=OfflineTtsModelConfig(
                kokoro=OfflineTtsKokoroModelConfig(
                    model=installer.modelFile().absolutePath,
                    voices="${dir.absolutePath}/voices.bin",
                    tokens="${dir.absolutePath}/tokens.txt",
                    dataDir="${dir.absolutePath}/espeak-ng-data",
                    lexicon=FilePathOrEmpty("${dir.absolutePath}/lexicon-us-en.txt")
                ),
                numThreads=threads.coerceIn(1,4),provider="cpu",debug=false
            ), maxNumSentences=1
        )
        engine=OfflineTts(config=config)
    }
    private fun FilePathOrEmpty(path:String):String=java.io.File(path).takeIf{it.isFile}?.absolutePath?:""
    fun synthesize(text:String,speaker:Int,speed:Float,onStop:()->Boolean):Pair<FloatArray,Int> {
        val generated=engine.generateWithConfigAndCallback(
            text=text,
            config=GenerationConfig(sid=speaker,speed=speed.coerceIn(0.6f,1.4f),silenceScale=0.0f),
            callback={if(onStop())0 else 1}
        )
        require(generated.samples.isNotEmpty()){"Kokoro generated an empty speech block"}
        require(generated.samples.all{it.isFinite()}){"Kokoro generated invalid samples"}
        return generated.samples to generated.sampleRate
    }
    override fun close(){engine.release()}
    companion object {
        val voices=linkedMapOf("ORION" to 16,"ROWAN" to 14,"LUNA" to 2,"SERENA" to 3)
    }
}
