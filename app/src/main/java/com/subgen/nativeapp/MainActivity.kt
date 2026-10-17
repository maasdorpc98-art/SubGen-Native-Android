package com.subgen.nativeapp

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.*

/** Pure native Android: original v8.0.32 sections, preset settings, controls and theme.
 * No WebView, HTML or embedded screenshots. Original controls are native Views.
 */
class MainActivity : Activity() {
    private val bg = Color.rgb(15,19,24)
    private val surface = Color.rgb(22,28,36)
    private val surface2 = Color.rgb(28,36,48)
    private val red = Color.rgb(255,51,51)
    private val coral = Color.rgb(255,102,102)
    private val bright = Color.WHITE
    private val muted = Color.rgb(160,184,204)
    private val cool = Color.rgb(138,183,214)
    private val green = Color.rgb(0,229,160)
    private val violet = Color.rgb(159,123,255)
    private val p by lazy { StateStore.prefs(this) }
    private val slides=linkedMapOf<String,OriginalStudioSliderView>()
    private val checks=linkedMapOf<String,CheckBox>()
    private val selects=linkedMapOf<String,Spinner>()
    private val imported=linkedMapOf<Int,String>()
    private val sourceLabels=linkedMapOf<String,TextView>()
    private val handler=Handler(Looper.getMainLooper())
    private lateinit var shell:LinearLayout
    private lateinit var viewport:ScrollView
    private lateinit var column:LinearLayout
    private lateinit var body:LinearLayout
    private lateinit var tabStudio:TextView
    private lateinit var tabHypnosis:TextView
    private lateinit var status:TextView
    private var activeTab="studio"
    private var applyingPreset=false
    private var selectedPreset="custom"
    private val originalPresets by lazy { org.json.JSONArray(assets.open("original-presets.json").bufferedReader().use{it.readText()}) }
    private val originalSelects by lazy { JSONObject(assets.open("original-selects.json").bufferedReader().use{it.readText()}) }
    private val mono=Typeface.create("monospace",Typeface.NORMAL)
    private val display=Typeface.create("sans-serif",Typeface.BOLD)
    private val dp get()=resources.displayMetrics.density
    private fun d(v:Int)=(v*dp+.5f).toInt()
    private fun params(h:Int=ViewGroup.LayoutParams.WRAP_CONTENT)=LinearLayout.LayoutParams(-1,h)
    private fun cardShape(fill:Int=surface,stroke:Int=Color.rgb(69,42,49),radius:Int=14):GradientDrawable =
        GradientDrawable().apply { setColor(fill); cornerRadius=d(radius).toFloat();setStroke(d(1).coerceAtLeast(1),stroke) }
    private fun gradient(a:Int,b:Int,radius:Int=12):GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TL_BR,intArrayOf(a,b)).apply{cornerRadius=d(radius).toFloat()}
    private fun space(parent:LinearLayout,px:Int){parent.addView(View(this),params(d(px)))}
    private fun pad(parent:LinearLayout,l:Int=14,t:Int=14,r:Int=14,b:Int=14){parent.setPadding(d(l),d(t),d(r),d(b))}
    private fun txt(parent:LinearLayout,value:String,size:Float=11f,color:Int=muted,bold:Boolean=false,monoFont:Boolean=false,tracking:Float=0f,gravity:Int=Gravity.START):TextView {
        val v=TextView(this).apply{
            text=value;textSize=size;setTextColor(color);typeface=if(monoFont)mono else if(bold)this@MainActivity.display else Typeface.create("sans-serif",Typeface.NORMAL)
            letterSpacing=tracking;this.gravity=gravity;includeFontPadding=false
            setLineSpacing(d(2).toFloat(),1.05f)
        };parent.addView(v,params());return v
    }
    private fun panel(parent:LinearLayout,fill:Int=surface,border:Int=Color.rgb(64,45,53),radius:Int=14,padding:Int=16,margin:Int=14):LinearLayout {
        val v=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;pad(this,padding,padding,padding,padding);background=cardShape(fill,border,radius)}
        parent.addView(v,params().apply{bottomMargin=d(margin)});return v
    }
    private fun section(parent:LinearLayout,heading:String):LinearLayout {
        val c=panel(parent,fill=Color.rgb(24,30,39),border=Color.rgb(77,37,46),radius=13,padding=15,margin=16)
        val bar=View(this).apply{background=gradient(red,Color.rgb(255,170,27),2)}
        c.addView(bar,params(d(2)))
        space(c,16)
        txt(c,heading.uppercase(),10f,coral,monoFont=true,tracking=.25f)
        space(c,19)
        return c
    }
    private fun subCard(parent:LinearLayout,title:String,subtitle:String="",tint:Int=Color.rgb(61,78,94)):LinearLayout {
        val c=panel(parent,fill=Color.rgb(20,27,36),border=tint,padding=15,margin=12)
        txt(c,title,14f,bright,true)
        if(subtitle.isNotBlank()){space(c,5);txt(c,subtitle,10f,cool,monoFont=true)}
        space(c,10)
        return c
    }
    private fun action(parent:LinearLayout,label:String,kind:String="secondary",callback:()->Unit):TextView {
        val a=when(kind){"primary"->Color.rgb(110,46,50);"green"->Color.rgb(0,61,44);"aqua"->Color.rgb(12,51,52);else->Color.rgb(28,28,37)}
        val border=when(kind){"primary"->red;"green","aqua"->green;else->Color.rgb(126,62,69)}
        val foreground=when(kind){"primary"->Color.rgb(255,222,224);"green"->Color.rgb(163,255,209);"aqua"->Color.rgb(194,254,242);else->Color.rgb(255,169,177)}
        val v=TextView(this).apply{
            text=label;textSize=if(kind=="primary")13f else 11f;typeface=mono;letterSpacing=if(kind=="primary").25f else .12f;setTextColor(foreground);gravity=Gravity.CENTER
            setPadding(d(10),d(13),d(10),d(13));background=cardShape(a,border,10)
            isClickable=true;isFocusable=true;setOnClickListener{Diagnostics.interaction("button:"+label);callback()}
        }
        parent.addView(v,params().apply{topMargin=d(8)});return v
    }
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        window.statusBarColor=bg;window.navigationBarColor=bg
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),42)
        viewport=ScrollView(this).apply{setBackgroundColor(bg);isFillViewport=false;clipToPadding=false}
        column=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;pad(this,12,26,12,60);background=GridBackdrop()}
        viewport.addView(column);setContentView(viewport)
        buildHeader()
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
        column.addView(body,params())
        showTab(if(savedInstanceState!=null)savedInstanceState.getString("tab")?:"studio" else "studio")
        // No automatic render restart: on a process death, preserve checkpoints and wait for explicit user input.
        handler.post(object:Runnable{override fun run(){if(!isFinishing){refreshStatus();handler.postDelayed(this,1200)}}})
    }
    override fun onResume(){super.onResume();Diagnostics.setForeground(true)}
    override fun onPause(){Diagnostics.setForeground(false);super.onPause()}
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);super.onDestroy()}
    override fun onSaveInstanceState(outState:Bundle){outState.putString("tab",activeTab);super.onSaveInstanceState(outState)}
    private inner class GridBackdrop:android.graphics.drawable.Drawable(){
        private val paint=Paint().apply{color=Color.argb(5,255,59,59);strokeWidth=d(1).toFloat()}
        override fun draw(c:Canvas){c.drawColor(bg);var x=0;while(x<bounds.width()){c.drawLine(x.toFloat(),0f,x.toFloat(),bounds.height().toFloat(),paint);x+=d(48)}
            var y=0;while(y<bounds.height()){c.drawLine(0f,y.toFloat(),bounds.width().toFloat(),y.toFloat(),paint);y+=d(48)} }
        override fun setAlpha(alpha:Int){}; override fun setColorFilter(f:android.graphics.ColorFilter?){};@Deprecated("legacy") override fun getOpacity()=android.graphics.PixelFormat.OPAQUE
    }
    private fun buildHeader(){
        val header=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER_HORIZONTAL}
        column.addView(header,params())
        txt(header,"SUBLIMINAL",30f,coral,true,tracking=.30f,gravity=Gravity.CENTER)
        txt(header,"STUDIO",30f,coral,true,tracking=.38f,gravity=Gravity.CENTER)
        space(header,8)
        txt(header,"A U T O M A T E D   ·   I N T E L L I G E N T",9f,cool,monoFont=true,gravity=Gravity.CENTER)
        txt(header,"P R E C I S I O N - E N G I N E E R E D",9f,cool,monoFont=true,gravity=Gravity.CENTER)
        space(header,11)
        val version=panel(header,Color.rgb(34,19,25),Color.rgb(130,25,35),radius=24,padding=8,margin=0)
        txt(version,"V8.0.32  ·  DEEP INTEGRATION  ·  ENCODE + INSTALL",9f,coral,monoFont=true,tracking=.12f,gravity=Gravity.CENTER)
        space(header,16)
        val diag=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;pad(this,30,0,30,0)}
        header.addView(diag,params())
        action(diag,"◉ SUBLIMINAL DIAGNOSTICS","aqua"){
            AlertDialog.Builder(this).setTitle("Subliminal Diagnostics")
                .setMessage(Diagnostics.overview()+"\n\nEngine: ${StateStore.status(this)}\nOriginal sliders: ${OriginalStudioControlSpec.sliders.size}\nPreset profiles: ${originalPresets.length()}")
                .setPositiveButton("EXPORT REPORT"){_,_->chooseExport(923,"SubGen-Diagnostic-Report.json")}
                .setNeutralButton("CLEAR HISTORY"){_,_->AlertDialog.Builder(this).setTitle("Delete diagnostic history?")
                    .setMessage("Only diagnostic logs will be deleted; audio, settings and presets are unchanged.")
                    .setPositiveButton("DELETE"){_,_->Diagnostics.clearHistory()}.setNegativeButton("CANCEL",null).show()}
                .setNegativeButton("CLOSE",null).show()
        }
        space(header,20)
        val save=panel(header,fill=Color.rgb(12,27,37),border=Color.rgb(58,80,94),radius=14,padding=13,margin=0)
        txt(save,"💾 SAVE WORK",14f,Color.rgb(229,251,255),true,tracking=.12f)
        space(save,5)
        val on=p.getBoolean("saveWork",false)
        val current=txt(save,if(on)"ON · Current settings and source audio are preserved in this app." else "OFF · Current session only. Turn on to preserve settings and source audio after the app closes.",10f,cool,monoFont=true)
        space(save,9)
        val toggle=action(save,if(on)"● SAVE WORK · ON" else "● SAVE WORK · OFF"){}
        toggle.setOnClickListener{val changed=!p.getBoolean("saveWork",false);p.edit().putBoolean("saveWork",changed).apply();toggle.text=if(changed)"● SAVE WORK · ON" else "● SAVE WORK · OFF";current.text=if(changed)"ON · Settings and source audio are kept in this app." else "OFF · Current session only. Source audio is retained until cleared."}
        space(header,18)
        val tabs=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;pad(this,6,6,6,6);background=cardShape(Color.rgb(24,28,37),Color.rgb(88,38,49),12)}
        header.addView(tabs,params())
        fun tab(label:String):TextView{
            val v=TextView(this).apply{text=label;gravity=Gravity.CENTER;textSize=10f;typeface=mono;letterSpacing=.17f;setTextColor(cool);setPadding(d(6),d(14),d(6),d(14))}
            tabs.addView(v,LinearLayout.LayoutParams(0,-2,1f));return v
        }
        tabStudio=tab("⚙  STUDIO")
        tabHypnosis=tab("🧠 HYPNOSIS ENGINE")
        tabStudio.setOnClickListener{showTab("studio")}
        tabHypnosis.setOnClickListener{showTab("hypnosis")}
        space(header,26)
    }
    private fun showTab(name:String){activeTab=name;body.removeAllViews();slides.clear();checks.clear();selects.clear();sourceLabels.clear()
        val onBg=cardShape(Color.rgb(52,26,34),Color.rgb(155,33,39),8)
        tabStudio.background=if(name=="studio")onBg else null
        tabHypnosis.background=if(name=="hypnosis")cardShape(Color.rgb(52,26,34),Color.rgb(155,33,39),8) else null
        tabStudio.setTextColor(if(name=="studio")coral else cool)
        tabHypnosis.setTextColor(if(name=="hypnosis")coral else cool)
        if(name=="studio")buildStudio() else buildHypnosis()
    }
    private fun refreshStatus(){if(::status.isInitialized){status.text=StateStore.status(this)}}
    private fun statusCard(label:String,value:String,detail:String,tint:Int):LinearLayout{
        val c=panel(body,fill=Color.rgb(20,26,34),border=Color.rgb(54,65,75),radius=13,padding=15,margin=11)
        c.setPadding(d(17),d(14),d(15),d(17))
        c.addView(View(this).apply{setBackgroundColor(tint)},LinearLayout.LayoutParams(d(2),0))
        txt(c,label.uppercase(),9f,cool,monoFont=true,tracking=.2f)
        space(c,8)
        txt(c,value,14f,bright,true)
        space(c,5)
        txt(c,detail,10f,cool,monoFont=true)
        return c
    }
    private fun buildStudio(){
        statusCard("ENGINE","Ready","Dual-affirmation engine · Carrier Normal + 8D · optional Binaural + Rhythmic generators",green)
        val loaded=listOf("carrierStandard","affirmNormal","affirmFrequency").count{p.getString(it,"")?.let{f->File(f).isFile}==true}
        statusCard("INPUTS","$loaded / 3 loaded","Load Affirmation 1 and Affirmation 1 (A)",Color.rgb(231,164,82))
        val mins=p.getString("ctrl-duration","10")?.toIntOrNull()?:10
        val rate=p.getString("ctrl-samplerate","44100")?.toIntOrNull()?:44100
        statusCard("PROJECTED OUTPUT","$mins min · ${"%.1f".format(Locale.US,mins*60.0*rate*4/1e6)} MB","${"%,d".format(rate)} Hz · stereo 16-bit WAV",Color.rgb(243,101,119))
        val prof=p.getString("presetLabel","Factory defaults")?:"Factory defaults"
        statusCard("MIX STATE",prof,"Use Save Work above for browser recovery",coral)
        val upload=section(body,"01 — Upload Tracks")
        txt(upload,"✓ Local processing · audio stays on your device.",11f,green)
        space(upload,8)
        source(upload,"🌊 CARRIER","One uploaded audio file","Feeds Normal + 8D carrier functions",901,"carrierStandard")
        source(upload,"🎙 PRIMARY · AFFIRMATION 1","Main affirmation recording","Primary · stationary voice",902,"affirmNormal")
        source(upload,"🎙 SECONDARY · AFFIRMATION 1 (A)","Second affirmation recording","Secondary · stationary voice",903,"affirmFrequency")
        val presets=section(body,"02 — Presets")
        txt(presets,"DEEP INTEGRATION · ENCODE + INSTALL is the flagship understanding-first preset: natural-speed intelligible Primary speech stays dominant.",10f,cool)
        space(presets,10)
        
        for(i in 0 until originalPresets.length()){
            val preset=originalPresets.getJSONObject(i)
            val raw=preset.getString("description")
            val label=preset.getString("title")
            val key=preset.getString("id")
            val c=panel(presets,fill=Color.rgb(26,28,38),border=Color.rgb(90,50,72),radius=10,padding=12,margin=7)
            val title=txt(c,label.uppercase(),11f,Color.rgb(232,224,255),true)
            val desc=raw.trim().take(260)
            if(desc.isNotBlank()){space(c,4);txt(c,desc,9f,cool)}
            c.setOnClickListener{applyPreset(key);Toast.makeText(this,"${label} preset applied",Toast.LENGTH_SHORT).show()}
            c.isClickable=true;c.isFocusable=true
        }
        val saved=subCard(presets,"✦ My saved presets","Saved to this Android app")
        textEntry(saved,"Name your mix (e.g. My dual affirmation mix)","namedPreset",p.getString("namedPreset","")?:"",single=true)
        action(saved,"＋ Save / Update") {saveNamedPreset()}
        action(saved,"✓ Load") {loadNamedPreset()}
        action(saved,"✕ Delete") {deleteNamedPreset()}
        action(saved,"⬇ Backup All") {chooseExport(920,"mix-presets.json")}
        action(saved,"⬆ Import Backup") {chooseJson(921)}
        val mixer=section(body,"03 / MIXING DESK")
        txt(mixer,"Three-source mixer + generated brainwave audio",17f,bright,true)
        space(mixer,6)
        txt(mixer,"One Carrier file feeds Normal and 8D. Primary and Secondary stay one layer each on the same base renderer.",10f,cool)
        space(mixer,12)
        val flow=subCard(mixer,"CARRIER  +  PRIMARY  +  SECONDARY","+ Binaural · OFF  + Rhythmic · OFF  → Master",Color.rgb(49,84,86))
        val carrier=subCard(mixer,"≈ Carrier","01 · One audio · Normal + 8D",Color.rgb(71,84,99))
        slider(carrier,"ctrl-vol-carrier")
        expandable(carrier,"▸ Carrier Functions · Normal + 8D ⌄",false){c->
            txt(c,"The same Carrier file can feed two functions: Normal is stationary, and only the 8D branch moves.",10f,cool)
            listOf("ctrl-vol-carrier-8d","ctrl-carrier-hpf","ctrl-carrier-lpf","ctrl-carrier-width","ctrl-carrier-fade","ctrl-carrier-8d-motion","ctrl-carrier-8d-speed").forEach{slider(c,it)}
        }
        val primary=subCard(mixer,"◉ Primary · Affirmation 1","02 · Primary source volume · shared 1-layer path",Color.rgb(79,68,112))
        slider(primary,"ctrl-vol1")
        val secondary=subCard(mixer,"◉ Secondary · Affirmation 1 (A)","03 · Standard relationship + optional preset-authorized edits",Color.rgb(85,51,77))
        slider(secondary,"ctrl-vol-noise")
        slider(secondary,"ctrl-master-balance-a",master=true)
        toggle(secondary,"ctrl-secondary-mod-on","Secondary Preset Modification · OFF")
        expandable(secondary,"▸ Secondary Functions · Standard + Preset Permission",false){c->
            txt(c,"Optional Secondary speed and tone edits require preset permission.",10f,cool)
            slider(c,"ctrl-secondary-speed")
            select(c,"ctrl-secondary-tone","SECONDARY TONE")
        }
        val balances=subCard(mixer,"Master balance","Carrier versus both affirmations together",Color.rgb(63,70,95))
        slider(balances,"ctrl-master-balance-b",master=true)
        val binaural=subCard(mixer,"🎧 Generated Brainwave Audio","Binaural Beat plus a separate Rhythmic Tone engine.",Color.rgb(55,81,102))
        toggle(binaural,"ctrl-binaural-on","Binaural Beat · OFF")
        expandable(binaural,"▸ Binaural Beat Controls 10.0 Hz · 400 Hz center",false){c->
            listOf("ctrl-binaural-volume","ctrl-binaural-beat","ctrl-binaural-center").forEach{slider(c,it)}
        }
        toggle(binaural,"ctrl-rhythmic-on","Rhythmic Tone · OFF")
        expandable(binaural,"▸ Rhythmic Tone Controls 6.0 Hz · Theta range",false){c->
            listOf("ctrl-rhythmic-volume","ctrl-rhythmic-pulse").forEach{slider(c,it)}
            action(c,"DELTA · 2 Hz"){setSliderValue("ctrl-rhythmic-pulse",2.0)}
            action(c,"THETA · 6 Hz"){setSliderValue("ctrl-rhythmic-pulse",6.0)}
            listOf("ctrl-rhythmic-tone","ctrl-rhythmic-depth").forEach{slider(c,it)}
            select(c,"ctrl-rhythmic-shape","SHAPE")
        }
        val output=subCard(mixer,"Final output","Master gain changes every active layer together",Color.rgb(36,97,84))
        slider(output,"ctrl-master-output-db",master=true)
        txt(output,"Audio creation and quality · configuration",10f,cool)
        for(n in listOf(2,4,8,10))action(output,"$n MINUTES"){p.edit().putString("ctrl-duration","$n").apply();showTab("studio")}
        expandable(output,"More durations, quality & safety ⌄",false){c->
            select(c,"ctrl-duration","LOOP DURATION")
            select(c,"ctrl-memory-mode","MEMORY MODE")
            select(c,"ctrl-samplerate","SAMPLE RATE")
            select(c,"ctrl-normalize","PEAK CEILING")
            toggle(c,"ctrl-fixed-output-lift","Fixed Output Lift · OFF")
        }
        txt(mixer,"Affirmations remain stationary and both start at the same time. Only the dedicated 8D Carrier copy moves left/right; generated tones are separate.",10f,cool)
        val layer=section(body,"04 — Shared Affirmation Layer")
        val info=subCard(layer,"◌ ONE LAYER · BOTH AFFIRMATIONS","Primary · Affirmation 1 and Secondary · Affirmation 1 (A) are separate recordings.",Color.rgb(91,66,173))
        txt(info,"Both sources always remain one layer each. They share base speed and shared layer volume.",11f,cool)
        toggle(layer,"ctrl-affirmation-clarity-on","Affirmation Clarity · OFF")
        expandable(layer,"Shared affirmation layer",false){c->
            slider(c,"ctrl-na-layer-speed-1")
            slider(c,"ctrl-na-layer-volume-1")
        }
        val render=section(body,"05 — Render Installation")
        action(render,"▶ GENERATE PURE MASTER","primary"){renderStudio(false)}
        action(render,"▷ PREVIEW 30 SEC"){renderStudio(true)}
        action(render,"■ Cancel Active Render"){startAction(RenderService.ACTION_CANCEL)}
        status=txt(render,"Ready to generate · source audio stays on device",10f,cool,monoFont=true)
        val download=section(body,"✓ Subliminal Complete")
        textEntry(download,"Name your completed file", "subName",p.getString("subName","Subliminal Session")?:"Subliminal Session",single=true)
        action(download,"⬇ DOWNLOAD NAMED WAV","green"){openOutput()}
        action(download,"⬇ DOWNLOAD MANIFEST JSON"){chooseExport(922,"subliminal-manifest.json")}
    }
    private fun applyPreset(key:String){
        val preset=(0 until originalPresets.length()).map{originalPresets.getJSONObject(it)}.firstOrNull{it.getString("id")==key}?:return
        val values=preset.getJSONObject("values")
        val keepMaster=p.getFloat("slider:ctrl-master-output-db",0f)
        applyingPreset=true
        val edit=p.edit()
        for(k in values.keys()){
            when (val v=values.get(k)){
                is Boolean->edit.putBoolean("check:$k",v)
                else -> if(OriginalStudioControlSpec.sliders.any{it.id==k})edit.putFloat("slider:$k",v.toString().toFloatOrNull()?:0f)
                else edit.putString("select:$k",v.toString())
            }
        }
        edit.putFloat("slider:ctrl-master-output-db",keepMaster)
        edit.putString("presetLabel",preset.getString("name").take(60)).apply()
        applyingPreset=false
        showTab("studio")
    }
    private fun source(parent:LinearLayout,title:String,subtitle:String,footer:String,code:Int,key:String){
        imported[code]=key
        val v=panel(parent,fill=surface2,border=Color.rgb(87,71,75),radius=12,padding=14,margin=11)
        txt(v,title,10f,coral,monoFont=true,tracking=.16f)
        space(v,5);txt(v,subtitle,12f,bright,true)
        space(v,4);txt(v,footer,9f,cool)
        val path=p.getString(key,"")?:""
        val file=txt(v,if(path.isNotBlank()&&File(path).exists())File(path).name else "No file selected",10f,cool,monoFont=true)
        sourceLabels[key]=file
        action(v,"▣ SELECT AUDIO FILE"){
            val pick=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="audio/*"}
            @Suppress("DEPRECATION") startActivityForResult(pick,code)
        }
    }
    private fun slider(parent:LinearLayout,id:String,master:Boolean=false){
        val spec=OriginalStudioControlSpec.slider(id)
        val v=OriginalStudioSliderView(this,spec).apply{
            value=p.getFloat("slider:$id",spec.initial.toFloat()).toDouble()
            onUserValueChanged={n->
                Diagnostics.interaction("slider:$id")
                p.edit().putFloat("slider:$id",n.toFloat()).putString("presetLabel","Custom").apply()
                if(id=="ctrl-master-balance-a"||id=="ctrl-master-balance-b")linkBalances()
            }
        }
        slides[id]=v
        val parentToUse=if(master)panel(parent,fill=Color.rgb(13,27,40),border=Color.rgb(75,76,108),radius=13,padding=12,margin=10) else parent
        parentToUse.addView(v,params(d(82)).apply{bottomMargin=d(6)})
    }
    private fun setSliderValue(id:String,v:Double){val spec=OriginalStudioControlSpec.slider(id);val new=spec.clampAndStep(v)
        p.edit().putFloat("slider:$id",new.toFloat()).apply();slides[id]?.value=new}
    private fun linkBalances(){
        val a=p.getFloat("slider:ctrl-master-balance-a",0f).toDouble().coerceIn(-100.0,100.0)
        val b=p.getFloat("slider:ctrl-master-balance-b",0f).toDouble().coerceIn(-100.0,100.0)
        val aff1Pair=if(a>0)100-a else 100.0
        val affAPair=if(a<0)100+a else 100.0
        val carrier=if(b>0)100-b else 100.0
        val affirmations=if(b<0)100+b else 100.0
        fun pctDb(percent:Double)= if(percent<=0)-60.0 else (20*log10(percent/100)).coerceIn(-60.0,0.0)
        setSliderValue("ctrl-vol-carrier",pctDb(carrier));setSliderValue("ctrl-vol-carrier-8d",pctDb(carrier))
        setSliderValue("ctrl-vol1",pctDb(aff1Pair*affirmations/100));setSliderValue("ctrl-vol-noise",pctDb(affAPair*affirmations/100))
    }
    private fun toggle(parent:LinearLayout,id:String,label:String){
        val value=p.getBoolean("check:$id",false)
        val c=CheckBox(this).apply{ text=label;setTextColor(cool);textSize=11f;typeface=mono;buttonTintList=ColorStateList.valueOf(green)
            isChecked=value;setPadding(d(2),d(8),d(2),d(8));setOnCheckedChangeListener{_,on->Diagnostics.interaction("toggle:$id");p.edit().putBoolean("check:$id",on).putString("presetLabel","Custom").apply()}
        }
        parent.addView(c,params());checks[id]=c
    }
    private fun select(parent:LinearLayout,id:String,label:String){
        txt(parent,label,10f,coral,monoFont=true,tracking=.12f)
        val array=originalSelects.getJSONArray(id)
        val labels=mutableListOf<String>();val values=mutableListOf<String>()
        for(i in 0 until array.length()){val item=array.getJSONObject(i);labels+=item.getString("label");values+=item.getString("value")}
        val spinner=Spinner(this)
        val adapter=ArrayAdapter(this,android.R.layout.simple_spinner_item,labels).apply{setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)}
        spinner.adapter=adapter
        val saved=p.getString("select:$id",when(id){"ctrl-duration"->"10";"ctrl-samplerate"->"44100";"ctrl-memory-mode"->"smart";else->values.firstOrNull().orEmpty()})
        spinner.setSelection(values.indexOf(saved).coerceAtLeast(0))
        spinner.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,rowId:Long){Diagnostics.interaction("select:$id");p.edit().putString("select:$id",values[position]).apply()}
        }
        parent.addView(spinner,params(d(48)).apply{bottomMargin=d(8)})
        selects[id]=spinner
    }
    private fun expandable(parent:LinearLayout,title:String,opened:Boolean,fill:(LinearLayout)->Unit){
        val header=action(parent,title){ }
        val content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;pad(this,4,12,4,6)}
        parent.addView(content,params())
        fill(content)
        content.visibility=if(opened)View.VISIBLE else View.GONE
        header.setOnClickListener{content.visibility=if(content.visibility==View.VISIBLE)View.GONE else View.VISIBLE}
    }
    private fun textEntry(parent:LinearLayout,hint:String,key:String,initial:String,single:Boolean=false):EditText{
        val edit=EditText(this).apply{setText(initial);this.hint=hint;textSize=12f;setTextColor(bright);setHintTextColor(cool)
            typeface=mono;setSingleLine(single);setPadding(d(13),d(12),d(13),d(12));background=cardShape(Color.rgb(15,23,31),Color.rgb(62,75,91),9)
        }
        parent.addView(edit,params(if(single)d(48) else d(255)).apply{bottomMargin=d(8)})
        edit.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){p.edit().putString(key,s?.toString() ?: "").apply()}
            override fun afterTextChanged(s:Editable?){}
        });return edit
    }
    private fun saveNamedPreset(){val name=p.getString("namedPreset","")?.trim().orEmpty();if(name.isBlank()){Toast.makeText(this,"Name your preset first",Toast.LENGTH_LONG).show();return}
        val obj=JSONObject();for(slider in OriginalStudioControlSpec.sliders)obj.put(slider.id,p.getFloat("slider:${slider.id}",slider.initial.toFloat()).toDouble())
        val all=JSONObject(p.getString("namedPresets","{}")?:"{}");all.put(name,obj);p.edit().putString("namedPresets",all.toString()).apply();Toast.makeText(this,"Saved $name",Toast.LENGTH_SHORT).show()
    }
    private fun loadNamedPreset(){val name=p.getString("namedPreset","")?.trim().orEmpty();val all=JSONObject(p.getString("namedPresets","{}")?:"{}")
        if(!all.has(name)){Toast.makeText(this,"No preset named $name",Toast.LENGTH_LONG).show();return}
        val saved=all.getJSONObject(name);val e=p.edit();for(key in saved.keys())e.putFloat("slider:$key",saved.getDouble(key).toFloat());e.putString("presetLabel",name).apply();showTab("studio")
    }
    private fun deleteNamedPreset(){val name=p.getString("namedPreset","")?.trim().orEmpty();val all=JSONObject(p.getString("namedPresets","{}")?:"{}")
        all.remove(name);p.edit().putString("namedPresets",all.toString()).apply();Toast.makeText(this,"Deleted $name",Toast.LENGTH_SHORT).show()
    }
    private fun renderStudio(preview:Boolean){
        Diagnostics.stage(if(preview)"PREVIEW_REQUESTED" else "STUDIO_RENDER_REQUESTED")
        if(listOf("carrierStandard","affirmNormal","affirmFrequency").none {p.getString(it,"")?.let{f->File(f).isFile}==true}){
            Toast.makeText(this,"Please load a source recording first",Toast.LENGTH_LONG).show();return
        }
        p.edit().putBoolean("subPreview",preview).apply();startAction(RenderService.ACTION_SUBLIMINAL)
    }
    private fun openOutput(){
        val uri=if(activeTab=="studio")p.getString("subLastFileUri",null) else p.getString("lastFileUri",null)
        if(uri==null){Toast.makeText(this,"Generate a WAV first",Toast.LENGTH_LONG).show();return}
        runCatching{startActivity(Intent(Intent.ACTION_VIEW).apply{setDataAndType(Uri.parse(uri),"audio/wav");addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)})}
            .onFailure{Toast.makeText(this,"Saved in Downloads/SubGen",Toast.LENGTH_LONG).show()}
    }
    private fun chooseExport(code:Int,fileName:String){val i=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json";putExtra(Intent.EXTRA_TITLE,fileName)}
        @Suppress("DEPRECATION")startActivityForResult(i,code)
    }
    private fun chooseJson(code:Int){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="application/json"}
        @Suppress("DEPRECATION")startActivityForResult(i,code)
    }
    private fun buildHypnosis(){
        val b=panel(body,fill=Color.rgb(19,19,34),border=Color.rgb(98,69,157),padding=18)
        txt(b,"HYPNOSIS ENGINE",20f,Color.rgb(200,185,255),true,tracking=.12f,gravity=Gravity.CENTER)
        txt(b,"Original Kokoro neural voices · controlled pauses · WAV rendering.",11f,cool,gravity=Gravity.CENTER)
        space(b,10)
        action(b,"↗ USE HYPNOSIS WAV AS PRIMARY AFFIRMATION","aqua"){
            val file=File(filesDir,"hypnosis-working.wav")
            if(!file.exists())Toast.makeText(this,"Render Hypnosis WAV first",Toast.LENGTH_LONG).show()
            else {p.edit().putString("affirmNormal",file.absolutePath).apply();Toast.makeText(this,"Loaded Hypnosis WAV as Primary",Toast.LENGTH_SHORT).show()}
        }
        val session=hypSection("01 — SESSION & VOICE")
        textEntry(session,"Name your session","name",p.getString("name","My Hypnosis Session")?:"My Hypnosis Session",true)
        txt(session,"KOKORO LOCAL NEURAL VOICES",10f,Color.rgb(190,177,255),monoFont=true,tracking=.18f)
        for(v in KokoroNative.voices.keys){
            val selected=p.getString("voice","SERENA")==v
            val voice=panel(session,fill=if(selected)Color.rgb(16,49,43) else Color.rgb(19,27,39),border=if(selected)green else Color.rgb(77,60,109),radius=9,padding=12,margin=7)
            txt(voice,v,13f,if(selected)Color.rgb(160,255,218) else Color.rgb(232,222,255),true,tracking=.15f)
            txt(voice,when(v){"ORION"->"Masculine · steady neural voice";"ROWAN"->"Masculine · warm neural voice";"LUNA"->"Feminine · deep neural voice";else->"Feminine · natural neural voice"},10f,cool)
            voice.setOnClickListener{p.edit().putString("voice",v).apply();showTab("hypnosis")};voice.isClickable=true
        }
        val speed=panel(session,fill=Color.rgb(21,25,43),border=Color.rgb(88,76,130),padding=12)
        txt(speed,"VOICE SPEED",10f,cool,monoFont=true)
        val bar=SeekBar(this).apply{max=55;progress=((p.getFloat("speed",.84f)-.65f)*100).toInt().coerceIn(0,55);progressTintList=ColorStateList.valueOf(violet);thumbTintList=ColorStateList.valueOf(Color.rgb(173,147,250))}
        speed.addView(bar,params());bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){};override fun onStopTrackingTouch(s:SeekBar){}
            override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){if(user)p.edit().putFloat("speed",.65f+n/100f).apply()}
        })
        val pack=if(ModelInstaller(this).present())"OFFLINE VOICES INSTALLED" else "INSTALL OFFLINE VOICE PACK"
        action(session,pack,"aqua"){startAction(RenderService.ACTION_INSTALL)}
        action(session,"GENERATE VOICE PREVIEW"){startAction(RenderService.ACTION_PREVIEW)}
        action(session,"▶ PLAY VOICE PREVIEW"){playPreview()}
        action(session,"BENCHMARK · AUTO-SELECT 2 OR 4 THREADS"){startAction(RenderService.ACTION_BENCHMARK)}
        val editor=hypSection("02 — HYPNOSIS SCRIPT")
        txt(editor,"[SECTION] · [PAUSE: 5] · [SLOW] · [SOFT] · [WHISPER] · [NORMAL]",10f,cool,monoFont=true)
        val field=textEntry(editor,"Write or paste your hypnosis script","script",p.getString("script","")?:"")
        action(editor,"IMPORT SCRIPT .TXT"){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/plain"};@Suppress("DEPRECATION")startActivityForResult(i,910)}
        val output=hypSection("03 — OUTPUT / RENDER")
        txt(output,"WAV EXPORT · 24 KHz · STEREO · EXACT TIMED PAUSES",10f,cool,monoFont=true)
        action(output,"RENDER HYPNOSIS WAV","primary"){startAction(RenderService.ACTION_RENDER)}
        action(output,"STOP RENDER · KEEP SAVED BLOCKS"){startAction(RenderService.ACTION_CANCEL)}
        action(output,"⬇ OPEN SAVED WAV","green"){openOutput()}
        status=txt(output,"Native Kokoro · Ready",10f,cool,monoFont=true)
        txt(output,"A foreground notification tracks processing. Completed spoken blocks remain saved for later sessions.",10f,cool)
        txt(body,"HYPNOSIS STUDIO v2.3 · Native Android · Kokoro neural engine",9f,cool,monoFont=true,gravity=Gravity.CENTER)
    }
    private fun hypSection(s:String):LinearLayout{
        val pane=panel(body,fill=Color.rgb(23,29,43),border=Color.rgb(79,63,124),radius=12,padding=16,margin=16)
        txt(pane,s,11f,Color.rgb(181,160,255),monoFont=true,tracking=.18f);space(pane,15);return pane
    }
    private fun playPreview(){runCatching{
        val f=File(filesDir,"native-voice-preview.wav");require(f.length()>44){"Generate preview first"}
        val player=android.media.MediaPlayer().apply{setDataSource(f.absolutePath);setOnCompletionListener{it.release()};prepare();start()}
    }.onFailure{Toast.makeText(this,it.message,Toast.LENGTH_LONG).show()}}
    private fun startAction(action:String?){runCatching{
        val i=Intent(this,RenderService::class.java).apply{this.action=action ?: RenderService.ACTION_RENDER}
        if(action==RenderService.ACTION_CANCEL)startService(i) else startForegroundService(i)
        Toast.makeText(this,"Processing in native Android",Toast.LENGTH_SHORT).show()
    }.onFailure{Toast.makeText(this,"Cannot start: ${it.message}",Toast.LENGTH_LONG).show()}}
    @Deprecated("Android document result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK)return;val uri=data?.data?:return
        runCatching{
            when(requestCode){
                923->{contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use{it.write(Diagnostics.report().toString(2))}}
                910->{val script=contentResolver.openInputStream(uri)!!.bufferedReader().use{it.readText()};p.edit().putString("script",script).apply();showTab("hypnosis")}
                920->{val all=JSONObject(p.getString("namedPresets","{}")?:"{}");contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use{it.write(all.toString(2))}}
                922->{val report=JSONObject().apply{
                    put("source","Subliminal Studio Native · v8.0.32 controls")
                    put("status",StateStore.status(this@MainActivity))
                    val sources=JSONObject();for(k in listOf("carrierStandard","affirmNormal","affirmFrequency"))sources.put(k,p.getString(k,"")?.let{path->File(path).name}?:"")
                    put("sources",sources)
                    val control=JSONObject();for(slider in OriginalStudioControlSpec.sliders)control.put(slider.id,p.getFloat("slider:${slider.id}",slider.initial.toFloat()).toDouble())
                    put("sliders",control)
                    put("preset",p.getString("presetLabel","Factory defaults"))
                };contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use{it.write(report.toString(2))}}
                921->{val json=contentResolver.openInputStream(uri)!!.bufferedReader().use{JSONObject(it.readText())};p.edit().putString("namedPresets",json.toString()).apply()}
                else->{val key=imported[requestCode]?:return
                    val dest=File(filesDir,"source-$key.wav");val temp=File(filesDir,"source-$key.tmp")
                    contentResolver.openInputStream(uri)!!.use{src->temp.outputStream().use{src.copyTo(it)}}
                    if(dest.exists())dest.delete();require(temp.renameTo(dest)){"Cannot store source file"}
                    val e=p.edit().putString(key,dest.absolutePath)
                    if(key=="carrierStandard")e.putString("carrier8d",dest.absolutePath)
                    e.apply();sourceLabels[key]?.text="${dest.name} · ${dest.length()/1024} KB"
                }
            }
        }.onFailure{Diagnostics.failure("IMPORT_OR_EXPORT_FAILED",it);Toast.makeText(this,"Import failed: ${it.message}",Toast.LENGTH_LONG).show()}
    }
}
