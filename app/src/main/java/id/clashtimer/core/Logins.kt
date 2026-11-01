package id.clashtimer.core

import java.time.Instant
import java.time.ZoneId

/** Satu sesi login: langkah rencana yang dimulai berdekatan (≤ 45 menit), jadi cukup buka game sekali. */
class Session(val at: Long, val steps: List<PlannedStep>, val now: Boolean)

/**
 * Jadwal login dari rencana: kapan perlu membuka game dan apa yang dimulai saat itu. Dipakai kartu
 * "Buka game berikutnya", agenda Jadwal, dan pengingat per sesi.
 */
object Logins {
    private const val GAP = 45 * 60_000L
    private const val HOUR = 3_600_000L

    fun build(sim: SimResult?, bb: Boolean, now: Long, until: Long): List<Session> {
        if (sim == null) return emptyList()
        val steps = sim.steps.filter { it.lane.bb == bb && !it.running && it.start <= until }.sortedBy { it.start }
        val out = ArrayList<Session>()
        var cur = ArrayList<PlannedStep>()
        var start = 0L
        fun isNow(t: Long) = t <= now + 60_000L
        fun flush() {
            if (cur.isNotEmpty()) out += Session(start, cur.toList(), isNow(start))
        }
        for (s in steps) {
            if (cur.isNotEmpty() && s.start - start <= GAP && isNow(s.start) == isNow(start)) cur += s
            else {
                flush()
                cur = arrayListOf(s)
                start = s.start
            }
        }
        flush()
        return out
    }

    /** "B3", "Lab", "Pet", "M1", "Star". */
    fun label(s: PlannedStep): String = when (s.lane) {
        Lane.BUILDER -> "B${s.laneIndex}"
        Lane.BB_BUILDER -> "M${s.laneIndex}"
        Lane.LAB -> "Lab"
        Lane.PET -> "Pet"
        Lane.STAR_LAB -> "Star"
    }

    fun isLab(s: PlannedStep) = s.lane == Lane.LAB || s.lane == Lane.PET || s.lane == Lane.STAR_LAB

    /** "B3 & Lab bebas", "B1, B4 & Lab bebas". */
    fun workers(session: Session): String {
        val l = session.steps.map { label(it) }.distinct()
        val joined = if (l.size <= 1) l.joinToString() else l.dropLast(1).joinToString(", ") + " & " + l.last()
        return "$joined bebas"
    }

    fun title(s: PlannedStep): String = s.title.substringBefore("  ×").substringBefore(" (sisa")

    /** Isi pengingat: siapa yang bebas + saran yang dimulai. */
    fun note(session: Session): String =
        workers(session) + "\nSaran: " + session.steps.take(3).joinToString(" · ") { title(it) }

    /** "sblm tidur" bila tidur dimulai ≤ 2 jam setelah sesi, "bangun" bila sesi tepat setelah jam tidur. */
    fun mood(schedule: OfflineSchedule, at: Long): String? {
        if (schedule.isOffline(at - 30 * 60_000L) && inSleep(schedule, at - 30 * 60_000L)) return "bangun"
        val off = schedule.nextOfflineStart(at) ?: return null
        return if (off - at in 0..2 * HOUR && inSleep(schedule, off)) "sblm tidur" else null
    }

    /** Jam [t] jatuh di jam tidur (bukan jam kerja). */
    fun inSleep(schedule: OfflineSchedule, t: Long): Boolean {
        val w = schedule.sleep
        if (!w.enabled || w.startMin == w.endMin) return false
        val z = Instant.ofEpochMilli(t).atZone(schedule.zone)
        val m = z.hour * 60 + z.minute
        return if (w.startMin < w.endMin) m in w.startMin until w.endMin else m >= w.startMin || m < w.endMin
    }

    /**
     * Akibat memulai saran [p] sekarang: "selesai 06:25 · pas bangun" (1), "menganggur 3j saat tidur" (-1),
     * atau "bisa diisi lagi sebelum tidur" / hanya jam selesai (0). [fmt] = format jam selesai.
     */
    fun outcome(p: Scored, schedule: OfflineSchedule, now: Long, fmt: (Long) -> String): Pair<String, Int> {
        val f = "selesai " + fmt(p.finish)
        if (p.idle > 30 * 60_000L) {
            val where = if (inSleep(schedule, p.finish)) "saat tidur" else "saat offline"
            return "$f · menganggur ${Fmt.dur(p.idle)} $where" to -1
        }
        // Selesai sesaat sebelum bangun (menunggu ≤ 30 menit) atau ≤ 45 menit setelah bangun.
        val woke = schedule.windowsBetween(p.finish - 45 * 60_000L, p.finish).any { it.last < p.finish }
        if (p.idle > 0 || woke) return "$f · pas bangun" to 1
        val off = schedule.nextOfflineStart(now)
        if (off != null && p.finish < off && off - p.finish < 6 * HOUR) return "$f · bisa diisi lagi sebelum tidur" to 0
        return f to 0
    }
}
