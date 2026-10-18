package org.tukaani.xz
import java.io.*
class LZMA2Options(val preset:Int)
class XZOutputStream(private val output:OutputStream,private val option:LZMA2Options):OutputStream(){
    private val data=ByteArrayOutputStream()
    override fun write(b:Int)=data.write(b)
    override fun write(b:ByteArray,off:Int,len:Int)=data.write(b,off,len)
    override fun close(){
        val p=ProcessBuilder("xz","-${option.preset}","-c").start()
        p.outputStream.use{it.write(data.toByteArray())}
        val bytes=p.inputStream.readBytes();val err=p.errorStream.readBytes();check(p.waitFor()==0){"XZ compression failed "+String(err)}
        output.write(bytes);output.flush()
    }
}
class XZInputStream(input:InputStream):InputStream(){
    private val source:ByteArrayInputStream
    init{val p=ProcessBuilder("xz","-d","-c").start();p.outputStream.use{it.write(input.readBytes())};val result=p.inputStream.readBytes();val err=p.errorStream.readBytes();check(p.waitFor()==0){"XZ decompression failed "+String(err)};source=ByteArrayInputStream(result)}
    override fun read()=source.read()
    override fun read(b:ByteArray,off:Int,len:Int)=source.read(b,off,len)
}