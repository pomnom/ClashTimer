package id.clashtimer

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import id.clashtimer.core.BBStrategy
import id.clashtimer.core.Boost
import id.clashtimer.core.HELPER_CYCLE
import id.clashtimer.core.Inst
import id.clashtimer.core.Village
import id.clashtimer.core.BoostKind
import id.clashtimer.core.Boosts
import id.clashtimer.core.Cat
import id.clashtimer.core.ClockTower
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.GameData
import id.clashtimer.core.Lane
import id.clashtimer.core.LaneSlot
import id.clashtimer.core.MagicItem
import id.clashtimer.core.PlanSettings
import id.clashtimer.core.PlannedStep
import id.clashtimer.core.Planner
import id.clashtimer.core.Scored
import id.clashtimer.core.SimResult
import id.clashtimer.core.Sixth
import java.time.ZoneId

/**
 * Tab "Dashboard" (desa yang dipilih saja): ringkasan strategi & boost, hitung mundur tukang & lab
 * tercepat, tukang dan lab yang bekerja + satu saran masing-masing, helper, lalu potion & item.
 */
object HomeView {

    fun render(act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, b: PlanBundle?) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val gd = Repo.gameData(act)
        val ups = Store.upgrades(act).filter { it.tag == tag }
        val acc = Store.accounts(act).firstOrNull { it.tag == tag } ?: return
        val hasExport = Store.exportText(act, tag) != null

        act.addWarnings(box)

        // Data API lebih baru dari export → minta update
        if (b != null) {
            val stale = runCatching { Repo.staleNotes(act, tag, b.village) }.getOrDefault(emptyList())
            if (stale.isNotEmpty()) box.addView(notice(act, tr("Data desa ketinggalan", "Village data is outdated"),
                stale.take(3).joinToString("; ") + tr(" · copy export di game lalu ketuk di sini", " · copy the export in the game, then tap here"), Ui.IDLE) { act.importFromClipboard() })
            // Helper & boost yang sudah bisa dipakai (Home & Builder Base) — selalu paling atas.
            runCatching { ready(act, box, tag, b, ups, now, zone) }
        }

        if (bb && acc.bh == 0 && ups.none { it.kind.builderBase }) {
            box.addView(Ui.muted(act, tr("Belum ada data Builder Base di export desa ini.", "No Builder Base data in this village export yet."), Ui.BODY_M).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 24), Ui.dp(act, 4), 0)
            })
            return
        }

        EventsUi.homeBanners(act, box, bb, now)

        val ps = b?.ps ?: Store.planSettings(act, tag)
        val sim = if (bb) b?.simBB else b?.simHome
        val labels = Sessions.labels(ups, now)
        val bLanes = PlanView.lanes(bb, lab = false)
        val lLanes = PlanView.lanes(bb, lab = true)
        val freeB = freeCards(act, tag, bb, b, sim, ups, labels, now, bLanes)
        val freeL = freeCards(act, tag, bb, b, sim, ups, labels, now, lLanes)

        summary(act, box, tag, bb, b, sim, ps, now, zone)
        if (!bb && b != null) {
            runCatching { war(act, box, tag, b, now, zone) }
            cwl(act, box, b, ups, now, zone)
        }
        countdowns(act, box, tag, bb, ups, labels, freeB, freeL, now, zone)
        finished(act, box, bb, ups, gd, labels, now)
        workers(act, box, tag, bb, false, b, sim, ups, gd, labels, freeB, now, zone, hasExport)
        workers(act, box, tag, bb, true, b, sim, ups, gd, labels, freeL, now, zone, hasExport)
        if (!bb) helpers(act, box, tag, ups, b, now, zone)
        if (bb) clockTower(act, box, tag, b, ups, now, zone)
        if (hasExport) items(act, box, tag, bb, b, now, zone)
        if (!bb) superTroops(act, box, tag, zone)
    }

    // ---------------------------------------------------------------- kartu kecil bersama

    /** Kartu pemberitahuan ringkas (peringatan, CWL): judul berwarna + keterangan. */
    private fun notice(act: MainActivity, title: String, body: String?, color: Int, onClick: (() -> Unit)? = null): View {
        val bg = when (color) {
            Ui.BUSY -> Ui.BUSY_BG
            Ui.LAB -> Ui.LAB_BG
            else -> Ui.SURFACE
        }
        val p = Ui.panel(act, padH = 14, padV = 10, color = bg, stroke = if (bg == Ui.SURFACE) color and 0x66FFFFFF else null, radiusDp = 14)
        p.addView(Ui.text(act, title, Ui.BODY_M).apply { textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(color) })
        if (body != null) p.addView(Ui.muted(act, body).apply { textSize = 13f; setTextColor(Ui.TEXT2) })
        if (onClick != null) {
            p.isClickable = true
            p.setOnClickListener { onClick() }
            Ui.ripple(p)
        }
        p.layoutParams = Ui.matchWrap(10, act)
        return p
    }

    private fun gap(act: MainActivity, v: View, dp: Int = 12): View {
        v.layoutParams = (v.layoutParams as? LinearLayout.LayoutParams ?: Ui.matchWrap()).apply { bottomMargin = Ui.dp(act, dp) }
        return v
    }

    /** "Bebas sekarang · 2" dengan angka hijau. */
    fun countTitle(text: String, n: Int): CharSequence {
        val s = SpannableString("$text · $n")
        s.setSpan(ForegroundColorSpan(Ui.FREE), text.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return s
    }

    /** Judul bagian: "Tukang · 4/5 bekerja" + tautan "Lihat semua ›". */
    private fun sectionHead(act: MainActivity, title: String, sub: String?, link: String, onLink: () -> Unit): View {
        val row = Ui.hbox(act).apply { setPadding(Ui.dp(act, 2), Ui.dp(act, 10), 0, Ui.dp(act, 2)) }
        val s = SpannableString(title + (sub?.let { " · $it" } ?: ""))
        if (sub != null) s.setSpan(ForegroundColorSpan(Ui.TEXT2), title.length, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        row.addView(Ui.text(act, s, Ui.TITLE_S).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, Ui.weight1())
        row.addView(TextView(act).apply {
            text = "$link ›"
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.FREE_TEXT)
            gravity = Gravity.CENTER_VERTICAL
            minHeight = Ui.dp(act, 40)
            setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 4), 0)
            isClickable = true
            setOnClickListener { onLink() }
        })
        return row
    }

    /** Pil kecil berwarna untuk ringkasan (Gold Pass, event, CWL, boost). */
    private fun chip(act: MainActivity, text: String, color: Int): View {
        val c = Ui.hbox(act).apply {
            background = Ui.rounded(Ui.SURFACE2, Ui.dp(act, 15).toFloat())
            setPadding(Ui.dp(act, 10), 0, Ui.dp(act, 10), 0)
            minimumHeight = Ui.dp(act, 30)
        }
        c.addView(View(act).apply { background = Ui.rounded(color, Ui.dp(act, 4).toFloat()) },
            LinearLayout.LayoutParams(Ui.dp(act, 7), Ui.dp(act, 7)).apply { marginEnd = Ui.dp(act, 6) })
        c.addView(TextView(act).apply { this.text = text; textSize = 12f; setTextColor(Ui.TEXT); maxLines = 1 })
        return c
    }

    /** "hari ini", "Rab", atau "12 Nov" untuk yang lebih jauh. */
    private fun day(t: Long, now: Long, zone: ZoneId): String {
        val d1 = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val d0 = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return when {
            d1 == d0 -> tr("hari ini", "today")
            d1.isBefore(d0.plusDays(6)) -> java.time.format.DateTimeFormatter.ofPattern("EEE", Fmt.locale).format(d1)
            else -> Fmt.dateShort(t, zone)
        }
    }

    // ---------------------------------------------------------------- 0. ringkasan strategi & boost

    fun strategyName(act: MainActivity, tag: String, ps: PlanSettings, bb: Boolean): String =
        if (bb) (if (Store.bobDone(act, tag)) "Max" else ps.bbStrategy.label) else VillageOptions.shortStrategy(ps.strategy)

    private fun summary(act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, b: PlanBundle?, sim: SimResult?, ps: PlanSettings, now: Long, zone: ZoneId) {
        val card = Ui.panel(act, padH = 14, padV = 12)
        val top = Ui.hbox(act)
        top.addView(Ui.icon(act, R.drawable.ic_target, 20, Ui.FREE_TEXT), LinearLayout.LayoutParams(Ui.dp(act, 20), Ui.dp(act, 20)).apply { marginEnd = Ui.dp(act, 10) })
        val col = Ui.vbox(act)
        val strat = strategyName(act, tag, ps, bb)
        val title: String
        val sub: String
        if (b == null || sim == null) {
            title = if (Store.exportText(act, tag) == null) tr("Import ulang desa ini sekali", "Re-import this village once") else tr("Menghitung rencana…", "Calculating plan…")
            sub = tr("strategi $strat", "strategy $strat")
        } else {
            val t = Planner.target(sim, ps, bb).takeIf { it != Long.MAX_VALUE }
            title = "$strat · ${PlanView.targetLabel(b, sim, bb)}" + (t?.let { " ±" + Fmt.date(it, zone) } ?: tr(" · belum tercapai", " · not reachable"))
            sub = etaLine(act, b, sim, bb, now, zone)
        }
        col.addView(Ui.text(act, title, Ui.BODY_M).apply { textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT) })
        col.addView(Ui.muted(act, sub).apply { textSize = 12f })
        top.addView(col, Ui.weight1())
        top.addView(Ui.icon(act, R.drawable.ic_chevron_right, 18, Ui.TEXT2))
        card.addView(top)

        val chips = chipsFor(act, tag, bb, ps, now, zone)
        if (chips.isNotEmpty()) {
            val row = Ui.hbox(act)
            chips.forEachIndexed { i, (txt, color) ->
                row.addView(chip(act, txt, color), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    if (i > 0) marginStart = Ui.dp(act, 6)
                })
            }
            card.addView(android.widget.HorizontalScrollView(act).apply {
                isHorizontalScrollBarEnabled = false
                addView(row)
            }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 10) })
        }
        card.isClickable = true
        card.contentDescription = tr("Opsi desa: strategi, Gold Pass, CWL", "Village options: strategy, Gold Pass, CWL")
        card.setOnClickListener { VillageOptions.sheet(act, tag, bb) }
        Ui.ripple(card)
        box.addView(gap(act, card))
    }

    /** "tukang ±27 Nov · lab ±7 Feb (penentu)" atau syarat B.O.B. */
    private fun etaLine(act: MainActivity, b: PlanBundle, sim: SimResult, bb: Boolean, now: Long, zone: ZoneId): String {
        val six = sim.sixth
        if (bb && b.ps.bbStrategy == BBStrategy.SIXTH && six != null && !Store.bobDone(act, b.tag)) return sixLine(six)
        val list = if (bb) listOf(tr("master builder", "master builder") to sim.bbBuilderDone, "star lab" to sim.starLabDone)
        else listOf(tr("tukang", "builders") to sim.builderDone, "lab" to sim.labDone)
        val open = list.filter { it.second != null && it.second!! > now }
        val last = open.maxByOrNull { it.second!! }
        return list.joinToString(" · ") { (name, t) ->
            when {
                t == null || t <= now -> tr("$name max", "$name max")
                else -> "$name ±${Fmt.dateShort(t, zone)}" + if (open.size > 1 && last?.first == name) tr(" (penentu)", " (decisive)") else ""
            }
        }
    }

    private fun sixLine(six: id.clashtimer.core.SixthPlan): String {
        val done = six.reqs.count { it.done }
        val next = six.reqs.firstOrNull { !it.done }
        return tr("$done/${six.reqs.size} syarat", "$done/${six.reqs.size} requirements") + (next?.let { tr(" · berikutnya ${it.label}", " · next ${it.label}") } ?: tr(" · semua syarat selesai", " · all requirements done"))
    }

    /** Gold Pass (+ kenaikan berikutnya), event, CWL, boost yang aktif. */
    private fun chipsFor(act: MainActivity, tag: String, bb: Boolean, ps: PlanSettings, now: Long, zone: ZoneId): List<Pair<String, Int>> {
        val out = ArrayList<Pair<String, Int>>()
        val gp = ps.gold.at(now)
        if (bb) {
            if (gp.builderPct > 0) out += "Gold Pass Builder ${gp.builderPct}%" to Ui.BUSY
        } else if (gp.builderPct > 0 || gp.researchPct > 0) {
            val cur = if (gp.builderPct == gp.researchPct) "${gp.builderPct}%" else "B${gp.builderPct}% · R${gp.researchPct}%"
            val next = ps.gold.steps.filter { it.at > now }.minByOrNull { it.at }
            val up = next?.let { s -> " · ${maxOf(s.builderPct, s.researchPct)}% ±${day(s.at, now, zone)}" } ?: ""
            out += "Gold Pass $cur$up" to Ui.BUSY
        }
        for (e in ps.events) {
            if (!e.enabled || e.end <= now || e.start > now + 7 * DAY) continue
            val mine = if (bb) e.covers(Lane.BB_BUILDER) else e.covers(Lane.BUILDER) || e.covers(Lane.LAB)
            if (!mine) continue
            out += (if (e.start <= now) tr("${e.name} −${e.timePct}% s/d ${Fmt.dateShort(e.end, zone)}", "${e.name} −${e.timePct}% until ${Fmt.dateShort(e.end, zone)}") else tr("${e.name} mulai ${day(e.start, now, zone)}", "${e.name} starts ${day(e.start, now, zone)}")) to Ui.LAB
        }
        if (!bb && ps.cwl.enabled) {
            val w = ps.cwl.next(now)
            if (w.first - now < 7 * DAY) {
                val txt = if (w.first <= now) tr("CWL s/d ${Fmt.dateShort(w.last + 1, zone)}", "CWL until ${Fmt.dateShort(w.last + 1, zone)}") else "CWL ${Fmt.dateShort(w.first, zone)}–${Fmt.dateShort(w.last + 1, zone)}"
                out += tr("$txt · hero ditunda", "$txt · heroes paused") to Ui.IDLE
            }
        }
        for (a in Boosts.merged(Store.boosts(act, tag))) {
            if (a.start > now || a.end <= now || a.kind.lanes.none { it.bb == bb }) continue
            out += "${a.kind.label} ${Fmt.dur(a.end - now)}" to Ui.FREE
        }
        return out
    }

    // ---------------------------------------------------------------- siap dipakai (helper & boost)

    /**
     * Kartu di paling atas untuk helper (Builder's Apprentice, Lab Assistant, Alchemist) dan boost
     * Clock Tower yang sudah bisa dipakai, seperti kartu "Data desa ketinggalan". Ketuk = catat.
     */
    private fun ready(act: MainActivity, box: LinearLayout, tag: String, b: PlanBundle, ups: List<Upgrade>, now: Long, zone: ZoneId) {
        val v = b.village
        val gd = Repo.gameData(act)
        val fin = Repo.finishes(act, tag)
        fun since(t: Long) = if (now - t > 5 * 60_000L) tr(" · sejak ${short(t, now, zone)}", " · since ${short(t, now, zone)}") else ""
        for ((id, kind) in listOf(GameData.APPRENTICE to Kind.BUILDER, GameData.LAB_ASSISTANT to Kind.LAB, GameData.ALCHEMIST to null)) {
            val h = v.helper(id) ?: continue
            if (h.level <= 0) continue
            val readyAt = helperReadyAt(act, tag, v, id, h.cooldown, kind, fin, now) ?: continue
            val name = gd.name(id)
            if (kind == null) {
                box.addView(notice(act, tr("🧪 $name siap dipakai", "🧪 $name is ready"),
                    tr("tukar sumber daya bila perlu", "convert resources if you need to") + since(readyAt) + tr(" · ketuk bila sudah", " · tap when done"), Ui.FREE_TEXT) {
                    Store.setHelperUsed(act, tag, id, System.currentTimeMillis(), null)
                    Repo.reproject(act, tag)
                    act.dataChanged()
                })
                continue
            }
            // Upgrade yang bisa dibantu: masih berjalan minimal 1 jam lagi, terlama dulu.
            val work = ups.filter { it.kind == kind && !it.id.contains("|gear|") && it.finishAt > now + 3_600_000L }
                .sortedByDescending { it.finishAt }
            if (work.isEmpty()) continue
            val top = work.first()
            box.addView(notice(act, tr("🧑‍🔧 $name siap dipakai", "🧑‍🔧 $name is ready"),
                tr("tugaskan ke ${top.title()} (sisa ${Fmt.dur(top.finishAt - now)})", "assign to ${top.title()} (${Fmt.dur(top.finishAt - now)} left)") +
                    since(readyAt) + tr(" · ketuk bila sudah", " · tap when done"), Ui.FREE_TEXT) {
                val labels = work.map { "${it.title()} · ${Fmt.dur(it.finishAt - System.currentTimeMillis())}" as CharSequence }.toTypedArray()
                com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
                    .setTitle(tr("$name ditugaskan ke…", "$name assigned to…"))
                    .setItems(labels) { _, w ->
                        Store.setHelperUsed(act, tag, id, System.currentTimeMillis(), work[w].id)
                        Repo.reproject(act, tag)
                        act.dataChanged()
                    }
                    .setNegativeButton(tr("Batal", "Cancel"), null).show()
            })
        }
        // Clock Tower: boost gratis siap dan Master Builder / Star Lab sedang bekerja.
        val ci = clockInfo(act, tag, v, ups, now) ?: return
        if (ci.active != null || ci.upgrading || ci.readyAt != null || ci.minutes <= 0) return
        val busy = ups.count { (it.kind == Kind.BB_BUILDER || it.kind == Kind.STAR_LAB) && !it.id.contains("|gear|") && it.finishAt > now + 10 * 60_000L }
        if (busy == 0) return
        box.addView(notice(act, tr("⏱️ Clock Tower: boost gratis siap", "⏱️ Clock Tower: free boost ready"),
            tr("10× selama ${ci.minutes} menit · $busy pekerja Builder Base sedang bekerja", "10× for ${ci.minutes} minutes · $busy Builder Base workers busy") +
                (ci.lastFree?.let { since(it + ClockTower.COOLDOWN) } ?: "") + tr(" · ketuk untuk mencatat", " · tap to log it"), Ui.FREE_TEXT) {
            ItemsUi.useClockTower(act, tag, ci.level)
        })
    }

    /**
     * Kapan helper [id] bebas dan perlu ditugaskan (≤ now), atau null bila masih cooldown / otomatis
     * membantu upgrade yang sama lagi (upgrade itu masih berjalan saat cooldown habis).
     */
    private fun helperReadyAt(
        act: MainActivity, tag: String, v: Village, id: Int, cooldownSec: Long, kind: Kind?,
        fin: Map<String, Long>, now: Long,
    ): Long? {
        val lane = when (kind) {
            Kind.BUILDER -> Lane.BUILDER
            Kind.LAB -> Lane.LAB
            else -> null
        }
        val rec = Store.helperUsed(act, tag, id)?.takeIf { it.first > v.exportAt }
        var ready = rec?.let { it.first + HELPER_CYCLE } ?: (v.exportAt + cooldownSec * 1000)
        val target = when {
            lane == null -> null
            rec != null -> rec.second
            else -> v.timers.firstOrNull { it.helped && it.lane == lane }?.uid
        }
        val tf = target?.let { fin[it] }
        if (tf != null) while (ready <= now && tf > ready) ready += HELPER_CYCLE
        return ready.takeIf { it <= now }
    }

    // ---------------------------------------------------------------- war / CWL

    /** Status war / CWL dari ClashKing (info saja): yang sedang diikuti dan kapan hari perangnya berakhir. */
    private fun war(act: MainActivity, box: LinearLayout, tag: String, b: PlanBundle, now: Long, zone: ZoneId) {
        val ws = Store.warState(act, tag) ?: return
        if (ws.end <= now) return
        val cwl = ws.kind == "cwl"
        val recheck = { ApiSync.syncAsync(act) { msg ->
            act.dataChanged()
            android.widget.Toast.makeText(act, msg, android.widget.Toast.LENGTH_SHORT).show()
        } }
        val info = Store.ckInfo(act, tag)
        val title = (if (cwl) "⚔️ CWL" else "⚔️ War") + (info?.clanName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
        val lines = ArrayList<String>()
        lines += if (cwl) tr("hari perang berakhir ${Fmt.at(ws.end, zone)}", "war day ends ${Fmt.at(ws.end, zone)}")
        else tr("berakhir ${Fmt.at(ws.end, zone)}", "ends ${Fmt.at(ws.end, zone)}")
        if (ws.needEnd > ws.end) lines += if (cwl) tr("hari perang berikutnya berakhir ${Fmt.at(ws.needEnd, zone)}", "next war day ends ${Fmt.at(ws.needEnd, zone)}")
        else tr("war berikutnya berakhir ${Fmt.at(ws.needEnd, zone)}", "next war ends ${Fmt.at(ws.needEnd, zone)}")
        if (cwl) info?.league?.takeIf { it.isNotBlank() }?.let { lines += it }
        lines += tr("ClashKing · ${short(ws.at, now, zone)} · ketuk untuk perbarui", "ClashKing · ${short(ws.at, now, zone)} · tap to refresh")
        box.addView(notice(act, title, lines.joinToString("\n"), Ui.TEXT) { recheck() })
    }

    // ---------------------------------------------------------------- CWL

    /** Hero yang masih upgrade saat CWL (CWL sendiri tampil di ringkasan). */
    private fun cwl(act: MainActivity, box: LinearLayout, b: PlanBundle, ups: List<Upgrade>, now: Long, zone: ZoneId) {
        if (!b.ps.cwl.enabled) return
        val fin = ups.associate { it.id to it.finishAt }
        for (i in b.village.insts) {
            if (!i.upgrading || i.item.cat != Cat.HERO) continue
            val f = fin[i.uid] ?: continue
            if (f > now && b.ps.cwl.overlap(now, f) != null) {
                box.addView(notice(act, tr("${i.item.name} masih upgrade saat CWL", "${i.item.name} still upgrading during CWL"), tr("selesai ${Fmt.at(f, zone)} · pertimbangkan Book of Heroes / potion", "done ${Fmt.at(f, zone)} · consider Book of Heroes / potion"), Ui.IDLE))
            }
        }
    }

    // ---------------------------------------------------------------- 1–2. hitung mundur tercepat

    private fun countdowns(
        act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, ups: List<Upgrade>, labels: Map<String, String>,
        freeB: List<FreeCard>, freeL: List<FreeCard>, now: Long, zone: ZoneId,
    ) {
        val bKinds = PlanView.lanes(bb, false).map { Kind.of(it) }.toSet()
        val lKinds = PlanView.lanes(bb, true).map { Kind.of(it) }.toSet()
        val row = Ui.hbox(act).apply { gravity = Gravity.TOP }
        row.addView(fastTile(act, (if (bb) "Master Builder" else tr("Tukang", "Builders")) + tr(" tercepat", " soonest"), false,
            ups.filter { it.kind in bKinds && it.finishAt > now && !it.extra }.sortedBy { it.finishAt }, labels, freeB, now, zone) {
            act.openTab(MainActivity.Tab.BUILDERS)
        }, Ui.weight1())
        row.addView(fastTile(act, (if (bb) "Star Lab" else "Lab") + tr(" tercepat", " soonest"), true,
            ups.filter { it.kind in lKinds && it.finishAt > now }.sortedBy { it.finishAt }, labels, freeL, now, zone) {
            act.openTab(MainActivity.Tab.LAB)
        }, Ui.weight1().apply { marginStart = Ui.dp(act, 10) })
        box.addView(gap(act, row.apply { layoutParams = Ui.matchWrap() }))
    }

    private fun fastTile(
        act: MainActivity, head: String, lab: Boolean, running: List<Upgrade>, labels: Map<String, String>,
        free: List<FreeCard>, now: Long, zone: ZoneId, onClick: () -> Unit,
    ): View {
        val color = if (lab) Ui.LAB else Ui.BUSY
        val card = Ui.panel(act, padH = 12, padV = 12)
        val first = running.firstOrNull()
        val top = Ui.hbox(act)
        top.addView(Ui.muted(act, head).apply { textSize = 12f; maxLines = 1 }, Ui.weight1())
        val label = first?.let { labels[it.id] } ?: free.firstOrNull()?.label
        if (label != null) top.addView(Sessions.badge(act, label, lab))
        card.addView(top)
        val none = first == null && free.isEmpty()
        val clock = Ui.monoText(act, if (first != null) act.clock(first.finishAt - now) else if (none) "—" else "bebas", 26f).apply {
            setTextColor(if (first != null) color else if (none) Ui.TEXT2 else Ui.FREE_TEXT)
            setPadding(0, Ui.dp(act, 2), 0, 0)
        }
        if (first != null) act.registerCountdown(clock, first.finishAt, clockStyle = true)
        card.addView(clock)
        card.addView(Ui.muted(act, first?.let { Widgets.shortTitle(it) + " · " + short(it.finishAt, now, zone) } ?: if (none) tr("tidak ada yang berjalan", "nothing running") else tr("siap diisi", "ready to fill")).apply {
            textSize = 12f
            setTextColor(Ui.TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val second = running.getOrNull(1)
        val (foot, footColor) = when {
            free.isNotEmpty() -> (free.first().label + tr(" bebas sekarang", " free now") + if (free.size > 1) " +${free.size - 1}" else "") to Ui.FREE_TEXT
            lab && second != null -> "${labels[second.id] ?: second.kind.label} ${act.countdown(second.finishAt - now)}" to Ui.TEXT2
            first != null -> tr("semua bekerja", "all busy") to Ui.TEXT2
            else -> "" to Ui.TEXT2
        }
        if (foot.isNotEmpty()) {
            val f = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 6), 0, 0) }
            f.addView(View(act).apply { background = Ui.rounded(if (footColor == Ui.FREE_TEXT) Ui.FREE else color, Ui.dp(act, 4).toFloat()) },
                LinearLayout.LayoutParams(Ui.dp(act, 7), Ui.dp(act, 7)).apply { marginEnd = Ui.dp(act, 6) })
            f.addView(TextView(act).apply {
                text = foot
                textSize = 12f
                setTextColor(footColor)
                if (footColor == Ui.FREE_TEXT) setTypeface(typeface, Typeface.BOLD)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, Ui.weight1())
            card.addView(f)
        }
        card.isClickable = true
        card.setOnClickListener { onClick() }
        Ui.ripple(card)
        return card
    }

    // ---------------------------------------------------------------- 3–4. tukang & lab + satu saran

    private fun workers(
        act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, lab: Boolean, b: PlanBundle?, sim: SimResult?,
        ups: List<Upgrade>, gd: GameData, labels: Map<String, String>, free: List<FreeCard>, now: Long, zone: ZoneId, hasExport: Boolean,
    ) {
        val kinds = PlanView.lanes(bb, lab).map { Kind.of(it) }.toSet()
        val running = ups.filter { it.kind in kinds && it.finishAt > now }.sortedBy { it.finishAt }
        // Tidak ada yang bebas: saran untuk pekerja yang paling cepat bebas (+ lonceng pengingat).
        val next = if (free.isEmpty()) nextStep(sim, PlanView.lanes(bb, lab), now) else null
        if (running.isEmpty() && free.isEmpty() && next == null) return
        val title = when {
            lab -> if (bb) "Star Lab" else "Lab"
            else -> if (bb) "Master Builder" else tr("Tukang", "Builders")
        }
        val sub = if (lab) tr("${running.size} berjalan", "${running.size} running") else act.builderCounts(tag, bb).let { (busy, total) -> tr("$busy/$total bekerja", "$busy/$total working") }
        box.addView(sectionHead(act, title, sub, tr("Lihat semua", "See all")) { act.openTab(if (lab) MainActivity.Tab.LAB else MainActivity.Tab.BUILDERS) })
        free.firstOrNull()?.let { box.addView(gap(act, freeCard(act, tag, b, it, bb, now, zone, hasExport), 8)) }
        if (free.size > 1) box.addView(Ui.muted(act, tr("+${free.size - 1} bebas lagi · lihat semua", "+${free.size - 1} more free · see all")).apply {
            textSize = 13f
            setTextColor(Ui.FREE_TEXT)
            setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 8))
            isClickable = true
            setOnClickListener { act.openTab(if (lab) MainActivity.Tab.LAB else MainActivity.Tab.BUILDERS) }
        })
        if (running.isEmpty() && next == null) {
            box.addView(Ui.space(act), LinearLayout.LayoutParams(1, Ui.dp(act, 4)))
            return
        }
        val panel = Ui.panel(act, padH = 14, padV = 2)
        running.forEachIndexed { i, u ->
            panel.addView(workRow(act, u, gd, labels[u.id], now))
            if (i < running.size - 1) panel.addView(line(act))
        }
        if (next != null && b != null) {
            if (running.isNotEmpty()) panel.addView(View(act), LinearLayout.LayoutParams(1, Ui.dp(act, 8)))
            panel.addView(nextBox(act, b, next, lab, now, zone), Ui.matchWrap().apply {
                if (running.isEmpty()) topMargin = Ui.dp(act, 12)
                bottomMargin = Ui.dp(act, 12)
            })
        }
        box.addView(gap(act, panel))
    }

    /** Langkah rencana berikutnya untuk [lanes] (belum dimulai, paling awal). */
    fun nextStep(sim: SimResult?, lanes: Set<Lane>, now: Long): PlannedStep? =
        sim?.steps?.filter { it.lane in lanes && !it.running && it.start > now + 60_000L }?.minByOrNull { it.start }

    /** "SARAN BERIKUTNYA · LAB BEBAS 17:14 — Rage Spell → 6" + lonceng pengingat. */
    private fun nextBox(act: MainActivity, b: PlanBundle, s: PlannedStep, lab: Boolean, now: Long, zone: ZoneId): View {
        val p = Ui.panel(act, padH = 12, padV = 10, color = Ui.SURFACE2, radiusDp = 14)
        val row = Ui.hbox(act)
        val col = Ui.vbox(act)
        col.addView(Ui.muted(act, tr("SARAN BERIKUTNYA", "NEXT SUGGESTION") + " · ${Sessions.label(s).uppercase(Fmt.locale)} ${if (s.idle > 10 * 60_000L) tr("MULAI", "STARTS") else tr("BEBAS", "FREE")} ${short(s.start, now, zone).uppercase(Fmt.locale)}").apply {
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        })
        col.addView(Ui.text(act, Sessions.title(s), Ui.BODY_M).apply { textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT) })
        val cost = if (s.cost > 0) " · ${Fmt.money(s.cost)} ${Fmt.resource(s.item?.resource ?: "")}" else ""
        col.addView(Ui.muted(act, tr("${Fmt.durSec(s.durationSec)}$cost · selesai ${short(s.finish, now, zone)}", "${Fmt.durSec(s.durationSec)}$cost · done ${short(s.finish, now, zone)}")).apply { textSize = 12f })
        row.addView(col, Ui.weight1())
        row.addView(bell(act, b.tag, Session(s.start, listOf(s), false)))
        p.addView(row)
        p.isClickable = true
        p.setOnClickListener { PlanView.stepSheet(act, b, s, now, zone) }
        return p
    }

    /** Tombol lonceng: pasang / lepas pengingat untuk sesi login ini. */
    fun bell(act: MainActivity, tag: String, s: Session): View {
        val on = Repo.sessionReminderOn(act, tag, s.at)
        val pill = Ui.hbox(act).apply {
            background = if (on) Ui.rounded(Ui.FREE_BG, Ui.dp(act, 18).toFloat(), Ui.FREE_LINE, Ui.dp(act, 1))
            else Ui.rounded(android.graphics.Color.TRANSPARENT, Ui.dp(act, 18).toFloat(), Ui.OUTLINE, Ui.dp(act, 1))
            setPadding(Ui.dp(act, 10), 0, Ui.dp(act, 12), 0)
            minimumHeight = Ui.dp(act, 40)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            contentDescription = if (on) tr("Pengingat aktif, ketuk untuk mematikan", "Reminder on, tap to turn off") else tr("Ingatkan saat waktunya login", "Remind me when it is time to log in")
            setOnClickListener {
                val nowOn = Repo.toggleSessionReminder(act, tag, s.at, Sessions.note(s))
                act.renderBody()
                act.snack(if (nowOn) tr("Pengingat ${Fmt.time(s.at, act.zoneId())} dipasang", "Reminder ${Fmt.time(s.at, act.zoneId())} set") else tr("Pengingat dimatikan", "Reminder off"))
            }
        }
        val c = if (on) Ui.FREE_TEXT else Ui.TEXT
        pill.addView(Ui.icon(act, if (on) R.drawable.ic_bell else R.drawable.ic_bell_off, 16, c), LinearLayout.LayoutParams(Ui.dp(act, 16), Ui.dp(act, 16)).apply { marginEnd = Ui.dp(act, 6) })
        pill.addView(TextView(act).apply { text = if (on) tr("Diingatkan", "Reminding") else tr("Ingatkan", "Remind me"); textSize = 13f; setTextColor(c) })
        return pill
    }

    // ---------------------------------------------------------------- pekerja bebas

    class FreeCard(val label: String, val lane: Lane, val slot: LaneSlot?, val pick: Scored?)

    /** Satu kartu per pekerja [lanes] yang bebas sekarang, dengan saran teratas masing-masing. */
    fun freeCards(
        act: MainActivity, tag: String, bb: Boolean, b: PlanBundle?, sim: SimResult?,
        ups: List<Upgrade>, labels: Map<String, String>, now: Long, lanes: Set<Lane>,
    ): List<FreeCard> {
        val cards = ArrayList<FreeCard>()
        val nowSteps = sim?.steps?.filter { it.lane.bb == bb && !it.running && it.start <= now + 60_000L }.orEmpty()
        val acc = Store.accounts(act).firstOrNull { it.tag == tag }
        for (lane in lanes.sortedBy { it.ordinal }) {
            val kind = Kind.of(lane)
            val running = ups.filter { it.kind == kind && it.finishAt > now && !it.extra }
            val total = b?.let { Planner.laneCount(it.village, lane, it.ps) } ?: when (lane) {
                Lane.BUILDER -> acc?.builders ?: 5
                else -> running.size
            }
            val free = (total - running.size).coerceAtLeast(0)
            if (free == 0) continue
            val freeLabels = when (lane) {
                Lane.BUILDER, Lane.BB_BUILDER -> {
                    val p = if (lane == Lane.BUILDER) "B" else "M"
                    val used = running.mapNotNull { labels[it.id] }.toSet()
                    (1..total).map { "$p$it" }.filter { it !in used }.take(free)
                }
                Lane.LAB -> listOf("Lab")
                Lane.PET -> listOf("Pet")
                Lane.STAR_LAB -> listOf("Star")
            }
            val slot = b?.slots?.firstOrNull { it.lane == lane && it.freeAt <= now }
            val picks = ArrayList<Scored>()
            if (slot != null) {
                // Urutan rencana (tercepat ke target) dulu, lalu sisa peringkat.
                for (st in nowSteps.filter { it.lane == lane }.sortedBy { it.laneIndex }) {
                    match(slot, st, picks)?.let { picks += it }
                }
                for (p in slot.ranked) {
                    if (picks.size >= freeLabels.size) break
                    if (picks.none { it === p }) picks += p
                }
            }
            // Label pekerja mengikuti rencana bila cocok.
            val stepLabels = nowSteps.filter { it.lane == lane }.sortedBy { it.laneIndex }.map { Sessions.label(it) }.filter { it in freeLabels }
            val order = (stepLabels + freeLabels).distinct()
            order.forEachIndexed { i, l -> cards += FreeCard(l, lane, slot, picks.getOrNull(i)) }
        }
        return cards
    }

    private fun match(slot: LaneSlot, st: PlannedStep, used: List<Scored>): Scored? =
        slot.ranked.firstOrNull { p ->
            p.c.item.id == st.item?.id && p.c.gear == st.gear && p.c.fromLevel == st.fromLevel && p.c.isNew == st.isNew && used.none { it === p }
        }

    fun freeCard(act: MainActivity, tag: String, b: PlanBundle?, fc: FreeCard, bb: Boolean, now: Long, zone: ZoneId, hasExport: Boolean): View {
        val lab = fc.lane == Lane.LAB || fc.lane == Lane.PET || fc.lane == Lane.STAR_LAB
        val card = Ui.panel(act, padH = 14, padV = 12, stroke = Ui.FREE_LINE)
        val p = fc.pick
        val top = Ui.hbox(act)
        top.addView(Ui.gameIcon(act, p?.let { Icons.forItem(it.c.item, it.c.fromLevel) }, p?.c?.item?.name ?: fc.label, 56,
            Sessions.badge(act, fc.label, lab)))
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
        if (p == null) {
            val msg = when {
                !hasExport -> tr("Import ulang desa ini sekali supaya saran muncul", "Re-import this village once to get suggestions")
                b == null -> tr("Menghitung saran…", "Calculating suggestions…")
                else -> tr("Tidak ada upgrade yang tersedia", "No upgrades available")
            }
            col.addView(Ui.text(act, tr("${fc.label} bebas", "${fc.label} free"), Ui.TITLE_S).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT) })
            col.addView(Ui.muted(act, msg).apply { textSize = 13f })
            top.addView(col, Ui.weight1())
            card.addView(top)
            return card
        }
        ItemMenu.attach(act, card, tag, p.c.item.id)
        col.addView(Ui.text(act, p.c.title, Ui.TITLE_S).apply { textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT) })
        col.addView(Ui.muted(act, meta(p)).apply { textSize = 13f })
        val ps = b?.ps
        if (ps != null) {
            val (txt, kind) = Sessions.outcome(p, ps.schedule, now, zone)
            col.addView(TextView(act).apply {
                text = txt
                textSize = 13f
                setTextColor(when (kind) { 1 -> Ui.FREE_TEXT; -1 -> Ui.IDLE; else -> Ui.TEXT2 })
                setPadding(0, Ui.dp(act, 2), 0, 0)
            })
        }
        tags(p)?.let { col.addView(Ui.muted(act, it).apply { textSize = 12f; setTextColor(Ui.BUSY) }) }
        p.reason?.let { col.addView(Ui.muted(act, it).apply { textSize = 12f }) }
        if (b != null) eventTip(act, b, p)?.let { col.addView(it) }
        top.addView(col, Ui.weight1())
        card.addView(top)
        val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
        r.addView(Ui.primary(act, tr("Sudah dimulai", "Started")) { act.startPick(tag, p) }, Ui.weight1())
        val slot = fc.slot
        if (slot != null) r.addView(Ui.secondary(act, tr("Opsi lain", "Other options")) { PickSheet.show(act, tag, slot, fc.label) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(act, 8) })
        card.addView(r)
        return card
    }

    /** "14j · 1,5 jt Gold". */
    fun meta(p: Scored): String {
        val cost = if (p.cost > 0) "${Fmt.money(p.cost)} ${Fmt.resource(p.c.item.resource)}" else "gratis"
        return "${Fmt.durSec(p.duration)} · $cost"
    }

    /** Tanda kecil: langkah builder ke-6, event. */
    fun tags(p: Scored): String? = listOfNotNull(
        if (p.tier > 0) tr("langkah B.O.B", "B.O.B step") else null,
        p.event?.let { "${it.name} −${it.timePct}%" },
    ).joinToString(" · ").ifBlank { null }

    /** Event yang mulai dalam 24 jam: tunjukkan hematnya bila upgrade ini ditunda sampai event. */
    private fun eventTip(act: MainActivity, b: PlanBundle, pick: Scored): View? {
        if (pick.event != null || pick.c.gear) return null
        val start = pick.start
        val ev = b.ps.events.filter { it.enabled && it.covers(pick.c.lane) && it.start > start && it.start - start < DAY }
            .minByOrNull { it.start } ?: return null
        val dur = b.ps.durationAt(pick.c, ev.start)
        val cost = b.ps.costAt(pick.c, ev.start)
        if (dur >= pick.duration) return null
        return Ui.muted(act,
            tr("${ev.name} mulai ${short(ev.start, start, act.zoneId())}: ${Fmt.durSec(dur)}", "${ev.name} starts ${short(ev.start, start, act.zoneId())}: ${Fmt.durSec(dur)}") +
                (if (cost < pick.cost) " · ${Fmt.money(cost)} ${Fmt.resource(pick.c.item.resource)}" else "") +
                tr(" — isi dengan upgrade singkat dulu", " — fill with a short upgrade first")).apply {
            textSize = 12f
            setTextColor(Ui.LAB)
            setPadding(0, Ui.dp(act, 2), 0, 0)
        }
    }

    // ---------------------------------------------------------------- baru selesai

    private fun finished(act: MainActivity, box: LinearLayout, bb: Boolean, ups: List<Upgrade>, gd: GameData, labels: Map<String, String>, now: Long) {
        val kinds = if (bb) setOf(Kind.BB_BUILDER, Kind.STAR_LAB) else setOf(Kind.BUILDER, Kind.LAB, Kind.PET)
        val done = ups.filter { it.kind in kinds && it.finishAt <= now }.sortedByDescending { it.finishAt }
        if (done.isEmpty()) return
        box.addView(Ui.heading(act, tr("Baru selesai", "Just finished"), tr("ketuk ✓ bila sudah diambil di game", "tap ✓ once collected in the game")))
        val panel = Ui.panel(act, padH = 14, padV = 2)
        done.take(4).forEachIndexed { i, u ->
            val row = Ui.hbox(act).apply {
                setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8))
                isClickable = true
                setOnClickListener { act.timerOptions(u) }
            }
            row.addView(icon(act, u, gd, 38, null))
            val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 10), 0, Ui.dp(act, 4), 0) }
            val t = Ui.hbox(act)
            labels[u.id]?.let { t.addView(Sessions.badge(act, it, Sessions.isLabKind(u.kind)), wrapEnd(act, 6)) }
            t.addView(Ui.text(act, Widgets.shortTitle(u), Ui.BODY_M).apply { textSize = 14f; setTextColor(Ui.TEXT); maxLines = 1 }, Ui.weight1())
            col.addView(t)
            col.addView(TextView(act).apply { text = tr("selesai ${short(u.finishAt, now, act.zoneId())}", "done ${short(u.finishAt, now, act.zoneId())}"); textSize = 12f; setTextColor(Ui.FREE_TEXT) })
            row.addView(col, Ui.weight1())
            row.addView(Ui.icon(act, R.drawable.ic_check, 22, Ui.FREE, tr("Tandai selesai", "Mark done")) { act.markUpgrade(u, true) })
            panel.addView(row)
            if (i < minOf(done.size, 4) - 1) panel.addView(line(act))
        }
        box.addView(gap(act, panel))
    }

    // ---------------------------------------------------------------- baris pekerja

    fun workRow(act: MainActivity, u: Upgrade, gd: GameData, label: String?, now: Long): View {
        val lab = Sessions.isLabKind(u.kind)
        val color = if (u.id.contains("|gear|")) Ui.BUSY else if (lab) Ui.LAB else Ui.BUSY
        val row = Ui.hbox(act).apply {
            setPadding(0, Ui.dp(act, 9), 0, Ui.dp(act, 9))
            isClickable = true
            setOnClickListener { act.timerOptions(u) }
        }
        ItemMenu.attach(act, row, u.tag, Repo.itemIdOf(u.id))
        row.addView(icon(act, u, gd, 38, null))
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 10), 0, 0, 0) }
        val t = Ui.hbox(act)
        if (label != null) t.addView(Sessions.badge(act, label, lab), wrapEnd(act, 6))
        t.addView(Ui.text(act, Widgets.shortTitle(u), Ui.BODY_M).apply {
            textSize = 14f
            setTextColor(Ui.TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, Ui.weight1())
        val cd = Ui.monoText(act, act.countdown(u.finishAt - now), 14f)
        act.registerCountdown(cd, u.finishAt, clockStyle = false)
        t.addView(cd)
        col.addView(t)
        col.addView(Ui.thinBar(act, progress(u, gd, now) ?: 0.0, color))
        row.addView(col, Ui.weight1())
        return row
    }

    fun icon(act: MainActivity, u: Upgrade, gd: GameData, size: Int, badge: TextView?): View {
        val gear = u.id.contains("|gear|")
        val item = Repo.itemIdOf(u.id)?.let { gd.item(it) }
        val level = if (gear) u.targetLevel else (u.targetLevel - 1).coerceAtLeast(1)
        return Ui.gameIcon(act, Icons.forItem(item, level), u.name, size, badge)
    }

    fun line(act: MainActivity): View = View(act).apply {
        setBackgroundColor(Ui.LINE)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 1))
    }

    private fun wrapEnd(act: MainActivity, dp: Int) =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(act, dp) }

    /** Bagian upgrade yang sudah berjalan (0..1), atau null bila durasi aslinya tidak diketahui. */
    fun progress(u: Upgrade, gd: GameData, now: Long): Double? {
        val itemId = Repo.itemIdOf(u.id)
        val gear = u.id.contains("|gear|")
        val rem = (u.finishAt - now) / 1000.0
        val item = itemId?.let { gd.item(it) }
        val total = if (gear) Sixth.rules.firstOrNull { it.homeId == itemId }?.durationSec ?: 0L
        else item?.lv(u.targetLevel)?.time?.takeIf { it > 0 }
            ?: item?.buildTimeFor(rem.toLong()) // kamp pasukan BB: lama menurut bangunan ke-N
            ?: 0L
        if (total <= 0) return null
        return (1 - rem / total).coerceIn(0.02, 0.99)
    }

    /** "Sab 11:34" (hari ini: "11:34"). */
    fun short(t: Long, now: Long, zone: ZoneId): String {
        val d1 = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val d0 = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = Fmt.time(t, zone)
        return if (d1 == d0) time else java.time.format.DateTimeFormatter.ofPattern("EEE", Fmt.locale).format(d1) + " " + time
    }

    // ---------------------------------------------------------------- Clock Tower & builder ke-6 (Builder Base)

    private class ClockInfo(
        val ct: Inst, val level: Int, val minutes: Int, val upgrade: Upgrade?, val upgrading: Boolean,
        val active: Boost?, val lastFree: Long?, val readyAt: Long?,
    )

    private fun clockInfo(act: MainActivity, tag: String, v: Village, ups: List<Upgrade>, now: Long): ClockInfo? {
        val ct = v.insts.firstOrNull { it.item.id == GameData.CLOCK_TOWER } ?: return null
        val upgrade = ups.firstOrNull { !it.id.contains("|gear|") && Repo.itemIdOf(it.id) == GameData.CLOCK_TOWER }
        val upgrading = upgrade != null && upgrade.finishAt > now
        val level = if (ct.upgrading && (upgrade == null || upgrade.finishAt <= now)) ct.level + 1 else ct.level
        val boosts = Store.boosts(act, tag)
        val active = Boosts.merged(boosts).firstOrNull { it.kind.group == "clock" && it.start <= now && it.end > now }
        val lastFree = Store.clockUsed(act, tag).takeIf { it > 0 }
            ?: boosts.filter { it.kind == BoostKind.CLOCK_TOWER }.maxOfOrNull { it.end }
        val readyAt = lastFree?.let { it + ClockTower.COOLDOWN }?.takeIf { it > now }
        return ClockInfo(ct, level, ClockTower.boostMinutes(level), upgrade, upgrading, active, lastFree, readyAt)
    }

    private fun clockTower(act: MainActivity, box: LinearLayout, tag: String, b: PlanBundle?, ups: List<Upgrade>, now: Long, zone: ZoneId) {
        val v = b?.village ?: return
        val ci = clockInfo(act, tag, v, ups, now) ?: return
        val ct = ci.ct
        val upgrade = ci.upgrade
        val upgrading = ci.upgrading
        val level = ci.level
        val minutes = ci.minutes
        val active = ci.active
        val readyAt = ci.readyAt
        val potions = Store.items(act, tag)[MagicItem.CLOCK_TOWER_POTION] ?: 0

        val card = Ui.panel(act, padH = 14, padV = 12)
        val top = Ui.hbox(act)
        top.addView(Ui.gameIcon(act, Icons.forItem(ct.item, level), "Clock Tower", 44))
        val tc = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
        val (state, color) = when {
            active != null -> tr("Clock Tower · boost aktif", "Clock Tower · boost active") to Ui.BUSY
            upgrading -> tr("Clock Tower · sedang di-upgrade", "Clock Tower · upgrading") to Ui.TEXT
            readyAt != null -> "Clock Tower · cooldown" to Ui.TEXT
            else -> tr("Clock Tower · boost siap", "Clock Tower · boost ready") to Ui.FREE_TEXT
        }
        tc.addView(Ui.text(act, state, Ui.TITLE_S).apply { textSize = 15f; setTypeface(typeface, Typeface.BOLD); setTextColor(color) })
        val sub = when {
            active != null -> tr("sampai ${Fmt.time(active.end, zone)} · timer Builder Base sudah dihitung 10×", "until ${Fmt.time(active.end, zone)} · Builder Base timers already count 10×")
            upgrading -> tr("boost bisa dipakai lagi ${short(upgrade!!.finishAt, now, zone)}", "boost usable again ${short(upgrade!!.finishAt, now, zone)}")
            readyAt != null -> tr("boost gratis berikutnya ±${short(readyAt, now, zone)}", "next free boost ±${short(readyAt, now, zone)}")
            else -> tr("10× selama $minutes menit · Master Builder & Star Lab", "10× for $minutes minutes · Master Builder & Star Lab")
        }
        tc.addView(Ui.muted(act, sub).apply { textSize = 13f })
        top.addView(tc, Ui.weight1())
        top.addView(Help.button(act, Help.Topic.CLOCK))
        card.addView(top)
        val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
        if (!upgrading && active == null && readyAt == null) {
            r.addView(Ui.tonal(act, tr("Pakai boost", "Use boost")) { ItemsUi.useClockTower(act, tag, level) }, Ui.weight1())
        }
        val potionLabel = if (potions > 0) "Potion · $potions" else "Clock Tower Potion"
        r.addView(Ui.secondary(act, potionLabel) { ItemsUi.use(act, tag, MagicItem.CLOCK_TOWER_POTION) },
            if (r.childCount > 0) LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(act, 8) }
            else Ui.weight1())
        card.addView(r)
        box.addView(gap(act, card))
    }

    // ---------------------------------------------------------------- helper

    /** Super troop yang sedang aktif menurut API (API tidak memberi waktu habisnya). */
    private fun superTroops(act: MainActivity, box: LinearLayout, tag: String, zone: ZoneId) {
        val st = Store.apiStats(act, tag) ?: return
        val arr = st.optJSONArray("supers") ?: return
        val names = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        if (names.isEmpty()) return
        box.addView(notice(act, tr("Super troop aktif: ", "Active super troops: ") + names.joinToString(", "), tr("dari API · ", "from API · ") + Fmt.at(st.optLong("at"), zone), Ui.LAB))
    }

    private fun helpers(act: MainActivity, box: LinearLayout, tag: String, ups: List<Upgrade>, b: PlanBundle?, now: Long, zone: ZoneId) {
        val gd = Repo.gameData(act)
        val ids = listOf(
            GameData.APPRENTICE to "Builder's Apprentice",
            GameData.LAB_ASSISTANT to "Lab Assistant",
            GameData.ALCHEMIST to "Alchemist",
        )
        val rows = ArrayList<View>()
        for ((id, name) in ids) {
            val level = b?.village?.helper(id)?.level
            val timer = ups.firstOrNull { it.id == "$tag|helpers|$id" }
            if (level == null && timer == null) continue
            val cooling = timer != null && timer.finishAt > now
            val line = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8)) }
            if (timer != null) {
                line.isClickable = true
                line.setOnClickListener { act.timerOptions(timer) }
            }
            line.addView(Ui.gameIcon(act, Icons.forHelper(gd, id), name, 38))
            val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 10), 0, Ui.dp(act, 8), 0) }
            col.addView(Ui.text(act, name + (level?.let { " · Lv $it" } ?: ""), Ui.BODY_M).apply { textSize = 14f; setTextColor(Ui.TEXT) })
            val state = when {
                cooling -> tr("dipakai · siap lagi ${short(timer!!.finishAt, now, zone)}", "used · ready again ${short(timer!!.finishAt, now, zone)}")
                id == GameData.ALCHEMIST -> tr("siap menukar sumber daya", "ready to convert resources")
                else -> tr("siap dipakai — tugaskan di game", "ready — assign it in the game")
            }
            col.addView(Ui.muted(act, state).apply { textSize = 12f; setTextColor(if (cooling) Ui.TEXT2 else Ui.FREE_TEXT) })
            line.addView(col, Ui.weight1())
            if (cooling) {
                val cd = Ui.monoText(act, act.countdown(timer!!.finishAt - now), 14f)
                act.registerCountdown(cd, timer.finishAt, clockStyle = false)
                line.addView(cd)
            }
            rows += line
        }
        if (rows.isEmpty()) return
        box.addView(Ui.heading(act, "Helper", help = Help.Topic.HELPERS))
        val panel = Ui.panel(act, padH = 14, padV = 2)
        rows.forEachIndexed { i, v ->
            if (i > 0) panel.addView(line(act))
            panel.addView(v)
        }
        box.addView(gap(act, panel))
    }

    // ---------------------------------------------------------------- potion & item

    private fun items(act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, b: PlanBundle?, now: Long, zone: ZoneId) {
        val tips = b?.tips?.filter { it.item.forBB == bb && it.item != MagicItem.CLOCK_TOWER_POTION }.orEmpty()
        val head = Ui.heading(act, "Potion & item", null, Help.Topic.ITEMS) as LinearLayout
        head.addView(TextView(act).apply {
            text = tr("Kelola", "Manage")
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.FREE_TEXT)
            setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 8))
            isClickable = true
            setOnClickListener { act.openItems(tag) }
        })
        box.addView(head)
        val panel = Ui.panel(act, padH = 14, padV = 2)
        var n = 0
        fun add(v: View) {
            if (n++ > 0) panel.addView(line(act))
            panel.addView(v)
        }
        for (t in tips.take(3)) {
            val row = Ui.hbox(act).apply {
                setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8))
                isClickable = true
                setOnClickListener { ItemsUi.use(act, tag, t.item) }
            }
            row.addView(Icons.view(act, t.item.icon, 36, t.item.emoji, Ui.LAB, round = false))
            val c = Ui.vbox(act).apply { setPadding(Ui.dp(act, 10), 0, Ui.dp(act, 8), 0) }
            c.addView(Ui.text(act, t.item.label + if (t.owned > 0) tr(" · punya ${t.owned}", " · owned ${t.owned}") else "", Ui.BODY_M).apply {
                textSize = 14f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT)
            })
            c.addView(Ui.muted(act, t.headline).apply { textSize = 12f })
            row.addView(c, Ui.weight1())
            row.addView(TextView(act).apply {
                text = tr("Pakai", "Use")
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.FREE_TEXT)
                background = Ui.rounded(Ui.SELECTED_BG, Ui.dp(act, 10).toFloat())
                setPadding(Ui.dp(act, 12), Ui.dp(act, 8), Ui.dp(act, 12), Ui.dp(act, 8))
            })
            add(row)
        }
        if (n == 0) {
            add(Ui.muted(act, tr("Belum ada yang layak dipakai sekarang · ketuk Kelola untuk mencatat item", "Nothing worth using right now · tap Manage to record items")).apply {
                textSize = 13f
                setPadding(0, Ui.dp(act, 12), 0, Ui.dp(act, 12))
            })
        }
        box.addView(gap(act, panel))
    }
}
