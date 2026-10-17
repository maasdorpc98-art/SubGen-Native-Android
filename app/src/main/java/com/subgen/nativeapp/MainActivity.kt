package com.subgen.nativeapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
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
    // Native recreation of Subliminal Studio's visual language: charcoal panels,
    // thin crimson accents, monospaced labels and teal Hypnosis voice status.
    private val paletteBg=Color.rgb(15,19,24)
    private val panel=Color.rgb(22,28,36)
    private val surface2=Color.rgb(27,36,48)
    private val accent=Color.rgb(255,55,60)
    private val accentBright=Color.rgb(255,102,108)
    private val violet=Color.rgb(160,138,242)
    private val aqua=Color.rgb(0,229,160)
    private val textColor=Color.rgb(245,247,252)
    private val muted=Color.rgb(160,184,204)
    private val p by lazy{StateStore.prefs(this)}
    private val imports=linkedMapOf<Int,String>()
    private val names=linkedMapOf<String,TextView>()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        window.statusBarColor=paletteBg;window.navigationBarColor=paletteBg
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),42)
        root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(paletteBg)}
        setContentView(root)
        val header=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(20.dp,22.dp,20.dp,13.dp)
            background=GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(Color.rgb(28,18,25),paletteBg)).apply{
                setStroke(1.dp,Color.rgb(82,35,44))
            }
        }
        root.addView(header,LinearLayout.LayoutParams(-1,-2))
        val brand=label(header,"SUBLIMINAL",29,true,accentBright)
        brand.letterSpacing=.18f
        brand.typeface=Typeface.create("sans-serif-black",Typeface.BOLD)
        val studio=label(header,"S T U D I O    /    N A T I V E",12,true,textColor)
        studio.letterSpacing=.11f
        label(header,"NEURAL AUDIO  ·  OFFLINE KOKORO  ·  AUTOMATIC SAVE",9,false,muted)
        val tabs=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL
            setPadding(8.dp,8.dp,8.dp,8.dp)
            background=shape(surface2,Color.rgb(62,54,72),16)
        }
        root.addView(tabs,LinearLayout.LayoutParams(-1,-2).apply{setMargins(16.dp,12.dp,16.dp,6.dp)})
        contents=FrameLayout(this)
        root.addView(contents,LinearLayout.LayoutParams(-1,0,1f))
        val subButton=tab(tabs,"⚙  STUDIO")
        val hypButton=tab(tabs,"◈  HYPNOSIS ENGINE")
        subButton.setOnClickListener{
            activateTab(subButton,hypButton);openSubliminal()
        }
        hypButton.setOnClickListener{
            activateTab(hypButton,subButton);openHypnosis()
        }
        val strip=LinearLayout(this).apply{
            orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL
            background=shape(Color.rgb(17,27,31),Color.rgb(26,82,76),12)
            setPadding(13.dp,10.dp,13.dp,10.dp)
        }
        root.addView(strip,LinearLayout.LayoutParams(-1,-2).apply{setMargins(16.dp,5.dp,16.dp,10.dp)})
        val dot=TextView(this).apply{text="●";textSize=12f;setTextColor(aqua)}
        strip.addView(dot,LinearLayout.LayoutParams(-2,-2).apply{rightMargin=10.dp})
        statusText=TextView(this).apply{text="READY  ·  NATIVE ENGINE";textSize=11f;typeface=Typeface.MONOSPACE;setTextColor(muted);maxLines=2}
        strip.addView(statusText,LinearLayout.LayoutParams(0,-2,1f))
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
        val session=section(col,"01   VOICE STUDIO")
        edit(session,"Session title",p.getString("name","My Hypnosis Session")?:"My Hypnosis Session",single=true){putString("name",it)}
        label(session,"VOICE LIBRARY   /   SELECT YOUR NARRATOR",11,false,muted).apply{typeface=Typeface.MONOSPACE;letterSpacing=.12f;setPadding(0,6.dp,0,9.dp)}
        val voiceItems=KokoroNative.voices.keys.toList()
        voiceCards(session,voiceItems,p.getString("voice","SERENA")?:"SERENA")
        label(session,"ONE SHARED OFFLINE VOICE PACK  ·  FOUR DISTINCT VOICES",11,false,muted)
        slider(session,"Speech speed",65,120,(p.getFloat("speed",.84f)*100).toInt(),"%") {p.edit().putFloat("speed",it/100f).apply()}
        val threads=p.getInt("threads",2)
        label(session,"Inference threads · benchmark 2 vs 4 on your S21",12,false,muted)
        spinner(session,listOf("1","2","3","4"),threads.toString()){p.edit().putInt("threads",it.toInt()).apply()}
        label(session,p.getString("benchmark","No local speed benchmark yet")?:"",12,false,muted)
        button(session,"BENCHMARK · AUTO-SELECT 2 OR 4 THREADS",false).setOnClickListener{startAction(RenderService.ACTION_BENCHMARK)}
        button(session,"GENERATE VOICE PREVIEW",false).setOnClickListener{startAction(RenderService.ACTION_PREVIEW)}
        button(session,"▶ PLAY VOICE PREVIEW",false).setOnClickListener{playPreview()}
        val info=label(session,if(ModelInstaller(this).present())"✓ OFFLINE VOICE LIBRARY READY ON THIS DEVICE" else "VOICE PACK NOT INSTALLED  ·  ONE-TIME DOWNLOAD",12,false,muted)
        button(session,"INSTALL / CHECK OFFLINE VOICES",false).setOnClickListener{
            startAction(RenderService.ACTION_INSTALL);info.text="Installing in Android app storage…"
        }
        val scriptCard=section(col,"02   HYPNOSIS SCRIPT")
        label(scriptCard,"[SECTION: INDUCTION]  [PAUSE: 5]  [BREATHE]  [PARAGRAPH]  [SLOW]  [SOFT]  [WHISPER]  [NORMAL]",11,false,muted)
        val initial=p.getString("script","")?:""
        val editor=EditText(this).apply{
            setText(initial);setTextColor(textColor);setHintTextColor(muted);hint="[SECTION: INDUCTION]\n\nTake a slow, comfortable breath.\n\n[PAUSE: 5]"
            textSize=15f;minLines=12;setSingleLine(false);gravity=Gravity.TOP;setPadding(12.dp,12.dp,12.dp,12.dp)
            background=shape(Color.rgb(15,22,30),Color.rgb(60,65,86),14);inputType=android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        scriptCard.addView(editor,LinearLayout.LayoutParams(-1,310.dp).apply{bottomMargin=12.dp})
        editor.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){p.edit().putString("script",s?.toString()?:"").apply()}
            override fun afterTextChanged(s:Editable?){}
        })
        val srInfo=label(scriptCard,"",12,false,muted)
        fun updateCount(){val nodes=ScriptParser.parse(editor.text.toString());srInfo.text="${nodes.count{it is ScriptNode.Speech}} speech blocks · ${nodes.count{it is ScriptNode.Pause}} timed pauses · auto-save ON"}
        updateCount()
        editor.addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){updateCount()};override fun afterTextChanged(s:Editable?){} })
        button(scriptCard,"IMPORT SCRIPT .TXT",false).setOnClickListener{chooseText()}
        val outputCard=section(col,"03   RENDER & EXPORT")
        check(outputCard,"Binaural tone (headphones)",p.getBoolean("binaural",false)){p.edit().putBoolean("binaural",it).apply()}
        label(outputCard,"24 kHz WAV · Stereo · saved to Downloads/SubGen. Completed blocks persist in private app storage.",12,false,muted)
        button(outputCard,"CREATE HYPNOSIS WAV · ALL IN ONE",true).setOnClickListener{
            p.edit().putString("script",editor.text.toString()).apply()
            startAction(null)
        }
        button(outputCard,"STOP RENDER · KEEP SAVED BLOCKS",false).setOnClickListener{startAction(RenderService.ACTION_CANCEL)}
        label(outputCard,"Press Home during rendering: a foreground notification will remain. Android can still stop the job if you force-stop the app or the OS kills it.",12,false,muted)
    }
    private fun openSubliminal(){
        val content=scrollPane();contents.removeAllViews();contents.addView(content)
        val col=content.getChildAt(0) as LinearLayout
        val mixerCard=section(col,"01   FOUR-TRACK AUDIO MIXER")
        label(mixerCard,"2 CARRIERS  /  2 AFFIRMATIONS  ·  LOSSLESS PCM16 WAV SOURCES",12,false,muted)
        pickAudio(mixerCard,801,"Carrier · STANDARD","carrierStandard")
        pickAudio(mixerCard,802,"Carrier · 8D","carrier8d")
        pickAudio(mixerCard,803,"Affirmation · NORMAL","affirmNormal")
        pickAudio(mixerCard,804,"Affirmation · FREQUENCY","affirmFrequency")
        edit(mixerCard,"Output filename",p.getString("subName","Subliminal Session")?:"Subliminal Session",true){putString("subName",it)}
        slider(mixerCard,"Carrier volume versus both affirmations",0,100,p.getInt("carrierPercent",50),"% carrier") {p.edit().putInt("carrierPercent",it).apply()}
        slider(mixerCard,"Normal vs frequency affirmations",0,100,p.getInt("primaryPercent",50),"% normal") {p.edit().putInt("primaryPercent",it).apply()}
        slider(mixerCard,"Output length",1,60,p.getInt("subMinutes",5)," minutes") {p.edit().putInt("subMinutes",it).apply()}
        button(mixerCard,"MIX & SAVE SUBLIMINAL WAV",true).setOnClickListener{startAction(RenderService.ACTION_SUBLIMINAL)}
        button(mixerCard,"STOP MIX",false).setOnClickListener{startAction(RenderService.ACTION_CANCEL)}
        label(mixerCard,"NATIVE MIX ENGINE  ·  PRESETS FROM v8.0.32 STILL BEING PORTED",12,false,muted)
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
    private fun shape(fill:Int,stroke:Int=Color.TRANSPARENT,radius:Int=14):GradientDrawable =
        GradientDrawable().apply{setColor(fill);cornerRadius=radius.dp.toFloat();if(stroke!=Color.TRANSPARENT)setStroke(1.dp,stroke)}
    private fun gradient(a:Int,b:Int,radius:Int=14):GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply{cornerRadius=radius.dp.toFloat()}
    private fun tab(parent:LinearLayout,name:String):TextView{
        val v=TextView(this).apply{text=name;gravity=Gravity.CENTER;textSize=10.5f;letterSpacing=.12f
            typeface=Typeface.create("sans-serif-medium",Typeface.BOLD);setTextColor(muted)
            background=shape(Color.TRANSPARENT,Color.TRANSPARENT,12)
        }
        parent.addView(v,LinearLayout.LayoutParams(0,48.dp,1f))
        return v
    }
    private fun activateTab(on:TextView,off:TextView){
        on.background=gradient(Color.rgb(66,28,39),Color.rgb(38,24,38),12)
        on.setTextColor(accentBright)
        off.background=shape(Color.TRANSPARENT)
        off.setTextColor(muted)
    }
    private fun scrollPane():ScrollView{
        val outer=ScrollView(this).apply{isFillViewport=false;clipToPadding=false}
        val inner=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(16.dp,10.dp,16.dp,30.dp)
        }
        outer.addView(inner);return outer
    }
    private fun section(parent:LinearLayout,title:String):LinearLayout{
        val panelCard=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;setPadding(18.dp,19.dp,18.dp,20.dp)
            background=gradient(Color.rgb(21,29,38),Color.rgb(15,22,30),17).apply{setStroke(1.dp,Color.rgb(62,53,77))}
        }
        parent.addView(panelCard,LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=15.dp})
        val heading=label(panelCard,title,12,true,muted).apply{
            letterSpacing=.18f;typeface=Typeface.MONOSPACE
            setPadding(0,0,0,14.dp)
        }
        val line=View(this).apply{background=gradient(accent,Color.rgb(74,50,99),2)}
        panelCard.addView(line,LinearLayout.LayoutParams(-1,1.dp).apply{bottomMargin=16.dp})
        return panelCard
    }
    private fun label(parent:LinearLayout,value:String,size:Int,bold:Boolean,color:Int):TextView{
        val v=TextView(this).apply{
            text=value;textSize=size.toFloat();setTextColor(color)
            typeface=if(bold)Typeface.create("sans-serif-medium",Typeface.BOLD) else Typeface.create("sans-serif",Typeface.NORMAL)
            includeFontPadding=true
        }
        parent.addView(v,LinearLayout.LayoutParams(-1,-2));return v
    }
    private fun button(parent:LinearLayout,title:String,primary:Boolean):Button{
        val v=Button(this).apply{
            text=title;textSize=11.5f;letterSpacing=.09f;setAllCaps(false)
            typeface=Typeface.create("sans-serif-medium",Typeface.BOLD)
            setTextColor(if(primary)Color.rgb(16,17,24) else textColor)
            background=if(primary)gradient(Color.rgb(163,119,245),Color.rgb(82,170,210),12)
                else shape(Color.rgb(25,33,45),Color.rgb(68,66,94),12)
            elevation=if(primary)3.dp.toFloat() else 0f
            minHeight=0;minimumHeight=0;setPadding(12.dp,0,12.dp,0)
        }
        parent.addView(v,LinearLayout.LayoutParams(-1,49.dp).apply{topMargin=10.dp})
        return v
    }
    private fun edit(parent:LinearLayout,title:String,initial:String,single:Boolean,save:android.content.SharedPreferences.Editor.(String)->Unit){
        label(parent,title.uppercase(),11,false,muted).apply{letterSpacing=.12f;typeface=Typeface.MONOSPACE;setPadding(0,3.dp,0,8.dp)}
        val e=EditText(this).apply{
            setText(initial);setTextColor(textColor);setHintTextColor(muted)
            textSize=14f;setSingleLine(single)
            background=shape(Color.rgb(15,22,30),Color.rgb(60,65,86),12)
            setPadding(13.dp,6.dp,13.dp,6.dp)
        }
        parent.addView(e,LinearLayout.LayoutParams(-1,48.dp).apply{bottomMargin=12.dp})
        e.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){p.edit().apply{save(s?.toString()?:"")}.apply()}
            override fun afterTextChanged(s:Editable?){}
        })
    }
    private fun voiceCards(parent:LinearLayout,items:List<String>,value:String){
        val choices=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        parent.addView(choices)
        val cards=linkedMapOf<String,LinearLayout>()
        val details=mapOf("ORION" to "MALE · STEADY", "ROWAN" to "MALE · CALM", "LUNA" to "FEMALE · WARM", "SERENA" to "FEMALE · SOFT")
        fun updateSelected(selected:String){
            cards.forEach{(name,card)->
                val active=name==selected
                card.background=shape(if(active)Color.rgb(16,48,47) else Color.rgb(14,24,34),if(active)aqua else Color.rgb(45,58,78),12)
                (card.getChildAt(1) as TextView).setTextColor(if(active)aqua else textColor)
            }
        }
        items.chunked(2).forEach{pair->
            val row=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
            choices.addView(row)
            pair.forEach{name->
                val card=LinearLayout(this).apply{
                    orientation=LinearLayout.VERTICAL;setPadding(13.dp,11.dp,11.dp,12.dp)
                }
                row.addView(card,LinearLayout.LayoutParams(0,92.dp,1f).apply{setMargins(0,0,8.dp,9.dp)})
                label(card,details[name]?:"NEURAL VOICE",9,false,muted).apply{typeface=Typeface.MONOSPACE;letterSpacing=.08f}
                label(card,name,18,true,textColor).apply{letterSpacing=.12f;setPadding(0,3.dp,0,0)}
                label(card,when(name){"ORION"->"am_michael";"ROWAN"->"am_fenrir";"LUNA"->"af_bella";else->"af_heart"},10,false,muted)
                cards[name]=card
                card.setOnClickListener{p.edit().putString("voice",name).apply();updateSelected(name)}
            }
        }
        updateSelected(value)
    }
    private fun spinner(parent:LinearLayout,items:List<String>,value:String,selected:(String)->Unit){
        val spin=Spinner(this)
        val adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items)
        spin.adapter=adapter;spin.setSelection(items.indexOf(value).coerceAtLeast(0))
        spin.background=shape(surface2,Color.rgb(64,68,91),12)
        parent.addView(spin,LinearLayout.LayoutParams(-1,48.dp).apply{bottomMargin=12.dp})
        spin.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(p:android.widget.AdapterView<*>?){}
            override fun onItemSelected(p:android.widget.AdapterView<*>?,v:View?,i:Int,id:Long){selected(items[i])}
        }
    }
    private fun slider(parent:LinearLayout,title:String,min:Int,max:Int,current:Int,suffix:String,selected:(Int)->Unit){
        val t=label(parent,"${title.uppercase()}     $current$suffix",11,false,muted).apply{
            typeface=Typeface.MONOSPACE;letterSpacing=.075f;setPadding(0,15.dp,0,5.dp)
        }
        val bar=SeekBar(this).apply{
            this.max=max-min;progress=(current-min).coerceIn(0,max-min)
            progressTintList=ColorStateList.valueOf(violet)
            thumbTintList=ColorStateList.valueOf(violet)
            progressBackgroundTintList=ColorStateList.valueOf(Color.rgb(48,47,70))
        }
        parent.addView(bar,LinearLayout.LayoutParams(-1,39.dp))
        bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(s:SeekBar?,n:Int,user:Boolean){val v=n+min;t.text="${title.uppercase()}     $v$suffix";if(user)selected(v)}
            override fun onStartTrackingTouch(s:SeekBar?){}
            override fun onStopTrackingTouch(s:SeekBar?){}
        })
    }
    private fun check(parent:LinearLayout,label:String,initial:Boolean,change:(Boolean)->Unit){
        val c=CheckBox(this).apply{
            text=label;isChecked=initial;setTextColor(textColor)
            buttonTintList=ColorStateList.valueOf(violet)
            textSize=12f;setOnCheckedChangeListener{_,v->change(v)}
        }
        parent.addView(c)
    }
    private val Int.dp:Int get()=(this*resources.displayMetrics.density).toInt()
}