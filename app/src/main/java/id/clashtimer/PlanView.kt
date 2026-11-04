package id.clashtimer

import id.clashtimer.core.Capacity

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
 * Tab "Tukang" dan "Lab": pekerja yang bebas (dengan saran), Agenda hari ini / Antrian riset atau
 * Timeline, yang sedang dikerjakan, lalu antrian besok dst. Strategi dipilih di lembar Opsi desa.
 */
object PlanView {

    /** Jenis pekerja per tab: Tukang = builder / Master Builder, Lab = Lab + Pet / Star Lab. */
    fun lanes(bb: Boolean, lab: Boolean): Set<Lane> = when {
        !lab && !bb -> setOf(Lane.BUILDER)
        !lab -> setOf(Lane.BB_BUILDER)
        !bb -> setOf(Lane.LAB, Lane.PET)
        else -> setOf(Lane.STAR_LAB)
    }

    /** 0 = Agenda (Tukang) / Antrian (Lab), 1 = Timeline. */
    fun mode(act: MainActivity, lab: Boolean) = pref(act, if (lab) "lab_mode" else "plan_mode")

    fun setMode(act: MainActivity, lab: Boolean, v: Int) = setPref(act, if (lab) "lab_mode" else "plan_mode", v)

    /** [showFree] = false di layar lebar (kartu pekerja bebas sudah ada di Dashboard sebelah kiri). */
    fun render(act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, lab: Boolean, b: PlanBundle?, showFree: Boolean = true) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        val acc = Store.accounts(act).firstOrNull { it.tag == tag } ?: return
        val ups = Store.upgrades(act).filter { it.tag == tag }
        if (bb && acc.bh == 0 && ups.none { it.kind.builderBase }) {
            box.addView(Ui.muted(act, tr("Belum ada data Builder Base di export desa ini.", "No Builder Base data in this village export yet."), Ui.BODY_M).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 24), Ui.dp(act, 4), 0)
            })
            return
        }
        val gd = Repo.gameData(act)
        val labels = Sessions.labels(ups, now)
        val sim = b?.let { if (bb) it.simBB else it.simHome }
        val lanes = lanes(bb, lab)
        val mode = mode(act, lab)
        val hasExport = Store.exportText(act, tag) != null

        // Pekerja yang bebas sekarang: kartu saran (Sudah dimulai / Opsi lain).
        val free = if (showFree) HomeView.freeCards(act, tag, bb, b, sim, ups, labels, now, lanes) else emptyList()
        if (free.isNotEmpty()) {
            val target = b?.let { if (bb) tr("urutan tercepat: ${it.ps.bbStrategy.label}", "fastest order: ${it.ps.bbStrategy.label}") else tr("urutan tercepat ke ${it.ps.strategy.target}", "fastest order to ${it.ps.strategy.target}") }
            box.addView(Ui.heading(act, HomeView.countTitle(tr("Bebas sekarang", "Free now"), free.size), target, Help.Topic.HOME))
            for (c in free) box.addView(HomeView.freeCard(act, tag, b, c, bb, now, zone, hasExport), Ui.matchWrap(12, act))
        }

        if (b == null || sim == null) {
            if (b == null) act.waiting(tag, box)
            working(act, box, bb, lab, null, null, ups, gd, labels, now, zone)
            return
        }
        if (!bb && !lab && sim.cwlDeferred.isNotEmpty() && b.ps.cwl.enabled) {
            val w = b.ps.cwl.next(now)
            box.addView(note(act, tr("CWL sampai ", "CWL until ") + Fmt.dateShort(w.last + 1, zone) + ": " + sim.cwlDeferred.joinToString(", ") + tr(" ditunda", " paused"), Ui.BUSY))
        }
        if (!lab) capacityLine(act, box, b, bb)
        val split = agendaEnd(b.ps.schedule, now)
        if (!lab) {
            if (mode == 1) timeline(act, box, b, sim, bb, lanes, now, zone) else agenda(act, box, b, sim, bb, lanes, split, now, zone)
            working(act, box, bb, false, b, sim, ups, gd, labels, now, zone)
            queue(act, box, b, sim, lanes, split, now, zone)
        } else {
            working(act, box, bb, true, b, sim, ups, gd, labels, now, zone)
            if (mode == 1) timeline(act, box, b, sim, bb, lanes, now, zone) else labQueue(act, box, b, sim, bb, lanes, now, zone)
            labProgress(act, box, b, bb)
        }
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
        b.ps.bbStrategy == BBStrategy.SIXTH && sim?.sixth != null -> tr("B.O.B terbuka", "B.O.B unlocked")
        else -> "Max BH${b.village.bh}"
    }

    // ---------------------------------------------------------------- agenda / timeline

    private fun pref(act: MainActivity, key: String) =
        act.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).getInt(key, 0)

    private fun setPref(act: MainActivity, key: String, v: Int) =
        act.getSharedPreferences("ui", android.content.Context.MODE_PRIVATE).edit().putInt(key, v).apply()

    private val dayFmt: DateTimeFormatter get() = DateTimeFormatter.ofPattern("EEEE d MMM", Fmt.locale)

    /** Batas Agenda: akhir jam tidur berikutnya (bangun), atau 24 jam bila jam tidur tidak diatur. */
    private fun agendaEnd(schedule: id.clashtimer.core.OfflineSchedule, now: Long): Long {
        val w = runCatching { schedule.windowsBetween(now, now + 2 * DAY) }.getOrDefault(emptyList())
            .firstOrNull { it.last > now && Sessions.inSleep(schedule, it.first.coerceAtLeast(now)) }
        return w?.let { it.last + 1 } ?: (now + DAY)
    }

    /** Sesi login sampai bangun berikutnya (yang sekarang sudah tampil sebagai kartu bebas). */
    private fun agenda(
        act: MainActivity, box: LinearLayout, b: PlanBundle, sim: SimResult, bb: Boolean, lanes: Set<Lane>,
        until: Long, now: Long, zone: ZoneId,
    ) {
        val schedule = b.ps.schedule
        val sessions = Sessions.build(sim, bb, now, until, lanes).filter { !it.now && it.at < until }
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val sleeps = runCatching { schedule.windowsBetween(now, until) }.getOrDefault(emptyList())
            .filter { it.last > now && it.first < until && Sessions.inSleep(schedule, it.first.coerceAtLeast(now)) }
        class Entry(val at: Long, val session: Session?, val sleep: LongRange?)
        val entries = (sessions.map { Entry(it.at, it, null) } + sleeps.map { Entry(it.first.coerceAtLeast(now), null, it) }).sortedBy { it.at }
        var lastDay: java.time.LocalDate? = null
        var first = true
        fun dayHead(d: java.time.LocalDate) {
            val prefix = when (d) {
                today -> tr("HARI INI · ", "TODAY · ")
                today.plusDays(1) -> tr("BESOK · ", "TOMORROW · ")
                else -> ""
            }
            val row = Ui.hbox(act).apply { setPadding(Ui.dp(act, 2), Ui.dp(act, if (first) 2 else 8), 0, Ui.dp(act, 6)) }
            row.addView(Ui.text(act, prefix + dayFmt.format(d).uppercase(Fmt.locale), Ui.BODY_S).apply {
                textSize = 12f
                letterSpacing = 0.06f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.TEXT2)
            }, Ui.weight1())
            if (first) row.addView(Help.button(act, Help.Topic.PLAN))
            box.addView(row, Ui.matchWrap())
            first = false
        }
        if (entries.isEmpty()) {
            dayHead(today)
            box.addView(Ui.muted(act, tr("Tidak ada yang perlu dimulai sampai ${HomeView.short(until, now, zone)}.", "Nothing to start until ${HomeView.short(until, now, zone)}.")).apply {
                textSize = 13f
                setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 10))
            })
            return
        }
        for (e in entries) {
            val d = Instant.ofEpochMilli(e.at).atZone(zone).toLocalDate()
            if (d != lastDay && (e.session != null || lastDay == null)) {
                lastDay = d
                dayHead(d)
            }
            if (e.sleep != null) box.addView(sleepBand(act, e.sleep, sim, bb, lanes, zone), Ui.matchWrap(10, act))
            else box.addView(sessionRow(act, b, e.session!!, schedule, now, zone), Ui.matchWrap(10, act))
        }
    }

    /** Antrian besok dst: langkah rencana setelah Agenda, per hari. */
    private fun queue(act: MainActivity, box: LinearLayout, b: PlanBundle, sim: SimResult, lanes: Set<Lane>, from: Long, now: Long, zone: ZoneId) {
        val steps = sim.steps.filter { it.lane in lanes && !it.running && it.start >= from && it.start < now + 7 * DAY }.sortedBy { it.start }
        if (steps.isEmpty()) return
        box.addView(Ui.heading(act, tr("Antrian", "Queue"), tr("urutan tercepat ke target", "fastest order to target")))
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val limit = 20
        val shown = steps.take(limit)
        for ((d, list) in shown.groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() }) {
            val card = Ui.panel(act, padH = 14, padV = 4)
            val prefix = when (d) {
                today -> tr("HARI INI · ", "TODAY · ")
                today.plusDays(1) -> tr("BESOK · ", "TOMORROW · ")
                else -> ""
            }
            card.addView(Ui.text(act, prefix + dayFmt.format(d).uppercase(Fmt.locale), Ui.BODY_S).apply {
                textSize = 12f
                letterSpacing = 0.06f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.TEXT2)
                setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 4))
            })
            for (s in list) {
                card.addView(View(act).apply { setBackgroundColor(Ui.LINE) },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 1)))
                val row = Ui.hbox(act).apply {
                    minimumHeight = Ui.dp(act, 44)
                    isClickable = true
                    setOnClickListener { stepSheet(act, b, s, now, zone) }
                }
                ItemMenu.attach(act, row, b.tag, s.item?.id)
                row.addView(Ui.monoText(act, Fmt.time(s.start, zone), 13f, bold = false).apply { setTextColor(Ui.TEXT2) },
                    LinearLayout.LayoutParams(Ui.dp(act, 48), LinearLayout.LayoutParams.WRAP_CONTENT))
                row.addView(stepLine(act, b, s, now, zone, clickable = false), Ui.weight1())
                card.addView(row)
            }
            box.addView(card, Ui.matchWrap(10, act))
        }
        val rest = steps.size - shown.size
        if (rest > 0) box.addView(Ui.muted(act, tr("… $rest langkah lagi dalam 7 hari", "… $rest more steps within 7 days")).apply { setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 8)) })
        if (lanes.contains(Lane.BUILDER) && sim.builderIdle7d > 30 * 60_000L) {
            box.addView(note(act, tr("Builder menganggur ±${Fmt.dur(sim.builderIdle7d)} dalam 7 hari ke depan", "Builders idle ±${Fmt.dur(sim.builderIdle7d)} over the next 7 days"), Ui.IDLE))
        }
    }

    /** Lab: antrian riset berikutnya dengan lonceng pengingat. */
    private fun labQueue(act: MainActivity, box: LinearLayout, b: PlanBundle, sim: SimResult, bb: Boolean, lanes: Set<Lane>, now: Long, zone: ZoneId) {
        val steps = sim.steps.filter { it.lane in lanes && !it.running && it.start > now + 60_000L }.sortedBy { it.start }
        val name = if (bb) "Star Lab" else "Lab"
        val head = Ui.heading(act, tr("Antrian $name", "$name queue"), tr("urutan tercepat ke target", "fastest order to target"), Help.Topic.PLAN)
        box.addView(head)
        if (steps.isEmpty()) {
            box.addView(Ui.muted(act, tr("Tidak ada riset lagi yang bisa direncanakan.", "No more research can be planned.")).apply { setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 10)) })
            return
        }
        val schedule = b.ps.schedule
        val panel = Ui.panel(act, padH = 14, padV = 4)
        val limit = 10
        steps.take(limit).forEachIndexed { i, s ->
            if (i > 0) panel.addView(View(act).apply { setBackgroundColor(Ui.LINE) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 1)))
            val row = Ui.hbox(act).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8))
                isClickable = true
                setOnClickListener { stepSheet(act, b, s, now, zone) }
            }
            ItemMenu.attach(act, row, b.tag, s.item?.id)
            row.addView(Ui.monoText(act, HomeView.short(s.start, now, zone), 13f, bold = false).apply { setTextColor(Ui.TEXT) },
                LinearLayout.LayoutParams(Ui.dp(act, 72), LinearLayout.LayoutParams.WRAP_CONTENT))
            val col = Ui.vbox(act)
            val t = Ui.hbox(act)
            if (lanes.size > 1) t.addView(Sessions.badge(act, Sessions.label(s), true), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(act, 6) })
            t.addView(Ui.text(act, short(s), Ui.BODY_M).apply {
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.TEXT)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, Ui.weight1())
            col.addView(t)
            val cost = if (s.cost > 0) " · ${Fmt.money(s.cost)} ${Fmt.resource(s.item?.resource ?: "")}" else ""
            val (extra, color) = when {
                s.idle > 30 * 60_000L -> tr(" · menganggur ${Fmt.dur(s.idle)}", " · idle ${Fmt.dur(s.idle)}") to Ui.IDLE
                schedule.isOffline(s.finish) -> tr(" · selesai saat tidur", " · done while asleep") to Ui.TEXT2
                Sessions.mood(schedule, s.finish) == "bangun" -> tr(" · pas bangun", " · right at wake-up") to Ui.FREE_TEXT
                else -> "" to Ui.TEXT2
            }
            col.addView(Ui.muted(act, tr("${Fmt.durSec(s.durationSec)}$cost · selesai ${HomeView.short(s.finish, now, zone)}$extra", "${Fmt.durSec(s.durationSec)}$cost · done ${HomeView.short(s.finish, now, zone)}$extra")).apply {
                textSize = 12f
                setTextColor(color)
            })
            row.addView(col, Ui.weight1())
            row.addView(bellIcon(act, b.tag, Session(s.start, listOf(s), false)))
            panel.addView(row)
        }
        box.addView(panel, Ui.matchWrap(8, act))
        val rest = steps.size - limit
        if (rest > 0) box.addView(Ui.muted(act, tr("… lalu $rest riset lagi", "… then $rest more research")).apply { setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 8)) })
    }

    // ---------------------------------------------------------------- sedang dikerjakan

    private fun working(
        act: MainActivity, box: LinearLayout, bb: Boolean, lab: Boolean, b: PlanBundle?, sim: SimResult?,
        ups: List<Upgrade>, gd: id.clashtimer.core.GameData, labels: Map<String, String>, now: Long, zone: ZoneId,
    ) {
        val kinds = lanes(bb, lab).map { Kind.of(it) }.toSet()
        val running = ups.filter { it.kind in kinds && it.finishAt > now }.sortedBy { it.finishAt }
        if (running.isEmpty()) return
        val tag = running.first().tag
        val sub = if (lab) tr("${running.size} berjalan", "${running.size} running") else act.builderCounts(tag, bb).let { (busy, total) -> tr("$busy/$total bekerja", "$busy/$total working") }
        box.addView(Ui.heading(act, (if (lab) tr("Sedang diriset", "Researching") else tr("Sedang dikerjakan", "In progress")) + " · $sub", tr("tercepat di atas", "soonest first")))
        for (u in running) box.addView(workCard(act, u, gd, labels[u.id], lab, b, sim, now, zone), Ui.matchWrap(10, act))
    }

    /** Kartu upgrade berjalan: ikon + label pekerja, hitung mundur, bar, dan apa yang terjadi setelahnya. */
    private fun workCard(
        act: MainActivity, u: Upgrade, gd: id.clashtimer.core.GameData, label: String?, lab: Boolean,
        b: PlanBundle?, sim: SimResult?, now: Long, zone: ZoneId,
    ): View {
        val color = if (lab) Ui.LAB else Ui.BUSY
        val card = Ui.panel(act, padH = 12, padV = 12)
        card.isClickable = true
        card.setOnClickListener { act.timerOptions(u) }
        Ui.ripple(card)
        ItemMenu.attach(act, card, u.tag, Repo.itemIdOf(u.id))
        val row = Ui.hbox(act)
        val badge = label?.let { Sessions.badge(act, it, lab) }
        row.addView(HomeView.icon(act, u, gd, if (lab) 52 else 46, badge))
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
        if (lab) {
            val where = when (u.kind) {
                Kind.PET -> "Pet House"
                Kind.STAR_LAB -> "Star Laboratory"
                else -> if (u.extra) "Goblin Researcher" else "Laboratory"
            }
            col.addView(Ui.muted(act, where).apply { textSize = 12f })
        }
        val t = Ui.hbox(act)
        t.addView(Ui.text(act, Widgets.shortTitle(u), Ui.BODY_M).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, Ui.weight1())
        val cd = Ui.monoText(act, act.countdown(u.finishAt - now), 15f).apply { if (lab) setTextColor(Ui.LAB) }
        act.registerCountdown(cd, u.finishAt, clockStyle = false)
        t.addView(cd)
        col.addView(t)
        col.addView(Ui.thinBar(act, HomeView.progress(u, gd, now) ?: 0.0, color))
        val (txt, c) = afterNote(u, label, b, sim, now, zone)
        col.addView(TextView(act).apply {
            text = txt
            textSize = 12f
            setTextColor(c)
            setPadding(0, Ui.dp(act, 5), 0, 0)
        })
        row.addView(col, Ui.weight1())
        card.addView(row)
        return card
    }

    /** "selesai 18:40 · lalu Air Sweeper → 6" atau "selesai Sel 02:05 · menganggur ±4j". */
    private fun afterNote(u: Upgrade, label: String?, b: PlanBundle?, sim: SimResult?, now: Long, zone: ZoneId): Pair<String, Int> {
        val done = tr("selesai ${HomeView.short(u.finishAt, now, zone)}", "done ${HomeView.short(u.finishAt, now, zone)}")
        if (u.extra) return "$done · Goblin (event)" to Ui.TEXT2
        val lane = when (u.kind) {
            Kind.BUILDER -> Lane.BUILDER
            Kind.BB_BUILDER -> Lane.BB_BUILDER
            Kind.LAB -> Lane.LAB
            Kind.PET -> Lane.PET
            Kind.STAR_LAB -> Lane.STAR_LAB
            else -> return done to Ui.TEXT2
        }
        // Langkah berjalan di rencana yang sama (selesai paling dekat dengan timer ini).
        val rs = sim?.running?.filter { it.lane == lane }?.minByOrNull { kotlin.math.abs(it.finish - u.finishAt) }
            ?.takeIf { kotlin.math.abs(it.finish - u.finishAt) < 5 * 60_000L }
        val idx = rs?.laneIndex ?: label?.drop(1)?.toIntOrNull()
        val next = sim?.steps?.filter { it.lane == lane && !it.running && (lane != Lane.BUILDER && lane != Lane.BB_BUILDER || it.laneIndex == idx) && it.start >= u.finishAt - 60_000L }
            ?.minByOrNull { it.start }
        val wait = when {
            rs != null -> rs.idle
            next != null -> (next.start - u.finishAt).coerceAtLeast(0)
            else -> 0L
        }
        return when {
            wait > 30 * 60_000L -> tr("$done · menganggur ±${Fmt.dur(wait)}", "$done · idle ±${Fmt.dur(wait)}") to Ui.IDLE
            next != null -> tr("$done · lalu ${short(next)}", "$done · then ${short(next)}") to Ui.TEXT2
            b != null && b.ps.schedule.isOffline(u.finishAt) -> tr("$done · saat tidur", "$done · while asleep") to Ui.TEXT2
            else -> done to Ui.TEXT2
        }
    }

    // ---------------------------------------------------------------- lab: sisa riset

    private fun labProgress(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean) {
        val p = (if (bb) b.bb else b.home) ?: return
        val cats = p.categories.filter { it.lane == Lane.LAB || it.lane == Lane.PET || it.lane == Lane.STAR_LAB }
        if (cats.isEmpty()) return
        box.addView(Ui.heading(act, tr("Sisa riset", "Research left"), null, Help.Topic.PROGRESS))
        val panel = Ui.panel(act, padH = 14, padV = 6)
        for (c in cats) {
            val pct = if (c.totalSec > 0) c.doneSec * 100.0 / c.totalSec else 100.0
            val row = Ui.hbox(act).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = Ui.dp(act, 40)
            }
            row.addView(Ui.text(act, c.title, Ui.BODY_M).apply { textSize = 13f; setTextColor(Ui.TEXT); maxLines = 1 },
                LinearLayout.LayoutParams(Ui.dp(act, 96), LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(Ui.thinBar(act, pct / 100.0, Ui.LAB).apply {
                layoutParams = LinearLayout.LayoutParams(0, Ui.dp(act, 6), 1f)
            })
            row.addView(Ui.monoText(act, if (c.remainingSec <= 0) "max" else Ui.pct(pct), 12f, bold = false).apply {
                setTextColor(if (c.remainingSec <= 0) Ui.FREE_TEXT else Ui.TEXT2)
                gravity = Gravity.END
            }, LinearLayout.LayoutParams(Ui.dp(act, 48), LinearLayout.LayoutParams.WRAP_CONTENT))
            panel.addView(row)
        }
        panel.isClickable = true
        panel.setOnClickListener { act.openTab(MainActivity.Tab.PROGRESS) }
        box.addView(panel, Ui.matchWrap(12, act))
    }

    private fun sleepBand(act: MainActivity, w: LongRange, sim: SimResult, bb: Boolean, lanes: Set<Lane>, zone: ZoneId): View {
        val band = Ui.hbox(act).apply {
            background = Ui.rounded(Ui.SURFACE2, Ui.dp(act, 12).toFloat())
            setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10))
        }
        band.addView(Ui.icon(act, R.drawable.ic_moon, 16, Ui.TEXT2), LinearLayout.LayoutParams(Ui.dp(act, 16), Ui.dp(act, 16)).apply { marginEnd = Ui.dp(act, 8) })
        band.addView(Ui.muted(act, tr("Tidur ${Fmt.time(w.first, zone)}–${Fmt.time(w.last + 1, zone)}", "Sleep ${Fmt.time(w.first, zone)}–${Fmt.time(w.last + 1, zone)}")).apply { textSize = 13f }, Ui.weight1())
        // Pekerja yang selesai saat tidur dan menunggu lama.
        val idle = (sim.running + sim.steps).filter { it.lane.bb == bb && it.lane in lanes && it.finish in w && it.idle > 30 * 60_000L }
            .map { Sessions.label(it) }.distinct()
        band.addView(TextView(act).apply {
            textSize = 13f
            if (idle.isEmpty()) { text = tr("semua tetap bekerja", "everyone keeps working"); setTextColor(Ui.FREE_TEXT) }
            else { text = idle.joinToString(", ") + tr(" menganggur", " idle"); setTextColor(Ui.IDLE) }
        })
        return band
    }

    private fun sessionRow(act: MainActivity, b: PlanBundle, s: Session, schedule: id.clashtimer.core.OfflineSchedule, now: Long, zone: ZoneId): View {
        val row = Ui.hbox(act).apply { gravity = Gravity.TOP }
        val left = Ui.vbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
        val timeColor = if (s.now) Ui.FREE else Ui.TEXT
        left.addView(Ui.monoText(act, if (s.now) Fmt.time(now, zone) else Fmt.time(s.at, zone), 15f).apply { setTextColor(timeColor) })
        val moodKey = if (s.now) null else Sessions.mood(schedule, s.at)
        val mood = when {
            s.now -> tr("sekarang", "now")
            moodKey == "bangun" -> tr("bangun", "wake-up")
            moodKey == "sblm tidur" -> tr("sblm tidur", "before bed")
            else -> tr("${s.steps.size} bebas", "${s.steps.size} free")
        }
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
        val sleepSoon = moodKey == "sblm tidur"
        s.steps.forEachIndexed { i, st ->
            list.addView(stepLine(act, b, st, now, zone), Ui.matchWrap().apply { if (i > 0) topMargin = Ui.dp(act, 8) })
            val o = when {
                st.idle > 30 * 60_000L -> tr("selesai ${HomeView.short(st.finish, st.start, zone)} · menganggur ${Fmt.dur(st.idle)}", "done ${HomeView.short(st.finish, st.start, zone)} · idle ${Fmt.dur(st.idle)}") to Ui.IDLE
                sleepSoon && schedule.isOffline(st.finish) || (sleepSoon && st.idle > 0) -> tr("selesai ${HomeView.short(st.finish, st.start, zone)} — pas bangun", "done ${HomeView.short(st.finish, st.start, zone)} — right at wake-up") to Ui.FREE_TEXT
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

    fun bellIcon(act: MainActivity, tag: String, s: Session): View {
        val on = Repo.sessionReminderOn(act, tag, s.at)
        return Ui.icon(act, if (on) R.drawable.ic_bell else R.drawable.ic_bell_off, 20, if (on) Ui.FREE_TEXT else Ui.TEXT2,
            if (on) tr("Pengingat aktif", "Reminder on") else tr("Ingatkan pukul ${Fmt.time(s.at, act.zoneId())}", "Remind me at ${Fmt.time(s.at, act.zoneId())}")) {
            val nowOn = Repo.toggleSessionReminder(act, tag, s.at, Sessions.note(s))
            act.renderBody()
            act.snack(if (nowOn) tr("Pengingat ${Fmt.time(s.at, act.zoneId())} dipasang", "Reminder ${Fmt.time(s.at, act.zoneId())} set") else tr("Pengingat dimatikan", "Reminder off"))
        }.apply {
            if (on) background = Ui.rounded(Ui.FREE_BG, Ui.dp(act, 10).toFloat())
            (layoutParams as LinearLayout.LayoutParams).marginStart = Ui.dp(act, 4)
        }
    }

    /** "B4 [ikon] Hidden Tesla → 8   14j". */
    private fun stepLine(act: MainActivity, b: PlanBundle, s: PlannedStep, now: Long, zone: ZoneId, clickable: Boolean = true): View {
        val line = Ui.hbox(act)
        if (clickable) {
            line.isClickable = true
            line.setOnClickListener { stepSheet(act, b, s, now, zone) }
            ItemMenu.attach(act, line, b.tag, s.item?.id)
        }
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
            s.isNew && s.fromLevel == 0 -> tr("Bangun $name", "Build $name")
            s.isNew -> tr("Buka $name", "Unlock $name")
            else -> "$name → ${s.toLevel}"
        }
    }

    private fun timeline(act: MainActivity, box: LinearLayout, b: PlanBundle, sim: SimResult, bb: Boolean, lanes: Set<Lane>, now: Long, zone: ZoneId) {
        val range = Ui.hbox(act).apply { setPadding(0, 0, 0, Ui.dp(act, 8)) }
        range.addView(Ui.segmentedWide(act, listOf(tr("24 jam", "24 hours"), tr("3 hari", "3 days")), pref(act, "tl_range"), heightDp = 36, lab = lanes.none { it == Lane.BUILDER || it == Lane.BB_BUILDER }) {
            setPref(act, "tl_range", it)
            act.renderBody()
        }, Ui.weight1())
        range.addView(Help.button(act, Help.Topic.TIMELINE))
        box.addView(range, Ui.matchWrap())
        val long = pref(act, "tl_range") == 1
        val from = now - if (long) 2 * 3_600_000L else 30 * 60_000L
        val to = from + if (long) 3 * DAY else DAY
        val card = Ui.panel(act, padH = 6, padV = 10)
        val tv = TimelineView(act)
        TimelineView.build(tv, sim.running.filter { it.lane in lanes }, sim.steps.filter { it.lane in lanes }, bb, from, to, now)
        tv.offline = runCatching { b.ps.schedule.windowsBetween(from, to) }.getOrDefault(emptyList())
        tv.zone = zone
        val detail = Ui.vbox(act)
        tv.onTap = { s ->
            detail.removeAllViews()
            detail.addView(stepCard(act, b, s, now, zone) { detail.removeAllViews() }, Ui.matchWrap())
        }
        if (tv.rows.isEmpty()) {
            card.addView(Ui.muted(act, tr("Tidak ada pekerja untuk ditampilkan.", "No workers to show."), Ui.BODY_M).apply { setPadding(Ui.dp(act, 8), Ui.dp(act, 8), 0, Ui.dp(act, 8)) })
        } else card.addView(tv, Ui.matchWrap())
        box.addView(card, Ui.matchWrap(8, act))
        box.addView(legend(act, lanes.none { it == Lane.BUILDER || it == Lane.BB_BUILDER }), Ui.matchWrap(10, act))
        val idle = tv.bars.filter { it.idle }.sumOf { (minOf(it.end, to) - maxOf(it.start, now)).coerceAtLeast(0) }
        if (idle > 30 * 60_000L) box.addView(note(act, tr("Menganggur ±", "Idle ±") + Fmt.dur(idle) + tr(" dalam ", " within ") + if (long) tr("3 hari", "3 days") else tr("24 jam", "24 hours"), Ui.IDLE))
        box.addView(detail, Ui.matchWrap())
        if (tv.rows.isNotEmpty()) detail.addView(Ui.muted(act, tr("Ketuk batang untuk melihat detailnya.", "Tap a bar to see its details.")).apply {
            textSize = 12f
            setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 8))
        })
    }

    private fun legend(act: MainActivity, lab: Boolean): View {
        val row = Ui.hbox(act).apply { setPadding(Ui.dp(act, 6), 0, Ui.dp(act, 6), 0) }
        fun item(label: String, color: Int, alpha: Int, h: Int = 10) {
            row.addView(View(act).apply { background = Ui.tint(color, Ui.dp(act, 3).toFloat(), alpha) },
                LinearLayout.LayoutParams(Ui.dp(act, 16), Ui.dp(act, h)).apply { marginEnd = Ui.dp(act, 6) })
            row.addView(Ui.muted(act, label).apply { textSize = 12f },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(act, 14) })
        }
        val c = if (lab) Ui.LAB else Ui.BUSY
        item(tr("berjalan", "running"), c, 0xFF)
        item(tr("rencana", "planned"), c, 0x55)
        item(tr("menganggur", "idle"), Ui.IDLE, 0xCC, 4)
        item(tr("offline", "offline"), Ui.GREY, 0x40)
        return HorizontalScrollView(act).apply {
            isHorizontalScrollBarEnabled = false
            addView(row)
        }
    }

    // ---------------------------------------------------------------- detail langkah

    fun stepSheet(act: MainActivity, b: PlanBundle, s: PlannedStep, now: Long, zone: ZoneId) {
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
        tc.addView(Ui.muted(act, (if (s.running) tr("berjalan", "running") else tr("rencana", "planned")) + cost).apply { textSize = 13f })
        head.addView(tc, Ui.weight1())
        col.addView(head)

        val grid = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 12), 0, 0) }
        fun add(label: String, value: String) {
            grid.addView(Sheets.stat(act, label, value).first, Ui.weight1().apply { if (grid.childCount > 0) marginStart = Ui.dp(act, 8) })
        }
        if (!s.running) add(tr("Mulai", "Start"), if (s.start <= now + 60_000L) "sekarang" else HomeView.short(s.start, now, zone))
        add(tr("Lama", "Duration"), Fmt.durSec(s.durationSec))
        add(tr("Selesai", "Done"), HomeView.short(s.finish, now, zone))
        col.addView(grid, Ui.matchWrap())

        val why = ArrayList<Pair<String, Int>>()
        if (!s.running && s.idle > 30 * 60_000L) why += tr("Selesai saat Anda offline; pekerja menunggu ±${Fmt.dur(s.idle)}.", "Finishes while you are offline; the worker waits ±${Fmt.dur(s.idle)}.") to Ui.IDLE
        else if (!s.running && b.ps.schedule.isOffline(s.finish)) why += tr("Selesai saat Anda offline — langsung diisi lagi begitu bangun.", "Finishes while you are offline — refill it right when you wake up.") to Ui.FREE_TEXT
        s.event?.let { why += tr("Dimulai saat $it — waktu & biaya sudah dipotong.", "Starts during $it — time & cost already reduced.") to Ui.LAB }
        s.note?.let { why += it to Ui.TEXT2 }
        if (s.tier > 0) why += tr("Langkah menuju builder ke-6.", "Step toward the 6th builder.") to Ui.BUSY
        if (s.gear) why += tr("Gear-up memakai Master Builder dari Builder Base dan bekerja di Home Village.", "Gear-ups use a Master Builder from the Builder Base and work in the Home Village.") to Ui.TEXT2
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
            col.addView(Ui.primary(act, tr("Sudah dimulai", "Started")) { done(); act.startPick(b.tag, pick) }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })
        } else if (!s.running && s.start > now) {
            val on = Repo.sessionReminderOn(act, b.tag, s.start)
            col.addView(Ui.tonal(act, if (on) tr("Pengingat ${Fmt.time(s.start, zone)} aktif · matikan", "Reminder ${Fmt.time(s.start, zone)} on · turn off") else tr("Ingatkan pukul ${Fmt.time(s.start, zone)}", "Remind me at ${Fmt.time(s.start, zone)}")) {
                val sess = Session(s.start, listOf(s), false)
                val nowOn = Repo.toggleSessionReminder(act, b.tag, s.start, Sessions.note(sess))
                done()
                act.renderBody()
                act.snack(if (nowOn) tr("Pengingat ${Fmt.time(s.start, zone)} dipasang", "Reminder ${Fmt.time(s.start, zone)} set") else tr("Pengingat dimatikan", "Reminder off"))
            }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })
        }
        return col
    }

    // ---------------------------------------------------------------- gudang

    /**
     * Kapasitas gudang (Home: Town Hall + Gold/Elixir/Dark Elixir Storage; Builder Base: Builder Hall + storage) —
     * upgrade yang biayanya di atasnya menunggu storage dinaikkan.
     */
    private fun capacityLine(act: MainActivity, box: LinearLayout, b: PlanBundle, bb: Boolean) {
        val v = b.village
        val kinds = if (bb) listOf(Capacity.GOLD, Capacity.ELIXIR) else listOf(Capacity.H_GOLD, Capacity.H_ELIXIR, Capacity.H_DARK)
        val caps = kinds.map { r -> r to (Capacity.of(v, r) ?: return) }
        val steps = (if (bb) b.simBB else b.simHome)?.steps.orEmpty()
        val over = steps.firstOrNull { s ->
            val res = s.item?.resource ?: return@firstOrNull false
            if (res !in kinds) return@firstOrNull false
            val cap = Capacity.of(v, res) ?: return@firstOrNull false
            s.cost > cap
        }
        val text = StringBuilder(tr("Gudang: ", "Storage: ") + caps.joinToString(" · ") { (r, c) -> "${Fmt.money(c)} ${Fmt.resource(r)}" })
        if (over != null) {
            text.append(" — ${over.title} " + tr("butuh", "needs") + " ${Fmt.money(over.cost)} ${Fmt.resource(over.item?.resource ?: "")}" + tr(", storage dinaikkan dulu", ", upgrade storage first"))
        }
        box.addView(note(act, text.toString(), if (over != null) Ui.BUSY else Ui.TEXT2))
    }
}
