package com.subgen.nativeapp

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin

fun fixture(file:File,type:Int,bits:Int,rate:Int=44100,channels:Int=2,frames:Int=2000) {
    val data=ByteArrayOutputStream()
    for(i in 0 until frames){
        for(ch in 0 until channels){
            val value=sin(2*PI*210*i/rate+ch*.13)
            val sample=when(bits){
                8->(((value*.4+1.0)*127.5).toInt().coerceIn(0,255))
                16->(value*9000).toInt()
                24->(value*3500000).toInt()
                32->if(type==3)java.lang.Float.floatToIntBits((value*.45).toFloat())
                    else (value*400000000).toInt()
                else->error("Unsupported fixture")
            }
            val bytes=bits/8
            for(j in 0 until bytes)data.write(sample ushr (8*j) and 255)
        }
    }
    val raw=data.toByteArray()
    val out=ByteArrayOutputStream()
    fun literal(s:String){out.write(s.toByteArray(Charsets.US_ASCII))}
    fun u16(v:Int){out.write(v and 255);out.write(v ushr 8 and 255)}
    fun u32(v:Long){for(k in 0..3)out.write(((v ushr (8*k)) and 255).toInt())}
    literal("RIFF");u32((raw.size+36).toLong());literal("WAVE")
    literal("fmt ");u32(16);u16(type);u16(channels)
    u32(rate.toLong());u32(rate.toLong()*channels*(bits/8))
    u16(channels*(bits/8));u16(bits)
    literal("data");u32(raw.size.toLong())
    out.write(raw)
    file.writeBytes(out.toByteArray())
}
fun main(){
    val root=Files.createTempDirectory("subgen-v35-audit-").toFile()
    try{
        val report=MorphicSystemSimulation.run(root){println("SIMULATING: "+it)}
        println(report.text())
        check(report.success){"The on-device simulation model found a failed stage"}
        check(report.checks.size>=11)
        val formats=listOf(1 to 8,1 to 16,1 to 24,1 to 32,3 to 32)
        for((type,bits) in formats){
            val from=File(root,"carrier-$type-$bits.wav")
            val to=File(root,"carrier-canonical-$type-$bits.wav")
            fixture(from,type,bits)
            val actual=MorphicCarrierWav.normalize(from,to)
            val details=MorphicCarrierMixer.readHeader(to)
            check(actual==2000L && details.frames==2000L)
            check(details.channels==2 && details.rate==44100)
            check(to.length()==44+2000L*4)
            println("PASS: WAV format "+type+"/"+bits+" converted to canonical PCM16")
        }
        val from=File(root,"carrier-cancel-source.wav")
        val dest=File(root,"carrier-cancel-out.wav")
        fixture(from,1,16)
        check(runCatching{MorphicCarrierWav.normalize(from,dest,stop={true})}.isFailure)
        check(!dest.exists()){"Canceled RIFF import left an output file"}
        val corrupted=File(root,"carrier-bad-riﬀ.wav")
        fixture(corrupted,1,16)
        RandomAccessFile(corrupted,"rw").use{r->r.setLength(r.length()-1)}
        check(runCatching{
            MorphicCarrierWav.normalize(corrupted,File(root,"bad-output.wav"))
        }.isFailure){"Truncated WAV was accepted"}
        val mono=File(root,"single-frame.wav")
        FileOutputStream(mono).use{out->
            WavIO.writeHeader(out,1L,8000,1)
            out.write(0);out.write(0)
        }
        MorphicCarrierMixer.CarrierReader(mono).use{reader->
            repeat(500){reader.advance();check(reader.left==0.0&&reader.right==0.0)}
        }
        check(runCatching{MorphicCarrierMixer.Gain(carrier=101)}.isFailure)
        check(runCatching{MorphicCarrierMixer.Gain(morphic=0)}.isFailure)
        val noCarrier=MorphicFieldV3.Blueprint("Keep focus","Repeat intention","I focus.")
        val m=listOf(1,3,5,10).first{it*60>=MorphicFieldV3.minimumSeconds(noCarrier)}
        val base=File(root,"no-carrier-original.wav")
        MorphicFieldV3.render(noCarrier,m,MorphicFieldV3.Profile.SIGNAL,base)
        val withTail=File(root,"invalid-trailing.wav")
        base.copyTo(withTail,overwrite=true)
        withTail.appendBytes(byteArrayOf(0x01))
        check(runCatching{MorphicFieldV3.verify(withTail)}.isFailure){
            "Verifier accepted undeclared trailing data"
        }
        println("PASS: 11-stage real PCM simulation, five carrier formats, cancellation, invalid RIFF, tiny carrier loop, strict length, volume range")
    }finally{root.deleteRecursively()}
}
