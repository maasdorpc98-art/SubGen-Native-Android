import com.subgen.nativeapp.VoiceSpeed

fun main() {
    check(VoiceSpeed.DEFAULT==1.0f)
    check(VoiceSpeed.position(VoiceSpeed.DEFAULT)==35)
    check(VoiceSpeed.fromPosition(35)==1.00f)
    check(VoiceSpeed.fromPosition(0)==0.65f)
    check(VoiceSpeed.fromPosition(55)==1.20f)
    for(n in 0..55){
        val v=VoiceSpeed.fromPosition(n)
        check(VoiceSpeed.position(v)==n){"Slider round-trip failed at $n"}
    }
    check(VoiceSpeed.label(1.0f)=="VOICE SPEED · 1.00×")
    check(VoiceSpeed.label(0.84f)=="VOICE SPEED · 0.84×")
    check(VoiceSpeed.label(1.2f)=="VOICE SPEED · 1.20×")
    println("PASS: 1.00x default, visible voice label and 0.01x precise slider mapping")
}
