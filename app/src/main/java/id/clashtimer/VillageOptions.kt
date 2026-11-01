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
 * Opsi per desa di Beranda: strategi saran, Gold Pass, CWL, collector, builder Builder Base.
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

    /** Kartu "Opsi desa ini" (2 × 2 kotak). */
    fun card(act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, ps: PlanSettings) {
        box.addView(Ui.sectionRow(act, "Opsi desa ini", "ketuk untuk mengubah"))
        val (card, col) = Ui.card(act, padH = 10, padV = 10)
        val tiles = ArrayList<View>()
        val auto = " · otomatis"
        val gpLabel = "🎫 Gold Pass" + if (goldAuto(act, tag, ps, bb)) auto else ""
        if (bb) {
            val bob = Store.bobDone(act, tag)
            tiles += tile(act, "💡 Strategi BB" + if (bob) auto else "", if (bob) "Max (B.O.B terbuka)" else ps.bbStrategy.label) { pickBbStrategy(act, tag) }
            tiles += tile(act, "🔨 Builder BB", if (ps.bbBuilders > 0) "${ps.bbBuilders} builder" else "Otomatis") { pickBbBuilders(act, tag) }
            tiles += tile(act, gpLabel, goldLabel(ps, true) + gpEnd(act)) { pickGoldPass(act, tag) }
            tiles += tile(act, "⛏️ Collector", if (ps.skipCollectors) "Tidak di-upgrade" else "Di-upgrade") { pickCollectors(act, tag) }
        } else {
            tiles += tile(act, "💡 Strategi", shortStrategy(ps.strategy)) { pickStrategy(act, tag) }
            tiles += tile(act, gpLabel, goldLabel(ps, false) + gpEnd(act)) { pickGoldPass(act, tag) }
            val cwlAuto = Store.cwlAuto(act, tag) != null
            tiles += tile(act, "⚔️ CWL" + if (cwlAuto) auto else "", cwlLabel(act, tag, ps)) { pickCwl(act, tag) }
            tiles += tile(act, "⛏️ Collector", if (ps.skipCollectors) "Tidak di-upgrade" else "Di-upgrade") { pickCollectors(act, tag) }
        }
        tiles.chunked(2).forEachIndexed { i, pair ->
            val row = Ui.hbox(act).apply { if (i > 0) setPadding(0, Ui.dp(act, 8), 0, 0) }
            pair.forEachIndexed { j, t -> row.addView(t, Ui.weight1().apply { if (j > 0) marginStart = Ui.dp(act, 8) }) }
            col.addView(row)
        }
        val nPin = Store.pins(act, tag).size
        val nHide = Store.hides(act, tag).size
        col.addView(tile(act, "📌 Prioritas & disembunyikan",
            if (nPin + nHide == 0) "Tahan lama sebuah upgrade" else "$nPin didahulukan · $nHide disembunyikan") { ItemMenu.manage(act, tag) },
            Ui.matchWrap().apply { topMargin = Ui.dp(act, 8) })
        val api = if (Store.apiKey(act).isNotBlank()) "CWL & upgrade yang selesai lebih cepat (API)" else "CWL bila API key diisi di Pengaturan"
        col.addView(Ui.muted(act, "🔎 Otomatis: jumlah builder, helper, B.O.B & gear-up (export), Gold Pass (dari lama timer saat import), $api. " +
            "Strategi & collector sesuai pilihan Anda.").apply { setPadding(Ui.dp(act, 2), Ui.dp(act, 8), Ui.dp(act, 2), 0) })
        box.addView(card)
    }

    private fun tile(act: MainActivity, label: String, value: String, onClick: () -> Unit): View {
        val v = Ui.vbox(act).apply {
            background = Ui.tint(Ui.GREY, Ui.dp(act, 10).toFloat(), 0x1A)
            setPadding(Ui.dp(act, 10), Ui.dp(act, 8), Ui.dp(act, 10), Ui.dp(act, 8))
            isClickable = true
            setOnClickListener { onClick() }
        }
        v.addView(Ui.muted(act, label))
        v.addView(Ui.text(act, "$value ▾", Ui.TITLE_S).apply {
            textSize = 13f
            setTextColor(Ui.primary(act))
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        return v
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

    /** Builder Boost & Research Boost dalam satu dialog. */
    fun pickGoldPass(act: MainActivity, tag: String) {
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 20), Ui.dp(act, 4), Ui.dp(act, 20), 0) }
        val options = intArrayOf(0, 10, 15, 20)
        lateinit var fill: () -> Unit
        fill = {
            col.removeAllViews()
            val gold = Store.planSettings(act, tag).gold
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
        }
        fill()
        MaterialAlertDialogBuilder(act)
            .setTitle("🎫 Gold Pass · ${name(act, tag)}")
            .setView(col)
            .setPositiveButton("Selesai", null)
            .setOnDismissListener { act.dataChanged() }
            .show()
    }
}
