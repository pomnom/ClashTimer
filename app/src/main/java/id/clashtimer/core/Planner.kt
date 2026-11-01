package id.clashtimer.core

import kotlin.math.sqrt

enum class Strategy(val label: String, val desc: String) {
    MAX("Seimbang (max TH)", "Maksimalkan TH sekarang: army, hero & pertahanan penting dulu, Town Hall terakhir"),
    RUSH("Rush TH", "Town Hall secepatnya, lalu offense (hero, army, lab); pertahanan belakangan"),
    HERO("Hero dulu", "Utamakan upgrade hero, lalu army dan pertahanan");
}

class PlanSettings(
    val schedule: OfflineSchedule,
    val cwl: CwlRule,
    val gold: GoldPass,
    val strategy: Strategy,
    /** 0 = otomatis dari export. */
    val bbBuilders: Int = 0,
    val bbStrategy: BBStrategy = BBStrategy.MAX,
    /** Jangan sarankan upgrade Gold Mine, Elixir Collector & Dark Elixir Drill. */
    val skipCollectors: Boolean = false,
    val events: List<GameEvent> = emptyList(),
    /** Item yang didahulukan pengguna (tahan lama → Prioritaskan). */
    val pinIds: Set<Int> = emptySet(),
    /** Item yang disembunyikan dari saran & rencana (tetap dihitung di progres). */
    val hideIds: Set<Int> = emptySet(),
) {
    /** Id item yang tidak di-upgrade dan tidak dihitung di progres (collector). */
    val skipIds: Set<Int> = if (skipCollectors) Planner.COLLECTORS else emptySet()

    /** Id item yang tidak disarankan: collector (bila dipilih) + item yang disembunyikan. */
    val planSkip: Set<Int> = skipIds + hideIds

    fun withBB(s: BBStrategy) = PlanSettings(schedule, cwl, gold, strategy, bbBuilders, s, skipCollectors, events, pinIds, hideIds)

    /** Salinan dengan Gold Pass / strategi / event lain (untuk simulasi). */
    fun with(gold: GoldPass = this.gold, strategy: Strategy = this.strategy, events: List<GameEvent> = this.events) =
        PlanSettings(schedule, cwl, gold, strategy, bbBuilders, bbStrategy, skipCollectors, events, pinIds, hideIds)

    fun event(lane: Lane, t: Long): GameEvent? = Events.at(events, lane, t)

    /** Durasi (detik) bila dimulai pada [t]: Gold Pass + potongan event bila sedang berlangsung. */
    fun durationAt(c: Candidate, t: Long): Long {
        if (c.gear) return c.duration
        val ev = event(c.lane, t)?.timePct ?: 0
        return if (ev == 0) c.duration else gold.apply(c.baseDuration, c.lane, ev)
    }

    fun costAt(c: Candidate, t: Long): Long {
        if (c.gear) return c.cost
        val ev = event(c.lane, t)?.costPct ?: 0
        return if (ev == 0) c.cost else c.cost * (100 - ev.coerceIn(0, 100)) / 100
    }
}

class Candidate(
    val lane: Lane,
    val item: Item,
    /** null = bangunan/hero/pet baru. */
    val inst: Inst?,
    val fromLevel: Int,
    val toLevel: Int,
    /** Detik, sudah termasuk diskon Gold Pass (tanpa event). */
    val duration: Long,
    val cost: Long,
    val isNew: Boolean,
    val count: Int = 1,
    /** Gear-up pertahanan Home Village oleh Master Builder. */
    val gear: Boolean = false,
    /** Durasi asli dari data game (tanpa diskon apa pun). */
    val baseDuration: Long = duration,
) {
    val title: String
        get() = when {
            gear -> "${item.name} · Gear-up"
            isNew && (item.cat == Cat.HERO || item.cat == Cat.BB_HERO || item.cat == Cat.PET) -> "Buka ${item.name}"
            isNew -> "Bangun ${item.name}" + if (count > 1) " (sisa $count)" else ""
            else -> "${item.name} → Lv $toLevel" + if (count > 1) "  ×$count" else ""
        }

    /** Judul tanpa jumlah. */
    val shortTitle: String get() = title.substringBefore("  ×").substringBefore(" (sisa")

    /** Keterangan level, mis. "Lv 17 → 18". */
    val levelLabel: String
        get() = when {
            gear -> "Gear-up · Home Village"
            isNew && fromLevel == 0 -> "Bangun baru"
            isNew -> "Buka di Lv $toLevel"
            else -> "Lv $fromLevel → $toLevel"
        }

    fun sameAs(o: Candidate): Boolean =
        item.id == o.item.id && fromLevel == o.fromLevel && isNew == o.isNew && gear == o.gear
}

class Scored(
    val c: Candidate,
    val start: Long,
    val finish: Long,
    /** Lama pekerja menganggur setelah selesai karena Anda sedang offline. */
    val idle: Long,
    val score: Double,
    /** Durasi (detik) & biaya efektif bila dimulai pada [start] (Gold Pass + event). */
    val duration: Long = c.duration,
    val cost: Long = c.cost,
    val event: GameEvent? = null,
    /** Urutan strategi builder ke-6 (kecil = lebih dulu); 0 = tidak dipakai. */
    val tier: Int = 0,
    /** Alasan khusus, mis. storage dinaikkan karena kapasitas gudang kurang. */
    val reason: String? = null,
) {
    fun with(score: Double = this.score, tier: Int = this.tier, reason: String? = this.reason) =
        Scored(c, start, finish, idle, score, duration, cost, event, tier, reason)
}

class LaneSlot(
    val lane: Lane,
    /** Kapan pekerja bebas (≤ now = sudah bebas). */
    val freeAt: Long,
    /** Kapan Anda bisa memulai (online). */
    val start: Long,
    /** Nama upgrade yang selesai dan membebaskan pekerja ini. */
    val freedBy: String?,
    /** Jumlah pekerja yang bebas di slot ini. */
    val count: Int,
    val ranked: List<Scored>,
    val cwlSkipped: List<Candidate>,
)

class PlannedStep(
    val lane: Lane,
    /** Nomor pekerja di jenisnya (B1, B2, …). */
    val laneIndex: Int,
    val title: String,
    val start: Long,
    val finish: Long,
    val idle: Long,
    /** Catatan, mis. hero yang ditunda karena CWL. */
    val note: String? = null,
    val durationSec: Long = 0,
    val item: Item? = null,
    val fromLevel: Int = 0,
    val toLevel: Int = 0,
    val isNew: Boolean = false,
    val gear: Boolean = false,
    /** Sudah berjalan saat rencana dihitung. */
    val running: Boolean = false,
    val cost: Long = 0,
    /** Nama event yang memotong upgrade ini. */
    val event: String? = null,
    val tier: Int = 0,
)

/** Sesi helper yang diperkirakan terjadi dalam simulasi. */
class HelperEvent(val time: Long, val lane: Lane, val level: Int, val target: String)

/** Upgrade termahal untuk satu sumber daya dalam satu rentang waktu. */
class NeedItem(val title: String, val cost: Long, val start: Long, val item: Item?)

/** Kebutuhan sumber daya untuk upgrade yang DIMULAI dalam [window] ms sejak rencana dihitung. */
class Needs(val window: Long, val costs: Map<String, Long>, val top: Map<String, NeedItem>)

class SimResult(
    val builderDone: Long?,
    val labDone: Long?,
    val petDone: Long?,
    val bbBuilderDone: Long?,
    val starLabDone: Long?,
    val steps: List<PlannedStep>,
    val builderIdle7d: Long,
    val truncated: Boolean,
    val helperEvents: List<HelperEvent> = emptyList(),
    /** Upgrade yang sudah berjalan, dengan nomor pekerjanya. */
    val running: List<PlannedStep> = emptyList(),
    /** Jalur builder ke-6 (hanya bila strategi Builder Base = Builder ke-6). */
    val sixth: SixthPlan? = null,
    /** Kebutuhan sumber daya 24 jam, 3 hari, 7 hari. */
    val needs: List<Needs> = emptyList(),
)

object Planner {
    /** Pengali skor item yang diprioritaskan pengguna: didahulukan bila tersedia. */
    const val PIN_BOOST = 20.0

    /**
     * Pengali hero yang sedang menganggur: hero (rantai upgrade terpanjang & paling berpengaruh) langsung
     * mendapat builder yang bebas. Hero yang bentrok dengan CWL tetap ditunda.
     */
    const val HERO_IDLE_BOOST = 10.0

    /** Nilai 1 Dark Elixir dalam Gold/Elixir saat membandingkan biaya (DE jauh lebih langka). */
    const val DE_VALUE = 100.0

    /** Pengali storage Builder Base saat ada upgrade yang tertahan kapasitas gudang. */
    const val STORAGE_NEED = 6.0

    /** Biaya dalam satuan Gold/Elixir (Dark Elixir disetarakan). */
    fun costValue(cost: Long, resource: String): Double =
        if (resource.contains("Dark", ignoreCase = true)) cost * DE_VALUE else cost.toDouble()


    /** Gold Mine, Elixir Collector, Dark Elixir Drill (Home) + Gold Mine & Elixir Collector (BB). */
    val COLLECTORS = setOf(1000004, 1000002, 1000023, 1000037, 1000035)

    private class W(
        val hero: Double, val army: Double, val defense: Double, val trap: Double,
        val storage: Double, val collector: Double, val worker: Double, val townHall: Double,
    )

    private fun weights(s: Strategy) = when (s) {
        Strategy.MAX -> W(2.2, 1.6, 1.0, 0.5, 0.6, 0.3, 0.6, 0.05)
        Strategy.RUSH -> W(3.0, 2.5, 0.2, 0.1, 0.6, 0.02, 0.2, 8.0)
        Strategy.HERO -> W(5.0, 1.6, 0.7, 0.3, 0.4, 0.2, 0.4, 0.05)
    }

    private val keyBuildings = setOf(
        "Army Camp", "Clan Castle", "Laboratory", "Pet House", "Hero Hall",
        "Star Laboratory", "Builder Barracks", "Reinforcement Camp",
    )
    private val keyDefenses = setOf("Inferno Tower", "Eagle Artillery", "Scattershot", "Monolith")

    fun importance(c: Candidate, strategy: Strategy): Double {
        val w = weights(strategy)
        val it = c.item
        val base = when (it.cat) {
            Cat.HERO, Cat.BB_HERO -> w.hero
            Cat.TROOP -> if (it.isSiege) 0.8 else 1.0
            Cat.SPELL, Cat.PET, Cat.BB_TROOP -> 1.0
            Cat.TRAP, Cat.BB_TRAP -> w.trap
            Cat.BUILDING, Cat.BB_BUILDING -> when {
                it.isTownHall -> w.townHall
                it.type == "Army" -> w.army
                it.type == "Defense" -> w.defense
                it.type == "Resource" -> if (it.name.contains("Storage")) w.storage else w.collector
                it.type.startsWith("Worker") -> w.worker
                else -> 1.0
            }
        }
        var mult = 1.0
        if (it.name in keyBuildings) mult *= 3.0
        if (it.name in keyDefenses) mult *= if (strategy == Strategy.RUSH) 4.5 else 2.0
        if (c.isNew) mult *= 2.0
        if (strategy == Strategy.RUSH && it.isTownHall) mult *= 100.0
        return base * mult
    }

    fun score(c: Candidate, start: Long, ps: PlanSettings, mult: Double = 1.0, tier: Int = 0): Scored {
        val dur = ps.durationAt(c, start)
        val cost = ps.costAt(c, start)
        val d = dur * 1000
        val finish = start + d
        val idle = ps.schedule.nextOnline(finish) - finish
        val hours = dur / 3600.0
        val costM = costValue(cost, c.item.resource) / 1_000_000.0
        val pin = if (c.item.id in ps.pinIds) PIN_BOOST else 1.0
        val hero = if (c.item.cat == Cat.HERO && !c.gear) HERO_IDLE_BOOST else 1.0
        val prio = importance(c, ps.strategy) * mult * pin * hero / sqrt((hours + 0.25) * (costM + 0.05))
        // Efisiensi = bagian waktu pekerja yang benar-benar dipakai. Dikuadratkan supaya
        // sebelum tidur upgrade panjang (yang selesai saat Anda bangun) lebih diutamakan
        // daripada upgrade singkat yang membuat builder menganggur semalaman.
        val eff = if (d + idle <= 0) 1.0 else d.toDouble() / (d + idle).toDouble()
        val choice = prio * eff * eff
        val ev = if (c.gear) null else ps.event(c.lane, start)
        return Scored(c, start, finish, idle, choice, dur, cost, ev, tier)
    }

    // ------------------------------------------------------------------ kandidat

    private fun mergeReady(v: Village, srcs: List<MergeSrc>, self: Inst?): Boolean {
        for ((key, group) in srcs.groupBy { Triple(it.id, it.level, it.geared) }) {
            val avail = v.insts.count {
                it !== self && it.item.id == key.first && !it.upgrading && it.level >= key.second &&
                    (!key.third || it.geared)
            }
            if (avail < group.size) return false
        }
        return true
    }

    /** Jumlah bangunan yang boleh dimiliki, dikurangi yang sudah dilebur ke bangunan gabungan. */
    fun allowed(v: Village, gd: GameData, id: Int, hall: Int, bb: Boolean): Int {
        var n = gd.allowedCount(id, hall, bb)
        for (i in v.insts) {
            for ((lvl, srcs) in i.item.merges) {
                if (lvl <= i.level) n -= srcs.count { it.id == id }
            }
        }
        return n.coerceAtLeast(0)
    }

    private val skipNew = setOf(GameData.HUT, GameData.BOB_HUT, GameData.HELPER_HUT, GameData.WALL, GameData.BB_WALL)

    fun candidates(
        v: Village, gd: GameData, lane: Lane, gold: GoldPass, includeHall: Boolean,
        skip: Set<Int> = emptySet(),
    ): List<Candidate> {
        val out = ArrayList<Candidate>()
        val bb = lane.bb
        val hall = if (bb) v.bh else v.th
        if (bb && hall == 0) return out
        val cats: Set<Cat> = when (lane) {
            Lane.BUILDER -> setOf(Cat.BUILDING, Cat.TRAP, Cat.HERO)
            Lane.LAB -> setOf(Cat.TROOP, Cat.SPELL)
            Lane.PET -> setOf(Cat.PET)
            Lane.BB_BUILDER -> setOf(Cat.BB_BUILDING, Cat.BB_HERO, Cat.BB_TRAP)
            Lane.STAR_LAB -> setOf(Cat.BB_TROOP)
        }
        val reqLevel = when (lane) {
            Lane.LAB -> v.labLevel
            Lane.PET -> v.petHouse
            Lane.STAR_LAB -> v.starLab
            else -> 0
        }
        if ((lane == Lane.LAB || lane == Lane.PET || lane == Lane.STAR_LAB) && reqLevel == 0) return out

        // Upgrade level berikutnya untuk yang sudah ada
        for (inst in v.insts) {
            if (inst.upgrading || inst.item.cat !in cats || inst.item.isWall) continue
            val item = inst.item
            if (item.id in skip) continue
            val next = item.lv(inst.level + 1) ?: continue
            if (next.time <= 0) continue
            // Syarat hall troop Builder Base di data tidak akurat; cukup level Star Lab.
            if (item.cat != Cat.BB_TROOP && next.hall > hall) continue
            if (item.isTownHall && !includeHall) continue
            when (item.cat) {
                Cat.HERO -> if (next.req > v.heroHall) continue
                Cat.TROOP, Cat.SPELL, Cat.PET, Cat.BB_TROOP -> if (next.req > reqLevel) continue
                else -> {}
            }
            val m = item.merges[inst.level + 1]
            if (m != null && !mergeReady(v, m, inst)) continue
            out += Candidate(lane, item, inst, inst.level, inst.level + 1, gold.apply(next.time, lane), next.cost, false,
                baseDuration = next.time)
        }

        // Bangunan baru yang sudah boleh dibangun
        var placements = 0
        if (lane == Lane.BUILDER || lane == Lane.BB_BUILDER) {
            for (id in gd.unlockIds(bb)) {
                if (id in skipNew) continue
                val item = gd.item(id) ?: continue
                if (item.cat != Cat.BUILDING && item.cat != Cat.TRAP && item.cat != Cat.BB_BUILDING && item.cat != Cat.BB_TRAP) continue
                if (item.isWall || item.levels.isEmpty() || item.merges[1] != null) continue
                val lv1 = item.lv(1) ?: continue
                if (lv1.hall > hall) continue
                val missing = allowed(v, gd, id, hall, bb) - v.count(id)
                if (missing <= 0) continue
                placements++
                out += Candidate(lane, item, null, 0, 1, gold.apply(lv1.time, lane), lv1.cost, true, missing,
                    baseDuration = lv1.time)
            }
        }
        // Town Hall / Builder Hall baru bisa naik setelah semua bangunan baru dipasang.
        if (placements > 0) out.removeAll { it.item.isTownHall }

        // Hero baru
        if (lane == Lane.BUILDER && v.heroHall > 0) {
            for (item in gd.items.values) {
                if (item.cat != Cat.HERO || v.has(item.id)) continue
                if (item.prodLevel > v.heroHall) continue
                val lv1 = item.lv(1) ?: continue
                if (lv1.hall > hall || lv1.time <= 0) continue
                out += Candidate(lane, item, null, 0, 1, gold.apply(lv1.time, lane), lv1.cost, true, baseDuration = lv1.time)
            }
        }
        // Hero Builder Base baru (Battle Copter mulai di Lv15)
        if (lane == Lane.BB_BUILDER) {
            for (item in gd.items.values) {
                if (item.cat != Cat.BB_HERO || v.has(item.id)) continue
                if (!bbHeroUnlocked(gd, item, hall)) continue
                val first = item.levels.firstOrNull { it.time > 0 } ?: continue
                if (first.hall > hall) continue
                out += Candidate(lane, item, null, first.level - 1, first.level, gold.apply(first.time, lane), first.cost, true,
                    baseDuration = first.time)
            }
        }
        // Pet baru
        if (lane == Lane.PET && v.petHouse > 0) {
            for (item in gd.items.values) {
                if (item.cat != Cat.PET || v.has(item.id)) continue
                if (item.prodLevel > v.petHouse) continue
                val lv1 = item.lv(1) ?: continue
                if (lv1.hall > hall || lv1.time <= 0) continue
                out += Candidate(lane, item, null, 0, 1, gold.apply(lv1.time, lane), lv1.cost, true, baseDuration = lv1.time)
            }
        }
        return out
    }

    /** Hero Builder Base terbuka bila altarnya (bangunan bernama sama) sudah tersedia di level hall ini. */
    fun bbHeroUnlocked(gd: GameData, hero: Item, hall: Int): Boolean {
        val altar = gd.items.values.firstOrNull { it.cat == Cat.BB_BUILDING && it.name == hero.name } ?: return false
        return gd.allowedCount(altar.id, hall, true) > 0
    }

    /** Gabungkan kandidat identik (mis. 3 Cannon di level yang sama). */
    fun dedupe(list: List<Candidate>): List<Candidate> {
        val map = LinkedHashMap<String, Candidate>()
        for (c in list) {
            val key = "${c.item.id}|${c.fromLevel}|${c.isNew}|${c.gear}"
            val prev = map[key]
            map[key] = if (prev == null) c
            else Candidate(c.lane, c.item, prev.inst, c.fromLevel, c.toLevel, c.duration, c.cost, c.isNew,
                if (c.isNew) c.count else prev.count + 1, c.gear, c.baseDuration)
        }
        return map.values.toList()
    }

    // ------------------------------------------------------------------ saran

    fun finishOf(v: Village, i: Inst, finishes: Map<String, Long>): Long =
        finishes[i.uid] ?: (v.exportAt + i.remaining * 1000)

    /** Selesaikan (di salinan desa) semua upgrade yang sudah selesai pada waktu [t]. */
    private fun completeUntil(v: Village, finishes: Map<String, Long>, t: Long) {
        for (i in v.insts) {
            if (!i.upgrading) continue
            if (finishOf(v, i, finishes) <= t) i.complete()
        }
    }

    fun laneCount(v: Village, lane: Lane, ps: PlanSettings): Int = when (lane) {
        Lane.BUILDER -> v.homeBuilders
        Lane.BB_BUILDER -> if (ps.bbBuilders > 0) ps.bbBuilders else v.bbBuildersAuto
        Lane.LAB -> if (v.labLevel > 0) 1 else 0
        Lane.PET -> if (v.petHouse > 0) 1 else 0
        Lane.STAR_LAB -> if (v.starLab > 0) 1 else 0
    }

    /** Pengali prioritas Home Village untuk syarat gear-up (strategi builder ke-6). */
    private fun homeMult(c: Candidate, needs: Map<Int, Int>): Double {
        val need = needs[c.item.id] ?: return 1.0
        return if (c.toLevel <= need) 3.0 else 1.0
    }

    private fun sixthActive(v: Village, lane: Lane, ps: PlanSettings): Boolean =
        lane.bb && ps.bbStrategy == BBStrategy.SIXTH && !Sixth.done(v)

    private fun regular(
        state: Village, gd: GameData, lane: Lane, start: Long, ps: PlanSettings,
    ): Pair<List<Scored>, List<Candidate>> {
        val skipped = ArrayList<Candidate>()
        val scored = ArrayList<Scored>()
        val needs = if (lane == Lane.BUILDER && ps.bbStrategy == BBStrategy.SIXTH) Sixth.homeNeeds(state) else emptyMap()
        for (c in dedupe(candidates(state, gd, lane, ps.gold, includeHall = true, skip = ps.planSkip))) {
            val s = score(c, start, ps, homeMult(c, needs))
            if (c.item.cat == Cat.HERO && ps.cwl.overlap(s.start, s.finish) != null) {
                skipped += c
                continue
            }
            scored += s
        }
        val fitted = fitCapacity(state, gd, lane, start, ps, scored, tiered = false).sortedByDescending { it.score }
        return fitted to skipped
    }

    // ------------------------------------------------------------------ kapasitas gudang (Builder Base)

    /** Kapasitas [resource] bila storage yang sedang di-upgrade sudah selesai. */
    private fun plannedCap(v: Village, resource: String): Long? {
        val now = Capacity.of(v, resource) ?: return null
        val id = Capacity.storageFor(resource) ?: return now
        return now + v.insts.filter { it.item.id == id && it.upgrading }.sumOf { Capacity.gain(it.level, it.level + 1) }
    }

    /** Upgrade Star Lab berikutnya yang paling diutamakan (untuk menyiapkan kapasitas Elixir BB lebih awal). */
    private fun starLabNext(v: Village, gd: GameData, ps: PlanSettings, start: Long): Scored? {
        if (v.starLab <= 0) return null
        if (sixthActive(v, Lane.STAR_LAB, ps)) {
            val f = Sixth.focusTroop(v) ?: return null
            val to = f.level + if (f.upgrading) 2 else 1
            val lv = f.item.lv(to) ?: return null
            val c = Candidate(Lane.STAR_LAB, f.item, f, to - 1, to, ps.gold.apply(lv.time, Lane.STAR_LAB), lv.cost, false, baseDuration = lv.time)
            return score(c, start, ps, tier = Sixth.T_TROOP)
        }
        return candidates(v, gd, Lane.STAR_LAB, ps.gold, includeHall = false, skip = ps.planSkip)
            .map { score(it, start, ps) }.maxByOrNull { it.score }
    }

    /**
     * Upgrade Builder Base yang biayanya melebihi kapasitas gudang (Builder Hall + storage) tidak bisa
     * dimulai: dibuang dari daftar, dan storage yang menampung resource itu didahulukan — di strategi
     * builder ke-6 dengan urutan yang sama dengan langkah yang tertahan, selain itu skornya dikalikan.
     * Kebutuhan Star Lab (mis. Night Witch 3,7 jt Elixir BB) ikut dihitung untuk Master Builder.
     */
    private fun fitCapacity(
        state: Village, gd: GameData, lane: Lane, start: Long, ps: PlanSettings, list: List<Scored>, tiered: Boolean,
    ): List<Scored> {
        if (!lane.bb || state.bh <= 0) return list
        fun more(a: Scored, b: Scored) = if (tiered) a.tier < b.tier || (a.tier == b.tier && a.score > b.score) else a.score > b.score
        val ok = list.filter { !Capacity.blocked(state, it.c, it.cost) }
        if (lane != Lane.BB_BUILDER) return ok
        // Kebutuhan per resource: langkah terpenting yang tidak muat di gudang (setelah storage yang sedang naik selesai).
        val need = HashMap<String, Scored>()
        val wants = list.filter { Capacity.blocked(state, it.c, it.cost) } + listOfNotNull(starLabNext(state, gd, ps, start))
        for (w in wants) {
            val res = w.c.item.resource
            val cap = plannedCap(state, res) ?: continue
            if (w.cost <= cap) continue
            val cur = need[res]
            if (cur == null || more(w, cur)) need[res] = w
        }
        if (need.isEmpty()) return ok
        val ids = need.keys.mapNotNull { Capacity.storageFor(it) }.toSet()
        val boosted = candidates(state, gd, lane, ps.gold, includeHall = false, skip = ps.planSkip)
            .filter { it.item.id in ids }
            .mapNotNull { c ->
                val res = Capacity.storedBy(c.item.id) ?: return@mapNotNull null
                val w = need[res] ?: return@mapNotNull null
                val base = score(c, start, ps)
                if (Capacity.blocked(state, c, base.cost)) return@mapNotNull null
                val cap = Capacity.of(state, res) ?: 0
                val why = "📦 Gudang ${Capacity.short(res)} ${Fmt.money(cap)}, ${w.c.shortTitle} butuh ${Fmt.money(w.cost)}"
                // Star Lab yang tertahan gudang menganggur: storage-nya setara langkah Builder Hall.
                val tier = if (w.c.lane == Lane.STAR_LAB) minOf(w.tier, Sixth.T_HALL) else minOf(w.tier, Sixth.T_FILL)
                if (tiered) base.with(tier = tier, reason = why)
                else base.with(score = base.score * STORAGE_NEED, reason = why)
            }
        val rest = ok.filter { o -> boosted.none { b -> b.c.item.id == o.c.item.id && b.c.inst === o.c.inst } }
        return rest + boosted
    }

    private fun rank(
        state: Village, gd: GameData, lane: Lane, start: Long, ps: PlanSettings,
    ): Pair<List<Scored>, List<Candidate>> {
        val reg = regular(state, gd, lane, start, ps)
        if (!sixthActive(state, lane, ps)) return reg
        val six = fitCapacity(state, gd, lane, start, ps, Sixth.options(state, gd, lane, ps).map { (c, tier) -> score(c, start, ps, tier = tier) }, tiered = true)
            .sortedWith(compareBy<Scored> { it.tier }.thenByDescending { it.score })
        if (six.isEmpty()) return reg
        // Langkah builder ke-6 dulu; opsi lain tetap bisa dipilih di bawahnya.
        val rest = reg.first.filter { r -> six.none { it.c.sameAs(r.c) } }
        return (six + rest) to reg.second
    }

    private fun runTitle(i: Inst): String = when {
        i.gearing -> "${i.item.name} · Gear-up"
        i.level == 0 -> "Bangun ${i.item.name}"
        else -> "${i.item.name} → Lv ${i.level + 1}"
    }

    /**
     * Saran per pekerja: yang bebas sekarang, lalu beberapa yang akan bebas berikutnya.
     * [finishes] = waktu selesai terproyeksi (helper & boost) per uid timer.
     */
    fun suggest(
        v: Village, gd: GameData, ps: PlanSettings, now: Long, finishes: Map<String, Long>,
        upcoming: Int = 3,
    ): List<LaneSlot> {
        val slots = ArrayList<LaneSlot>()
        for (lane in Lane.entries) {
            val total = laneCount(v, lane, ps)
            if (total <= 0) continue
            val running = v.insts.filter { it.upgrading && it.lane == lane }.map { it to finishOf(v, it, finishes) }
            val unknownBusy = v.timers.count { t -> t.lane == lane && t.cat == null && (v.exportAt + t.remaining * 1000) > now }
            // Goblin Builder / Goblin Researcher ("extra") tidak memakai/membebaskan pekerja tetap.
            val stillRunning = running.filter { it.second > now && !it.first.extra }.sortedBy { it.second }
            val freeNow = (total - stillRunning.size - unknownBusy).coerceAtLeast(0)

            if (freeNow > 0) {
                val st = v.copy()
                completeUntil(st, finishes, now)
                Sixth.autoClaim(st)
                val (ranked, skipped) = rank(st, gd, lane, now, ps)
                slots += LaneSlot(lane, now, now, null, freeNow, ranked, skipped)
            }
            for ((inst, fin) in stillRunning.take(upcoming)) {
                val st = v.copy()
                completeUntil(st, finishes, fin)
                Sixth.autoClaim(st)
                val start = ps.schedule.nextOnline(fin)
                val (ranked, skipped) = rank(st, gd, lane, start, ps)
                slots += LaneSlot(lane, fin, start, runTitle(inst), 1, ranked, skipped)
            }
        }
        return slots
    }

    // ------------------------------------------------------------------ simulasi

    private class SimLane(val lane: Lane, val index: Int, var freeAt: Long, var retired: Boolean = false, var lastFinish: Long? = null)
    private class SimRun(val inst: Inst, var finish: Long, val laneRef: SimLane)

    /**
     * Simulasi maju: pekerja mengambil saran terbaik setiap kali bebas (hanya saat Anda online),
     * sampai semua upgrade pada level hall sekarang selesai. Town Hall / Builder Hall tidak ikut,
     * kecuali strategi builder ke-6 (Builder Hall 9 termasuk syaratnya).
     */
    fun simulate(
        v0: Village, gd: GameData, ps: PlanSettings, now: Long, finishes: Map<String, Long>,
        lanesWanted: Set<Lane> = Lane.entries.toSet(),
        maxSteps: Int = 4000,
        keepSteps: Int = 40,
    ): SimResult {
        val v = v0.copy()
        val sixthMode = ps.bbStrategy == BBStrategy.SIXTH && lanesWanted.any { it.bb } && v.bh > 0 && !Sixth.done(v)
        val lanes = ArrayList<SimLane>()
        val runs = ArrayList<SimRun>()
        val running = ArrayList<PlannedStep>()
        for (lane in Lane.entries) {
            if (lane !in lanesWanted) continue
            val n = laneCount(v, lane, ps)
            val all = v.insts.filter { it.upgrading && it.lane == lane }
            val busy = all.filter { !it.extra }
            val count = maxOf(n, busy.size)
            if (count == 0 && all.isEmpty()) continue
            val laneObjs = (0 until count).map { SimLane(lane, it + 1, now) }
            lanes += laneObjs
            // Goblin (event, dibayar gem): pekerjaannya tetap selesai walau event berakhir, tetapi tidak diberi
            // pekerjaan baru karena event & pemakaiannya tidak bisa diketahui.
            all.filter { it.extra }.forEachIndexed { k, inst ->
                val f = maxOf(finishOf(v, inst, finishes), now)
                val l = SimLane(lane, count + k + 1, f, retired = true, lastFinish = f)
                lanes += l
                runs += SimRun(inst, f, l)
                running += PlannedStep(
                    lane, l.index, runTitle(inst), now, f, 0, if (lane == Lane.LAB) "👺 Goblin Researcher (event)" else "👺 Goblin Builder (event)", (f - now) / 1000, inst.item,
                    inst.level, inst.level + 1, false, false, running = true,
                )
            }
            // B1 = pekerja yang paling cepat bebas
            busy.sortedBy { finishOf(v, it, finishes) }.forEachIndexed { idx, inst ->
                val f = maxOf(finishOf(v, inst, finishes), now)
                val l = laneObjs[idx]
                l.freeAt = f
                l.lastFinish = f
                runs += SimRun(inst, f, l)
                running += PlannedStep(
                    lane, l.index, runTitle(inst), now, f, 0, null, (f - now) / 1000, inst.item,
                    inst.level, if (inst.gearing) inst.level else inst.level + 1, inst.level == 0, inst.gearing, running = true,
                )
            }
        }
        // Helper harian
        class HelperSim(val lane: Lane, val level: Int, var next: Long)
        val helpers = ArrayList<HelperSim>()
        for ((id, lane) in listOf(GameData.APPRENTICE to Lane.BUILDER, GameData.LAB_ASSISTANT to Lane.LAB)) {
            if (lane !in lanesWanted) continue
            val h = v.helper(id) ?: continue
            if (h.level <= 0) continue
            var nx = v.exportAt + h.cooldown * 1000
            while (nx < now) nx += HELPER_CYCLE
            helpers += HelperSim(lane, h.level, nx)
        }

        val steps = ArrayList<PlannedStep>()
        val helperEvents = ArrayList<HelperEvent>()
        val horizon = now + 4L * 365 * DAY
        var count = 0
        var truncated = false
        var newSeq = 0
        var builderIdle = 0L
        val idleWindowEnd = now + 7 * DAY
        val homeNeeds = if (ps.bbStrategy == BBStrategy.SIXTH && Lane.BUILDER in lanesWanted) Sixth.homeNeeds(v) else emptyMap()

        // Kebutuhan sumber daya per rentang waktu (dihitung untuk semua langkah, bukan hanya yang disimpan)
        val windows = longArrayOf(DAY, 3 * DAY, 7 * DAY)
        val needCost = Array(windows.size) { HashMap<String, Long>() }
        val needTop = Array(windows.size) { HashMap<String, NeedItem>() }
        fun addNeed(s: Scored) {
            if (s.cost <= 0) return
            val r = s.c.item.resource.ifBlank { return }
            for (w in windows.indices) {
                if (s.start >= now + windows[w]) continue
                needCost[w][r] = (needCost[w][r] ?: 0L) + s.cost
                val top = needTop[w][r]
                if (top == null || s.cost > top.cost) needTop[w][r] = NeedItem(s.c.shortTitle, s.cost, s.start, s.c.item)
            }
        }

        // Jalur builder ke-6
        val sixthEta = HashMap<String, Long>()
        var sixthDoneAt: Long? = null
        if (sixthMode) Sixth.autoClaim(v)
        val initiallyDone = if (sixthMode) Sixth.status(v).filter { it.done }.map { it.key }.toSet() else emptySet()
        val sixthStart = if (sixthMode) Sixth.status(v) else emptyList()

        fun trackSixth(at: Long) {
            if (!sixthMode) return
            Sixth.autoClaim(v)
            for (r in Sixth.status(v)) {
                if (r.done && r.key !in initiallyDone && r.key !in sixthEta) sixthEta[r.key] = at
            }
            if (sixthDoneAt == null && Sixth.done(v)) sixthDoneAt = at
        }

        fun applyCompletions(t: Long) {
            val due = runs.filter { it.finish <= t }.sortedBy { it.finish }
            for (r in due) {
                r.inst.complete()
                runs.remove(r)
                trackSixth(r.finish)
            }
        }

        while (true) {
            val active = lanes.filter { !it.retired }
            if (active.isEmpty()) break
            if (count++ > maxSteps) { truncated = true; break }

            val nextLane = active.minByOrNull { if (it.freeAt <= now) now else ps.schedule.nextOnline(it.freeAt) }!!
            val tStart = if (nextLane.freeAt <= now) now else ps.schedule.nextOnline(nextLane.freeAt)
            if (tStart > horizon) { truncated = true; break }

            val h = helpers.minByOrNull { it.next }
            if (h != null && h.next < tStart) {
                val target = runs.filter { it.laneRef.lane == h.lane && it.finish > h.next && !it.inst.gearing }.maxByOrNull { it.finish }
                if (target != null) {
                    if (helperEvents.size < keepSteps) helperEvents += HelperEvent(h.next, h.lane, h.level, target.inst.item.name)
                    val rem = target.finish - h.next
                    val boost = h.level * HOUR
                    target.finish = if (rem <= boost + HOUR) h.next + rem / (h.level + 1) else target.finish - boost
                    target.laneRef.freeAt = target.finish
                    target.laneRef.lastFinish = target.finish
                }
                h.next += HELPER_CYCLE
                continue
            }

            applyCompletions(tStart)
            if (sixthMode && sixthDoneAt != null) {
                // B.O.B sudah terbuka: rencana builder ke-6 selesai.
                lanes.filter { it.lane.bb }.forEach { it.retired = true }
                continue
            }

            val lane = nextLane.lane
            var best: Scored? = null
            var bestBlocked: Scored? = null
            var cwlBlocked: Long? = null
            if (sixthMode && lane.bb) {
                val opts = Sixth.options(v, gd, lane, ps).map { (c, tier) -> score(c, tStart, ps, tier = tier) }
                for (s in fitCapacity(v, gd, lane, tStart, ps, opts, tiered = true)) {
                    val b = best
                    if (b == null || s.tier < b.tier || (s.tier == b.tier && s.score > b.score)) best = s
                }
            } else {
                val opts = candidates(v, gd, lane, ps.gold, includeHall = false, skip = ps.planSkip).map { score(it, tStart, ps, homeMult(it, homeNeeds)) }
                for (s in fitCapacity(v, gd, lane, tStart, ps, opts, tiered = false)) {
                    val c = s.c
                    if (c.item.cat == Cat.HERO) {
                        val w = ps.cwl.overlap(s.start, s.finish)
                        if (w != null) {
                            cwlBlocked = minOf(cwlBlocked ?: Long.MAX_VALUE, w.last + 1)
                            if (bestBlocked == null || s.score > bestBlocked.score) bestBlocked = s
                            continue
                        }
                    }
                    val b = best
                    if (b == null || s.score > b.score) best = s
                }
            }

            val pick = best
            if (pick != null) {
                if (lane == Lane.BUILDER && nextLane.freeAt < idleWindowEnd) {
                    builderIdle += (minOf(tStart, idleWindowEnd) - maxOf(nextLane.freeAt, now)).coerceAtLeast(0)
                }
                val c = pick.c
                val inst = c.inst ?: Inst("sim|new|${c.item.id}|${newSeq++}", c.item, c.fromLevel, false, 0, false, false)
                    .also { v.insts += it }
                inst.upgrading = true
                if (c.gear) inst.gearing = true
                runs += SimRun(inst, pick.finish, nextLane)
                nextLane.freeAt = pick.finish
                nextLane.lastFinish = pick.finish
                addNeed(pick)
                if (steps.size < keepSteps) {
                    val blocked = bestBlocked
                    val note = blocked?.takeIf { it.score > pick.score }?.let { "⚔️ ${it.c.item.name} ditunda (CWL)" } ?: pick.reason
                    steps += PlannedStep(
                        lane, nextLane.index, c.shortTitle, pick.start, pick.finish, pick.idle, note, pick.duration,
                        c.item, c.fromLevel, c.toLevel, c.isNew, c.gear, false, pick.cost, pick.event?.name, pick.tier,
                    )
                }
            } else {
                val waitFor = (runs.filter { it.finish > tStart }.map { it.finish } + listOfNotNull(cwlBlocked)).minOrNull()
                if (waitFor == null) nextLane.retired = true else nextLane.freeAt = waitFor
            }
        }

        fun doneFor(lane: Lane): Long? {
            val ls = lanes.filter { it.lane == lane }
            if (ls.isEmpty()) return null
            return ls.mapNotNull { it.lastFinish }.maxOrNull() ?: now
        }
        val sixth = if (sixthMode) {
            SixthPlan(sixthStart, sixthEta, sixthDoneAt, if (sixthDoneAt == null) Sixth.blockers(v) else emptyList())
        } else null
        val needs = windows.indices.map { Needs(windows[it], needCost[it], needTop[it]) }
        return SimResult(
            doneFor(Lane.BUILDER), doneFor(Lane.LAB), doneFor(Lane.PET),
            doneFor(Lane.BB_BUILDER), doneFor(Lane.STAR_LAB), steps, builderIdle, truncated, helperEvents,
            running, sixth, needs,
        )
    }
}
