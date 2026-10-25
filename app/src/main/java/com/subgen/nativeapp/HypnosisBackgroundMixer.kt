package com.subgen.nativeapp

import android.content.SharedPreferences
import java.io.File
import kotlin.math.*

/** Disk-backed optional stationary audio underlay; no generated tones or 8D motion. */
object HypnosisBackgroundMixer {
    data class Layer(val name:String,val file:File,val volumeDb:Double,val speed:Double){
        init {
            require(file.isFile) { "Missing $name audio" }
            require(volumeDb.isFinite() && volumeDb in -60.0..0.0)
            require(speed.isFinite() && speed in .5..2.0)
        }
    }
    fun selected(p:SharedPreferences):List<Layer> = listOf("affirm" to -18f,"noise" to -24f).mapNotNull { (name,defaultDb) ->
        val key="hyp:bg:$name"
        if (!p.getBoolean("$key:enabled",false)) null else {
            val path=p.getString("$key:path","").orEmpty()
            require(path.isNotBlank()) { "Select an audio file or turn OFF background $name" }
            Layer(name,File(path),p.getFloat("$key:volumeDb",defaultDb).toDouble(),
                p.getFloat("$key:speed",1f).toDouble())
        }
    }
    fun mix(narration:File,target:File,layers:List<Layer>,hq:Boolean,stop:()->Boolean={false}) {
        require(layers.isNotEmpty())
        require(narration.canonicalPath!=target.canonicalPath)
        HighQualityPcm.Reader(narration).use { voice ->
            require(voice.info.rate==24000 && voice.info.channels==2) { "Expected 24k stereo narration" }
            val prepared=layers.map { layer ->
                val reader=HighQualityPcm.Reader(layer.file)
                require(reader.info.rate==24000) { "Background loop must be resampled to 24 kHz" }
                Pair(layer,reader)
            }
            try {
                fun layerValue(frame:Long,ch:Int,reader:HighQualityPcm.Reader):Double {
                    val loopFrames=reader.info.frames
                    val position=frame%loopFrames
                    val primary=reader.at(position,ch.coerceAtMost(reader.info.channels-1))
                    val fade=min(120L,loopFrames/4)
                    if (fade==0L || position>=fade) return primary
                    val fraction=position.toDouble()/fade
                    val tail=reader.at(loopFrames-1,ch.coerceAtMost(reader.info.channels-1))
                    return tail*(1.0-fraction)+primary*fraction
                }
                fun mixed(frame:Long,ch:Int):Double {
                    var sum=voice.at(frame,ch)
                    for ((layer,reader) in prepared) {
                        sum+=layerValue(frame,ch,reader)*10.0.pow(layer.volumeDb/20.0)
                    }
                    return sum
                }
                val frames=voice.info.frames
                var attenuation=1.0
                if (!hq) {
                    var peak=0.0
                    for (i in 0 until frames) {
                        if (i%8192L==0L && stop()) throw InterruptedException("Background mix cancelled")
                        peak=max(peak,max(abs(mixed(i,0)),abs(mixed(i,1))))
                    }
                    if (peak>0.9999) attenuation=0.9999/peak
                }
                HighQualityPcm.Writer(target,frames,24000,2,if(hq)32 else 16,hq).use { writer ->
                    for (i in 0 until frames) {
                        if (i%8192L==0L && stop()) throw InterruptedException("Background mix cancelled")
                        writer.put(mixed(i,0)*attenuation)
                        writer.put(mixed(i,1)*attenuation)
                    }
                    writer.finish()
                }
            } finally { prepared.forEach { (_,reader) -> reader.close() } }
        }
    }
}
