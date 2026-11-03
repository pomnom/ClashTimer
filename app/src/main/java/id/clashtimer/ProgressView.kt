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
            box.addView(Ui.muted(act, tr("Belum ada data Builder Base di export desa ini.", "No Builder Base data in this village export yet."), Ui.BODY_M).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 24), 0, 0)
            })
            return
        }
        summary(act, box, p, sim, b, zone)
        (if (bb) b.simBB else b.simHome)?.let { needsCard(act, box, it, zone) }
        if (bb && b.ps.bbStrategy == id.clashtimer.core.BBStrategy.SIXTH) b.simBB?.sixth?.let { sixthCard(act, box, it, zone) }

        val open = p.categories.filter { it.remainingSec > 0 }.sortedBy { it.pct }
        val doneCats = p.categories.filter { it.remainingSec <= 0 }
        box.addView(Ui.sectionRow(act, tr("Kategori", "Categories"), tr("sisa biaya", "cost left"), Help.Topic.PROGRESS))
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
            r.addView(Ui.muted(act, tr("sudah max", "maxed")))
            col.addView(r)
        }
        box.addView(card)

        p.walls?.let { wallsCard(act, box, it) }
        if (!bb) {
            val (oreCard, oreCol) = Ui.card(act)
            oreCol.addView(Ui.text(act, tr("⚒️ Kalkulator ore & equipment", "⚒️ Ore & equipment calculator"), Ui.TITLE_S))
            oreCol.addView(Ui.muted(act, tr("Kebutuhan Shiny/Glowy/Starry Ore ke level target & perkiraan berapa minggu lagi", "Shiny/Glowy/Starry Ore needed for the target level & estimated weeks")))
            oreCard.setOnClickListener {
                act.startActivity(android.content.Intent(act, OreActivity::class.java).putExtra(OreActivity.EXTRA_TAG, b.tag))
            }
            box.addView(oreCard, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })
        }
        if (!bb) craftedCard(act, box, b)
        simulationCard(act, box, b, bb, zone)
        History.render(act, box, b, bb)

        val collectors = if (b.ps.skipCollectors) tr(" Collector tidak dihitung (Pengaturan).", " Collectors not counted (Settings).") else ""
        box.addView(Ui.muted(act,
            tr("Perkiraan memperhitungkan jam istirahat, helper, Gold Pass, event dan CWL (hero), tanpa potion/book. ", "Estimates include rest hours, helpers, Gold Pass, events and CWL (heroes), without potions/books. ") +
                tr("Town Hall & tembok tidak ikut dihitung.$collectors Data durasi game ${Repo.gameData(act).version}.", "Town Hall & walls are not counted.$collectors Game duration data ${Repo.gameData(act).version}.")).apply {
            setPadding(Ui.dp(act, 4), Ui.dp(act, 14), Ui.dp(act, 4), 0)
        })
    }

    private fun summary(act: MainActivity, box: LinearLayout, p: VillageProgress, sim: SimResult?, b: PlanBundle, zone: ZoneId) {
        val now = b.computedAt
        val hall = if (p.bb) "BH${p.hall}" else "TH${p.hall}"
        val card = Ui.panel(act, padH = 14, padV = 14)
        val top = Ui.hbox(act).apply { gravity = android.view.Gravity.BOTTOM }
        top.addView(Ui.monoText(act, Ui.pct(p.pct), 40f))
        val week = History.weekDelta(act, b.tag, p.bb)
        top.addView(Ui.muted(act, tr("menuju max $hall", "to max $hall") + (week?.let { tr(" · +${Ui.pct(it)} minggu ini", " · +${Ui.pct(it)} this week") } ?: "")).apply {
            textSize = 14f
            setPadding(Ui.dp(act, 10), 0, 0, Ui.dp(act, 4))
        }, Ui.weight1())
        card.addView(top)
        card.addView(Ui.muted(act, workDays(b, p)).apply { textSize = 13f; setTextColor(Ui.TEXT); setPadding(0, Ui.dp(act, 6), 0, 0) })
        card.addView(Ui.thinBar(act, p.pct / 100.0, Ui.FREE).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 8)).apply { topMargin = Ui.dp(act, 10) }
        })

        val builderLane = if (p.bb) Lane.BB_BUILDER else Lane.BUILDER
        val labLane = if (p.bb) Lane.STAR_LAB else Lane.LAB
        val bRem = p.remaining(builderLane) * 1000
        val lRem = p.remaining(labLane) * 1000
        class Eta(val label: String, val done: Long?, val remaining: Long)
        val etas = arrayListOf(Eta(if (p.bb) "Master Builder" else "Builder", sim?.let { if (p.bb) it.bbBuilderDone else it.builderDone }, bRem))
        if (!p.bb && sim?.heroDone != null) etas += Eta("Hero", sim.heroDone, 1)
        etas += Eta(if (p.bb) "Star Lab" else "Lab", sim?.let { if (p.bb) it.starLabDone else it.labDone }, lRem)
        val pRem = if (!p.bb) p.remaining(Lane.PET) * 1000 else 0
        if (pRem > 0 && sim?.petDone != null && etas.size < 3) etas += Eta("Pet", sim.petDone, pRem)
        // Penentu = yang paling akhir selesai.
        val last = etas.filter { it.remaining > 0 && it.done != null }.maxByOrNull { it.done!! }
        val grid = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 12), 0, 0) }
        for (e in etas) {
            val decisive = e === last && etas.count { it.remaining > 0 } > 1
            val tile = Ui.panel(act, padH = 10, padV = 8, color = Ui.SURFACE2, stroke = if (decisive) Ui.LAB else null, radiusDp = 12)
            tile.addView(Ui.muted(act, e.label + if (decisive) tr(" · penentu", " · decisive") else "").apply {
                textSize = 11f
                if (decisive) setTextColor(Ui.LAB)
            })
            val value = when {
                e.remaining <= 0 -> "max ✓"
                e.done != null && e.done > now -> "±" + Fmt.dateShort(e.done, zone)
                else -> Fmt.bigDur(e.remaining)
            }
            tile.addView(Ui.monoText(act, value, 15f).apply { setPadding(0, Ui.dp(act, 3), 0, 0); if (e.remaining <= 0) setTextColor(Ui.FREE_TEXT) })
            if (e.remaining > 0 && e.done != null && e.done > now) tile.addView(Ui.muted(act, days(e.done - now)).apply { textSize = 11f })
            grid.addView(tile, Ui.weight1().apply { if (grid.childCount > 0) marginStart = Ui.dp(act, 8) })
        }
        card.addView(grid, Ui.matchWrap())
        box.addView(card, Ui.matchWrap(12, act))
    }

    /**
     * "188 hari kerja builder · 103 hari lab": total lama upgrade yang tersisa (Gold Pass ikut), dikurangi
     * bantuan Builder's Apprentice & Lab Assistant (tiap hari menyelesaikan [level] jam kerja).
     */
    private fun workDays(b: PlanBundle, p: VillageProgress): String {
        val day = 86_400.0
        val builderLane = if (p.bb) Lane.BB_BUILDER else Lane.BUILDER
        val labLane = if (p.bb) Lane.STAR_LAB else Lane.LAB
        val builders = Planner.laneCount(b.village, builderLane, b.ps).coerceAtLeast(1)
        var bDays = p.remaining(builderLane) / day
        var lDays = (p.remaining(labLane) + if (p.bb) 0 else p.remaining(Lane.PET)) / day
        if (!p.bb) {
            val app = b.village.helper(id.clashtimer.core.GameData.APPRENTICE)?.level ?: 0
            val asst = b.village.helper(id.clashtimer.core.GameData.LAB_ASSISTANT)?.level ?: 0
            // Helper bekerja per hari kalender: hemat [level] jam untuk tiap hari pekerjaannya berjalan.
            if (app > 0) bDays -= (bDays / builders) * app / 24.0
            if (asst > 0) lDays /= (1 + asst / 24.0)
        }
        val who = if (p.bb) "master builder" else "builder"
        val lab = if (p.bb) "star lab" else "lab"
        val parts = arrayListOf(tr("${Math.round(bDays)} hari kerja $who", "${Math.round(bDays)} $who days"))
        if (lDays > 0) parts += tr("${Math.round(lDays)} hari $lab", "${Math.round(lDays)} $lab days")
        return parts.joinToString(" · ")
    }

    // ---------------------------------------------------------------- kebutuhan sumber daya

    /** Rentang yang dipilih: 0 = 24 jam, 1 = 3 hari, 2 = 7 hari. */
    private var needWindow = 0

    private val resourceOrder = listOf("Gold", "Elixir", "Dark Elixir", "Builder Gold", "Builder Elixir")

    private fun resColor(r: String): Int = when (r) {
        "Gold", "Builder Gold" -> 0xFFE8C35A.toInt()
        "Elixir", "Builder Elixir" -> 0xFFD47AE0.toInt()
        "Dark Elixir" -> 0xFF8E8AA0.toInt()
        else -> Ui.GREY
    }

    /** "Siapkan resource": total biaya upgrade yang dimulai dalam 24 jam / 3 / 7 hari. */
    private fun needsCard(act: MainActivity, box: LinearLayout, sim: SimResult, zone: ZoneId) {
        val needs = sim.needs
        if (needs.isEmpty()) return
        val now = System.currentTimeMillis()
        val card = Ui.panel(act, padH = 14, padV = 12)
        val head = Ui.hbox(act)
        head.addView(Ui.text(act, tr("Siapkan resource", "Prepare resources"), Ui.TITLE_S).apply { textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(Ui.TEXT) })
        head.addView(Help.button(act, Help.Topic.NEEDS))
        card.addView(head)
        card.addView(Ui.segmentedWide(act, listOf(tr("24 jam", "24 hours"), tr("3 hari", "3 days"), tr("7 hari", "7 days")), needWindow, heightDp = 36) {
            needWindow = it
            act.renderBody()
        }.apply { background = Ui.rounded(Ui.SURFACE2, Ui.dp(act, 10).toFloat()) }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 8) })
        val n = needs.getOrNull(needWindow) ?: needs.first()
        if (n.costs.isEmpty()) {
            card.addView(Ui.muted(act, tr("Tidak ada upgrade yang dimulai pada rentang ini — pekerja masih sibuk.", "No upgrades start in this range — workers are still busy.")).apply {
                textSize = 13f
                setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 2))
            })
        } else {
            val sorted = n.costs.entries.sortedWith(compareBy({ resourceOrder.indexOf(it.key).let { i -> if (i < 0) 99 else i } }, { -it.value }))
            val max = sorted.maxOf { it.value }.coerceAtLeast(1)
            for ((res, cost) in sorted) {
                val line = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
                line.addView(Ui.text(act, Fmt.resource(res), Ui.BODY_M).apply { textSize = 14f; setTextColor(Ui.TEXT) },
                    LinearLayout.LayoutParams(Ui.dp(act, 96), LinearLayout.LayoutParams.WRAP_CONTENT))
                line.addView(Ui.thinBar(act, cost.toDouble() / max, resColor(res)).apply {
                    layoutParams = LinearLayout.LayoutParams(0, Ui.dp(act, 6), 1f)
                })
                line.addView(Ui.monoText(act, Fmt.money(cost), 14f).apply { gravity = android.view.Gravity.END },
                    LinearLayout.LayoutParams(Ui.dp(act, 76), LinearLayout.LayoutParams.WRAP_CONTENT))
                card.addView(line)
            }
            n.top.entries.maxByOrNull { it.value.cost }?.let { (res, t) ->
                card.addView(Ui.muted(act, tr("Termahal: ${t.title} · ${Fmt.money(t.cost)} ${Fmt.resource(res)} · ${HomeView.short(t.start, now, zone)}", "Most expensive: ${t.title} · ${Fmt.money(t.cost)} ${Fmt.resource(res)} · ${HomeView.short(t.start, now, zone)}")).apply {
                    textSize = 12f
                    setPadding(0, Ui.dp(act, 10), 0, 0)
                })
            }
        }
        box.addView(card, Ui.matchWrap(12, act))
    }

    // ---------------------------------------------------------------- builder ke-6

    private fun sixthCard(act: MainActivity, box: LinearLayout, six: id.clashtimer.core.SixthPlan, zone: ZoneId) {
        val now = System.currentTimeMillis()
        val card = Ui.panel(act, padH = 14, padV = 12)
        val head = Ui.hbox(act)
        head.addView(Ui.text(act, tr("Builder ke-6 (B.O.B)", "6th builder (B.O.B)"), Ui.TITLE_S).apply { textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(Ui.TEXT) })
        head.addView(Help.button(act, Help.Topic.SIXTH))
        head.addView(Ui.space(act), Ui.weight1())
        val eta = six.doneAt
        head.addView(Ui.monoText(act, eta?.let { "±" + Fmt.dateShort(it, zone) } ?: tr("belum bisa", "not yet possible"), 15f).apply { setTextColor(Ui.BUSY) })
        card.addView(head)
        if (eta != null) {
            val d = Math.round((eta - now) / DAY.toDouble())
            card.addView(Ui.muted(act, if (d <= 0) tr("hari ini", "today") else tr("±$d hari lagi · ${six.reqs.count { it.done }}/${six.reqs.size} syarat", "±$d days left · ${six.reqs.count { it.done }}/${six.reqs.size} requirements")).apply { textSize = 13f })
        } else six.blockers.forEach { card.addView(Ui.muted(act, it).apply { textSize = 13f; setTextColor(Ui.IDLE) }) }
        for (r in six.reqs) {
            val line = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0); gravity = android.view.Gravity.TOP }
            line.addView(android.view.View(act).apply {
                background = if (r.done) Ui.rounded(Ui.FREE, Ui.dp(act, 9).toFloat())
                else Ui.rounded(android.graphics.Color.TRANSPARENT, Ui.dp(act, 9).toFloat(), Ui.OUTLINE, Ui.dp(act, 2))
            }, LinearLayout.LayoutParams(Ui.dp(act, 18), Ui.dp(act, 18)).apply { marginEnd = Ui.dp(act, 10); topMargin = Ui.dp(act, 1) })
            val c = Ui.vbox(act)
            c.addView(Ui.text(act, r.label, Ui.BODY_M).apply { textSize = 14f; setTextColor(if (r.done) Ui.TEXT2 else Ui.TEXT) })
            c.addView(Ui.muted(act, r.detail).apply { textSize = 12f })
            line.addView(c, Ui.weight1())
            val right = when {
                r.done -> "selesai"
                six.eta[r.key] != null -> "±" + Fmt.dateShort(six.eta[r.key]!!, zone)
                else -> "–"
            }
            line.addView(Ui.monoText(act, right, 13f, bold = false).apply {
                setTextColor(if (r.done) Ui.FREE_TEXT else Ui.TEXT2)
                setPadding(Ui.dp(act, 8), 0, 0, 0)
            })
            card.addView(line)
        }
        box.addView(card, Ui.matchWrap(12, act))
    }

    /** Tembok: progres, biaya naik satu level per level sekarang, dan total ke max. */
    private fun wallsCard(act: MainActivity, box: LinearLayout, w: WallProgress) {
        box.addView(Ui.sectionRow(act, tr("🧱 Tembok", "🧱 Walls"), tr("${w.atMax}/${w.total} di Lv${w.maxLevel}", "${w.atMax}/${w.total} at Lv${w.maxLevel}")))
        val (card, col) = Ui.card(act)
        val pct = if (w.total > 0) 100.0 * w.atMax / w.total else 100.0
        val head = Ui.hbox(act)
        head.addView(Ui.text(act, tr("Progres tembok", "Wall progress"), Ui.TITLE_S), Ui.weight1())
        head.addView(Ui.text(act, Ui.pct(pct), Ui.TITLE_S))
        col.addView(head)
        col.addView(Ui.bar(act, pct))
        val res = when (w.resource) {
            "Gold" -> tr("Gold atau Elixir", "Gold or Elixir")
            "Builder Gold" -> "Gold BB"
            else -> w.resource
        }
        if (w.costLeft <= 0) {
            col.addView(Ui.muted(act, tr("Semua tembok sudah Lv${w.maxLevel} (max untuk hall ini) ✓", "All walls are Lv${w.maxLevel} (max for this hall) ✓")))
        } else {
            for ((lvl, n) in w.levels.entries.sortedByDescending { it.key }) {
                val next = w.nextCosts[lvl]
                val line = if (next == null) "${n}× Lv$lvl · max ✓" else "${n}× Lv$lvl → Lv${lvl + 1}: ${Fmt.money(next)}"
                col.addView(Ui.text(act, line, Ui.BODY_M).apply { setPadding(0, Ui.dp(act, 4), 0, 0) })
            }
            val oneUp = w.nextCosts.values.sum()
            col.addView(Ui.muted(act, tr("Naik satu level semua: ±${Fmt.money(oneUp)} · semua ke Lv${w.maxLevel}: ±${Fmt.money(w.costLeft)} ($res)", "All up one level: ±${Fmt.money(oneUp)} · all to Lv${w.maxLevel}: ±${Fmt.money(w.costLeft)} ($res)")).apply {
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
        box.addView(Ui.sectionRow(act, "🛠️ Crafting Station", tr("modul / max TH$th", "modules / max TH$th"), Help.Topic.CRAFTING))
        val (card, col) = Ui.card(act)
        crafted.forEachIndexed { i, c ->
            val def = gd.craftedDef(c.defenseId)
            col.addView(Ui.text(act, def?.name ?: tr("Pertahanan rakitan ${i + 1}", "Crafted defense ${i + 1}"), Ui.TITLE_S).apply {
                if (i > 0) setPadding(0, Ui.dp(act, 8), 0, 0)
            })
            val parts = c.modules.map { (mid, lvl) ->
                val m = def?.modules?.firstOrNull { it.id == mid }
                val max = m?.maxFor(th)
                val label = m?.short?.substringAfterLast(' ')?.ifBlank { null } ?: tr("Modul", "Module")
                "$label $lvl" + (max?.let { "/$it" } ?: "")
            }
            col.addView(Ui.muted(act, parts.joinToString(" · ")))
            val next = c.modules.mapNotNull { (mid, lvl) ->
                def?.modules?.firstOrNull { it.id == mid }?.let { m -> m.levels.firstOrNull { it.level == lvl + 1 && it.hall <= th } }
            }
            if (next.isNotEmpty()) {
                col.addView(Ui.muted(act, tr("Berikutnya: ", "Next: ") + next.joinToString(" · ") { "${Fmt.money(it.cost)} · ${Fmt.durSec(it.time)}" }))
            }
        }
        box.addView(card)
    }

    /** Hasil simulasi terakhir per desa & versi rencana (berat: dihitung saat diminta). */
    private var simCache: Triple<String, Int, List<Scenarios.Row>>? = null

    private fun simulationCard(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean, zone: ZoneId) {
        box.addView(Ui.sectionRow(act, tr("🔮 Simulasi", "🔮 Simulation"), tr("bagaimana jika", "what if"), Help.Topic.SIMULATION))
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
                        d < 0 -> tr(" · ${-d} hari lebih cepat", " · ${-d} days sooner")
                        d > 0 -> tr(" · $d hari lebih lama", " · $d days later")
                        else -> tr(" · sama", " · same")
                    }
                    return "$label: ${Fmt.dateShort(t, zone)} ${java.time.Instant.ofEpochMilli(t).atZone(zone).year}$delta"
                }
                val lines = if (bb) listOfNotNull(line("Builder Base max", r.bb, cur?.bb))
                else listOfNotNull(
                    line(tr("Bangunan & jebakan", "Buildings & traps"), r.buildings, cur?.buildings),
                    line("Hero", r.heroes, cur?.heroes),
                    line("Lab", r.lab, cur?.lab),
                )
                box2.addView(Ui.text(act, if (lines.isEmpty()) tr("Tidak memengaruhi ", "Does not affect ") + if (bb) "Builder Base" else "Home Village" else lines.joinToString("\n"), Ui.BODY_M))
                col.addView(box2)
            }
        }
        if (cached != null) show(cached)
        else {
            col.addView(Ui.outlined(act, tr("Hitung simulasi", "Run simulation")) {
                col.removeAllViews()
                col.addView(Ui.muted(act, tr("Menghitung… (beberapa detik)", "Calculating… (a few seconds)")))
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
                            col.addView(Ui.muted(act, tr("Simulasi gagal dihitung.", "Simulation failed.")))
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
        r.addView(Ui.muted(act, listOf(costs, tr("${Fmt.bigDur(c.remainingSec * 1000)} kerja", "${Fmt.bigDur(c.remainingSec * 1000)} of work")).filter { it.isNotBlank() }.joinToString(" · ")))

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
                    list.addView(Ui.flat(act, tr("Lihat semua ${c.rows.size}", "See all ${c.rows.size}")) { fill(true) }.apply { isAllCaps = false })
                }
            }
            fill(false)
            r.addView(list)
            val hint = Ui.muted(act, tr("Ketuk untuk melihat ${c.rows.size} item ▾", "Tap to see ${c.rows.size} items ▾"))
            r.addView(hint)
            r.setOnClickListener {
                val show = list.visibility != View.VISIBLE
                list.visibility = if (show) View.VISIBLE else View.GONE
                hint.text = if (show) tr("Sembunyikan ▴", "Hide ▴") else tr("Ketuk untuk melihat ${c.rows.size} item ▾", "Tap to see ${c.rows.size} items ▾")
            }
        }
        return r
    }

    private fun monthYear(t: Long, zone: ZoneId): String =
        DateTimeFormatter.ofPattern("MMM yyyy", Fmt.locale).format(Instant.ofEpochMilli(t).atZone(zone))

    private fun days(ms: Long): String {
        val d = ms / DAY.toDouble()
        return if (d < 1) tr("kurang dari sehari lagi", "less than a day left") else tr("±${Math.round(d)} hari lagi", "±${Math.round(d)} days left")
    }

    private fun days0(ms: Long): Long = Math.round(ms / DAY.toDouble())
}
