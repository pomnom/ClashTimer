package id.clashtimer.core

const val MIN = 60_000L
const val HOUR = 3_600_000L
const val DAY = 86_400_000L

/** Lama siklus helper: bekerja 1 jam, cooldown 23 jam sejak ditugaskan. */
const val HELPER_CYCLE = 23 * HOUR
const val HELPER_WORK = HOUR

/** Potion & snack yang mempercepat pekerja. [extra] = tambahan kecepatan (10× → 9). */
enum class BoostKind(
    val label: String, val lanes: Set<Lane>, val extra: Double, val duration: Long,
    /** Boost dalam grup yang sama tidak menumpuk (yang baru memperpanjang). */
    val group: String,
) {
    BUILDER_POTION("Builder Potion", setOf(Lane.BUILDER), 9.0, HOUR, "builder_potion"),
    RESEARCH_POTION("Research Potion", setOf(Lane.LAB), 23.0, HOUR, "research_potion"),
    PET_POTION("Pet Potion", setOf(Lane.PET), 23.0, HOUR, "pet_potion"),
    BUILDER_BITE("Builder Bite", setOf(Lane.BUILDER), 1.0, HOUR, "builder_bite"),
    STUDY_SOUP("Study Soup", setOf(Lane.LAB, Lane.PET), 3.0, HOUR, "study_soup"),
    /** Durasi tergantung level Clock Tower (lihat [ClockTower.boostMinutes]). */
    CLOCK_TOWER("Clock Tower", setOf(Lane.BB_BUILDER, Lane.STAR_LAB), 9.0, 22 * MIN, "clock"),
    CLOCK_TOWER_POTION("Clock Tower Potion", setOf(Lane.BB_BUILDER, Lane.STAR_LAB), 9.0, 30 * MIN, "clock");

    val speedLabel: String get() = "${(extra + 1).toInt()}×"
}

/** Clock Tower Builder Base: 10× selama 12 + 2×level menit, cooldown 22 jam sejak boost selesai (data Supercell, cocok dengan export). */
object ClockTower {
    const val COOLDOWN = 22 * HOUR
    fun boostMinutes(level: Int): Int = if (level <= 0) 0 else 12 + 2 * level
}

/** Boost yang dicatat pengguna. Waktu dalam ms. */
data class Boost(val kind: BoostKind, val start: Long, val end: Long)

object Boosts {
    /**
     * Catat pemakaian boost pada [at]. Boost segrup tidak menumpuk: bila masih aktif,
     * durasinya diperpanjang (seperti di game).
     */
    fun add(list: List<Boost>, kind: BoostKind, at: Long, duration: Long = kind.duration): List<Boost> {
        val active = list.filter { it.kind.group == kind.group && it.end > at && it.start <= at }.maxByOrNull { it.end }
        return if (active != null) {
            list.map { if (it === active) it.copy(end = it.end + duration) else it }
        } else list + Boost(kind, at, at + duration)
    }

    /** Setelah import ulang: buang boost yang sudah habis sebelum export, potong sisanya. */
    fun afterImport(list: List<Boost>, exportAt: Long): List<Boost> =
        list.filter { it.end > exportAt }.map { if (it.start < exportAt) it.copy(start = exportAt) else it }

    /** Gabungkan interval boost segrup (tidak menumpuk). */
    fun merged(list: List<Boost>): List<Boost> {
        val out = ArrayList<Boost>()
        for ((_, group) in list.groupBy { it.kind.group }) {
            val sorted = group.sortedBy { it.start }
            var cur: Boost? = null
            for (b in sorted) {
                cur = when {
                    cur == null -> b
                    b.start <= cur.end -> cur.copy(end = maxOf(cur.end, b.end))
                    else -> { out += cur; b }
                }
            }
            if (cur != null) out += cur
        }
        return out
    }
}

/** Info helper (Builder's Apprentice / Lab Assistant) yang membantu satu upgrade. */
data class HelperPlan(val level: Int, val firstSession: Long)

data class Projection(
    val finishAt: Long,
    /** Waktu selesai tanpa helper & boost (langsung dari export). */
    val baseFinishAt: Long,
    /** Awal sesi helper yang benar-benar terpakai sebelum selesai. */
    val helperSessions: List<Long>,
)

/** Potongan sisa waktu sekali jalan (mis. event yang ikut memotong upgrade berjalan). */
data class WorkCut(val at: Long, val factor: Double)

object Projector {

    private class Seg(val start: Long, val end: Long, val extra: Double)

    /**
     * Hitung kapan upgrade selesai. [remainingSec] = sisa waktu saat [from] (biasanya waktu export).
     * Kecepatan = 1 + jumlah tambahan dari boost aktif & sesi helper (dijumlah, karena
     * potion tidak mempengaruhi helper dan sebaliknya). [cuts] mengalikan sisa kerja pada waktunya.
     */
    fun project(
        from: Long,
        remainingSec: Long,
        lane: Lane,
        boosts: List<Boost>,
        helper: HelperPlan?,
        cuts: List<WorkCut> = emptyList(),
        /** Hanya sesi helper yang mulai sebelum waktu ini (untuk "waktu di game" sebelum sesi berikutnya). */
        sessionsBefore: Long = Long.MAX_VALUE,
    ): Projection {
        val base = from + remainingSec * 1000
        val segs = ArrayList<Seg>()
        for (b in Boosts.merged(boosts)) {
            if (lane !in b.kind.lanes) continue
            val s = maxOf(b.start, from)
            if (b.end > s) segs += Seg(s, b.end, b.kind.extra)
        }
        val sessionStarts = ArrayList<Long>()
        if (helper != null && helper.level > 0) {
            // Sesi helper berulang tiap HELPER_CYCLE sejak sesi pertama; [from] bisa setelahnya
            // (mis. sisa waktu dikoreksi pengguna), jadi lompati sesi yang sudah lewat.
            var s = helper.firstSession
            while (s + HELPER_WORK <= from) s += HELPER_CYCLE
            while (s < base && s < sessionsBefore) {
                sessionStarts += s
                segs += Seg(maxOf(s, from), s + HELPER_WORK, helper.level.toDouble())
                s += HELPER_CYCLE
            }
        }
        val cutList = cuts.filter { it.at > from && it.at < base }.sortedBy { it.at }
        if (segs.isEmpty() && cutList.isEmpty()) return Projection(base, base, emptyList())

        val points = java.util.TreeSet<Long>()
        points += from
        points += base
        for (sg in segs) { points += sg.start; points += sg.end }
        for (c in cutList) points += c.at

        var work = (remainingSec * 1000).toDouble() // ms kerja normal yang tersisa
        var t = from
        var finish = -1L
        var ci = 0
        val iter = points.iterator()
        var prev = iter.next()
        while (iter.hasNext() && finish < 0) {
            val next = iter.next()
            if (next <= t) { prev = next; continue }
            val a = maxOf(prev, t)
            while (ci < cutList.size && cutList[ci].at <= a) { work *= cutList[ci].factor; ci++ }
            var speed = 1.0
            for (sg in segs) if (sg.start <= a && sg.end > a) speed += sg.extra
            val span = (next - a).toDouble()
            val capacity = span * speed
            if (work <= capacity) {
                finish = a + Math.ceil(work / speed).toLong()
            } else {
                work -= capacity
                t = next
            }
            prev = next
        }
        if (finish < 0) finish = t + Math.ceil(work).toLong()
        val used = sessionStarts.filter { it < finish }
        return Projection(finish, base, used)
    }

    /** Rencana helper untuk satu upgrade menurut export (hanya yang ditandai berulang). */
    fun helperFor(v: Village, lane: Lane, helped: Boolean): HelperPlan? {
        if (!helped) return null
        val id = when (lane) {
            Lane.BUILDER -> GameData.APPRENTICE
            Lane.LAB -> GameData.LAB_ASSISTANT
            else -> return null
        }
        val h = v.helper(id) ?: return null
        return HelperPlan(h.level, v.exportAt + h.cooldown * 1000)
    }
}
