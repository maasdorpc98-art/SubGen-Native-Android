import com.subgen.nativeapp.*
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.FileTime
import kotlin.math.*

fun main(){
    val dir=Files.createTempDirectory("subgen-hq-tests").toFile();var passed=0
    fun test(name:String,fn:()->Unit){fn();passed++;println("PASS HQ $passed: $name")}
    fun tone(name:String,rate:Int=24000,seconds:Double=1.0,hz:Double=700.0,amplitude:Double=.7,channels:Int=1):File {
        val file=File(dir,name);val count=(seconds*rate).roundToLong()
        HighQualityPcm.Writer(file,count,rate,channels).use{w->for(i in 0 until count)repeat(channels){ch->w.put(sin(2*PI*hz*i/rate)*amplitude*(if(ch==0)1.0 else .5))};w.finish()}
        return file
    }
    try{
        test("Float voice checkpoint retains raw values; PCM24 retains detail below PCM16 precision"){
            val samples=floatArrayOf(.0000005f,-.0000005f,.1f,-.3f,.75f)
            val voice=File(dir,"voice.wav");HighQualityPcm.writeVoice(voice,samples,24000)
            HighQualityPcm.Reader(voice).use{r->samples.forEachIndexed{i,v->check(r.at(i.toLong(),0)==v.toDouble())}}
            val target=File(dir,"voice24.wav");HighQualityPcm.export24(voice,target,24000,1,ceilingDb=0.0)
            HighQualityPcm.Reader(target).use{r->check(r.info.bits==24);check(abs(r.at(0,0))>0.0)
                samples.forEachIndexed{i,v->check(abs(r.at(i.toLong(),0)-v)<2.5/8388608.0)}}
        }
        test("PCM24 input and float32 stereo preserve channels and frame count"){
            val stereo=tone("stereo.wav",48000,channels=2)
            val target=File(dir,"stereo24.wav");HighQualityPcm.export24(stereo,target,48000,2,ceilingDb=0.0)
            HighQualityPcm.Reader(target).use{r->check(r.info.frames==48000L);check(r.info.bits==24)
                check(abs(r.at(123,0)-r.at(123,1)*2)<1e-6)}
        }
        test("Downsampling rejects an out-of-band 20 kHz source instead of aliasing into speech"){
            val high=tone("20khz.wav",48000,hz=20000.0);val down=File(dir,"down.wav")
            HighQualityPcm.resample(high,down,24000,1)
            HighQualityPcm.Reader(down).use{r->var squares=0.0
                for(i in 100L until r.info.frames-100)squares+=r.at(i,0).pow(2)
                check(sqrt(squares/(r.info.frames-200))<.01)}
        }
        test("HQ narration retains exact speech order, phase gain and 1.5 seconds of digital silence"){
            val a=tone("a.wav",seconds=.1);val b=tone("b.wav",seconds=.2,hz=1100.0)
            val nodes=listOf(ScriptNode.Speech("a","NORMAL",gainDb=-2.0),ScriptNode.Pause(1.5),ScriptNode.Speech("b","NORMAL"))
            val assembled=File(dir,"assembled.wav")
            HighQualityNarration.assemble(nodes,listOf(a,null,b),assembled)
            val out=File(dir,"assembled24.wav");HighQualityPcm.export24(assembled,out,24000,2)
            HighQualityPcm.Reader(out).use{r->check(r.info.frames==43200L)
                for(i in 2400L until 38400L)check(r.at(i,0)==0.0&&r.at(i,1)==0.0)
                check(abs(r.at(39001,0))>1e-4)}
        }
        test("Overloaded three-track mix uses uniform gain, preserves a sine wave and avoids chopped peaks"){
            val input=tone("mix-input.wav",48000)
            val out=File(dir,"mix24.wav")
            val s=NativeSubliminal.Settings(input,null,input,input,2,48000,peakCeiling=.5)
            val result=HighQualityMixer.mix(s,out,File(dir,"mix-cache"),{}){false}
            check(result.appliedGainDb< -10.0)
            HighQualityPcm.Reader(out).use{r->check(r.info.bits==24&&r.info.channels==2&&r.info.frames==96000L)
                var peak=0.0;var sum=0.0
                for(i in 0 until r.info.frames){val x=r.at(i,0);peak=max(peak,abs(x));sum+=x*x}
                check(peak<=.5000001);check(abs(sqrt(sum/r.info.frames)-.5/sqrt(2.0))<.001)}
        }
        test("Prepared sources are reused for a shorter export; damaged cache is regenerated"){
            val input=File(dir,"mix-input.wav");val cache=File(dir,"mix-cache")
            val saved=cache.listFiles()!!.filter{it.extension=="wav"}.associateWith{HighQualityPcm.sha(it)}
            saved.keys.forEach{Files.setLastModifiedTime(it.toPath(),FileTime.fromMillis(100000L))}
            val s=NativeSubliminal.Settings(input,null,input,input,1,48000,peakCeiling=.5)
            HighQualityMixer.mix(s,File(dir,"repeat24.wav"),cache,{}){false}
            saved.forEach{(file,hash)->check(file.lastModified()==100000L&&HighQualityPcm.sha(file)==hash)}
            val damaged=saved.keys.first();java.io.RandomAccessFile(damaged,"rw").use{r->r.seek(50);r.writeInt(12345)}
            HighQualityMixer.mix(s,File(dir,"repaired24.wav"),cache,{}){false}
            check(HighQualityPcm.sha(damaged)==saved[damaged])
        }
        test("NeuroMod A protected PCM32 recovery stays exact while mixer copy is PCM24"){
            val source=File(dir,"normalized.wav");HighQualityPcm.normalized16(File(dir,"mix-input.wav"),source)
            val master=File(dir,"a-master32.wav");val listening=File(dir,"old-listen16.wav")
            NeuroModulationA.generate(source,master,listening,NeuroModulationA.Config(1.0,40.0,.9))
            val before=HighQualityPcm.sha(master);val copy=File(dir,"a-listen24.wav")
            HighQualityPcm.export24(master,copy,96000,1,ceilingDb=-18.0,fadeSeconds=.035,normalize=true)
            check(before==HighQualityPcm.sha(master));check(HighQualityPcm.inspect(master).bits==32)
            HighQualityPcm.Reader(copy).use{r->check(r.info.bits==24&&r.info.frames==96000L)
                var p=0.0;for(i in 0 until r.info.frames)p=max(p,abs(r.at(i,0)))
                check(p<=10.0.pow(-18.0/20)+1e-7)}
        }
        test("Normalization of native PCM16 is sample-exact; HQ conversion does not alter algorithm payload precision"){
            val input=File(dir,"pcm16.wav");HighQualityPcm.Writer(input,1000,24000,1,16,false).use{w->for(i in 0..999)w.put((i-500)/32768.0);w.finish()}
            val out=File(dir,"pcm16norm.wav");HighQualityPcm.normalized16(input,out)
            check(input.readBytes().contentEquals(out.readBytes()))
        }
        test("NeuroMod B stereo PCM32 remains independently recoverable after a separate PCM24 listening export"){
            val normalized=File(dir,"normalized.wav");val master=File(dir,"b-master32.wav")
            val config=NeuroModulationA.Config(3.0,40.0,.9)
            val result=NeuroModulationB.generate(normalized,master,config)
            val original=HighQualityPcm.sha(master);val out=File(dir,"b-listen24.wav")
            HighQualityPcm.export24(master,out,96000,1,ceilingDb=-18.0,normalize=true)
            check(HighQualityPcm.inspect(master).channels==2&&HighQualityPcm.inspect(out).bits==24)
            check(HighQualityPcm.inspect(out).frames==288000L)
            check(HighQualityPcm.sha(master)==original)
            check(NeuroModulationB.recover(master,result.scale,config).size==48000)
        }
        test("Export cancellation and incomplete writes preserve an existing completed target"){
            val target=File(dir,"keep.wav");target.writeText("previous completed output")
            check(runCatching{HighQualityPcm.export24(File(dir,"mix-input.wav"),target,48000,2,stop={true})}.isFailure)
            check(target.readText()=="previous completed output")
            check(runCatching{HighQualityPcm.Writer(target,10,24000,1).use{w->w.put(.1);w.finish()}}.isFailure)
            check(target.readText()=="previous completed output");check(!File(dir,"keep.wav.partial").exists())
        }
        test("Settings snapshot retains the selected values when live settings are edited"){
            val fake=object:android.content.SharedPreferences{
                val map=mutableMapOf<String,Any?>("rate" to 48000,"gain" to -2f,"tags" to mutableSetOf("a"))
                override fun getAll()=map
                override fun contains(key:String?)=map.containsKey(key)
                override fun getInt(key:String?,defValue:Int)=map[key] as? Int?:defValue
                override fun getLong(key:String?,defValue:Long)=defValue
                override fun getFloat(key:String?,defValue:Float)=map[key] as? Float?:defValue
                override fun getBoolean(key:String?,defValue:Boolean)=defValue
                override fun getString(key:String?,defValue:String?)=defValue
                override fun getStringSet(key:String?,defValues:MutableSet<String>?)=defValues
                override fun edit():android.content.SharedPreferences.Editor=error("unused")
                override fun registerOnSharedPreferenceChangeListener(listener:android.content.SharedPreferences.OnSharedPreferenceChangeListener?){}
                override fun unregisterOnSharedPreferenceChangeListener(listener:android.content.SharedPreferences.OnSharedPreferenceChangeListener?){}
            }
            val snap=PreferenceSnapshot(fake);fake.map["rate"]=32000;fake.map["gain"]=0f
            @Suppress("UNCHECKED_CAST") (fake.map["tags"] as MutableSet<String>).add("b")
            check(snap.getInt("rate",0)==48000&&snap.getFloat("gain",0f)==-2f)
            check(snap.getStringSet("tags",null)==setOf("a"))
        }
        test("60-second HQ mixer benchmark produces the complete expected file"){
            val source=File(dir,"mix-input.wav");val start=System.nanoTime()
            HighQualityMixer.mix(NativeSubliminal.Settings(source,null,source,null,60,48000),File(dir,"benchmark.wav"),File(dir,"benchmark-cache"),{}){false}
            val elapsed=(System.nanoTime()-start)/1e9
            check(HighQualityPcm.inspect(File(dir,"benchmark.wav")).frames==2_880_000L)
            println("DESKTOP BENCHMARK: 60s stereo 48k PCM24 mixed/exported in ${"%.3f".format(elapsed)}s; not a Samsung measurement")
        }
        test("20-minute synthetic HQ narration exports complete stereo PCM24 with bounded memory"){
            val clip=tone("long-voice.wav",seconds=.2)
            val nodes=ArrayList<ScriptNode>();val clips=ArrayList<File?>()
            repeat(1000){nodes+=ScriptNode.Speech("fixture","NORMAL");clips+=clip;nodes+=ScriptNode.Pause(1.0);clips+=null}
            val raw=File(dir,"long-float.wav");val out=File(dir,"long24.wav");val start=System.nanoTime()
            check(HighQualityNarration.assemble(nodes,clips,raw)==1200.0)
            HighQualityPcm.export24(raw,out,24000,2)
            val info=HighQualityPcm.inspect(out)
            check(info.frames==28_800_000L&&info.bits==24&&info.channels==2)
            check(out.length()==172_800_044L)
            println("DESKTOP LONG QA: 20min synthetic HQ assembled/exported in ${"%.3f".format((System.nanoTime()-start)/1e9)}s with -Xmx256m; no neural inference or Samsung claim")
        }
        println("SUCCESS: $passed high-quality test groups passed")
    }finally{dir.deleteRecursively()}
}
