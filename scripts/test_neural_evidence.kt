import com.subgen.nativeapp.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

fun main(args:Array<String>){
    val dir=File("test-results/neural").apply{mkdirs()}
    val short="[profile MASTER_HYPNOTIST][phase INDUCTION]Take a comfortable breath. Relax [tempo 0.90][volume -2dB]Deeply.[pause 1.25][tempo reset][volume reset][SECTION: VOID ENTRY][phase DEEPENER]Relax Your System Deeply.[pause 2][SECTION: INTEGRATION][phase INTEGRATION]Notice your surroundings.[phase WAKE]Here and Now. Feel comfortably alert."
    val scripts=linkedMapOf("directed" to short,"neutral" to short,"long-session" to File("test-results/long-synthetic-script.txt").readText())
    val plan=JSONObject().put("backend","sherpa-onnx 1.13.8 + original kokoro-multi-lang-v1_0 FP32, desktop CPU")
        .put("neuralListeningAccepted",false).put("androidDeviceTested",false)
    val plans=JSONArray()
    for((name,source)in scripts){
        val raw=ScriptParser.parse(source,maxSpeechChars=160)
        val prepared=raw.map{node->if(node is ScriptNode.Speech){
            val n=if(name=="neutral")node.copy(delivery="NORMAL",tempo=null,gainDb=null,scriptProfile=null,acousticPhase=null,explicitPhaseMode=true)else node
            val r=VoiceDirection.resolve(n,1f);n.copy(gainDb=r.gainDb)
        }else node}
        val items=JSONArray()
        raw.forEachIndexed{i,node->when(node){
            is ScriptNode.Speech->{val n=if(name=="neutral")node.copy(delivery="NORMAL",tempo=null,gainDb=null,scriptProfile=null,acousticPhase=null,explicitPhaseMode=true)else node
                val r=VoiceDirection.resolve(n,1f);items.put(JSONObject().put("kind","speech").put("index",i).put("text",n.text).put("speed",r.speed.toDouble()).put("gainDb",r.gainDb))}
            is ScriptNode.Pause->items.put(JSONObject().put("kind","pause").put("index",i).put("seconds",node.seconds))
            else->Unit
        }}
        plans.put(JSONObject().put("name",name).put("sourceScript",source).put("nodes",items))
        if(args.firstOrNull()=="assemble"){
            val clips=raw.mapIndexed{i,n->if(n is ScriptNode.Speech)File(dir,"serena-$name-$i.wav")else null}
            val assembled=File(dir,"$name-assembled.wav")
            val seconds=WavIO.assemble(prepared,clips,assembled)
            val output=File(dir,"$name-final.wav");OutputFormat.convert(assembled,output,24000,2,volumeDb=0.0,ceilingDb=-1.0)
            val qa=HyperAudioQa.analyze(output)
            val expectedFrames=prepared.indices.sumOf{i->when(val n=prepared[i]){is ScriptNode.Speech->WavIO.loadMono(clips[i]!!).frames;is ScriptNode.Pause->kotlin.math.round(n.seconds*24000).toLong();else->0L}}
            check(qa.frames==expectedFrames&&qa.railSamples==0L)
            check(kotlin.math.abs(seconds-qa.seconds)<1.0/24000)
            val measured=HyperJson.audio(qa).put("speechBlocks",clips.count{it!=null}).put("scriptSha256",HyperAnalysis.hash(source))
                .put("programmedSilenceFrames24k",prepared.filterIsInstance<ScriptNode.Pause>().sumOf{kotlin.math.round(it.seconds*24000).toLong()})
                .put("preRenderEstimatedSeconds",ScriptTimeline.analyze(source).totalSeconds)
                .put("listeningAcceptance","NOT PERFORMED").put("androidDeviceTested",false)
            File(dir,"$name-measurements.json").writeText(measured.toString(2))
            HyperAudioQa.audition(output,File(dir,"$name-junction.wav"))
            println("PASS: real neural $name → original Kotlin PCM pipeline: ${qa.frames} frames, ${qa.seconds}s, zero rail samples")
        }
    }
    plan.put("plans",plans);File(dir,"neural-plan.json").writeText(plan.toString(2))
}
