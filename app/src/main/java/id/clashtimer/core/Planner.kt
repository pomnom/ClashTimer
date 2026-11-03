package id.clashtimer.core

import kotlin.math.sqrt

enum class Strategy(private val labelId: String, private val descId: String, private val targetId: String) {
    MAX("Seimbang (max TH)", "Target: semua upgrade di TH sekarang (builder, lab & pet) selesai secepatnya; Town Hall berikutnya dimulai begitu builder kehabisan pekerjaan", "max TH"),
    RUSH("Rush TH", "Target: Town Hall berikutnya secepatnya — bangunan baru dipasang dulu, lalu TH; sisanya offense", "Town Hall naik"),
    HERO("Hero dulu", "Target: semua level hero di TH sekarang selesai secepatnya; builder lain mengisi sisanya", "hero max");

    val label: String get() = if (!Lang.en) labelId else when (this) {
        MAX -> "Balanced (max TH)"
        RUSH -> "Rush TH"
        HERO -> "Heroes first"
    }
    val desc: String get() = if (!Lang.en) descId else when (this) {
        MAX -> "Target: every upgrade at the current TH (builders, lab & pets) done as soon as possible; the next Town Hall starts as soon as builders run out of work"
        RUSH -> "Target: the next Town Hall as soon as possible — new buildings first, then the TH; the rest is offense"
        HERO -> "Target: every hero level at the current TH done as soon as possible; other builders fill the rest"
    }
    val target: String get() = if (!Lang.en) targetId else when (this) {
        MAX -> "max TH"
        RUSH -> "next Town Hall"
        HERO -> "max heroes"
    }
}

/**
 * Cara mengurutkan upgrade. Rencana mencoba semuanya lalu memakai yang paling cepat mencapai target
 * strategi ([Planner.optimize]); saran & widget memakai urutan yang sama.
 */
enum class Policy(val label: String) {
    /** Cara lama: nilai upgrade dibagi √(lama × biaya). */
    VALUE("nilai per waktu & biaya"),
    /** Rantai upgrade terpanjang dulu (sisa semua level item itu), memperhitungkan jam tidur. */
    CHAIN("rantai upgrade terpanjang dulu"),
    /** Upgrade terlama dulu, memperhitungkan jam tidur. */
    LPT("upgrade terlama dulu"),
    /** Rantai terpanjang dulu tanpa memperhitungkan jam tidur. */
    CHAIN_STRICT("rantai terpanjang, urutan murni"),
    /** Rantai terpanjang, tapi pekerja yang akan menganggur saat tidur sangat dihindari (pas jam tidur). */
    FIT("pas jam tidur"),
    /** Builder ke-6: syarat B.O.B yang paling lama sisanya dikerjakan dulu (jalur kritis). Selain itu = rantai. */
    CRITICAL("jalur kritis");
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
    /** Urutan tercepat per pekerja hasil [Planner.optimize] (tidak ada = cara lama). */
    val policies: Map<Lane, Policy> = emptyMap(),
) {
    fun policyFor(lane: Lane): Policy = policies[lane] ?: Policy.VALUE

    /** Id item yang tidak di-upgrade dan tidak dihitung di progres (collector). */
    val skipIds: Set<Int> = if (skipCollectors) Planner.COLLECTORS else emptySet()

    /** Id item yang tidak disarankan: collector (bila dipilih) + item yang disembunyikan. */
    val planSkip: Set<Int> = skipIds + hideIds

    fun withBB(s: BBStrategy) = PlanSettings(schedule, cwl, gold, strategy, bbBuilders, s, skipCollectors, events, pinIds, hideIds, policies)

    /** Salinan dengan Gold Pass / strategi / event lain (untuk simulasi). */
    fun with(gold: GoldPass = this.gold, strategy: Strategy = this.strategy, events: List<GameEvent> = this.events) =
        PlanSettings(schedule, cwl, gold, strategy, bbBuilders, bbStrategy, skipCollectors, events, pinIds, hideIds, policies)

    /** Salinan dengan urutan lain untuk pekerja tertentu (sisanya tetap). */
    fun withPolicies(set: Map<Lane, Policy>) =
        PlanSettings(schedule, cwl, gold, strategy, bbBuilders, bbStrategy, skipCollectors, events, pinIds, hideIds, policies + set)

    fun event(lane: Lane, t: Long): GameEvent? = Events.at(events, lane, t)

    /** Durasi (detik) bila dimulai pada [t]: Gold Pass + potongan event bila sedang berlangsung. */
    fun durationAt(c: Candidate, t: Long): Long {
        if (c.gear) return c.duration
        val ev = event(c.lane, t)?.timePct ?: 0
        // Gold Pass bisa naik di tengah musim (15% / 20% setelah poin tercapai).
        val g = gold.at(t)
        return if (ev == 0 && g.pctFor(c.lane) == gold.pctFor(c.lane)) c.duration else g.apply(c.baseDuration, c.lane, ev)
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
    /** Detik: level ini + semua level berikutnya item ini yang masih bisa di hall sekarang (Gold Pass). */
    val chain: Long = duration,
    /** Laboratory / Pet House / Hero Hall / Star Lab: naik level membuka level riset, pet atau hero. */
    val gate: Boolean = false,
) {
    val title: String
        get() = when {
            gear -> "${item.name} · Gear-up"
            isNew && (item.cat == Cat.HERO || item.cat == Cat.BB_HERO || item.cat == Cat.PET) -> tr("Buka ${item.name}", "Unlock ${item.name}")
            isNew -> tr("Bangun ${item.name}", "Build ${item.name}") + if (count > 1) tr(" (sisa $count)", " ($count left)") else ""
            else -> "${item.name} → Lv $toLevel" + if (count > 1) "  ×$count" else ""
        }

    /** Judul tanpa jumlah. */
    val shortTitle: String get() = title.substringBefore("  ×").substringBefore(" (sisa").substringBefore(" (").takeIf { it.isNotBlank() } ?: title

    /** Keterangan level, mis. "Lv 17 → 18". */
    val levelLabel: String
        get() = when {
            gear -> "Gear-up · Home Village"
            isNew && fromLevel == 0 -> tr("Bangun baru", "New building")
            isNew -> tr("Buka di Lv $toLevel", "Unlocks at Lv $toLevel")
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
    /** Level hero terakhir di hall sekarang selesai (null = tidak ada hero yang di-upgrade). */
    val heroDone: Long? = null,
    /** Town Hall berikutnya selesai (strategi Rush TH). */
    val hallDone: Long? = null,
    /** Hero yang ditunda karena CWL (tidak dimasukkan ke antrean selama CWL). */
    val cwlDeferred: List<String> = emptyList(),
)

/** Ringkasan urutan tercepat untuk ditampilkan & disimpan (tanpa hasil simulasinya). */
class PlanTarget(
    /** Tanggal target saat dihitung (null = tidak tercapai). */
    val target: Long?,
    /** Tanggal target dengan cara lama. */
    val legacy: Long?,
    /** Jumlah simulasi yang dicoba. */
    val tried: Int,
    val policies: Map<Lane, Policy>,
)

/** Hasil [Planner.optimize]: simulasi dengan urutan tercepat + perbandingannya. */
class Optimized(
    val sim: SimResult,
    /** Urutan terpilih per pekerja. */
    val policies: Map<Lane, Policy>,
    /** Tanggal target (null = tidak tercapai). */
    val target: Long?,
    /** Tanggal target dengan cara lama (nilai per waktu & biaya). */
    val legacyTarget: Long?,
    /** Jumlah cara yang dicoba. */
    val tried: Int,
) {
    fun summary() = PlanTarget(target, legacyTarget, tried, policies)
}

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
        // Efisiensi = bagian waktu pekerja yang benar-benar dipakai. Dikuadratkan supaya
        // sebelum tidur upgrade panjang (yang selesai saat Anda bangun) lebih diutamakan
        // daripada upgrade singkat yang membuat builder menganggur semalaman.
        val eff = if (d + idle <= 0) 1.0 else d.toDouble() / (d + idle).toDouble()
        val policy = ps.policyFor(c.lane)
        val choice = if (policy == Policy.VALUE) {
            val costM = costValue(cost, c.item.resource) / 1_000_000.0
            val pin = if (c.item.id in ps.pinIds) PIN_BOOST else 1.0
            val hero = if (c.item.cat == Cat.HERO && !c.gear) HERO_IDLE_BOOST else 1.0
            importance(c, ps.strategy) * mult * pin * hero / sqrt((hours + 0.25) * (costM + 0.05)) * eff * eff
        } else {
            // Waktu tercepat ke target: bobot target × lama rantai (atau lama upgrade ini).
            val pin = if (c.item.id in ps.pinIds) PIN_TARGET else 1.0
            val rushPlace = !c.lane.bb && ps.strategy == Strategy.RUSH && isPlacement(c)
            val size = when {
                rushPlace || policy == Policy.LPT -> hours
                else -> c.chain / 3600.0
            } + 0.05
            val base = targetWeight(c, ps) * mult * pin * size
            when (policy) {
                Policy.CHAIN_STRICT -> base
                Policy.FIT -> base * Math.pow(eff, 12.0)
                else -> base * eff * eff
            }
        }
        val ev = if (c.gear) null else ps.event(c.lane, start)
        return Scored(c, start, finish, idle, choice, dur, cost, ev, tier)
    }

    /** Semua hero dijalankan bersamaan di strategi Seimbang. */
    @JvmField var HERO_PARALLEL = true

    /** Seimbang: Town Hall berikutnya dimulai saat builder kehabisan pekerjaan sementara lab masih jalan. */
    @JvmField var EARLY_HALL = true

    /** Pengali item yang diprioritaskan pengguna pada urutan waktu-tercepat. */
    const val PIN_TARGET = 1e4

    private fun isPlacement(c: Candidate) = c.isNew && (c.item.cat == Cat.BUILDING || c.item.cat == Cat.TRAP)

    /**
     * Bobot target strategi (urutan waktu-tercepat): upgrade yang menentukan tanggal target didahulukan,
     * sisanya diurutkan menurut lamanya.
     */
    private fun targetWeight(c: Candidate, ps: PlanSettings): Double {
        val it = c.item
        if (c.lane.bb) return when {
            it.isTownHall -> 1e-6
            c.gate -> 1e3
            else -> 1.0
        }
        return when (ps.strategy) {
            Strategy.MAX -> when {
                it.isTownHall -> 1e-6
                c.gate -> 1e3
                // Tiap hero hanya bisa di-upgrade satu level sekaligus: semua hero dijalankan bersamaan.
                it.cat == Cat.HERO && !c.gear && HERO_PARALLEL -> 1e2
                else -> 1.0
            }
            Strategy.HERO -> when {
                it.isTownHall -> 1e-6
                it.id == GameData.HERO_HALL -> 1e5
                it.cat == Cat.HERO && !c.gear -> 1e4
                c.gate -> 1e2
                else -> 1.0
            }
            Strategy.RUSH -> when {
                it.isTownHall -> 1e8
                isPlacement(c) -> 1e6
                else -> importance(c, Strategy.RUSH)
            }
        }
    }

    /** Pengali syarat gear-up (builder ke-6) & storage yang tertahan kapasitas, menurut cara mengurutkan. */
    private fun needBoost(ps: PlanSettings, lane: Lane, legacy: Double): Double =
        if (ps.policyFor(lane) == Policy.VALUE) legacy else 1e5

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

    private val GATES = setOf(GameData.LAB, GameData.PET_HOUSE, GameData.HERO_HALL, GameData.STAR_LAB)

    /**
     * Lama (detik, Gold Pass) level [from]+1 dan semua level sesudahnya yang masih bisa di hall sekarang,
     * termasuk level yang butuh Laboratory / Pet House / Hero Hall / Star Lab lebih tinggi (yang juga
     * akan di-upgrade di hall ini).
     */
    /** Hasil [chainOf] (data game tetap; kunci = item, level, hall, Gold Pass). */
    private val chainMemo = java.util.concurrent.ConcurrentHashMap<Long, Long>()

    private fun chainOf(v: Village, gd: GameData, item: Item, from: Int, lane: Lane, gold: GoldPass): Long {
        val hall = if (lane.bb) v.bh else v.th
        val key = (((item.id.toLong() * 512 + from) * 64 + hall) * 128 + gold.pctFor(lane)) * 2 + if (lane.bb) 1 else 0
        chainMemo[key]?.let { return it }
        return chainCalc(gd, item, from, lane, gold, hall).also { if (chainMemo.size < 50_000) chainMemo[key] = it }
    }

    private fun chainCalc(gd: GameData, item: Item, from: Int, lane: Lane, gold: GoldPass, hall: Int): Long {
        val reqCap = when (item.cat) {
            Cat.TROOP, Cat.SPELL -> gd.item(GameData.LAB)?.maxLevelFor(hall)
            Cat.PET -> gd.item(GameData.PET_HOUSE)?.maxLevelFor(hall)
            Cat.HERO -> gd.item(GameData.HERO_HALL)?.maxLevelFor(hall)?.takeIf { it > 0 }
            Cat.BB_TROOP -> gd.item(GameData.STAR_LAB)?.maxLevelFor(hall)
            else -> null
        }
        var sum = 0L
        for (l in item.levels.sortedBy { it.level }) {
            if (l.level <= from) continue
            if (l.time <= 0) break
            if (item.cat != Cat.BB_TROOP && l.hall > hall) break
            if (reqCap != null && l.req > reqCap) break
            sum += gold.apply(l.time, lane)
        }
        return sum
    }

    fun candidates(
        v: Village, gd: GameData, lane: Lane, gold: GoldPass, includeHall: Boolean,
        skip: Set<Int> = emptySet(),
    ): List<Candidate> = candidatesRaw(v, gd, lane, gold, includeHall, skip).map { c ->
        if (c.gear) c
        else Candidate(c.lane, c.item, c.inst, c.fromLevel, c.toLevel, c.duration, c.cost, c.isNew, c.count, c.gear, c.baseDuration,
            maxOf(c.duration, chainOf(v, gd, c.item, c.fromLevel, lane, gold)), c.item.id in GATES)
    }

    private fun candidatesRaw(
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
                // Army Camp / Reinforcement Camp BB: waktu & biaya menurut bangunan ke berapa.
                val lv1 = item.build(v.count(id) + 1) ?: continue
                if (lv1.hall > hall) continue
                val missing = allowed(v, gd, id, hall, bb) - v.count(id)
                if (missing <= 0) continue
                // Kamp pasukan tidak menahan Builder Hall (bukan syarat naik hall).
                if (item.instances.isEmpty()) placements++
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
                if (c.isNew) c.count else prev.count + 1, c.gear, c.baseDuration, maxOf(c.chain, prev.chain), c.gate || prev.gate)
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
    private fun homeMult(c: Candidate, needs: Map<Int, Int>, ps: PlanSettings): Double {
        val need = needs[c.item.id] ?: return 1.0
        return if (c.toLevel <= need) needBoost(ps, c.lane, 3.0) else 1.0
    }

    /** Builder ke-6 dikejar dulu: strategi Builder ke-6, dan juga Max (B.O.B mempercepat sisa Builder Base). */
    private fun sixthActive(v: Village, lane: Lane, ps: PlanSettings): Boolean =
        lane.bb && (ps.bbStrategy == BBStrategy.SIXTH || ps.bbStrategy == BBStrategy.MAX && v.bh >= 9) && !Sixth.done(v)

    private fun regular(
        state: Village, gd: GameData, lane: Lane, start: Long, ps: PlanSettings,
    ): Pair<List<Scored>, List<Candidate>> {
        val skipped = ArrayList<Candidate>()
        val scored = ArrayList<Scored>()
        val needs = if (lane == Lane.BUILDER && ps.bbStrategy == BBStrategy.SIXTH) Sixth.homeNeeds(state) else emptyMap()
        for (c in dedupe(candidates(state, gd, lane, ps.gold, includeHall = true, skip = ps.planSkip))) {
            val s = score(c, start, ps, homeMult(c, needs, ps))
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
        val labWaits = HashSet<String>()
        val wants = list.filter { Capacity.blocked(state, it.c, it.cost) } + listOfNotNull(starLabNext(state, gd, ps, start))
        for (w in wants) {
            val res = w.c.item.resource
            val cap = plannedCap(state, res) ?: continue
            if (w.cost <= cap) continue
            val cur = need[res]
            if (cur == null || more(w, cur)) need[res] = w
            if (w.c.lane == Lane.STAR_LAB) labWaits += res
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
                val why = tr("📦 Gudang ${Capacity.short(res)} ${Fmt.money(cap)}, ${w.c.shortTitle} butuh ${Fmt.money(w.cost)}", "📦 ${Capacity.short(res)} storage ${Fmt.money(cap)}, ${w.c.shortTitle} needs ${Fmt.money(w.cost)}")
                // Star Lab yang tertahan gudang akan menganggur: storage-nya paling didahulukan.
                val labWait = res in labWaits
                val tier = if (labWait) Sixth.T_UNBLOCK else minOf(w.tier, Sixth.T_FILL)
                if (tiered) base.with(tier = tier, reason = why)
                else base.with(score = base.score * needBoost(ps, lane, STORAGE_NEED) * (if (labWait) 1e4 else 1.0), reason = why)
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
        i.level == 0 -> tr("Bangun ${i.item.name}", "Build ${i.item.name}")
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

    /**
     * Upgrade hero yang bisa dimulai begitu builder bebas (sekarang atau builder tercepat), dengan jam
     * selesainya — untuk mengecek hero mana yang sempat selesai sebelum serangan war berikutnya.
     */
    fun heroOptions(v: Village, gd: GameData, ps: PlanSettings, now: Long, finishes: Map<String, Long>): List<Scored> {
        val st = v.copy()
        completeUntil(st, finishes, now)
        val total = laneCount(v, Lane.BUILDER, ps)
        val busy = v.insts.filter { it.upgrading && it.lane == Lane.BUILDER && !it.extra }
            .map { finishOf(v, it, finishes) }.filter { it > now }.sorted()
        val freeAt = if (busy.size < total) now else busy.first()
        // Builder bebas sekarang: Anda sedang membuka aplikasi, jadi bisa langsung dimulai.
        val start = if (freeAt <= now) now else ps.schedule.nextOnline(freeAt)
        return candidates(st, gd, Lane.BUILDER, ps.gold, includeHall = false, skip = ps.planSkip)
            .filter { it.item.cat == Cat.HERO && !it.gear && !it.isNew }
            .map { score(it, start, ps) }
            .sortedBy { it.finish }
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
        // Max BB: B.O.B dikejar dulu (builder ke-6 mempercepat semuanya), lalu lanjut max dengan 4 Master Builder.
        // (hanya di Builder Hall 9: di bawahnya B.O.B butuh Builder Hall naik, di luar target "max BH sekarang").
        val maxAfterSixth = ps.bbStrategy == BBStrategy.MAX && lanesWanted.any { it.bb } && v.bh >= 9 && !Sixth.done(v)
        val sixthMode = (ps.bbStrategy == BBStrategy.SIXTH || maxAfterSixth) && lanesWanted.any { it.bb } && v.bh > 0 && !Sixth.done(v)
        var sixthPicking = sixthMode
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

        val startRunning = runs.map { it.inst }.toHashSet()
        val steps = ArrayList<PlannedStep>()
        val helperEvents = ArrayList<HelperEvent>()
        val horizon = now + 4L * 365 * DAY
        var count = 0
        var truncated = false
        var newSeq = 0
        var builderIdle = 0L
        val idleWindowEnd = now + 7 * DAY
        val homeNeeds = if (ps.bbStrategy == BBStrategy.SIXTH && Lane.BUILDER in lanesWanted) Sixth.homeNeeds(v) else emptyMap()
        // Rush TH: Town Hall ikut direncanakan (sekali); level desa lain tetap dihitung di TH sekarang.
        val rushHall = ps.strategy == Strategy.RUSH && Lane.BUILDER in lanesWanted
        var hallDone: Long? = null
        // Seimbang: Town Hall berikutnya dimulai begitu builder kehabisan pekerjaan sementara lab masih jalan.
        val earlyHallOn = EARLY_HALL && ps.strategy == Strategy.MAX && Lane.BUILDER in lanesWanted &&
            (Lane.LAB in lanesWanted || Lane.PET in lanesWanted)
        var earlyHall: Inst? = null
        var heroDone: Long? = null
        val cwlDeferred = LinkedHashSet<String>()
        // Hanya CWL terdekat yang dilaporkan (CWL bulan berikutnya belum relevan).
        val nearCwlEnd = if (ps.cwl.enabled) ps.cwl.next(now).last else Long.MIN_VALUE

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
                val item = r.inst.item
                if (r.inst === earlyHall) {
                    // Town Hall lebih awal: sisa rencana tetap dihitung di level TH sekarang.
                    r.inst.upgrading = false
                    r.inst.remaining = 0
                } else if (item.id == GameData.TOWN_HALL && rushHall && hallDone == null) {
                    hallDone = r.finish
                    if (r.inst in startRunning) r.inst.complete()
                    else {
                        // Hasil Rush: tanggal TH naik. Sisa rencana tetap dihitung di level TH sekarang.
                        r.inst.upgrading = false
                        r.inst.remaining = 0
                    }
                } else r.inst.complete()
                if (item.cat == Cat.HERO && !r.inst.gearing) heroDone = maxOf(heroDone ?: 0L, r.finish)
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
            if (sixthPicking && sixthDoneAt != null) {
                if (maxAfterSixth) {
                    // B.O.B terbuka: Master Builder ke-4 ikut bekerja, sisa Builder Base di-max seperti biasa.
                    sixthPicking = false
                    if (Lane.BB_BUILDER in lanesWanted) {
                        val idx = (lanes.filter { it.lane == Lane.BB_BUILDER }.maxOfOrNull { it.index } ?: 0) + 1
                        lanes += SimLane(Lane.BB_BUILDER, idx, maxOf(sixthDoneAt!!, tStart))
                    }
                } else {
                    // B.O.B sudah terbuka: rencana builder ke-6 selesai.
                    lanes.filter { it.lane.bb }.forEach { it.retired = true }
                }
                continue
            }

            val lane = nextLane.lane
            var best: Scored? = null
            var cwlBlocked: Long? = null
            if (sixthPicking && lane.bb) {
                val opts = Sixth.options(v, gd, lane, ps).map { (c, tier) -> score(c, tStart, ps, tier = tier) }
                for (s in fitCapacity(v, gd, lane, tStart, ps, opts, tiered = true)) {
                    val b = best
                    if (b == null || s.tier < b.tier || (s.tier == b.tier && s.score > b.score)) best = s
                }
            } else {
                val hall = rushHall && lane == Lane.BUILDER && hallDone == null && runs.none { it.inst.item.id == GameData.TOWN_HALL }
                val opts = candidates(v, gd, lane, ps.gold, includeHall = hall, skip = ps.planSkip).map { score(it, tStart, ps, homeMult(it, homeNeeds, ps)) }
                for (s in fitCapacity(v, gd, lane, tStart, ps, opts, tiered = false)) {
                    val c = s.c
                    if (c.item.cat == Cat.HERO) {
                        val w = ps.cwl.overlap(s.start, s.finish)
                        if (w != null) {
                            cwlBlocked = minOf(cwlBlocked ?: Long.MAX_VALUE, w.last + 1)
                            if (w.first <= nearCwlEnd) cwlDeferred += c.item.name
                            continue
                        }
                    }
                    val b = best
                    if (b == null || s.score > b.score) best = s
                }
            }

            var early = false
            if (best == null && earlyHallOn && earlyHall == null && lane == Lane.BUILDER && cwlBlocked == null &&
                runs.none { it.inst.item.id == GameData.TOWN_HALL } &&
                lanes.any { (it.lane == Lane.LAB || it.lane == Lane.PET) && !it.retired }
            ) {
                candidates(v, gd, lane, ps.gold, includeHall = true, skip = ps.planSkip).firstOrNull { it.item.isTownHall }?.let { th ->
                    best = score(th, tStart, ps).with(reason = tr("Builder sudah tidak ada pekerjaan di TH${v.th}, lab masih menyelesaikan riset — Town Hall dimulai lebih awal", "Builders have no more TH${v.th} work while the lab finishes research — the Town Hall starts early"))
                    early = true
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
                // Town Hall lebih awal tidak dihitung ke "builder selesai" (target tetap max TH sekarang).
                if (early) earlyHall = inst else nextLane.lastFinish = pick.finish
                addNeed(pick)
                if (steps.size < keepSteps) {
                    // Hero yang ditunda karena CWL tidak disebut di tiap langkah (lihat SimResult.cwlDeferred).
                    steps += PlannedStep(
                        lane, nextLane.index, c.shortTitle, pick.start, pick.finish, pick.idle, pick.reason, pick.duration,
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
        // Upgrade yang masih berjalan saat simulasi berhenti.
        for (r in runs) {
            if (r.inst.item.cat == Cat.HERO && !r.inst.gearing) heroDone = maxOf(heroDone ?: 0L, r.finish)
            if (r.inst.item.id == GameData.TOWN_HALL && rushHall && hallDone == null) hallDone = r.finish
        }
        return SimResult(
            doneFor(Lane.BUILDER), doneFor(Lane.LAB), doneFor(Lane.PET),
            doneFor(Lane.BB_BUILDER), doneFor(Lane.STAR_LAB), steps, builderIdle, truncated, helperEvents,
            running, sixth, needs, heroDone, hallDone, cwlDeferred.toList(),
        )
    }

    // ------------------------------------------------------------------ urutan tercepat

    private const val H = 3_600_000L

    /** Tanggal target strategi dari hasil simulasi (Long.MAX_VALUE = tidak tercapai). */
    fun target(s: SimResult, ps: PlanSettings, bb: Boolean): Long {
        if (s.truncated) return Long.MAX_VALUE
        val t = if (bb) {
            if (ps.bbStrategy == BBStrategy.SIXTH && s.sixth != null) s.sixth.doneAt
            else listOfNotNull(s.bbBuilderDone, s.starLabDone).maxOrNull()
        } else when (ps.strategy) {
            Strategy.MAX -> listOfNotNull(s.builderDone, s.labDone, s.petDone).maxOrNull()
            Strategy.HERO -> s.heroDone ?: s.builderDone
            Strategy.RUSH -> s.hallDone ?: s.builderDone
        }
        return t ?: Long.MAX_VALUE
    }

    /** Pembanding kedua bila tanggal target sama: pekerja utama selesai lebih awal. */
    private fun secondary(s: SimResult, bb: Boolean): List<Long> = if (bb) {
        listOf(listOfNotNull(s.bbBuilderDone, s.starLabDone).maxOrNull() ?: Long.MAX_VALUE, s.bbBuilderDone ?: Long.MAX_VALUE)
    } else {
        listOf(s.builderDone ?: Long.MAX_VALUE, s.heroDone ?: Long.MAX_VALUE, s.labDone ?: Long.MAX_VALUE)
    }

    /** Ukuran satu jenis pekerja: bagian target yang ditentukan pekerja itu, lalu kapan pekerja itu selesai. */
    private fun laneKey(s: SimResult, ps: PlanSettings, lane: Lane): List<Long> {
        val m = Long.MAX_VALUE
        val bob = s.sixth?.takeIf { ps.bbStrategy == BBStrategy.SIXTH }?.let { it.doneAt ?: m }
        return when (lane) {
            Lane.BUILDER -> when (ps.strategy) {
                Strategy.MAX -> listOf(s.builderDone ?: m, s.heroDone ?: m)
                Strategy.HERO -> listOf(s.heroDone ?: s.builderDone ?: m, s.builderDone ?: m)
                Strategy.RUSH -> listOf(s.hallDone ?: m, s.builderDone ?: m)
            }
            Lane.LAB -> listOf(s.labDone ?: m)
            Lane.PET -> listOf(s.petDone ?: m)
            Lane.BB_BUILDER -> listOfNotNull(bob, s.bbBuilderDone ?: m)
            Lane.STAR_LAB -> listOfNotNull(bob, s.starLabDone ?: m)
        }
    }

    private fun hours(k: List<Long>) = k.map { if (it == Long.MAX_VALUE) it else it / H }

    /**
     * Menganggur yang bisa dihindari dalam 7 hari: jeda antara dua langkah rencana pada satu pekerja
     * (jeda setelah upgrade yang sudah berjalan, dan pekerja yang sudah tidak punya pekerjaan, tidak dihitung).
     * Hasil: [jeda terlama (per 30 menit), total (per jam)].
     */
    fun idleKey(s: SimResult, lanes: Set<Lane>, now: Long): List<Long> {
        val (longest, total) = idleStats(s, lanes, now)
        return listOf(longest / (30 * 60_000L), total / H)
    }

    fun idleStats(s: SimResult, lanes: Set<Lane>, now: Long): Pair<Long, Long> {
        val horizon = now + 7 * DAY
        var longest = 0L
        var total = 0L
        for ((key, list) in (s.running + s.steps).groupBy { it.lane to it.laneIndex }) {
            if (key.first !in lanes || list.any { it.note?.startsWith("👺") == true }) continue
            var cursor = now
            var prevRunning = true
            for (st in list.sortedBy { it.start }) {
                if (st.start >= horizon) break
                if (!st.running && !prevRunning) {
                    val gap = st.start - cursor
                    if (gap > 30 * 60_000L) {
                        total += gap
                        if (gap > longest) longest = gap
                    }
                }
                cursor = maxOf(cursor, st.finish)
                prevRunning = st.running
            }
        }
        return longest to total
    }

    private val keyOrder = Comparator<List<Long>> { a, b ->
        for (i in 0 until minOf(a.size, b.size)) { val c = a[i].compareTo(b[i]); if (c != 0) return@Comparator c }
        a.size.compareTo(b.size)
    }

    /**
     * Cari urutan yang paling cepat mencapai target strategi terpilih (Seimbang: semua selesai; Rush: TH
     * naik; Hero: hero max; BB: B.O.B / BB max) dengan jam tidur, Gold Pass, event, CWL, helper & kapasitas
     * gudang yang sama: simulasikan tiap [Policy] untuk semua pekerja, pilih yang terbaik per jenis pekerja
     * (builder, lab, pet, …), lalu uji gabungannya. Seri (selisih < 1 jam) → cara yang lebih awal di daftar.
     */
    fun optimize(
        v: Village, gd: GameData, ps: PlanSettings, now: Long, finishes: Map<String, Long>,
        lanes: Set<Lane>, keepSteps: Int, policies: List<Policy> = Policy.entries,
    ): Optimized {
        val bb = lanes.any { it.bb }
        fun all(p: Policy) = lanes.associateWith { p }
        // Tercepat ke target dulu; bila sama (jam yang sama): jeda menganggur terlama, lalu total menganggur.
        fun key(s: SimResult) = hours(listOf(target(s, ps, bb))) + idleKey(s, lanes, now) + hours(secondary(s, bb))
        val tryList = policies.filter { it != Policy.CRITICAL || bb }
        val runs = tryList.map { p -> all(p) to simulate(v, gd, ps.withPolicies(all(p)), now, finishes, lanes, keepSteps = keepSteps) }
        var best = runs.minWithOrNull { a, b -> keyOrder.compare(key(a.second), key(b.second)) }!!
        // Terbaik per jenis pekerja (antar jenis hampir tidak saling memengaruhi), lalu uji gabungannya.
        val mix = lanes.associateWith { lane ->
            fun lk(s: SimResult) = hours(laneKey(s, ps, lane)) + idleKey(s, setOf(lane), now)
            runs.minWithOrNull { a, b -> keyOrder.compare(lk(a.second), lk(b.second)) }!!.first[lane]!!
        }
        if (runs.none { it.first == mix }) {
            val sim = simulate(v, gd, ps.withPolicies(mix), now, finishes, lanes, keepSteps = keepSteps)
            if (keyOrder.compare(key(sim), key(best.second)) < 0) best = mix to sim
        }
        val legacy = runs.firstOrNull { it.first.values.all { p -> p == Policy.VALUE } }?.second
        fun real(t: Long) = t.takeIf { it != Long.MAX_VALUE }
        return Optimized(best.second, best.first, real(target(best.second, ps, bb)), legacy?.let { real(target(it, ps, bb)) },
            runs.size + if (runs.none { it.first == mix }) 1 else 0)
    }
}
