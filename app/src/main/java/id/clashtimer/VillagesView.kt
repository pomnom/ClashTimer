package id.clashtimer

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.Lane
import java.time.ZoneId

/**
 * Lembar "Desa anda" (ketuk nama desa di atas): semua desa sekaligus — builder, lab, Builder Base,
 * Star Lab — seperti widget. Ketuk untuk membuka, tahan untuk ganti nama / hapus.
 */
object VillagesView {

    fun sheet(act: MainActivity) {
        val dialog = Sheets.open(act, "Desa anda", "ketuk untuk membuka · tahan untuk ganti nama / hapus", Help.Topic.VILLAGES) { col, dismiss ->
            fun fill() {
                col.removeAllViews()
                render(act, col, act.villageRows(), dismiss)
            }
            act.onVillageRows = { fill() }
            fill()
        }
        dialog.setOnDismissListener { act.onVillageRows = null }
    }

    private fun render(act: MainActivity, box: LinearLayout, rows: List<Widgets.VRow>?, dismiss: () -> Unit) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val current = act.currentTag()
        if (rows == null) {
            box.addView(Ui.muted(act, "Memuat semua desa…", Ui.BODY_M).apply { setPadding(Ui.dp(act, 4), Ui.dp(act, 12), 0, Ui.dp(act, 12)) })
        } else {
            val ups = Store.upgrades(act)
            if (rows.size > 1) nextLogins(act, box, rows, ups, now, zone)
            // Desa yang dibuka di atas, lalu yang butuh perhatian (ada pekerja bebas).
            val sorted = rows.sortedWith(compareByDescending<Widgets.VRow> { it.tag == current }
                .thenByDescending { r -> r.cells.filter { !it.lane.bb }.sumOf { it.free } > 0 })
            for (r in sorted) box.addView(card(act, r, r.tag == current, ups, now, zone, dismiss), Ui.matchWrap(12, act))
        }
        val actions = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 4), 0, 0) }
        actions.addView(Ui.tonal(act, "Tambah / update desa") { dismiss(); ImportUi.show(act) }, Ui.weight1())
        actions.addView(Ui.icon(act, R.drawable.ic_settings, 22, Ui.TEXT2, "Pengaturan") { dismiss(); act.openSettings() },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(act, 8) })
        box.addView(actions, Ui.matchWrap())
    }

    /** "Login berikutnya (semua desa)": kapan tiap desa perlu dibuka, paling awal di atas. */
    private fun nextLogins(act: MainActivity, box: LinearLayout, rows: List<Widgets.VRow>, ups: List<Upgrade>, now: Long, zone: ZoneId) {
        class Next(val name: String, val at: Long, val who: String)
        val list = ArrayList<Next>()
        for (r in rows) {
            val home = r.cells.filter { !it.lane.bb }
            if (home.isEmpty()) continue
            val labels = Sessions.labels(ups.filter { it.tag == r.tag }, now)
            val free = home.filter { it.free > 0 }
            if (free.isNotEmpty()) {
                list += Next(r.name, now, free.joinToString(" & ") { c -> if (c.lane == Lane.BUILDER) "${c.free} builder" else laneShort(c.lane) } + " bebas")
                continue
            }
            val nexts = home.mapNotNull { it.next }
            val first = nexts.minByOrNull { it.finishAt } ?: continue
            val sched = runCatching { Store.planSettings(act, r.tag).schedule }.getOrNull()
            val at = sched?.nextOnline(first.finishAt) ?: first.finishAt
            val who = nexts.filter { it.finishAt - first.finishAt <= 45 * 60_000L }.map { labels[it.id] ?: it.kind.label }.distinct()
            list += Next(r.name, at, who.joinToString(" & "))
        }
        if (list.isEmpty()) return
        val card = Ui.panel(act, padH = 14, padV = 12, color = Ui.SURFACE2)
        card.addView(Ui.muted(act, "Login berikutnya (semua desa)").apply { textSize = 13f; setPadding(0, 0, 0, Ui.dp(act, 4)) })
        list.sortedBy { it.at }.take(4).forEachIndexed { i, n ->
            val line = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 4)) }
            val t = Ui.monoText(act, if (n.at <= now) "kini" else HomeView.short(n.at, now, zone), 20f).apply {
                if (i > 0) setTextColor(Ui.TEXT2)
                if (n.at <= now) setTextColor(Ui.FREE)
            }
            line.addView(t, LinearLayout.LayoutParams(Ui.dp(act, 92), LinearLayout.LayoutParams.WRAP_CONTENT))
            line.addView(Ui.text(act, "${n.name} · ${n.who}", Ui.BODY_M).apply {
                textSize = 14f
                setTextColor(if (i == 0) Ui.TEXT else Ui.TEXT2)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, Ui.weight1())
            if (i == 0) line.addView(View(act).apply { background = Ui.rounded(Ui.FREE, Ui.dp(act, 4).toFloat()) },
                LinearLayout.LayoutParams(Ui.dp(act, 8), Ui.dp(act, 8)))
            card.addView(line)
        }
        box.addView(card, Ui.matchWrap(12, act))
    }

    private fun laneShort(l: Lane) = when (l) {
        Lane.BUILDER -> "Builder"
        Lane.LAB -> "Lab"
        Lane.PET -> "Pet"
        Lane.BB_BUILDER -> "Builder Base"
        Lane.STAR_LAB -> "Star Lab"
    }

    private fun card(act: MainActivity, r: Widgets.VRow, open: Boolean, ups: List<Upgrade>, now: Long, zone: ZoneId, dismiss: () -> Unit): View {
        val freeHome = r.cells.filter { !it.lane.bb }.sumOf { it.free }
        val card = Ui.panel(act, padH = 12, padV = 12, color = if (open) Ui.FREE_BG else Ui.SURFACE2,
            stroke = if (open) Ui.FREE else if (freeHome > 0) Ui.FREE_LINE else null)
        card.isClickable = true
        card.setOnClickListener { dismiss(); if (!open) act.selectVillage(r.tag) }
        card.setOnLongClickListener { dismiss(); VillagesUi.options(act, r.tag); true }
        Ui.ripple(card)

        val head = Ui.hbox(act)
        val hc = Ui.vbox(act)
        hc.addView(Ui.text(act, r.name, Ui.TITLE_M).apply {
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.TEXT)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        val pct = Store.lastPct(act, r.tag).takeIf { it >= 0 }?.let { "${Ui.pct(it.toDouble())} menuju max" }
        val hall = listOfNotNull(
            r.acc.th.takeIf { it > 0 }?.let { "TH$it" },
            r.acc.bh.takeIf { it > 0 }?.let { "BH$it" },
            pct,
        ).joinToString(" · ")
        if (hall.isNotEmpty()) hc.addView(Ui.muted(act, hall).apply { textSize = 12f })
        head.addView(hc, Ui.weight1())
        val freeAll = r.cells.sumOf { it.free }
        head.addView(android.widget.TextView(act).apply {
            text = if (open) "dibuka" else if (freeAll > 0) "$freeAll bebas" else "semua sibuk"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(if (open) Ui.FREE_TEXT else if (freeAll > 0) Ui.ON_FREE else Ui.TEXT2)
            background = Ui.rounded(if (open) android.graphics.Color.TRANSPARENT else if (freeAll > 0) Ui.FREE else Ui.SURFACE, Ui.dp(act, 10).toFloat())
            setPadding(Ui.dp(act, 9), Ui.dp(act, 3), Ui.dp(act, 9), Ui.dp(act, 3))
        })
        card.addView(head)

        if (r.cells.isEmpty()) {
            val free = act.freeBuilders(r.tag, false)
            card.addView(Ui.text(act, if (free > 0) "$free builder bebas" else "semua builder sibuk", Ui.BODY_M).apply {
                setTextColor(if (free > 0) Ui.FREE_TEXT else Ui.TEXT2)
                setPadding(0, Ui.dp(act, 8), 0, 0)
            })
        } else {
            val goblins = ups.filter { it.tag == r.tag && it.extra && it.finishAt > now }
            val shown = r.cells.filter { it.total > 0 || it.next != null || !it.lane.bb }
            shown.chunked(2).forEachIndexed { i, pair ->
                val line = Ui.hbox(act).apply { gravity = Gravity.TOP }
                pair.forEachIndexed { j, c ->
                    val gob = goblins.filter { g -> Kind.of(c.lane) == g.kind }.minByOrNull { it.finishAt }
                    line.addView(tile(act, r.tag, c, gob, now, dismiss), Ui.weight1().apply { if (j > 0) marginStart = Ui.dp(act, 8) })
                }
                if (pair.size == 1) line.addView(Ui.space(act), Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
                card.addView(line, Ui.matchWrap().apply { topMargin = Ui.dp(act, if (i == 0) 10 else 8) })
            }
        }

        val age = now - r.acc.exportedAt
        if (r.acc.exportedAt > 0 && age > DAY) {
            card.addView(Ui.muted(act, "export ${Fmt.dur(age)} lalu · update supaya akurat").apply {
                textSize = 12f
                setTextColor(Ui.IDLE)
                setPadding(0, Ui.dp(act, 8), 0, 0)
            })
        }
        return card
    }

    /** Kotak satu jenis pekerja: "2 bebas" + titik builder, atau hitung mundur upgrade yang paling cepat selesai. */
    private fun tile(act: MainActivity, tag: String, c: Widgets.Cell, goblin: Upgrade?, now: Long, dismiss: () -> Unit): View {
        val free = c.free > 0
        val next = c.next
        val lab = c.lane == Lane.LAB || c.lane == Lane.PET || c.lane == Lane.STAR_LAB
        val v = Ui.vbox(act).apply {
            background = Ui.rounded(if (free) Ui.FREE_BG else Ui.SURFACE, Ui.dp(act, 12).toFloat())
            setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8))
            isClickable = true
            setOnClickListener {
                dismiss()
                val open = when (c.lane) {
                    Lane.LAB, Lane.PET, Lane.STAR_LAB -> MainActivity.Tab.LAB
                    else -> MainActivity.Tab.BUILDERS
                }
                act.selectVillage(tag, bb = c.lane.bb, open = open)
            }
        }
        v.addView(Ui.muted(act, laneShort(c.lane)).apply { textSize = 12f; maxLines = 1 })
        val main = when {
            free -> Ui.text(act, "${c.free} bebas", Ui.TITLE_S).apply { setTextColor(Ui.FREE_TEXT); setTypeface(typeface, Typeface.BOLD) }
            next != null -> Ui.monoText(act, act.countdown(next.finishAt - now), 15f).apply {
                setTextColor(if (lab) Ui.LAB else Ui.BUSY)
                Sheets.live(this, next.finishAt) { left -> act.countdown(left) }
            }
            c.total == 0 -> Ui.text(act, "—", Ui.TITLE_S).apply { setTextColor(Ui.TEXT2) }
            else -> Ui.text(act, "Menganggur", Ui.TITLE_S).apply { setTextColor(Ui.IDLE) }
        }
        v.addView(main.apply { textSize = 15f; maxLines = 1; setPadding(0, Ui.dp(act, 2), 0, Ui.dp(act, 3)) })
        if ((c.lane == Lane.BUILDER || c.lane == Lane.BB_BUILDER) && c.total > 1) {
            // Titik per builder: kuning = sibuk, hijau = bebas.
            val dots = Ui.hbox(act)
            for (k in 0 until c.total) dots.addView(View(act).apply {
                background = Ui.rounded(if (k < c.busy) Ui.BUSY else Ui.FREE, Ui.dp(act, 4).toFloat())
            }, LinearLayout.LayoutParams(Ui.dp(act, 8), Ui.dp(act, 8)).apply { marginEnd = Ui.dp(act, 4) })
            v.addView(dots)
        } else {
            val sub = when {
                goblin != null -> "+ Goblin ${act.countdown(goblin.finishAt - now)}"
                free -> c.tip ?: "siap diisi"
                next != null -> Widgets.shortTitle(next)
                c.total == 0 -> "belum ada"
                else -> "tidak ada saran"
            }
            v.addView(Ui.muted(act, sub).apply {
                textSize = 12f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            })
        }
        return v
    }
}
