package id.clashtimer

import android.view.View
import android.widget.LinearLayout
import id.clashtimer.core.CatProgress
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.Lane
import id.clashtimer.core.Planner
import id.clashtimer.core.Scenarios
import id.clashtimer.core.SimResult
import id.clashtimer.core.VillageProgress
import id.clashtimer.core.WallProgress
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Tab "Progres": seberapa jauh menuju max TH / BH. */
object ProgressView {

    fun render(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean) {
        val zone = ZoneId.systemDefault()
        val p = if (bb) b.bb else b.home
        // Progres menuju max memakai simulasi "Max" (strategi builder ke-6 berhenti saat B.O.B terbuka).
        val sim = if (bb) b.simBBMax else b.simHome
        if (p == null) {
            box.addView(Ui.muted(act, "Belum ada data Builder Base di export desa ini.", Ui.BODY_M).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 24), 0, 0)
            })
            return
        }
        summary(act, box, p, sim, b, zone)

        val open = p.categories.filter { it.remainingSec > 0 }.sortedBy { it.pct }
        val doneCats = p.categories.filter { it.remainingSec <= 0 }
        box.addView(Ui.sectionRow(act, "Kategori", "sisa biaya", Help.Topic.PROGRESS))
        val (card, col) = Ui.card(act, padH = 0, padV = 0)
        var n = 0
        for (c in open) {
            if (n++ > 0) col.addView(Ui.divider(act))
            col.addView(categoryRow(act, c, b.tag))
        }
        if (doneCats.isNotEmpty()) {
            if (n > 0) col.addView(Ui.divider(act))
            val r = Ui.hbox(act).apply { setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12)) }
            r.addView(Ui.text(act, "✓ " + doneCats.joinToString(", ") { it.title }, Ui.TITLE_S).apply { setTextColor(Ui.GREEN) }, Ui.weight1())
            r.addView(Ui.muted(act, "sudah max"))
            col.addView(r)
        }
        box.addView(card)

        p.walls?.let { wallsCard(act, box, it) }
        if (!bb) {
            val (oreCard, oreCol) = Ui.card(act)
            oreCol.addView(Ui.text(act, "⚒️ Kalkulator ore & equipment", Ui.TITLE_S))
            oreCol.addView(Ui.muted(act, "Kebutuhan Shiny/Glowy/Starry Ore ke level target & perkiraan berapa minggu lagi"))
            oreCard.setOnClickListener {
                act.startActivity(android.content.Intent(act, OreActivity::class.java).putExtra(OreActivity.EXTRA_TAG, b.tag))
            }
            box.addView(oreCard, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })
        }
        if (!bb) craftedCard(act, box, b)
        simulationCard(act, box, b, bb, zone)
        History.render(act, box, b, bb)

        val collectors = if (b.ps.skipCollectors) " Collector tidak dihitung (Pengaturan)." else ""
        box.addView(Ui.muted(act,
            "Perkiraan memperhitungkan jam istirahat, helper, Gold Pass, event dan CWL (hero), tanpa potion/book. " +
                "Town Hall & tembok tidak ikut dihitung.$collectors Data durasi game ${Repo.gameData(act).version}.").apply {
            setPadding(Ui.dp(act, 4), Ui.dp(act, 14), Ui.dp(act, 4), 0)
        })
    }

    private fun summary(act: MainActivity, box: LinearLayout, p: VillageProgress, sim: SimResult?, b: PlanBundle, zone: ZoneId) {
        val now = b.computedAt
        val hall = if (p.bb) "BH${p.hall}" else "TH${p.hall}"
        val (card, col) = Ui.card(act, padV = 14)
        col.addView(Ui.muted(act, "$hall menuju max", Ui.BODY_M))
        col.addView(Ui.text(act, Ui.pct(p.pct), Ui.TITLE_L).apply { textSize = 34f })
        col.addView(Ui.bar(act, p.pct))

        val builderLane = if (p.bb) Lane.BB_BUILDER else Lane.BUILDER
        val labLane = if (p.bb) Lane.STAR_LAB else Lane.LAB
        val builders = Planner.laneCount(b.village, builderLane, b.ps).coerceAtLeast(1)
        val bRem = p.remaining(builderLane) * 1000
        val lRem = p.remaining(labLane) * 1000

        val boxes = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, 0) }
        fun metric(label: String, done: Long?, remaining: Long, first: Boolean) {
            val m = Ui.vbox(act).apply {
                background = Ui.tint(Ui.GREY, Ui.dp(act, 8).toFloat(), 0x1A)
                setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8))
            }
            m.addView(Ui.muted(act, label))
            val value = when {
                remaining <= 0 -> "Sudah max ✓"
                done != null && done > now -> "±" + monthYear(done, zone)
                else -> Fmt.bigDur(remaining)
            }
            m.addView(Ui.text(act, value, Ui.TITLE_S))
            if (remaining > 0 && done != null && done > now) m.addView(Ui.muted(act, days(done - now)))
            boxes.addView(m, Ui.weight1().apply { if (!first) marginStart = Ui.dp(act, 8) })
        }
        metric(if (p.bb) "Builder BB selesai" else "Builder selesai", sim?.let { if (p.bb) it.bbBuilderDone else it.builderDone }, bRem, true)
        metric(if (p.bb) "Star Lab selesai" else "Lab selesai", sim?.let { if (p.bb) it.starLabDone else it.labDone }, lRem, false)
        col.addView(boxes, Ui.matchWrap())

        val parts = ArrayList<String>()
        if (bRem > 0) parts += "${days0(bRem)} hari kerja builder (÷$builders ≈ ${days0(bRem / builders)} hari)"
        if (lRem > 0) parts += "${days0(lRem)} hari ${labLane.label.lowercase()}"
        val pRem = if (!p.bb) p.remaining(Lane.PET) * 1000 else 0
        if (pRem > 0) parts += "${days0(pRem)} hari pet"
        if (parts.isNotEmpty()) col.addView(Ui.muted(act, parts.joinToString(" · ")).apply { setPadding(0, Ui.dp(act, 8), 0, 0) })
        box.addView(card)
    }

    /** Tembok: progres, biaya naik satu level per level sekarang, dan total ke max. */
    private fun wallsCard(act: MainActivity, box: LinearLayout, w: WallProgress) {
        box.addView(Ui.sectionRow(act, "🧱 Tembok", "${w.atMax}/${w.total} di Lv${w.maxLevel}"))
        val (card, col) = Ui.card(act)
        val pct = if (w.total > 0) 100.0 * w.atMax / w.total else 100.0
        val head = Ui.hbox(act)
        head.addView(Ui.text(act, "Progres tembok", Ui.TITLE_S), Ui.weight1())
        head.addView(Ui.text(act, Ui.pct(pct), Ui.TITLE_S))
        col.addView(head)
        col.addView(Ui.bar(act, pct))
        val res = when (w.resource) {
            "Gold" -> "Gold atau Elixir"
            "Builder Gold" -> "Gold BB"
            else -> w.resource
        }
        if (w.costLeft <= 0) {
            col.addView(Ui.muted(act, "Semua tembok sudah Lv${w.maxLevel} (max untuk hall ini) ✓"))
        } else {
            for ((lvl, n) in w.levels.entries.sortedByDescending { it.key }) {
                val next = w.nextCosts[lvl]
                val line = if (next == null) "${n}× Lv$lvl · max ✓" else "${n}× Lv$lvl → Lv${lvl + 1}: ${Fmt.money(next)}"
                col.addView(Ui.text(act, line, Ui.BODY_M).apply { setPadding(0, Ui.dp(act, 4), 0, 0) })
            }
            val oneUp = w.nextCosts.values.sum()
            col.addView(Ui.muted(act, "Naik satu level semua: ±${Fmt.money(oneUp)} · semua ke Lv${w.maxLevel}: ±${Fmt.money(w.costLeft)} ($res)").apply {
                setPadding(0, Ui.dp(act, 6), 0, 0)
            })
        }
        box.addView(card)
    }

    /** Crafting Station: pertahanan rakitan musim ini dan level modulnya (dari export). */
    private fun craftedCard(act: MainActivity, box: LinearLayout, b: PlanBundle) {
        val crafted = b.village.crafted
        if (crafted.isEmpty()) return
        val gd = Repo.gameData(act)
        val th = b.village.th
        box.addView(Ui.sectionRow(act, "🛠️ Crafting Station", "modul / max TH$th", Help.Topic.CRAFTING))
        val (card, col) = Ui.card(act)
        crafted.forEachIndexed { i, c ->
            val def = gd.craftedDef(c.defenseId)
            col.addView(Ui.text(act, def?.name ?: "Pertahanan rakitan ${i + 1}", Ui.TITLE_S).apply {
                if (i > 0) setPadding(0, Ui.dp(act, 8), 0, 0)
            })
            val parts = c.modules.map { (mid, lvl) ->
                val m = def?.modules?.firstOrNull { it.id == mid }
                val max = m?.maxFor(th)
                val label = m?.short?.substringAfterLast(' ')?.ifBlank { null } ?: "Modul"
                "$label $lvl" + (max?.let { "/$it" } ?: "")
            }
            col.addView(Ui.muted(act, parts.joinToString(" · ")))
            val next = c.modules.mapNotNull { (mid, lvl) ->
                def?.modules?.firstOrNull { it.id == mid }?.let { m -> m.levels.firstOrNull { it.level == lvl + 1 && it.hall <= th } }
            }
            if (next.isNotEmpty()) {
                col.addView(Ui.muted(act, "Berikutnya: " + next.joinToString(" · ") { "${Fmt.money(it.cost)} · ${Fmt.durSec(it.time)}" }))
            }
        }
        box.addView(card)
    }

    /** Hasil simulasi terakhir per desa & versi rencana (berat: dihitung saat diminta). */
    private var simCache: Triple<String, Int, List<Scenarios.Row>>? = null

    private fun simulationCard(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean, zone: ZoneId) {
        box.addView(Ui.sectionRow(act, "🔮 Simulasi", "bagaimana jika", Help.Topic.SIMULATION))
        val (card, col) = Ui.card(act)
        val cached = simCache?.takeIf { it.first == b.tag && it.second == b.version }?.third
        fun show(rows: List<Scenarios.Row>) {
            col.removeAllViews()
            val cur = rows.firstOrNull { it.current }
            for ((i, r) in rows.withIndex()) {
                if (i > 0) col.addView(Ui.divider(act))
                val box2 = Ui.vbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8)) }
                box2.addView(Ui.text(act, (if (r.current) "● " else "") + r.label, Ui.TITLE_S))
                box2.addView(Ui.muted(act, r.note))
                fun line(label: String, t: Long?, base: Long?): String? {
                    if (t == null) return null
                    val d = if (!r.current && base != null) Math.round((t - base) / DAY.toDouble()) else null
                    val delta = when {
                        d == null -> ""
                        d < 0 -> " · ${-d} hari lebih cepat"
                        d > 0 -> " · $d hari lebih lama"
                        else -> " · sama"
                    }
                    return "$label: ${Fmt.dateShort(t, zone)} ${java.time.Instant.ofEpochMilli(t).atZone(zone).year}$delta"
                }
                val lines = if (bb) listOfNotNull(line("Builder Base max", r.bb, cur?.bb))
                else listOfNotNull(
                    line("Bangunan & jebakan", r.buildings, cur?.buildings),
                    line("Hero", r.heroes, cur?.heroes),
                    line("Lab", r.lab, cur?.lab),
                )
                box2.addView(Ui.text(act, if (lines.isEmpty()) "Tidak memengaruhi ${if (bb) "Builder Base" else "Home Village"}" else lines.joinToString("\n"), Ui.BODY_M))
                col.addView(box2)
            }
        }
        if (cached != null) show(cached)
        else {
            col.addView(Ui.outlined(act, "Hitung simulasi") {
                col.removeAllViews()
                col.addView(Ui.muted(act, "Menghitung… (beberapa detik)"))
                val app = act.applicationContext
                Thread {
                    val rows = runCatching {
                        val fin = Repo.finishes(app, b.tag)
                        Scenarios.compute(b.village, Repo.gameData(app), b.ps, System.currentTimeMillis(), fin) { VillageOptions.shortStrategy(it) }
                    }.getOrDefault(emptyList())
                    act.runOnUiThread {
                        if (act.isFinishing) return@runOnUiThread
                        simCache = Triple(b.tag, b.version, rows)
                        if (rows.isEmpty()) {
                            col.removeAllViews()
                            col.addView(Ui.muted(act, "Simulasi gagal dihitung."))
                        } else show(rows)
                    }
                }.start()
            }.apply { isAllCaps = false }, Ui.matchWrap())
        }
        box.addView(card)
    }

    private fun categoryRow(act: MainActivity, c: CatProgress, tag: String): View {
        val r = Ui.vbox(act).apply {
            setPadding(Ui.dp(act, 14), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10))
            isClickable = true
        }
        val head = Ui.hbox(act)
        head.addView(Ui.text(act, c.title, Ui.TITLE_S), Ui.weight1())
        head.addView(Ui.text(act, Ui.pct(c.pct), Ui.TITLE_S))
        r.addView(head)
        r.addView(Ui.bar(act, c.pct))
        val costs = Fmt.costs(c.costs)
        r.addView(Ui.muted(act, listOf(costs, "${Fmt.bigDur(c.remainingSec * 1000)} kerja").filter { it.isNotBlank() }.joinToString(" · ")))

        if (c.rows.isNotEmpty()) {
            val list = Ui.vbox(act).apply { visibility = View.GONE; setPadding(0, Ui.dp(act, 6), 0, 0) }
            fun fill(all: Boolean) {
                list.removeAllViews()
                val shown = if (all) c.rows else c.rows.take(8)
                val pins = Store.pins(act, tag)
                val hides = Store.hides(act, tag)
                for (row in shown) {
                    val up = if (row.upgrading) " ⏳" else ""
                    val mark = when (row.itemId) {
                        in pins -> "📌 "
                        in hides -> "🙈 "
                        else -> ""
                    }
                    list.addView(Ui.text(act, "$mark${row.name}$up", Ui.BODY_M).apply { ItemMenu.attach(act, this, tag, row.itemId) })
                    list.addView(Ui.muted(act, "   ${row.detail} · ${Fmt.bigDur(row.remainingSec * 1000)}").apply {
                        setPadding(0, 0, 0, Ui.dp(act, 4))
                    })
                }
                if (!all && c.rows.size > shown.size) {
                    list.addView(Ui.flat(act, "Lihat semua ${c.rows.size}") { fill(true) }.apply { isAllCaps = false })
                }
            }
            fill(false)
            r.addView(list)
            val hint = Ui.muted(act, "Ketuk untuk melihat ${c.rows.size} item ▾")
            r.addView(hint)
            r.setOnClickListener {
                val show = list.visibility != View.VISIBLE
                list.visibility = if (show) View.VISIBLE else View.GONE
                hint.text = if (show) "Sembunyikan ▴" else "Ketuk untuk melihat ${c.rows.size} item ▾"
            }
        }
        return r
    }

    private fun monthYear(t: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("MMM yyyy", Fmt.locale).format(Instant.ofEpochMilli(t).atZone(zone))

    private fun days(ms: Long): String {
        val d = ms / DAY.toDouble()
        return if (d < 1) "kurang dari sehari lagi" else "±${Math.round(d)} hari lagi"
    }

    private fun days0(ms: Long): Long = Math.round(ms / DAY.toDouble())
}
