package id.clashtimer.core

/** Strategi untuk Builder Base. */
enum class BBStrategy(val label: String, val desc: String) {
    SIXTH("Builder ke-6", "Target: B.O.B (builder ke-6) secepatnya — hero, Builder Hall 9, gear-up, troop Lv18 & pertahanan Lv9"),
    MAX("Max", "Target: semua bangunan, hero & troop Builder Base di Builder Hall sekarang selesai secepatnya"),
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
    const val T_HERO = 8
    const val T_PLACE = 9
    const val T_HALL = 10
    const val T_BOB = 11
    const val T_STARLAB_BLOCK = 12
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
                gearRunning(v, r) -> "${r.name} berjalan"
                else -> r.name
            }
        }
        val hallDetail = when {
            c >= 1 -> "B.O.B Control sudah dibangun"
            bh >= 9 -> "BH9 ✓ · tinggal bangun B.O.B Control"
            else -> "BH$bh dari 9"
        }
        val troopName = focusTroop(v)?.item?.name
        return listOf(
            Req("hall", "Builder Hall 9 + B.O.B Control", c >= 1, hallDetail, if (c >= 1) 1.0 else bh / 9.0),
            Req("gears", "Tiga gear-up di Home Village", gears >= 3 || c >= 2, "$gears/3 · $gearDetail", gears / 3.0),
            Req("troop", "Satu troop BB di Lv18", troop >= 18 || c >= 3,
                "tertinggi Lv$troop" + (troopName?.let { " · fokus $it" } ?: ""), troop / 18.0),
            Req("defense", "Satu pertahanan BB di Lv9", def >= 9 || c >= 4, "tertinggi Lv$def (tanpa tembok & jebakan)", def / 9.0),
            Req("heroes", "Battle Machine + Battle Copter = 45", bm + bcl >= 45 || c >= 5, "$bm + $bcl = ${bm + bcl}", (bm + bcl) / 45.0),
            Req("control", "B.O.B Control Lv5", c >= 5, "Lv$c dari 5 · klaim tiap syarat di B.O.B Control", c / 5.0),
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

    /** Kandidat + urutan (kecil = lebih dulu) untuk jalur builder ke-6. */
    fun options(v: Village, gd: GameData, lane: Lane, ps: PlanSettings): List<Pair<Candidate, Int>> {
        if (done(v)) return emptyList()
        val st = status(v)
        val gold = ps.gold
        val out = ArrayList<Pair<Candidate, Int>>()

        if (lane == Lane.STAR_LAB) {
            if (isDone(st, "troop")) return out
            val focus = focusTroop(v) ?: return out
            if (focus.upgrading) return out
            val next = focus.item.lv(focus.level + 1) ?: return out
            if (next.req > v.starLab || next.time <= 0) return out
            out += Candidate(lane, focus.item, focus, focus.level, focus.level + 1, gold.apply(next.time, lane), next.cost, false,
                baseDuration = next.time) to T_TROOP
            return out
        }
        if (lane != Lane.BB_BUILDER) return out

        val all = Planner.candidates(v, gd, lane, gold, includeHall = true, skip = ps.planSkip)
        val bh = v.bh
        fun add(c: Candidate, tier: Int) { out += c to tier }

        // Hero: rantai terpanjang (Battle Machine dari Lv1, Battle Copter dari Lv15), jalankan terus.
        if (!isDone(st, "heroes") && heroTotal(v, planned = true) < 45) {
            all.filter { it.item.cat == Cat.BB_HERO }.forEach { add(it, T_HERO) }
        }
        // Builder Hall 9. Bangunan & jebakan baru harus dipasang dulu sebelum Builder Hall bisa naik.
        if (bh < 9 && control(v) == 0) {
            all.filter { it.isNew && (it.item.cat == Cat.BB_BUILDING || it.item.cat == Cat.BB_TRAP) && it.item.id != GameData.BOB_CONTROL }
                .forEach { add(it, T_PLACE) }
            all.firstOrNull { it.item.id == GameData.BUILDER_HALL }?.let { add(it, T_HALL) }
        }
        if (bh >= 9 && control(v) == 0) {
            all.firstOrNull { it.item.id == GameData.BOB_CONTROL && it.isNew }?.let { add(it, T_BOB) }
        }
        // Star Lab sampai Lv9 untuk troop Lv18
        if (!isDone(st, "troop")) {
            val f = focusTroop(v)
            val nextReq = f?.item?.lv(f.level + if (f.upgrading) 2 else 1)?.req ?: 0
            all.firstOrNull { it.item.id == GameData.STAR_LAB && it.toLevel <= 9 }?.let {
                add(it, if (nextReq > v.starLab) T_STARLAB_BLOCK else T_STARLAB)
            }
        }
        // Gear-up dan bangunan Builder Base yang menjadi syaratnya
        for (r in rules) {
            if (gearDone(v, r) || gearRunning(v, r)) continue
            if (homeLevel(v, r.bbId) < r.bbLevel) {
                all.filter { it.item.id == r.bbId && !it.isNew && it.toLevel <= r.bbLevel }
                    .maxByOrNull { it.fromLevel }?.let { add(it, T_PREP) }
                continue
            }
            val home = v.insts.filter { it.item.id == r.homeId && !it.upgrading && !it.geared && it.level >= r.homeLevel }
                .maxByOrNull { it.level } ?: continue
            add(Candidate(Lane.BB_BUILDER, home.item, home, home.level, home.level, r.durationSec, r.cost, false, gear = true), T_GEAR)
        }
        // Satu pertahanan Lv9 (Multi Mortar: sekalian syarat gear-up Mortar)
        if (!isDone(st, "defense")) {
            val defs = bbDefenses(v)
            val mortarPending = !gearDone(v, rules[2])
            val mm = defs.filter { it.item.id == MULTI_MORTAR }.maxByOrNull { it.level }
            val chosen = if (mm != null && (mortarPending || mm.level >= 8)) mm
            else defs.filter { it.level < 9 && it.item.maxLevelFor(gd.maxBH) >= 9 }.minByOrNull { remainingTo9(it) }
            if (chosen != null && !chosen.upgrading && chosen.level < 9) {
                all.firstOrNull { it.inst === chosen }?.let { add(it, T_DEF9) }
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
