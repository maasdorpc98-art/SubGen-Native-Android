package com.subgen.nativeapp

import android.content.SharedPreferences

/** A render reads one immutable selection while progress/results continue to use live prefs. */
class PreferenceSnapshot(source:SharedPreferences):SharedPreferences {
    private val values:Map<String,Any?> = source.getAll().mapValues{(_,v)->if(v is Set<*>)v.toSet()else v}.toMap()
    override fun getAll():MutableMap<String,*> = values.mapValues{(_,v)->if(v is Set<*>)v.toMutableSet()else v}.toMutableMap()
    override fun contains(key:String?)=values.containsKey(key)
    override fun getString(key:String?,defValue:String?):String?=values[key] as? String?:defValue
    @Suppress("UNCHECKED_CAST") override fun getStringSet(key:String?,defValues:MutableSet<String>?):MutableSet<String>?=
        (values[key] as? Set<String>)?.toMutableSet()?:defValues?.toMutableSet()
    override fun getInt(key:String?,defValue:Int)=values[key] as? Int?:defValue
    override fun getLong(key:String?,defValue:Long)=values[key] as? Long?:defValue
    override fun getFloat(key:String?,defValue:Float)=values[key] as? Float?:defValue
    override fun getBoolean(key:String?,defValue:Boolean)=values[key] as? Boolean?:defValue
    override fun edit():SharedPreferences.Editor=throw UnsupportedOperationException("Render settings are immutable")
    override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener?)=Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener?)=Unit
}
