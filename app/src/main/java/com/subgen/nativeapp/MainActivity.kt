package com.subgen.nativeapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import java.io.File

/** Actual Android Views; no WebView, HTML, JavaScript, or Web Audio. */
class MainActivity:Activity(){
    private lateinit var root:LinearLayout
    private lateinit var contents:FrameLayout
    private lateinit var statusText:TextView
    private val handler=Handler(Looper.getMainLooper())
    private val paletteBg=Color.rgb(15,19,26)
    private val panel=Color.rgb(25,31,43)
    private val accent=Color.rgb(170,144,255)
    private val textColor=Color.rgb(233,239,250)
    private val muted=Color.rgb(151,175,192)
    private val p by lazy{StateStore.prefs(this)}
    private val imports=linkedMapOf<Int,String>()
    private val names=linkedMapOf<String,TextView>()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        window.statusBarColor=paletteBg;window.navigationBarColor=paletteBg
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),42)
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(paletteBg);setPadding(14.dp,16.dp,14.dp,8.dp)}
        setContentView(root)
        label(root,"SUB GEN",24,true,accent)
        label(root,"NATIVE ANDROID · KOKORO OFFLINE · SAVED WORK",10,false,muted)
        val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;setPadding(0,8.dp,0,8.dp)}
        root.addView(tabs)
        contents=FrameLayout(this)
        root.addView(contents,LinearLayout.LayoutParams(-1,0,1f))
        val hypButton=button(tabs,"HYPNOSIS",false)
        val subButton=button(tabs,"SUBLIMINAL",false)
        hypButton.layoutParams=LinearLayout.LayoutParams(0,48.dp,1f)
        subButton.layoutParams=LinearLayout.LayoutParams(0,48.dp,1f)
        hypButton.setOnClickListener{hypButton.setTextColor(accent);subButton.setTextColor(textColor);openHypnosis()}
        subButton.setOnClickListener{subButton.setTextColor(accent);hypButton.setTextColor(textColor);openSubliminal()}
        statusText=label(root,"Idle",12,false,muted)
        statusText.setPadding(8.dp,12.dp,8.dp,12.dp)
        hypButton.performClick()
        if(StateStore.active(this)){
            // If activity was recreated while the native service is running, do not restart work.
            // If Android killed the service, startForegroundService can resume cached blocks.
            runCatching{startForegroundService(Intent(this,RenderService::class.java))}
        }
        handler.post(object:Runnable{override fun run(){statusText.text=StateStore.status(this@MainActivity);handler.postDelayed(this,1100)}})
    }
    private fun openHypnosis(){
        val content=scrollPane();contents.removeAllViews();contents.addView(content)
        val col=content.getChildAt(0) as LinearLayout
        section(col,"SESSION")
        edit(col,"Session title",p.getString("name","My Hypnosis Session")?:"My Hypnosis Session",single=true){putString("name",it)}
        label(col,"Voice · real Kokoro neural speakers",12,false,muted)
        val voiceItems=KokoroNative.voices.keys.toList()
        spinner(col,voiceItems,p.getString("voice","SERENA")?:"SERENA"){putString("voice",it)}
        label(col,"ORION / ROWAN / LUNA / SERENA use the installed Kokoro voice table.",11,false,muted)
        slider(col,"Speech speed",65,120,(p.getFloat("speed",.84f)*100).toInt(),"%") {p.edit().putFloat("speed",it/100f).apply()}
        val threads=p.getInt("threads",2)
        label(col,"Inference threads · benchmark 2 vs 4 on your S21",12,false,muted)
        spinner(col,listOf("1","2","3","4"),threads.toString()){p.edit().putInt("threads",it.toInt()).apply()}
        label(col,p.getString("benchmark","No local speed benchmark yet")?:"",12,false,muted)
        button(col,"BENCHMARK · AUTO-SELECT 2 OR 4 THREADS",false).setOnClickListener{startAction(RenderService.ACTION_BENCHMARK)}
        button(col,"GENERATE VOICE PREVIEW",false).setOnClickListener{startAction(RenderService.ACTION_PREVIEW)}
        button(col,"▶ PLAY VOICE PREVIEW",false).setOnClickListener{playPreview()}
        val info=label(col,if(ModelInstaller(this).present())"✓ Kokoro voice/model files installed on this phone" else "Kokoro pack not installed · first use downloads once",12,false,muted)
        button(col,"INSTALL / CHECK OFFLINE VOICES",false).setOnClickListener{
            startAction(RenderService.ACTION_INSTALL);info.text="Installing in Android app storage…"
        }
        section(col,"HYPNOSIS SCRIPT")
        label(col,"[SECTION: INDUCTION]  [PAUSE: 5]  [BREATHE]  [PARAGRAPH]  [SLOW]  [SOFT]  [WHISPER]  [NORMAL]",11,false,muted)
        val initial=p.getString("script","")?:""
        val editor=EditText(this).apply{
            setText(initial);setTextColor(textColor);setHintTextColor(muted);hint="[SECTION: INDUCTION]\n\nTake a slow, comfortable breath.\n\n[PAUSE: 5]"
            textSize=15f;minLines=12;setSingleLine(false);gravity=Gravity.TOP;setPadding(12.dp,12.dp,12.dp,12.dp)
            setBackgroundColor(panel);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        col.addView(editor,LinearLayout.LayoutParams(-1,310.dp))
        editor.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){p.edit().putString("script",s?.toString()?:"").apply()}
            override fun afterTextChanged(s:Editable?){}
        })
        val srInfo=label(col,"",12,false,muted)
        fun updateCount(){val nodes=ScriptParser.parse(editor.text.toString());srInfo.text="${nodes.count{it is ScriptNode.Speech}} speech blocks · ${nodes.count{it is ScriptNode.Pause}} timed pauses · auto-save ON"}
        updateCount()
        editor.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){updateCount()};override fun afterTextChanged(s:Editable?){} })
        button(col,"IMPORT SCRIPT .TXT",false).setOnClickListener{chooseText()}
        section(col,"RENDER & OUTPUT")
        check(col,"Binaural tone (headphones)",p.getBoolean("binaural",false)){p.edit().putBoolean("binaural",it).apply()}
        label(col,"24 kHz WAV · Stereo · saved to Downloads/SubGen. Completed blocks persist in private app storage.",12,false,muted)
        button(col,"CREATE HYPNOSIS WAV · ALL IN ONE",true).setOnClickListener{
            p.edit().putString("script",editor.text.toString()).apply()
            startAction(null)
        }
        button(col,"STOP RENDER · KEEP SAVED BLOCKS",false).setOnClickListener{startAction(RenderService.ACTION_CANCEL)}
        label(col,"Press Home during rendering: a foreground notification will remain. Android can still stop the job if you force-stop the app or the OS kills it.",12,false,muted)
    }
    private fun openSubliminal(){
        val content=scrollPane();contents.removeAllViews();contents.addView(content)
        val col=content.getChildAt(0) as LinearLayout
        section(col,"NATIVE SUBLIMINAL MIXER")
        label(col,"Two carrier sources and two affirmation sources. This native mixer currently accepts PCM16 WAV input; original v8.0.32 advanced DSP/presets are not migrated yet.",12,false,muted)
        pickAudio(col,801,"Carrier · STANDARD","carrierStandard")
        pickAudio(col,802,"Carrier · 8D","carrier8d")
        pickAudio(col,803,"Affirmation · NORMAL","affirmNormal")
        pickAudio(col,804,"Affirmation · FREQUENCY","affirmFrequency")
        edit(col,"Output filename",p.getString("subName","Subliminal Session")?:"Subliminal Session",true){putString("subName",it)}
        slider(col,"Carrier volume versus both affirmations",0,100,p.getInt("carrierPercent",50),"% carrier") {p.edit().putInt("carrierPercent",it).apply()}
        slider(col,"Normal vs frequency affirmations",0,100,p.getInt("primaryPercent",50),"% normal") {p.edit().putInt("primaryPercent",it).apply()}
        slider(col,"Output length",1,60,p.getInt("subMinutes",5)," minutes") {p.edit().putInt("subMinutes",it).apply()}
        button(col,"MIX & SAVE SUBLIMINAL WAV",true).setOnClickListener{startAction(RenderService.ACTION_SUBLIMINAL)}
        button(col,"STOP MIX",false).setOnClickListener{startAction(RenderService.ACTION_CANCEL)}
        label(col,"The original working Subliminal v0.4 remains a separate, untouched installation.",12,false,muted)
    }
    private fun pickAudio(parent:LinearLayout,code:Int,title:String,key:String){
        imports[code]=key
        val current=p.getString(key,"")?:""
        val name=label(parent,"$title · ${if(current.isNotBlank())File(current).name else "no file"}",12,false,muted)
        names[key]=name
        button(parent,"CHOOSE $title",false).setOnClickListener{
            val pick=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="audio/*"}
            @Suppress("DEPRECATION") startActivityForResult(pick,code)
        }
    }
    private fun chooseText(){val pick=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/plain"}
        @Suppress("DEPRECATION") startActivityForResult(pick,810)}
    @Deprecated("Android Activity callback for document picker")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK)return
        val uri=data?.data?:return
        try{
            if(requestCode==810){val script=contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()};
                p.edit().putString("script",script).apply();openHypnosis();return
            }
            val key=imports[requestCode]?:return
            val target=File(filesDir,"import-$key.wav")
            val tmp=File(filesDir,"import-$key.tmp")
            contentResolver.openInputStream(uri)!!.use{src->tmp.outputStream().use{dst->src.copyTo(dst)}}
            if(target.exists())target.delete();check(tmp.renameTo(target)){"Cannot store audio"}
            p.edit().putString(key,target.absolutePath).apply()
            names[key]?.text="${key.replaceFirstChar{it.uppercase()}} · ${target.length()/1024} KB loaded"
            Toast.makeText(this,"Stored native audio file",Toast.LENGTH_SHORT).show()
        }catch(t:Throwable){Toast.makeText(this,"Import failed: ${t.message}",Toast.LENGTH_LONG).show()}
    }
    private fun playPreview(){try{
        val file=File(filesDir,"native-voice-preview.wav")
        require(file.length()>44){"Generate a voice preview first"}
        val player=android.media.MediaPlayer()
        player.setDataSource(file.absolutePath)
        player.setOnCompletionListener{it.release()}
        player.prepare();player.start()
    }catch(t:Throwable){Toast.makeText(this,t.message,Toast.LENGTH_LONG).show()}}
    private fun startAction(action:String?){try{
        val intent=Intent(this,RenderService::class.java)
        if(action!=null)intent.action=action
        if(action==RenderService.ACTION_CANCEL)startService(intent) else startForegroundService(intent)
        Toast.makeText(this,"Processing in native Android service",Toast.LENGTH_SHORT).show()
    }catch(t:Throwable){Toast.makeText(this,"Cannot start: ${t.message}",Toast.LENGTH_LONG).show()}}
    private fun scrollPane():ScrollView{val outer=ScrollView(this).apply{fillViewport=false}
        val inner=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(0,6.dp,0,30.dp)}
        outer.addView(inner);return outer
    }
    private fun section(parent:LinearLayout,title:String){label(parent,title,14,true,accent).setPadding(4.dp,21.dp,4.dp,9.dp)}
    private fun label(parent:LinearLayout,value:String,size:Int,bold:Boolean,color:Int):TextView{
        val v=TextView(this).apply{text=value;textSize=size.toFloat();setTextColor(color);if(bold)setTypeface(null,Typeface.BOLD)}
        parent.addView(v,LinearLayout.LayoutParams(-1,-2));return v
    }
    private fun button(parent:LinearLayout,title:String,primary:Boolean):Button{
        val v=Button(this).apply{text=title;textSize=12f;setAllCaps(false)
            setTextColor(if(primary)Color.BLACK else textColor)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(if(primary)accent else panel))}
        parent.addView(v,LinearLayout.LayoutParams(-1,50.dp).apply{topMargin=8.dp})
        return v
    }
    private fun edit(parent:LinearLayout,title:String,initial:String,single:Boolean,save:android.content.SharedPreferences.Editor.(String)->Unit){
        label(parent,title,12,false,muted)
        val e=EditText(this).apply{setText(initial);setTextColor(textColor);textSize=15f;setSingleLine(single);setBackgroundColor(panel);setPadding(12.dp,4.dp,12.dp,4.dp)}
        parent.addView(e,LinearLayout.LayoutParams(-1,48.dp).apply{bottomMargin=10.dp})
        e.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){p.edit().apply{save(s?.toString()?:"")}.apply()};override fun afterTextChanged(s:Editable?){} })
    }
    private fun spinner(parent:LinearLayout,items:List<String>,value:String,selected:(String)->Unit){
        val spin=Spinner(this);val adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items)
        spin.adapter=adapter;spin.setSelection(items.indexOf(value).coerceAtLeast(0));spin.setBackgroundColor(panel)
        parent.addView(spin,LinearLayout.LayoutParams(-1,48.dp).apply{bottomMargin=8.dp})
        spin.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
            override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,i:Int,id:Long){selected(items[i])}
        }
    }
    private fun slider(parent:LinearLayout,title:String,min:Int,max:Int,current:Int,suffix:String,selected:(Int)->Unit){
        val t=label(parent,"$title · $current$suffix",12,false,textColor)
        val bar=SeekBar(this).apply{this.max=max-min;progress=(current-min).coerceIn(0,max-min)}
        parent.addView(bar)
        bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,n:Int,user:Boolean){val value=n+min;t.text="$title · $value$suffix";if(user)selected(value)}
            override fun onStartTrackingTouch(s:SeekBar?){}
            override fun onStopTrackingTouch(s:SeekBar?){}
        })
    }
    private fun check(parent:LinearLayout,label:String,initial:Boolean,change:(Boolean)->Unit){
        val c=CheckBox(this).apply{text=label;isChecked=initial;setTextColor(textColor);setOnCheckedChangeListener{_,v->change(v)}}
        parent.addView(c)
    }
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()
}
