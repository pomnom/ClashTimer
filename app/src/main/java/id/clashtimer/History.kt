package id.clashtimer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.LinearLayout
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.GameData
import id.clashtimer.core.Lane
import id.clashtimer.core.PlanSettings
import id.clashtimer.core.Village
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Riwayat & statistik per desa, dikumpulkan dari setiap import:
 * - snapshot % menuju max (paling banyak satu per hari),
 * - log upgrade (perkiraan mulai & selesai) → jam pekerja menganggur per minggu dan upgrade selesai per bulan,
 * - statistik akun & clan dari API.
 */
object History {
    private const val HOUR = 3_600_000L
    private const val MAX_SNAPS = 400
    private const val MAX_LOG = 600

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- penyimpanan

    class Snap(val day: Long, val home: Double, val bb: Double)

    /** Satu upgrade yang pernah terlihat: lane, item, level tujuan, perkiraan mulai & selesai (ms). */
    class Entry(val id: Int, val lane: String, val name: String, val lv: Int, val s: Long, var f: Long)

    fun snaps(ctx: Context, tag: String): List<Snap> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("hist_$tag", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Snap(it.optLong("d"), it.optDouble("h", -1.0), it.optDouble("b", -1.0)) }
        }
    }

    /** Kenaikan % menuju max dibanding ±7 hari lalu (null bila belum ada datanya). */
    fun weekDelta(ctx: Context, tag: String, bb: Boolean): Double? {
        val list = snaps(ctx, tag)
        val today = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val cur = list.lastOrNull() ?: return null
        val old = list.lastOrNull { it.day <= today - 6 } ?: return null
        val a = if (bb) old.bb else old.home
        val b = if (bb) cur.bb else cur.home
        if (a < 0 || b < 0) return null
        return (b - a).takeIf { it >= 0.5 }
    }

    /** Simpan % menuju max hari ini (menimpa snapshot hari yang sama). */
    fun snapshot(ctx: Context, tag: String, home: Double, bb: Double?) {
        val day = LocalDate.now(ZoneId.systemDefault()).toEpochDay()
        val list = snaps(ctx, tag).filter { it.day != day } + Snap(day, home, bb ?: -1.0)
        val arr = JSONArray()
        list.sortedBy { it.day }.takeLast(MAX_SNAPS).forEach {
            arr.put(JSONObject().put("d", it.day).put("h", it.home).put("b", it.bb))
        }
        prefs(ctx).edit().putString("hist_$tag", arr.toString()).apply()
    }

    fun log(ctx: Context, tag: String): List<Entry> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("ulog_$tag", "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Entry(it.optInt("i"), it.optString("l"), it.optString("n"), it.optInt("v"), it.optLong("s"), it.optLong("f")) }
        }
    }

    private fun saveLog(ctx: Context, tag: String, list: List<Entry>) {
        val arr = JSONArray()
        list.sortedBy { it.f }.takeLast(MAX_LOG).forEach {
            arr.put(JSONObject().put("i", it.id).put("l", it.lane).put("n", it.name).put("v", it.lv).put("s", it.s).put("f", it.f))
        }
        prefs(ctx).edit().putString("ulog_$tag", arr.toString()).apply()
    }

    /** Waktu riwayat desa ini mulai dicatat. */
    fun since(ctx: Context, tag: String): Long = prefs(ctx).getLong("hist_start_$tag", 0L)

    /**
     * Saat import: catat setiap upgrade berjalan. Waktu mulai diperkirakan dari durasi (Gold Pass) dikurangi
     * sisa waktu; upgrade yang sudah tercatat diperbarui waktu selesainya.
     */
    fun onImport(ctx: Context, gd: GameData, v: Village, ps: PlanSettings, prevExportAt: Long) {
        val tag = v.tag
        if (since(ctx, tag) == 0L) prefs(ctx).edit().putLong("hist_start_$tag", v.exportAt).apply()
        val log = log(ctx, tag).toMutableList()
        val used = HashSet<Entry>()
        for (t in v.timers) {
            val target = t.fromLevel + 1
            val finish = v.exportAt + t.remaining * 1000
            val base = if (t.gear) null else gd.item(t.dataId)?.lv(target)?.time
            val dur = base?.let { ps.gold.apply(it, t.lane) } ?: t.remaining
            val start = v.exportAt - max(0L, dur - t.remaining) * 1000
            val match = log.filter {
                it !in used && it.id == t.dataId && it.lv == target && it.lane == t.lane.name && it.f >= prevExportAt - HOUR
            }.minByOrNull { abs(it.f - finish) }
            if (match != null) {
                match.f = finish
                used += match
            } else {
                val e = Entry(t.dataId, t.lane.name, t.name, target, start, finish)
                log += e
                used += e
            }
        }
        saveLog(ctx, tag, log)
    }

    fun remove(ctx: Context, tag: String) {
        prefs(ctx).edit().remove("hist_$tag").remove("ulog_$tag").remove("hist_start_$tag").apply()
    }

    // ---------------------------------------------------------------- hitungan

    private fun weekStart(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** Jam pekerja menganggur per minggu (Senin–Minggu) untuk [lane], [weeks] minggu terakhir. Null = belum ada data. */
    fun idleWeeks(log: List<Entry>, lane: Lane, workers: Int, from: Long, now: Long, zone: ZoneId, weeks: Int = 8): List<Pair<LocalDate, Double?>> {
        val intervals = log.filter { it.lane == lane.name && it.f > it.s }
        val thisWeek = weekStart(LocalDate.now(zone))
        return (weeks - 1 downTo 0).map { k ->
            val w = thisWeek.minusWeeks(k.toLong())
            val ws = w.atStartOfDay(zone).toInstant().toEpochMilli()
            val we = w.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val a = max(ws, from)
            val b = min(we, now)
            if (from <= 0 || b <= a || workers <= 0) return@map w to null
            val ev = ArrayList<Pair<Long, Int>>()
            for (iv in intervals) {
                val s = max(iv.s, a)
                val f = min(iv.f, b)
                if (f > s) { ev += s to 1; ev += f to -1 }
            }
            ev.sortWith(compareBy({ it.first }, { it.second }))
            var busy = 0
            var t = a
            var idle = 0.0
            for ((time, d) in ev) {
                if (time > t) idle += max(0, workers - busy) * (time - t).toDouble()
                busy += d
                t = max(t, time)
            }
            if (b > t) idle += max(0, workers - busy) * (b - t).toDouble()
            w to idle / HOUR
        }
    }

    /** Jumlah upgrade selesai per bulan, [months] bulan terakhir (sejak riwayat dicatat). */
    fun doneMonths(log: List<Entry>, from: Long, now: Long, zone: ZoneId, months: Int = 6): List<Pair<YearMonth, Int?>> {
        val cur = YearMonth.now(zone)
        val start = if (from > 0) YearMonth.from(Instant.ofEpochMilli(from).atZone(zone)) else cur
        return (months - 1 downTo 0).map { k ->
            val m = cur.minusMonths(k.toLong())
            if (m.isBefore(start)) m to null
            else m to log.count { it.f <= now && YearMonth.from(Instant.ofEpochMilli(it.f).atZone(zone)) == m }
        }
    }

    /** % menuju max di akhir tiap minggu (snapshot terakhir minggu itu). */
    fun pctWeeks(snaps: List<Snap>, bb: Boolean, zone: ZoneId, weeks: Int = 12): List<Pair<LocalDate, Double?>> {
        val thisWeek = weekStart(LocalDate.now(zone))
        return (weeks - 1 downTo 0).map { k ->
            val w = thisWeek.minusWeeks(k.toLong())
            val from = w.toEpochDay()
            val to = w.plusWeeks(1).toEpochDay()
            val s = snaps.filter { it.day in from until to }.maxByOrNull { it.day }
            val v = s?.let { if (bb) it.bb else it.home }?.takeIf { it >= 0 }
            w to v
        }
    }

    // ---------------------------------------------------------------- tampilan

    fun render(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val tag = b.tag
        val from = since(act, tag)
        val log = log(act, tag)
        box.addView(Ui.sectionRow(act, tr("📈 Riwayat", "📈 History"), if (from > 0) tr("sejak ${Fmt.dateShort(from, zone)}", "since ${Fmt.dateShort(from, zone)}") else null))
        val (card, col) = Ui.card(act)
        val dm = DateTimeFormatter.ofPattern("d/M", Fmt.locale)

        val pct = pctWeeks(snaps(act, tag), bb, zone)
        col.addView(Ui.text(act, tr("% menuju max per minggu", "% toward max per week"), Ui.TITLE_S))
        col.addView(ChartView(act, pct.map { it.second }, pct.map { dm.format(it.first) }, line = true) { Ui.pct(it) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 130)))

        val lane = if (bb) Lane.BB_BUILDER else Lane.BUILDER
        val workers = if (bb) (if (b.ps.bbBuilders > 0) b.ps.bbBuilders else b.village.bbBuildersAuto) else b.village.homeBuilders
        val idle = idleWeeks(log, lane, workers, from, now, zone)
        col.addView(Ui.text(act, tr("Jam ${if (bb) "builder BB" else "builder"} menganggur per minggu", "Idle ${if (bb) "BB builder" else "builder"} hours per week"), Ui.TITLE_S).apply {
            setPadding(0, Ui.dp(act, 12), 0, 0)
        })
        col.addView(ChartView(act, idle.map { it.second }, idle.map { dm.format(it.first) }) { String.format(java.util.Locale.ROOT, tr("%.0fj", "%.0fh"), it) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 130)))

        val months = doneMonths(log, from, now, zone)
        val mf = DateTimeFormatter.ofPattern("MMM", Fmt.locale)
        col.addView(Ui.text(act, tr("Upgrade selesai per bulan (semua pekerja)", "Upgrades finished per month (all workers)"), Ui.TITLE_S).apply { setPadding(0, Ui.dp(act, 12), 0, 0) })
        col.addView(ChartView(act, months.map { it.second?.toDouble() }, months.map { mf.format(it.first) }) { it.toInt().toString() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 130)))
        col.addView(Ui.muted(act, tr("Dikumpulkan dari setiap import (dan tanda Sudah dimulai). Upgrade yang mulai & selesai di antara ", "Collected from every import (and Started marks). Upgrades that start & finish between ") +
            tr("dua import tidak terlihat, jadi jam menganggur adalah perkiraan atas.", "two imports are not visible, so idle hours are an upper estimate.")).apply { setPadding(0, Ui.dp(act, 6), 0, 0) })
        box.addView(card)

        stats(act, box, tag, zone)
    }

    /** Statistik akun & clan dari API resmi (sinkron terakhir). */
    private fun stats(act: MainActivity, box: LinearLayout, tag: String, zone: ZoneId) {
        val s = Store.apiStats(act, tag) ?: return
        box.addView(Ui.sectionRow(act, tr("👤 Statistik akun", "👤 Account stats"), "API · ${Fmt.at(s.optLong("at"), zone)}"))
        val (card, col) = Ui.card(act)
        fun row(label: String, value: String?) {
            if (value.isNullOrBlank() || value == "0" || value == "null") return
            val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 3), 0, Ui.dp(act, 3)) }
            r.addView(Ui.muted(act, label, Ui.BODY_M), Ui.weight1())
            r.addView(Ui.text(act, value, Ui.BODY_M))
            col.addView(r)
        }
        fun n(k: String) = s.optLong(k, 0).takeIf { it > 0 }?.let { String.format(Fmt.locale, "%,d", it) }
        row(tr("Level pengalaman", "Experience level"), n("exp"))
        row(tr("Trophy (terbaik)", "Trophies (best)"), n("trophies")?.let { t -> t + (n("bestTrophies")?.let { " ($it)" } ?: "") })
        row(tr("Liga", "League"), s.optString("league"))
        row(tr("Trophy Builder Base", "Builder Base trophies"), n("bbTrophies")?.let { t -> t + (n("bbBest")?.let { " ($it)" } ?: "") })
        row(tr("Liga Builder Base", "Builder Base league"), s.optString("bbLeague"))
        row("War stars", n("warStars"))
        row(tr("Menang serang / bertahan", "Attack / defense wins"), listOfNotNull(n("attackWins"), n("defenseWins")).joinToString(" / "))
        row(tr("Donasi diberi / diterima", "Donations given / received"), listOfNotNull(n("donations") ?: "0", n("donationsReceived") ?: "0").joinToString(" / "))
        row(tr("Kontribusi Clan Capital", "Clan Capital contributions"), n("capital"))
        row(tr("Peran di clan", "Clan role"), s.optString("role"))
        row("Achievements", s.optString("achStars").takeIf { it.isNotBlank() }?.let { "$it ⭐ · ${s.optString("achDone")}" })
        s.optJSONArray("achNext")?.let { a ->
            for (i in 0 until a.length()) {
                val o = a.optJSONObject(i) ?: continue
                row("  ${o.optString("n")}", "${String.format(Fmt.locale, "%,d", o.optLong("v"))} / ${String.format(Fmt.locale, "%,d", o.optLong("t"))}")
            }
        }
        s.optJSONObject("clan")?.let { c ->
            col.addView(Ui.text(act, "🛡️ ${c.optString("name")}", Ui.TITLE_S).apply { setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 2)) })
            row(tr("Level clan · anggota", "Clan level · members"), "${c.optInt("level")} · ${c.optInt("members")}/50")
            row(tr("Liga CWL", "CWL league"), c.optString("warLeague"))
            row(tr("Capital Hall · liga Capital", "Capital Hall · Capital league"), listOf(c.optInt("capitalHall").takeIf { it > 0 }?.toString(), c.optString("capitalLeague"))
                .filter { !it.isNullOrBlank() }.joinToString(" · "))
            row(tr("Rentetan menang war", "War win streak"), c.optInt("winStreak").takeIf { it > 0 }?.toString())
            row("War log", if (c.optBoolean("warLogPublic")) tr("publik", "public") else tr("privat (pengingat war biasa tidak bisa)", "private (regular war reminders not possible)"))
        }
        box.addView(card)
    }
}

/** Grafik batang / garis sederhana; [values] null = belum ada data untuk titik itu. */
class ChartView(
    ctx: Context,
    private val values: List<Double?>,
    private val labels: List<String>,
    private val line: Boolean = false,
    private val fmt: (Double) -> String,
) : View(ctx) {
    private val main = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.primary(ctx); strokeWidth = Ui.dp(ctx, 2).toFloat() }
    private val soft = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Ui.GREY; alpha = 70 }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Ui.onSurface(ctx); textSize = Ui.dp(ctx, 10).toFloat(); textAlign = Paint.Align.CENTER
    }
    private val label = Paint(text).apply { color = Ui.GREY }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = values.size
        if (n == 0) return
        val top = Ui.dp(context, 16).toFloat()
        val bottom = height - Ui.dp(context, 16).toFloat()
        val slot = width / n.toFloat()
        val maxV = values.filterNotNull().maxOrNull()?.takeIf { it > 0 } ?: 1.0
        val ceil = if (line) 100.0.coerceAtLeast(maxV) else maxV
        canvas.drawLine(0f, bottom, width.toFloat(), bottom, soft)
        var prev: Pair<Float, Float>? = null
        for (i in 0 until n) {
            val cx = slot * i + slot / 2
            canvas.drawText(labels.getOrElse(i) { "" }, cx, height - Ui.dp(context, 3).toFloat(), label)
            val v = values[i] ?: run { prev = null; null } ?: continue
            val y = (bottom - (bottom - top) * (v / ceil)).toFloat()
            if (line) {
                prev?.let { (px, py) -> canvas.drawLine(px, py, cx, y, main) }
                canvas.drawCircle(cx, y, Ui.dp(context, 3).toFloat(), main)
                prev = cx to y
            } else {
                val w = slot * 0.55f
                canvas.drawRoundRect(RectF(cx - w / 2, y, cx + w / 2, bottom), 6f, 6f, main)
            }
            if (!line || i == n - 1 || values.getOrNull(i + 1) == null) canvas.drawText(fmt(v), cx, y - Ui.dp(context, 4), text)
        }
        if (values.all { it == null }) {
            canvas.drawText(tr("Belum ada data — terisi setelah beberapa kali import", "No data yet — fills in after a few imports"), width / 2f, (top + bottom) / 2, label)
        }
    }
}
