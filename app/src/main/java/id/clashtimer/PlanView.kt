package id.clashtimer

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import id.clashtimer.core.BBStrategy
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.Lane
import id.clashtimer.core.PlannedStep
import id.clashtimer.core.Planner
import id.clashtimer.core.SimResult
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Tab "Jadwal": kapan perlu login dan apa yang dimulai tiap sesi (Agenda), atau semua pekerja di garis
 * waktu (Timeline). Strategi dipilih di lembar Opsi desa.
 */
object PlanView {

    fun render(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val ps = b.ps
        val sim = if (bb) b.simBB else b.simHome
        if (sim == null) {
            box.addView(Ui.muted(act, "Belum ada data untuk rencana ini.", Ui.BODY_M).apply { setPadding(Ui.dp(act, 4), Ui.dp(act, 16), 0, 0) })
            return
        }
        val mode = pref(act, "plan_mode")
        modeRow(act, box, mode)
        targetCard(act, box, b, sim, bb, zone)
        if (!bb && sim.cwlDeferred.isNotEmpty() && ps.cwl.enabled) {
            val w = ps.cwl.next(now)
            box.addView(note(act, "CWL sampai ${Fmt.dateShort(w.last + 1, zone)}: ${sim.cwlDeferred.joinToString(", ")} ditunda", Ui.BUSY))
        }
        if (bb) capacityLine(act, box, b)
        if (mode == 1) timeline(act, box, b, sim, bb, now, zone)
        else agenda(act, box, b, sim, bb, now, zone)
    }

    private fun note(act: MainActivity, text: String, color: Int): View =
        Ui.muted(act, text).apply {
            textSize = 13f
            setTextColor(color)
            setPadding(Ui.dp(act, 4), Ui.dp(act, 2), Ui.dp(act, 4), Ui.dp(act, 10))
        }

    // ---------------------------------------------------------------- target

    private fun laneWord(l: Lane) = when (l) {
        Lane.BUILDER -> "Builder"
        Lane.LAB -> "Lab"
        Lane.PET -> "Pet"
        Lane.BB_BUILDER -> "Master Builder"
        Lane.STAR_LAB -> "Star Lab"
    }

    /** Label target ("max TH11", "B.O.B terbuka"). */
    fun targetLabel(b: PlanBundle, sim: SimResult?, bb: Boolean): String = when {
        !bb && b.ps.strategy == id.clashtimer.core.Strategy.MAX -> "Max TH${b.village.th}"
        !bb && b.ps.strategy == id.clashtimer.core.Strategy.RUSH -> "TH${b.village.th + 1}"
        !bb -> "Hero max TH${b.village.th}"
        b.ps.bbStrategy == BBStrategy.SIXTH && sim?.sixth != null -> "B.O.B terbuka"
        else -> "Max BH${b.village.bh}"
    }

    /** "Max TH11 ±7 Feb 2027" + strategi & selisih dengan urutan lama. Ketuk = opsi desa. */
    private fun targetCard(act: MainActivity, box: LinearLayout, b: PlanBundle, sim: SimResult, bb: Boolean, zone: ZoneId) {
        val ps = b.ps
        val t = Planner.target(sim, ps, bb).takeIf { it != Long.MAX_VALUE }
        val info = if (bb) b.bbTarget else b.homeTarget
        val card = Ui.panel(act, padH = 12, padV = 10, radiusDp = 14)
        val row = Ui.hbox(act)
        row.addView(Ui.icon(act, R.drawable.ic_target, 20, Ui.FREE_TEXT), LinearLayout.LayoutParams(Ui.dp(act, 20), Ui.dp(act, 20)).apply { marginEnd = Ui.dp(act, 10) })
        val col = Ui.vbox(act)
        col.addView(Ui.text(act, targetLabel(b, sim, bb) + if (t != null) " ±${Fmt.date(t, zone)}" else " · belum tercapai", Ui.BODY_M).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.TEXT)
        })
        val strat = if (bb) (if (Store.bobDone(act, b.tag)) "Max" else ps.bbStrategy.label) else ps.strategy.label
        val diff = if (t != null && info?.legacy != null) info.legacy - t else null
        val sub = strat + if (diff != null && diff >= 3_600_000L) " · ${Fmt.dur(diff)} lebih cepat dari urutan lama" else ""
        col.addView(Ui.muted(act, sub).apply { textSize = 12f })
        row.addView(col, Ui.weight1())
        row.addView(Help.button(act, Help.Topic.PLAN) {
            val lanes = info?.policies?.keys?.filter { Planner.laneCount(b.village, it, ps) > 0 }?.sortedBy { it.ordinal }.orEmpty()
            val how = lanes.groupBy { ps.policyFor(it) }.entries.joinToString("\n") { (p, ls) ->
                "• " + ls.joinToString(" & ") { laneWord(it) } + ": " + p.label
            }
            (if (how.isNotEmpty()) "Urutan yang dipakai desa ini (dari ${info?.tried ?: 0} simulasi):\n$how\n\n" else "") +
                "Dihitung ${Fmt.time(b.computedAt, zone)}. Ganti strategi lewat ikon opsi di atas."
        })
        card.addView(row)
        card.isClickable = true
        card.setOnClickListener { VillageOptions.sheet(act, b.tag, bb) }
        Ui.ripple(card)
        box.addView(card, Ui.matchWrap(12, act))
    }

    // ---------------------------------------------------------------- agenda / timeline

    private fun pref(act: MainActivity, key: String) =
        act.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).getInt(key, 0)

    private fun setPref(act: MainActivity, key: String, v: Int) =
        act.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putInt(key, v).apply()

    private fun modeRow(act: MainActivity, box: LinearLayout, mode: Int) {
        val row = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 2), 0, Ui.dp(act, 10)) }
        row.addView(Ui.segmentedWide(act, listOf("Agenda", "Timeline"), mode, heightDp = 38) {
            setPref(act, "plan_mode", it)
            act.renderBody()
        }, Ui.weight1())
        if (mode == 1) {
            row.addView(Ui.segmentedWide(act, listOf("24 jam", "3 hari"), pref(act, "tl_range"), heightDp = 38) {
                setPref(act, "tl_range", it)
                act.renderBody()
            }, Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
        }
        row.addView(Help.button(act, if (mode == 1) Help.Topic.TIMELINE else Help.Topic.PLAN))
        box.addView(row, Ui.matchWrap())
    }

    private val dayFmt: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE d MMM", Fmt.locale)

    private fun agenda(act: MainActivity, box: LinearLayout, b: PlanBundle, sim: SimResult, bb: Boolean, now: Long, zone: ZoneId) {
        val schedule = b.ps.schedule
        val until = now + 7 * DAY
        val sessions = Sessions.build(sim, bb, now, until)
        if (sessions.isEmpty()) {
            box.addView(Ui.muted(act, "Tidak ada upgrade yang bisa direncanakan — semua sudah max atau menunggu syarat.", Ui.BODY_M).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 16), 0, 0)
            })
            return
        }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // Sesi + jam tidur, berurutan.
        val sleeps = schedule.windowsBetween(now, sessions.last().at).filter { it.first > now && Sessions.inSleep(schedule, it.first) }
        class Entry(val at: Long, val session: Session?, val sleep: LongRange?)
        val entries = (sessions.map { Entry(it.at, it, null) } + sleeps.map { Entry(it.first, null, it) }).sortedBy { it.at }
        var lastDay: java.time.LocalDate? = null
        var shown = 0
        val limit = 40
        for (e in entries) {
            if (shown >= limit) break
            val d = Instant.ofEpochMilli(e.at).atZone(zone).toLocalDate()
            if (d != lastDay && e.session != null) {
                lastDay = d
                val prefix = when (d) {
                    today -> "HARI INI · "
                    today.plusDays(1) -> "BESOK · "
                    else -> ""
                }
                box.addView(Ui.text(act, prefix + dayFmt.format(d).uppercase(Fmt.locale), Ui.BODY_S).apply {
                    textSize = 12f
                    letterSpacing = 0.06f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Ui.TEXT2)
                    setPadding(Ui.dp(act, 2), Ui.dp(act, if (shown == 0) 2 else 8), 0, Ui.dp(act, 8))
                })
            }
            if (e.sleep != null) box.addView(sleepBand(act, e.sleep, sim, bb, zone), Ui.matchWrap(10, act))
            else {
                box.addView(sessionRow(act, b, e.session!!, schedule, now, zone), Ui.matchWrap(10, act))
                shown++
            }
        }
        val rest = sessions.size - shown
        if (rest > 0) box.addView(Ui.muted(act, "… $rest sesi lagi dalam 7 hari").apply { setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 8)) })
        if (!bb && sim.builderIdle7d > 30 * 60_000L) box.addView(note(act, "Builder menganggur ±${Fmt.dur(sim.builderIdle7d)} dalam 7 hari ke depan", Ui.IDLE))
    }

    private fun sleepBand(act: MainActivity, w: LongRange, sim: SimResult, bb: Boolean, zone: ZoneId): View {
        val band = Ui.hbox(act).apply {
            background = Ui.rounded(Ui.SURFACE2, Ui.dp(act, 12).toFloat())
            setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10))
        }
        band.addView(Ui.icon(act, R.drawable.ic_moon, 16, Ui.TEXT2), LinearLayout.LayoutParams(Ui.dp(act, 16), Ui.dp(act, 16)).apply { marginEnd = Ui.dp(act, 8) })
        band.addView(Ui.muted(act, "Tidur ${Fmt.time(w.first, zone)}–${Fmt.time(w.last + 1, zone)}").apply { textSize = 13f }, Ui.weight1())
        // Pekerja yang selesai saat tidur dan menunggu lama.
        val idle = (sim.running + sim.steps).filter { it.lane.bb == bb && it.finish in w && it.idle > 30 * 60_000L }
            .map { Sessions.label(it) }.distinct()
        band.addView(TextView(act).apply {
            textSize = 13f
            if (idle.isEmpty()) { text = "semua tetap bekerja"; setTextColor(Ui.FREE_TEXT) }
            else { text = idle.joinToString(", ") + " menganggur"; setTextColor(Ui.IDLE) }
        })
        return band
    }

    private fun sessionRow(act: MainActivity, b: PlanBundle, s: Session, schedule: id.clashtimer.core.OfflineSchedule, now: Long, zone: ZoneId): View {
        val row = Ui.hbox(act).apply { gravity = Gravity.TOP }
        val left = Ui.vbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
        val timeColor = if (s.now) Ui.FREE else Ui.TEXT
        left.addView(Ui.monoText(act, if (s.now) Fmt.time(now, zone) else Fmt.time(s.at, zone), 15f).apply { setTextColor(timeColor) })
        val mood = if (s.now) "sekarang" else Sessions.mood(schedule, s.at) ?: "${s.steps.size} bebas"
        left.addView(TextView(act).apply {
            text = mood
            textSize = 11f
            setTextColor(if (s.now) Ui.FREE else Ui.TEXT2)
            setPadding(0, Ui.dp(act, 2), 0, 0)
        })
        row.addView(left, LinearLayout.LayoutParams(Ui.dp(act, 58), LinearLayout.LayoutParams.WRAP_CONTENT))
        val card = Ui.panel(act, padH = 12, padV = 10, stroke = if (s.now) Ui.FREE_LINE else null, radiusDp = 14)
        val inner = Ui.hbox(act).apply { gravity = Gravity.TOP }
        val list = Ui.vbox(act)
        val sleepSoon = mood == "sblm tidur"
        s.steps.forEachIndexed { i, st ->
            list.addView(stepLine(act, b, st, now, zone), Ui.matchWrap().apply { if (i > 0) topMargin = Ui.dp(act, 8) })
            val o = when {
                st.idle > 30 * 60_000L -> "selesai ${HomeView.short(st.finish, st.start, zone)} · menganggur ${Fmt.dur(st.idle)}" to Ui.IDLE
                sleepSoon && schedule.isOffline(st.finish) || (sleepSoon && st.idle > 0) -> "selesai ${HomeView.short(st.finish, st.start, zone)} — pas bangun" to Ui.FREE_TEXT
                else -> null
            }
            if (o != null) list.addView(TextView(act).apply {
                text = o.first
                textSize = 12f
                setTextColor(o.second)
                setPadding(Ui.dp(act, 36), Ui.dp(act, 3), 0, 0)
            })
        }
        inner.addView(list, Ui.weight1())
        if (!s.now) inner.addView(bellIcon(act, b.tag, s))
        card.addView(inner)
        row.addView(card, Ui.weight1())
        return row
    }

    private fun bellIcon(act: MainActivity, tag: String, s: Session): View {
        val on = Repo.sessionReminderOn(act, tag, s.at)
        return Ui.icon(act, if (on) R.drawable.ic_bell else R.drawable.ic_bell_off, 20, if (on) Ui.FREE_TEXT else Ui.TEXT2,
            if (on) "Pengingat aktif" else "Ingatkan pukul ${Fmt.time(s.at, act.zoneId())}") {
            val nowOn = Repo.toggleSessionReminder(act, tag, s.at, Sessions.note(s))
            act.renderBody()
            act.snack(if (nowOn) "Pengingat ${Fmt.time(s.at, act.zoneId())} dipasang" else "Pengingat dimatikan")
        }.apply {
            if (on) background = Ui.rounded(Ui.FREE_BG, Ui.dp(act, 10).toFloat())
            (layoutParams as LinearLayout.LayoutParams).marginStart = Ui.dp(act, 4)
        }
    }

    /** "B4 [ikon] Hidden Tesla → 8   14j". */
    private fun stepLine(act: MainActivity, b: PlanBundle, s: PlannedStep, now: Long, zone: ZoneId): View {
        val line = Ui.hbox(act).apply {
            isClickable = true
            setOnClickListener { stepSheet(act, b, s, now, zone) }
        }
        ItemMenu.attach(act, line, b.tag, s.item?.id)
        line.addView(Sessions.badge(act, Sessions.label(s), Sessions.isLab(s)), LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(act, 8) })
        val name = s.item?.name ?: s.title
        val iconLevel = if (s.gear) s.fromLevel else s.fromLevel.coerceAtLeast(1)
        line.addView(Icons.view(act, Icons.forItem(s.item, iconLevel), 22, Icons.initials(name), Ui.TEXT2, round = false),
            LinearLayout.LayoutParams(Ui.dp(act, 22), Ui.dp(act, 22)).apply { marginEnd = Ui.dp(act, 8) })
        line.addView(Ui.text(act, short(s), Ui.BODY_M).apply {
            textSize = 14f
            setTextColor(Ui.TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, Ui.weight1())
        line.addView(Ui.monoText(act, Fmt.durSec(s.durationSec), 13f, bold = false).apply { setTextColor(Ui.TEXT2) })
        return line
    }

    /** "Hidden Tesla → 8", "Bangun Cannon", "Archer Tower · Gear-up". */
    private fun short(s: PlannedStep): String {
        val name = s.item?.name ?: s.title
        return when {
            s.gear -> "$name · Gear-up"
            s.isNew && s.fromLevel == 0 -> "Bangun $name"
            s.isNew -> "Buka $name"
            else -> "$name → ${s.toLevel}"
        }
    }

    private fun timeline(act: MainActivity, box: LinearLayout, b: PlanBundle, sim: SimResult, bb: Boolean, now: Long, zone: ZoneId) {
        val long = pref(act, "tl_range") == 1
        val from = now - if (long) 2 * 3_600_000L else 30 * 60_000L
        val to = from + if (long) 3 * DAY else DAY
        val card = Ui.panel(act, padH = 6, padV = 10)
        val tv = TimelineView(act)
        TimelineView.build(tv, sim.running, sim.steps, bb, from, to, now)
        tv.offline = runCatching { b.ps.schedule.windowsBetween(from, to) }.getOrDefault(emptyList())
        tv.zone = zone
        val detail = Ui.vbox(act)
        tv.onTap = { s ->
            detail.removeAllViews()
            detail.addView(stepCard(act, b, s, now, zone) { detail.removeAllViews() }, Ui.matchWrap())
        }
        if (tv.rows.isEmpty()) {
            card.addView(Ui.muted(act, "Tidak ada pekerja untuk ditampilkan.", Ui.BODY_M).apply { setPadding(Ui.dp(act, 8), Ui.dp(act, 8), 0, Ui.dp(act, 8)) })
        } else card.addView(tv, Ui.matchWrap())
        box.addView(card, Ui.matchWrap(8, act))
        box.addView(legend(act), Ui.matchWrap(10, act))
        val idle = tv.bars.filter { it.idle }.sumOf { (minOf(it.end, to) - maxOf(it.start, now)).coerceAtLeast(0) }
        if (idle > 30 * 60_000L) box.addView(note(act, "Menganggur ±${Fmt.dur(idle)} dalam ${if (long) "3 hari" else "24 jam"}", Ui.IDLE))
        box.addView(detail, Ui.matchWrap())
        if (tv.rows.isNotEmpty()) detail.addView(Ui.muted(act, "Ketuk batang untuk melihat detailnya.").apply {
            textSize = 12f
            setPadding(Ui.dp(act, 4), 0, 0, 0)
        })
    }

    private fun legend(act: MainActivity): View {
        val row = Ui.hbox(act).apply { setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 6), 0) }
        fun item(label: String, color: Int, alpha: Int, h: Int = 10) {
            row.addView(View(act).apply { background = Ui.tint(color, Ui.dp(act, 3).toFloat(), alpha) },
                LinearLayout.LayoutParams(Ui.dp(act, 16), Ui.dp(act, h)).apply { marginEnd = Ui.dp(act, 6) })
            row.addView(Ui.muted(act, label).apply { textSize = 12f },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(act, 14) })
        }
        item("berjalan", Ui.BUSY, 0xFF)
        item("rencana", Ui.BUSY, 0x55)
        item("lab", Ui.LAB, 0xFF)
        item("menganggur", Ui.IDLE, 0xCC, 4)
        item("offline", Ui.GREY, 0x40)
        return HorizontalScrollView(act).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    // ---------------------------------------------------------------- detail langkah

    private fun stepSheet(act: MainActivity, b: PlanBundle, s: PlannedStep, now: Long, zone: ZoneId) {
        Sheets.open(act, null) { col, dismiss -> col.addView(stepBody(act, b, s, now, zone, dismiss)) }
    }

    /** Kartu detail di bawah timeline (batang yang diketuk). */
    private fun stepCard(act: MainActivity, b: PlanBundle, s: PlannedStep, now: Long, zone: ZoneId, close: () -> Unit): View {
        val card = Ui.panel(act, padH = 14, padV = 14)
        card.addView(stepBody(act, b, s, now, zone, close))
        return card
    }

    private fun stepBody(act: MainActivity, b: PlanBundle, s: PlannedStep, now: Long, zone: ZoneId, done: () -> Unit): View {
        val col = Ui.vbox(act)
        val head = Ui.hbox(act)
        val name = s.item?.name ?: s.title
        val iconLevel = if (s.gear) s.fromLevel else s.fromLevel.coerceAtLeast(1)
        head.addView(Ui.gameIcon(act, Icons.forItem(s.item, iconLevel), name, 52, Sessions.badge(act, Sessions.label(s), Sessions.isLab(s))))
        val tc = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
        tc.addView(Ui.text(act, Sessions.title(s), Ui.TITLE_S).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT) })
        val cost = if (s.cost > 0) " · ${Fmt.money(s.cost)} ${Fmt.resource(s.item?.resource ?: "")}" else ""
        tc.addView(Ui.muted(act, (if (s.running) "berjalan" else "rencana") + cost).apply { textSize = 13f })
        head.addView(tc, Ui.weight1())
        col.addView(head)

        val grid = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 12), 0, 0) }
        fun add(label: String, value: String) {
            grid.addView(Sheets.stat(act, label, value).first, Ui.weight1().apply { if (grid.childCount > 0) marginStart = Ui.dp(act, 8) })
        }
        if (!s.running) add("Mulai", if (s.start <= now + 60_000L) "sekarang" else HomeView.short(s.start, now, zone))
        add("Lama", Fmt.durSec(s.durationSec))
        add("Selesai", HomeView.short(s.finish, now, zone))
        col.addView(grid, Ui.matchWrap())

        val why = ArrayList<Pair<String, Int>>()
        if (!s.running && s.idle > 30 * 60_000L) why += "Selesai saat Anda offline; pekerja menunggu ±${Fmt.dur(s.idle)}." to Ui.IDLE
        else if (!s.running && b.ps.schedule.isOffline(s.finish)) why += "Selesai saat Anda offline — langsung diisi lagi begitu bangun." to Ui.FREE_TEXT
        s.event?.let { why += "Dimulai saat $it — waktu & biaya sudah dipotong." to Ui.LAB }
        s.note?.let { why += it to Ui.TEXT2 }
        if (s.tier > 0) why += "Langkah menuju builder ke-6." to Ui.BUSY
        if (s.gear) why += "Gear-up memakai Master Builder dari Builder Base dan bekerja di Home Village." to Ui.TEXT2
        for ((t, c) in why) col.addView(TextView(act).apply {
            text = t
            textSize = 13f
            setTextColor(c)
            setPadding(0, Ui.dp(act, 10), 0, 0)
        })

        // Bisa langsung ditandai dimulai bila pekerjanya bebas sekarang; selain itu pasang pengingat.
        val pick = if (!s.running && s.start <= now + 5 * 60_000L) {
            b.slots.filter { it.lane == s.lane && it.freeAt <= now }.flatMap { it.ranked }
                .firstOrNull { it.c.item.id == s.item?.id && it.c.fromLevel == s.fromLevel && it.c.gear == s.gear }
        } else null
        if (pick != null) {
            col.addView(Ui.primary(act, "Sudah dimulai") { done(); act.startPick(b.tag, pick) }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })
        } else if (!s.running && s.start > now) {
            val on = Repo.sessionReminderOn(act, b.tag, s.start)
            col.addView(Ui.tonal(act, if (on) "Pengingat ${Fmt.time(s.start, zone)} aktif · matikan" else "Ingatkan pukul ${Fmt.time(s.start, zone)}") {
                val sess = Session(s.start, listOf(s), false)
                val nowOn = Repo.toggleSessionReminder(act, b.tag, s.start, Sessions.note(sess))
                done()
                act.renderBody()
                act.snack(if (nowOn) "Pengingat ${Fmt.time(s.start, zone)} dipasang" else "Pengingat dimatikan")
            }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })
        }
        return col
    }

    // ---------------------------------------------------------------- gudang Builder Base

    /** Kapasitas gudang Builder Base (Builder Hall + storage) — upgrade di atasnya menunggu storage dinaikkan. */
    private fun capacityLine(act: MainActivity, box: LinearLayout, b: PlanBundle) {
        val v = b.village
        val gold = id.clashtimer.core.Capacity.of(v, id.clashtimer.core.Capacity.GOLD) ?: return
        val elixir = id.clashtimer.core.Capacity.of(v, id.clashtimer.core.Capacity.ELIXIR) ?: return
        val over = b.simBB?.steps.orEmpty().firstOrNull { s ->
            val res = s.item?.resource ?: return@firstOrNull false
            val cap = id.clashtimer.core.Capacity.of(v, res) ?: return@firstOrNull false
            s.cost > cap
        }
        val text = StringBuilder("Gudang: ${Fmt.money(gold)} Gold BB · ${Fmt.money(elixir)} Elixir BB")
        if (over != null) {
            text.append(" — ${over.title} butuh ${Fmt.money(over.cost)} ${Fmt.resource(over.item?.resource ?: "")}, storage dinaikkan dulu")
        }
        box.addView(note(act, text.toString(), if (over != null) Ui.BUSY else Ui.TEXT2))
    }
}
