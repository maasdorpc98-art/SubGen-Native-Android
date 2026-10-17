import com.subgen.nativeapp.*

fun main(){
    val x=ScriptParser.parse("""[SECTION: INDUCTION]
Take a comfortable breath.

[PAUSE: 5]
[SLOW]
You can drift into a calm feeling.
[BREATHE]
[PARAGRAPH]
[stage DEEPENER]
[whisper]
Soft, slower, quieter.
""")
    check(x.count{it is ScriptNode.Speech}==3){"incorrect speech count: $x"}
    check(x.count{it is ScriptNode.Pause}==3){"incorrect pause count: $x"}
    check(x.filterIsInstance<ScriptNode.Pause>().sumOf{it.seconds}==13.0)
    check(x.filterIsInstance<ScriptNode.Speech>()[1].delivery=="SLOW")
    check(x.filterIsInstance<ScriptNode.Speech>()[2].delivery=="WHISPER")
    check(x.any{it is ScriptNode.Section && it.name=="DEEPENER"})
    val long="One calm word followed by another. ".repeat(100)
    val chunks=ScriptParser.parse(long,maxSpeechChars=120).filterIsInstance<ScriptNode.Speech>()
    check(chunks.size>3 && chunks.all{it.text.length<=120})
    println("PASS: sections, accurate pauses, delivery controls, speech chunking (${chunks.size} blocks)")
}
