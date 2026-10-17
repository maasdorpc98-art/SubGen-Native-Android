package com.subgen.nativeapp

import android.app.*
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.*
import android.util.Log
import org.json.*
import java.io.*
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Offline diagnostic recorder: private rolling log, resource samples, crash stacks, export. */
object Diagnostics {
    private const val PREF = "subgen-diagnostic-v1"
    private const val LIMIT_BYTES = 384L * 1024
    private val io = Executors.newSingleThreadExecutor { Thread(it, "SubGen-Diagnostics").apply { isDaemon = true } }
    private val timer = Executors.newSingleThreadScheduledExecutor { Thread(it, "SubGen-Watchdog").apply { isDaemon = true } }
    private val guard = Any()
    private val ring = ArrayDeque<JSONObject>()
    private val sequence = AtomicLong()
    private val pingQueued = AtomicBoolean()
    private val fatal = AtomicBoolean()
    private val main = Handler(Looper.getMainLooper())
    private val session = System.currentTimeMillis().toString(36)
    private val born = SystemClock.elapsedRealtime()
    @Volatile private var ctx: Context? = null
    @Volatile private var job = "none"
    @Volatile private var phase = "idle"
    @Volatile private var lastControl = "none"
    @Volatile private var visible = false
    @Volatile private var lastPing = SystemClock.uptimeMillis()
    @Volatile private var stalled = false
    private var ticks = 0
    fun install(app: Application) {
        if (ctx != null) return
        ctx = app.applicationContext
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val unfinished = prefs.getString("active-job", "none") ?: "none"
        prefs.edit().putString("active-job", "none").commit()
        record("PROCESS_STARTED")
        if (unfinished != "none") {
            record("PREVIOUS_JOB_INTERRUPTED", unfinished)
            // An interrupted native render must NEVER be resumed without an explicit tap.
            StateStore.active(app, false)
            StateStore.status(app, "Previous render interrupted. Reopen diagnostics before retrying.")
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    val am = app.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val started = prefs.getLong("lastJobStartedMs", 0L)
                    val exit = am.getHistoricalProcessExitReasons(app.packageName, 0, 12)
                        .filter { it.timestamp >= started - 5000L && started > 0L }
                        .maxByOrNull { it.timestamp }
                    if (exit != null) {
                        val reason = when (exit.reason) {
                            ApplicationExitInfo.REASON_CRASH_NATIVE -> "NATIVE_CRASH"
                            ApplicationExitInfo.REASON_CRASH -> "JAVA_CRASH"
                            ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY_KILL"
                            ApplicationExitInfo.REASON_ANR -> "APP_NOT_RESPONDING"
                            ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
                            ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
                            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCES"
                            else -> "OTHER_" + exit.reason
                        }
                        record("ANDROID_PREVIOUS_PROCESS_EXIT", reason,
                            JSONObject().put("reasonCode", exit.reason).put("status", exit.status)
                                .put("timestampMs", exit.timestamp).put("pssKiB", exit.pss)
                                .put("rssKiB", exit.rss).put("importance", exit.importance))
                    }
                } catch (error: Throwable) {
                    record("ANDROID_EXIT_INFO_UNAVAILABLE", error.javaClass.simpleName)
                }
            }
        }
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            if (fatal.compareAndSet(false, true)) {
                try {
                    val details = JSONObject().put("thread", thread.name.take(80))
                        .put("exception", error.javaClass.name).put("stackTrace", trace(error))
                        .put("resources", resources())
                    persist(event("FATAL_UNCAUGHT_EXCEPTION", "Uncaught Java/Kotlin exception", details), true)
                    prefs.edit().putString("lastFatal", error.javaClass.name).commit()
                } catch (ignored: Throwable) { Log.e("SubGenDiagnostics", "Fatal write error", ignored) }
            }
            if (oldHandler != null) oldHandler.uncaughtException(thread, error)
            else { Log.e("SubGenDiagnostics", "Fatal error", error); android.os.Process.killProcess(android.os.Process.myPid()); System.exit(10) }
        }
        timer.scheduleAtFixedRate({ monitor() }, 2, 2, TimeUnit.SECONDS)
    }
    private fun monitor() {
        try {
            if (pingQueued.compareAndSet(false, true)) main.post {
                if (stalled) record("UI_RESPONSIVE_AGAIN")
                lastPing = SystemClock.uptimeMillis(); stalled = false; pingQueued.set(false)
            }
            val delayed = SystemClock.uptimeMillis() - lastPing
            if (visible && delayed > 6000 && !stalled) {
                stalled = true
                record("SUSPECTED_UI_STALL", "Main thread delayed (ms): " + delayed, resources())
            }
            ticks++
            if (job != "none" && ticks % 6 == 0) record("RENDER_RESOURCE_SAMPLE", "", resources())
        } catch (ignored: Throwable) { }
    }
    fun setForeground(on: Boolean) { visible = on; lastPing = SystemClock.uptimeMillis(); stalled = false }
    fun interaction(control: String) { if (control != lastControl) { lastControl = control.take(90); record("CONTROL_INTERACTION", lastControl) } }
    fun startJob(kind: String, metadata: JSONObject = JSONObject()) {
        job = kind.take(80); phase = "starting"
        ctx?.getSharedPreferences(PREF, Context.MODE_PRIVATE)?.edit()
            ?.putString("active-job", job)?.putLong("lastJobStartedMs", System.currentTimeMillis())?.commit()
        record("JOB_STARTED", job, metadata)
        val data = resources()
        if (data.optBoolean("systemMemoryLow")) record("LOW_SYSTEM_MEMORY_WARNING", "", data)
        if (data.optLong("privateStorageFreeMiB", 9999) < 250) record("LOW_STORAGE_WARNING", "", data)
    }
    fun stage(name: String, data: JSONObject = JSONObject()) { phase = name.take(100); record("STAGE", phase, data) }
    /** Synchronous fsync before potentially fatal JNI invocation. No speech text is logged. */
    fun beforeNative(name: String, metadata: JSONObject = JSONObject(), durable: Boolean = true) {
        phase = name.take(100)
        if (ctx == null) return
        val entry = event("BEFORE_NATIVE_CALL", phase, metadata)
        try {
            synchronized(guard) { ring.addLast(entry); while (ring.size > 160) ring.removeFirst() }
            if(durable) persist(entry, true)
            else io.execute { persist(entry, false) }
        } catch (error: Throwable) { Log.w("SubGenDiagnostics", "Native marker failed", error) }
    }
    fun progress(message: String) {
        val label = when {
            message.startsWith("Decoding") -> "decoding"
            message.startsWith("Mixing") -> "mixing"
            message.startsWith("Generating") -> "synthesis"
            message.startsWith("Saving") -> "export"
            else -> "running"
        }
        if (phase != label) { phase = label; record("PROGRESS", label) }
    }
    fun expectedOutput(bytes: Long) {
        val free = resources().optLong("privateStorageFreeMiB", -1)
        if (free >= 0 && free < 64 + 2 * (bytes / 1048576))
            record("LOW_OUTPUT_SPACE_WARNING", "Insufficient space likely", JSONObject().put("estimatedBytes", bytes).put("freeMiB", free))
    }
    fun finishJob(result: String, ms: Long) {
        record("JOB_FINISHED", result, JSONObject().put("elapsedMs", ms).put("resources", resources()))
        job = "none"; phase = "idle"
        ctx?.getSharedPreferences(PREF, Context.MODE_PRIVATE)?.edit()?.putString("active-job", "none")?.commit()
    }
    fun failure(kind: String, error: Throwable) = record(kind, error.javaClass.name,
        JSONObject().put("message", error.message?.take(300)).put("stackTrace", trace(error)).put("resources", resources()))
    private fun trace(error: Throwable) = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString().take(12000)
    private fun event(kind: String, desc: String, extra: JSONObject) = JSONObject().apply {
        put("id", session + "-" + sequence.incrementAndGet())
        put("kind", kind); put("description", desc.replace('\n', ' ').take(450))
        put("timestampMs", System.currentTimeMillis()); put("elapsedMs", SystemClock.elapsedRealtime() - born)
        put("thread", Thread.currentThread().name.take(80))
        put("job", job); put("stage", phase); put("lastInteraction", lastControl)
        if (extra.length() > 0) put("data", extra)
    }
    fun record(kind: String, desc: String = "", extra: JSONObject = JSONObject()) {
        if (ctx == null) return
        val data = event(kind, desc, extra)
        synchronized(guard) { ring.addLast(data); while (ring.size > 160) ring.removeFirst() }
        try { io.execute { persist(data, false) } } catch (ignored: Throwable) { }
    }
    private fun folder(): File? = ctx?.let { File(it.filesDir, "diagnostics").apply { mkdirs() } }
    private fun persist(data: JSONObject, sync: Boolean) {
        try {
            synchronized(guard) {
                val dir = folder() ?: return
                val active = File(dir, "events.jsonl")
                if (active.length() > LIMIT_BYTES) {
                    val prior = File(dir, "events.previous.jsonl")
                    if (prior.exists()) prior.delete()
                    active.renameTo(prior)
                }
                FileOutputStream(active, true).use {
                    it.write((data.toString() + "\n").toByteArray(Charsets.UTF_8))
                    it.flush(); if (sync) it.fd.sync()
                }
            }
        } catch (error: Throwable) { Log.w("SubGenDiagnostics", "Log write failed", error) }
    }
    private fun resources() = JSONObject().apply {
        val rt = Runtime.getRuntime()
        put("heapUsedMiB", (rt.totalMemory() - rt.freeMemory()) / 1048576L)
        put("heapMaxMiB", rt.maxMemory() / 1048576L)
        put("nativeAllocatedMiB", Debug.getNativeHeapAllocatedSize() / 1048576L)
        try { val m = Debug.MemoryInfo(); Debug.getMemoryInfo(m); put("processPssMiB", m.totalPss / 1024) } catch (ignored: Throwable) { }
        try {
            ctx?.let { c ->
                put("privateStorageFreeMiB", StatFs(c.filesDir.absolutePath).availableBytes / 1048576L)
                val am = c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val m = ActivityManager.MemoryInfo(); am.getMemoryInfo(m)
                put("systemMemoryLow", m.lowMemory); put("systemMemoryAvailableMiB", m.availMem / 1048576L)
                if (Build.VERSION.SDK_INT >= 29)
                    put("thermalStatus", (c.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus)
            }
        } catch (ignored: Throwable) { }
    }
    fun report(): JSONObject {
        val app = ctx ?: return JSONObject().put("error", "Recorder not ready")
        val collected = linkedMapOf<String, JSONObject>()
        synchronized(guard) {
            for (name in listOf("events.previous.jsonl", "events.jsonl")) {
                val file = folder()?.let { File(it, name) }
                if (file?.isFile == true) file.forEachLine { text ->
                    try { val value = JSONObject(text); collected[value.optString("id")] = value } catch (ignored: Exception) { }
                }
            }
            ring.forEach { collected[it.optString("id")] = it }
        }
        val items = collected.values.sortedBy { it.optLong("timestampMs") }.takeLast(420)
        val kinds = items.map { it.optString("kind") }.toSet()
        val leads = JSONArray()
        if ("FATAL_UNCAUGHT_EXCEPTION" in kinds) leads.put("Inspect FATAL_UNCAUGHT_EXCEPTION stack trace for the originating app source line.")
        if ("JOB_FAILED" in kinds) leads.put("Review STAGE preceding JOB_FAILED and the stack trace.")
        if ("PREVIOUS_JOB_INTERRUPTED" in kinds) leads.put("Render was interrupted; app now waits for manual retry. Check Android process-exit reason.")
        if ("ANDROID_PREVIOUS_PROCESS_EXIT" in kinds) leads.put("Check ANDROID_PREVIOUS_PROCESS_EXIT reasonCode for native crash, OS kill or ANR.")
        if ("SUSPECTED_UI_STALL" in kinds) leads.put("Inspect last controls and work on the main thread before the stall.")
        if ("LOW_STORAGE_WARNING" in kinds || "LOW_OUTPUT_SPACE_WARNING" in kinds) leads.put("Check free storage before output.")
        if (leads.length() == 0) leads.put("Reproduce the crash, reopen and export diagnostics.")
        val prefs = app.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("schema", "subgen-native-diagnostics-v1")
            put("privacy", "No files or scripts intentionally logged; inspect exception traces before sharing.")
            put("createdAtMs", System.currentTimeMillis())
            put("app", JSONObject().put("package", app.packageName).put("version", try {
                app.packageManager.getPackageInfo(app.packageName, 0).versionName
            } catch (ignored: Exception) { "unknown" }).put("androidApi", Build.VERSION.SDK_INT).put("model", Build.MODEL))
            put("runtime", JSONObject().put("job", job).put("stage", phase).put("lastControl", lastControl))
            put("resources", resources())
            put("lastFatal", prefs.getString("lastFatal", "none"))
            put("latestIncident", items.lastOrNull { it.optString("kind") in setOf("FATAL_UNCAUGHT_EXCEPTION", "JOB_FAILED", "PREVIOUS_JOB_INTERRUPTED", "SUSPECTED_UI_STALL") } ?: JSONObject.NULL)
            put("suggestions", leads)
            put("events", JSONArray().apply { items.forEach { put(it) } })
        }
    }
    fun overview() = "Recorder: ON, private and offline\nCurrent job: " + job + "\nStage: " + phase +
        "\nLast control: " + lastControl + "\n\nCaptures crashes, stages, memory, storage, thermal status and UI stalls.\nEXPORT REPORT saves the JSON file."
    fun clearHistory() {
        synchronized(guard) {
            ring.clear()
            folder()?.let { File(it, "events.jsonl").delete(); File(it, "events.previous.jsonl").delete() }
        }
        ctx?.getSharedPreferences(PREF, Context.MODE_PRIVATE)?.edit()?.remove("lastFatal")?.apply()
    }
}
class SubGenApplication : Application() {
    override fun onCreate() { super.onCreate(); Diagnostics.install(this) }
}
