package com.subgen.nativeapp

import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest

/**
 * Real offline diagnostic. Exercises the same production authoring,
 * architectural computation, PCM generation, carrier normalization/mixing,
 * complete-cycle verification, tamper rejection, low-gain operation and cancel
 * checks. Does not touch the user's saved audio or launch a fake MediaStore
 * export. Device audio picker and compressed decoders require separate tests.
 */
object MorphicSystemSimulation {
    data class Check(val name:String,val result:String,val detail:String)
    data class Report(val checks:List<Check>) {
        val pass get()=checks.count{it.result=="PASS"}
        val fail get()=checks.count{it.result=="FAIL"}
        val skip get()=checks.count{it.result=="SKIP"}
        val success get()=fail==0&&skip==0
        fun text():String=buildString{
            append("SUBGEN MORPHIC V3.5 — LIVE PIPELINE SIMULATION\n")
            append("Passed: ").append(pass).append(" · Failed: ").append(fail)
                .append(" · Skipped: ").append(skip).append("\n\n")
            checks.forEachIndexed{i,c->
                append(i+1).append(". ").append(c.result).append(" · ")
                    .append(c.name).append("\n").append(c.detail).append("\n\n")
            }
            append("This tests local computations and audio, not real-world morphic effects.\n")
            append("Android picker, compressed audio codecs, MediaStore and device speaker output require device-specific testing.")
        }
    }

    private fun shortCarrier(file:File,frames:Int=16000){
        FileOutputStream(file).buffered(65536).use{out->
            WavIO.writeHeader(out,frames.toLong(),16000,1)
            for(i in 0 until frames){
                val v=WavIO.floatToS16((0.16*kotlin.math.sin(
                    2.0*kotlin.math.PI*210.0*i/16000.0)).toFloat())
                out.write(v and 255)
                out.write(v ushr 8)
            }
        }
    }
    private fun withJunk(original:File,dest:File){
        val all=original.readBytes()
        require(all.size>44)
        val filler=ByteArray(16)
        "JUNK".toByteArray(Charsets.US_ASCII).copyInto(filler,0)
        filler[4]=8
        "SubGen35".toByteArray(Charsets.US_ASCII).copyInto(filler,8)
        val data=all.copyOfRange(0,36)+filler+all.copyOfRange(36,all.size)
        val n=(data.size-8)
        for(k in 0..3)data[4+k]=((n ushr (8*k)) and 255).toByte()
        dest.writeBytes(data)
    }
    private fun corrupt(file:File){
        RandomAccessFile(file,"rw").use{r->
            require(r.length()>44+80000)
            r.seek(44L+80000)
            r.write(ByteArray(6000))
        }
    }

    fun run(cacheDir:File,progress:(String)->Unit={}):Report{
        val folder=java.nio.file.Files.createTempDirectory(cacheDir.toPath(),
            "morphic-sim-").toFile()
        val results=mutableListOf<Check>()
        var broken=false
        fun check(name:String,body:()->String){
            if(broken){results.add(Check(name,"SKIP","Blocked by prior failure"));return}
            progress(name)
            try{
                val detail=body()
                results.add(Check(name,"PASS",detail))
            }catch(e:Throwable){
                results.add(Check(name,"FAIL",e.message?:e.javaClass.simpleName))
                broken=true
            }
        }
        val text="I understand my exact intended result.\n"+
            "My intelligence creates pathways and overcomes every obstacle.\n"+
            "You continuously improve every chosen affirmation."
        var blueprint=MorphicFieldV3.Blueprint("","","")
        var minutes=0
        val original=File(folder,"original.wav")
        val carrierSimple=File(folder,"carrier-basic.wav")
        val carrierChunked=File(folder,"carrier-with-JUNK.wav")
        val normalized=File(folder,"carrier-normalized.wav")
        val mixed=File(folder,"carrier-mixed.wav")
        val low=File(folder,"reduced-level.wav")
        val tampered=File(folder,"tampered.wav")
        try{
            check("Offline affirmation interpretation and original text"){
                val analysis=AdaptiveFieldComposer.compose(text)
                require(analysis.observedLines==3&&analysis.coverage>=3)
                require(analysis.objective.isNotBlank()&&analysis.instructions.isNotBlank())
                blueprint=MorphicFieldV3.Blueprint(analysis.objective,analysis.instructions,text)
                    .validated()
                "3 exact statements · "+analysis.coverage+" function categories"
            }
            check("Seven-node architecture and stable attractor"){
                val plan=FieldArchitecture.create(MorphicFieldV2.Blueprint(
                    blueprint.objective,blueprint.instructions,blueprint.affirmations))
                require(plan.nodes.size==7&&plan.dynamics.converged)
                minutes=listOf(1,3,5,10,20,30,60).firstOrNull{
                    it*60>=MorphicFieldV3.minimumSeconds(blueprint)
                }?:error("Field cannot fit in 60 minutes")
                "All seven components present · converged · "+minutes+" minute output"
            }
            check("Render actual PCM16 stereo Morphic WAV"){
                val result=MorphicFieldV3.render(blueprint,minutes,
                    MorphicFieldV3.Profile.AMBIENT,original)
                require(result.completeCycles>=1)
                require(original.isFile&&original.length()>44)
                "Generated "+result.durationSeconds+"s · "+result.completeCycles+" complete data cycles"
            }
            check("Decode source, check exact UTF-8 and every complete cycle"){
                val verified=MorphicFieldV3.verify(original)
                require(verified.blueprint==blueprint)
                require(verified.completeCycles>=1)
                "Exact source text and computed field architecture recovered"
            }
            check("Import WAV with additional RIFF metadata"){
                shortCarrier(carrierSimple)
                withJunk(carrierSimple,carrierChunked)
                val count=MorphicCarrierWav.normalize(carrierChunked,normalized)
                require(count==16000L)
                val head=MorphicCarrierMixer.readHeader(normalized)
                require(head.rate==16000&&head.channels==1)
                "Extended WAV normalized to canonical PCM16 · 16 kHz mono"
            }
            check("Loop and resample carrier, mix three independent volume levels"){
                val gain=MorphicCarrierMixer.Gain(100,65,80)
                val stats=MorphicCarrierMixer.mix(original,normalized,mixed,gain)
                require(stats.carrierUsed&&stats.clippedFrames==0L)
                require(mixed.length()==original.length())
                "Looping 16k→24k carrier · Morphic 100%, Carrier 65%, Master 80%"
            }
            check("Decode and verify FINAL carrier-mixed WAV"){
                val originalCheck=MorphicFieldV3.verify(original)
                val mixedCheck=MorphicFieldV3.verify(
                    mixed,MorphicCarrierMixer.Gain(100,65,80),normalized)
                require(mixedCheck.blueprint==blueprint)
                require(mixedCheck.sha256==originalCheck.sha256)
                "Identical original information recovered after real carrier mixing"
            }
            check("Low-volume mix retains decodable data"){
                val quieter=MorphicCarrierMixer.Gain(25,0,60)
                MorphicCarrierMixer.mix(original,null,low,quieter)
                require(MorphicFieldV3.verify(low,quieter).blueprint==blueprint)
                "Morphic 25% · Carrier 0% · Master 60% still verified"
            }
            check("Reject corrupted mixed field recording"){
                mixed.copyTo(tampered,overwrite=true)
                corrupt(tampered)
                require(runCatching{MorphicFieldV3.verify(
                    tampered,MorphicCarrierMixer.Gain(100,65,80),normalized
                )}.isFailure){"Corrupted WAV unexpectedly passed"}
                "PCM tampering identified by source SHA/packet verification"
            }
            check("Cancel/invalid volume rejection and temporary-file cleanup"){
                val canceled=File(folder,"canceled.wav")
                require(runCatching{
                    MorphicCarrierMixer.mix(original,normalized,canceled,
                        MorphicCarrierMixer.Gain(),cancel={true})
                }.isFailure)
                require(!canceled.exists())
                require(runCatching{MorphicCarrierMixer.Gain(morphic=0)}.isFailure)
                require(runCatching{MorphicCarrierMixer.Gain(master=0)}.isFailure)
                "Cancellation rejected before final publish; no partial WAV left"
            }
        }finally{
            folder.deleteRecursively()
        }
        return Report(results)
    }
}
