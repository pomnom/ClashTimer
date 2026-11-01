package id.clashtimer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Cincin progres kecil dengan angka persen di tengah (seperti ClashClock). */
class RingView(ctx: Context, private val sizeDp: Int = 40) : View(ctx) {
    var pct: Int = 0
        set(value) { field = value.coerceIn(0, 100); invalidate() }
    var label: String? = null
        set(value) { field = value; invalidate() }
    /** false bila tengah cincin diisi ikon. */
    var showText: Boolean = true
        set(value) { field = value; invalidate() }

    private val density = ctx.resources.displayMetrics.density
    private val stroke = 3.5f * density
    private val rect = RectF()

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = 0x33888888
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
        color = Ui.GREEN
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_SP, 11f, ctx.resources.displayMetrics,
        )
        color = Ui.onSurface(ctx)
    }

    fun setColor(c: Int) {
        arc.color = c
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val px = (sizeDp * density).toInt()
        setMeasuredDimension(px, px)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val half = stroke / 2f
        rect.set(half, half, width - half, height - half)
        canvas.drawArc(rect, 0f, 360f, false, track)
        if (pct > 0) canvas.drawArc(rect, -90f, 360f * pct / 100f, false, arc)
        if (!showText) return
        val y = height / 2f - (text.descent() + text.ascent()) / 2f
        canvas.drawText(label ?: pct.toString(), width / 2f, y, text)
    }
}
