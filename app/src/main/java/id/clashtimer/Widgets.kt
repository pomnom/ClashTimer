package id.clashtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.Display
import android.view.View
import android.widget.RemoteViews
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.GameData
import id.clashtimer.core.Lane
import id.clashtimer.core.LaneSlot
import id.clashtimer.core.PlanSettings
import id.clashtimer.core.Planner
import id.clashtimer.core.Village
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Widget layar utama "Semua desa": satu baris per desa dengan kolom Builder, Lab, Builder Base,
 * Star Lab. Di tablet (sw600dp) memakai layout besar: huruf lebih besar, baris mengisi tinggi
 * widget, dan nama upgrade tampil di bawah tiap waktu.
 */
class AllVillagesWidget : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        // Ketuk ↻ di widget, atau alarm penyegar widget.
        if (intent.action == Widgets.ACTION_REFRESH) {
            Widgets.renderAll(context, null, goAsync())
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        Widgets.renderAll(context, appWidgetIds, goAsync())
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) {
        Widgets.renderAll(context, intArrayOf(appWidgetId), goAsync())
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        Widgets.forget(context, appWidgetIds)
    }

    override fun onDisabled(context: Context) {
        Widgets.cancelTick(context)
    }
}

object Widgets {
    const val EXTRA_TAG = "widget_tag"
    const val EXTRA_BB = "widget_bb"
    /** "VILLAGES", "HOME" atau "PLAN": tab yang dibuka (widget & notifikasi membuka Beranda desa itu). */
    const val EXTRA_TAB = "widget_tab"
    /** Perbarui semua widget sekarang (tombol ↻ dan alarm penyegar). */
    const val ACTION_REFRESH = "id.clashtimer.WIDGET_REFRESH"

    private const val MIN = 60_000L
    private const val HOUR = 3_600_000L
    /** Paling lama widget dibiarkan tanpa digambar ulang (sistem HyperOS tidak menjalankan updatePeriodMillis). */
    private const val TICK_MAX = 30 * MIN
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private val exec = Executors.newSingleThreadExecutor()
    private val queued = AtomicBoolean(false)
    private val waiting = ConcurrentLinkedQueue<() -> Unit>()

    /** Perbarui semua widget (setelah import, mulai upgrade, alarm, dll). [done] dipanggil setelah selesai. */
    fun updateAll(ctx: Context, done: (() -> Unit)? = null) {
        val app = ctx.applicationContext
        val mgr = runCatching { AppWidgetManager.getInstance(app) }.getOrNull()
        if (mgr == null || idsOf(app, mgr).isEmpty()) {
            done?.invoke()
            return
        }
        if (done != null) waiting.add(done)
        // Permintaan beruntun (import → alarm → API) cukup digambar sekali; yang datang setelah
        // penggambaran dimulai akan digambar lagi.
        if (!queued.compareAndSet(false, true)) return
        exec.execute {
            queued.set(false)
            val callbacks = generateSequence { waiting.poll() }.toList()
            try {
                draw(app, mgr, idsOf(app, mgr))
            } catch (_: Throwable) {
            } finally {
                callbacks.forEach { runCatching { it() } }
            }
        }
    }

    private fun idsOf(ctx: Context, mgr: AppWidgetManager): IntArray =
        runCatching { mgr.getAppWidgetIds(ComponentName(ctx, AllVillagesWidget::class.java)) }.getOrNull() ?: IntArray(0)

    /** Dari broadcast widget ([ids] null = semua widget). */
    fun renderAll(ctx: Context, ids: IntArray?, pending: BroadcastReceiver.PendingResult?) {
        val app = ctx.applicationContext
        exec.execute {
            try {
                val mgr = AppWidgetManager.getInstance(app)
                draw(app, mgr, ids ?: idsOf(app, mgr))
            } catch (_: Throwable) {
            } finally {
                pending?.finish()
            }
        }
    }

    // ---------------------------------------------------------------- data

    internal class Snap(
        val tag: String,
        val v: Village?,
        val ups: List<Upgrade>,
        /** Pekerja yang bebas sekarang beserta pilihan upgradenya. */
        val slots: List<LaneSlot>,
        val ps: PlanSettings?,
    )

    /** Satu baris widget: desa + status tiap kolom. */
    internal class VRow(val tag: String, val name: String, val acc: Account, val cells: List<Cell>)

    private fun snapshot(ctx: Context, gd: GameData, tag: String, all: List<Upgrade>, now: Long): Snap {
        val ups = all.filter { it.tag == tag }
        val ps = runCatching { Store.planSettings(ctx, tag) }.getOrNull()
        val v = if (ps != null) runCatching { Repo.village(ctx, tag) }.getOrNull() else null
        val fin = ups.associate { it.id to it.finishAt }
        val slots = if (v != null && ps != null) {
            runCatching { Planner.suggest(v, gd, ps, now, fin, upcoming = 0) }.getOrDefault(emptyList())
        } else emptyList()
        return Snap(tag, v, ups, slots, ps)
    }

    /** Semua desa, yang paling cepat punya pekerja bebas di atas. */
    internal fun rows(ctx: Context, now: Long): List<VRow> {
        val gd = Repo.gameData(ctx)
        val all = Store.upgrades(ctx)
        return Store.accounts(ctx).map { acc ->
            val s = snapshot(ctx, gd, acc.tag, all, now)
            VRow(acc.tag, Store.villageName(ctx, acc.tag), acc, LANES.map { cellOf(s, acc, it, now) })
        }.sortedWith(compareBy<VRow>({ r -> r.cells.minOf { it.soonest(now) } }, { it.name.lowercase() }))
    }

    private fun draw(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        val rows = runCatching { rows(ctx, now) }.getOrNull()
        for (id in ids) {
            val layout = nextLayout(ctx, id)
            val rv = (if (rows != null) runCatching { compose(ctx, mgr, id, layout, rows, now) }.getOrNull() else null)
                ?: runCatching { message(ctx, layout, now, "Buka Clash Timer sekali untuk memperbarui widget.") }.getOrNull()
                ?: continue
            runCatching {
                mgr.updateAppWidget(id, rv)
                rememberLayout(ctx, id, layout)
            }
        }
        scheduleTick(ctx, rows, now)
    }

    // ---------------------------------------------------------------- layout bergantian

    private fun wprefs(ctx: Context) = ctx.getSharedPreferences("widgets", Context.MODE_PRIVATE)

    /**
     * Bergantian antara dua id layout yang isinya sama (widget_all dan aliasnya widget_all_b). Launcher
     * yang menerima id layout berbeda selalu membuat tampilan baru dari layout terbaru, bukan menimpa
     * tampilan lama — jadi widget tidak tertahan pada tampilan versi aplikasi sebelumnya dan tidak ada
     * sisa keadaan dari pembaruan sebelumnya.
     */
    private fun nextLayout(ctx: Context, id: Int): Int =
        if (wprefs(ctx).getBoolean("b_$id", false)) R.layout.widget_all else R.layout.widget_all_b

    private fun rememberLayout(ctx: Context, id: Int, layout: Int) {
        wprefs(ctx).edit().putBoolean("b_$id", layout == R.layout.widget_all_b).apply()
    }

    fun forget(ctx: Context, ids: IntArray) {
        val e = wprefs(ctx).edit()
        for (id in ids) e.remove("b_$id")
        e.apply()
    }

    // ---------------------------------------------------------------- penyegar

    private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 5996, Intent(ctx, AllVillagesWidget::class.java).setAction(ACTION_REFRESH), FLAGS,
    )

    /**
     * Kapan tampilan widget berubah berikutnya: upgrade selesai, sisa waktu turun di bawah 24 jam
     * (mulai hitung mundur), atau angka jam pada "1h 21j" berganti. Paling lambat [TICK_MAX].
     */
    private fun nextChange(rows: List<VRow>, now: Long): Long {
        var next = now + TICK_MAX
        for (r in rows) for (c in r.cells) {
            if (c.free > 0) continue
            val u = c.next ?: continue
            val rem = u.finishAt - now
            if (rem <= 0) continue
            next = minOf(next, u.finishAt)
            if (rem > DAY) {
                next = minOf(next, u.finishAt - DAY)
                // Fmt.dur membulatkan ke menit ke atas; "1h 21j" berganti saat sisa ≤ jam penuh − 1 menit.
                val hours = (rem + MIN - 1) / MIN * MIN / HOUR
                next = minOf(next, u.finishAt - (hours * HOUR - MIN))
            }
        }
        return maxOf(next + 2_000, now + MIN)
    }

    /**
     * Alarm penyegar widget (tidak membangunkan HP — widget hanya terlihat saat layar menyala). Upgrade
     * selesai juga sudah memperbarui widget lewat alarm notifikasi; ini menutup perubahan lain dan
     * menggantikan updatePeriodMillis yang tidak dijalankan sebagian launcher.
     */
    private fun scheduleTick(ctx: Context, rows: List<VRow>?, now: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val at = if (rows == null) now + TICK_MAX else nextChange(rows, now)
        val pi = tickIntent(ctx)
        try {
            if (AlarmScheduler.canExact(ctx)) am.setExact(AlarmManager.RTC, at, pi)
            else am.setWindow(AlarmManager.RTC, at, 10 * MIN, pi)
        } catch (_: Exception) {
            runCatching { am.setWindow(AlarmManager.RTC, at, 10 * MIN, pi) }
        }
    }

    fun cancelTick(ctx: Context) {
        runCatching { ctx.getSystemService(AlarmManager::class.java)?.cancel(tickIntent(ctx)) }
    }

    // ---------------------------------------------------------------- kolom

    private val LANES = listOf(Lane.BUILDER, Lane.LAB, Lane.BB_BUILDER, Lane.STAR_LAB)

    /** Kotak, Chronometer, teks waktu, dan baris nama upgrade (hanya ada di layout tablet) tiap kolom. */
    private class CellIds(val box: Int, val chrono: Int, val text: Int, val sub: Int)

    private val CELL_IDS = listOf(
        CellIds(R.id.ar_b1, R.id.ar_c1, R.id.ar_t1, R.id.ar_s1), CellIds(R.id.ar_b2, R.id.ar_c2, R.id.ar_t2, R.id.ar_s2),
        CellIds(R.id.ar_b3, R.id.ar_c3, R.id.ar_t3, R.id.ar_s3), CellIds(R.id.ar_b4, R.id.ar_c4, R.id.ar_t4, R.id.ar_s4),
    )

    /** Perkiraan tinggi (dp): satu baris desa, bagian tetap (padding, judul, judul kolom, catatan bawah), catatan bawah. */
    private class Metrics(val rowDp: Int, val chromeDp: Int, val footerDp: Int)

    private val PHONE = Metrics(45, 82, 24)
    private val TABLET = Metrics(72, 128, 30)

    /** Status satu jenis pekerja di satu desa. */
    internal class Cell(
        val lane: Lane,
        /** Jumlah pekerja (0 = belum ada, mis. Star Lab belum dibangun). */
        val total: Int,
        /** Pekerja bebas yang punya pilihan upgrade. */
        val free: Int,
        /** Upgrade berjalan yang paling cepat selesai. */
        val next: Upgrade?,
        /** Saran untuk pekerja yang bebas. */
        val tip: String?,
        /** Goblin Builder / Goblin Researcher (event) yang sedang bekerja — di luar pekerja tetap. */
        val goblins: Int = 0,
        /** Pekerja tetap yang sedang bekerja. */
        val busy: Int = 0,
    ) {
        fun soonest(now: Long): Long = when {
            free > 0 -> now
            next != null -> next.finishAt
            else -> Long.MAX_VALUE
        }
    }

    /**
     * Sama dengan Beranda: Goblin (event) tidak dihitung sebagai pekerja tetap — tidak menambah jumlah
     * pekerja dan tidak dianggap "berikutnya bebas".
     */
    private fun cellOf(s: Snap, acc: Account?, lane: Lane, now: Long): Cell {
        val kind = Kind.of(lane)
        val all = s.ups.filter { it.kind == kind && it.finishAt > now }
        val running = all.filter { !it.extra }
        val goblins = all.size - running.size
        val next = running.minByOrNull { it.finishAt }
        val v = s.v
        val ps = s.ps
        if (v == null || ps == null) {
            val total = maxOf(if (lane == Lane.BUILDER) acc?.builders ?: 0 else 0, running.size)
            return Cell(lane, total, (total - running.size).coerceAtLeast(0), next, null, goblins, running.size)
        }
        val total = maxOf(Planner.laneCount(v, lane, ps), running.size)
        // Pekerja bebas tanpa pilihan upgrade (sudah max) tidak dihitung "bebas".
        val slot = s.slots.firstOrNull { it.lane == lane && it.freeAt <= now }
        val pick = slot?.ranked?.firstOrNull()
        val free = if (pick != null) slot?.count ?: 0 else 0
        return Cell(lane, total, free, next, pick?.c?.title, goblins, running.size)
    }

    internal fun laneName(lane: Lane): String = when (lane) {
        Lane.BUILDER -> "Builder"
        Lane.LAB -> "Lab"
        Lane.PET -> "Pet House"
        Lane.BB_BUILDER -> "Builder BB"
        Lane.STAR_LAB -> "Star Lab"
    }

    /** "Hidden Tesla → 8", "Cannon · Gear-up" (untuk baris kecil di tablet). */
    internal fun shortTitle(u: Upgrade): String = when {
        u.customTitle.isNotBlank() -> u.customTitle
        u.targetLevel > 0 -> keep("${u.name} → ${u.targetLevel}")
        else -> u.name
    }

    /** Spasi tak terputus agar "→ 8" / "Lv 2" tidak terpisah ke baris berikutnya. */
    private fun keep(text: String): String = text.replace(" → ", " → ").replace("Lv ", "Lv ")

    private fun showText(ctx: Context, rv: RemoteViews, ids: CellIds, value: String, colorRes: Int) {
        rv.setViewVisibility(ids.chrono, View.GONE)
        rv.setViewVisibility(ids.text, View.VISIBLE)
        rv.setTextViewText(ids.text, value)
        rv.setTextColor(ids.text, color(ctx, colorRes))
    }

    /** Warna waktu: emas = builder, ungu = lab (sama dengan aplikasi). */
    private fun laneColor(lane: Lane): Int =
        if (lane == Lane.BUILDER || lane == Lane.BB_BUILDER) R.color.widget_builder else R.color.widget_lab

    /** Baris nama upgrade (ids.sub) selalu diisi; di layout HP view itu tidak ada sehingga diabaikan. */
    private fun fillCell(ctx: Context, rv: RemoteViews, ids: CellIds, c: Cell, now: Long) {
        val next = c.next
        var sub = ""
        var subColor = R.color.widget_text2
        rv.setInt(ids.box, "setBackgroundResource", if (c.free > 0) R.drawable.widget_tile_free else R.drawable.widget_tile)
        when {
            c.free > 0 -> {
                showText(ctx, rv, ids, if (c.free > 1) "${c.free} bebas" else "bebas", R.color.widget_green)
                c.tip?.let {
                    sub = "💡 " + keep(it)
                    subColor = R.color.widget_accent
                }
            }
            next != null -> {
                val rem = next.finishAt - now
                if (rem in 1..DAY) {
                    rv.setViewVisibility(ids.text, View.GONE)
                    rv.setViewVisibility(ids.chrono, View.VISIBLE)
                    rv.setTextColor(ids.chrono, color(ctx, laneColor(c.lane)))
                    rv.setChronometer(ids.chrono, SystemClock.elapsedRealtime() + rem, null, true)
                    rv.setChronometerCountDown(ids.chrono, true)
                } else showText(ctx, rv, ids, Fmt.dur(rem), laneColor(c.lane))
                sub = shortTitle(next)
                if (c.total > 1) sub += " · ${c.busy}/${c.total}"
            }
            c.total > 0 -> showText(ctx, rv, ids, "max", R.color.widget_text2)
            else -> {
                showText(ctx, rv, ids, "–", R.color.widget_text2)
                sub = "belum ada"
            }
        }
        rv.setTextViewText(ids.sub, sub)
        rv.setTextColor(ids.sub, color(ctx, subColor))
    }

    // ---------------------------------------------------------------- tampilan

    /**
     * Tablet atau HP menurut ukuran layar penuh. Konfigurasi aplikasi ini tidak bisa dipakai: saat layar
     * dibagi (mis. game + Clash Timer) konfigurasinya mengikuti jendela aplikasi yang sempit (< 600dp),
     * padahal launcher tetap memakai layout tablet — dulu widget tablet jadi tergambar tanpa nama upgrade.
     */
    private fun isLarge(ctx: Context): Boolean {
        val sw = runCatching {
            val d = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
            val m = DisplayMetrics()
            @Suppress("DEPRECATION")
            d.getRealMetrics(m)
            minOf(m.widthPixels, m.heightPixels) * DisplayMetrics.DENSITY_DEFAULT / m.densityDpi
        }.getOrDefault(0)
        if (sw > 0) return sw >= 600
        return runCatching { ctx.resources.getBoolean(R.bool.widget_large) }.getOrDefault(false)
    }

    /** Jumlah baris desa yang muat pada tinggi [h] dp, dan apakah catatan bawah ikut tampil. */
    private fun fit(m: Metrics, h: Int): Pair<Int, Boolean> {
        if (h <= 0) return 3 to true
        val n = (h - m.chromeDp) / m.rowDp
        if (n >= 1) return n.coerceAtMost(8) to true
        return ((h - m.chromeDp + m.footerDp) / m.rowDp).coerceIn(1, 8) to false
    }

    /**
     * Widget berukuran lebar×tinggi berbeda saat layar tegak dan mendatar (tablet): tegak memakai tinggi
     * maksimum, mendatar tinggi minimum. Bila jumlah baris yang muat berbeda, kirim dua versi.
     */
    private fun compose(ctx: Context, mgr: AppWidgetManager, id: Int, layout: Int, rows: List<VRow>, now: Long): RemoteViews {
        if (rows.isEmpty()) return message(ctx, layout, now, "Belum ada desa · buka Clash Timer lalu import Export village data.")
        val m = if (isLarge(ctx)) TABLET else PHONE
        val o = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        val tall = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val short = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) ?: 0
        val portrait = build(ctx, layout, rows, now, fit(m, tall))
        if (tall <= 0 || short <= 0 || fit(m, short) == fit(m, tall)) return portrait
        return RemoteViews(build(ctx, layout, rows, now, fit(m, short)), portrait)
    }

    private fun build(ctx: Context, layout: Int, rows: List<VRow>, now: Long, size: Pair<Int, Boolean>): RemoteViews {
        val (count, showFooter) = size
        val shown = rows.take(count)
        val hidden = rows.size - shown.size
        val rv = base(ctx, layout, now)
        val zone = ZoneId.systemDefault()

        val freeTotal = rows.sumOf { r -> r.cells.sumOf { it.free } }
        rv.setTextViewText(R.id.a_summary, (if (hidden > 0) "${shown.size}/${rows.size} desa · " else "") + "$freeTotal bebas")
        rv.setTextColor(R.id.a_summary, color(ctx, if (freeTotal > 0) R.color.widget_green else R.color.widget_text2))
        // Semua bagian diatur tegas setiap kali (tidak bergantung pada keadaan tampilan sebelumnya).
        rv.setViewVisibility(R.id.a_cols, View.VISIBLE)
        rv.setViewVisibility(R.id.a_message, View.GONE)
        rv.setViewVisibility(R.id.a_rows, View.VISIBLE)

        rv.removeAllViews(R.id.a_rows)
        shown.forEachIndexed { i, r -> rv.addView(R.id.a_rows, row(ctx, r, i, now)) }

        rv.setViewVisibility(R.id.a_footer_line, if (showFooter) View.VISIBLE else View.GONE)
        rv.setViewVisibility(R.id.a_footer, if (showFooter) View.VISIBLE else View.GONE)
        if (!showFooter) return rv
        val free = rows.firstNotNullOfOrNull { r -> r.cells.firstOrNull { it.free > 0 }?.let { r to it } }
        if (free != null) {
            val (r, c) = free
            val what = c.tip?.let { ": $it" } ?: " bebas"
            rv.setTextViewText(R.id.a_footer, "💡 ${r.name} · ${laneName(c.lane)}$what")
            rv.setTextColor(R.id.a_footer, color(ctx, R.color.widget_accent))
        } else {
            val next = rows.flatMap { r -> r.cells.mapNotNull { c -> c.next?.let { r to it } } }
                .minByOrNull { it.second.finishAt }
            rv.setTextViewText(R.id.a_footer, next?.let { (r, u) ->
                "⏱️ Berikutnya: ${r.name} · ${u.title()}, ${short(u.finishAt, now, zone)}"
            } ?: "Tidak ada upgrade berjalan")
            rv.setTextColor(R.id.a_footer, color(ctx, R.color.widget_text2))
        }
        return rv
    }

    private fun row(ctx: Context, r: VRow, index: Int, now: Long): RemoteViews {
        val row = RemoteViews(ctx.packageName, R.layout.widget_all_row)
        row.setTextViewText(R.id.ar_name, r.name)
        val gob = r.cells.sumOf { it.goblins }
        row.setTextViewText(R.id.ar_hall, listOfNotNull(
            r.acc.th.takeIf { it > 0 }?.let { "TH$it" },
            r.acc.bh.takeIf { it > 0 }?.let { "BH$it" },
            gob.takeIf { it > 0 }?.let { "👺$it" },
        ).joinToString(" · "))
        r.cells.forEachIndexed { k, c -> fillCell(ctx, row, CELL_IDS[k], c, now) }
        // Ketuk baris: Beranda desa itu (Home Village); kotak Builder Base / Star Lab: Beranda Builder Base.
        row.setOnClickPendingIntent(R.id.ar_row, openVillage(ctx, r.tag, false, 9_100_000 + index * 2))
        val bb = openVillage(ctx, r.tag, true, 9_100_001 + index * 2)
        r.cells.forEachIndexed { k, c -> if (c.lane.bb) row.setOnClickPendingIntent(CELL_IDS[k].box, bb) }
        return row
    }

    private fun openVillage(ctx: Context, tag: String, bb: Boolean, code: Int): PendingIntent {
        val open = Intent(ctx, MainActivity::class.java)
            .setAction("id.clashtimer.OPEN_HOME")
            .setData(Uri.parse("clashtimer://desa/" + Uri.encode(tag) + if (bb) "/bb" else ""))
            .putExtra(EXTRA_TAG, tag)
            .putExtra(EXTRA_BB, bb)
            .putExtra(EXTRA_TAB, "HOME")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(ctx, code, open, FLAGS)
    }

    /** Kerangka widget: ketuk di luar baris membuka aplikasi; ↻ jam terakhir diperbarui (ketuk = perbarui). */
    private fun base(ctx: Context, layout: Int, now: Long): RemoteViews {
        val rv = RemoteViews(ctx.packageName, layout)
        val open = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        rv.setOnClickPendingIntent(R.id.a_root, PendingIntent.getActivity(ctx, 5998, open, FLAGS))
        // Judul "Semua desa" → tab Desa di aplikasi.
        val villages = Intent(ctx, MainActivity::class.java)
            .setAction("id.clashtimer.OPEN_VILLAGES")
            .putExtra(EXTRA_TAB, "VILLAGES")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        rv.setOnClickPendingIntent(R.id.a_title, PendingIntent.getActivity(ctx, 5995, villages, FLAGS))
        rv.setTextViewText(R.id.a_refresh, "↻ " + Fmt.time(now, ZoneId.systemDefault()))
        val refresh = Intent(ctx, AllVillagesWidget::class.java).setAction(ACTION_REFRESH)
        rv.setOnClickPendingIntent(R.id.a_refresh, PendingIntent.getBroadcast(ctx, 5997, refresh, FLAGS))
        return rv
    }

    private fun message(ctx: Context, layout: Int, now: Long, text: String): RemoteViews {
        val rv = base(ctx, layout, now)
        rv.setTextViewText(R.id.a_summary, "")
        rv.setViewVisibility(R.id.a_cols, View.GONE)
        rv.removeAllViews(R.id.a_rows)
        rv.setViewVisibility(R.id.a_message, View.VISIBLE)
        rv.setTextViewText(R.id.a_message, text)
        rv.setViewVisibility(R.id.a_footer_line, View.GONE)
        rv.setViewVisibility(R.id.a_footer, View.GONE)
        return rv
    }

    private fun color(ctx: Context, res: Int): Int = ctx.getColor(res)

    /** "15:08" (hari ini) atau "Sab 15:08". */
    private fun short(t: Long, now: Long, zone: ZoneId): String {
        val d1 = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val d0 = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = Fmt.time(t, zone)
        return if (d1 == d0) time else DateTimeFormatter.ofPattern("EEE", Fmt.locale).format(d1) + " " + time
    }
}
