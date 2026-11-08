package com.subgen.nativeapp

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sin

/**
 * SubGen Field Lab v2: experimental information-bearing audio, NOT a validated
 * physical or metaphysical morphic-field encoding method.
 *
 * A complete blueprint is stored in the AUDIO SAMPLES (not a metadata tag):
 *   SMF3 | profile | four UTF-8 lengths | SHA-256(body) | body
 * where body = objective + instructions + affirmations + seven-node architecture.
 * Every original UTF-8 input is preserved exactly; the architecture is derived.
 *
 * Every bit occupies exactly 120 PCM frames at 24 kHz (5 ms). A zero is
 * 800 Hz, a one 1600 Hz; each bit is smoothly windowed. Ambient mode adds
 * deterministic low-level stereo texture. It is subtracted during verification
 * so the exact written fields can be recovered from the final saved WAV.
 */
object MorphicFieldV3 {
    const val RATE = 24000
    private const val BIT_FRAMES = 120
    private const val MAX_TEXT_BYTES = 16384
    private const val HEADER_BYTES = 54
    private const val SIGNAL_GAIN = .065
    private const val AMBIENT_SIGNAL_GAIN = .043
    private val MAGIC = byteArrayOf(83, 77, 70, 51) // "SMF3"

    enum class Profile(val label: String, val channels: Int) {
        SIGNAL("Signal", 1),
        AMBIENT("Ambient", 2)
    }

    data class Blueprint(
        val objective: String,
        val instructions: String,
        val affirmations: String
    ) {
        fun validated(): Blueprint {
            require(affirmations.isNotBlank()) { "Enter written affirmations first" }
            require(objective.toByteArray(Charsets.UTF_8).size <= 4096) { "Objective exceeds 4096 UTF-8 bytes" }
            require(instructions.toByteArray(Charsets.UTF_8).size <= 8192) { "Field instructions exceed 8192 UTF-8 bytes" }
            require(affirmations.toByteArray(Charsets.UTF_8).size <= MAX_TEXT_BYTES) { "Affirmations exceed 16384 UTF-8 bytes" }
            val total = objective.toByteArray(Charsets.UTF_8).size +
                instructions.toByteArray(Charsets.UTF_8).size +
                affirmations.toByteArray(Charsets.UTF_8).size
            require(total in 1..MAX_TEXT_BYTES) { "Combined written field exceeds 16384 UTF-8 bytes" }
            return this
        }
    }

    data class Result(
        val blueprint: Blueprint,
        val profile: Profile,
        val durationSeconds: Int,
        val completeCycles: Long,
        val textBytes: Int,
        val sha256: String,
        val file: File,
        val architecture:String
    )

    private fun u32(out: ByteArray, pos: Int, value: Int) {
        for (i in 0..3) out[pos + i] = (value ushr (24 - 8 * i)).toByte()
    }

    private fun u32(data: ByteArray, pos: Int): Int {
        var result = 0
        for(i in 0..3) result = (result shl 8) or (data[pos + i].toInt() and 255)
        return result
    }

    private fun digest(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
    private fun hex(data: ByteArray) = data.joinToString("") { "%02x".format(it) }

    private fun makePacket(blueprint: Blueprint, profile: Profile): Triple<ByteArray,ByteArray,String> {
        blueprint.validated()
        val a = blueprint.objective.toByteArray(Charsets.UTF_8)
        val b = blueprint.instructions.toByteArray(Charsets.UTF_8)
        val c = blueprint.affirmations.toByteArray(Charsets.UTF_8)
        val architecture=FieldArchitecture.create(
            MorphicFieldV2.Blueprint(blueprint.objective,blueprint.instructions,blueprint.affirmations)
        ).canonical()
        val d=architecture.toByteArray(Charsets.UTF_8)
        require(d.size<=8192){"Field architecture exceeds 8192 UTF-8 bytes"}
        val body = ByteArray(a.size + b.size + c.size + d.size)
        a.copyInto(body, 0)
        b.copyInto(body, a.size)
        c.copyInto(body, a.size + b.size)
        d.copyInto(body, a.size + b.size + c.size)
        val hash = digest(body)
        val packet = ByteArray(HEADER_BYTES + body.size)
        MAGIC.copyInto(packet, 0)
        packet[4] = 3 // schema
        packet[5] = if (profile == Profile.AMBIENT) 1 else 0
        u32(packet,6,a.size); u32(packet,10,b.size); u32(packet,14,c.size);u32(packet,18,d.size)
        hash.copyInto(packet,22)
        body.copyInto(packet,HEADER_BYTES)
        return Triple(packet,hash,architecture)
    }

    fun minimumSeconds(blueprint:Blueprint): Int {
        val (packet,_,_)=makePacket(blueprint,Profile.SIGNAL)
        return ceil(packet.size * 8.0 * BIT_FRAMES / RATE).toInt()
    }

    private fun bitWave(one:Boolean,bitFrame:Int):Double {
        val freq=if(one)1600.0 else 800.0
        val taper=min(1.0,min(bitFrame, BIT_FRAMES-1-bitFrame)/8.0)
        return taper*sin(2.0*PI*freq*bitFrame/RATE)
    }

    // A low-volume deterministic soundscape. No downloaded or copyrighted samples.
    // The stereo channels differ subtly; output stays below clipping.
    private fun pad(frame:Long,totalFrames:Long,ch:Int):Double {
        val t=frame.toDouble()/RATE
        val head=(t/1.7).coerceIn(0.0,1.0)
        val tail=((totalFrames-frame-1).toDouble()/(RATE*1.7)).coerceIn(0.0,1.0)
        val envelope=min(head,tail)
        val shift=if(ch==0)0.0 else 0.28
        val swell=.76+.16*sin(2*PI*.047*t+shift)
        val chord=.055*sin(2*PI*110*t+shift) +
             .048*sin(2*PI*164.81*t+.7+shift) +
             .037*sin(2*PI*220*t+1.4-shift) +
             .024*sin(2*PI*329.63*t+2.1+shift) +
             .012*sin(2*PI*440*t+.9-shift)
        // Slowly changing, deterministic airy texture, not random per render.
        val drift=.006*sin(2*PI*687.0*t+2.1*shift)*sin(2*PI*.31*t)
        return envelope*(swell*chord+drift)
    }

    /**
     * Architecture-conditioned modulation: actual field-dynamics targets and
     * source-content SHA determine seven distinguishable stereo audio patterns.
     * Real computational attractor dynamics; no asserted external causation.
     */
    private fun architectureTone(frame:Long,plan:FieldArchitecture.Plan,ch:Int):Double {
        val t=frame.toDouble()/RATE
        var mix=0.0
        val cycles=doubleArrayOf(18.0,45.0,120.0,29.0,63.0,96.0,150.0)
        val tones=doubleArrayOf(231.0,277.18,311.13,369.99,415.3,466.16,554.37)
        for(i in plan.nodes.indices){
            val target=plan.dynamics.target[i]
            val hashByte=plan.sourceSha256.substring(i*2,i*2+2).toInt(16)
            val signature=hashByte/255.0
            val phase=i*.41+signature*2.0*PI+(if(ch==0)0.0 else .21)
            val cycle=cycles[i]*(0.8+0.4*target)
            val frequency=tones[i]+26.0*(signature-.5)+12.0*(target-.5)
            val level=.0004+.0012*target
            val envelope=.6+.4*sin(2.0*PI*t/cycle+phase)
            mix+=level*envelope*sin(2.0*PI*frequency*t+phase)
        }
        return mix
    }

    fun render(blueprint:Blueprint,minutes:Int,profile:Profile,dest:File,
               update:(String)->Unit={},stop:()->Boolean={false}):Result {
        require(minutes in 1..60) { "Choose an output duration of 1 to 60 minutes" }
        val (packet,hash,architecture)=makePacket(blueprint,profile)
        val neededFrames=packet.size.toLong()*8L*BIT_FRAMES
        val totalFrames=minutes.toLong()*60L*RATE
        require(neededFrames<=totalFrames) {
            "The complete written field needs at least ${minimumSeconds(blueprint)} seconds; select a longer duration"
        }
        val tmp=File(dest.parentFile,dest.name+".partial")
        val channels=profile.channels
        val architecturePlan=FieldArchitecture.create(MorphicFieldV2.Blueprint(
            blueprint.objective,blueprint.instructions,blueprint.affirmations))
        val pcm=ByteArray(65536)
        val gain=if(profile==Profile.AMBIENT)AMBIENT_SIGNAL_GAIN else SIGNAL_GAIN
        var success=false
        try {
            FileOutputStream(tmp).buffered(65536).use { out ->
                WavIO.writeHeader(out,totalFrames,RATE,channels)
                var pos=0
                var written=0L
                var lastPercent=-1
                var packetByteCounter=0L
                while(written+neededFrames<=totalFrames){
                    if(stop())throw InterruptedException("Field render cancelled")
                    for(raw in packet){
                        if(packetByteCounter++ % 32L == 0L && stop())throw InterruptedException("Field render cancelled")
                        for(bit in 7 downTo 0){
                            val one=((raw.toInt() ushr bit) and 1)!=0
                            val count=min(BIT_FRAMES.toLong(),totalFrames-written).toInt()
                            for(i in 0 until count){
                                val frame=written+i
                                val core=gain*bitWave(one,i)
                                for(ch in 0 until channels){
                                    val sound=core+(if(profile==Profile.AMBIENT)
                                        pad(frame,totalFrames,ch)+architectureTone(frame,architecturePlan,ch)
                                    else 0.0)
                                    val sample=WavIO.floatToS16(sound.toFloat())
                                    if(pos+2>pcm.size){out.write(pcm,0,pos);pos=0}
                                    pcm[pos++]=(sample and 255).toByte()
                                    pcm[pos++]=((sample ushr 8) and 255).toByte()
                                }
                            }
                            written+=count
                            if(written>=totalFrames)break
                        }
                        val progress=(written*100/totalFrames).toInt()
                        if(progress!=lastPercent){lastPercent=progress;update("Encoding field blueprint: $progress%")}
                        if(written>=totalFrames)break
                    }
                }
                // The remainder has intentional ambience or silence only;
                // never leave a partial encoded packet at the end of the WAV.
                while(written<totalFrames){
                    if(stop())throw InterruptedException("Field render cancelled")
                    val take=min(4096L,totalFrames-written).toInt()
                    for(i in 0 until take){
                        val frame=written+i
                        for(ch in 0 until channels){
                            val sound=if(profile==Profile.AMBIENT)
                                pad(frame,totalFrames,ch)+architectureTone(frame,architecturePlan,ch)
                                else 0.0
                            val sample=WavIO.floatToS16(sound.toFloat())
                            if(pos+2>pcm.size){out.write(pcm,0,pos);pos=0}
                            pcm[pos++]=(sample and 255).toByte()
                            pcm[pos++]=((sample ushr 8) and 255).toByte()
                        }
                    }
                    written+=take
                    val percent=(written*100/totalFrames).toInt()
                    if(percent!=lastPercent){
                        lastPercent=percent
                        update("Completing verified field audio: $percent%")
                    }
                }
                if(pos>0)out.write(pcm,0,pos)
            }
            if(stop())throw InterruptedException("Field render cancelled")
            if(dest.exists())require(dest.delete()){"Cannot replace previous output"}
            require(tmp.renameTo(dest)){"Cannot publish verified field audio"}
            success=true
        } finally {if(!success)tmp.delete()}
        return Result(blueprint,profile,minutes*60,totalFrames/neededFrames,
                      packet.size-HEADER_BYTES,hex(hash),dest,architecture)
    }

    /**
     * Recover the embedded blueprint from the actual PCM samples. For the
     * optional carrier overlay we subtract exactly the user's selected carrier
     * stream and applied volume gains before demodulating the packet. This
     * catches problems caused by mixing and requires the retained carrier WAV
     * to independently re-verify a mixed recording.
     */
    fun verify(
        file:File,
        overlay:MorphicCarrierMixer.Gain?=null,
        carrier:File?=null
    ):Result {
        val carrierReader=if(overlay!=null && carrier!=null)
            MorphicCarrierMixer.CarrierReader(carrier) else null
        try{
        FileInputStream(file).buffered(65536).use { input ->
            val head=ByteArray(44)
            var filled=0
            while(filled<head.size){
                val n=input.read(head,filled,head.size-filled)
                require(n>0){"Truncated WAV header"};filled+=n
            }
            fun le16(at:Int)=(head[at].toInt() and 255) or ((head[at+1].toInt() and 255) shl 8)
            fun le32(at:Int):Long {
                var value=0L
                for(i in 0..3)value=value or ((head[at+i].toLong() and 255L) shl (8*i))
                return value
            }
            require(String(head,0,4)=="RIFF" && String(head,8,4)=="WAVE" &&
                String(head,36,4)=="data" && le16(20)==1 && le16(34)==16){"Expected uncompressed 16-bit PCM WAV"}
            val channels=le16(22)
            require(channels==1 || channels==2){"Unsupported channel count"}
            require(le32(24)==RATE.toLong()){"Expected 24 kHz field WAV"}
            val frameBytes=channels*2
            val dataBytes=le32(40)
            require(dataBytes>0 && dataBytes%frameBytes==0L &&
                dataBytes+44<=file.length()){"Truncated WAV audio"}
            val totalFrames=dataBytes/frameBytes
            var frame=0L
            // Ambient is determined by channel count. Reconstruct the known
            // synthetic bed and subtract it before FSK detection.
            fun decodeByte():Int {
                var value=0
                for(bit in 7 downTo 0){
                    var zero=0.0; var one=0.0
                    for(i in 0 until BIT_FRAMES){
                        carrierReader?.advance()
                        var mixed=0.0
                        for(ch in 0 until channels){
                            val lo=input.read();val hi=input.read()
                            require(lo>=0&&hi>=0){"Truncated audio packet"}
                            val sample=((hi shl 8) or lo).toShort().toInt().toDouble()/32767.0
                            val carrierValue=when(ch){
                                0->carrierReader?.left?:0.0
                                else->carrierReader?.right?:0.0
                            }
                            val carrierMix=carrierValue*(overlay?.carrierScale?:0.0)
                            val morphicGain=overlay?.morphicScale?:1.0
                            mixed+=sample-carrierMix-
                                (if(channels==2)pad(frame,totalFrames,ch)*morphicGain else 0.0)
                        }
                        frame++
                        val core=mixed/channels
                        zero+=core*sin(2.0*PI*800.0*i/RATE)
                        one +=core*sin(2.0*PI*1600.0*i/RATE)
                    }
                    if(abs(one)>abs(zero))value=value or (1 shl bit)
                }
                return value
            }
            require(totalFrames>=HEADER_BYTES.toLong()*8*BIT_FRAMES){"Audio is too short for packet"}
            val header=ByteArray(HEADER_BYTES){decodeByte().toByte()}
            require(header.copyOfRange(0,4).contentEquals(MAGIC) && header[4]==3.toByte()){"Field blueprint signature not found in audio"}
            val profile=when(header[5].toInt() and 255){
                0->Profile.SIGNAL;1->Profile.AMBIENT
                else->error("Unknown field audio profile")
            }
            require(profile.channels==channels){"Audio profile does not match channel count"}
            val len0=u32(header,6);val len1=u32(header,10)
            val len2=u32(header,14);val len3=u32(header,18)
            require(len0 in 0..4096 && len1 in 0..8192 &&
                len2 in 1..MAX_TEXT_BYTES && len3 in 1..8192){
                "Invalid field architecture lengths"
            }
            val size=len0.toLong()+len1+len2+len3
            require(size in 1..(MAX_TEXT_BYTES+8192L)){"Invalid total field packet size"}
            require((HEADER_BYTES+size)*8*BIT_FRAMES<=totalFrames){"Incomplete blueprint packet"}
            val body=ByteArray(size.toInt()){decodeByte().toByte()}
            val sha=digest(body)
            require(sha.contentEquals(header.copyOfRange(22,54))){"Field blueprint SHA-256 verification failed"}
            val objective=body.copyOfRange(0,len0).toString(Charsets.UTF_8)
            val instructions=body.copyOfRange(len0,len0+len1).toString(Charsets.UTF_8)
            val affirmations=body.copyOfRange(len0+len1,len0+len1+len2).toString(Charsets.UTF_8)
            val architecture=body.copyOfRange(len0+len1+len2,body.size).toString(Charsets.UTF_8)
            val blueprint=Blueprint(objective,instructions,affirmations).validated()
            require(FieldArchitecture.create(MorphicFieldV2.Blueprint(
                blueprint.objective,blueprint.instructions,blueprint.affirmations
            )).canonical()==architecture){"Encoded architecture does not match the source"}
            val totalPacketFrames=(HEADER_BYTES+size)*8*BIT_FRAMES
            val fullCycles=totalFrames/totalPacketFrames
            // Every complete occurrence must contain the exact same packet.
            // This prevents later-cycle corruption being missed when only the
            // first source packet was checked.
            val firstPacket=header+body
            for(cycle in 1L until fullCycles){
                val repeated=ByteArray(firstPacket.size){decodeByte().toByte()}
                require(repeated.contentEquals(firstPacket)){
                    "Field audio cycle "+(cycle+1)+" differs from the verified source"
                }
            }
            return Result(blueprint,profile,(totalFrames/RATE).toInt(),
                          totalFrames/totalPacketFrames,size.toInt(),hex(sha),file,architecture)
        }
        }finally{carrierReader?.close()}
    }
}
