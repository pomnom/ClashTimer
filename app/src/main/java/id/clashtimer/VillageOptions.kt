package id.clashtimer

import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.BBStrategy
import id.clashtimer.core.Fmt
import id.clashtimer.core.PlanSettings
import id.clashtimer.core.Strategy

/**
 * Opsi per desa (lembar dari ikon opsi di atas): strategi saran, Gold Pass, CWL, collector, builder Builder Base.
 * Semua disimpan untuk desa [tag] saja.
 */
object VillageOptions {

    fun shortStrategy(s: Strategy) = when (s) {
        Strategy.MAX -> "Seimbang"
        Strategy.RUSH -> "Rush TH"
        Strategy.HERO -> "Hero dulu"
    }

    private fun goldLabel(ps: PlanSettings, bb: Boolean): String {
        val b = ps.gold.builderPct
        val r = ps.gold.researchPct
        return when {
            bb -> if (b == 0) "Tidak ada" else "Builder $b%"
            b == 0 && r == 0 -> "Tidak ada"
            b == r -> "$b%"
            else -> "B $b% · R $r%"
        }
    }

    /** "· s/d 1 Des" bila akhir musim Gold Pass diketahui dari API. */
    private fun gpEnd(act: MainActivity): String {
        val end = Store.goldPassEnd(act)
        return if (end > System.currentTimeMillis()) " · s/d ${id.clashtimer.core.Fmt.dateShort(end, java.time.ZoneId.systemDefault())}" else ""
    }

    /** Nilai Gold Pass sekarang sama dengan hasil deteksi dari timer. */
    private fun goldAuto(act: MainActivity, tag: String, ps: PlanSettings, bb: Boolean): Boolean {
        val b = Store.goldDetected(act, tag, false)?.takeIf { it.pct == ps.gold.builderPct }
        val r = Store.goldDetected(act, tag, true)
        return if (bb) b != null else b != null && (r == null || r.pct == ps.gold.researchPct)
    }

    private fun cwlLabel(act: MainActivity, tag: String, ps: PlanSettings): String {
        when (Store.cwlAuto(act, tag)?.state) {
            "notroster" -> return "Tidak di roster"
            "optout" -> return "War preference out"
            "noclan" -> return "Tidak di clan"
            "open" -> if (!ps.cwl.isKnown(ps.cwl.next(System.currentTimeMillis()))) return "Perkiraan tgl 1"
        }
        return cwlLabel(ps)
    }

    private fun cwlLabel(ps: PlanSettings): String {
        if (!ps.cwl.enabled) return "Tidak dihitung"
        val zone = java.time.ZoneId.systemDefault()
        val w = ps.cwl.next(System.currentTimeMillis())
        return if (ps.cwl.isKnown(w)) {
            "Asli ${id.clashtimer.core.Fmt.dateShort(w.first, zone)}–${id.clashtimer.core.Fmt.dateShort(w.last + 1, zone)}"
        } else "Lewati hero · ${ps.cwl.days} hari"
    }

    /** Ringkasan satu baris di Beranda: "💡 Seimbang · 🎫 20% · ⚔️ CWL" → buka lembar opsi. */
    fun summary(act: MainActivity, tag: String, bb: Boolean, ps: PlanSettings): String {
        val parts = ArrayList<String>()
        if (bb) {
            parts += "💡 " + if (Store.bobDone(act, tag)) "Max" else ps.bbStrategy.label
            if (ps.gold.builderPct > 0) parts += "🎫 ${ps.gold.builderPct}%"
        } else {
            parts += "💡 " + shortStrategy(ps.strategy)
            parts += "🎫 " + goldLabel(ps, false)
            parts += if (ps.cwl.enabled) "⚔️ CWL" else "⚔️ tanpa CWL"
        }
        if (ps.skipCollectors) parts += "⛏️ tanpa collector"
        val nPin = Store.pins(act, tag).size
        if (nPin > 0) parts += "📌 $nPin"
        return parts.joinToString(" · ")
    }

    /** Perkiraan tanggal target per strategi (dihitung di background, satu simulasi per strategi). */
    private val etaCache = HashMap<String, Long?>()

    private fun etaKey(act: MainActivity, tag: String, name: String) = "$tag|${Store.planVersion(act)}|$name"

    /** Lembar bawah "Opsi desa": Home/BB, strategi (dengan tanggal target), Gold Pass, CWL, collector, tindakan desa. */
    fun sheet(act: MainActivity, tag: String, bb0: Boolean) {
        val acc = Store.accounts(act).firstOrNull { it.tag == tag } ?: return
        var bb = bb0 && acc.bh > 0
        val dialog = Sheets.open(act, "Opsi ${name(act, tag)}", help = Help.Topic.OPTIONS) { col, dismiss ->
            val content = Ui.vbox(act)
            col.addView(content, Ui.matchWrap())
            lateinit var render: () -> Unit
            render = {
                content.removeAllViews()
                val ps = Store.planSettings(act, tag)
                if (acc.bh > 0) content.addView(Ui.segmentedWide(act, listOf("Home Village", "Builder Base"), if (bb) 1 else 0) {
                    bb = it == 1
                    render()
                }.apply { background = Ui.rounded(Ui.SURFACE2, Ui.dp(act, 12).toFloat()) }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })
                content.addView(label(act, "STRATEGI"))
                strategies(act, content, tag, bb, ps) { render() }
                content.addView(rows(act, tag, bb, ps), Ui.matchWrap().apply { topMargin = Ui.dp(act, 6) })
                footer(act, content, tag, dismiss)
            }
            render()
            act.onDataChanged = { render() }
        }
        dialog.setOnDismissListener { act.onDataChanged = null }
    }

    private fun label(act: MainActivity, text: String) = Ui.text(act, text, Ui.BODY_S).apply {
        textSize = 12f
        letterSpacing = 0.05f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setTextColor(Ui.TEXT2)
        setPadding(Ui.dp(act, 2), Ui.dp(act, 16), 0, Ui.dp(act, 8))
    }

    /** Kartu pilihan strategi: radio + nama + akibatnya ("Semua upgrade TH11 selesai ±7 Feb"). */
    private fun strategies(act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, ps: PlanSettings, refresh: () -> Unit) {
        val b = act.planFor(tag)
        val v = b?.village
        val zone = java.time.ZoneId.systemDefault()
        class Opt(val name: String, val title: String, val what: String, val selected: Boolean, val ps: PlanSettings, val pick: () -> Unit)
        val opts = ArrayList<Opt>()
        if (bb) {
            if (Store.bobDone(act, tag)) opts += Opt("BB_MAX", "Max · otomatis", "B.O.B sudah terbuka — semua Builder Base max", true, ps) {}
            else for (s in BBStrategy.entries) opts += Opt("BB_${s.name}", s.label,
                if (s == BBStrategy.SIXTH) "B.O.B terbuka" else "Semua Builder Base max", s == ps.bbStrategy, ps.withBB(s)) {
                Store.putPlan(act, "bb_strategy", s.name, tag)
            }
        } else {
            val th = v?.th ?: acc(act, tag)?.th ?: 0
            for (s in Strategy.entries) {
                val what = when (s) {
                    Strategy.MAX -> "Semua upgrade TH$th selesai"
                    Strategy.RUSH -> "TH${th + 1} mulai · pertahanan belakangan"
                    Strategy.HERO -> "Semua hero max"
                }
                opts += Opt(s.name, shortStrategy(s), what, s == ps.strategy, ps.with(strategy = s)) {
                    Store.putPlan(act, "strategy", s.name, tag)
                }
            }
        }
        val pending = ArrayList<Pair<Opt, android.widget.TextView>>()
        for (o in opts) {
            val card = Ui.hbox(act).apply {
                background = if (o.selected) Ui.rounded(Ui.FREE_BG, Ui.dp(act, 14).toFloat(), Ui.FREE_LINE, Ui.dp(act, 1))
                else Ui.rounded(android.graphics.Color.TRANSPARENT, Ui.dp(act, 14).toFloat(), Ui.OUTLINE, Ui.dp(act, 1))
                setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
                isClickable = true
                setOnClickListener {
                    if (!o.selected) {
                        o.pick()
                        act.dataChanged()
                        refresh()
                    }
                }
            }
            card.addView(View(act).apply {
                background = if (o.selected) android.graphics.drawable.LayerDrawable(arrayOf(
                    Ui.rounded(android.graphics.Color.TRANSPARENT, Ui.dp(act, 10).toFloat(), Ui.FREE, Ui.dp(act, 2)),
                    Ui.rounded(Ui.FREE, Ui.dp(act, 5).toFloat()),
                )).apply { setLayerInset(1, Ui.dp(act, 5), Ui.dp(act, 5), Ui.dp(act, 5), Ui.dp(act, 5)) }
                else Ui.rounded(android.graphics.Color.TRANSPARENT, Ui.dp(act, 10).toFloat(), Ui.OUTLINE, Ui.dp(act, 2))
            }, LinearLayout.LayoutParams(Ui.dp(act, 20), Ui.dp(act, 20)).apply { marginEnd = Ui.dp(act, 12) })
            val tc = Ui.vbox(act)
            tc.addView(Ui.text(act, o.title, Ui.BODY_M).apply {
                textSize = 15f
                setTextColor(Ui.TEXT)
                if (o.selected) setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
            val value = Ui.muted(act, o.what).apply { textSize = 13f }
            tc.addView(value)
            card.addView(tc, Ui.weight1())
            box.addView(card, Ui.matchWrap(8, act))
            // Tanggal target: strategi aktif dari rencana, lainnya disimulasikan.
            val known = when {
                o.selected && b != null -> {
                    val sim = if (bb) b.simBB else b.simHome
                    sim?.let { id.clashtimer.core.Planner.target(it, b.ps, bb).takeIf { t -> t != Long.MAX_VALUE } }
                }
                else -> etaCache[etaKey(act, tag, o.name)]
            }
            val cached = o.selected && b != null || etaCache.containsKey(etaKey(act, tag, o.name))
            if (cached) value.text = o.what + (known?.let { " ±" + Fmt.dateShort(it, zone) } ?: "")
            else if (b != null) pending += o to value
        }
        if (pending.isNotEmpty() && b != null) {
            pending.forEach { (o, tv) -> tv.text = o.what + " · menghitung…" }
            val app = act.applicationContext
            Thread {
                val gd = Repo.gameData(app)
                val now = System.currentTimeMillis()
                val fin = Repo.finishes(app, tag)
                val lanes = if (bb) setOf(id.clashtimer.core.Lane.BB_BUILDER, id.clashtimer.core.Lane.STAR_LAB)
                else setOf(id.clashtimer.core.Lane.BUILDER, id.clashtimer.core.Lane.LAB, id.clashtimer.core.Lane.PET)
                for ((o, tv) in pending) {
                    val t = runCatching {
                        val sim = id.clashtimer.core.Planner.simulate(b.village, gd, o.ps.withPolicies(b.ps.policies), now, fin, lanes, keepSteps = 1)
                        id.clashtimer.core.Planner.target(sim, o.ps, bb).takeIf { it != Long.MAX_VALUE }
                    }.getOrNull()
                    act.runOnUiThread {
                        etaCache[etaKey(act, tag, o.name)] = t
                        if (tv.isAttachedToWindow) tv.text = o.what + (t?.let { " ±" + Fmt.dateShort(it, zone) } ?: " · tidak tercapai")
                    }
                }
            }.start()
        }
    }

    private fun acc(act: MainActivity, tag: String) = Store.accounts(act).firstOrNull { it.tag == tag }

    /** Baris pengaturan: Gold Pass, CWL, collector, builder BB, prioritas. */
    private fun rows(act: MainActivity, tag: String, bb: Boolean, ps: PlanSettings): View {
        val col = Ui.vbox(act)
        fun row(title: String, auto: Boolean, value: String?, extra: View? = null, onClick: (() -> Unit)?) {
            val r = Ui.hbox(act).apply {
                minimumHeight = Ui.dp(act, 52)
                setPadding(Ui.dp(act, 2), Ui.dp(act, 8), Ui.dp(act, 2), Ui.dp(act, 8))
                if (onClick != null) {
                    isClickable = true
                    setOnClickListener { onClick() }
                    Ui.ripple(this)
                }
            }
            val t = Ui.hbox(act)
            t.addView(Ui.text(act, title, Ui.BODY_M).apply { textSize = 15f; setTextColor(Ui.TEXT) })
            if (auto) t.addView(android.widget.TextView(act).apply {
                text = "OTOMATIS"
                textSize = 10f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(Ui.FREE_TEXT)
                background = Ui.rounded(Ui.SELECTED_BG, Ui.dp(act, 6).toFloat())
                setPadding(Ui.dp(act, 6), Ui.dp(act, 1), Ui.dp(act, 6), Ui.dp(act, 1))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(act, 8) })
            r.addView(t, Ui.weight1())
            if (value != null) r.addView(Ui.muted(act, value).apply {
                textSize = 13f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = android.view.Gravity.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(act, 8) })
            if (extra != null) r.addView(extra)
            if (col.childCount > 0) col.addView(View(act).apply { setBackgroundColor(Ui.LINE) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 1)))
            col.addView(r, Ui.matchWrap())
        }
        row("Gold Pass", goldAuto(act, tag, ps, bb), goldLabel(ps, bb).let { if (it.endsWith("%") && !bb && !it.contains("·")) "Builder $it · Riset $it" else it } +
            (stepsText(act, tag)?.let { " · naik: " + it.substringBefore(" · ") } ?: gpEnd(act))) { pickGoldPass(act, tag) }
        if (bb) row("Builder Builder Base", false, if (ps.bbBuilders > 0) "${ps.bbBuilders} builder" else "otomatis dari export") { pickBbBuilders(act, tag) }
        else row("CWL", Store.cwlAuto(act, tag) != null, cwlLabel(act, tag, ps)) { pickCwl(act, tag) }
        val sw = androidx.appcompat.widget.SwitchCompat(act).apply {
            isChecked = !ps.skipCollectors
            setOnCheckedChangeListener { _, on ->
                Store.putPlan(act, "skip_collectors", !on, tag)
                act.dataChanged()
            }
        }
        row("Upgrade collector", false, null, sw) { sw.toggle() }
        val nPin = Store.pins(act, tag).size
        val nHide = Store.hides(act, tag).size
        row("Prioritas & disembunyikan", false, if (nPin + nHide == 0) "tahan lama sebuah upgrade" else "$nPin didahulukan · $nHide disembunyikan") { ItemMenu.manage(act, tag) }
        return col
    }

    private fun footer(act: MainActivity, box: LinearLayout, tag: String, dismiss: () -> Unit) {
        box.addView(Ui.tonal(act, "Update dari export") { dismiss(); act.importFromClipboard() }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 16) })
        val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, 0) }
        r.addView(Ui.secondary(act, "Ganti nama") { dismiss(); VillagesUi.rename(act, tag) }, Ui.weight1())
        r.addView(Ui.secondary(act, "Hapus desa") { dismiss(); VillagesUi.confirmDelete(act, tag) }.apply { setTextColor(Ui.IDLE) },
            Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
        box.addView(r, Ui.matchWrap())
        val links = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, 0) }
        links.addView(Ui.flat(act, "Potion & item") { dismiss(); act.openItems(tag) }.apply { isAllCaps = false; setTextColor(Ui.FREE_TEXT) }, Ui.weight1())
        links.addView(Ui.flat(act, "Pengaturan aplikasi") { dismiss(); act.openSettings() }.apply { isAllCaps = false; setTextColor(Ui.FREE_TEXT) }, Ui.weight1())
        box.addView(links, Ui.matchWrap())
    }

    private fun name(act: MainActivity, tag: String) = Store.villageName(act, tag)

    // ---------------------------------------------------------------- pemilih

    fun pickStrategy(act: MainActivity, tag: String) {
        val all = Strategy.entries
        val cur = Store.planSettings(act, tag).strategy
        val labels = all.map { "${it.label}\n${it.desc}" as CharSequence }.toTypedArray()
        MaterialAlertDialogBuilder(act)
            .setTitle("💡 Strategi · ${name(act, tag)}")
            .setSingleChoiceItems(labels, all.indexOf(cur)) { d, w ->
                Store.putPlan(act, "strategy", all[w].name, tag)
                d.dismiss()
                act.dataChanged()
            }
            .show()
    }

    fun pickBbStrategy(act: MainActivity, tag: String) {
        if (Store.bobDone(act, tag)) {
            MaterialAlertDialogBuilder(act)
                .setTitle("💡 Strategi Builder Base · ${name(act, tag)}")
                .setMessage("B.O.B sudah terbuka (terbaca dari export), jadi jalur builder ke-6 selesai dan saran Builder Base otomatis memakai strategi Max.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val all = BBStrategy.entries
        val cur = Store.planSettings(act, tag).bbStrategy
        val labels = all.map { "${it.label}\n${it.desc}" as CharSequence }.toTypedArray()
        MaterialAlertDialogBuilder(act)
            .setTitle("💡 Strategi Builder Base · ${name(act, tag)}")
            .setSingleChoiceItems(labels, all.indexOf(cur)) { d, w ->
                Store.putPlan(act, "bb_strategy", all[w].name, tag)
                d.dismiss()
                act.dataChanged()
            }
            .show()
    }

    fun pickBbBuilders(act: MainActivity, tag: String) {
        val cur = Store.planSettings(act, tag).bbBuilders
        val options = intArrayOf(0, 1, 2, 3)
        val labels = options.map { (if (it == 0) "Otomatis dari export" else "$it builder") as CharSequence }.toTypedArray()
        MaterialAlertDialogBuilder(act)
            .setTitle("🔨 Builder di Builder Base · ${name(act, tag)}")
            .setSingleChoiceItems(labels, options.indexOf(cur).coerceAtLeast(0)) { d, w ->
                Store.putPlan(act, "bb_builders", options[w], tag)
                d.dismiss()
                act.dataChanged()
            }
            .show()
    }

    fun pickCwl(act: MainActivity, tag: String) {
        Store.cwlAuto(act, tag)?.let { a ->
            val zone = java.time.ZoneId.systemDefault()
            val known = Store.cwlKnown(act, tag)
            val dates = known?.let { " (${Fmt.dateShort(it.first, zone)}–${Fmt.dateShort(it.last + 1, zone)})" } ?: ""
            val text = when (a.state) {
                "roster" -> "Terdaftar di CWL clan ${a.clan}$dates. Upgrade hero yang bentrok dengan CWL dilewati."
                "notroster" -> "Clan ${a.clan} sedang CWL, tetapi akun ini tidak ada di roster — hero bebas di-upgrade."
                "optout" -> "War preference akun ini OUT — CWL tidak dihitung, hero bebas di-upgrade."
                "noclan" -> "Akun ini tidak di clan — CWL tidak dihitung."
                else -> "Clan ${a.clan} belum ikut CWL musim ini. CWL berikutnya diperkirakan mulai tgl 1 (hero yang bentrok dilewati) sampai jadwal aslinya muncul."
            }
            MaterialAlertDialogBuilder(act)
                .setTitle("⚔️ CWL · ${name(act, tag)} · otomatis")
                .setMessage("$text\n\nDibaca dari API (roster CWL clan & war preference) setiap import dan saat aplikasi dibuka — terakhir ${Fmt.at(a.at, zone)}.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val ps = Store.planSettings(act, tag)
        val days = (7..12).toList()
        val labels = (listOf("Tidak dihitung — hero boleh di-upgrade kapan saja") +
            days.map { "Lewati upgrade hero yang bentrok · CWL $it hari" }).map { it as CharSequence }.toTypedArray()
        val cur = if (!ps.cwl.enabled) 0 else days.indexOf(ps.cwl.days).coerceAtLeast(0) + 1
        val start = java.time.ZonedDateTime.of(2026, 1, 1, ps.cwl.startUtcHour, 0, 0, 0, java.time.ZoneOffset.UTC)
            .withZoneSameInstant(java.time.ZoneId.systemDefault())
        val clock = id.clashtimer.core.Fmt.clock(start.hour * 60 + start.minute)
        MaterialAlertDialogBuilder(act)
            .setTitle("⚔️ CWL · ${name(act, tag)} (mulai tgl 1, $clock)")
            .setSingleChoiceItems(labels, cur) { d, w ->
                if (w == 0) Store.putPlan(act, "cwl_on", false, tag)
                else {
                    Store.putPlan(act, "cwl_on", true, tag)
                    Store.putPlan(act, "cwl_days", days[w - 1], tag)
                }
                d.dismiss()
                act.dataChanged()
            }
            .show()
    }

    fun pickCollectors(act: MainActivity, tag: String) {
        val cur = Store.planSettings(act, tag).skipCollectors
        val labels = arrayOf<CharSequence>(
            "Di-upgrade seperti biasa",
            "Jangan di-upgrade\nGold Mine, Elixir Collector & Dark Elixir Drill (juga di Builder Base) tidak disarankan dan tidak dihitung di progres",
        )
        MaterialAlertDialogBuilder(act)
            .setTitle("⛏️ Collector · ${name(act, tag)}")
            .setSingleChoiceItems(labels, if (cur) 1 else 0) { d, w ->
                Store.putPlan(act, "skip_collectors", w == 1, tag)
                d.dismiss()
                act.dataChanged()
            }
            .show()
    }

    /** "Builder 15% ±Rab 4 Nov · Research 20% ±Sen 9 Nov" dari kenaikan yang diperkirakan. */
    fun stepsText(act: MainActivity, tag: String): String? {
        val g = Store.planSettings(act, tag).gold
        if (g.steps.isEmpty()) return null
        val now = System.currentTimeMillis()
        var b = g.builderPct
        var r = g.researchPct
        val parts = ArrayList<String>()
        for (s in g.steps) {
            val at = HomeView.short(s.at, now, act.zoneId())
            if (s.builderPct > b) parts += "Builder ${s.builderPct}% ±$at"
            if (s.researchPct > r) parts += "Research ${s.researchPct}% ±$at"
            b = maxOf(b, s.builderPct)
            r = maxOf(r, s.researchPct)
        }
        return parts.joinToString(" · ").ifBlank { null }
    }

    /** Bagian "Naik bertahap": poin track sekarang → kapan boost 15% / 20% terbuka. */
    private fun staged(act: MainActivity, col: LinearLayout, tag: String, stored: id.clashtimer.core.GoldPass, refresh: () -> Unit) {
        col.addView(Ui.text(act, "📈 Naik bertahap (poin track)", Ui.BODY_M).apply { setPadding(0, Ui.dp(act, 16), 0, Ui.dp(act, 2)) })
        val track = Store.gpTrack(act)
        val last = Store.gpPoints(act, tag).lastOrNull()
        val zone = java.time.ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val text = when {
            stored.builderPct == 0 && stored.researchPct == 0 ->
                "Pilih persen di atas dulu (akun ini punya Gold Pass), lalu isi poin track supaya 15% & 20% dihitung otomatis."
            last == null ->
                "Boost 15% terbuka di ${track.r15} (Research) & ${track.b15} poin (Builder), 20% di ${track.r20} & ${track.b20} poin. " +
                    "Isi poin track sekarang: rencana memakai persen yang naik di perkiraan tanggalnya."
            else -> {
                val g = Store.planSettings(act, tag).gold
                val rate = Store.gpRate(act, tag).toInt()
                val auto = Store.gpRateManual(act, tag) == 0
                "Poin ${last.second} (${HomeView.short(last.first, now, zone)}) · ±$rate poin/hari" + (if (auto) " (perkiraan)" else "") +
                    "\nSekarang: Builder ${g.builderPct}% · Research ${g.researchPct}%" +
                    (stepsText(act, tag)?.let { "\nNaik: $it" } ?: "\nSudah 20% semua, atau musim berakhir sebelum ambang berikutnya.")
            }
        }
        col.addView(Ui.muted(act, text))
        if (stored.builderPct == 0 && stored.researchPct == 0) return
        val row = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 6), 0, 0) }
        fun btn(label: String, onClick: () -> Unit) {
            row.addView(Ui.outlined(act, label, onClick).apply { isAllCaps = false; minWidth = 0; minimumWidth = 0 },
                Ui.weight1().apply { if (row.childCount > 0) marginStart = Ui.dp(act, 4) })
        }
        btn("Isi poin") {
            numbers(act, "Poin Gold Pass sekarang", listOf("poin" to (last?.second ?: 0))) { v ->
                Store.addGpPoints(act, tag, v[0])
                refresh()
            }
        }
        btn("Poin/hari") {
            numbers(act, "Poin per hari (0 = otomatis)", listOf("poin/hari" to Store.gpRateManual(act, tag))) { v ->
                Store.setGpRateManual(act, tag, v[0])
                refresh()
            }
        }
        btn("Ambang") {
            numbers(act, "Ambang poin musim ini (semua desa)", listOf(
                "Research 15%" to track.r15, "Builder 15%" to track.b15, "Research 20%" to track.r20, "Builder 20%" to track.b20,
            )) { v ->
                Store.setGpTrack(act, id.clashtimer.core.GpTrack(v[0], v[1], v[2], v[3]))
                refresh()
            }
        }
        col.addView(row)
    }

    /** Dialog isian angka sederhana. */
    private fun numbers(act: MainActivity, title: String, fields: List<Pair<String, Int>>, onSave: (List<Int>) -> Unit) {
        val box = Ui.vbox(act).apply { setPadding(Ui.dp(act, 24), Ui.dp(act, 8), Ui.dp(act, 24), 0) }
        val edits = fields.map { (label, value) ->
            val line = Ui.hbox(act)
            line.addView(Ui.text(act, label, Ui.BODY_M), Ui.weight1())
            val e = android.widget.EditText(act).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(value.toString())
                setSelectAllOnFocus(true)
                gravity = android.view.Gravity.END
                minEms = 4
            }
            line.addView(e)
            box.addView(line, Ui.matchWrap())
            e
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(title)
            .setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                onSave(edits.mapIndexed { i, e -> e.text.toString().trim().toIntOrNull()?.coerceAtLeast(0) ?: fields[i].second })
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    /** Builder Boost & Research Boost dalam satu dialog. */
    fun pickGoldPass(act: MainActivity, tag: String) {
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 20), Ui.dp(act, 4), Ui.dp(act, 20), 0) }
        val options = intArrayOf(0, 10, 15, 20)
        lateinit var fill: () -> Unit
        fill = {
            col.removeAllViews()
            // Nilai yang tersimpan (deteksi / pilihan manual), tanpa perkiraan kenaikan dari poin.
            val gold = id.clashtimer.core.GoldPass(Store.getInt(act, "gp_builder", 0, tag), Store.getInt(act, "gp_research", 0, tag))
            val zone = java.time.ZoneId.systemDefault()
            val seasonStart = Store.goldPassStart(act)
            fun line(label: String, d: Store.GoldDetected?): String = when {
                d == null -> "$label: belum terdeteksi"
                d.guessed -> "$label: ${d.pct}% (perkiraan, sama dengan boost lain dari ${d.title})"
                else -> "$label: ${d.pct}% dari ${d.title}, ${Fmt.dateShort(d.at, zone)}" +
                    if (seasonStart > 0 && d.at < seasonStart) " (musim lalu)" else ""
            }
            col.addView(Ui.text(act, "🔎 Deteksi otomatis", Ui.BODY_M).apply { setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 2)) })
            col.addView(Ui.muted(act, line("Builder Boost", Store.goldDetected(act, tag, false)) + "\n" +
                line("Research Boost", Store.goldDetected(act, tag, true))))
            col.addView(Ui.muted(act, "Export & API tidak memuat status Gold Pass, jadi aplikasi membacanya dari lama timer saat import: " +
                "upgrade yang ditandai \"Sudah dimulai\" atau yang baru dimulai sejak import sebelumnya dibandingkan dengan durasi aslinya. " +
                "Nilai di bawah terisi sendiri; ubah manual hanya bila keliru.").apply { setPadding(0, Ui.dp(act, 4), 0, 0) })
            val rows = listOf(
                Triple("Builder Boost (Home Village & Builder Base)", "gp_builder", gold.builderPct),
                Triple("Research Boost (Laboratory & Pet House)", "gp_research", gold.researchPct),
            )
            for ((label, key, cur) in rows) {
                col.addView(Ui.text(act, label, Ui.BODY_M).apply { setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 4)) })
                val row = Ui.hbox(act)
                options.forEachIndexed { i, pct ->
                    val text = "$pct%"
                    val b: MaterialButton = if (pct == cur) Ui.filled(act, text) {}
                    else Ui.outlined(act, text) {
                        Store.putPlan(act, key, pct, tag)
                        fill()
                    }
                    b.isAllCaps = false
                    b.minWidth = 0
                    b.minimumWidth = 0
                    b.setPadding(0, b.paddingTop, 0, b.paddingBottom)
                    row.addView(b, Ui.weight1().apply { if (i > 0) marginStart = Ui.dp(act, 4) })
                }
                col.addView(row)
            }
            col.addView(Ui.muted(act, "Ubah manual: lihat persentasenya di menu Gold Pass akun ini. Hanya untuk ${name(act, tag)}.").apply {
                setPadding(0, Ui.dp(act, 8), 0, 0)
            })
            staged(act, col, tag, gold) { fill() }
        }
        fill()
        MaterialAlertDialogBuilder(act)
            .setTitle("🎫 Gold Pass · ${name(act, tag)}")
            .setView(android.widget.ScrollView(act).apply { addView(col) })
            .setPositiveButton("Selesai", null)
            .setOnDismissListener { act.dataChanged() }
            .show()
    }
}
