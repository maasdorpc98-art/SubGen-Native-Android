package com.subgen.nativeapp
object Diagnostics {
    fun stage(name:String,extra:org.json.JSONObject=org.json.JSONObject()){}
    fun expectedOutput(bytes:Long){}
    fun record(kind:String,desc:String="",extra:org.json.JSONObject=org.json.JSONObject()){}
}
