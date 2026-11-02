package id.clashtimer.core

/** Kategori item game. */
enum class Cat(val code: String, val bb: Boolean) {
    BUILDING("b", false),
    TRAP("t", false),
    HERO("h", false),
    TROOP("u", false),
    SPELL("s", false),
    PET("p", false),
    BB_BUILDING("B", true),
    BB_HERO("H", true),
    BB_TROOP("U", true),
    BB_TRAP("T", true);

    companion object {
        fun of(code: String): Cat? = entries.firstOrNull { it.code == code }
    }
}

/** Antrean pekerja di game. */
enum class Lane(val label: String, val emoji: String, val bb: Boolean) {
    BUILDER("Builder", "🔨", false),
    LAB("Laboratory", "🧪", false),
    PET("Pet House", "🐾", false),
    BB_BUILDER("Builder Base", "🛠️", true),
    STAR_LAB("Star Lab", "⭐", true);

    companion object {
        fun of(cat: Cat): Lane = when (cat) {
            Cat.BUILDING, Cat.TRAP, Cat.HERO -> BUILDER
            Cat.TROOP, Cat.SPELL -> LAB
            Cat.PET -> PET
            Cat.BB_BUILDING, Cat.BB_HERO, Cat.BB_TRAP -> BB_BUILDER
            Cat.BB_TROOP -> STAR_LAB
        }
    }
}

/**
 * Data satu level. [time] dan [cost] adalah durasi & biaya untuk MENCAPAI level ini
 * dari level sebelumnya (sudah dicek terhadap timer export asli).
 * [hall] = level Town Hall / Builder Hall yang dibutuhkan,
 * [req]  = level Lab / Hero Hall / Pet House / Star Lab yang dibutuhkan.
 */
data class Lv(val level: Int, val time: Long, val cost: Long, val hall: Int, val req: Int)

data class MergeSrc(val id: Int, val level: Int, val geared: Boolean)

/** Modul pertahanan rakitan (Crafting Station). [levels]: waktu & biaya MENCAPAI level itu, [Lv.hall] = TH minimal. */
class CraftedModule(val id: Int, val name: String, val short: String, val resource: String, val levels: List<Lv>) {
    fun maxFor(th: Int): Int = levels.filter { it.hall <= th }.maxOfOrNull { it.level } ?: 1
}

/** Pertahanan rakitan musim ini beserta modulnya. */
class CraftedDef(val id: Int, val name: String, val modules: List<CraftedModule>)

class Item(
    val id: Int,
    val name: String,
    val cat: Cat,
    val type: String,
    val resource: String,
    val prodBuilding: String,
    val prodLevel: Int,
    val levels: List<Lv>,
    val merges: Map<Int, List<MergeSrc>>,
    /** Jalur ikon di assets.clashk.ing (folder per level, atau file tunggal). */
    val iconPath: String? = null,
    val iconLevels: List<Int> = emptyList(),
    /**
     * Bangunan yang waktu & biayanya per bangunan ke-N, bukan per level (Army Camp & Reinforcement Camp
     * Builder Base). [Lv.level] = urutan bangunan (1 = pertama), [Lv.hall] = Builder Hall minimal.
     */
    val instances: List<Lv> = emptyList(),
) {
    /** Waktu & biaya membangun bangunan ke-[n] (1 = pertama). Tanpa [instances]: sama dengan level 1. */
    fun build(n: Int): Lv? = if (instances.isEmpty()) lv(1) else instances.firstOrNull { it.level == n } ?: instances.lastOrNull()

    /** Perkiraan lama total pembangunan yang masih berjalan dengan sisa [remainingSec] (bangunan ke-N tidak diketahui). */
    fun buildTimeFor(remainingSec: Long): Long? =
        instances.filter { it.time >= remainingSec }.minByOrNull { it.time }?.time

    /** Jalur ikon untuk level tertentu (dibatasi ke level gambar yang tersedia). */
    fun icon(level: Int): String? {
        val p = iconPath ?: return null
        if (iconLevels.isEmpty()) return p
        val l = level.coerceIn(iconLevels.first(), iconLevels.last())
        return "$p/level_$l.webp"
    }

    private val byLevel: Map<Int, Lv> = levels.associateBy { it.level }

    fun lv(level: Int): Lv? = byLevel[level]

    /** Level tertinggi yang boleh dicapai pada level hall ini. */
    fun maxLevelFor(hall: Int): Int = levels.filter { it.hall <= hall }.maxOfOrNull { it.level } ?: 0

    val lane: Lane get() = Lane.of(cat)
    val isWall: Boolean get() = type == "Wall"
    val isTownHall: Boolean get() = id == GameData.TOWN_HALL || id == GameData.BUILDER_HALL
    val isSiege: Boolean get() = prodBuilding == "Workshop"
}

class GameData(
    val version: String,
    val maxTH: Int,
    val maxBH: Int,
    val items: Map<Int, Item>,
    private val homeUnlocks: Map<Int, List<Pair<Int, Int>>>,
    private val bbUnlocks: Map<Int, List<Pair<Int, Int>>>,
    private val names: Map<String, String>,
    /** Ikon lain (helper) per id. */
    val extraIcons: Map<String, String> = emptyMap(),
    /** Level Town Hall → id item senjata yang masih punya level (TH17: Inferno Artillery). */
    val weapons: Map<Int, Int> = emptyMap(),
    /** Pertahanan rakitan Crafting Station musim ini. */
    val crafted: List<CraftedDef> = emptyList(),
) {
    fun item(id: Int): Item? = items[id]

    /** Senjata Town Hall yang bisa di-upgrade di level TH ini (TH12–16 tidak lagi punya level senjata). */
    fun weaponFor(th: Int): Item? = weapons[th]?.let { items[it] }

    fun craftedDef(id: Int): CraftedDef? = crafted.firstOrNull { it.id == id }

    fun name(id: Int): String = items[id]?.name ?: names[id.toString()] ?: "#$id"

    /** Jumlah bangunan [id] yang boleh dimiliki pada level hall ini (tanpa koreksi penggabungan). */
    fun allowedCount(id: Int, hall: Int, bb: Boolean): Int {
        val table = if (bb) bbUnlocks else homeUnlocks
        var n = 0
        for ((lvl, list) in table) if (lvl <= hall) for ((uid, q) in list) if (uid == id) n += q
        return n
    }

    /** Semua id bangunan yang muncul di tabel unlock. */
    fun unlockIds(bb: Boolean): Set<Int> =
        (if (bb) bbUnlocks else homeUnlocks).values.flatten().map { it.first }.toSet()

    companion object {
        const val TOWN_HALL = 1000001
        const val LAB = 1000007
        const val WALL = 1000010
        const val HUT = 1000015
        const val BOB_HUT = 1000064
        const val PET_HOUSE = 1000068
        const val HERO_HALL = 1000071
        const val HELPER_HUT = 1000093
        const val BUILDER_HALL = 1000034
        const val BB_WALL = 1000033
        const val STAR_LAB = 1000046
        const val BOTO_SHACK = 1000047
        const val OTTO_OUTPOST = 1000078

        const val CLOCK_TOWER = 1000039
        const val BOB_CONTROL = 1000065
        const val MULTI_GEAR = 1000079
        const val BATTLE_MACHINE = 28000003
        const val BATTLE_COPTER = 28000005

        const val APPRENTICE = 93000000
        const val LAB_ASSISTANT = 93000001
        const val ALCHEMIST = 93000002

        fun parse(text: String): GameData {
            val root = Json.parse(text).obj() ?: throw IllegalArgumentException("gamedata rusak")
            val items = HashMap<Int, Item>()
            for (raw in root["items"].arr().orEmpty()) {
                val o = raw.obj() ?: continue
                val cat = Cat.of(o["c"].str()) ?: continue
                val levels = o["lv"].arr().orEmpty().mapNotNull { l ->
                    val a = l.arr() ?: return@mapNotNull null
                    if (a.size < 5) null
                    else Lv(a[0].int(), a[1].long(), a[2].long(), a[3].int(), a[4].int())
                }
                val merges = HashMap<Int, List<MergeSrc>>()
                for (m in o["mg"].arr().orEmpty()) {
                    val a = m.arr() ?: continue
                    if (a.size < 2) continue
                    merges[a[0].int()] = a[1].arr().orEmpty().mapNotNull { s ->
                        val sa = s.arr() ?: return@mapNotNull null
                        if (sa.size < 3) null else MergeSrc(sa[0].int(), sa[1].int(), sa[2].bool())
                    }
                }
                val id = o["id"].int()
                // Item yang sama bisa muncul di Home & Builder Base (mis. id troop BB) — ambil yang pertama.
                if (!items.containsKey(id)) {
                    val instances = o["in"].arr().orEmpty().mapNotNull { l ->
                        val a = l.arr() ?: return@mapNotNull null
                        if (a.size < 4) null else Lv(a[0].int(), a[1].long(), a[2].long(), a[3].int(), 0)
                    }
                    items[id] = Item(
                        id, o["n"].str(), cat, o["t"].str(), o["r"].str(),
                        o["pb"].str(), o["pbl"].int(), levels, merges,
                        o["ic"]?.str(), o["il"].arr().orEmpty().map { it.int() }, instances,
                    )
                }
            }
            fun unlocks(o: Map<String, Any?>?): Map<Int, List<Pair<Int, Int>>> {
                val res = HashMap<Int, List<Pair<Int, Int>>>()
                o?.forEach { (k, v) ->
                    val lvl = k.toIntOrNull() ?: return@forEach
                    res[lvl] = v.arr().orEmpty().mapNotNull { p ->
                        val a = p.arr() ?: return@mapNotNull null
                        if (a.size < 2) null else a[0].int() to a[1].int()
                    }
                }
                return res
            }
            val unl = root["unlocks"].obj()
            val names = HashMap<String, String>()
            root["names"].obj()?.forEach { (k, v) -> names[k] = v.str() }
            val icons = HashMap<String, String>()
            root["icons"].obj()?.forEach { (k, v) -> icons[k] = v.str() }
            val weapons = HashMap<Int, Int>()
            root["weapons"].obj()?.forEach { (k, v) -> k.toIntOrNull()?.let { weapons[it] = v.int() } }
            val crafted = root["crafted"].arr().orEmpty().mapNotNull { raw ->
                val o = raw.obj() ?: return@mapNotNull null
                val mods = o["m"].arr().orEmpty().mapNotNull { mr ->
                    val m = mr.obj() ?: return@mapNotNull null
                    val lv = m["lv"].arr().orEmpty().mapNotNull { l ->
                        val a = l.arr() ?: return@mapNotNull null
                        if (a.size < 4) null else Lv(a[0].int(), a[1].long(), a[2].long(), a[3].int(), 0)
                    }
                    CraftedModule(m["id"].int(), m["n"].str(), m["s"].str(), m["r"].str(), lv)
                }
                CraftedDef(o["id"].int(), o["n"].str(), mods)
            }
            return GameData(
                root["v"].str(), root["maxTH"].int(18), root["maxBH"].int(10), items,
                unlocks(unl?.get("home").obj()), unlocks(unl?.get("bb").obj()), names, icons, weapons, crafted,
            )
        }
    }
}
