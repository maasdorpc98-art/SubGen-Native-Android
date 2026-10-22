package com.subgen.baseline

/** A HYPER personality is writing metadata, not Kokoro's neural emotion control.
 * Only speed (Kokoro inference) and gain (post-TTS PCM) are acoustic controls.
 */
object VoiceDirection {
    data class SectionRule(val tempo:Double=1.0,val gainDb:Double=0.0)
    data class Config(val enabled:Boolean=false,val profile:String="MASTER HYPNOTIST",
                      val globalGainDb:Double=0.0,
                      val sectionRules:Map<String,SectionRule> = emptyMap())
    data class Resolved(val speed:Float,val gainDb:Double,val mode:String,
                        val section:String,val actualTempoFactor:Double)
    val profiles=listOf("GENTLE GUIDE","CONFIDENT AUTHORITY","MASTER HYPNOTIST",
                        "COMMANDING / DRAMATIC","INTELLIGENT INSTRUCTOR")
    // Script names are intentionally canonical, not free-text personality guesses.
    val profileCodes=ScriptParser.PROFILE_CODES
    fun profileFromCode(value:String):String?=profileCodes[value.trim().uppercase()]
    val sections=listOf("ARRIVAL","INDUCTION","DEEPENER","FRACTIONATION",
                        "SUGGESTIONS","REINFORCEMENT","INTEGRATION","WAKE","SLEEP")
    fun normalizeSection(section:String):String{
        val s=section.trim().uppercase()
        return when {
            s.startsWith("DEEPENER")||s=="DEEP TRANCE"||s=="VOID"||s=="THE VOID"->"DEEPENER"
            s.startsWith("FRACTIONATION")->"FRACTIONATION"
            s.startsWith("SUGGESTION")||s=="TRAINING"->"SUGGESTIONS"
            s.startsWith("WAKE")||s=="AWAKENING"->"WAKE"
            s.startsWith("SLEEP")->"SLEEP"
            s in sections->s
            else->"" // Unknown chapter uses global settings, not a fabricated mapping.
        }
    }
    /** Acoustic *approximations*, deliberately modest and opt-in. */
    fun preset(profile:String,section:String):SectionRule{
        val phase=normalizeSection(section)
        return when(profile.uppercase()) {
            "GENTLE GUIDE"->when(phase){"ARRIVAL","INDUCTION"->SectionRule(.94,-2.0);"DEEPENER","SLEEP"->SectionRule(.88,-3.0);"WAKE"->SectionRule(1.0,-1.0);else->SectionRule(.96,-1.0)}
            "CONFIDENT AUTHORITY"->when(phase){"DEEPENER"->SectionRule(.91,-1.0);"WAKE"->SectionRule(1.02,0.0);else->SectionRule(1.0,0.0)}
            "MASTER HYPNOTIST"->when(phase){"ARRIVAL","INDUCTION"->SectionRule(.96,-1.0);"DEEPENER","SLEEP"->SectionRule(.88,-2.0);"WAKE"->SectionRule(1.04,0.0);else->SectionRule(1.0,0.0)}
            "COMMANDING / DRAMATIC"->when(phase){"DEEPENER"->SectionRule(.89,-1.0);"WAKE"->SectionRule(1.02,1.0);else->SectionRule(1.0,0.0)}
            "INTELLIGENT INSTRUCTOR"->when(phase){"DEEPENER"->SectionRule(.97,-1.0);"WAKE"->SectionRule(1.04,0.0);else->SectionRule(1.0,0.0)}
            else->SectionRule()
        }
    }
    /** Legacy modes remain exclusive: a mode multiplier is never stacked with
     * a phrase-level explicit tempo. No DSP time-stretch or altered pitch.
     */
    fun resolve(node:ScriptNode.Speech,globalSpeed:Float,config:Config=Config()):Resolved{
        require(globalSpeed.isFinite()&&globalSpeed in .65f..1.2f)
        // New script syntax opts into independent acoustic phases. Existing
        // scripts without [profile]/[phase] preserve v3.1.0 legacy mapping.
        val section=if(node.explicitPhaseMode) node.acousticPhase.orEmpty()
            else normalizeSection(node.legacyPhase.ifEmpty { node.section })
        val active=node.scriptProfile!=null||config.enabled
        val selected=node.scriptProfile?:config.profile
        // Script-selected acoustic presets ignore UI overrides for other profiles.
        val rule=when {
            node.scriptProfile!=null->preset(selected,section)
            config.enabled->config.sectionRules[section]?:preset(selected,section)
            else->SectionRule()
        }
        val legacy=when(node.delivery){"SLOW"->.82;"SOFT"->.94;"WHISPER"->.90;else->1.0}
        val factor=node.tempo ?: if(node.delivery!="NORMAL")legacy else if(active)rule.tempo else 1.0
        val gain=(if(config.enabled)config.globalGainDb else 0.0)+
            (node.gainDb ?: if(active)rule.gainDb else 0.0)
        require(factor.isFinite()&&factor in .5..1.5){"Effective tempo out of range"}
        require(gain.isFinite()&&gain in -30.0..6.0){"Effective gain exceeds safe profile range"}
        val rawSpeed=(globalSpeed.toDouble()*factor).toFloat()
        // Existing Kokoro clamped slow legacy delivery to 0.60; preserve it.
        // New opt-in settings must be in range and never silently clamp.
        if(node.tempo!=null||active)require(rawSpeed in .6f..1.4f){
            "Effective tempo exceeds native Kokoro 0.60–1.40. Adjust tempo or global speed."
        }
        return Resolved(rawSpeed.coerceIn(.6f,1.4f),gain,node.delivery,section,factor)
    }
}