package id.clashtimer

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.BBStrategy
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.Lane
import id.clashtimer.core.PlannedStep
import id.clashtimer.core.SixthPlan
import id.clashtimer.core.Strategy
import java.time.Instant
import java.time.ZoneId

/** Tab "Rencana": antrean upgrade per pekerja, seperti ClashClock. */
object PlanView {

    fun render(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val ps = b.ps

        strategySwitch(act, box, b, bb)
        chips(act, box, b, bb, now, zone)

        val sim = if (bb) b.simBB else b.simHome
        if (sim == null) {
            box.addView(Ui.muted(act, "Belum ada data untuk rencana ini.", Ui.BODY_M).apply { setPadding(Ui.dp(act, 4), Ui.dp(act, 16), 0, 0) })
            return
        }
        if (bb) sim.sixth?.let { sixthCard(act, box, it, now, zone) }
        needsCard(act, box, sim, now, zone)

        val running = sim.running.filter { it.lane.bb == bb }.sortedBy { it.finish }
        val steps = sim.steps.filter { it.lane.bb == bb }
        if (running.isEmpty() && steps.isEmpty()) {
            box.addView(Ui.muted(act, "Tidak ada upgrade yang bisa direncanakan — semua sudah max atau menunggu syarat.", Ui.BODY_M).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 16), 0, 0)
            })
        }

        // --- kelompok waktu
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        fun group(t: Long): Int {
            val d = Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
            return when {
                !d.isAfter(today) -> 0
                d == today.plusDays(1) -> 1
                d.isBefore(today.plusDays(7)) -> 2
                else -> 3
            }
        }
        val titles = listOf("Hari ini", "Besok", "Minggu ini", "Nanti")
        val grouped = steps.groupBy { group(it.start) }

        if (running.isNotEmpty()) {
            section(act, box, "Sedang berjalan", "${running.size} pekerja", running.map { stepRow(act, b, it, now, zone) })
        }
        for (g in 0..3) {
            val list = grouped[g].orEmpty()
            if (list.isEmpty()) continue
            val shown = if (g == 3) list.take(30) else list
            val views = shown.map { stepRow(act, b, it, now, zone) }.toMutableList<View>()
            if (shown.size < list.size) views += Ui.muted(act, "… dan ${list.size - shown.size} langkah lagi").apply {
                setPadding(Ui.dp(act, 14), Ui.dp(act, 8), 0, Ui.dp(act, 8))
            }
            section(act, box, titles[g], "${list.size} langkah", views)
        }

        if (!bb && sim.builderIdle7d > 0) {
            box.addView(Ui.muted(act, "💤 Builder menganggur 7 hari ke depan karena Anda offline: ±${Fmt.dur(sim.builderIdle7d)}", Ui.BODY_M).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 4), 0)
            })
        }
        val extra = if (ps.skipCollectors) " Collector tidak disarankan (Pengaturan)." else ""
        box.addView(Ui.muted(act,
            "Rencana = perkiraan bila setiap pekerja langsung diisi saran teratas saat Anda online (jam tidur & helper dihitung).$extra " +
                "Dihitung ${Fmt.time(b.computedAt, zone)}. Ketuk langkah untuk detail; setelah memulai di game tekan \"Sudah dimulai\".").apply {
            setPadding(Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4), 0)
        })
    }

    private fun section(act: MainActivity, box: LinearLayout, title: String, right: String, rows: List<View>) {
        box.addView(Ui.sectionRow(act, title, right))
        val (card, col) = Ui.card(act, padH = 0, padV = 0)
        rows.forEachIndexed { i, v ->
            if (i > 0) col.addView(Ui.divider(act))
            col.addView(v)
        }
        box.addView(card)
    }

    // ---------------------------------------------------------------- strategi

    private fun shortLabel(s: Strategy) = when (s) {
        Strategy.MAX -> "Seimbang"
        Strategy.RUSH -> "Rush TH"
        Strategy.HERO -> "Hero dulu"
    }

    private fun strategySwitch(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean) {
        val row = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 4), 0, 0) }
        fun button(label: String, selected: Boolean, first: Boolean, onClick: () -> Unit) {
            val btn: MaterialButton = if (selected) Ui.filled(act, label) {} else Ui.outlined(act, label) { onClick() }
            btn.isAllCaps = false
            btn.maxLines = 1
            row.addView(btn, Ui.weight1().apply { if (!first) marginStart = Ui.dp(act, 6) })
        }
        val desc: String
        if (bb && Store.bobDone(act, b.tag)) {
            // B.O.B sudah terbuka: tidak ada lagi yang perlu dipilih.
            button("Max · otomatis", true, true) {}
            desc = "B.O.B sudah terbuka (terbaca dari export), jadi saran Builder Base otomatis memakai strategi Max."
        } else if (bb) {
            val cur = b.ps.bbStrategy
            BBStrategy.entries.forEachIndexed { i, s ->
                button(s.label, s == cur, i == 0) {
                    Store.putPlan(act, "bb_strategy", s.name, b.tag)
                    act.dataChanged()
                }
            }
            desc = cur.desc
        } else {
            val cur = b.ps.strategy
            Strategy.entries.forEachIndexed { i, s ->
                button(shortLabel(s), s == cur, i == 0) {
                    Store.putPlan(act, "strategy", s.name, b.tag)
                    act.dataChanged()
                }
            }
            desc = cur.desc
        }
        box.addView(row, Ui.matchWrap())
        box.addView(Ui.muted(act, desc).apply { setPadding(Ui.dp(act, 4), Ui.dp(act, 2), Ui.dp(act, 4), Ui.dp(act, 4)) })
    }

    private fun chips(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean, now: Long, zone: ZoneId) {
        val ps = b.ps
        val s = ps.schedule
        val chips = Ui.hbox(act)
        fun chip(text: String, onClick: () -> Unit) {
            chips.addView(Ui.chip(act, text, onClick), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = Ui.dp(act, 6) })
        }
        if (s.sleep.enabled) chip("😴 ${Fmt.clock(s.sleep.startMin)}–${Fmt.clock(s.sleep.endMin)}") { act.openSettings() }
        else chip("😴 atur jam tidur") { act.openSettings() }
        if (s.work.enabled) chip("💼 ${Fmt.clock(s.work.startMin)}–${Fmt.clock(s.work.endMin)}") { act.openSettings() }
        chip("🎫 Gold Pass ${ps.gold.builderPct}%") { VillageOptions.pickGoldPass(act, b.tag) }
        for (e in ps.events.filter { it.enabled && it.end > now && (if (bb) it.bb else it.builder || it.lab) }) {
            val label = if (e.start <= now) "🎉 ${e.name} s/d ${Fmt.dateShort(e.end, zone)}" else "🎉 ${e.name} ${Fmt.dateShort(e.start, zone)}"
            chip(label) { act.openSettings() }
        }
        if (!bb) {
            if (ps.cwl.enabled) {
                val w = ps.cwl.next(now)
                chip("⚔️ CWL ${Fmt.dateShort(w.first, zone)}–${Fmt.dateShort(w.last + 1, zone)}") { VillageOptions.pickCwl(act, b.tag) }
            } else chip("⚔️ CWL tidak dihitung") { VillageOptions.pickCwl(act, b.tag) }
        }
        chip(if (ps.skipCollectors) "⛏️ Tanpa collector" else "⛏️ Collector di-upgrade") { VillageOptions.pickCollectors(act, b.tag) }
        val hs = HorizontalScrollView(act).apply {
            isHorizontalScrollBarEnabled = false
            addView(chips)
            setPadding(0, Ui.dp(act, 2), 0, Ui.dp(act, 2))
        }
        box.addView(hs, Ui.matchWrap())
    }

    // ---------------------------------------------------------------- kebutuhan sumber daya

    /** Rentang yang dipilih: 0 = 24 jam, 1 = 3 hari, 2 = 7 hari. */
    private var needWindow = 0

    private val resourceOrder = listOf("Gold", "Elixir", "Dark Elixir", "Builder Gold", "Builder Elixir")

    private fun needsCard(act: MainActivity, box: LinearLayout, sim: id.clashtimer.core.SimResult, now: Long, zone: ZoneId) {
        val needs = sim.needs
        if (needs.isEmpty()) return
        box.addView(Ui.sectionRow(act, "💰 Kebutuhan sumber daya", "upgrade yang dimulai"))
        val (card, col) = Ui.card(act, padV = 10)
        val seg = Ui.hbox(act)
        listOf("24 jam", "3 hari", "7 hari").forEachIndexed { i, label ->
            val btn: MaterialButton = if (i == needWindow) Ui.filled(act, label) {} else Ui.outlined(act, label) {
                needWindow = i
                act.renderBody()
            }
            btn.isAllCaps = false
            btn.minWidth = 0
            btn.minimumWidth = 0
            seg.addView(btn, Ui.weight1().apply { if (i > 0) marginStart = Ui.dp(act, 6) })
        }
        col.addView(seg)
        val n = needs.getOrNull(needWindow) ?: needs.first()
        if (n.costs.isEmpty()) {
            col.addView(Ui.muted(act, "Tidak ada upgrade yang dimulai pada rentang ini — pekerja masih sibuk.", Ui.BODY_M).apply {
                setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 4))
            })
        } else {
            val sorted = n.costs.entries.sortedWith(compareBy({ resourceOrder.indexOf(it.key).let { i -> if (i < 0) 99 else i } }, { -it.value }))
            for ((res, cost) in sorted) {
                val line = Ui.vbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, 0) }
                val head = Ui.hbox(act)
                head.addView(Ui.text(act, Fmt.resource(res), Ui.TITLE_S), Ui.weight1())
                head.addView(Ui.text(act, Fmt.money(cost), Ui.TITLE_S).apply { setTypeface(typeface, Typeface.BOLD) })
                line.addView(head)
                n.top[res]?.let { t ->
                    line.addView(Ui.muted(act, "termahal: ${t.title} · ${Fmt.money(t.cost)} · ${HomeView.short(t.start, now, zone)}"))
                }
                col.addView(line)
            }
        }
        col.addView(Ui.muted(act, "Sesuai rencana di bawah; Gold Pass & event sudah dihitung. Pastikan gudang cukup untuk upgrade termahal.").apply {
            setPadding(0, Ui.dp(act, 8), 0, 0)
        })
        box.addView(card)
    }

    // ---------------------------------------------------------------- builder ke-6

    private fun sixthCard(act: MainActivity, box: LinearLayout, six: SixthPlan, now: Long, zone: ZoneId) {
        box.addView(Ui.sectionRow(act, "Menuju builder ke-6 (B.O.B)", "${six.reqs.count { it.done }}/${six.reqs.size}"))
        val (card, col) = Ui.card(act, padV = 12)
        val eta = six.doneAt
        if (eta != null) {
            col.addView(Ui.muted(act, "Perkiraan B.O.B terbuka", Ui.BODY_M))
            col.addView(Ui.text(act, Fmt.date(eta, zone), Ui.TITLE_L).apply { textSize = 24f; setTextColor(Ui.GOLD) })
            val days = Math.round((eta - now) / DAY.toDouble())
            col.addView(Ui.muted(act, if (days <= 0) "hari ini" else "±$days hari lagi"))
        } else {
            col.addView(Ui.text(act, "Belum bisa diperkirakan", Ui.TITLE_S).apply { setTextColor(Ui.AMBER) })
            six.blockers.forEach { col.addView(Ui.muted(act, "• $it")) }
        }
        col.addView(Ui.divider(act).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = Ui.dp(act, 10) })
        for (r in six.reqs) {
            val line = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, 0); gravity = Gravity.TOP }
            line.addView(Ui.text(act, if (r.done) "✅" else "⬜", Ui.BODY_M), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = Ui.dp(act, 8) })
            val c = Ui.vbox(act)
            c.addView(Ui.text(act, r.label, Ui.TITLE_S).apply { textSize = 14f })
            c.addView(Ui.muted(act, r.detail))
            line.addView(c, Ui.weight1())
            val right = when {
                r.done -> "selesai"
                six.eta[r.key] != null -> "±" + Fmt.dateShort(six.eta[r.key]!!, zone)
                else -> "–"
            }
            line.addView(Ui.text(act, right, Ui.BODY_S).apply {
                setTextColor(if (r.done) Ui.GREEN else Ui.secondary(act))
                setPadding(Ui.dp(act, 8), 0, 0, 0)
            })
            col.addView(line)
        }
        col.addView(Ui.muted(act,
            "Perkiraan tanpa Clock Tower boost, book & gem. Gear-up dikerjakan Master Builder di Home Village " +
                "(bayar Gold Home Village) dan butuh level bangunan di kedua desa. Level B.O.B Control diklaim gratis tiap syarat terpenuhi.").apply {
            setPadding(0, Ui.dp(act, 10), 0, 0)
        })
        box.addView(card)
    }

    // ---------------------------------------------------------------- baris antrean

    private fun laneLabel(s: PlannedStep): Pair<String, Int> = when (s.lane) {
        Lane.BUILDER -> "B${s.laneIndex}" to Ui.BLUE
        Lane.LAB -> "Lab" to Ui.VIOLET
        Lane.PET -> "Pet" to Ui.VIOLET
        Lane.BB_BUILDER -> "M${s.laneIndex}" to Ui.BLUE
        Lane.STAR_LAB -> "Star Lab" to Ui.VIOLET
    }

    private fun title(s: PlannedStep): String {
        val name = s.item?.name ?: s.title
        return when {
            s.gear -> "$name · Gear-up"
            s.isNew && s.fromLevel == 0 -> "Bangun $name"
            s.isNew -> "Buka $name"
            else -> "$name ${s.fromLevel} → ${s.toLevel}"
        }
    }

    private fun stepRow(act: MainActivity, b: PlanBundle, s: PlannedStep, now: Long, zone: ZoneId): View {
        val line = Ui.hbox(act).apply {
            setPadding(Ui.dp(act, 12), Ui.dp(act, 9), Ui.dp(act, 12), Ui.dp(act, 9))
            gravity = Gravity.TOP
            isClickable = true
            setOnClickListener { details(act, b, s, now, zone) }
        }
        ItemMenu.attach(act, line, b.tag, s.item?.id)
        val name = s.item?.name ?: s.title
        val iconLevel = if (s.gear) s.fromLevel else s.fromLevel.coerceAtLeast(1)
        line.addView(Icons.view(act, Icons.forItem(s.item, iconLevel), 38, Icons.initials(name),
            if (s.running) Ui.GREEN else Ui.GREY))

        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 8), 0) }
        col.addView(Ui.text(act, title(s), Ui.TITLE_S).apply { textSize = 14f })
        val sub = if (s.running) "Berjalan · selesai ${HomeView.short(s.finish, now, zone)}"
        else {
            val cost = if (s.cost > 0) " · ${Fmt.money(s.cost)} ${Fmt.resource(s.item?.resource ?: "")}" else ""
            "${Fmt.durSec(s.durationSec)}$cost · selesai ${HomeView.short(s.finish, s.start, zone)}"
        }
        col.addView(Ui.muted(act, sub))
        val warn = listOfNotNull(
            if (!s.running && s.idle > 30 * 60_000L) "💤 nganggur ${Fmt.dur(s.idle)} setelah selesai" else null,
            s.note?.let { "⚔️ $it" },
            s.event?.let { "🎉 $it" },
        )
        if (warn.isNotEmpty()) col.addView(Ui.text(act, warn.joinToString(" · "), Ui.BODY_S).apply {
            setTextColor(if (s.event != null && s.note == null && s.idle <= 30 * 60_000L) Ui.VIOLET else Ui.AMBER)
        })
        if (s.gear) col.addView(Ui.text(act, "Home Village · Master Builder", Ui.BODY_S).apply { setTextColor(Ui.GOLD) })
        line.addView(col, Ui.weight1())

        val right = Ui.vbox(act).apply { gravity = Gravity.END }
        val t = if (s.running) s.finish else s.start
        right.addView(Ui.text(act, if (!s.running && s.start <= now + 60_000L) "sekarang" else HomeView.short(t, now, zone), Ui.TITLE_S).apply {
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.END
        })
        val (lane, color) = laneLabel(s)
        right.addView(Ui.pill(act, lane, color), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = Ui.dp(act, 4); gravity = Gravity.END })
        line.addView(right)
        return line
    }

    private fun details(act: MainActivity, b: PlanBundle, s: PlannedStep, now: Long, zone: ZoneId) {
        val (lane, _) = laneLabel(s)
        val msg = StringBuilder()
        if (s.running) {
            msg.append("Sedang berjalan di $lane.\nSelesai: ${Fmt.at(s.finish, zone)}")
        } else {
            msg.append("Pekerja: $lane\nMulai: ${Fmt.at(s.start, zone)}\nSelesai: ${Fmt.at(s.finish, zone)}\nDurasi: ${Fmt.durSec(s.durationSec)}")
            if (s.cost > 0) msg.append("\nBiaya: ${Fmt.money(s.cost)} ${Fmt.resource(s.item?.resource ?: "")}")
            if (s.idle > 30 * 60_000L) msg.append("\n\n💤 Selesai saat Anda offline; pekerja menganggur ±${Fmt.dur(s.idle)}.")
            s.event?.let { msg.append("\n\n🎉 Dimulai saat $it — waktu & biaya sudah dipotong.") }
            s.note?.let { msg.append("\n\n⚔️ $it") }
            if (s.tier > 0) msg.append("\n\n🏗️ Langkah menuju builder ke-6.")
            if (s.gear) msg.append("\n\nGear-up memakai Master Builder dari Builder Base dan bekerja di Home Village.")
        }
        // Bisa langsung ditandai dimulai bila pekerjanya bebas sekarang
        val pick = if (!s.running && s.start <= now + 5 * 60_000L) {
            b.slots.filter { it.lane == s.lane && it.freeAt <= now }.flatMap { it.ranked }
                .firstOrNull { it.c.item.id == s.item?.id && it.c.fromLevel == s.fromLevel && it.c.gear == s.gear }
        } else null
        val dlg = MaterialAlertDialogBuilder(act).setTitle(title(s)).setMessage(msg.toString())
            .setNegativeButton("Tutup", null)
        if (pick != null) dlg.setPositiveButton("Sudah dimulai") { _, _ -> act.startPick(b.tag, pick) }
        dlg.show()
    }
}
