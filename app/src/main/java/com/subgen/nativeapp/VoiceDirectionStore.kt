package com.subgen.nativeapp

import android.content.SharedPreferences

object VoiceDirectionStore {
    fun read(p:SharedPreferences):VoiceDirection.Config {
        val name=p.getString("dir:profile","MASTER HYPNOTIST")?:"MASTER HYPNOTIST"
        val sections=VoiceDirection.sections.associateWith { section ->
            val speedKey="dir:$section:tempo";val gainKey="dir:$section:gain"
            if(p.contains(speedKey)||p.contains(gainKey))VoiceDirection.SectionRule(
                if(p.contains(speedKey))p.getFloat(speedKey,1f).toDouble() else VoiceDirection.preset(name,section).tempo,
                if(p.contains(gainKey))p.getFloat(gainKey,0f).toDouble() else VoiceDirection.preset(name,section).gainDb)
            else VoiceDirection.preset(name,section)
        }
        return VoiceDirection.Config(p.getBoolean("dir:enabled",false),name,
            p.getFloat("dir:gain",0f).toDouble(),sections)
    }
}