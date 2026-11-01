package id.clashtimer

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.BBStrategy
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
import id.clashtimer.core.Planner
import id.clashtimer.core.Scored
import id.clashtimer.core.Sixth
import java.time.ZoneId

/** Tab "Beranda": timer berjalan + saran untuk pekerja yang bebas. */
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
            if (stale.isNotEmpty()) {
                box.addView(Ui.banner(act,
                    "⚠️ Data desa ketinggalan: ${stale.take(3).joinToString("; ")}. Copy export di game lalu ketuk di sini.",
                    Ui.AMBER).apply { setOnClickListener { act.importFromClipboard() } })
            }
        }

        if (bb && acc.bh == 0 && ups.none { it.kind.builderBase }) {
            box.addView(Ui.text(act, "Belum ada data Builder Base di export desa ini.", Ui.BODY_M).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 24), Ui.dp(act, 4), 0)
            })
            return
        }

        EventsUi.homeBanners(act, box, bb, now)

        val builderKind = if (bb) Kind.BB_BUILDER else Kind.BUILDER
        val builderLane = if (bb) Lane.BB_BUILDER else Lane.BUILDER
        val running = ups.filter { it.kind == builderKind && it.finishAt > now }.sortedBy { it.finishAt }
        val finished = ups.filter { it.kind == builderKind && it.finishAt <= now }.sortedByDescending { it.finishAt }
        val total = b?.let { Planner.laneCount(it.village, builderLane, it.ps) }
            ?: (if (bb) maxOf(1, running.size) else acc.builders)
        // Goblin Builder (event, dibayar gem) tidak dihitung sebagai builder tetap.
        val regular = running.count { !it.extra }
        val extraN = running.size - regular
        val free = (total - regular).coerceAtLeast(0)
        val slots = b?.slots?.filter { it.lane == builderLane }.orEmpty()
        val nowSlot = slots.firstOrNull { it.freeAt <= now }
        val nextSlot = slots.firstOrNull { it.freeAt > now }
        val workers = if (bb) "Master Builder" else "Builder"

        // --- judul besar
        val (hc, hcol) = Ui.card(act, padV = 14)
        if (free > 0) {
            hcol.addView(Ui.muted(act, "$workers tersedia", Ui.BODY_M))
            hcol.addView(Ui.text(act, "$free bebas", Ui.TITLE_L).apply {
                textSize = 30f
                setTextColor(Ui.GREEN)
            })
            suggestionStrip(act, hcol, tag, nowSlot, nowSlot?.ranked?.firstOrNull(), canStart = true, b, hasExport)
        } else if (running.isNotEmpty()) {
            val first = running.firstOrNull { !it.extra } ?: running.first()
            hcol.addView(Ui.muted(act, "$workers berikutnya bebas dalam", Ui.BODY_M))
            val big = Ui.text(act, act.clock(first.finishAt - now), Ui.TITLE_L).apply {
                textSize = 32f
                setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            }
            act.registerCountdown(big, first.finishAt, clockStyle = true)
            hcol.addView(big)
            hcol.addView(Ui.muted(act, "${first.title()} · ${Fmt.at(first.finishAt, zone)}", Ui.BODY_M))
            gameLine(act, first, now)?.let { hcol.addView(it) }
            suggestionStrip(act, hcol, tag, nextSlot, nextSlot?.ranked?.firstOrNull(), canStart = false, b, hasExport)
        } else {
            hcol.addView(Ui.text(act, "Belum ada upgrade yang tercatat.", Ui.BODY_M))
        }
        box.addView(hc)

        // --- opsi desa ini (strategi, Gold Pass, CWL, collector)
        VillageOptions.card(act, box, tag, bb, Store.planSettings(act, tag))

        // --- builder
        box.addView(Ui.sectionRow(act, workers, "$regular dari $total sibuk" + if (extraN > 0) " + $extraN Goblin" else ""))
        val (bc, bcol) = Ui.card(act, padH = 0, padV = 0)
        var rows = 0
        fun add(v: View) {
            if (rows++ > 0) bcol.addView(Ui.divider(act))
            bcol.addView(v)
        }
        for (u in finished.take(3)) add(timerRow(act, u, now, zone, gd, b, Ui.GREEN))
        for (u in running) add(timerRow(act, u, now, zone, gd, b, Ui.GREEN))
        if (free > 1 && nowSlot != null) {
            for (k in 1 until free) {
                val pick = nowSlot.ranked.getOrNull(k) ?: break
                add(freeRow(act, tag, nowSlot, pick, "$workers bebas"))
            }
        }
        if (rows == 0) bcol.addView(Ui.muted(act, "Belum ada upgrade.", Ui.BODY_M).apply { setPadding(Ui.dp(act, 14), Ui.dp(act, 12), 0, Ui.dp(act, 12)) })
        box.addView(bc)

        // --- lab, pet, star lab
        val labLanes = if (bb) listOf(Lane.STAR_LAB) else listOf(Lane.LAB, Lane.PET)
        for (lane in labLanes) {
            val kind = Kind.of(lane)
            val r = ups.filter { it.kind == kind && it.finishAt > now }.sortedBy { it.finishAt }
            val exists = b?.let { Planner.laneCount(it.village, lane, it.ps) > 0 } ?: r.isNotEmpty()
            if (!exists && r.isEmpty()) continue
            val rx = r.count { it.extra }
            box.addView(Ui.sectionRow(act, lane.label, if (r.isEmpty()) "kosong" else "${r.size - rx} dari 1 sibuk" + if (rx > 0) " + $rx Goblin" else ""))
            val (lc, lcol) = Ui.card(act, padH = 0, padV = 0)
            if (r.isNotEmpty()) {
                r.forEachIndexed { i, u ->
                    if (i > 0) lcol.addView(Ui.divider(act))
                    lcol.addView(timerRow(act, u, now, zone, gd, b, Ui.VIOLET))
                }
            } else {
                val slot = b?.slots?.firstOrNull { it.lane == lane && it.freeAt <= now }
                val pick = slot?.ranked?.firstOrNull()
                if (slot != null && pick != null) lcol.addView(freeRow(act, tag, slot, pick, "${lane.label} kosong"))
                else lcol.addView(Ui.muted(act, if (b == null) "Menghitung saran…" else "Tidak ada riset yang tersedia.", Ui.BODY_M).apply {
                    setPadding(Ui.dp(act, 14), Ui.dp(act, 12), 0, Ui.dp(act, 12))
                })
            }
            box.addView(lc)
        }

        needsMini(act, box, if (bb) b?.simBB else b?.simHome)

        if (bb) {
            clockTower(act, box, tag, b, ups, now, zone)
            sixthMini(act, box, b, now, zone)
        } else {
            helpers(act, box, tag, ups, b, now, zone)
            superTroops(act, box, tag, zone)
        }
        items(act, box, tag, bb, b, now, zone)

        // --- CWL
        if (!bb && b != null && b.ps.cwl.enabled) {
            val w = b.ps.cwl.next(now)
            if (w.first - now < 3 * DAY) {
                val txt = if (now >= w.first) "⚔️ CWL berlangsung sampai ${Fmt.at(w.last + 1, zone)} · hero yang bentrok tidak disarankan"
                else "⚔️ CWL mulai ${Fmt.at(w.first, zone)} · hero yang bentrok tidak disarankan"
                box.addView(Ui.banner(act, txt, Ui.GOLD).also { topMargin(it, act, 12) })
            }
            val fin = ups.associate { it.id to it.finishAt }
            for (i in b.village.insts) {
                if (!i.upgrading || i.item.cat != Cat.HERO) continue
                val f = fin[i.uid] ?: continue
                if (f > now && b.ps.cwl.overlap(now, f) != null) {
                    box.addView(Ui.banner(act, "⚠️ ${i.item.name} masih upgrade saat CWL (selesai ${Fmt.at(f, zone)}) — pertimbangkan Book of Heroes / potion", Ui.AMBER))
                }
            }
        }

        // --- desa lain
        val others = Store.accounts(act).filter { it.tag != tag }
        for (o in others) {
            val f = act.freeBuilders(o.tag, false)
            if (f > 0) {
                box.addView(Ui.banner(act, "🏰 ${Store.villageName(act, o.tag)}: $f builder bebas — ketuk untuk membuka", Ui.GREEN).apply {
                    setOnClickListener { act.selectVillage(o.tag) }
                }.also { topMargin(it, act, 8) })
            }
        }

        // --- tombol bawah
        val row = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 12), 0, 0) }
        row.addView(Ui.outlined(act, "⚡ Potion & item") { act.openItems(tag) }.apply { isAllCaps = false }, Ui.weight1())
        row.addView(Ui.outlined(act, "📋 Update desa") { act.importFromClipboard() }.apply { isAllCaps = false },
            Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
        box.addView(row)
        box.addView(Ui.muted(act, "Ketuk sebuah upgrade untuk menandai selesai atau batal. Setelah memulai upgrade di game, tekan \"Sudah dimulai\" pada saran — atau Update desa.").apply {
            setPadding(Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 4), 0)
            gravity = Gravity.CENTER
        })
    }

    private fun topMargin(v: View, act: MainActivity, dp: Int) {
        (v.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin = Ui.dp(act, dp)
    }

    private fun wrap(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    // ---------------------------------------------------------------- saran

    /** "12j · 1,2 jt Gold · selesai Sab 15:08". */
    fun pickLine(p: Scored, zone: ZoneId): String {
        val c = p.c
        val cost = if (p.cost > 0) "${Fmt.money(p.cost)} ${Fmt.resource(c.item.resource)}" else "gratis"
        val timing = if (p.idle > 30 * 60_000L) "💤 nganggur ${Fmt.dur(p.idle)}" else "selesai ${Fmt.at(p.finish, zone)}"
        return "${Fmt.durSec(p.duration)} · $cost · $timing"
    }

    /** Pill kecil di bawah saran: langkah builder ke-6, event. */
    private fun pickPills(act: MainActivity, p: Scored): View? {
        val row = Ui.hbox(act)
        if (p.tier > 0) row.addView(Ui.pill(act, "🏗️ builder ke-6", Ui.GOLD), wrap().apply { marginEnd = Ui.dp(act, 6) })
        p.event?.let { row.addView(Ui.pill(act, "🎉 ${it.name} −${it.timePct}%", Ui.VIOLET), wrap()) }
        return if (row.childCount > 0) row else null
    }

    private fun suggestionStrip(
        act: MainActivity, col: LinearLayout, tag: String, slot: LaneSlot?, pick: Scored?,
        canStart: Boolean, b: PlanBundle?, hasExport: Boolean,
    ) {
        col.addView(Ui.divider(act).also { topMargin(it, act, 12) })
        val wrap = Ui.vbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
        when {
            !hasExport -> wrap.addView(Ui.muted(act, "💡 Import ulang desa ini sekali supaya saran upgrade muncul.", Ui.BODY_M))
            b == null -> wrap.addView(Ui.muted(act, "💡 Menghitung saran…", Ui.BODY_M))
            slot == null || pick == null -> wrap.addView(Ui.muted(act, "💡 Tidak ada upgrade yang tersedia untuk saat ini.", Ui.BODY_M))
            else -> {
                val lead = if (canStart) "💡 Saran" else "💡 Saran saat bebas"
                wrap.addView(Ui.muted(act, lead))
                val line = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 4), 0, 0) }
                line.addView(Icons.view(act, Icons.forItem(pick.c.item, pick.c.fromLevel), 40, Icons.initials(pick.c.item.name)))
                val tc = Ui.vbox(act).apply { setPadding(Ui.dp(act, 10), 0, 0, 0) }
                tc.addView(Ui.text(act, pick.c.title, Ui.TITLE_S))
                tc.addView(Ui.muted(act, pickLine(pick, act.zoneId())))
                pickPills(act, pick)?.let { tc.addView(it, Ui.matchWrap().apply { topMargin = Ui.dp(act, 3) }) }
                line.addView(tc, Ui.weight1())
                wrap.addView(line)
                pick.reason?.let { wrap.addView(Ui.muted(act, it)) }
                if (slot.cwlSkipped.isNotEmpty()) {
                    wrap.addView(Ui.muted(act, "⚔️ Ditunda karena CWL: " + slot.cwlSkipped.joinToString(", ") { it.item.name }))
                }
                eventTip(act, b, pick)?.let { wrap.addView(it) }
                sixthNote(act, b, slot, pick)?.let { wrap.addView(it) }
                val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 6), 0, 0) }
                if (canStart) {
                    r.addView(Ui.filled(act, "Sudah dimulai") { act.startPick(tag, pick) }.apply { isAllCaps = false }, Ui.weight1())
                    r.addView(Ui.outlined(act, "Lihat opsi") { Options.show(act, tag, slot, true) }.apply { isAllCaps = false },
                        Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
                } else {
                    r.addView(Ui.outlined(act, "Lihat opsi") { Options.show(act, tag, slot, false) }.apply { isAllCaps = false }, Ui.weight1())
                }
                wrap.addView(r)
            }
        }
        col.addView(wrap)
    }

    /** Event yang mulai dalam 24 jam: tunjukkan hematnya bila upgrade ini ditunda sampai event. */
    private fun eventTip(act: MainActivity, b: PlanBundle, pick: Scored): View? {
        if (pick.event != null || pick.c.gear) return null
        val start = pick.start
        val ev = b.ps.events.filter { it.enabled && it.covers(pick.c.lane) && it.start > start && it.start - start < DAY }
            .minByOrNull { it.start } ?: return null
        val dur = b.ps.durationAt(pick.c, ev.start)
        val cost = b.ps.costAt(pick.c, ev.start)
        if (dur >= pick.duration) return null
        val zone = act.zoneId()
        return Ui.text(act,
            "🎉 ${ev.name} mulai ${short(ev.start, start, zone)} — bila dimulai saat event: ${Fmt.durSec(dur)}" +
                (if (cost < pick.cost) " · ${Fmt.money(cost)} ${Fmt.resource(pick.c.item.resource)}" else "") +
                ". Isi builder dengan upgrade singkat dulu.", Ui.BODY_S).apply {
            setTextColor(Ui.VIOLET)
            setPadding(0, Ui.dp(act, 4), 0, 0)
        }
    }

    /** Penjelasan bila strategi builder ke-6 aktif tetapi saran teratas bukan langkahnya. */
    private fun sixthNote(act: MainActivity, b: PlanBundle, slot: LaneSlot, pick: Scored): View? {
        if (!slot.lane.bb || b.ps.bbStrategy != BBStrategy.SIXTH || Sixth.done(b.village) || pick.tier > 0) return null
        return Ui.muted(act, "Belum ada langkah builder ke-6 untuk ${slot.lane.label} saat ini — ini saran biasa yang tidak menghambat.")
    }

    private fun freeRow(act: MainActivity, tag: String, slot: LaneSlot, pick: Scored, head: String): View {
        val line = Ui.hbox(act).apply { setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 8)); gravity = Gravity.TOP }
        ItemMenu.attach(act, line, tag, pick.c.item.id)
        line.addView(Icons.view(act, Icons.forItem(pick.c.item, pick.c.fromLevel), 44, Icons.initials(pick.c.item.name), Ui.GREEN))
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
        val top = Ui.hbox(act)
        top.addView(Ui.text(act, head, Ui.TITLE_S).apply { setTextColor(Ui.GREEN) }, Ui.weight1())
        top.addView(Ui.pill(act, "saran", Ui.GOLD))
        col.addView(top)
        col.addView(Ui.text(act, pick.c.title, Ui.BODY_M))
        col.addView(Ui.muted(act, pickLine(pick, act.zoneId())))
        pickPills(act, pick)?.let { col.addView(it, Ui.matchWrap().apply { topMargin = Ui.dp(act, 3) }) }
        val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 4), 0, 0) }
        r.addView(Ui.outlined(act, "Sudah dimulai") { act.startPick(tag, pick) }.apply { isAllCaps = false }, Ui.weight1())
        r.addView(Ui.flat(act, "Lihat opsi") { Options.show(act, tag, slot, true) }.apply { isAllCaps = false },
            Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
        col.addView(r)
        line.addView(col, Ui.weight1())
        return line
    }

    // ---------------------------------------------------------------- baris timer

    /** Cincin progres dengan ikon di tengah. */
    private fun badge(act: MainActivity, ring: RingView, path: String?, name: String, sizeDp: Int): View {
        val frame = FrameLayout(act)
        val px = Ui.dp(act, sizeDp)
        frame.addView(ring, FrameLayout.LayoutParams(px, px))
        if (path != null && Icons.enabled(act)) {
            ring.showText = false
            val inner = sizeDp - 14
            val icon = Icons.view(act, path, inner, Icons.initials(name), round = false)
            frame.addView(icon, FrameLayout.LayoutParams(Ui.dp(act, inner), Ui.dp(act, inner), Gravity.CENTER))
        }
        return frame
    }

    private fun timerRow(
        act: MainActivity, u: Upgrade, now: Long, zone: ZoneId, gd: GameData, b: PlanBundle?, color: Int,
    ): View {
        val done = u.finishAt <= now
        val gear = u.id.contains("|gear|")
        val itemId = Repo.itemIdOf(u.id)
        val item = itemId?.let { gd.item(it) }
        val line = Ui.hbox(act).apply {
            setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10))
            isClickable = true
            setOnClickListener { act.timerOptions(u) }
        }
        ItemMenu.attach(act, line, u.tag, itemId)
        val ring = RingView(act, 46)
        if (done) {
            ring.pct = 100
            ring.label = "✓"
            ring.setColor(Ui.GREEN)
        } else {
            val total = if (gear) Sixth.rules.firstOrNull { it.homeId == itemId }?.durationSec ?: 0L
            else item?.lv(u.targetLevel)?.time ?: 0L
            if (total > 0) {
                val rem = (u.finishAt - now) / 1000.0
                ring.pct = (100 * (1 - rem / total)).toInt().coerceIn(1, 99)
            } else ring.label = "–"
            ring.setColor(if (gear) Ui.GOLD else color)
        }
        val iconLevel = if (gear) u.targetLevel else (u.targetLevel - 1).coerceAtLeast(1)
        line.addView(badge(act, ring, Icons.forItem(item, iconLevel), u.name, 46))

        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 8), 0) }
        col.addView(Ui.text(act, if (gear) u.title() else u.name, Ui.TITLE_S))
        val lv = when {
            gear -> "Home Village · Lv ${u.targetLevel}"
            u.targetLevel <= 1 -> "Bangun baru"
            item != null && item.lv(u.targetLevel - 1) == null && item.cat == Cat.BB_HERO -> "Buka · Lv ${u.targetLevel}"
            else -> "Lv ${u.targetLevel - 1} → ${u.targetLevel}"
        }
        col.addView(Ui.muted(act, if (done) "$lv · selesai, ketuk untuk tandai" else lv))
        val pills = Ui.hbox(act)
        fun pill(text: String, c: Int) {
            pills.addView(Ui.pill(act, text, c), wrap().apply { if (pills.childCount > 0) marginStart = Ui.dp(act, 6) })
        }
        if (u.helped && !done) {
            val helperId = if (u.kind == Kind.LAB) GameData.LAB_ASSISTANT else GameData.APPRENTICE
            val level = b?.village?.helper(helperId)?.level
            val name = if (u.kind == Kind.LAB) "Lab Assistant" else "Apprentice"
            pill(if (level != null) "$name +${level}j/hari" else "dibantu $name", if (u.kind == Kind.LAB) Ui.VIOLET else Ui.GREEN)
        }
        if (gear && !done) pill("Master Builder", Ui.GOLD)
        if (u.extra && !done) pill(if (u.kind == Kind.BUILDER) "👺 Goblin Builder" else "👺 Goblin Researcher", Ui.GREY)
        if (u.note.contains("⚡") && !done) pill("⚡ dipercepat", Ui.GOLD)
        if (u.note.contains("🎉") && !done) pill("🎉 event", Ui.VIOLET)
        if (!done && u.note.contains("dimulai di aplikasi", ignoreCase = true)) pill("dimulai di aplikasi", Ui.GREY)
        if (pills.childCount > 0) col.addView(pills, Ui.matchWrap().apply { topMargin = Ui.dp(act, 3) })
        line.addView(col, Ui.weight1())

        val right = Ui.vbox(act).apply { gravity = Gravity.END }
        val cd = Ui.text(act, if (done) "Selesai" else act.countdown(u.finishAt - now), Ui.TITLE_S).apply {
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            setTextColor(if (done) Ui.GREEN else Ui.onSurface(act))
            gravity = Gravity.END
        }
        if (!done) act.registerCountdown(cd, u.finishAt, clockStyle = false)
        right.addView(cd)
        right.addView(Ui.muted(act, short(u.finishAt, now, zone)).apply { gravity = Gravity.END })
        if (!done) gameLine(act, u, now)?.let { right.addView(it.apply { gravity = Gravity.END }) }
        line.addView(right)
        return line
    }

    /**
     * Helper berulang: waktu di aplikasi sudah memperhitungkan sesi helper berikutnya (otomatis besok),
     * sedangkan timer di game belum — tampilkan juga sisa waktu seperti yang terlihat di game.
     */
    private fun gameLine(act: MainActivity, u: Upgrade, now: Long): TextView? {
        val game = u.gameFinishAt(now)
        if (game - u.finishAt < 60_000L || game <= now) return null
        val prefix = "di game "
        val tv = Ui.muted(act, prefix + act.countdown(game - now))
        act.registerCountdown(tv, game, clockStyle = false, prefix = prefix)
        return tv
    }

    /** "Sab 11:34" (hari ini: "11:34"). */
    fun short(t: Long, now: Long, zone: ZoneId): String {
        val d1 = java.time.Instant.ofEpochMilli(t).atZone(zone).toLocalDate()
        val d0 = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = Fmt.time(t, zone)
        return if (d1 == d0) time else java.time.format.DateTimeFormatter.ofPattern("EEE", Fmt.locale).format(d1) + " " + time
    }

    // ---------------------------------------------------------------- helper

    /** Super troop yang sedang aktif menurut API (API tidak memberi waktu habisnya). */
    private fun superTroops(act: MainActivity, box: LinearLayout, tag: String, zone: ZoneId) {
        val st = Store.apiStats(act, tag) ?: return
        val arr = st.optJSONArray("supers") ?: return
        val names = (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
        if (names.isEmpty()) return
        val (card, col) = Ui.card(act, tinted = true)
        col.addView(Ui.text(act, "💪 Super troop aktif: " + names.joinToString(", "), Ui.BODY_M))
        col.addView(Ui.muted(act, "Dari API · ${Fmt.at(st.optLong("at"), zone)} (boost aktif 3 hari; waktu habisnya tidak tersedia di API)"))
        box.addView(card, Ui.matchWrap().apply { topMargin = Ui.dp(act, 8) })
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
            val line = Ui.hbox(act).apply { setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10)) }
            if (timer != null) {
                line.isClickable = true
                line.setOnClickListener { act.timerOptions(timer) }
            }
            line.addView(Icons.view(act, Icons.forHelper(gd, id), 40, Icons.initials(name), if (cooling) Ui.GREY else Ui.GREEN))
            val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, Ui.dp(act, 8), 0) }
            col.addView(Ui.text(act, name + (level?.let { " · Lv $it" } ?: ""), Ui.TITLE_S))
            val state = when {
                cooling -> timer!!.title().substringAfter("$name ").let { if (it == timer.title()) "cooldown" else it }
                id == GameData.ALCHEMIST -> "Siap menukar sumber daya"
                else -> "Siap dipakai — tugaskan di game"
            }
            col.addView(Ui.text(act, state, Ui.BODY_S).apply { setTextColor(if (cooling) Ui.secondary(act) else Ui.GREEN) })
            line.addView(col, Ui.weight1())
            if (cooling) {
                val right = Ui.vbox(act).apply { gravity = Gravity.END }
                val cd = Ui.text(act, act.countdown(timer!!.finishAt - now), Ui.TITLE_S).apply {
                    setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
                    gravity = Gravity.END
                }
                act.registerCountdown(cd, timer.finishAt, clockStyle = false)
                right.addView(cd)
                right.addView(Ui.muted(act, short(timer.finishAt, now, zone)).apply { gravity = Gravity.END })
                line.addView(right)
            }
            rows += line
        }
        if (rows.isEmpty()) return
        box.addView(Ui.sectionRow(act, "Helper"))
        val (card, col) = Ui.card(act, padH = 0, padV = 0)
        rows.forEachIndexed { i, v ->
            if (i > 0) col.addView(Ui.divider(act))
            col.addView(v)
        }
        box.addView(card)
    }

    // ---------------------------------------------------------------- Clock Tower (Builder Base)

    private fun clockTower(act: MainActivity, box: LinearLayout, tag: String, b: PlanBundle?, ups: List<Upgrade>, now: Long, zone: ZoneId) {
        val v = b?.village ?: return
        val ct = v.insts.firstOrNull { it.item.id == GameData.CLOCK_TOWER } ?: return
        val upgrade = ups.firstOrNull { !it.id.contains("|gear|") && Repo.itemIdOf(it.id) == GameData.CLOCK_TOWER }
        val upgrading = upgrade != null && upgrade.finishAt > now
        val level = if (ct.upgrading && (upgrade == null || upgrade.finishAt <= now)) ct.level + 1 else ct.level
        val minutes = ClockTower.boostMinutes(level)
        val boosts = Store.boosts(act, tag)
        val active = Boosts.merged(boosts).firstOrNull { it.kind.group == "clock" && it.start <= now && it.end > now }
        val lastFree = Store.clockUsed(act, tag).takeIf { it > 0 }
            ?: boosts.filter { it.kind == BoostKind.CLOCK_TOWER }.maxOfOrNull { it.end }
        val readyAt = lastFree?.let { it + ClockTower.COOLDOWN }?.takeIf { it > now }
        val potions = Store.items(act, tag)[MagicItem.CLOCK_TOWER_POTION] ?: 0

        box.addView(Ui.sectionRow(act, "Clock Tower", "Lv$level · 10× selama $minutes menit"))
        val (card, col) = Ui.card(act, padV = 12)
        val top = Ui.hbox(act)
        top.addView(Icons.view(act, Icons.forItem(ct.item, level), 44, "⏱️", Ui.GOLD))
        val tc = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
        val (state, color) = when {
            active != null -> "⚡ Boost aktif sampai ${Fmt.time(active.end, zone)} — timer Builder Base sudah dihitung 10×" to Ui.GOLD
            upgrading -> "Sedang di-upgrade · boost bisa dipakai lagi ${short(upgrade!!.finishAt, now, zone)}" to Ui.secondary(act)
            readyAt != null -> "Cooldown · boost gratis berikutnya ±${short(readyAt, now, zone)}" to Ui.secondary(act)
            else -> "Boost gratis siap dipakai" to Ui.GREEN
        }
        tc.addView(Ui.text(act, state, Ui.BODY_M).apply { setTextColor(color) })
        tc.addView(Ui.muted(act, "Mempercepat Master Builder & Star Lab (bukan gear-up di Home Village)."))
        top.addView(tc, Ui.weight1())
        col.addView(top)
        val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 8), 0, 0) }
        if (!upgrading && active == null) {
            r.addView(Ui.filled(act, "Pakai boost") { ItemsUi.useClockTower(act, tag, level) }.apply { isAllCaps = false }, Ui.weight1())
        }
        val potionLabel = if (potions > 0) "Potion ($potions)" else "Clock Tower Potion"
        r.addView(Ui.outlined(act, potionLabel) { ItemsUi.use(act, tag, MagicItem.CLOCK_TOWER_POTION) }.apply { isAllCaps = false },
            Ui.weight1().apply { if (r.childCount > 0) marginStart = Ui.dp(act, 8) })
        col.addView(r)
        box.addView(card)
    }

    // ---------------------------------------------------------------- kebutuhan sumber daya (ringkas)

    private fun needsMini(act: MainActivity, box: LinearLayout, sim: id.clashtimer.core.SimResult?) {
        val n = sim?.needs?.firstOrNull() ?: return
        box.addView(Ui.sectionRow(act, "💰 Kebutuhan 24 jam", "ketuk untuk 3 & 7 hari"))
        val (card, col) = Ui.card(act, padV = 10)
        card.setOnClickListener { act.openTab(MainActivity.Tab.PLAN) }
        if (n.costs.isEmpty()) {
            col.addView(Ui.muted(act, "Tidak ada upgrade baru dalam 24 jam — pekerja masih sibuk.", Ui.BODY_M))
        } else {
            col.addView(Ui.text(act, Fmt.costs(n.costs), Ui.TITLE_S))
            n.top.entries.maxByOrNull { it.value.cost }?.let { (res, t) ->
                col.addView(Ui.muted(act, "termahal: ${t.title} · ${Fmt.money(t.cost)} ${Fmt.resource(res)}"))
            }
        }
        box.addView(card)
    }

    // ---------------------------------------------------------------- builder ke-6 (ringkas)

    private fun sixthMini(act: MainActivity, box: LinearLayout, b: PlanBundle?, now: Long, zone: ZoneId) {
        if (b == null || b.ps.bbStrategy != BBStrategy.SIXTH) return
        val six = b.simBB?.sixth ?: return
        val done = six.reqs.count { it.done }
        box.addView(Ui.sectionRow(act, "Menuju builder ke-6", "$done/${six.reqs.size} syarat"))
        val (card, col) = Ui.card(act, padV = 12)
        card.setOnClickListener { act.openTab(MainActivity.Tab.PLAN) }
        val eta = six.doneAt
        col.addView(Ui.text(act, if (eta != null) "B.O.B terbuka ±${Fmt.date(eta, zone)}" else "Belum bisa diperkirakan", Ui.TITLE_S))
        col.addView(Ui.bar(act, 100.0 * done / six.reqs.size.coerceAtLeast(1)))
        val next = six.reqs.firstOrNull { !it.done }
        col.addView(Ui.muted(act, (next?.let { "Berikutnya: ${it.label} · ${it.detail}" } ?: "Semua syarat selesai") + "\nKetuk untuk melihat rencana lengkap ›"))
        box.addView(card)
    }

    // ---------------------------------------------------------------- potion & item

    private fun items(act: MainActivity, box: LinearLayout, tag: String, bb: Boolean, b: PlanBundle?, now: Long, zone: ZoneId) {
        val tips = b?.tips?.filter { it.item.forBB == bb && it.item != MagicItem.CLOCK_TOWER_POTION }.orEmpty()
        val active = Boosts.merged(Store.boosts(act, tag)).filter { b2 ->
            b2.start <= now && b2.end > now && b2.kind.lanes.any { it.bb == bb } && b2.kind.group != "clock"
        }
        if (tips.isEmpty() && active.isEmpty()) return
        box.addView(Ui.sectionRow(act, "Potion & item", "ketuk untuk memakai"))
        val (card, col) = Ui.card(act, padH = 0, padV = 0)
        var n = 0
        for (a in active) {
            if (n++ > 0) col.addView(Ui.divider(act))
            val line = Ui.hbox(act).apply { setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10)) }
            val mi = MagicItem.entries.firstOrNull { it.boost == a.kind }
            line.addView(Icons.view(act, mi?.icon, 36, mi?.emoji ?: "⚡", Ui.GOLD))
            val c = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
            c.addView(Ui.text(act, "${a.kind.label} aktif", Ui.TITLE_S).apply { setTextColor(Ui.GOLD) })
            c.addView(Ui.muted(act, "${a.kind.speedLabel} sampai ${Fmt.time(a.end, zone)} · sudah dihitung ke timer"))
            line.addView(c, Ui.weight1())
            col.addView(line)
        }
        for (t in tips.take(5)) {
            if (n++ > 0) col.addView(Ui.divider(act))
            val line = Ui.hbox(act).apply {
                setPadding(Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 14), Ui.dp(act, 10))
                isClickable = true
                setOnClickListener { ItemsUi.use(act, tag, t.item) }
            }
            line.addView(Icons.view(act, t.item.icon, 36, t.item.emoji, Ui.VIOLET))
            val c = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
            c.addView(Ui.text(act, t.item.label + if (t.owned > 0) " · punya ${t.owned}" else "", Ui.TITLE_S))
            c.addView(Ui.text(act, t.headline, Ui.BODY_S))
            c.addView(Ui.muted(act, t.detail))
            line.addView(c, Ui.weight1())
            col.addView(line)
        }
        box.addView(card)
    }

    // ---------------------------------------------------------------- lihat opsi

    object Options {
        fun show(act: MainActivity, tag: String, slot: LaneSlot, canStart: Boolean) {
            val zone = act.zoneId()
            val list = slot.ranked.take(60)
            val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 4)) }
            var dialog: AlertDialog? = null
            if (canStart) col.addView(Ui.muted(act, "Ketuk yang Anda mulai di game — timer & notifikasi langsung dipasang.").apply {
                setPadding(Ui.dp(act, 4), 0, 0, Ui.dp(act, 6))
            })
            list.forEachIndexed { i, p ->
                if (i > 0) col.addView(Ui.divider(act))
                col.addView(optionRow(act, p, i == 0, zone) {
                    if (canStart) {
                        dialog?.dismiss()
                        act.startPick(tag, p)
                    }
                })
            }
            if (list.isEmpty()) col.addView(Ui.muted(act, "Tidak ada upgrade yang tersedia.", Ui.BODY_M))
            if (slot.cwlSkipped.isNotEmpty()) {
                col.addView(Ui.muted(act, "⚔️ Disembunyikan (CWL): " + slot.cwlSkipped.joinToString(", ") { it.title }).apply {
                    setPadding(Ui.dp(act, 4), Ui.dp(act, 8), 0, 0)
                })
            }
            val title = if (canStart) "Pilih yang Anda mulai" else "Opsi saat bebas ${Fmt.at(slot.start, zone)}"
            dialog = MaterialAlertDialogBuilder(act)
                .setTitle(title)
                .setView(ScrollView(act).apply { addView(col) })
                .setNegativeButton("Tutup", null)
                .show()
        }

        private fun optionRow(act: MainActivity, p: Scored, best: Boolean, zone: ZoneId, onClick: () -> Unit): View {
            val c = p.c
            val row = Ui.hbox(act).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4), Ui.dp(act, 10))
                isClickable = true
                setOnClickListener { onClick() }
            }
            row.addView(Icons.view(act, Icons.forItem(c.item, c.fromLevel), 40, Icons.initials(c.item.name)))
            val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
            col.addView(Ui.text(act, (if (best) "★ " else "") + c.title, Ui.TITLE_S))
            col.addView(Ui.muted(act, pickLine(p, zone)))
            val pills = Ui.hbox(act)
            if (p.tier > 0) pills.addView(Ui.pill(act, "🏗️ builder ke-6", Ui.GOLD), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginEnd = Ui.dp(act, 6) })
            p.event?.let { pills.addView(Ui.pill(act, "🎉 −${it.timePct}%", Ui.VIOLET)) }
            if (pills.childCount > 0) col.addView(pills, Ui.matchWrap().apply { topMargin = Ui.dp(act, 3) })
            row.addView(col, Ui.weight1())
            return row
        }
    }
}
