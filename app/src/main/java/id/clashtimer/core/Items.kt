package id.clashtimer.core

/** Item ajaib yang bisa dicatat. [icon] = jalur di assets.clashk.ing. */
enum class MagicItem(val label: String, val emoji: String, val boost: BoostKind? = null, val icon: String) {
    BUILDER_POTION("Builder Potion", "🧪", BoostKind.BUILDER_POTION, "magic_items/builder_potion.webp"),
    RESEARCH_POTION("Research Potion", "⚗️", BoostKind.RESEARCH_POTION, "magic_items/research_potion.webp"),
    PET_POTION("Pet Potion", "🐾", BoostKind.PET_POTION, "magic_items/pet_potion.webp"),
    BUILDER_BITE("Builder Bite", "🍪", BoostKind.BUILDER_BITE, "magic_snacks/builder_bite.webp"),
    STUDY_SOUP("Study Soup", "🍲", BoostKind.STUDY_SOUP, "magic_snacks/study_soup.webp"),
    CLOCK_TOWER_POTION("Clock Tower Potion", "⏱️", BoostKind.CLOCK_TOWER_POTION, "magic_items/clock_tower_potion.webp"),
    BOOK_BUILDING("Book of Building", "📘", icon = "magic_items/book_of_building.webp"),
    BOOK_HEROES("Book of Heroes", "📕", icon = "magic_items/book_of_heroes.webp"),
    BOOK_FIGHTING("Book of Fighting", "📗", icon = "magic_items/book_of_fighting.webp"),
    BOOK_SPELLS("Book of Spells", "📙", icon = "magic_items/book_of_spells.webp"),
    BOOK_EVERYTHING("Book of Everything", "📚", icon = "magic_items/book_of_everything.webp"),
    HAMMER_BUILDING("Hammer of Building", "🔨", icon = "magic_items/hammer_of_building.webp"),
    HAMMER_HEROES("Hammer of Heroes", "⚒️", icon = "magic_items/hammer_of_heroes.webp"),
    HAMMER_FIGHTING("Hammer of Fighting", "🪓", icon = "magic_items/hammer_of_fighting.webp"),
    HAMMER_SPELLS("Hammer of Spells", "🔧", icon = "magic_items/hammer_of_spells.webp");

    val isBook: Boolean get() = name.startsWith("BOOK_")
    val isHammer: Boolean get() = name.startsWith("HAMMER_")
    /** Item untuk Builder Base. */
    val forBB: Boolean get() = boost?.lanes?.all { it.bb } == true

    /** Apakah item ini bisa dipakai pada kategori tersebut. */
    fun fits(cat: Cat): Boolean = when (this) {
        BOOK_BUILDING, HAMMER_BUILDING -> cat == Cat.BUILDING || cat == Cat.TRAP
        BOOK_HEROES, HAMMER_HEROES -> cat == Cat.HERO
        BOOK_FIGHTING, HAMMER_FIGHTING -> cat == Cat.TROOP
        BOOK_SPELLS, HAMMER_SPELLS -> cat == Cat.SPELL
        BOOK_EVERYTHING -> !cat.bb
        else -> false
    }
}

class ItemTip(val item: MagicItem, val owned: Int, val headline: String, val detail: String, val valueMs: Long)

object ItemAdvisor {

    /** Nilai potion bila dipakai pada [at]: total jam kerja yang dihemat semua pekerja sibuk. */
    fun potionValue(kind: BoostKind, running: List<Pair<Lane, Long>>, at: Long): Pair<Long, Int> {
        var saved = 0.0
        var n = 0
        val speed = kind.extra + 1
        for ((lane, finish) in running) {
            if (lane !in kind.lanes || finish <= at) continue
            n++
            val rem = (finish - at).toDouble()
            val cap = kind.duration * speed
            saved += if (rem >= cap) kind.duration * kind.extra else rem - rem / speed
        }
        return saved.toLong() to n
    }

    fun tips(
        v: Village, gd: GameData, ps: PlanSettings, now: Long, finishes: Map<String, Long>,
        owned: Map<MagicItem, Int>,
    ): List<ItemTip> {
        val out = ArrayList<ItemTip>()
        val running = v.insts.filter { it.upgrading }.map { it to (finishes[it.uid] ?: (v.exportAt + it.remaining * 1000)) }
            .filter { it.second > now }
        // Gear-up (Master Builder di Home Village) tidak ikut dipercepat potion / Clock Tower.
        val runLanes = running.filter { !it.first.gearing }.map { it.first.lane to it.second }

        // Potion: selalu tampil (paling sering dipakai)
        for (mi in listOf(MagicItem.BUILDER_POTION, MagicItem.RESEARCH_POTION, MagicItem.PET_POTION, MagicItem.CLOCK_TOWER_POTION)) {
            val kind = mi.boost!!
            val lane = kind.lanes.first()
            val total = Planner.laneCount(v, lane, ps)
            if (total == 0) continue
            val (saved, busy) = potionValue(kind, runLanes, now)
            val maxPossible = kind.duration * kind.extra.toLong() * total
            val detail = when {
                busy == 0 -> "Tidak ada yang sedang berjalan — simpan dulu."
                busy < total -> "${total - busy} ${if (lane.bb) "Master Builder" else lane.label.lowercase()} masih menganggur; isi dulu supaya potion bernilai penuh."
                saved >= maxPossible * 0.95 -> "Waktu yang bagus: semua sibuk dengan upgrade panjang."
                else -> "Ada upgrade yang selesai < ${(kind.extra + 1).toInt()} jam lagi; nilai belum maksimal."
            }
            if (busy == 0 && (owned[mi] ?: 0) == 0) continue
            out += ItemTip(mi, owned[mi] ?: 0, "Sekarang hemat ${Fmt.dur(saved)} kerja", detail, saved)
        }

        // Book: selesaikan upgrade berjalan terlama yang cocok
        for (mi in MagicItem.entries.filter { it.isBook }) {
            val n = owned[mi] ?: 0
            if (n <= 0) continue
            val best = running.filter { mi.fits(it.first.item.cat) }.maxByOrNull { it.second }
            if (best == null) {
                out += ItemTip(mi, n, "Belum ada upgrade yang cocok berjalan", "Pakai pada upgrade terpanjang supaya paling hemat.", 0)
            } else {
                val (inst, fin) = best
                val title = if (inst.gearing) "${inst.item.name} · Gear-up" else "${inst.item.name} → Lv ${inst.level + 1}"
                out += ItemTip(mi, n, title, "Hemat ${Fmt.dur(fin - now)} — upgrade berjalan terlama yang cocok.", fin - now)
            }
        }

        // Hammer: selesaikan satu level terpanjang yang tersedia (tanpa perlu dimulai)
        for (mi in MagicItem.entries.filter { it.isHammer }) {
            val n = owned[mi] ?: 0
            if (n <= 0) continue
            val lanes = listOf(Lane.BUILDER, Lane.LAB)
            // Town Hall hanya disarankan untuk strategi Rush (menaikkan TH = rush).
            val cands = lanes.flatMap { Planner.candidates(v, gd, it, ps.gold, includeHall = ps.strategy == Strategy.RUSH, skip = ps.planSkip) }
                .filter { !it.isNew && mi.fits(it.item.cat) }
            val best = cands.maxByOrNull { it.duration }
            if (best == null) {
                out += ItemTip(mi, n, "Belum ada target", "Semua yang cocok sudah max atau sedang di-upgrade.", 0)
            } else {
                out += ItemTip(mi, n, "${best.item.name} → Lv ${best.toLevel}",
                    "Hemat ${Fmt.durSec(best.duration)} — upgrade tersedia paling lama.", best.duration * 1000)
            }
        }
        return out
    }
}
