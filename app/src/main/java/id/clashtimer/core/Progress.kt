package id.clashtimer.core

class ProgressRow(
    val name: String,
    /** Contoh: "2× Lv7, 5× Lv13 → 15". */
    val detail: String,
    val remainingSec: Long,
    val upgrading: Boolean,
    val itemId: Int = 0,
)

class CatProgress(
    val key: String,
    val title: String,
    val lane: Lane,
    val doneSec: Long,
    val totalSec: Long,
    val doneLevels: Int,
    val totalLevels: Int,
    val remainingSec: Long,
    /** resource → biaya tersisa. */
    val costs: Map<String, Long>,
    val rows: List<ProgressRow>,
) {
    val pct: Double get() = if (totalSec <= 0) 100.0 else 100.0 * doneSec / totalSec
}

/**
 * [costLeft] = biaya semua tembok ke max, [nextCosts] = per level sekarang: biaya menaikkan tembok di level itu
 * satu level (jumlah tembok × harga level berikutnya). [resource] = sumber daya tembok.
 */
class WallProgress(
    val atMax: Int, val total: Int, val maxLevel: Int, val costLeft: Long, val levels: Map<Int, Int>,
    val nextCosts: Map<Int, Long> = emptyMap(), val resource: String = "",
)

class VillageProgress(
    val bb: Boolean,
    val hall: Int,
    val categories: List<CatProgress>,
    val walls: WallProgress?,
) {
    val doneSec: Long get() = categories.sumOf { it.doneSec }
    val totalSec: Long get() = categories.sumOf { it.totalSec }
    val pct: Double get() = if (totalSec <= 0) 100.0 else 100.0 * doneSec / totalSec
    fun remaining(lane: Lane): Long = categories.filter { it.lane == lane }.sumOf { it.remainingSec }
}

object ProgressCalc {

    private class Acc(val key: String, val title: String, val lane: Lane) {
        var done = 0L
        var total = 0L
        var doneLv = 0
        var totalLv = 0
        var rem = 0L
        val costs = HashMap<String, Long>()
        val rows = ArrayList<ProgressRow>()
    }

    private fun catKey(it: Item): Pair<String, String> = when (it.cat) {
        Cat.HERO, Cat.BB_HERO -> "hero" to "Hero"
        Cat.TROOP -> "troop" to "Troop & Siege"
        Cat.SPELL -> "spell" to "Spell"
        Cat.PET -> "pet" to "Pet"
        Cat.TRAP, Cat.BB_TRAP -> "trap" to "Jebakan"
        Cat.BB_TROOP -> "bbtroop" to "Troop (Star Lab)"
        Cat.BUILDING, Cat.BB_BUILDING -> when {
            it.type == "Defense" -> "def" to "Pertahanan"
            it.type == "Army" -> "army" to "Army & pendukung"
            it.type == "Resource" -> "res" to "Sumber daya"
            else -> "other" to "Lainnya"
        }
    }

    /** Level awal yang dihitung (troop/spell level 1 didapat gratis saat dibuka). */
    private fun firstCounted(it: Item): Int = when (it.cat) {
        Cat.TROOP, Cat.SPELL, Cat.BB_TROOP -> 2
        else -> 1
    }

    /** [exclude] = id item yang tidak dihitung (mis. collector bila memilih tidak meng-upgrade-nya). */
    fun compute(
        v: Village, gd: GameData, bb: Boolean, gold: GoldPass, now: Long, finishes: Map<String, Long>,
        exclude: Set<Int> = emptySet(),
    ): VillageProgress {
        val hall = if (bb) v.bh else v.th
        val accs = LinkedHashMap<String, Acc>()
        fun acc(item: Item): Acc {
            val (k, t) = catKey(item)
            return accs.getOrPut(k) { Acc(k, t, item.lane) }
        }

        // Kelompokkan instance per item
        val byItem = v.insts.filter { it.item.cat.bb == bb && !it.item.isWall && !it.item.isTownHall }
            .groupBy { it.item.id }

        val itemIds = LinkedHashSet<Int>()
        itemIds += byItem.keys
        // Bangunan yang belum dibangun
        for (id in gd.unlockIds(bb)) {
            val item = gd.item(id) ?: continue
            if (item.isWall || item.isTownHall || item.levels.isEmpty()) continue
            if (id == GameData.BOB_HUT || id == GameData.HELPER_HUT) continue
            if (Planner.allowed(v, gd, id, hall, bb) > (byItem[id]?.size ?: 0)) itemIds += id
        }
        // Hero / troop / spell / pet yang belum terbuka tapi tersedia di level hall ini
        if (!bb) {
            val hhMax = gd.item(GameData.HERO_HALL)?.maxLevelFor(hall) ?: 0
            val phMax = gd.item(GameData.PET_HOUSE)?.maxLevelFor(hall) ?: 0
            for (item in gd.items.values) {
                if (item.id in itemIds) continue
                val usable = item.levels.any { it.time > 0 }
                if (!usable) continue
                val lv1 = item.lv(1) ?: continue
                when (item.cat) {
                    Cat.HERO -> if (item.prodLevel <= hhMax && lv1.hall <= hall) itemIds += item.id
                    Cat.PET -> if (item.prodLevel <= phMax && lv1.hall <= hall && phMax > 0) itemIds += item.id
                    Cat.TROOP, Cat.SPELL -> {
                        val pb = gd.items.values.firstOrNull { it.name == item.prodBuilding && !it.cat.bb } ?: continue
                        if (item.prodLevel <= pb.maxLevelFor(hall) && item.maxLevelFor(hall) >= 2) itemIds += item.id
                    }
                    else -> {}
                }
            }
        } else {
            // Hero Builder Base yang altarnya sudah tersedia tetapi belum dibuka (Battle Copter di BH8)
            for (item in gd.items.values) {
                if (item.cat != Cat.BB_HERO || item.id in itemIds) continue
                if (Planner.bbHeroUnlocked(gd, item, hall)) itemIds += item.id
            }
        }

        for (id in itemIds) {
            if (id in exclude) continue
            val item = gd.item(id) ?: continue
            val max = item.maxLevelFor(hall)
            if (max <= 0) continue
            val first = firstCounted(item)
            // Kamp pasukan BB: bangunan yang sudah jadi = urutan awal, yang sedang dibangun = berikutnya.
            val insts = byItem[id].orEmpty().let { l -> if (item.instances.isEmpty()) l else l.sortedByDescending { it.level } }
            val allowedN = when (item.cat) {
                Cat.BUILDING, Cat.TRAP, Cat.BB_BUILDING, Cat.BB_TRAP -> maxOf(Planner.allowed(v, gd, id, hall, bb), insts.size)
                else -> maxOf(1, insts.size)
            }
            if (allowedN == 0) continue
            val a = acc(item)

            var itemRem = 0L
            var anyUp = false
            val levelCounts = sortedMapOf<Int, Int>()
            for (k in 0 until allowedN) {
                val inst = insts.getOrNull(k)
                val cur = inst?.level ?: 0
                levelCounts[cur] = (levelCounts[cur] ?: 0) + 1
                for (l in first..max) {
                    val lv = (if (l == 1 && item.instances.isNotEmpty()) item.build(k + 1) else item.lv(l)) ?: continue
                    val t = gold.apply(lv.time, item.lane)
                    a.total += t
                    a.totalLv += 1
                    when {
                        l <= cur -> { a.done += t; a.doneLv += 1 }
                        l == cur + 1 && inst != null && inst.upgrading && !inst.gearing -> {
                            anyUp = true
                            val fin = finishes[inst.uid] ?: (v.exportAt + inst.remaining * 1000)
                            val left = ((fin - now) / 1000).coerceIn(0, t)
                            a.done += t - left
                            a.rem += left
                            itemRem += left
                        }
                        else -> {
                            a.rem += t
                            itemRem += t
                            a.costs[item.resource] = (a.costs[item.resource] ?: 0) + lv.cost
                        }
                    }
                }
            }
            val atMax = (levelCounts[max] ?: 0)
            if (itemRem > 0 || atMax < allowedN) {
                val parts = levelCounts.filterKeys { it < max }.entries.joinToString(", ") { (lvl, n) ->
                    val l = if (lvl == 0) "belum ada" else "Lv$lvl"
                    if (n > 1) "$n× $l" else l
                }
                a.rows += ProgressRow(item.name, "$parts → $max", itemRem, anyUp, item.id)
            }
        }

        for (a in accs.values) a.rows.sortByDescending { it.remainingSec }

        // Tembok
        val wallItem = gd.item(if (bb) GameData.BB_WALL else GameData.WALL)
        val wallLevels = if (bb) v.bbWalls else v.walls
        val walls = if (wallItem != null && wallLevels.isNotEmpty()) {
            val max = wallItem.maxLevelFor(hall)
            var cost = 0L
            for ((lvl, n) in wallLevels) {
                for (l in (lvl + 1)..max) cost += (wallItem.lv(l)?.cost ?: 0) * n
            }
            val total = maxOf(wallLevels.values.sum(), gd.allowedCount(wallItem.id, hall, bb))
            val next = wallLevels.filterKeys { it < max }.mapValues { (lvl, n) -> (wallItem.lv(lvl + 1)?.cost ?: 0) * n }
            WallProgress(wallLevels.filterKeys { it >= max }.values.sum(), total, max, cost, wallLevels.toSortedMap(),
                next.toSortedMap(), wallItem.resource)
        } else null

        val order = listOf("hero", "army", "def", "trap", "res", "other", "troop", "spell", "pet", "bbtroop")
        val cats = accs.values.sortedBy { order.indexOf(it.key).let { i -> if (i < 0) 99 else i } }.map {
            CatProgress(it.key, it.title, it.lane, it.done, it.total, it.doneLv, it.totalLv, it.rem, it.costs, it.rows)
        }
        return VillageProgress(bb, hall, cats, walls)
    }
}
