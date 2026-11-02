package id.clashtimer.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** Jendela harian (mis. tidur 23:00–06:00). Menit sejak tengah malam. */
data class DailyWindow(
    val enabled: Boolean,
    val startMin: Int,
    val endMin: Int,
    val days: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
)

/** Kapan pengguna tidak bisa membuka game (tidur / kerja). */
class OfflineSchedule(
    val sleep: DailyWindow,
    val work: DailyWindow,
    val zone: ZoneId,
) {
    private val cache = HashMap<Long, List<LongRange>>()

    /** Jendela offline (sudah digabung) di sekitar hari [day] (epoch day lokal). */
    private fun windowsForDay(day: Long): List<LongRange> = cache.getOrPut(day) {
        val raw = ArrayList<LongRange>()
        for (d in (day - 1)..(day + 1)) {
            val date = LocalDate.ofEpochDay(d)
            for (w in listOf(sleep, work)) {
                if (!w.enabled || w.startMin == w.endMin) continue
                if (date.dayOfWeek !in w.days) continue
                val s = date.atStartOfDay(zone).plusMinutes(w.startMin.toLong()).toInstant().toEpochMilli()
                val endDate = if (w.endMin > w.startMin) date else date.plusDays(1)
                val e = endDate.atStartOfDay(zone).plusMinutes(w.endMin.toLong()).toInstant().toEpochMilli()
                raw += s until e
            }
        }
        merge(raw)
    }

    private fun merge(list: List<LongRange>): List<LongRange> {
        val sorted = list.sortedBy { it.first }
        val out = ArrayList<LongRange>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                out[out.size - 1] = last.first..maxOf(last.last, r.last)
            } else out += r
        }
        return out
    }

    private fun localDay(t: Long): Long =
        Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toEpochDay()

    fun windowAt(t: Long): LongRange? = windowsForDay(localDay(t)).firstOrNull { t in it }

    fun isOffline(t: Long): Boolean = windowAt(t) != null

    /** Waktu online paling awal ≥ [t]. */
    fun nextOnline(t: Long): Long {
        var cur = t
        repeat(10) {
            val w = windowAt(cur) ?: return cur
            cur = w.last + 1
        }
        return cur
    }

    /** Awal jendela offline berikutnya setelah [t] (dalam 3 hari), atau null. */
    fun nextOfflineStart(t: Long): Long? {
        val day = localDay(t)
        for (d in day..(day + 3)) {
            windowsForDay(d).firstOrNull { it.first > t }?.let { return it.first }
        }
        return null
    }

    /** Semua jendela offline yang bersinggungan dengan [a, b]. */
    fun windowsBetween(a: Long, b: Long): List<LongRange> {
        val all = ArrayList<LongRange>()
        var d = localDay(a) - 1
        val end = localDay(b) + 1
        while (d <= end) { all += windowsForDay(d); d++ }
        return merge(all).filter { it.first <= b && it.last >= a }
    }

    val anyEnabled: Boolean get() = (sleep.enabled && sleep.startMin != sleep.endMin) || (work.enabled && work.startMin != work.endMin)
}

/**
 * Clan War League. Default mengikuti asumsi umum: mulai tanggal 1 pukul 08:00 UTC
 * (16:00 WITA), berlangsung 11 hari.
 */
class CwlRule(
    val enabled: Boolean,
    val startDay: Int = 1,
    val startUtcHour: Int = 8,
    val days: Int = 11,
    /** Jadwal CWL asli dari API (menggantikan perkiraan bulan yang sama). */
    val known: List<LongRange> = emptyList(),
) {
    private fun windowForMonth(year: Int, month: Int): LongRange {
        val day = startDay.coerceIn(1, 28)
        val s = ZonedDateTime.of(year, month, day, startUtcHour.coerceIn(0, 23), 0, 0, 0, ZoneOffset.UTC)
        val e = s.plusDays(days.toLong().coerceIn(1, 27))
        return s.toInstant().toEpochMilli() until e.toInstant().toEpochMilli()
    }

    private fun monthKey(t: Long): Int {
        val z = Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC)
        return z.year * 12 + z.monthValue
    }

    /** Apakah jendela ini jadwal asli (bukan perkiraan). */
    fun isKnown(w: LongRange): Boolean = known.any { it.first == w.first && it.last == w.last }

    fun windowsAround(t: Long): List<LongRange> {
        val z = Instant.ofEpochMilli(t).atZone(ZoneOffset.UTC)
        val out = ArrayList<LongRange>()
        var m = z.withDayOfMonth(1).minusMonths(1)
        repeat(4) {
            val est = windowForMonth(m.year, m.monthValue)
            out += known.firstOrNull { monthKey(it.first) == monthKey(est.first) } ?: est
            m = m.plusMonths(1)
        }
        return out.sortedBy { it.first }
    }

    /** Jendela CWL yang bertabrakan dengan [start, end), atau null. */
    fun overlap(start: Long, end: Long): LongRange? {
        if (!enabled) return null
        val ws = windowsAround(start) + windowsAround(end)
        return ws.firstOrNull { it.first < end && it.last + 1 > start }
    }

    /** Jendela CWL berikutnya yang belum selesai pada [t]. */
    fun next(t: Long): LongRange = windowsAround(t).first { it.last >= t }
}

object Events {
    /** Event aktif untuk [lane] pada [t] (potongan waktu terbesar bila ada beberapa). */
    fun at(list: List<GameEvent>, lane: Lane, t: Long): GameEvent? =
        list.filter { it.activeAt(t) && it.covers(lane) }.maxByOrNull { it.timePct }

    /** Potongan untuk upgrade yang sudah berjalan sejak [startedAt]. */
    fun cutsFor(list: List<GameEvent>, lane: Lane, startedAt: Long): List<WorkCut> =
        list.filter { it.enabled && it.cutRunning && it.covers(lane) && it.start > startedAt && it.timePct > 0 }
            .map { WorkCut(it.start, (100 - it.timePct.coerceIn(0, 90)) / 100.0) }

    /** Event yang sedang berlangsung atau dimulai dalam [withinMs]. */
    fun upcoming(list: List<GameEvent>, now: Long, withinMs: Long): List<GameEvent> =
        list.filter { it.enabled && it.end > now && it.start - now < withinMs }.sortedBy { it.start }
}

/** Diskon Gold Pass (Builder Boost & Research Boost), dengan pembulatan seperti di game. */
/** Gold Pass naik di tengah musim (poin track): mulai [at], boost menjadi [builderPct] / [researchPct]. */
data class GpStep(val at: Long, val builderPct: Int, val researchPct: Int)

/**
 * Track Gold Pass musim ini: poin yang dibutuhkan untuk Research/Builder Boost 15% dan 20%
 * (boost 10% terbuka di awal track begitu Gold Pass dibeli).
 */
data class GpTrack(val r15: Int, val b15: Int, val r20: Int, val b20: Int) {
    fun builderAt(points: Int) = when { points >= b20 -> 20; points >= b15 -> 15; else -> 10 }
    fun researchAt(points: Int) = when { points >= r20 -> 20; points >= r15 -> 15; else -> 10 }

    /** Kapan tiap ambang tercapai dari [points] poin pada [at] dengan [perDay] poin per hari (sebelum [end]). */
    fun steps(points: Int, at: Long, perDay: Double, end: Long): List<GpStep> {
        if (perDay <= 0) return emptyList()
        val out = ArrayList<GpStep>()
        for (p in listOf(r15, b15, r20, b20).filter { it > points }.distinct().sorted()) {
            val t = at + ((p - points) / perDay * DAY).toLong()
            if (end > 0 && t >= end) break
            out += GpStep(t, builderAt(p), researchAt(p))
        }
        return out
    }

    companion object {
        /** Musim Clash of Titans (November 2026). */
        val DEFAULT = GpTrack(580, 640, 1000, 1060)
    }
}

data class GoldPass(val builderPct: Int, val researchPct: Int, val steps: List<GpStep> = emptyList()) {
    /** Gold Pass yang berlaku pada [t] (setelah kenaikan yang sudah tercapai). */
    fun at(t: Long): GoldPass {
        if (steps.isEmpty()) return this
        var b = builderPct
        var r = researchPct
        for (s in steps) if (s.at <= t) { b = maxOf(b, s.builderPct); r = maxOf(r, s.researchPct) }
        return GoldPass(b, r)
    }

    fun pctFor(lane: Lane): Int = when (lane) {
        Lane.BUILDER, Lane.BB_BUILDER -> builderPct
        Lane.LAB, Lane.PET -> researchPct
        Lane.STAR_LAB -> 0
    }

    /** Durasi (detik) setelah diskon Gold Pass dan (bila ada) potongan event [eventPct]. */
    fun apply(seconds: Long, lane: Lane, eventPct: Int = 0): Long {
        val pct = pctFor(lane).coerceIn(0, 90)
        val ev = eventPct.coerceIn(0, 90)
        if ((pct == 0 && ev == 0) || seconds <= 0) return seconds
        val t = seconds * (100 - pct) / 100.0 * (100 - ev) / 100.0
        return when {
            t < 1800 -> Math.ceil(t).toLong()
            t <= 86400 -> (Math.floor(t / 600) * 600).toLong()
            else -> (Math.floor(t / 3600) * 3600).toLong()
        }
    }

    companion object {
        val NONE = GoldPass(0, 0)
    }
}

/**
 * Event game (mis. Hammer Jam) yang memotong waktu & biaya upgrade yang DIMULAI selama event.
 * Tidak ada sumber resmi untuk jadwal event, jadi tanggalnya diisi/dikonfirmasi pengguna.
 */
data class GameEvent(
    val id: String,
    val name: String,
    val start: Long,
    val end: Long,
    val timePct: Int,
    val costPct: Int,
    /** Bangunan, jebakan & hero Home Village. */
    val builder: Boolean,
    /** Laboratory & Pet House. */
    val lab: Boolean,
    /** Builder Base (bangunan, hero, Star Lab). */
    val bb: Boolean,
    /** Upgrade yang sedang berjalan saat event mulai ikut dipotong. */
    val cutRunning: Boolean,
    val enabled: Boolean,
) {
    fun covers(lane: Lane): Boolean = when (lane) {
        Lane.BUILDER -> builder
        Lane.LAB, Lane.PET -> lab
        Lane.BB_BUILDER, Lane.STAR_LAB -> bb
    }

    fun activeAt(t: Long): Boolean = enabled && t >= start && t < end

    val scopeLabel: String
        get() = listOfNotNull(if (builder) "Builder" else null, if (lab) "Lab & Pet" else null, if (bb) "Builder Base" else null)
            .joinToString(", ").ifBlank { "tidak ada" }

    companion object {
        /** Event mulai/berakhir biasanya pukul 08:00 UTC. */
        private fun utc8(year: Int, month: Int, day: Int): Long =
            ZonedDateTime.of(year, month, day, 8, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

        private fun yearFor(now: Long, month: Int, day: Int): Int {
            val y = Instant.ofEpochMilli(now).atZone(ZoneOffset.UTC).year
            return if (utc8(y, month, day) < now) y + 1 else y
        }

        /** Hammer Jam: perkiraan 1–17 November (seperti 2025), −50% waktu & biaya. */
        fun hammerJam(now: Long): GameEvent {
            val y = yearFor(now, 11, 17)
            return GameEvent("hammer_$y", "Hammer Jam", utc8(y, 11, 1), utc8(y, 11, 17), 50, 50,
                builder = true, lab = true, bb = false, cutRunning = false, enabled = false)
        }

        /** Builder Base Bonanza: −50% waktu & biaya di Builder Base. Tanggal diisi sendiri. */
        fun bonanza(now: Long): GameEvent {
            val s = (now / DAY) * DAY + 8 * HOUR
            return GameEvent("bonanza_$now", "Builder Base Bonanza", s, s + 14 * DAY, 50, 50,
                builder = false, lab = false, bb = true, cutRunning = false, enabled = true)
        }

        fun custom(now: Long): GameEvent {
            val s = (now / DAY) * DAY + 8 * HOUR
            return GameEvent("event_$now", "Event", s, s + 7 * DAY, 50, 50,
                builder = true, lab = true, bb = false, cutRunning = false, enabled = true)
        }
    }
}
