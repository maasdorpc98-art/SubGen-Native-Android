package android.content
import java.io.File
class Assets(private val root:File){fun open(name:String)=File(root,name).inputStream()}
class Context(val filesDir:File,val assets:Assets)
class SharedPreferences {
    val values=mutableMapOf<String,Any>()
    fun getString(k:String,d:String?):String?=values[k] as? String?:d
    fun getFloat(k:String,d:Float)=values[k] as? Float?:d
    fun getInt(k:String,d:Int)=values[k] as? Int?:d
    fun getLong(k:String,d:Long)=values[k] as? Long?:d
    fun getBoolean(k:String,d:Boolean)=values[k] as? Boolean?:d
    fun contains(k:String)=k in values
}
