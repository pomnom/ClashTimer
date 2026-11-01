package id.clashtimer

import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import id.clashtimer.core.Fmt
import id.clashtimer.core.Lane
import java.time.ZoneId

/** Tab "Desa": semua desa sekaligus — builder, lab, Builder Base, Star Lab — seperti widget. */
object VillagesView {

    internal fun render(act: MainActivity, box: LinearLayout, rows: List<Widgets.VRow>?, wide: Boolean) {
        act.addWarnings(box)
        if (rows == null) {
            box.addView(Ui.muted(act, "⏳ Memuat semua desa…", Ui.BODY_M).apply { setPadding(Ui.dp(act, 4), Ui.dp(act, 24), 0, 0) })
            return
        }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val ups = Store.upgrades(act)
        val freeTotal = rows.sumOf { r -> r.cells.filter { !it.lane.bb }.sumOf { it.free } }
        box.addView(Ui.sectionRow(act,
            if (freeTotal > 0) "$freeTotal pekerja Home Village bebas" else "Semua pekerja Home Village sibuk",
            "paling cepat di atas"))
        val cards = rows.map { card(act, it, ups, now, zone) }
        if (wide) {
            cards.chunked(2).forEach { pair ->
                val line = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                pair.forEachIndexed { i, c -> line.addView(c, Ui.weight1().apply { if (i > 0) marginStart = Ui.dp(act, 8) }) }
                if (pair.size == 1) line.addView(View(act), Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
                box.addView(line, Ui.matchWrap())
            }
        } else cards.forEach { box.addView(it) }

        val add = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
        add.addView(Ui.filled(act, "+ Tambah desa dari clipboard") { act.importFromClipboard() }.apply { isAllCaps = false }, Ui.weight1())
        add.addView(Ui.outlined(act, "Tempel") { act.showPasteDialog() }.apply { isAllCaps = false },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(act, 8)
            })
        box.addView(add)
        box.addView(Ui.muted(act, "Ketuk desa atau kotaknya untuk membuka · tahan untuk ganti nama / hapus. Desa baru ditambah otomatis saat meng-import export akun lain.").apply {
            setPadding(Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4), 0)
            gravity = Gravity.CENTER
        })
    }

    private fun card(act: MainActivity, r: Widgets.VRow, ups: List<Upgrade>, now: Long, zone: ZoneId): View {
        val (card, col) = Ui.card(act, padH = 12, padV = 12)
        card.setOnClickListener { act.selectVillage(r.tag, bb = false, open = MainActivity.Tab.HOME) }
        card.setOnLongClickListener { VillagesUi.options(act, r.tag); true }

        val head = Ui.hbox(act)
        head.addView(Ui.text(act, r.name, Ui.TITLE_M).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        }, Ui.weight1())
        val hall = listOfNotNull(
            r.acc.th.takeIf { it > 0 }?.let { "TH$it" },
            r.acc.bh.takeIf { it > 0 }?.let { "BH$it" },
        ).joinToString(" · ")
        if (hall.isNotEmpty()) head.addView(Ui.muted(act, hall, Ui.BODY_M))
        col.addView(head)

        if (r.cells.isEmpty()) {
            val free = act.freeBuilders(r.tag, false)
            col.addView(Ui.text(act, if (free > 0) "🔨 $free builder bebas" else "🔨 semua builder sibuk", Ui.BODY_M).apply {
                setTextColor(if (free > 0) Ui.FREE else Ui.secondary(act))
                setPadding(0, Ui.dp(act, 6), 0, 0)
            })
        } else {
            val shown = r.cells.filter { it.total > 0 || it.next != null || !it.lane.bb }
            shown.chunked(2).forEachIndexed { i, pair ->
                val line = Ui.hbox(act).apply { gravity = Gravity.TOP }
                pair.forEachIndexed { j, c ->
                    line.addView(tile(act, r.tag, c, now), Ui.weight1().apply { if (j > 0) marginStart = Ui.dp(act, 8) })
                }
                if (pair.size == 1) line.addView(View(act), Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
                col.addView(line, Ui.matchWrap().apply { topMargin = Ui.dp(act, if (i == 0) 10 else 8) })
            }
        }

        // Goblin Builder / Researcher (event): pekerja tambahan sementara.
        val goblins = ups.filter { it.tag == r.tag && it.extra && it.finishAt > now }.sortedBy { it.finishAt }
        for (g in goblins) {
            val who = if (g.kind == Kind.LAB) "Goblin Researcher" else "Goblin Builder"
            val line = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, 0) }
            line.addView(Ui.muted(act, "👺 $who · ${Widgets.shortTitle(g)}").apply {
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }, Ui.weight1())
            val cd = Ui.muted(act, act.countdown(g.finishAt - now))
            act.registerCountdown(cd, g.finishAt, clockStyle = false)
            line.addView(cd)
            col.addView(line)
        }

        val pct = Store.lastPct(act, r.tag).takeIf { it >= 0 }?.let { "${Ui.pct(it.toDouble())} menuju max · " } ?: ""
        col.addView(Ui.muted(act, "${pct}import ${Fmt.at(r.acc.exportedAt, zone)}").apply { setPadding(0, Ui.dp(act, 8), 0, 0) })
        return card
    }

    private fun laneTitle(lane: Lane): String = when (lane) {
        Lane.BUILDER -> "🔨 Builder"
        Lane.LAB -> "🧪 Lab"
        Lane.PET -> "🐾 Pet House"
        Lane.BB_BUILDER -> "🛠️ Builder BB"
        Lane.STAR_LAB -> "⭐ Star Lab"
    }

    /** Kotak satu jenis pekerja: "2 bebas" + saran, atau hitung mundur upgrade yang paling cepat selesai. */
    private fun tile(act: MainActivity, tag: String, c: Widgets.Cell, now: Long): View {
        val free = c.free > 0
        val next = c.next
        val v = Ui.vbox(act).apply {
            background = Ui.tileBg(act, if (free) Ui.FREE else null)
            setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8))
            isClickable = true
            setOnClickListener { act.selectVillage(tag, bb = c.lane.bb, open = MainActivity.Tab.HOME) }
        }
        val top = Ui.hbox(act)
        top.addView(Ui.muted(act, laneTitle(c.lane)).apply { maxLines = 1 }, Ui.weight1())
        if (c.total > 1) {
            val busy = (c.total - c.free).coerceAtLeast(0)
            top.addView(Ui.muted(act, "$busy/${c.total}"))
        }
        v.addView(top)
        val main = when {
            free -> Ui.text(act, "${c.free} bebas", Ui.TITLE_S).apply { setTextColor(Ui.FREE) }
            next != null -> Ui.text(act, act.countdown(next.finishAt - now), Ui.TITLE_S).apply {
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                act.registerCountdown(this, next.finishAt, clockStyle = false)
            }
            c.total == 0 -> Ui.text(act, "—", Ui.TITLE_S).apply { setTextColor(Ui.secondary(act)) }
            else -> Ui.text(act, "Menganggur", Ui.TITLE_S).apply { setTextColor(Ui.secondary(act)) }
        }
        v.addView(main.apply { maxLines = 1 })
        val sub = when {
            free -> c.tip?.let { "💡 $it" } ?: "siap diisi"
            next != null -> Widgets.shortTitle(next)
            c.total == 0 -> "belum ada"
            else -> "tidak ada saran"
        }
        v.addView(Ui.muted(act, sub).apply {
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        return v
    }
}
