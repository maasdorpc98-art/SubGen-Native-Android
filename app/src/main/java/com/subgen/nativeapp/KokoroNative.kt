package com.subgen.nativeapp

import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsKokoroModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.GenerationConfig

/** No WebView, no JS, no cloud inference. The JNI runtime and ONNX model must be installed. */
class KokoroNative(installer:ModelInstaller,threads:Int):AutoCloseable {
    private val engine:OfflineTts
    private var generations=0
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
                    lexicon=FilePathOrEmpty("${dir.absolutePath}/lexicon-us-en.txt"),
                    lang="en-us"
                ),
                numThreads=threads.coerceIn(1,4),provider="cpu",debug=false
            ), maxNumSentences=1
        )
        Diagnostics.beforeNative("KOKORO_ENGINE_CREATE",
            org.json.JSONObject().put("threads",threads.coerceIn(1,4))
                .put("modelBytes",installer.modelFile().length()).put("precision",ModelInstaller.MODEL_VARIANT))
        engine=OfflineTts(config=config)
        Diagnostics.record("KOKORO_ENGINE_READY", "", org.json.JSONObject().put("numSpeakers",engine.numSpeakers()).put("sampleRate",engine.sampleRate()))
    }
    private fun FilePathOrEmpty(path:String):String=java.io.File(path).takeIf{it.isFile}?.absolutePath?:""
    fun synthesize(text:String,speaker:Int,speed:Float,onStop:()->Boolean):Pair<FloatArray,Int> {
        // The native callback overload can SIGABRT in sherpa-onnx on Android.
        // Non-callback inference preserves the complete result; cancellation is checked
        // at safe boundaries between short, disk-checkpointed speech blocks.
        require(!onStop()){"Speech generation cancelled before block"}
        require(text.length in 1..180){"Speech block outside stable 180-character synthesis limit"}
        require(speaker in 0 until engine.numSpeakers()){"Requested speaker is not present in installed Kokoro model"}
        Diagnostics.beforeNative("KOKORO_GENERATE",org.json.JSONObject().put("characters",text.length)
            .put("speaker",speaker).put("speed",speed.toDouble()).put("sequence",generations+1),
            durable=(generations++%16==0))
        // IMPORTANT: no callback crosses the C++ -> JVM JNI boundary.
        val generated=engine.generateWithConfig(
            text=text,
            config=GenerationConfig(sid=speaker,speed=speed.coerceIn(0.6f,1.4f),silenceScale=0.0f)
        )
        require(!onStop()){"Speech generation cancelled after block"}
        val audio=AudioQuality.examine(generated.samples)
        val details=org.json.JSONObject()
            .put("modelVariant",ModelInstaller.MODEL_VARIANT).put("samples",audio.count)
            .put("sampleRate",generated.sampleRate).put("nanCount",audio.nanCount)
            .put("infiniteCount",audio.infiniteCount).put("clippedCount",audio.clippedCount)
            .put("longestRailRun",audio.longestRailRun).put("peak",audio.peak)
            .put("rms",audio.rms)
        if(!audio.isAcceptable(generated.sampleRate)) {
            Diagnostics.record("KOKORO_AUDIO_REJECTED","Generated audio is corrupt or near-silent; refusing to save",details)
            throw IllegalStateException("Kokoro returned unusable audio; see KOKORO_AUDIO_REJECTED diagnostics")
        }
        Diagnostics.record("KOKORO_AUDIO_ACCEPTED","",details)
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
