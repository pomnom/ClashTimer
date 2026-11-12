package id.clashtimer.core

/** Strategi untuk Builder Base. */
enum class BBStrategy(private val labelId: String, private val descId: String) {
    SIXTH("Builder ke-6", "Target: B.O.B (builder ke-6) secepatnya — hero, Builder Hall 9, gear-up, troop Lv18 & pertahanan Lv9"),
    MAX("Max", "Target: semua bangunan, hero & troop Builder Base di Builder Hall sekarang selesai secepatnya (di BH9: B.O.B dibuka dulu karena builder ke-6 mempercepat sisanya)");

    val label: String get() = if (!Lang.en) labelId else when (this) {
        SIXTH -> "6th builder"
        MAX -> "Max"
    }
    val desc: String get() = if (!Lang.en) descId else when (this) {
        SIXTH -> "Target: B.O.B (6th builder) as soon as possible — heroes, Builder Hall 9, gear-ups, troop Lv18 & defense Lv9"
        MAX -> "Target: every Builder Base building, hero & troop at the current Builder Hall done as soon as possible (at BH9: B.O.B first, since the 6th builder speeds up the rest)"
    }
}

/** Hasil simulasi jalur builder ke-6. */
class SixthPlan(
    /** Status syarat saat rencana dihitung. */
    val reqs: List<Sixth.Req>,
    /** key syarat → perkiraan selesai (ms). Tidak ada = sudah selesai atau tidak tercapai. */
    val eta: Map<String, Long>,
    /** Perkiraan B.O.B terbuka (null = tidak tercapai dalam simulasi). */
    val doneAt: Long?,
    /** Penyebab bila tidak tercapai. */
    val blockers: List<String>,
)

/**
 * Jalur builder ke-6 (B.O.B): bangun B.O.B Control di Builder Hall 9, tiga gear-up,
 * satu troop BB Lv18, satu pertahanan BB Lv9, Battle Machine + Battle Copter total 45.
 */
object Sixth {

    class GearRule(
        val name: String, val homeId: Int, val homeLevel: Int,
        val bbId: Int, val bbName: String, val bbLevel: Int,
        val durationSec: Long, val cost: Long,
    )

    val rules = listOf(
        GearRule("Cannon", 1000008, 7, 1000041, "Double Cannon", 4, 2 * 86400L, 1_000_000),
        GearRule("Archer Tower", 1000009, 10, 1000048, "Archer Tower", 6, 7 * 86400L, 3_000_000),
        GearRule("Mortar", 1000013, 8, 1000045, "Multi Mortar", 8, 14 * 86400L, 6_000_000),
    )

    private const val MULTI_MORTAR = 1000045

    // Urutan (kecil = lebih dulu). Hero adalah rantai terpanjang, jadi selalu dijalankan.
    /** Membuka pekerja lain yang sedang menunggu (Star Lab tertahan level / gudang): paling dulu. */
    const val T_UNBLOCK = 5
    /** Urutan syarat menurut jalur kritis: T_CRIT … T_CRIT+4. */
    const val T_CRIT = 6
    const val T_HERO = 8
    const val T_PLACE = 9
    const val T_HALL = 10
    const val T_BOB = 11
    const val T_GEAR = 13
    const val T_PREP = 14
    const val T_DEF9 = 15
    const val T_STARLAB = 16
    const val T_TROOP = 18
    const val T_FILL = 30

    class Req(val key: String, val label: String, val done: Boolean, val detail: String, val progress: Double)

    fun unlocked(v: Village): Boolean = v.has(GameData.BOB_HUT)
    fun control(v: Village): Int = v.level(GameData.BOB_CONTROL)

    /** B.O.B sudah terbuka (atau B.O.B Control sudah Lv5). */
    fun done(v: Village): Boolean = unlocked(v) || control(v) >= 5

    fun gearDone(v: Village, r: GearRule): Boolean =
        v.insts.any { it.item.id == r.homeId && it.geared } || control(v) >= 2 ||
            (r.homeId != 1000013 && v.has(GameData.MULTI_GEAR))

    fun gearRunning(v: Village, r: GearRule): Boolean = v.insts.any { it.item.id == r.homeId && it.gearing }

    fun homeLevel(v: Village, id: Int): Int = v.insts.filter { it.item.id == id }.maxOfOrNull { it.level } ?: 0

    private fun heroLevel(v: Village, id: Int, planned: Boolean): Int {
        val i = v.insts.firstOrNull { it.item.id == id } ?: return 0
        return i.level + if (planned && i.upgrading) 1 else 0
    }

    fun heroTotal(v: Village, planned: Boolean = false): Int =
        heroLevel(v, GameData.BATTLE_MACHINE, planned) + heroLevel(v, GameData.BATTLE_COPTER, planned)

    fun topTroop(v: Village): Int = v.insts.filter { it.item.cat == Cat.BB_TROOP }.maxOfOrNull { it.level } ?: 0

    /** Troop yang difokuskan ke Lv18: level tertinggi (Baby Dragon bila seri). */
    fun focusTroop(v: Village): Inst? =
        v.insts.filter { it.item.cat == Cat.BB_TROOP && (it.item.levels.maxOfOrNull { l -> l.level } ?: 0) >= 18 }
            .maxWithOrNull(compareBy<Inst>({ it.level + if (it.upgrading) 1 else 0 }, { if (it.item.name == "Baby Dragon") 1 else 0 }))

    private fun bbDefenses(v: Village) =
        v.insts.filter { it.item.cat == Cat.BB_BUILDING && it.item.type == "Defense" && !it.item.isWall }

    fun topDefense(v: Village): Int = bbDefenses(v).maxOfOrNull { it.level } ?: 0

    fun status(v: Village): List<Req> {
        val c = control(v)
        val bh = v.bh
        val gears = rules.count { gearDone(v, it) }
        val troop = topTroop(v)
        val def = topDefense(v)
        val bm = heroLevel(v, GameData.BATTLE_MACHINE, false)
        val bcl = heroLevel(v, GameData.BATTLE_COPTER, false)
        val gearDetail = rules.joinToString(" · ") { r ->
            when {
                gearDone(v, r) -> "${r.name} ✓"
                gearRunning(v, r) -> tr("${r.name} berjalan", "${r.name} running")
                else -> r.name
            }
        }
        val hallDetail = when {
            c >= 1 -> tr("B.O.B Control sudah dibangun", "B.O.B Control already built")
            bh >= 9 -> tr("BH9 ✓ · tinggal bangun B.O.B Control", "BH9 ✓ · just build B.O.B Control")
            else -> tr("BH$bh dari 9", "BH$bh of 9")
        }
        val troopName = focusTroop(v)?.item?.name
        return listOf(
            Req("hall", "Builder Hall 9 + B.O.B Control", c >= 1, hallDetail, if (c >= 1) 1.0 else bh / 9.0),
            Req("gears", tr("Tiga gear-up di Home Village", "Three gear-ups in the Home Village"), gears >= 3 || c >= 2, "$gears/3 · $gearDetail", gears / 3.0),
            Req("troop", tr("Satu troop BB di Lv18", "One BB troop at Lv18"), troop >= 18 || c >= 3,
                tr("tertinggi Lv$troop", "highest Lv$troop") + (troopName?.let { tr(" · fokus $it", " · focus $it") } ?: ""), troop / 18.0),
            Req("defense", tr("Satu pertahanan BB di Lv9", "One BB defense at Lv9"), def >= 9 || c >= 4, tr("tertinggi Lv$def (tanpa tembok & jebakan)", "highest Lv$def (walls & traps excluded)"), def / 9.0),
            Req("heroes", "Battle Machine + Battle Copter = 45", bm + bcl >= 45 || c >= 5, "$bm + $bcl = ${bm + bcl}", (bm + bcl) / 45.0),
            Req("control", "B.O.B Control Lv5", c >= 5, tr("Lv$c dari 5 · klaim tiap syarat di B.O.B Control", "Lv$c of 5 · claim each requirement at B.O.B Control"), c / 5.0),
        )
    }

    private fun isDone(st: List<Req>, key: String) = st.first { it.key == key }.done

    /** Klaim level B.O.B Control yang syaratnya sudah terpenuhi (tanpa waktu builder). */
    fun autoClaim(v: Village) {
        val bob = v.insts.firstOrNull { it.item.id == GameData.BOB_CONTROL && !it.upgrading } ?: return
        val gates = listOf("gears", "troop", "defense", "heroes")
        while (bob.level in 1..4) {
            val st = status(v)
            if (!isDone(st, gates[bob.level - 1])) break
            bob.level += 1
        }
    }

    /** Id bangunan Home Village yang masih perlu dinaikkan untuk syarat gear-up → level yang dibutuhkan. */
    fun homeNeeds(v: Village): Map<Int, Int> {
        if (done(v)) return emptyMap()
        val out = HashMap<Int, Int>()
        for (r in rules) {
            if (gearDone(v, r) || gearRunning(v, r)) continue
            if (homeLevel(v, r.homeId) < r.homeLevel) out[r.homeId] = r.homeLevel
        }
        return out
    }

    /** Penyebab jalur tidak selesai dalam simulasi Builder Base. */
    fun blockers(v: Village): List<String> {
        val out = ArrayList<String>()
        for (r in rules) {
            if (gearDone(v, r) || gearRunning(v, r)) continue
            val lv = homeLevel(v, r.homeId)
            if (lv < r.homeLevel) out += "Gear-up ${r.name} butuh ${r.name} Lv${r.homeLevel} di Home Village (sekarang Lv$lv)"
        }
        if (out.isEmpty()) out += "Belum semua syarat bisa dijadwalkan dari data export ini"
        return out
    }

    private fun remainingTo9(i: Inst): Long =
        i.item.levels.filter { it.level > i.level && it.level <= 9 }.sumOf { it.time }

    /**
     * Jalur kritis: perkiraan sisa waktu kerja (detik) tiap syarat bila dikerjakan terus mulai sekarang.
     * Syarat yang paling lama didahulukan supaya semua syarat selesai berdekatan.
     */
    fun remainingByReq(v: Village, gd: GameData, gold: GoldPass): Map<String, Long> {
        val st = status(v)
        val out = HashMap<String, Long>()
        val bbLane = Lane.BB_BUILDER
        // Hero: level berikutnya dibagi ke dua rantai (BM & BC), yang lebih pendek diisi dulu.
        if (!isDone(st, "heroes")) {
            var need = 45 - heroTotal(v, planned = true)
            val bm = v.insts.firstOrNull { it.item.id == GameData.BATTLE_MACHINE }
            val bc = v.insts.firstOrNull { it.item.id == GameData.BATTLE_COPTER }
            var lb = (bm?.level ?: 0) + if (bm?.upgrading == true) 1 else 0
            var lc = (bc?.level ?: 0) + if (bc?.upgrading == true) 1 else 0
            var tb = 0L; var tc = 0L
            while (need > 0) {
                val nb = bm?.item?.lv(lb + 1)?.time?.let { gold.apply(it, bbLane) }
                val nc = bc?.item?.lv(lc + 1)?.time?.let { gold.apply(it, bbLane) }
                if (nb == null && nc == null) break
                if (nc == null || (nb != null && tb + nb <= tc + nc)) { tb += nb!!; lb++ } else { tc += nc; lc++ }
                need--
            }
            out["heroes"] = maxOf(tb, tc)
        }
        if (!isDone(st, "troop")) {
            val f = focusTroop(v)
            var sum = 0L
            var needLab = v.starLab
            if (f != null) {
                val from = f.level + if (f.upgrading) 1 else 0
                for (l in from + 1..18) {
                    val lv = f.item.lv(l) ?: break
                    sum += gold.apply(lv.time, Lane.STAR_LAB)
                    needLab = maxOf(needLab, lv.req)
                }
            }
            val sl = v.insts.firstOrNull { it.item.id == GameData.STAR_LAB }
            if (sl != null) for (l in (sl.level + if (sl.upgrading) 2 else 1)..needLab) sl.item.lv(l)?.let { sum += gold.apply(it.time, bbLane) }
            out["troop"] = sum
        }
        if (!isDone(st, "gears")) {
            var worst = 0L
            for (r in rules) {
                if (gearDone(v, r) || gearRunning(v, r)) continue
                val b = v.insts.filter { it.item.id == r.bbId }.maxByOrNull { it.level }
                var prep = 0L
                if (b != null) for (l in (b.level + if (b.upgrading) 2 else 1)..r.bbLevel) b.item.lv(l)?.let { prep += gold.apply(it.time, bbLane) }
                worst = maxOf(worst, prep + r.durationSec)
            }
            out["gears"] = worst
        }
        if (!isDone(st, "defense")) {
            val mm = bbDefenses(v).filter { it.item.id == MULTI_MORTAR }.maxByOrNull { it.level }
            val d = (mm ?: bbDefenses(v).filter { it.level < 9 }.minByOrNull { remainingTo9(it) })
            out["defense"] = d?.let { remainingTo9(it) } ?: 0L
        }
        if (!isDone(st, "hall")) {
            val hall = v.insts.firstOrNull { it.item.id == GameData.BUILDER_HALL }
            var sum = 0L
            if (hall != null) for (l in (hall.level + if (hall.upgrading) 2 else 1)..9) hall.item.lv(l)?.let { sum += it.time }
            out["hall"] = sum + 3600
        }
        return out
    }

    /** Urutan tiap syarat: yang sisa waktunya paling lama = urutan terkecil. */
    private fun reqTiers(v: Village, gd: GameData, gold: GoldPass): Map<String, Int> {
        val rem = remainingByReq(v, gd, gold)
        return rem.entries.sortedByDescending { it.value }.mapIndexed { i, e -> e.key to (T_CRIT + i) }.toMap()
    }

    /**
     * Upgrade Star Laboratory [sl] harus dimulai sekarang supaya selesai sebelum troop fokus [f] butuh
     * level berikutnya: riset fokus yang masih bisa di level Star Lab sekarang dijalankan dari [labFreeAt],
     * lalu dibandingkan dengan [start] + lama upgrade Star Laboratory (+12 jam cadangan jam tidur/kapasitas).
     */
    private fun starLabDue(
        v: Village, f: Inst?, sl: Candidate, ps: PlanSettings, start: Long?, labFreeAt: Long?, nextBuilderFree: Long?,
    ): Boolean {
        if (f == null || start == null || labFreeAt == null) return false
        val gold = ps.gold
        // Bila ditunda, Master Builder berikutnya baru bebas pada [nextBuilderFree]: masih sempat?
        val latest = maxOf(start, nextBuilderFree?.let { ps.schedule.nextOnline(it) } ?: Long.MAX_VALUE / 4)
        var t = maxOf(labFreeAt, start)
        var l = f.level + if (f.upgrading) 1 else 0
        while (l < 18) {
            val lv = f.item.lv(l + 1) ?: return false
            if (lv.req > v.starLab) return latest + storageLead(v, sl, gold) + sl.duration * 1000 + 6 * 3_600_000L >= t
            t += gold.apply(lv.time, Lane.STAR_LAB) * 1000
            l++
        }
        return false
    }

    /**
     * Lama (ms) menaikkan gudang sampai biaya [c] muat: storage terendah dinaikkan satu per satu
     * (berurutan, perkiraan aman). 0 bila sudah muat.
     */
    private fun storageLead(v: Village, c: Candidate, gold: GoldPass): Long {
        val id = Capacity.storageFor(c.item.resource) ?: return 0L
        var cap = Capacity.of(v, c.item.resource) ?: return 0L
        val st = v.insts.filter { it.item.id == id }
        cap += st.filter { it.upgrading }.sumOf { Capacity.gain(c.item.resource, it.level, it.level + 1) }
        if (c.cost <= cap) return 0L
        val lv = st.map { it.level + if (it.upgrading) 1 else 0 }.toMutableList()
        var lead = 0L
        while (cap < c.cost) {
            val i = lv.indices.filter { k -> st[k].item.lv(lv[k] + 1)?.let { it.hall <= v.bh } == true }.minByOrNull { lv[it] } ?: break
            val next = st[i].item.lv(lv[i] + 1)!!
            lead += gold.apply(next.time, Lane.BB_BUILDER) * 1000
            cap += Capacity.gain(c.item.resource, lv[i], lv[i] + 1)
            lv[i]++
        }
        return lead
    }

    /** Star Lab bebas tetapi tidak ada riset yang bisa dimulai di levelnya sekarang. */
    private fun labStarved(v: Village, gd: GameData, ps: PlanSettings): Boolean {
        if (v.starLab <= 0 || v.insts.any { it.upgrading && it.lane == Lane.STAR_LAB }) return false
        if (v.insts.any { it.item.id == GameData.STAR_LAB && it.upgrading }) return false
        return Planner.candidates(v, gd, Lane.STAR_LAB, ps.gold, includeHall = false, skip = ps.planSkip).isEmpty()
    }

    /** Kandidat + urutan (kecil = lebih dulu) untuk jalur builder ke-6. */
    /**
     * [start] = kapan Master Builder ini mulai; [labFreeAt] = kapan Star Lab bebas. Dengan keduanya,
     * upgrade Star Laboratory dimulai tepat waktu: selesai sebelum troop fokus butuh level Star Lab berikutnya.
     */
    fun options(
        v: Village, gd: GameData, lane: Lane, ps: PlanSettings, start: Long? = null, labFreeAt: Long? = null,
        nextBuilderFree: Long? = null,
    ): List<Pair<Candidate, Int>> {
        if (done(v)) return emptyList()
        val st = status(v)
        val gold = ps.gold
        val out = ArrayList<Pair<Candidate, Int>>()

        if (lane == Lane.STAR_LAB) {
            val focus = focusTroop(v)
            val next = focus?.takeIf { !isDone(st, "troop") && !it.upgrading }?.item?.lv(focus.level + 1)
            if (focus != null && next != null && next.req <= v.starLab && (next.time > 0 || next.cost > 0)) {
                out += Candidate(lane, focus.item, focus, focus.level, focus.level + 1, gold.apply(next.time, lane), next.cost, false,
                    baseDuration = next.time) to T_TROOP
            }
            // Star Lab tidak boleh menganggur: riset lain sambil menunggu troop fokus.
            Planner.candidates(v, gd, lane, gold, includeHall = false, skip = ps.planSkip)
                .filter { it.inst !== focus }.forEach { out += it to T_FILL }
            return out
        }
        if (lane != Lane.BB_BUILDER) return out

        val all = Planner.candidates(v, gd, lane, gold, includeHall = true, skip = ps.planSkip)
        val bh = v.bh
        // Jalur kritis: syarat yang paling lama sisanya didahulukan (urutan tetap lama bila tidak dipilih).
        val critical = ps.policyFor(Lane.BB_BUILDER) == Policy.CRITICAL
        val tiers = if (critical) reqTiers(v, gd, gold) else emptyMap()
        fun tierOf(key: String, old: Int) = tiers[key] ?: old
        fun add(c: Candidate, tier: Int) { out += c to tier }

        // Hero: rantai terpanjang (Battle Machine dari Lv1, Battle Copter dari Lv15), jalankan terus.
        if (!isDone(st, "heroes") && heroTotal(v, planned = true) < 45) {
            all.filter { it.item.cat == Cat.BB_HERO }.forEach { add(it, tierOf("heroes", T_HERO)) }
        }
        // Builder Hall 9. Bangunan & jebakan baru harus dipasang dulu sebelum Builder Hall bisa naik.
        if (bh < 9 && control(v) == 0) {
            all.filter {
                it.isNew && (it.item.cat == Cat.BB_BUILDING || it.item.cat == Cat.BB_TRAP) && it.item.id != GameData.BOB_CONTROL &&
                    it.item.instances.isEmpty() // kamp pasukan tidak dibutuhkan untuk naik hall
            }
                .forEach { add(it, tierOf("hall", T_PLACE)) }
            all.firstOrNull { it.item.id == GameData.BUILDER_HALL }?.let { add(it, tierOf("hall", T_HALL)) }
        }
        if (bh >= 9 && control(v) == 0) {
            all.firstOrNull { it.item.id == GameData.BOB_CONTROL && it.isNew }?.let { add(it, T_BOB) }
        }
        // Star Lab sampai Lv9 untuk troop Lv18
        if (!isDone(st, "troop")) {
            val f = focusTroop(v)
            val nextReq = f?.item?.lv(f.level + if (f.upgrading) 2 else 1)?.req ?: 0
            all.firstOrNull { it.item.id == GameData.STAR_LAB && it.toLevel <= 9 }?.let {
                // Jalur kritis: Star Laboratory dimulai tepat waktu supaya riset troop fokus tidak tertahan.
                val urgent = nextReq > v.starLab || (critical && starLabDue(v, f, it, ps, start, labFreeAt, nextBuilderFree))
                add(it, if (urgent) T_UNBLOCK else tierOf("troop", T_STARLAB))
            }
        }
        // Star Lab yang tidak punya riset lagi di levelnya: upgrade Star Laboratory paling didahulukan.
        if (labStarved(v, gd, ps)) all.firstOrNull { it.item.id == GameData.STAR_LAB }?.let { add(it, T_UNBLOCK) }
        // Gear-up dan bangunan Builder Base yang menjadi syaratnya. Hanya satu Master Builder yang bisa ke
        // Home Village, jadi gear-up berikutnya baru bisa dimulai setelah yang sedang berjalan selesai.
        val gearBusy = v.insts.any { it.gearing }
        for (r in rules) {
            if (gearDone(v, r) || gearRunning(v, r)) continue
            if (homeLevel(v, r.bbId) < r.bbLevel) {
                all.filter { it.item.id == r.bbId && !it.isNew && it.toLevel <= r.bbLevel }
                    .maxByOrNull { it.fromLevel }?.let { add(it, tierOf("gears", T_PREP)) }
                continue
            }
            if (gearBusy) continue
            val home = v.insts.filter { it.item.id == r.homeId && !it.upgrading && !it.geared && it.level >= r.homeLevel }
                .maxByOrNull { it.level } ?: continue
            add(Candidate(Lane.BB_BUILDER, home.item, home, home.level, home.level, r.durationSec, r.cost, false, gear = true), tierOf("gears", T_GEAR))
        }
        // Satu pertahanan Lv9 (Multi Mortar: sekalian syarat gear-up Mortar)
        if (!isDone(st, "defense")) {
            val defs = bbDefenses(v)
            val mortarPending = !gearDone(v, rules[2])
            val mm = defs.filter { it.item.id == MULTI_MORTAR }.maxByOrNull { it.level }
            val chosen = if (mm != null && (mortarPending || mm.level >= 8)) mm
            else defs.filter { it.level < 9 && it.item.maxLevelFor(gd.maxBH) >= 9 }.minByOrNull { remainingTo9(it) }
            if (chosen != null && !chosen.upgrading && chosen.level < 9) {
                all.firstOrNull { it.inst === chosen }?.let { add(it, tierOf("defense", T_DEF9)) }
            }
        }
        // Pengisi saat tidak ada langkah wajib: Clock Tower (boost lebih lama), army camp, gudang.
        for (c in all) {
            if (c.isNew) continue
            if (c.item.id == GameData.CLOCK_TOWER || c.item.name == "Army Camp" || c.item.name.endsWith("Storage")) add(c, T_FILL)
        }
        // Satu kandidat bisa masuk dua kali (mis. Multi Mortar): ambil urutan terbaik.
        return out.groupBy { "${it.first.inst?.uid ?: "new"}|${it.first.item.id}|${it.first.gear}" }
            .map { (_, g) -> g.minByOrNull { it.second }!! }
    }
}
