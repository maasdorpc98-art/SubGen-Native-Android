package com.subgen.nativeapp

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.sin

fun makeCarrier(file:File,rate:Int,channels:Int,seconds:Int,frequency:Double){
    val frames=seconds.toLong()*rate
    FileOutputStream(file).buffered(65536).use{out->
        WavIO.writeHeader(out,frames,rate,channels)
        val pcm=ByteArray(8192)
        var pending=0
        for(frame in 0 until frames){
            val t=frame.toDouble()/rate
            val signal=.28*sin(2.0*PI*frequency*t)+.09*sin(2.0*PI*83.1*t)
            for(ch in 0 until channels){
                val sample=WavIO.floatToS16((signal+ch*.012).toFloat())
                if(pending+2>pcm.size){out.write(pcm,0,pending);pending=0}
                pcm[pending++]=(sample and 255).toByte()
                pcm[pending++]=(sample ushr 8).toByte()
            }
        }
        if(pending>0)out.write(pcm,0,pending)
    }
}
fun main(){
    val blueprint=MorphicFieldV3.Blueprint(
        "Concentrated intention",
        "Maintain each original word and its intended destination",
        "I understand my objective.\nMy intention is clear.\nYou act with precision."
    )
    val length=MorphicFieldV3.minimumSeconds(blueprint)
    val minutes=listOf(1,3,5,10,20,30,60).firstOrNull{it*60>=length}
        ?:error("Field too long")
    val source=File.createTempFile("morphic-original-", ".wav")
    val carrier=File.createTempFile("morphic-imported-", ".wav")
    val out=File.createTempFile("morphic-carrier-mixed-", ".wav")
    val noCarrier=File.createTempFile("morphic-mix-only-", ".wav")
    val invalid=File.createTempFile("morphic-mix-invalid-", ".wav")
    try{
        val original=MorphicFieldV3.render(blueprint,minutes,MorphicFieldV3.Profile.AMBIENT,source)
        check(MorphicFieldV3.verify(source).sha256==original.sha256)
        makeCarrier(carrier,44100,2,2,312.0)
        val header=MorphicCarrierMixer.readHeader(carrier)
        check(header.channels==2 && header.rate==44100)
        val gains=MorphicCarrierMixer.Gain(morphic=100,carrier=75,master=80)
        val stats=MorphicCarrierMixer.mix(source,carrier,out,gains)
        check(stats.carrierUsed && stats.clippedFrames==0L)
        check(out.length()==source.length())
        val checked=MorphicFieldV3.verify(out,gains,carrier)
        check(checked.blueprint==blueprint && checked.sha256==original.sha256)
        check(checked.completeCycles>=1)
        check(checked.architecture==original.architecture)
        // No carrier but custom volumes must preserve the payload, too.
        val noCarrierGains=MorphicCarrierMixer.Gain(morphic=35,carrier=0,master=60)
        MorphicCarrierMixer.mix(source,null,noCarrier,noCarrierGains)
        check(MorphicFieldV3.verify(noCarrier,noCarrierGains).sha256==original.sha256)
        // A wave sample mutation must not pass the mixed integrity check.
        out.copyTo(invalid,overwrite=true)
        RandomAccessFile(invalid,"rw").use{f->
            f.seek(44L+24000L*4)
            f.write(ByteArray(6000))
        }
        check(runCatching{MorphicFieldV3.verify(invalid,gains,carrier)}.isFailure){
            "Corrupted carrier-mixed field was accepted"
        }
        check(runCatching{MorphicCarrierMixer.Gain(morphic=0)}.isFailure)
        check(runCatching{MorphicCarrierMixer.Gain(carrier=101)}.isFailure)
        println("PASS: V3.4 Carrier over Morphic · 44.1k stereo looping/resampling · 100/75/80 volumes")
        println("PASS: Verified mixed WAV · original 3-field blueprint unchanged · mono/no-carrier volume verification")
        println("PASS: Mixed-audio corruption rejected · volume range guards · clipping="+stats.clippedFrames)
    }finally{
        listOf(source,carrier,out,noCarrier,invalid).forEach{it.delete()}
    }
}
