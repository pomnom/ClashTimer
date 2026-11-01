package id.clashtimer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.view.MotionEvent
import android.view.View
import id.clashtimer.core.Fmt
import id.clashtimer.core.Lane
import id.clashtimer.core.PlannedStep
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Rencana sebagai garis waktu: satu baris per pekerja (B1…, Lab, Pet / M1…, Star Lab), batang untuk
 * upgrade yang berjalan (penuh) dan yang direncanakan (transparan), celah menganggur (merah), dan jam
 * offline/tidur (abu-abu). Ketuk batang untuk detail.
 */
class TimelineView(ctx: Context) : View(ctx) {

    class Row(val label: String, val color: Int)

    class Bar(
        val row: Int,
        val start: Long,
        val end: Long,
        val color: Int,
        /** true = sedang berjalan, false = rencana. */
        val solid: Boolean,
        val label: String,
        val step: PlannedStep?,
        val idle: Boolean = false,
    )

    var rows: List<Row> = emptyList()
    var bars: List<Bar> = emptyList()
    var offline: List<LongRange> = emptyList()
    var from = 0L
    var to = 1L
    var now = 0L
    var zone: ZoneId = ZoneId.systemDefault()
    var onTap: ((PlannedStep) -> Unit)? = null

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun sp(v: Float) = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val rowH = dp(30f)
    private val labelW = dp(42f)
    private val headerH = dp(20f)
    private val padR = dp(6f)
    private val radius = dp(4f)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1.2f) }
    private val line = Paint().apply { strokeWidth = dp(1f) }
    private val text = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(11f) }
    private val small = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize = sp(10f) }
    private val rect = RectF()

    private val onSurface = Ui.onSurface(ctx)
    private val secondary = Ui.secondary(ctx)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = (headerH + rows.size * rowH + dp(6f)).toInt()
        setMeasuredDimension(w, h)
    }

    private fun x(t: Long): Float {
        val span = (to - from).coerceAtLeast(1)
        val f = (t - from).toDouble() / span
        return (labelW + f.coerceIn(0.0, 1.0) * (width - labelW - padR)).toFloat()
    }

    private fun alpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bottom = headerH + rows.size * rowH

        // jam offline / tidur
        fill.color = alpha(Ui.GREY, 0x26)
        for (w in offline) {
            if (w.last < from || w.first > to) continue
            canvas.drawRect(x(w.first), headerH, x(w.last), bottom, fill)
        }

        // garis jam
        val spanH = (to - from) / 3_600_000L
        val step = if (spanH <= 30) 6 else 12
        var t = Instant.ofEpochMilli(from).atZone(zone).truncatedTo(ChronoUnit.HOURS)
        line.color = 0x22888888
        small.color = secondary
        while (t.toInstant().toEpochMilli() <= to) {
            val ms = t.toInstant().toEpochMilli()
            if (ms >= from && t.hour % step == 0) {
                val xx = x(ms)
                canvas.drawLine(xx, headerH - dp(2f), xx, bottom, line)
                val label = if (t.hour == 0 && spanH > 30) {
                    java.time.format.DateTimeFormatter.ofPattern("EEE d", Fmt.locale).format(t)
                } else String.format(java.util.Locale.ROOT, "%02d", t.hour)
                val lw = small.measureText(label)
                if (xx + lw / 2 < width && xx - lw / 2 > labelW - dp(4f)) {
                    canvas.drawText(label, xx - lw / 2, headerH - dp(6f), small)
                }
            }
            t = t.plusHours(1)
        }

        // baris
        text.color = onSurface
        rows.forEachIndexed { i, r ->
            val top = headerH + i * rowH
            if (i > 0) {
                line.color = 0x16888888
                canvas.drawLine(labelW, top, width - padR, top, line)
            }
            text.color = r.color
            text.isFakeBoldText = true
            val tw = text.measureText(r.label)
            canvas.drawText(r.label, (labelW - tw - dp(8f)).coerceAtLeast(dp(2f)), top + rowH / 2 + text.textSize / 3, text)
        }
        text.isFakeBoldText = false

        // batang
        for (b in bars) {
            if (b.end <= from || b.start >= to) continue
            val top = headerH + b.row * rowH
            val x1 = x(maxOf(b.start, from))
            val x2 = maxOf(x(minOf(b.end, to)), x1 + dp(2f))
            if (b.idle) {
                rect.set(x1, top + rowH / 2 - dp(2f), x2, top + rowH / 2 + dp(2f))
                fill.color = alpha(Ui.IDLE, 0xCC)
                canvas.drawRoundRect(rect, dp(2f), dp(2f), fill)
                continue
            }
            rect.set(x1 + dp(0.5f), top + dp(5f), x2 - dp(0.5f), top + rowH - dp(5f))
            fill.color = if (b.solid) b.color else alpha(b.color, 0x40)
            canvas.drawRoundRect(rect, radius, radius, fill)
            if (!b.solid) {
                stroke.color = alpha(b.color, 0xAA)
                canvas.drawRoundRect(rect, radius, radius, stroke)
            }
            val room = rect.width() - dp(8f)
            if (room > dp(18f)) {
                small.color = if (b.solid) 0xFFFFFFFF.toInt() else onSurface
                val s = TextUtils.ellipsize(b.label, small, room, TextUtils.TruncateAt.END).toString()
                if (s.isNotEmpty()) canvas.drawText(s, rect.left + dp(4f), rect.centerY() + small.textSize / 3, small)
            }
        }

        // sekarang
        if (now in from..to) {
            line.color = Ui.IDLE
            line.strokeWidth = dp(1.5f)
            val xx = x(now)
            canvas.drawLine(xx, headerH - dp(4f), xx, bottom, line)
            line.strokeWidth = dp(1f)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                val row = ((event.y - headerH) / rowH).toInt()
                if (event.y < headerH || row !in rows.indices) return true
                val hit = bars.filter { it.row == row && !it.idle && it.step != null }
                    .minByOrNull { b ->
                        val x1 = x(maxOf(b.start, from))
                        val x2 = x(minOf(b.end, to))
                        when {
                            event.x < x1 -> x1 - event.x
                            event.x > x2 -> event.x - x2
                            else -> 0f
                        }
                    }
                val ok = hit != null && run {
                    val x1 = x(maxOf(hit.start, from)) - dp(12f)
                    val x2 = x(minOf(hit.end, to)) + dp(12f)
                    event.x in x1..x2
                }
                if (ok) {
                    performClick()
                    hit?.step?.let { onTap?.invoke(it) }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    companion object {
        /** Baris & batang dari hasil simulasi untuk satu sisi (Home / BB) dalam rentang [from, to]. */
        fun build(
            view: TimelineView, running: List<PlannedStep>, steps: List<PlannedStep>, bb: Boolean,
            from: Long, to: Long, now: Long,
        ) {
            val all = (running + steps).filter { it.lane.bb == bb }
            fun goblin(s: PlannedStep) = s.note?.startsWith("👺") == true
            val keys = all.map { Triple(it.lane, it.laneIndex, goblin(it)) }.distinct()
                .sortedWith(compareBy({ it.third }, { it.first.ordinal }, { it.second }))
            val rows = keys.map { (lane, idx, gob) ->
                val color = when {
                    gob -> Ui.GREY
                    lane == Lane.BUILDER || lane == Lane.BB_BUILDER -> Ui.BUSY
                    else -> Ui.LAB
                }
                val label = when {
                    gob -> "👺"
                    lane == Lane.BUILDER -> "B$idx"
                    lane == Lane.BB_BUILDER -> "M$idx"
                    lane == Lane.LAB -> "Lab"
                    lane == Lane.PET -> "Pet"
                    else -> "SL"
                }
                TimelineView.Row(label, color)
            }
            val bars = ArrayList<Bar>()
            keys.forEachIndexed { r, key ->
                val color = rows[r].color
                val mine = all.filter { Triple(it.lane, it.laneIndex, goblin(it)) == key }.sortedBy { it.start }
                var cursor = now
                for (s in mine) {
                    if (s.running) {
                        bars += Bar(r, from, s.finish, color, true, name(s), s)
                        cursor = maxOf(cursor, s.finish)
                        continue
                    }
                    if (s.start - cursor > 10 * 60_000L) bars += Bar(r, cursor, s.start, Ui.IDLE, false, "", null, idle = true)
                    bars += Bar(r, s.start, s.finish, if (s.gear) Ui.GOLD else color, false, name(s), s)
                    cursor = maxOf(cursor, s.finish)
                    if (s.start > to) break
                }
            }
            view.rows = rows
            view.bars = bars
            view.from = from
            view.to = to
            view.now = now
        }

        private fun name(s: PlannedStep): String {
            val n = s.item?.name ?: s.title
            return when {
                s.gear -> "$n · gear"
                s.isNew -> n
                else -> "$n ${s.toLevel}"
            }
        }
    }
}
