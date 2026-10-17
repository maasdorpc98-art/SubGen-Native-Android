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
        Diagnostics.beforeNative("KOKORO_ENGINE_CREATE",
            org.json.JSONObject().put("threads",threads.coerceIn(1,4))
                .put("modelBytes",installer.modelFile().length()))
        engine=OfflineTts(config=config)
        Diagnostics.record("KOKORO_ENGINE_READY")
    }
    private fun FilePathOrEmpty(path:String):String=java.io.File(path).takeIf{it.isFile}?.absolutePath?:""
    fun synthesize(text:String,speaker:Int,speed:Float,onStop:()->Boolean):Pair<FloatArray,Int> {
        // Bounded JNI input protects against accidental unbounded inference allocations.
        require(text.length in 1..180){"Speech block outside stable 180-character synthesis limit"}
        Diagnostics.beforeNative("KOKORO_GENERATE",org.json.JSONObject().put("characters",text.length)
            .put("speaker",speaker).put("speed",speed.toDouble()))
        val generated=engine.generateWithConfigAndCallback(
            text=text,
            config=GenerationConfig(sid=speaker,speed=speed.coerceIn(0.6f,1.4f),silenceScale=0.0f),
            callback={if(onStop())0 else 1}
        )
        require(generated.samples.isNotEmpty()){"Kokoro generated an empty speech block"}
        require(generated.samples.all{it.isFinite()}){"Kokoro generated invalid samples"}
        Diagnostics.record("KOKORO_GENERATE_COMPLETED","",
            org.json.JSONObject().put("samples",generated.samples.size)
                .put("sampleRate",generated.sampleRate))
        return generated.samples to generated.sampleRate
    }
    override fun close(){engine.release()}
    companion object {
        val voices=linkedMapOf("ORION" to 16,"ROWAN" to 14,"LUNA" to 2,"SERENA" to 3)
    }
}
