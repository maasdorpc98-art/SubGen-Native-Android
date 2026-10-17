package com.subgen.nativeapp

import android.content.Context
import android.content.SharedPreferences
import java.io.File
import java.security.MessageDigest

object StateStore {
    private const val PREFS="subgen-native-v2"
    fun prefs(c:Context):SharedPreferences=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun status(c:Context)=prefs(c).getString("status","Idle")?:"Idle"
    fun status(c:Context,value:String){ prefs(c).edit().putString("status",value).apply() }
    fun active(c:Context):Boolean=prefs(c).getBoolean("renderActive",false)
    fun active(c:Context,active:Boolean){prefs(c).edit().putBoolean("renderActive",active).apply()}
    fun hash(text:String):String=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    fun blocksDir(c:Context):File=File(c.filesDir,"speech-cache").apply{mkdirs()}
    fun partialFile(c:Context):File=File(c.filesDir,"hypnosis-working.wav")
}
