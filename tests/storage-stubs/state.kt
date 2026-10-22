package com.subgen.nativeapp
import android.content.Context
import android.content.SharedPreferences
object StateStore {
    val testPrefs=SharedPreferences()
    fun prefs(c:Context)=testPrefs
}
