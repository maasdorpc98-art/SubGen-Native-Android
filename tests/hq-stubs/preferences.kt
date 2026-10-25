package android.content
interface SharedPreferences {
    interface Editor
    interface OnSharedPreferenceChangeListener
    fun getAll():MutableMap<String,*>
    fun contains(key:String?):Boolean
    fun getString(key:String?,defValue:String?):String?
    fun getStringSet(key:String?,defValues:MutableSet<String>?):MutableSet<String>?
    fun getInt(key:String?,defValue:Int):Int
    fun getLong(key:String?,defValue:Long):Long
    fun getFloat(key:String?,defValue:Float):Float
    fun getBoolean(key:String?,defValue:Boolean):Boolean
    fun edit():Editor
    fun registerOnSharedPreferenceChangeListener(listener:OnSharedPreferenceChangeListener?)
    fun unregisterOnSharedPreferenceChangeListener(listener:OnSharedPreferenceChangeListener?)
}
