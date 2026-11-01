package id.clashtimer

import android.content.Context
import id.clashtimer.core.BBStrategy
import id.clashtimer.core.Boost
import id.clashtimer.core.BoostKind
import id.clashtimer.core.Boosts
import id.clashtimer.core.Events
import id.clashtimer.core.Scored
import id.clashtimer.core.Started
import id.clashtimer.core.Fmt
import id.clashtimer.core.GameData
import id.clashtimer.core.GoldPassDetect
import id.clashtimer.core.GpEvidence
import id.clashtimer.core.Sixth
import id.clashtimer.core.ExportReader
import id.clashtimer.core.ItemAdvisor
import id.clashtimer.core.ItemTip
import id.clashtimer.core.Lane
import id.clashtimer.core.LaneSlot
import id.clashtimer.core.PlanSettings
import id.clashtimer.core.PlanTarget
import id.clashtimer.core.Planner
import id.clashtimer.core.ProgressCalc
import id.clashtimer.core.Projector
import id.clashtimer.core.SimResult
import id.clashtimer.core.Village
import id.clashtimer.core.VillageProgress
import java.time.ZoneId

class ImportSummary(
    val tag: String, val running: Int, val next: Upgrade?, val exportAt: Long, val unknown: Int, val helped: Int,
    /** Gold Pass yang terdeteksi dari timer pada import ini. */
    val gold: List<GpEvidence> = emptyList(),
)

class PlanBundle(
    val tag: String,
    val village: Village,
    val ps: PlanSettings,
    val slots: List<LaneSlot>,
    val tips: List<ItemTip>,
    val home: VillageProgress,
    val bb: VillageProgress?,
    val simHome: SimResult,
    val simBB: SimResult?,
    val computedAt: Long,
    val version: Int,
    /** Simulasi Builder Base strategi Max (untuk progres bila strategi = builder ke-6). */
    val simBBMax: SimResult? = simBB,
    /** Urutan tercepat ke target (Home / Builder Base) dan perbandingannya dengan cara lama. */
    val homeTarget: PlanTarget? = null,
    val bbTarget: PlanTarget? = null,
)

/** Penghubung antara penyimpanan Android dan logika inti. */
object Repo {
    @Volatile private var gd: GameData? = null

    fun gameData(ctx: Context): GameData {
        gd?.let { return it }
        synchronized(this) {
            gd?.let { return it }
            val text = ctx.assets.open("gamedata.json").bufferedReader().use { it.readText() }
            return GameData.parse(text).also { gd = it }
        }
    }

    /** Desa tersimpan + koreksi sejak export (book, hammer, dibatalkan). */
    fun village(ctx: Context, tag: String): Village? {
        val text = Store.exportText(ctx, tag) ?: return null
        val v = runCatching { ExportReader.read(text, gameData(ctx), System.currentTimeMillis()) }.getOrNull() ?: return null
        v.applyStarted(gameData(ctx), Store.started(ctx, tag))
        v.applyOverrides(Store.done(ctx, tag).keys, Store.bumps(ctx, tag).map { it.first to it.second })
        val cancelled = Store.cancelled(ctx, tag).keys
        for (i in v.insts) if (i.uid in cancelled) { i.upgrading = false; i.gearing = false; i.remaining = 0 }
        // Bangunan baru yang dibatalkan: hapus lagi.
        v.insts.removeAll { it.uid in cancelled && it.level == 0 }
        return v
    }

    private fun helperName(lane: Lane): String = when (lane) {
        Lane.BUILDER -> "Builder's Apprentice"
        Lane.LAB -> "Lab Assistant"
        else -> "helper"
    }

    /** Susun daftar timer (dengan proyeksi helper & potion) dari data export. */
    fun buildTimers(ctx: Context, v: Village): List<Upgrade> {
        val tag = v.tag
        val zone = ZoneId.systemDefault()
        val boosts = Store.boosts(ctx, tag)
        val events = Store.events(ctx)
        val done = Store.done(ctx, tag)
        val cancelled = Store.cancelled(ctx, tag)
        val list = ArrayList<Upgrade>()
        val finishes = HashMap<String, Long>()

        for (t in v.timers) {
            if (t.uid in cancelled) continue
            if (t.gear) {
                // Gear-up: Master Builder bekerja di Home Village; tidak ikut potion / Clock Tower.
                val base = v.exportAt + t.remaining * 1000
                val doneAt = done[t.uid]
                val finish = doneAt ?: base
                finishes[t.uid] = finish
                list += Upgrade(
                    id = t.uid, tag = tag, kind = Kind.BB_BUILDER, name = t.name,
                    targetLevel = t.fromLevel, finishAt = finish, helped = false, baseFinishAt = base,
                    note = if (doneAt != null) "Ditandai selesai lebih awal" else "Gear-up · Home Village",
                    customTitle = "${t.name} · Gear-up",
                )
                continue
            }
            val helper = Projector.helperFor(v, t.lane, t.helped)
            val cuts = Events.cutsFor(events, t.lane, v.exportAt)
            val p = Projector.project(v.exportAt, t.remaining, t.lane, boosts, helper, cuts)
            val doneAt = done[t.uid]
            val finish = doneAt ?: p.finishAt
            finishes[t.uid] = finish
            val noBoost = Projector.project(v.exportAt, t.remaining, t.lane, emptyList(), helper, cuts).finishAt
            val noCut = if (cuts.isEmpty()) p.finishAt else Projector.project(v.exportAt, t.remaining, t.lane, boosts, helper).finishAt
            val notes = ArrayList<String>()
            if (doneAt != null) notes += "Ditandai selesai lebih awal"
            else {
                if (p.helperSessions.isNotEmpty()) {
                    notes += "🧑‍🔧 dibantu ${helperName(t.lane)} ${p.helperSessions.size}× (perkiraan) · tanpa helper ${Fmt.at(p.baseFinishAt, zone)}"
                }
                if (noBoost - p.finishAt > 60_000) notes += "⚡ dipercepat item ${Fmt.dur(noBoost - p.finishAt)}"
                if (noCut - p.finishAt > 60_000) notes += "🎉 dipotong event ${Fmt.dur(noCut - p.finishAt)}"
            }
            // Waktu di game sebelum tiap sesi helper berikutnya (game belum menghitung sesi yang belum mulai).
            val steps = if (doneAt != null || helper == null) emptyList() else p.helperSessions.map { s ->
                s to Projector.project(v.exportAt, t.remaining, t.lane, boosts, helper, cuts, sessionsBefore = s).finishAt
            }
            list += Upgrade(
                id = t.uid, tag = tag, kind = Kind.of(t.lane), name = t.name,
                targetLevel = t.fromLevel + 1, finishAt = finish, helped = t.helped,
                baseFinishAt = p.baseFinishAt, note = notes.joinToString("\n"), helperSteps = steps, extra = t.extra,
            )
        }

        // Upgrade yang dimulai lewat aplikasi (tanpa import ulang)
        for (st in Store.started(ctx, tag)) {
            if (st.uid in cancelled) continue
            val doneAt = done[st.uid]
            if (st.gear) {
                val finish = doneAt ?: st.finish
                finishes[st.uid] = finish
                list += Upgrade(
                    id = st.uid, tag = tag, kind = Kind.BB_BUILDER, name = st.name,
                    targetLevel = st.fromLevel, finishAt = finish, helped = false, baseFinishAt = st.finish,
                    note = if (doneAt != null) "Ditandai selesai lebih awal" else "Gear-up · Home Village · dimulai di aplikasi",
                    customTitle = "${st.name} · Gear-up",
                )
                continue
            }
            val p = Projector.project(st.start, st.durationSec, st.lane, boosts, null, Events.cutsFor(events, st.lane, st.start))
            val finish = doneAt ?: p.finishAt
            finishes[st.uid] = finish
            val note = when {
                doneAt != null -> "Ditandai selesai lebih awal"
                st.finish - p.finishAt > 60_000 -> "Dimulai di aplikasi · ⚡ dipercepat ${Fmt.dur(st.finish - p.finishAt)}"
                else -> "Dimulai di aplikasi"
            }
            list += Upgrade(
                id = st.uid, tag = tag, kind = Kind.of(st.lane), name = st.name,
                targetLevel = st.fromLevel + 1, finishAt = finish, helped = false,
                baseFinishAt = st.finish, note = note,
            )
        }

        // Pengingat sesi login yang dinyalakan di Sekarang / Jadwal
        for (r in Store.sessionReminders(ctx, tag)) {
            if (r.at < System.currentTimeMillis() - 60_000L) continue
            list += Upgrade(
                id = "$tag|session|${r.at}", tag = tag, kind = Kind.REMINDER, name = "Login",
                targetLevel = 0, finishAt = r.at, helped = false, note = r.text, customTitle = "🔔 Waktunya login",
            )
        }

        // Clock Tower siap lagi (boost gratis yang dicatat di aplikasi, cooldown 22 jam)
        val clockUsed = Store.clockUsed(ctx, tag)
        if (clockUsed > 0) {
            val ready = clockUsed + id.clashtimer.core.ClockTower.COOLDOWN
            if (ready > System.currentTimeMillis() - 60_000L) {
                list += Upgrade(
                    id = "$tag|clock|$clockUsed", tag = tag, kind = Kind.REMINDER, name = "Clock Tower",
                    targetLevel = 0, finishAt = ready, helped = false,
                    note = "Boost gratis 10× sudah bisa dipakai lagi", customTitle = "⏱️ Clock Tower siap lagi",
                )
            }
        }

        // Cooldown helper
        for (h in v.helpers) {
            if (h.cooldown <= 0 || "$tag|helpers|${h.id}" in cancelled) continue
            val at = v.exportAt + h.cooldown * 1000
            val lane = when (h.id) {
                GameData.APPRENTICE -> Lane.BUILDER
                GameData.LAB_ASSISTANT -> Lane.LAB
                else -> null
            }
            val target = if (lane == null) null else v.timers.firstOrNull { it.helped && it.lane == lane && it.uid !in cancelled }
            val targetFinish = target?.let { finishes[it.uid] }
            val name = gameData(ctx).name(h.id)
            val (title, note) = if (target != null && targetFinish != null && targetFinish > at) {
                "$name bantu ${target.name} lagi" to "Otomatis membantu ${target.name} (+${h.level} jam kerja)"
            } else "$name siap dipakai" to "Helper sudah bisa ditugaskan lagi"
            list += Upgrade(
                id = "$tag|helpers|${h.id}", tag = tag, kind = Kind.HELPER, name = name,
                targetLevel = 0, finishAt = at, helped = false, note = note, customTitle = title,
            )
        }
        return list.sortedBy { it.finishAt }
    }

    fun importText(ctx: Context, text: String): ImportSummary {
        val now = System.currentTimeMillis()
        val gd = gameData(ctx)
        val v = ExportReader.read(text, gd, now)
        val tag = v.tag
        // Gold Pass dari lama timer: upgrade yang ditandai "Sudah dimulai" atau baru muncul sejak export sebelumnya.
        val prev = Store.exportText(ctx, tag)?.let { runCatching { ExportReader.read(it, gd, now) }.getOrNull() }
        // Potion yang aktif saat export atau dicatat sejak export sebelumnya membuat timer tidak bisa jadi bukti Gold Pass.
        val boosted = HashSet<Lane>()
        v.potions?.let {
            if (it.builderSec > 0) boosted += Lane.BUILDER
            if (it.labSec > 0) boosted += Lane.LAB
            if (it.petSec > 0) boosted += Lane.PET
        }
        Store.boosts(ctx, tag).filter { it.end > (prev?.exportAt ?: 0L) }.forEach { boosted += it.kind.lanes }
        val gold = runCatching { GoldPassDetect.detect(gd, prev, v, Store.started(ctx, tag), Store.events(ctx), boosted) }.getOrDefault(emptyList())
        applyGold(ctx, tag, gold)
        Store.setBobDone(ctx, tag, Sixth.done(v))
        // Riwayat: catat upgrade yang berjalan (perkiraan mulai & selesai).
        val prevAt = Store.accounts(ctx).firstOrNull { it.tag == tag }?.exportedAt ?: 0L
        runCatching { History.onImport(ctx, gd, v, Store.planSettings(ctx, tag), prevAt) }
        Store.saveExport(ctx, tag, text)
        Store.setDone(ctx, tag, emptyMap())
        Store.setCancelled(ctx, tag, emptyMap())
        Store.setBumps(ctx, tag, emptyList())
        Store.setBoosts(ctx, tag, boostsFromExport(ctx, tag, v, Boosts.afterImport(Store.boosts(ctx, tag), v.exportAt)))
        // Upgrade yang ditandai dimulai sebelum export sudah tercakup di export.
        Store.setStarted(ctx, tag, Store.started(ctx, tag).filter { it.start > v.exportAt })
        val ups = buildTimers(ctx, v)
        Store.replaceAccount(ctx, Account(tag, v.homeBuilders, v.exportAt, v.th, v.bh), ups, Store.earlyMinutes(ctx))
        if (Store.selectedTag(ctx) == null) Store.setSelectedTag(ctx, tag)
        AlarmReceiver.processDue(ctx)
        val running = ups.filter { it.kind != Kind.HELPER && it.finishAt > now }
        return ImportSummary(tag, running.size, running.minByOrNull { it.finishAt }, v.exportAt, v.unknown, ups.count { it.helped }, gold)
    }

    /**
     * Boost yang sedang berjalan menurut export menggantikan catatan manual: Clock Tower (sisa boost &
     * kapan siap lagi), Builder Potion (builder_boost), Research Potion (lab_boost), Pet Potion (pet_boost).
     * Boost yang sudah lewat tidak berpengaruh (timer export sudah memperhitungkannya), dan boost yang
     * dicatat setelah waktu export tetap dipakai. Tidak ada asumsi boost berikutnya pasti dipakai.
     * Export hanya memberi sisa detik, bukan jenisnya: Builder Bite / Study Soup dipakai bila itu yang
     * dicatat, selain itu dianggap potion.
     */
    private fun boostsFromExport(ctx: Context, tag: String, v: Village, boosts: List<Boost>): List<Boost> {
        val at = v.exportAt
        var list = boosts
        fun replace(kinds: Set<BoostKind>, sec: Long, default: BoostKind) {
            val active = list.filter { it.kind in kinds && it.start <= at }
            val kind = active.firstOrNull { it.kind == default }?.kind ?: active.firstOrNull()?.kind ?: default
            list = list - active.toSet()
            if (sec > 0) list = list + Boost(kind, at, at + sec * 1000)
        }
        v.clock?.let { c ->
            replace(setOf(BoostKind.CLOCK_TOWER, BoostKind.CLOCK_TOWER_POTION), c.boostSec, BoostKind.CLOCK_TOWER)
            Store.setClockUsed(ctx, tag, if (c.cooldownSec > 0) at + c.cooldownSec * 1000 - id.clashtimer.core.ClockTower.COOLDOWN else 0L)
        }
        v.potions?.let { p ->
            replace(setOf(BoostKind.BUILDER_POTION, BoostKind.BUILDER_BITE), p.builderSec, BoostKind.BUILDER_POTION)
            replace(setOf(BoostKind.RESEARCH_POTION, BoostKind.STUDY_SOUP), p.labSec, BoostKind.RESEARCH_POTION)
            replace(setOf(BoostKind.PET_POTION), p.petSec, BoostKind.PET_POTION)
        }
        return list
    }

    /**
     * Terapkan Gold Pass yang terdeteksi. Bila hanya satu jenis boost yang terbukti dan jenis lainnya
     * belum pernah terdeteksi, jenis lainnya diperkirakan sama (Gold Pass yang sama) sampai ada buktinya.
     */
    private fun applyGold(ctx: Context, tag: String, found: List<GpEvidence>) {
        for (g in found) {
            Store.putPlan(ctx, if (g.research) "gp_research" else "gp_builder", g.pct, tag)
            Store.setGoldDetected(ctx, tag, g.research, Store.GoldDetected(g.pct, g.startedAt, g.title))
        }
        val one = found.singleOrNull() ?: return
        val other = !one.research
        val otherSeen = Store.goldDetected(ctx, tag, other)
        if (otherSeen == null || otherSeen.guessed) {
            Store.putPlan(ctx, if (other) "gp_research" else "gp_builder", one.pct, tag)
            Store.setGoldDetected(ctx, tag, other, Store.GoldDetected(one.pct, one.startedAt, one.title, guessed = true))
        }
    }

    /**
     * Upgrade hero, lab, pet, Star Lab, Town Hall & Builder Hall yang menurut API sudah mencapai level
     * tujuannya sebelum timer habis (book, gem, potion): tandai selesai. Kembalian: yang ditandai.
     */
    fun applyApiProgress(ctx: Context, tag: String): List<String> {
        val api = Store.apiPlayer(ctx, tag) ?: return emptyList()
        val done = Store.done(ctx, tag).toMutableMap()
        val out = ArrayList<String>()
        for (u in Store.upgrades(ctx)) {
            if (u.tag != tag || u.id in done || u.targetLevel <= 0 || u.finishAt <= api.at) continue
            if (u.kind == Kind.HELPER || u.kind == Kind.REMINDER || u.id.contains("|gear|")) continue
            val level = when (u.name) {
                "Town Hall" -> api.th.takeIf { !u.kind.builderBase && it > 0 }
                "Builder Hall" -> api.bh.takeIf { u.kind.builderBase && it > 0 }
                else -> api.units[(if (u.kind.builderBase) "b:" else "h:") + u.name]
            } ?: continue
            if (level >= u.targetLevel) {
                done[u.id] = api.at
                out += "${u.name} Lv ${u.targetLevel}"
            }
        }
        if (out.isNotEmpty()) {
            Store.setDone(ctx, tag, done)
            reproject(ctx, tag)
        }
        return out
    }

    /** Hitung ulang timer satu akun setelah item dipakai / upgrade ditandai. */
    fun reproject(ctx: Context, tag: String) {
        reprojectQuiet(ctx, tag)
        AlarmReceiver.processDue(ctx)
    }

    /** Seperti [reproject] tetapi tanpa mengirim notifikasi / memasang alarm. */
    private fun reprojectQuiet(ctx: Context, tag: String) {
        val text = Store.exportText(ctx, tag) ?: return
        val v = runCatching { ExportReader.read(text, gameData(ctx), System.currentTimeMillis()) }.getOrNull() ?: return
        val fresh = buildTimers(ctx, v)
        Store.mergeProjections(ctx, tag, fresh, Store.done(ctx, tag).keys)
    }

    /**
     * Setelah aplikasi di-update: bangun ulang timer dari export tersimpan (mis. gear-up yang dulu
     * terbaca sebagai upgrade biasa). Entri baru yang waktunya sudah lewat tidak dinotifikasi.
     */
    fun migrate(ctx: Context) {
        if (Store.dataVersion(ctx) >= Store.DATA_VERSION) return
        Store.setDataVersion(ctx, Store.DATA_VERSION)
        val before = Store.upgrades(ctx).map { it.id }.toSet()
        for (a in Store.accounts(ctx)) runCatching { reprojectQuiet(ctx, a.tag) }
        rebuildGlobalRemindersQuiet(ctx)
        val now = System.currentTimeMillis()
        val list = Store.upgrades(ctx)
        var changed = false
        for (u in list) {
            if (u.id !in before && u.finishAt <= now && !u.doneNotified) {
                u.doneNotified = true
                u.earlyNotified = true
                changed = true
            }
        }
        if (changed) Store.saveUpgrades(ctx, list)
    }

    /** Pengingat global (tanpa desa): event mulai / berakhir besok, Gold Pass berakhir besok. */
    fun rebuildGlobalReminders(ctx: Context) {
        rebuildGlobalRemindersQuiet(ctx)
        AlarmScheduler.schedule(ctx)
    }

    private fun rebuildGlobalRemindersQuiet(ctx: Context) {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val fresh = ArrayList<Upgrade>()
        for (e in Store.events(ctx).filter { it.enabled }) {
            if (e.start > now) {
                fresh += Upgrade(
                    id = "event|${e.id}|start", tag = "", kind = Kind.REMINDER, name = e.name, targetLevel = 0,
                    finishAt = e.start, helped = false, customTitle = "🎉 ${e.name} dimulai",
                    note = "Waktu −${e.timePct}% & biaya −${e.costPct}% (${e.scopeLabel}) sampai ${Fmt.at(e.end, zone)}",
                )
            }
            val soon = e.end - id.clashtimer.core.DAY
            if (soon > now && soon > e.start) {
                fresh += Upgrade(
                    id = "event|${e.id}|end", tag = "", kind = Kind.REMINDER, name = e.name, targetLevel = 0,
                    finishAt = soon, helped = false, customTitle = "⏳ ${e.name} berakhir besok",
                    note = "Berakhir ${Fmt.at(e.end, zone)} — mulai upgrade panjang sebelum event selesai",
                )
            }
        }
        val gpEnd = Store.goldPassEnd(ctx)
        val gpSoon = gpEnd - id.clashtimer.core.DAY
        if (gpEnd > 0 && gpSoon > now) {
            fresh += Upgrade(
                id = "goldpass|$gpEnd", tag = "", kind = Kind.REMINDER, name = "Gold Pass", targetLevel = 0,
                finishAt = gpSoon, helped = false, customTitle = "🎫 Gold Pass berakhir besok",
                note = "Musim berakhir ${Fmt.at(gpEnd, zone)}. Setelah itu cek lagi Builder/Research Boost tiap desa (ikon opsi).",
            )
        }
        Store.mergeProjections(ctx, "", fresh, emptySet())
    }

    /** Pilihan upgrade untuk pekerja [lane] yang bebas sekarang di desa [tag] (urutan saran). */
    fun freeOptions(ctx: Context, tag: String, lane: Lane): List<Scored> {
        val v = village(ctx, tag) ?: return emptyList()
        val now = System.currentTimeMillis()
        val slots = Planner.suggest(v, gameData(ctx), Store.planSettings(ctx, tag), now, finishes(ctx, tag), upcoming = 0)
        return slots.filter { it.lane == lane && it.freeAt <= now }.flatMap { it.ranked }
    }

    /** "Sudah dimulai" dari notifikasi: cari saran yang sama lalu pasang timernya. */
    fun startById(ctx: Context, tag: String, lane: Lane, itemId: Int, fromLevel: Int, gear: Boolean): Upgrade? {
        val pick = freeOptions(ctx, tag, lane).firstOrNull {
            it.c.item.id == itemId && it.c.fromLevel == fromLevel && it.c.gear == gear
        } ?: return null
        return startUpgrade(ctx, tag, pick)
    }

    /** Bandingkan level dari API dengan data aplikasi: export yang ketinggalan. */
    fun staleNotes(ctx: Context, tag: String, v: Village): List<String> {
        val api = Store.apiPlayer(ctx, tag) ?: return emptyList()
        if (api.at <= v.exportAt) return emptyList()
        val fin = finishes(ctx, tag)
        val out = ArrayList<String>()
        if (api.th > v.th) out += "Town Hall TH${api.th} di game (aplikasi TH${v.th})"
        if (api.bh > v.bh && v.bh > 0) out += "Builder Hall BH${api.bh} di game (aplikasi BH${v.bh})"
        val heroCats = setOf(id.clashtimer.core.Cat.HERO, id.clashtimer.core.Cat.BB_HERO)
        for ((name, lvl) in api.heroes) {
            // Hero yang belum ada di data game aplikasi tidak bisa dibandingkan
            if (gameData(ctx).items.values.none { it.name == name && it.cat in heroCats }) continue
            val inst = v.insts.firstOrNull { it.item.name == name && it.item.cat in heroCats }
            val done = inst != null && inst.upgrading && (fin[inst.uid] ?: Long.MAX_VALUE) <= api.at
            val projected = (inst?.level ?: 0) + if (done) 1 else 0
            if (lvl > projected) out += "$name Lv$lvl di game (aplikasi Lv$projected)"
        }
        return out
    }

    /**
     * Tandai saran sebagai "sudah dimulai di game": timer & notifikasi langsung dipasang.
     * Durasi diambil dari saran (sudah termasuk Gold Pass & event bila dimulai sekarang).
     */
    fun startUpgrade(ctx: Context, tag: String, pick: Scored): Upgrade? {
        val now = System.currentTimeMillis()
        val c = pick.c
        val ps = Store.planSettings(ctx, tag)
        val duration = ps.durationAt(c, now)
        val uid = if (c.gear) "$tag|gear|${c.item.id}|m$now" else "$tag|manual|${c.item.id}|${c.fromLevel}|$now"
        val st = Started(uid, c.item.id, c.fromLevel, c.lane, c.item.name, now, duration, gear = c.gear)
        Store.setStarted(ctx, tag, Store.started(ctx, tag) + st)
        reproject(ctx, tag)
        return Store.upgrades(ctx).firstOrNull { it.id == st.uid }
    }

    /** Batalkan "Sudah dimulai" (tombol Batalkan di pesan bawah). */
    fun undoStart(ctx: Context, tag: String, uid: String) {
        Store.setStarted(ctx, tag, Store.started(ctx, tag).filter { it.uid != uid })
        Store.saveUpgrades(ctx, Store.upgrades(ctx).filter { it.id != uid })
        reproject(ctx, tag)
    }

    /** Pengingat sesi login aktif untuk jam [at] (toleransi 1 menit). */
    fun sessionReminderOn(ctx: Context, tag: String, at: Long): Boolean =
        Store.sessionReminders(ctx, tag).any { kotlin.math.abs(it.at - at) < 60_000L }

    /** Nyalakan / matikan pengingat sesi; yang sudah lewat dibuang. */
    fun toggleSessionReminder(ctx: Context, tag: String, at: Long, text: String): Boolean {
        val now = System.currentTimeMillis()
        val list = Store.sessionReminders(ctx, tag).filter { it.at > now - 60_000L }
        val on = list.none { kotlin.math.abs(it.at - at) < 60_000L }
        val next = if (on) list + Store.SessionReminder(at, text) else list.filter { kotlin.math.abs(it.at - at) >= 60_000L }
        Store.setSessionReminders(ctx, tag, next)
        reproject(ctx, tag)
        return on
    }

    /** Id item game dari id timer ("tag|bagian|id|…"). */
    fun itemIdOf(timerId: String): Int? = timerId.split('|').getOrNull(2)?.toIntOrNull()

    fun finishes(ctx: Context, tag: String): Map<String, Long> =
        Store.upgrades(ctx).filter { it.tag == tag && it.kind != Kind.REMINDER }.associate { it.id to it.finishAt }

    /** Hitung saran, progres dan simulasi (berat — jalankan di background). */
    fun computePlan(ctx: Context, tag: String): PlanBundle? {
        val v = village(ctx, tag) ?: return null
        val gd = gameData(ctx)
        val ps0 = Store.planSettings(ctx, tag)
        val now = System.currentTimeMillis()
        val fin = finishes(ctx, tag)
        val home = ProgressCalc.compute(v, gd, false, ps0.gold, now, fin, ps0.skipIds)
        val bb = if (v.hasBB && v.bh > 0) ProgressCalc.compute(v, gd, true, ps0.gold, now, fin, ps0.skipIds) else null
        val homeLanes = setOf(Lane.BUILDER, Lane.LAB, Lane.PET)
        val bbLanes = setOf(Lane.BB_BUILDER, Lane.STAR_LAB)

        // Urutan tercepat ke target strategi: dicari ulang bila data / pengaturan berubah (atau tiap 6 jam),
        // selain itu memakai hasil terakhir (satu simulasi saja).
        val sig = listOf(Store.planVersion(ctx), v.exportAt, fin.size, fin.values.sum(), ps0.strategy, ps0.bbStrategy,
            ps0.gold.builderPct, ps0.gold.researchPct, bb != null).joinToString("|")
        val cached = Store.optCache(ctx, tag)?.takeIf { it.sig == sig && now - it.at in 0..6 * 3_600_000L }
        val ps: PlanSettings
        val simHome: SimResult
        val simBB: SimResult?
        val homeTarget: PlanTarget?
        val bbTarget: PlanTarget?
        if (cached != null) {
            ps = ps0
            simHome = Planner.simulate(v, gd, ps, now, fin, homeLanes, keepSteps = 120)
            simBB = if (bb != null) Planner.simulate(v, gd, ps, now, fin, bbLanes, keepSteps = 160) else null
            homeTarget = cached.home
            bbTarget = cached.bb
        } else {
            val oh = Planner.optimize(v, gd, ps0, now, fin, homeLanes, keepSteps = 120)
            val ob = if (bb != null) Planner.optimize(v, gd, ps0, now, fin, bbLanes, keepSteps = 160) else null
            val chosen = oh.policies + (ob?.policies ?: emptyMap())
            ps = ps0.withPolicies(chosen)
            simHome = oh.sim
            simBB = ob?.sim
            homeTarget = oh.summary()
            bbTarget = ob?.summary()
            Store.setOptCache(ctx, tag, Store.OptCache(sig, now, chosen, homeTarget, bbTarget))
        }
        val slots = Planner.suggest(v, gd, ps, now, fin)
        val tips = ItemAdvisor.tips(v, gd, ps, now, fin, Store.items(ctx, tag))
        val simBBMax = if (simBB?.sixth != null) Planner.simulate(v, gd, ps.withBB(BBStrategy.MAX), now, fin, bbLanes, keepSteps = 10) else simBB
        Store.setLastPct(ctx, tag, home.pct.toFloat())
        runCatching { History.snapshot(ctx, tag, home.pct, bb?.pct) }
        return PlanBundle(tag, v, ps, slots, tips, home, bb, simHome, simBB, now, Store.planVersion(ctx), simBBMax, homeTarget, bbTarget)
    }
}
