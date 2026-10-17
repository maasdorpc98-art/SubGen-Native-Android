package com.subgen.nativeapp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/** A native Android drawn slider. No WebView, HTML or standard Android SeekBar skin.
 * Reference from Subliminal Studio v1.1 CSS: 7px track, 14px circular thumb,
 * mono uppercase labels, cyan tinted notes. Visual parity is subject to Android screenshot checks.
 */
class OriginalStudioSliderView(
    context: Context,
    val spec: OriginalSliderSpec,
    private val thumbColour: Int = Color.rgb(255, 102, 102),
    var onUserValueChanged: ((Double) -> Unit)? = null
) : View(context) {
    private val px = resources.displayMetrics.density
    private fun d(n: Float) = n * px
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val track = RectF()
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(233, 246, 248); textSize = d(11f); typeface = Typeface.MONOSPACE
        letterSpacingCompat()
    }
    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(239, 255, 255); textSize = d(12f); typeface = Typeface.MONOSPACE
    }
    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(169, 198, 208); textSize = d(10f); typeface = Typeface.MONOSPACE
    }
    private var current = spec.initial
    var value: Double
        get() = current
        set(newValue) {
            current = spec.clampAndStep(newValue)
            contentDescription = "${spec.label}, ${formatted(current)}"
            invalidate()
        }
    init {
        isClickable = true
        isFocusable = true
        contentDescription = "${spec.label}, ${formatted(value)}"
        minimumHeight = d(80f).roundToInt()
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, resolveSize(d(84f).roundToInt(), heightMeasureSpec))
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val widthF = width.toFloat()
        val pad = d(3f)
        val top = d(16f)
        paint.color = labelPaint.color
        val title = cleanLabel(spec.label).uppercase().take(110)
        val room = widthF - d(98f)
        val drawTitle = if (labelPaint.measureText(title) > room) shorten(title, room) else title
        canvas.drawText(drawTitle, pad, top, labelPaint)
        canvas.drawText(formatted(value), widthF - pad - valuePaint.measureText(formatted(value)), top, valuePaint)
        val ty = d(36f)
        track.set(d(7f), ty - d(3.5f), widthF - d(7f), ty + d(3.5f))
        paint.color = Color.argb(46, 255, 60, 60)
        canvas.drawRoundRect(track, d(3.5f), d(3.5f), paint)
        val range = spec.maximum - spec.minimum
        val fraction = if (range > 0.0) ((current - spec.minimum) / range).toFloat().coerceIn(0f,1f) else 0f
        val cx = track.left + (track.width() * fraction)
        val active = RectF(track.left, track.top, cx, track.bottom)
        paint.color = thumbColour
        paint.alpha = 155
        canvas.drawRoundRect(active, d(3.5f), d(3.5f), paint)
        paint.alpha = 55
        canvas.drawCircle(cx, ty, d(10f), paint)
        paint.alpha = 255
        canvas.drawCircle(cx, ty, d(7f), paint)
        val rawNote = spec.note
        if (rawNote.isNotBlank()) {
            val wrapped = wrap(rawNote, notePaint, widthF - d(6f))
            for ((index, part) in wrapped.take(2).withIndex())
                canvas.drawText(part, pad, d(59f + 15f * index), notePaint)
        }
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val fraction = ((event.x - d(7f)) / (width.toFloat() - d(14f))).coerceIn(0f, 1f)
                val next = spec.clampAndStep(spec.minimum + fraction * (spec.maximum - spec.minimum))
                if (next != current) { value = next; onUserValueChanged?.invoke(next) }
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun formatted(n: Double): String = when(spec.id) {
        "ctrl-carrier-hpf" -> if(n<=0)"OFF" else "${intArrayOf(0,20,40,80,100,150,200,300,400,600,800)[n.toInt().coerceIn(0,10)]} Hz"
        "ctrl-carrier-lpf" -> if(n<=0)"OFF" else "${intArrayOf(0,18000,16000,14000,12000,10000,8000,6000,4000,3000,2000)[n.toInt().coerceIn(0,10)]} Hz"
        "ctrl-carrier-fade" -> "${n.toInt()}s"
        "ctrl-binaural-beat", "ctrl-rhythmic-pulse" -> String.format(java.util.Locale.US,"%.1f Hz",n)
        "ctrl-binaural-center", "ctrl-rhythmic-tone" -> "${n.toInt()} Hz"
        "ctrl-carrier-width", "ctrl-carrier-8d-motion", "ctrl-carrier-8d-speed", "ctrl-rhythmic-depth" -> "${n.toInt()}%"
        "ctrl-master-balance-a", "ctrl-master-balance-b" -> if(n==0.0)"Equal · 100%" else "${n.toInt()}"
        "ctrl-na-layer-speed-1", "ctrl-secondary-speed" -> String.format(java.util.Locale.US,"%.2fx",n)
        "ctrl-na-layer-volume-1", "ctrl-vol-carrier", "ctrl-vol-noise", "ctrl-vol1", "ctrl-vol-carrier-8d", "ctrl-binaural-volume", "ctrl-rhythmic-volume", "ctrl-master-output-db" -> String.format(java.util.Locale.US, "%.1f dB", n)
        else -> if(n==n.toInt().toDouble())"${n.toInt()}" else String.format(java.util.Locale.US,"%.2f",n)
    }
    private fun cleanLabel(label:String):String {
        return label.replace(Regex("\\s+(?:−|-)?\\d+(?:\\.\\d+)?(?:\\s?(?:dB|Hz|x|%|s))$",RegexOption.IGNORE_CASE),"")
           .replace(Regex("\\s+OFF$",RegexOption.IGNORE_CASE),"")
    }
    private fun shorten(str: String, limit: Float): String {
        var text = str
        while (text.isNotEmpty() && labelPaint.measureText("$text…") > limit) text = text.dropLast(1)
        return "$text…"
    }
    private fun wrap(str: String, p: Paint, available: Float): List<String> {
        val result = mutableListOf<String>()
        var line = ""
        for (word in str.split(" ")) {
            val next = if (line.isEmpty()) word else "$line $word"
            if (p.measureText(next) > available && line.isNotEmpty()) {
                result.add(line); line = word
            } else line = next
        }
        if (line.isNotEmpty()) result.add(line)
        return result
    }
    // Paint has no letterSpacing property (TextPaint/View does); keep the renderer native.
    private fun Paint.letterSpacingCompat() = Unit
}
