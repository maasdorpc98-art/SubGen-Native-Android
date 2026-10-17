package android.content
class ContentValues { fun put(key:String,value:Any?){} }
open class Context { val contentResolver=ContentResolver() }
class ContentResolver {
 fun insert(uri:android.net.Uri,cv:ContentValues):android.net.Uri?=uri
 fun openOutputStream(uri:android.net.Uri,mode:String):java.io.OutputStream?=null
 fun update(uri:android.net.Uri,cv:ContentValues,where:String?,args:Array<String>?):Int=0
 fun delete(uri:android.net.Uri,where:String?,args:Array<String>?):Int=0
}
