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
import android.media.MediaPlayer
import android.media.MediaMetadataRetriever
import android.provider.DocumentsContract
import android.provider.OpenableColumns
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
    private lateinit var tabNeuro:TextView
    private var neuroStatusText:TextView?=null
    private var neuroPlayer:MediaPlayer?=null
    private var neuroOutputVisible=false
    private var neuroLastRefreshToken=""
    private lateinit var status:TextView
    private var activeTab="studio"
    private var applyingPreset=false
    private var selectedPreset="custom"
    private var refreshHyp: (() -> Unit)?=null
    private var hypScriptField:EditText?=null
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
    override fun onDestroy(){handler.removeCallbacksAndMessages(null);neuroPlayer?.release();neuroPlayer=null;super.onDestroy()}
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
        tabNeuro=tab("≈ NEUROMOD")
        tabStudio.setOnClickListener{showTab("studio")}
        tabHypnosis.setOnClickListener{showTab("hypnosis")}
        tabNeuro.setOnClickListener{showTab("neuro")}
        space(header,26)
    }
    private fun showTab(name:String){activeTab=name;refreshHyp=null;neuroStatusText=null;neuroOutputVisible=false;body.removeAllViews();slides.clear();checks.clear();selects.clear();sourceLabels.clear()
        val onBg=cardShape(Color.rgb(52,26,34),Color.rgb(155,33,39),8)
        tabStudio.background=if(name=="studio")onBg else null
        tabHypnosis.background=if(name=="hypnosis")cardShape(Color.rgb(52,26,34),Color.rgb(155,33,39),8) else null
        tabNeuro.background=if(name=="neuro")cardShape(Color.rgb(52,26,34),Color.rgb(155,33,39),8) else null
        tabStudio.setTextColor(if(name=="studio")coral else cool)
        tabHypnosis.setTextColor(if(name=="hypnosis")coral else cool)
        tabNeuro.setTextColor(if(name=="neuro")coral else cool)
        when(name){"studio"->buildStudio();"neuro"->buildNeuro();else->buildHypnosis()}
    }
    private fun refreshStatus(){
        if(::status.isInitialized)status.text=StateStore.status(this)
        if(activeTab=="neuro"){
            neuroStatusText?.text=p.getString("neuro:status","Ready for spoken audio")
            val mode=p.getString("neuro:mode","A")?:"A"
            val token=listOf(mode,listOf("A","B").joinToString("|"){k->
                "${k}:${p.getBoolean("neuro:verified:$k",false)}"},p.getBoolean("neuro:active",false).toString()).joinToString("/")
            if(token!=neuroLastRefreshToken){neuroLastRefreshToken=token
                if(!p.getBoolean("neuro:active",false)){
                    val exists=if(mode=="AB") listOf("A","B").all{p.getBoolean("neuro:verified:$it",false)}
                        else p.getBoolean("neuro:verified:$mode",false)
                    if(exists!=neuroOutputVisible)showTab("neuro")
                }
            }
        }
    }
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
            chooseAudio(code)
        }
    }
    private fun chooseAudio(code:Int){
        val pick=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE)
            type="audio/*"
            // Start in the system Audio category, regardless of the last folder visited.
            // If unavailable, the system picker falls back to its default location.
            putExtra(DocumentsContract.EXTRA_INITIAL_URI,
                DocumentsContract.buildRootUri("com.android.providers.media.documents","audio_root"))
        }
        @Suppress("DEPRECATION") startActivityForResult(pick,code)
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
    private fun buildNeuro(){
        val chosen=p.getString("neuro:mode","A")?:"A"
        val intro=panel(body,fill=Color.rgb(24,18,27),border=Color.rgb(130,36,54),padding=18)
        txt(intro,"NEUROMODULATION",21f,coral,true,tracking=.13f,gravity=Gravity.CENTER)
        txt(intro,"Experiment 001 · Native A + Original B OFDM/QAM",10f,cool,gravity=Gravity.CENTER)
        txt(intro,"All three modes are selectable. Settings remain available before a recording is loaded; exporting requires a completed verified render.",10f,cool)
        val input=section(body,"01 — Original Spoken Affirmations")
        val sourceFile=p.getString("neuro:input",null)?.let(::File)
        txt(input,if(sourceFile?.isFile==true)"SOURCE · ${sourceFile.name} · ${sourceFile.length()/1024} KB" else "No spoken recording loaded · Choose source below",11f,bright)
        action(input,"▣ CHOOSE / REPLACE AFFIRMATION AUDIO"){
            chooseAudio(930)
        }
        txt(input,"Local WAV, MP3, M4A or FLAC when the device supports decoding. Original input is kept intact. Processing normalizes to 24 kHz mono PCM16.",10f,cool)

        val modes=section(body,"02 — Select Original Experiment Method")
        for((mode,label,desc) in listOf(
            Triple("A","VERSION A · DIRECT AM","Mono 96 kHz PCM32 protected master · exact normalized speech recovery"),
            Triple("B","VERSION B · OFDM/QAM","Original 3-tone predictor + XZ correction, stereo 2048 OFDM / 22-bit QAM"),
            Triple("AB","GENERATE A + B","Two independent verified masters and separate mixer editions"))){
            action(modes,(if(chosen==mode)"● SELECTED · " else "○ CHOOSE · ")+label){
                p.edit().putString("neuro:mode",mode).apply();showTab("neuro")
            }
            txt(modes,desc,10f,if(chosen==mode)green else cool)
        }
        val settings=section(body,"03 — Experiment Settings")
        txt(settings,"ORIGINAL REFERENCE · 60 seconds · 40 Hz · 90% amplitude modulation",11f,bright,true)
        action(settings,"60 SECONDS · RESET DURATION TO ORIGINAL"){
            p.edit().putString("neuro:seconds","60").apply();showTab("neuro")
        }
        action(settings,"MATCH SPOKEN SOURCE LENGTH"){
            val f=p.getString("neuro:input",null)?.let(::File)
            if(f?.isFile!=true){Toast.makeText(this,"Choose your recording first",Toast.LENGTH_LONG).show();return@action}
            runCatching{
                val duration=runCatching{val m=NeuroModulationA.inspect(f);m.frames.toDouble()/m.rate}.getOrElse{
                    val m=MediaMetadataRetriever();try{m.setDataSource(f.absolutePath);(m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull()?:error("Unknown source duration"))/1000.0}finally{m.release()}
                }
                require(duration in 1.0..180.0){"Source exceeds the 180-second output limit. Use a shorter recording."}
                p.edit().putString("neuro:seconds",String.format(Locale.US,"%.3f",duration)).apply();showTab("neuro")
            }.onFailure{Toast.makeText(this,it.message,Toast.LENGTH_LONG).show()}
        }
        val seconds=textEntry(settings,"Output duration (seconds 1–180)","neuro:seconds",p.getString("neuro:seconds","60")?:"60",single=true)
        seconds.inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        txt(settings,"B requires enough OFDM capacity for the compressed speech. A requires enough output sample positions for each normalized input sample. Neither truncates silently.",10f,cool)
        val custom=CheckBox(this).apply{text="CUSTOM FREQUENCY / DEPTH (off = reference values)";textSize=12f;setTextColor(bright);buttonTintList=ColorStateList.valueOf(coral);isChecked=p.getBoolean("neuro:custom",false)}
        settings.addView(custom,params())
        val hzLabel=txt(settings,"AM FREQUENCY · ${p.getFloat("neuro:hz",40f)} Hz",11f,coral,true)
        val hz=SeekBar(this).apply{max=199;progress=((p.getFloat("neuro:hz",40f)-.5f)*2).roundToInt().coerceIn(0,199);isEnabled=true}
        settings.addView(hz,params(d(48)))
        val depthLabel=txt(settings,"AM DEPTH · ${p.getInt("neuro:depth",90)}%",11f,coral,true)
        val depth=SeekBar(this).apply{max=98;progress=p.getInt("neuro:depth",90).coerceIn(0,98);isEnabled=true}
        settings.addView(depth,params(d(48)))
        // Sliders stay interactive. Touching either selects custom mode automatically.
        hz.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(bar:SeekBar?,n:Int,user:Boolean){val v=.5f+n*.5f;hzLabel.text="AM FREQUENCY · $v Hz";if(user){p.edit().putFloat("neuro:hz",v).apply();custom.isChecked=true}}
            override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){}
        })
        depth.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onProgressChanged(bar:SeekBar?,n:Int,user:Boolean){depthLabel.text="AM DEPTH · $n%";if(user){p.edit().putInt("neuro:depth",n).apply();custom.isChecked=true}}
            override fun onStartTrackingTouch(bar:SeekBar?){};override fun onStopTrackingTouch(bar:SeekBar?){}
        })
        custom.setOnCheckedChangeListener{_,enabled->p.edit().putBoolean("neuro:custom",enabled).apply()}
        action(settings,"RESTORE REFERENCE 40 Hz / 90%"){
            p.edit().putBoolean("neuro:custom",false).putFloat("neuro:hz",40f).putInt("neuro:depth",90).apply();showTab("neuro")
        }

        val run=section(body,"04 — Generate & Verify")
        neuroStatusText=txt(run,p.getString("neuro:status","Ready for spoken audio")?:"Ready for spoken audio",11f,green,true)
        action(run,"▶ GENERATE ${if(chosen=="AB")"BOTH A + B" else "VERSION $chosen"}","primary"){
            if(p.getBoolean("neuro:active",false)){
                Toast.makeText(this,"A NeuroModulation render is already running",Toast.LENGTH_LONG).show();return@action
            }
            if(p.getString("neuro:input",null)?.let(::File)?.isFile!=true){Toast.makeText(this,"Choose a recording in Section 01 first",Toast.LENGTH_LONG).show();return@action}
            val duration=seconds.text.toString().toDoubleOrNull()
            val customOn=custom.isChecked
            val frequency=if(customOn).5+hz.progress*.5 else 40.0
            val amount=if(customOn)depth.progress/100.0 else .9
            val validation=runCatching{NeuroModulationA.Config(duration?:Double.NaN,frequency,amount).checked()}
            if(validation.isFailure){Toast.makeText(this,validation.exceptionOrNull()?.message,Toast.LENGTH_LONG).show();return@action}
            p.edit().putString("neuro:status","Preparing $chosen · generating and verifying…").apply()
            val i=Intent(this,NeuroModulationService::class.java).apply{
                action=NeuroModulationService.ACTION_GENERATE
                putExtra("mode",chosen);putExtra("duration",duration!!);putExtra("frequency",frequency);putExtra("depth",amount)
            }
            runCatching{startForegroundService(i)}.onFailure{Toast.makeText(this,it.message,Toast.LENGTH_LONG).show()}
        }
        action(run,"■ CANCEL GENERATION"){
            if(p.getBoolean("neuro:active",false))startService(Intent(this,NeuroModulationService::class.java).apply{action=NeuroModulationService.ACTION_CANCEL})
            else Toast.makeText(this,"No generation is currently running",Toast.LENGTH_SHORT).show()
        }
        action(run,"↻ REFRESH OUTPUT STATUS"){showTab("neuro")}
        txt(run,"Verification checks unchanged protected PCM32 masters. Preview and mixer editions are not digitally reversible.",10f,cool)

        val out=section(body,"05 — Export, Audition & Studio Transfer")
        fun ready(m:String)=p.getBoolean("neuro:verified:$m",false) &&
            p.getString("neuro:master:$m",null)?.let{File(it).isFile}==true &&
            p.getString("neuro:mixer:$m",null)?.let{File(it).isFile}==true
        val rendered=if(chosen=="AB")listOf("A","B") else listOf(chosen)
        neuroOutputVisible=rendered.all{ready(it)}
        txt(out,if(neuroOutputVisible)"✓ VERIFIED OUTPUT READY" else "Output controls remain visible. Generate and verify ${rendered.joinToString(" + ")} first.",11f,if(neuroOutputVisible)green else cool,true)
        for(m in rendered){
            txt(out,"VERSION $m · ${if(ready(m))"SOURCE RECOVERY VERIFIED" else "NOT YET VERIFIED"}",11f,if(ready(m))green else cool,true)
            val desc=if(m=="A")"96 kHz · mono · protected PCM32" else "96 kHz · stereo · protected PCM32"
            txt(out,"Master: $desc · never send protected master to Studio",9f,cool)
            fun requireReady():Boolean{
                if(ready(m))return true
                Toast.makeText(this,"Version $m must finish and pass recovery verification before exporting",Toast.LENGTH_LONG).show()
                return false
            }
            action(out,"↓ SAVE VERSION $m · PROTECTED PCM32 MASTER"){
                if(requireReady())neuroChooseExport(if(m=="A")931 else 935,"EXP_001_${m}_Protected_96k_PCM32.wav")
            }
            action(out,"↓ SAVE VERSION $m · MIXER PCM16 EDITION"){
                if(requireReady())neuroChooseExport(if(m=="A")932 else 936,"EXP_001_${m}_Listening_96k_PCM16.wav")
            }
            action(out,"↓ SAVE VERSION $m · VERIFICATION MANIFEST"){
                if(requireReady())chooseExport(if(m=="A")934 else 937,"EXP_001_${m}_Verification.json")
            }
            action(out,"▶ PLAY VERSION $m · MIXER EDITION"){
                if(requireReady())runCatching{
                    neuroPlayer?.release();neuroPlayer=MediaPlayer().apply{
                        setDataSource(p.getString("neuro:mixer:$m","")!!);prepare();start()
                        setOnCompletionListener{it.release();if(neuroPlayer===it)neuroPlayer=null}
                    }
                }.onFailure{Toast.makeText(this,"Playback unavailable: ${it.message}",Toast.LENGTH_LONG).show()}
            }
            action(out,"↗ SEND VERSION $m → STUDIO SECONDARY","aqua"){
                if(requireReady()){
                    p.edit().putString("affirmFrequency",p.getString("neuro:mixer:$m",null)).apply();showTab("studio")
                }
            }
            action(out,"↗ SEND VERSION $m → STUDIO PRIMARY","aqua"){
                if(requireReady()){
                    p.edit().putString("affirmNormal",p.getString("neuro:mixer:$m",null)).apply();showTab("studio")
                }
            }
        }
        action(out,"■ STOP AUDIO PREVIEW"){
            runCatching{neuroPlayer?.stop();neuroPlayer?.release()};neuroPlayer=null
        }
        action(out,"↓ SAVE NORMALIZED 24 kHz SOURCE"){
            val f=p.getString("neuro:normalized",null)?.let(::File)
            if(f?.isFile==true)neuroChooseExport(933,"EXP_001_Normalized_24k_PCM16.wav")
            else Toast.makeText(this,"Generate a version first",Toast.LENGTH_LONG).show()
        }
        txt(out,"Protected PCM32 data remains reversible only when unchanged. Mixing, PCM16 conversion, resampling, streaming or headphone playback voids the digital recovery guarantee.",10f,cool)
    }
    private fun neuroChooseExport(code:Int,name:String){
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{
            addCategory(Intent.CATEGORY_OPENABLE);type="audio/wav";putExtra(Intent.EXTRA_TITLE,name)
        }
        @Suppress("DEPRECATION") startActivityForResult(intent,code)
    }
    private fun buildHypnosis(){
        val b=panel(body,fill=Color.rgb(19,19,34),border=Color.rgb(98,69,157),padding=18)
        txt(b,"HYPNOSIS ENGINE",20f,Color.rgb(200,185,255),true,tracking=.12f,gravity=Gravity.CENTER)
        txt(b,"Original Kokoro neural voices · controlled pauses · WAV rendering.",11f,cool,gravity=Gravity.CENTER)
        space(b,10)
        action(b,"↗ USE HYPNOSIS WAV AS PRIMARY AFFIRMATION","aqua"){
            val file=File(filesDir,"hypnosis-final.wav")
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
        val speedValue=txt(speed,"VOICE SPEED · 1.00×",11f,Color.rgb(225,213,255),monoFont=true)
        val currentVoiceSpeed=p.getFloat("speed",VoiceSpeed.DEFAULT).coerceIn(VoiceSpeed.MIN,VoiceSpeed.MAX)
        speedValue.text=VoiceSpeed.label(currentVoiceSpeed)
        val bar=SeekBar(this).apply{
            max=VoiceSpeed.STEPS
            progress=VoiceSpeed.position(currentVoiceSpeed)
            progressTintList=ColorStateList.valueOf(violet)
            thumbTintList=ColorStateList.valueOf(Color.rgb(173,147,250))
        }
        speed.addView(bar,params())
        txt(speed,"0.65×  ·  1.00× NORMAL  ·  1.20×",9f,cool,monoFont=true)
        bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){}
            override fun onStopTrackingTouch(s:SeekBar){}
            override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){
                val selected=VoiceSpeed.fromPosition(n)
                speedValue.text=VoiceSpeed.label(selected)
                if(user){
                    p.edit().putFloat("speed",selected).apply()
                    refreshHyp?.invoke()
                }
            }
        })
        action(speed,"↺ RESET VOICE SPEED · 1.00×"){
            bar.progress=VoiceSpeed.position(VoiceSpeed.DEFAULT)
            speedValue.text=VoiceSpeed.label(VoiceSpeed.DEFAULT)
            p.edit().putFloat("speed",VoiceSpeed.DEFAULT).apply()
            refreshHyp?.invoke()
        }
        val pack=if(ModelInstaller(this).present())"OFFLINE VOICES INSTALLED" else "INSTALL OFFLINE VOICE PACK"
        action(session,pack,"aqua"){startAction(RenderService.ACTION_INSTALL)}
        action(session,"BENCHMARK · FIND FASTEST CPU SETTING"){startAction(RenderService.ACTION_BENCHMARK)}
        val directionPanel=hypSection("VOICE DIRECTION · OPTIONAL")
        txt(directionPanel,"HYPER personality is a WRITING LABEL. Kokoro supports speed; section softness is gain/rate approximation, not neural emotion or true whisper. A script [profile ...] overrides this UI default; [phase ...] selects the acoustic category independently of [SECTION: NAME].",10f,cool)
        val directions=CheckBox(this).apply {
            text="ENABLE VOICE DIRECTION FOR THIS PROJECT"
            setTextColor(bright);isChecked=p.getBoolean("dir:enabled",false)
            setOnCheckedChangeListener{_,checked->
                p.edit().putBoolean("dir:enabled",checked).apply();refreshHyp?.invoke()
            }
        }
        directionPanel.addView(directions,params())
        txt(directionPanel,"HYPNOTIST PROFILE · AUTHORING LABEL + ACOUSTIC APPROXIMATIONS",10f,cool,monoFont=true)
        val profile=Spinner(this).apply{
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,VoiceDirection.profiles).apply{
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(VoiceDirection.profiles.indexOf(p.getString("dir:profile","MASTER HYPNOTIST")).coerceAtLeast(0))
            onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){
                    p.edit().putString("dir:profile",VoiceDirection.profiles[position]).apply()
                    refreshHyp?.invoke()
                }
            }
        }
        directionPanel.addView(profile,params(d(48)))
        val gainLabel=txt(directionPanel,"GLOBAL DIRECTION GAIN · +0.0 dB",10f,Color.rgb(201,187,255),monoFont=true)
        val globalGain=SeekBar(this).apply{
            max=120;progress=((p.getFloat("dir:gain",0f)+12f)*10).roundToInt().coerceIn(0,120)
            progressTintList=ColorStateList.valueOf(violet)
            thumbTintList=ColorStateList.valueOf(Color.rgb(173,147,250))
        }
        directionPanel.addView(globalGain,params())
        globalGain.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){}
            override fun onStopTrackingTouch(s:SeekBar){}
            override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){
                val db=n/10f-12f
                gainLabel.text=String.format(Locale.US,"GLOBAL DIRECTION GAIN · %+.1f dB",db)
                if(user){p.edit().putFloat("dir:gain",db).apply();refreshHyp?.invoke()}
            }
        })
        gainLabel.text=String.format(Locale.US,"GLOBAL DIRECTION GAIN · %+.1f dB",p.getFloat("dir:gain",0f))
        txt(directionPanel,"SECTION DEFAULTS · select a section, then customize rate/gain",10f,cool,monoFont=true)
        val sectionSelector=Spinner(this).apply{
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,VoiceDirection.sections).apply{
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        directionPanel.addView(sectionSelector,params(d(48)))
        val sectionTempoValue=txt(directionPanel,"SECTION RATE",10f,cool,monoFont=true)
        val sectionTempoBar=SeekBar(this).apply{max=65;progress=25;progressTintList=ColorStateList.valueOf(violet)}
        directionPanel.addView(sectionTempoBar,params())
        val sectionGainValue=txt(directionPanel,"SECTION GAIN",10f,cool,monoFont=true)
        val sectionGainBar=SeekBar(this).apply{max=150;progress=120;progressTintList=ColorStateList.valueOf(violet)}
        directionPanel.addView(sectionGainBar,params())
        fun currentSection():String=VoiceDirection.sections[sectionSelector.selectedItemPosition.coerceIn(0,VoiceDirection.sections.lastIndex)]
        fun refreshRule(){
            val section=currentSection()
            val cfg=VoiceDirectionStore.read(p)
            val rule=cfg.sectionRules[section]?:VoiceDirection.SectionRule()
            sectionTempoBar.progress=((rule.tempo-.75)*100).roundToInt().coerceIn(0,65)
            sectionGainBar.progress=((rule.gainDb+12.0)*10).roundToInt().coerceIn(0,150)
            sectionTempoValue.text=String.format(Locale.US,"%s RATE · %.2fx",section,rule.tempo)
            sectionGainValue.text=String.format(Locale.US,"%s GAIN · %+.1f dB",section,rule.gainDb)
        }
        sectionSelector.onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
            override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
            override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){refreshRule()}
        }
        sectionTempoBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){}
            override fun onStopTrackingTouch(s:SeekBar){}
            override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){
                val value=.75f+n/100f
                sectionTempoValue.text=String.format(Locale.US,"%s RATE · %.2fx",currentSection(),value)
                if(user){p.edit().putFloat("dir:${currentSection()}:tempo",value).apply();refreshHyp?.invoke()}
            }
        })
        sectionGainBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
            override fun onStartTrackingTouch(s:SeekBar){}
            override fun onStopTrackingTouch(s:SeekBar){}
            override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){
                val db=n/10f-12f
                sectionGainValue.text=String.format(Locale.US,"%s GAIN · %+.1f dB",currentSection(),db)
                if(user){p.edit().putFloat("dir:${currentSection()}:gain",db).apply();refreshHyp?.invoke()}
            }
        })
        action(directionPanel,"↺ RESTORE SECTION PROFILE DEFAULTS"){
            p.edit().remove("dir:${currentSection()}:tempo").remove("dir:${currentSection()}:gain").apply()
            refreshRule();refreshHyp?.invoke()
        }
        refreshRule()
        val editor=hypSection("02 — HYPNOSIS SCRIPT")
        txt(editor,"[profile MASTER_HYPNOTIST] · [profile reset] · [phase DEEPENER] · [phase reset] · [SECTION: NAME] · [pause 2] · [SOFT] · [SLOW] · [WHISPER≈] · [NORMAL] · [tempo 0.90] · [volume -2dB]",10f,cool,monoFont=true)
        fun pauseSetting(key:String,title:String,default:Float){
            val value=txt(editor,title,10f,cool,monoFont=true)
            val bar=SeekBar(this).apply{max=120;progress=(p.getFloat(key,default)*2).roundToInt().coerceIn(0,120)
                progressTintList=ColorStateList.valueOf(violet)}
            editor.addView(bar,params())
            fun refresh(){value.text=String.format(Locale.US,"%s · %.1f sec",title,p.getFloat(key,default))}
            bar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener{
                override fun onStartTrackingTouch(s:SeekBar){}
                override fun onStopTrackingTouch(s:SeekBar){}
                override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){
                    value.text=String.format(Locale.US,"%s · %.1f sec",title,n/2f)
                    if(user){p.edit().putFloat(key,n/2f).apply();refreshHyp?.invoke()}
                }
            });refresh()
        }
        pauseSetting("hyp:breatheSec","[breathe] PAUSE",5f)
        pauseSetting("hyp:paragraphSec","[paragraph] PAUSE",3f)
        val field=textEntry(editor,"Write or paste your hypnosis script","script",p.getString("script","")?:"")
        hypScriptField=field
        action(editor,"IMPORT SCRIPT .TXT"){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type="text/plain"};@Suppress("DEPRECATION")startActivityForResult(i,910)}
        val check=hypSection("03 — SCRIPT CHECK + TIMELINE")
        val stats=txt(check,"Analysing script…",11f,Color.rgb(207,194,255),monoFont=true)
        space(check,10)
        val checks=txt(check,"",10f,cool,monoFont=true)
        space(check,10)
        val timeline=panel(check,fill=Color.rgb(17,24,32),border=Color.rgb(76,61,119),radius=11,padding=13,margin=5)
        val previewLines=txt(timeline,"",10f,cool,monoFont=true)
        action(check,"VIEW FULL SCRIPT TIMELINE") {
            val summary=ScriptTimeline.analyze(field.text.toString(),p.getFloat("speed",VoiceSpeed.DEFAULT),breathe=p.getFloat("hyp:breatheSec",5f).toDouble(),paragraph=p.getFloat("hyp:paragraphSec",3f).toDouble(),direction=VoiceDirectionStore.read(p))
            val detail=buildString {
                append("Speech timing is estimated; pauses are exact.\n\n")
                for(row in summary.rows) {
                    append(ScriptTimeline.time(row.seconds))
                    append("  ·  ");append(row.kind);append("  ·  ");append(row.detail);append("\n\n")
                }
            }
            val scroll=ScrollView(this)
            val bodyText=TextView(this).apply {
                text=detail;textSize=12f;typeface=mono;setTextColor(bright);setPadding(d(17),d(15),d(17),d(20))
            }
            scroll.addView(bodyText)
            AlertDialog.Builder(this).setTitle("Script Check + Timeline")
                .setView(scroll).setPositiveButton("CLOSE",null).show()
        }
        val voicePreview=hypSection("04 — NEURAL VOICE PREVIEW")
        txt(voicePreview,"Listen to the selected Kokoro voice before a full render. The preview uses your current voice and speech speed.",10f,cool,monoFont=true)
        textEntry(voicePreview,"Phrase or paragraph to audition","dir:previewText",
            p.getString("dir:previewText","Take a slow, comfortable breath. You can relax now.")?:"Take a slow, comfortable breath. You can relax now.")
        txt(voicePreview,"PREVIEW SECTION · choose matching script section",10f,cool,monoFont=true)
        val previewSection=Spinner(this).apply{
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,VoiceDirection.sections).apply{
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(VoiceDirection.sections.indexOf(p.getString("dir:previewSection","INDUCTION")).coerceAtLeast(0))
            onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long){
                    p.edit().putString("dir:previewSection",VoiceDirection.sections[position]).apply()
                }
            }
        }
        voicePreview.addView(previewSection,params(d(46)))
        action(voicePreview,"▶ GENERATE DIRECTED PHRASE","aqua"){
            p.edit().putBoolean("dir:previewNeutral",false).apply();startAction(RenderService.ACTION_PREVIEW)
        }
        action(voicePreview,"▶ PLAY DIRECTED PHRASE"){playPreview("native-preview-directed.wav")}
        action(voicePreview,"▶ GENERATE NEUTRAL A/B COMPARISON"){
            p.edit().putBoolean("dir:previewNeutral",true).apply();startAction(RenderService.ACTION_PREVIEW)
        }
        action(voicePreview,"▶ PLAY NEUTRAL COMPARISON"){playPreview("native-preview-neutral.wav")}
        val output=hypSection("05 — OUTPUT / RENDER")
        txt(output,"PCM 16-BIT WAV · EXACT PAUSES · LOCAL PROCESSING",10f,cool,monoFont=true)
        space(output,12)
        var updateSummary:(()->Unit)?=null
        txt(output,"OUTPUT SAMPLE RATE",10f,Color.rgb(190,177,255),monoFont=true,tracking=.12f)
        val rates=OutputFormat.rates
        val rateNames=arrayOf("16 kHz","22.05 kHz","24 kHz · Neural native","32 kHz","44.1 kHz","48 kHz")
        val rateSelect=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,rateNames).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            val n=rates.indexOf(p.getInt("hyp:sampleRate",24000))
            setSelection(n.coerceAtLeast(0))
            onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener{
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                    p.edit().putInt("hyp:sampleRate",rates[position]).apply()
                    updateSummary?.invoke()
                }
            }
        }
        output.addView(rateSelect,params(d(46)))
        space(output,12)
        txt(output,"OUTPUT CHANNELS",10f,Color.rgb(190,177,255),monoFont=true,tracking=.12f)
        val channelSelect=Spinner(this).apply {
            adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_item,arrayOf("MONO · Smaller WAV","STEREO · Binaural compatible")).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
            setSelection(if(p.getInt("hyp:channels",2)==1)0 else 1)
            onItemSelectedListener=object:android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent:android.widget.AdapterView<*>?){}
                override fun onItemSelected(parent:android.widget.AdapterView<*>?,view:View?,position:Int,id:Long) {
                    p.edit().putInt("hyp:channels",if(position==0)1 else 2).apply()
                    updateSummary?.invoke()
                }
            }
        }
        output.addView(channelSelect,params(d(46)))
        space(output,12)
        val volumeText=txt(output,"FINAL OUTPUT VOLUME",10f,Color.rgb(190,177,255),monoFont=true)
        val volumeBar=SeekBar(this).apply {
            max=420;progress=((p.getFloat("hyp:outputDb",0f)+30f)*10f).roundToInt().coerceIn(0,420)
            progressTintList=ColorStateList.valueOf(violet)
            thumbTintList=ColorStateList.valueOf(Color.rgb(182,158,255))
        }
        output.addView(volumeBar,params())
        volumeBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(s:SeekBar){}
            override fun onStopTrackingTouch(s:SeekBar){}
            override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean) {
                val db=n/10f-30f
                volumeText.text=String.format(Locale.US,"FINAL OUTPUT VOLUME · %+.1f dB",db)
                if(user)p.edit().putFloat("hyp:outputDb",db).apply()
            }
        })
        volumeText.text=String.format(Locale.US,"FINAL OUTPUT VOLUME · %+.1f dB",p.getFloat("hyp:outputDb",0f))
        val peakText=txt(output,"PEAK CEILING · -1.0 dBFS",10f,Color.rgb(190,177,255),monoFont=true)
        val peakBar=SeekBar(this).apply {
            max=120;progress=((p.getFloat("hyp:peakDb",-1f)+12f)*10f).roundToInt().coerceIn(0,120)
            progressTintList=ColorStateList.valueOf(violet)
            thumbTintList=ColorStateList.valueOf(Color.rgb(182,158,255))
        }
        output.addView(peakBar,params())
        peakBar.setOnSeekBarChangeListener(object:SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(s:SeekBar){}
            override fun onStopTrackingTouch(s:SeekBar){}
            override fun onProgressChanged(s:SeekBar,n:Int,user:Boolean){
                val db=n/10f-12f
                peakText.text=String.format(Locale.US,"PEAK CEILING · %.1f dBFS",db)
                if(user)p.edit().putFloat("hyp:peakDb",db).apply()
            }
        })
        peakText.text=String.format(Locale.US,"PEAK CEILING · %.1f dBFS",p.getFloat("hyp:peakDb",-1f))
        val info=panel(output,fill=Color.rgb(17,24,32),border=Color.rgb(73,68,107),padding=13,margin=10)
        val estimated=txt(info,"ESTIMATED DURATION",10f,Color.rgb(190,177,255),monoFont=true)
        space(info,8)
        val size=txt(info,"ESTIMATED WAV SIZE",10f,Color.rgb(190,177,255),monoFont=true)
        space(info,6)
        val guidance=txt(info,"",10f,cool,monoFont=true)
        updateSummary={
            val summary=ScriptTimeline.analyze(field.text.toString(),p.getFloat("speed",VoiceSpeed.DEFAULT),breathe=p.getFloat("hyp:breatheSec",5f).toDouble(),paragraph=p.getFloat("hyp:paragraphSec",3f).toDouble(),direction=VoiceDirectionStore.read(p))
            stats.text="SPOKEN BLOCKS  "+summary.speechBlocks+
                "\nPAUSE BLOCKS  "+summary.pauseBlocks+
                "\nTOTAL EXACT SILENCE  "+ScriptTimeline.time(summary.silenceSeconds)+
                "\nWORDS  "+summary.words+
                "\nESTIMATED SESSION  "+ScriptTimeline.time(summary.totalSeconds)
            val markerNames=summary.markers.joinToString(" · ")
            val hasMain=summary.markers.any { it.contains("SUGGEST",true) || it.contains("MAIN",true) }
            val unknown=summary.invalidCommands
            checks.text="SECTIONS  "+(if(markerNames.isBlank())"No named sections" else markerNames.take(240))+
                "\nMAIN SUGGESTIONS  "+(if(hasMain)"✓ Marked" else "! No labelled section")+
                "\nUNRECOGNISED TAGS  "+unknown+
                "\nSpeech estimates may change after neural generation."
            previewLines.text=summary.rows.take(9).joinToString("\n\n"){
                ScriptTimeline.time(it.seconds)+"   "+it.kind+"   "+it.detail.take(58)
            }.ifEmpty{"Add a script to see the timeline."}
            val hz=p.getInt("hyp:sampleRate",24000)
            val channels=p.getInt("hyp:channels",2)
            val mb=ScriptTimeline.estimatedWavBytes(summary.totalSeconds,hz,channels)/1e6
            estimated.text="ESTIMATED DURATION  ·  "+ScriptTimeline.time(summary.totalSeconds)+
                " (range "+ScriptTimeline.time(summary.lowerSeconds)+"–"+ScriptTimeline.time(summary.upperSeconds)+")"+
                (if(p.getString("hyp:lastScriptHash","")==StateStore.hash(field.text.toString()) && p.getLong("hyp:lastFinalFrames",0)>0)
                    "\nACTUAL LAST EXPORT · "+ScriptTimeline.time(p.getLong("hyp:lastFinalFrames",0).toDouble()/p.getInt("hyp:lastRate",24000)) else "")
            size.text=String.format(Locale.US,"ESTIMATED WAV SIZE  ·  %.1f MB",mb)
            guidance.text=(if(channels==1 && p.getBoolean("binaural",false))
                "Mono export omits binaural stereo separation.\n" else "")+
                "24 kHz is Kokoro's original voice rate; higher rates do not add new speech detail. "+
                "Output gain stays below the selected peak ceiling. "+
                "WHISPER is speed approximation, not true neural whisper. Invalid codes block rendering."
        }
        refreshHyp=updateSummary
        field.addTextChangedListener(object:TextWatcher{
            override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){}
            override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){updateSummary?.invoke()}
            override fun afterTextChanged(s:Editable?){}
        })
        updateSummary?.invoke()
        action(output,"RENDER HYPNOSIS WAV","primary"){startAction(RenderService.ACTION_RENDER)}
        action(output,"STOP RENDER · KEEP SAVED BLOCKS"){startAction(RenderService.ACTION_CANCEL)}
        action(output,"⬇ OPEN SAVED WAV","green"){openOutput()}
        action(output,"HYPER AUDIT & PRODUCTION TOOLS · OPTIONAL"){
            @Suppress("DEPRECATION")startActivityForResult(Intent(this,HyperActivity::class.java),940)
        }
        status=txt(output,"Native Kokoro · Ready",10f,cool,monoFont=true)
        txt(output,"A foreground notification tracks processing. Completed spoken blocks remain saved for later sessions.",10f,cool)
        txt(body,"HYPNOSIS STUDIO v3.2 · Native Android · Optional HYPER production tools",9f,cool,monoFont=true,gravity=Gravity.CENTER)
    }
    private fun hypSection(s:String):LinearLayout{
        val pane=panel(body,fill=Color.rgb(23,29,43),border=Color.rgb(79,63,124),radius=12,padding=16,margin=16)
        txt(pane,s,11f,Color.rgb(181,160,255),monoFont=true,tracking=.18f);space(pane,15);return pane
    }
    private fun playPreview(filename:String="native-voice-preview.wav"){runCatching{
        val f=File(filesDir,filename);require(f.length()>44){"Generate preview first"}
        val player=android.media.MediaPlayer().apply{setDataSource(f.absolutePath);setOnCompletionListener{it.release()};prepare();start()}
    }.onFailure{Toast.makeText(this,it.message,Toast.LENGTH_LONG).show()}}
    private fun startAction(action:String?){runCatching{
        val i=Intent(this,RenderService::class.java).apply{this.action=action ?: RenderService.ACTION_RENDER}
        if(action==RenderService.ACTION_CANCEL)startService(i) else startForegroundService(i)
        Toast.makeText(this,"Processing in native Android",Toast.LENGTH_SHORT).show()
    }.onFailure{Toast.makeText(this,"Cannot start: ${it.message}",Toast.LENGTH_LONG).show()}}
    @Deprecated("Android document result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
        if(resultCode!=RESULT_OK)return
        if(requestCode==940){
            val field=hypScriptField?:return
            val start=data?.getIntExtra("sourceStart",0)?.coerceIn(0,field.length())?:0
            val end=data?.getIntExtra("sourceEnd",start)?.coerceIn(start,field.length())?:start
            field.requestFocus();field.setSelection(start,end);field.post{field.bringPointIntoView(start)};return
        }
        val uri=data?.data?:return
        runCatching{
            when(requestCode){
                930->{
                    // SAF source kept in private storage. Import is off the UI thread for large recordings.
                    val incoming=uri
                    val name=runCatching{contentResolver.query(incoming,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{cursor->
                        if(cursor.moveToFirst())cursor.getString(0) else null
                    }}.getOrNull()?:"recording.wav"
                    val extension=Regex("\\.[A-Za-z0-9]{2,6}$").find(name)?.value?:".bin"
                    p.edit().putString("neuro:status","Importing original audio…").apply()
                    Thread({
                        runCatching{
                            val dest=File(filesDir,"neuro-source"+extension.lowercase(Locale.US))
                            val tmp=File(filesDir,"neuro-source-upload.partial")
                            contentResolver.openInputStream(incoming)!!.use{src->tmp.outputStream().buffered(65536).use{src.copyTo(it)}}
                            require(tmp.length()>44){"Audio input is empty"}
                            if(dest.exists())require(dest.delete()){"Unable to replace source"}
                            require(tmp.renameTo(dest)){"Could not finish source import"}
                            p.edit().putString("neuro:input",dest.absolutePath).putBoolean("neuro:verified",false)
                                .putBoolean("neuro:verified:A",false).putBoolean("neuro:verified:B",false)
                                .putString("neuro:status","Original spoken recording imported: ${dest.name}").apply()
                        }.onFailure{p.edit().putString("neuro:status","Import error: ${it.message}").apply()}
                        runOnUiThread{if(!isFinishing && activeTab=="neuro")showTab("neuro")}
                    },"neuro-audio-import").start()
                }
                931,932,933,935,936->{
                    val key=when(requestCode){931->"neuro:master:A";932->"neuro:mixer:A";935->"neuro:master:B";936->"neuro:mixer:B";else->"neuro:normalized"}
                    val file=p.getString(key,null)?.let(::File)
                    require(file?.isFile==true){"Protected output not found. Render and verify this version."}
                    p.edit().putString("neuro:status","Saving ${file!!.name} in the background…").apply()
                    Thread({
                        runCatching{
                            contentResolver.openOutputStream(uri,"wt")!!.use{target->
                                file.inputStream().buffered(256*1024).use{src->src.copyTo(target,256*1024)}
                            }
                        }.onSuccess{p.edit().putString("neuro:status","SAVED · ${file.name}").apply()}
                         .onFailure{Diagnostics.failure("NEURO_EXPORT_FAILED",it)
                             p.edit().putString("neuro:status","EXPORT FAILED · ${it.message}").apply()}
                    },"neuro-export").start()
                }
                934,937->{
                    val key=if(requestCode==934)"neuro:manifest:A" else "neuro:manifest:B"
                    val manifest=p.getString(key,null)?:error("No verified manifest")
                    contentResolver.openOutputStream(uri,"wt")!!.bufferedWriter().use{it.write(manifest)}
                }
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
