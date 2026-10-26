package com.subgen.nativeapp

import java.io.File
import kotlin.math.*

/** Prepare each source once, reuse it across loop repetitions, and quantize once at export. */
object HighQualityMixer {
    private const val TAU=2*Math.PI
    private class Oscillator(hz:Double,rate:Int){
        private val angle=TAU*hz/rate;private val sn=sin(angle);private val cs=cos(angle)
        private var a=0.0;private var b=1.0;private var frame=0L
        fun next():Double {
            if(frame%4096L==0L){a=sin(angle*frame);b=cos(angle*frame)}
            val value=a;val n=a*cs+b*sn;b=b*cs-a*sn;a=n;frame++;return value
        }
    }
    private fun prepared(file:File,rate:Int,speed:Double,maxFrames:Long,cache:File,update:(String)->Unit,stop:()->Boolean):File {
        cache.mkdirs()
        val key=HighQualityPcm.key("${HighQualityPcm.VERSION}|mix-source|${HighQualityPcm.sha(file)}|$rate|$speed")
        val target=File(cache,"$key.wav")
        val info=HighQualityPcm.inspect(file)
        val needed=min(maxFrames,max(1L,round(info.frames.toDouble()*rate/info.rate/speed).toLong()))
        val checksum=File(cache,"$key.sha256")
        if(runCatching{val saved=HighQualityPcm.inspect(target);saved.rate==rate&&saved.frames>=needed&&checksum.readText()==HighQualityPcm.sha(target)}.getOrDefault(false)){
            update("HQ · reusing converted source ${file.name}");return target
        }
        update("HQ · preparing ${file.name} once for this rate and speed…")
        HighQualityPcm.resample(file,target,rate,info.channels,speed,maxFrames,stop)
        checksum.writeText(HighQualityPcm.sha(target))
        return target
    }
    fun mix(s:NativeSubliminal.Settings,dest:File,cache:File,update:(String)->Unit,stop:()->Boolean):HighQualityPcm.Result {
        require(s.rate in setOf(32000,44100,48000))
        require(s.durationSeconds in 1..5400)
        val rate=s.rate;val frames=s.durationSeconds.toLong()*rate
        val files=listOf(s.carrierA,s.carrierB,s.affirmationA,s.affirmationB,s.affirmationMy,s.affirmationYou)
        require(files.any{it!=null}){"Select at least one audio source"}
        val preparedFiles=mutableMapOf<String,File>()
        val converted=files.mapIndexed {i,file->file?.let{val speed=when(i){2->s.primarySpeed;3->s.secondarySpeed;4->s.mySpeed;5->s.youSpeed;else->1.0};preparedFiles.getOrPut("${it.canonicalPath}|$speed"){prepared(it,rate,speed,frames,cache,update,stop)}}}
        val opened=linkedMapOf<String,HighQualityPcm.Reader>()
        val sources=try{converted.map{f->f?.let{opened.getOrPut(it.canonicalPath){HighQualityPcm.Reader(it)}}}}
            catch(t:Throwable){opened.values.forEach{it.close()};throw t}
        val floatMix=File(dest.parentFile,dest.name+".float.wav")
        fun gain(db:Double)=10.0.pow(db.coerceIn(-60.0,0.0)/20.0)
        val g0=gain(s.carrierDb+s.carrierBalanceDb);val g1=gain(s.carrier8dDb+s.carrierBalanceDb)
        val g2=gain(s.primaryDb+s.layerDb+s.spokenBalanceDb+s.affirmationBalanceDb)
        val g3=gain(s.secondaryDb+s.layerDb+s.frequencyBalanceDb+s.affirmationBalanceDb)
        val g4=gain(s.myDb+s.layerDb+s.spokenBalanceDb+s.affirmationBalanceDb)
        val g5=gain(s.youDb+s.layerDb+s.spokenBalanceDb+s.affirmationBalanceDb)
        val final=gain(s.finalDb);val fade=s.fadeSeconds.coerceIn(0.0,30.0);val width=s.stereoWidth.coerceIn(0.0,2.0)
        val pan=Oscillator(.004+s.eightDPercent.coerceIn(0.0,100.0)*.0008,rate)
        val binL=Oscillator(s.centerHz-s.beatHz/2,rate);val binR=Oscillator(s.centerHz+s.beatHz/2,rate)
        val tone=Oscillator(s.rhythmicTone,rate);val pulse=Oscillator(s.rhythmicPulse,rate)
        val binGain=gain(s.binauralDb)*.2;val rhythmGain=gain(s.rhythmicDb)*.2;val depth=s.rhythmicDepth.coerceIn(0.0,1.0)
        val lp=if(s.lowPassHz>0)1-exp(-TAU*s.lowPassHz/rate)else 1.0
        val hp=if(s.highPassHz>0)exp(-TAU*s.highPassHz/rate)else 0.0
        val low=DoubleArray(2);val prev=DoubleArray(2);val high=DoubleArray(2)
        val clarityA=if(s.clarity&&s.primarySpeed>1)1-exp(-TAU*.42/s.primarySpeed)else 1.0
        val clarityB=if(s.clarity&&s.secondarySpeed>1)1-exp(-TAU*.42/s.secondarySpeed)else 1.0
        val clarityMy=if(s.clarity&&s.mySpeed>1)1-exp(-TAU*.42/s.mySpeed)else 1.0
        val clarityYou=if(s.clarity&&s.youSpeed>1)1-exp(-TAU*.42/s.youSpeed)else 1.0
        val clearA=DoubleArray(2);val clearB=DoubleArray(2);val clearMy=DoubleArray(2);val clearYou=DoubleArray(2)
        fun at(source:HighQualityPcm.Reader?,f:Long,ch:Int)=source?.let{it.at(f%it.info.frames,ch)}?:0.0
        try{
            HighQualityPcm.requireSpace(dest.absoluteFile.parentFile,frames*14L)
            HighQualityPcm.Writer(floatMix,frames,rate,2).use { w->
                for(i in 0 until frames){
                    if(i%8192L==0L&&stop())throw InterruptedException("HQ mixer cancelled")
                    if(i%(rate*4L)==0L)update("HQ mixing ${i*100/frames}% · ${i/rate}s / ${s.durationSeconds}s")
                    var left=at(sources[0],i,0);var right=at(sources[0],i,1)
                    if(width!=1.0){val mid=(left+right)*.5;val side=(left-right)*.5*width;left=mid+side;right=mid-side}
                    val signal=(at(sources[1],i,0)+at(sources[1],i,1))*.5;val p=pan.next()
                    val eightL=signal*sqrt((1-p).coerceAtLeast(0.0)*.5);val eightR=signal*sqrt((1+p).coerceAtLeast(0.0)*.5)
                    if(lp<1){low[0]+=lp*(left-low[0]);low[1]+=lp*(right-low[1]);left=low[0];right=low[1]}
                    if(hp>0){val a=hp*(high[0]+left-prev[0]);val b=hp*(high[1]+right-prev[1]);prev[0]=left;prev[1]=right;high[0]=a;high[1]=b;left=a;right=b}
                    val fadeGain=if(fade>0)(i.toDouble()/rate/fade).coerceIn(0.0,1.0)else 1.0
                    val bl=if(s.binauralOn)binL.next()*binGain else 0.0
                    val br=if(s.binauralOn)binR.next()*binGain else 0.0
                    val rhythmic=if(s.rhythmicOn){val n=pulse.next();val shape=if(s.rhythmicSine).5+.5*n else if(n>=0)1.0 else 0.0
                        tone.next()*(1-depth+depth*shape)*rhythmGain}else 0.0
                    for(ch in 0..1){
                        var a=at(sources[2],i,ch);var b=at(sources[3],i,ch)
                        if(clarityA<1){clearA[ch]+=clarityA*(a-clearA[ch]);a=clearA[ch]}
                        if(clarityB<1){clearB[ch]+=clarityB*(b-clearB[ch]);b=clearB[ch]}
                        var my=at(sources[4],i,ch);var you=at(sources[5],i,ch)
                        if(clarityMy<1){clearMy[ch]+=clarityMy*(my-clearMy[ch]);my=clearMy[ch]}
                        if(clarityYou<1){clearYou[ch]+=clarityYou*(you-clearYou[ch]);you=clearYou[ch]}
                        val carrier=if(ch==0)left else right;val eight=if(ch==0)eightL else eightR;val binaural=if(ch==0)bl else br
                        w.put(((carrier*g0+eight*g1)*fadeGain+a*g2+b*g3+my*g4+you*g5+binaural+rhythmic)*final)
                    }
                };w.finish()
            }
            update("HQ · final peak protection and 24-bit export…")
            val result=HighQualityPcm.export24(floatMix,dest,rate,2,ceilingDb=20*log10(s.peakCeiling.coerceIn(.01,1.0)),stop=stop)
            update("HQ complete · 24-bit WAV · peak adjustment ${"%.2f".format(result.appliedGainDb)} dB")
            return result
        }finally{opened.values.forEach{it.close()};floatMix.delete()}
    }
}
