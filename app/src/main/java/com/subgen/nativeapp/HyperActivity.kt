package com.subgen.nativeapp

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaPlayer
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.*

/** Optional native production tools. Never called by the ordinary Create action. */
class HyperActivity:Activity() {
    private val store by lazy{HyperStore(this)}
    private val prefs by lazy{StateStore.prefs(this)}
    private val worker=Executors.newSingleThreadExecutor()
    private var busy=false
    private var player:MediaPlayer?=null
    private lateinit var body:LinearLayout
    private lateinit var status:TextView
    private var lastAudit:HyperAnalysis.Result?=null
    private var page="Audit"
    private var pendingExport:File?=null
    private val white=Color.rgb(225,229,242)
    private val muted=Color.rgb(161,179,204)
    private val purple=Color.rgb(188,165,255)
    private fun dp(n:Int)=(n*resources.displayMetrics.density).roundToInt()
    private fun source()=prefs.getString("script","")?:""
    override fun onCreate(state:Bundle?){super.onCreate(state);window.statusBarColor=Color.rgb(15,19,24);window.navigationBarColor=Color.rgb(15,19,24);show("Audit")}
    override fun onDestroy(){player?.release();player=null;worker.shutdownNow();super.onDestroy()}
    private fun label(parent:LinearLayout,s:String,size:Float=13f,color:Int=white):TextView=TextView(this).apply{
        text=s;textSize=size;setTextColor(color);setPadding(0,dp(8),0,dp(8));setTextIsSelectable(true)
        parent.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    private fun button(parent:LinearLayout,s:String,run:()->Unit)=Button(this).apply{
        text=s;textSize=11f;isAllCaps=false;setTextColor(white);setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(58,44,80)))
        parent.addView(this,LinearLayout.LayoutParams(-1,-2));setOnClickListener{runCatching{run()}.onFailure{error(it)}}
    }
    private fun field(parent:LinearLayout,hint:String,value:String="",multiline:Boolean=false)=EditText(this).apply{
        this.hint=hint;setText(value);setTextColor(white);setHintTextColor(muted);textSize=13f;setSingleLine(!multiline)
        if(multiline){minLines=5;maxLines=18;gravity=android.view.Gravity.TOP}
        parent.addView(this,LinearLayout.LayoutParams(-1,-2))
    }
    private fun picker(parent:LinearLayout,name:String,values:List<String>,current:String):Spinner{
        label(parent,name,12f,muted)
        return Spinner(this).apply{adapter=ArrayAdapter(this@HyperActivity,android.R.layout.simple_spinner_dropdown_item,values)
            setSelection(values.indexOf(current).coerceAtLeast(0));parent.addView(this,LinearLayout.LayoutParams(-1,dp(48)))}
    }
    private fun error(t:Throwable){if(!isFinishing)AlertDialog.Builder(this).setTitle("Production tools").setMessage(t.message?:t.javaClass.simpleName).setPositiveButton("Close",null).show()}
    private fun background(message:String,work:()->Unit){
        check(!busy){"A production check is already running"};busy=true;status.text=message
        worker.execute{val result=runCatching{work()};runOnUiThread{busy=false;if(!isFinishing){result.onFailure{error(it)};show(page)}}}
    }
    private fun audit():HyperAnalysis.Result=store.analyze(source()).also{lastAudit=it}
    private fun show(name:String){
        page=name
        val scroll=ScrollView(this).apply{setBackgroundColor(Color.rgb(15,19,24))}
        body=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(20),dp(16),dp(40))}
        scroll.addView(body);setContentView(scroll)
        label(body,"HYPER · Production tools",22f,purple)
        label(body,"Optional analysis. Your script and Create → Export workflow stay in Hypnosis. Structure describes a plan; it does not measure listener trance.",12f,muted)
        val nav=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}
        val horizontal=HorizontalScrollView(this);horizontal.addView(nav);body.addView(horizontal)
        for(tab in listOf("Audit","Cues","Timeline","Audio / Loop","Modes / Rules","Reviews / Reports")){
            val b=Button(this).apply{text=tab;textSize=10f;setOnClickListener{if(!busy)show(tab)}};nav.addView(b)
        }
        status=label(body,if(busy)"Check in progress…" else "Local tools · HYPER ${store.rules().version}",12f,muted)
        button(body,"Return to Hypnosis editor"){finish()}
        runCatching{when(name){"Audit"->overview();"Cues"->cues();"Timeline"->timeline();"Audio / Loop"->audio();"Modes / Rules"->configuration();else->reviews()}}.onFailure{label(body,it.message?:"Unable to analyze",13f,Color.rgb(255,155,155))}
    }
    private fun overview(){
        val a=audit();val by=a.occurrences.groupingBy{it.cue.id}.eachCount()
        label(body,"${a.words} words · estimated ${ScriptTimeline.time(a.totalSeconds)} (${ScriptTimeline.time(a.lowerSeconds)}–${ScriptTimeline.time(a.upperSeconds)})",17f,purple)
        label(body,"145 WPM baseline. Programmed silence: ${a.pauses.size} pauses / ${"%.2f".format(a.silenceSeconds)} seconds. Volume does not change duration.")
        label(body,"${a.mode.architecture} · ${a.mode.eyes} eyes · ending ${a.mode.ending} · ${a.mode.focus.joinToString()}\nClassification: ${a.mode.source}")
        for(c in a.cues)label(body,"${c.kind}: ${c.phrase} — ${by[c.id]?:0}",13f,if(c.kind=="SESSION")Color.rgb(91,222,204)else white)
        label(body,"${a.technicalErrors} technical errors · ${a.warnings} editorial warnings",16f,purple)
        button(body,"Save this audit snapshot"){store.saveReport(a,source());Toast.makeText(this,"Audit snapshot saved",Toast.LENGTH_SHORT).show()}
        a.findings.forEach{f->button(body,"${f.severity} · ${f.rule}\n${f.message}"+(f.source?.let{"\nL${it.line}:C${it.column}"}?:"")){finding(a,f)}}
    }
    private fun finding(a:HyperAnalysis.Result,f:HyperAnalysis.Finding){
        val dialog=AlertDialog.Builder(this).setTitle("${f.severity} · ${f.rule}").setMessage(f.message)
            .setPositiveButton("Close",null)
        if(f.source!=null)dialog.setNeutralButton("Go to source"){_,_->navigate(f.source)}
        if(f.allowException)dialog.setNegativeButton("Record exception"){_,_->note("Exception reason for ${f.rule}"){reason->
            store.addReview(HyperGovernance.exception(f,store.identity(a),reason));show(page)}}
        dialog.show()
    }
    private fun navigate(s:ScriptSourceMap.Span){setResult(RESULT_OK,Intent().putExtra("sourceStart",s.start).putExtra("sourceEnd",s.end));finish()}
    private fun note(title:String,run:(String)->Unit){
        val input=EditText(this).apply{hint="Reviewer notes / reason";minLines=3}
        val dialog=AlertDialog.Builder(this).setTitle(title).setView(input).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            runCatching{require(input.text.isNotBlank()){"Enter reviewer notes"};run(input.text.toString());dialog.dismiss()}.onFailure{error(it)}
        }};dialog.show()
    }
    private fun cues(){
        val a=audit();label(body,"Spoken occurrences only. Section names are excluded. Commands can separate cue words; timed pauses stop matching.",12f,muted)
        val length=picker(body,"Context words before and after cue",listOf("100","120","180","250"),store.config().optInt("contextWords",120).toString())
        button(body,"Apply context length"){store.saveConfig(store.config().put("contextWords",length.selectedItem.toString().toInt()));show(page)}
        button(body,"Register / edit a session cue"){cueEditor()}
        for(h in a.occurrences)button(body,"${h.cue.kind} · ${h.cue.phrase} · ~${ScriptTimeline.time(h.at)}\nL${h.source.line}:C${h.source.column} · ${h.section} / ${h.phase}"){cueContext(a,h)}
        label(body,"Session cue registry",17f,purple)
        store.allCues().forEach{c->button(body,"${c.sessionId} · ${c.phrase} · ${if(c.authorized)"AUTHORIZED" else "PENDING"}"){cueEditor(c)}}
    }
    private fun cueEditor(existing:HyperAnalysis.Cue?=null){
        val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),0,dp(12),0)}
        val cfg=store.config();val session=field(layout,"Session ID",existing?.sessionId?:cfg.optString("sessionId"))
        val priorName=store.registry().let{r->(0 until r.length()).map{r.getJSONObject(it)}.firstOrNull{it.optString("sessionId")==existing?.sessionId}?.optString("sessionName")?:""}
        val name=field(layout,"Session name",priorName)
        val phrase=field(layout,"Approved exact spoken cue",existing?.phrase?:"")
        val function=field(layout,"Intended function",existing?.function?:"")
        val experience=field(layout,"Associated experience",existing?.experience?:"")
        val expected=field(layout,"Optional expected count",existing?.expected?.toString()?:"")
        val phases=field(layout,"Optional preferred phases/sections, comma separated",existing?.preferred?.joinToString(",")?:"")
        val approved=CheckBox(this).apply{text="This session-specific cue is explicitly authorized";setTextColor(white);isChecked=existing?.authorized?:false;layout.addView(this)}
        val scroll=ScrollView(this).apply{addView(layout)}
        val dialog=AlertDialog.Builder(this).setTitle("Session-specific cue · no script changes").setView(scroll).setPositiveButton("Save",null).setNegativeButton("Cancel",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{runCatching{
            val exp=expected.text.toString().trim();require(exp.isEmpty()||exp.toIntOrNull()!=null){"Expected usage must be a whole number"}
            val c=HyperAnalysis.Cue(existing?.id?:"SESSION_"+java.util.UUID.randomUUID(),phrase.text.toString().trim(),"SESSION",session.text.toString().trim(),
                function.text.toString().trim(),experience.text.toString().trim(),exp.toIntOrNull(),phases.text.toString().split(',').map{it.trim()}.filter{it.isNotBlank()},approved.isChecked,store.rules().version)
            store.saveCue(c,name.text.toString().trim());store.saveConfig(store.config().put("sessionId",c.sessionId));dialog.dismiss();show(page)
        }.onFailure{error(it)}}};dialog.show()
    }
    private fun cueContext(a:HyperAnalysis.Result,h:HyperAnalysis.Occurrence){
        val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),0,dp(16),dp(16))}
        label(layout,"${h.cue.kind} · L${h.source.line}:C${h.source.column} · ~${ScriptTimeline.time(h.at)}\n${h.section} / ${h.phase}",12f,muted)
        val highlighted=SpannableStringBuilder(h.before+"\n\n"+h.spoken+"\n\n"+h.after)
        val start=h.before.length+2;highlighted.setSpan(BackgroundColorSpan(Color.rgb(91,68,133)),start,start+h.spoken.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        highlighted.setSpan(ForegroundColorSpan(Color.WHITE),start,start+h.spoken.length,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        label(layout,"",14f).text=highlighted
        button(layout,"Go to exact source"){navigate(h.source)}
        val checks=listOf("Preparation present","Correct cue meaning","Meaningful continuation","No contradictory instruction","Reinforcement appropriate","Reviewed / approved")
        val boxes=checks.map{s->CheckBox(this).apply{text=s;setTextColor(white);layout.addView(this)}}
        val notes=field(layout,"Editorial notes",multiline=true)
        button(layout,"Save cue context review"){
            require(notes.text.isNotBlank()){"Record reviewer notes"}
            val summary=checks.indices.joinToString("; "){checks[it]+"="+boxes[it].isChecked}+"; "+notes.text
            val id=store.identity(a);store.addReview(HyperGovernance.Review("CUE:${h.id}",if(boxes.all{it.isChecked})"ACCEPTED" else"NEEDS REVISION",id.textKey,summary,System.currentTimeMillis(),rule="CUE_CONTEXT"))
            Toast.makeText(this,"Context review saved for this script",Toast.LENGTH_SHORT).show()
        }
        val scroll=ScrollView(this).apply{addView(layout)}
        AlertDialog.Builder(this).setTitle(h.cue.phrase).setView(scroll).setPositiveButton("Close",null).show()
    }
    private fun timeline(){
        val a=audit();label(body,"Planned hypnotic structure — editorial storyboard",17f,purple)
        label(body,"Tap the graphic or a passage to navigate to the original script. These are estimated positions and detected labels, not a measurement of listener depth.",12f,muted)
        body.addView(Storyboard(a),LinearLayout.LayoutParams(-1,dp(190)))
        val groups=a.passages.groupBy{it.section to it.role}
        groups.forEach{(key,ps)->label(body,"${key.second}: ${key.first.ifEmpty{"Unlabelled"}} · ~${ScriptTimeline.time(ps.first().at)} · ~${ScriptTimeline.time(ps.sumOf{it.seconds})}",12f,purple)}
        button(body,"Mark first response in a passage"){val ps=a.passages;AlertDialog.Builder(this).setTitle("Choose the first-response passage")
            .setItems(ps.map{it.text.take(90)}.toTypedArray()){_,i->store.saveConfig(store.config().put("firstResponseOffset",ps[i].source.start).put("firstResponseScriptHash",a.scriptHash));show(page)}.show()}
        a.events.forEach{e->button(body,"~${ScriptTimeline.time(e.at)} · ${e.kind} · ${e.detail}"){navigate(e.source)}}
        a.passages.forEach{p->button(body,"~${ScriptTimeline.time(p.at)} · ${p.section} / ${p.phase}\n${p.profile} · ${"%.3f".format(p.speed)}× · ${"%+.1f".format(p.gainDb)} dB\n${p.settingSource}\n${p.text.take(130)}"){navigate(p.source)}}
        label(body,"Programmed pauses",17f,purple)
        a.pauses.forEach{p->button(body,"~${ScriptTimeline.time(p.at)} · ${p.seconds}s · ${p.phase} · L${p.source.line}"){navigate(p.source)}}
    }
    private inner class Storyboard(private val a:HyperAnalysis.Result):View(this@HyperActivity){
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c:Canvas){super.onDraw(c);val w=width.toFloat();val total=a.totalSeconds.coerceAtLeast(1.0)
            paint.color=Color.rgb(34,34,51);c.drawRect(0f,dp(15).toFloat(),w,dp(145).toFloat(),paint)
            for(p in a.passages){paint.color=when(p.role){"INDUCTION"->Color.rgb(84,121,200);"FIRST_RESPONSE"->Color.rgb(91,205,189);"DEEPENER","RENEWED_DEEPENING"->Color.rgb(159,113,229);"OBJECTIVE"->Color.rgb(214,149,73);"INTEGRATION"->Color.rgb(113,173,122);else->Color.rgb(99,108,130)}
                c.drawRect((p.at/total*w).toFloat(),dp(30).toFloat(),((p.at+p.seconds)/total*w).toFloat().coerceAtLeast((p.at/total*w).toFloat()+1),dp(100).toFloat(),paint)}
            paint.color=Color.rgb(210,218,233);a.pauses.forEach{val x=(it.at/total*w).toFloat();c.drawLine(x,dp(105).toFloat(),((it.at+it.seconds)/total*w).toFloat(),dp(105).toFloat(),paint)}
            a.occurrences.forEach{h->paint.color=if(h.cue.kind=="SESSION")Color.CYAN else Color.rgb(255,115,115);val x=(h.at/total*w).toFloat();c.drawCircle(x,dp(125).toFloat(),dp(3).toFloat(),paint)}
            paint.color=white;paint.textSize=dp(12).toFloat();c.drawText("0:00",0f,dp(170).toFloat(),paint);val end=ScriptTimeline.time(a.totalSeconds);c.drawText(end,w-paint.measureText(end),dp(170).toFloat(),paint)
        }
        override fun onTouchEvent(e:MotionEvent):Boolean{if(e.action==MotionEvent.ACTION_UP){val seconds=e.x/width*a.totalSeconds
            a.passages.minByOrNull{abs(it.at-seconds)}?.let{navigate(it.source)};performClick();return true};return true}
        override fun performClick():Boolean{super.performClick();return true}
    }
    private fun audio(){
        label(body,"Measured WAV / Loop QA",18f,purple)
        label(body,"Read-only measurements of the completed WAV. No crossfade, gain correction, silence removal or pacing changes. RMS and zero crossings are limited technical indicators.",12f,muted)
        button(body,"Measure completed WAV"){background("Measuring WAV frames, levels, silence and seam…"){store.measure()}}
        val a=audit();val id=store.identity(a);val qa=store.measurements()
        if(qa!=null){label(body,qa.toString(2),12f);if(id.audioHash.isBlank())label(body,"These measurements are historical or do not match current script/settings. New relevant review is required.",13f,Color.rgb(255,175,120))
            else label(body,"Measured − estimated duration: ${"%+.3f".format(qa.getDouble("measuredDurationSeconds")-a.totalSeconds)} s",14f,purple)}
        val record=store.render();if(record!=null)label(body,"Render evidence:\n${record.toString(2)}",11f,muted)
        button(body,"Play original single cycle"){play(File(filesDir,"hypnosis-final.wav"))}
        fun audition(label:String){button(body,label){background("Preparing original tail → head audition window…"){
            val out=File(cacheDir,"hyper-junction.wav");HyperAudioQa.audition(File(filesDir,"hypnosis-final.wav"),out)
            runOnUiThread{if(!isFinishing)play(out)}
        }}}
        audition("Audition A → B junction (last 2s + first 2s)");audition("Audition B → C junction (same repeated PCM boundary)")
        button(body,"Play exact three-cycle test"){background("Streaming three unmodified cycles to a temporary WAV…"){
            val out=File(cacheDir,"hyper-three-cycle.wav");if(out.exists())out.delete();HyperAudioQa.exportCycles(File(filesDir,"hypnosis-final.wav"),out,stop={Thread.currentThread().isInterrupted})
            runOnUiThread{if(!isFinishing)play(out)}
        }}
        button(body,"Export three-cycle QA WAV"){background("Preparing three-cycle export; original remains available…"){
            val out=File(cacheDir,"hyper-three-cycle-export.wav");if(out.exists())out.delete();HyperAudioQa.exportCycles(File(filesDir,"hypnosis-final.wav"),out,stop={Thread.currentThread().isInterrupted})
            runOnUiThread{if(!isFinishing)export(out,"audio/wav")}
        }}
        button(body,"Stop playback"){player?.release();player=null}
        label(body,"Manual seam approval is recorded under Reviews / Reports. A technically smooth waveform does not verify the narrative junction.",12f,muted)
    }
    private fun play(file:File){require(file.isFile){"Render or prepare audio first"};player?.release();player=MediaPlayer().apply{
        setDataSource(file.absolutePath);setOnCompletionListener{it.release();player=null};prepare();start()}}
    private fun configuration(){
        label(body,"Optional recording classification",18f,purple)
        val cfg=store.config();val m=cfg.optJSONObject("mode")?.let{HyperJson.mode(it)}?:HyperAnalysis.Mode()
        val architecture=picker(body,"Primary architecture",listOf("UNRESOLVED","FOUNDATION","DEEP","LOOP","BOOSTER","SLEEP"),m.architecture)
        val eyes=picker(body,"Eye state",listOf("UNRESOLVED","OPEN","CLOSED"),m.eyes)
        val ending=picker(body,"Ending",listOf("UNRESOLVED","WAKE","SLEEP","LOOP"),m.ending)
        val familiarity=picker(body,"Listener familiarity",listOf("UNRESOLVED","NEW","FAMILIAR"),m.familiarity)
        val focus=listOf("BODY","MIND","AMPLIFIER").map{x->CheckBox(this).apply{text=x;setTextColor(white);isChecked=x in m.focus;body.addView(this)}}
        val session=field(body,"Session ID for cue registry",cfg.optString("sessionId"))
        button(body,"Save optional classification"){
            val mode=HyperAnalysis.Mode(architecture.selectedItem.toString(),eyes.selectedItem.toString(),ending.selectedItem.toString(),focus.filter{it.isChecked}.map{it.text.toString()}.toSet(),familiarity.selectedItem.toString(),"USER")
            store.saveConfig(store.config().put("mode",HyperJson.mode(mode)).put("sessionId",session.text.toString().trim()));show(page)
        }
        label(body,"Recording categories can overlap: Foundation / Trigger Installation; Deep Eyes-Closed; Deep Eyes-Open; Continuous Eyes-Open Loop; Continuous Eyes-Closed Loop; Rapid / Booster; Body-Focused; Mind-Focused; Hypnosis / Affirmation Amplifier; Natural Sleep.",12f,muted)
        label(body,"Versioned rule packs",18f,purple)
        val packs=store.availableRules();val selected=packs.indexOfFirst{it.second==cfg.optString("activeRulePack")}.coerceAtLeast(0)
        val pack=picker(body,"Select retained standard",packs.map{it.first},packs[selected].first)
        button(body,"Select pack and re-audit"){store.saveConfig(store.config().put("activeRulePack",packs[pack.selectedItemPosition].second));show("Audit")}
        button(body,"Edit / import rule JSON"){
            val edit=EditText(this).apply{setText(store.rulesRaw());minLines=10;maxLines=22;textSize=11f}
            val dialog=AlertDialog.Builder(this).setTitle("Store as a new immutable rule pack").setView(edit).setPositiveButton("Validate and save",null).setNegativeButton("Cancel",null).create()
            dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{runCatching{store.installRules(edit.text.toString());dialog.dismiss();show(page)}.onFailure{error(it)}}};dialog.show()
        }
        label(body,"Rule updates never rewrite historical audit snapshots. Production convenience presets and voice-specific calibration are deferred.",12f,muted)
    }
    private fun reviews(){
        val a=audit();val id=store.identity(a);val rs=store.reviews();val s=HyperGovernance.state(id,rs,a.technicalErrors,a.warnings,a.mode.ending=="LOOP")
        label(body,"${s.technical}\n${s.editorial} · ${s.audio}\n${s.listener} · ${s.release}",17f,purple)
        label(body,"Approvals bind exact script, rules, mode, session cues and acoustic settings. Audio reviews also bind render configuration, WAV hash and a unique render ID.",12f,muted)
        val items=listOf("Induction quality","Meaningful first response","Distinct deepeners","Sustained hypnotic development","Cue preparation","Cue continuation","Objective coherence","Sensory / cognitive immersion","Voice and pacing","Appropriate ending","Loop continuity","Listener comfort")
        items.forEach{item->button(body,"Review · $item"){
            AlertDialog.Builder(this).setTitle(item).setItems(arrayOf("Accepted","Needs revision","Not applicable","Exception approved")){_,choice->
                if(choice==3){val findings=a.findings.filter{it.allowException};AlertDialog.Builder(this).setTitle("Select the relevant rule")
                    .setItems(findings.map{it.rule+": "+it.message.take(60)}.toTypedArray()){_,i->finding(a,findings[i])}.show()}
                else note(item){notes->store.addReview(HyperGovernance.Review("EDITORIAL:$item",listOf("ACCEPTED","NEEDS REVISION","NOT APPLICABLE")[choice],id.textKey,notes,System.currentTimeMillis()));show(page)}
            }.show()
        }}
        for(scope in listOf("TEXT","AUDIO","LISTENER","RELEASE"))button(body,"Record ${scope.lowercase()} approval"){
            fun approve(loopListened:Boolean){note("$scope review notes"){notes->
                // Recompute bindings at the moment of approval; stale displays never carry approval forward.
                val fresh=audit();val identity=store.identity(fresh)
                if(scope!="TEXT")require(identity.audioHash.isNotEmpty()){"Measure the completed WAV for these exact materials first"}
                store.addReview(HyperGovernance.approve(scope,identity,store.reviews(),fresh.technicalErrors,fresh.warnings,fresh.mode.ending=="LOOP",notes,loopListened));show(page)
            }}
            if(scope=="AUDIO"&&a.mode.ending=="LOOP")AlertDialog.Builder(this).setTitle("Manual loop listening")
                .setMessage("Record only after you have listened to both repeated junctions and reviewed narrative continuity.")
                .setPositiveButton("I listened and accept both"){_,_->approve(true)}.setNegativeButton("Cancel",null).show()
            else approve(false)
        }
        button(body,"Export JSON production report"){val pair=store.saveReport(a,source());export(pair.first,"application/json")}
        button(body,"Export Markdown production report"){val pair=store.saveReport(a,source());export(pair.second,"text/markdown")}
        val history=File(store.directory,"reports").listFiles().orEmpty().filter{it.extension=="json"}.sortedByDescending{it.lastModified()}
        button(body,"Historical audit snapshots (${history.size})"){
            AlertDialog.Builder(this).setTitle("Immutable historical results").setItems(history.map{it.name}.toTypedArray()){_,i->
                val report=JSONObject(history[i].readText());val view=TextView(this).apply{text=HyperJson.markdown(report);setPadding(dp(14),dp(14),dp(14),dp(14));textSize=12f;setTextIsSelectable(true)}
                val scroll=ScrollView(this).apply{addView(view)};AlertDialog.Builder(this).setTitle(history[i].name).setView(scroll).setPositiveButton("Close",null).show()
            }.show()
        }
        label(body,"Review history (current bindings plus preserved historical records)",16f,purple)
        rs.takeLast(30).forEach{r->label(body,"${r.scope} · ${r.status} · ${r.binding.take(12)}\n${r.note}",12f,muted)}
    }
    private fun export(file:File,mime:String){pendingExport=file;val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).apply{addCategory(Intent.CATEGORY_OPENABLE);type=mime;putExtra(Intent.EXTRA_TITLE,file.name)}
        @Suppress("DEPRECATION")startActivityForResult(intent,941)}
    @Deprecated("Native document result API")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){super.onActivityResult(requestCode,resultCode,data)
        if(requestCode!=941||resultCode!=RESULT_OK)return;val uri=data?.data?:return;val file=pendingExport?:return
        background("Exporting QA evidence…"){contentResolver.openOutputStream(uri,"wt")!!.use{out->file.inputStream().buffered(131072).use{it.copyTo(out,131072)}}}
    }
}
