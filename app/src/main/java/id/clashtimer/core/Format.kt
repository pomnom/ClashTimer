package id.clashtimer.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    val locale: Locale get() = Lang.locale
    private val dayTime get() = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", locale)
    private val dayOnly get() = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale)
    private val timeOnly = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)
    private val dateShort get() = DateTimeFormatter.ofPattern("d MMM", locale)

    /** Durasi ringkas: "2h 3j", "5j 10m", "12m" (English: "2d 3h", "5h 10m"). */
    fun dur(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 59_999) / 60_000 * 60 // bulatkan ke atas per menit
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        val (dd, hh) = if (Lang.en) "d" to "h" else "h" to "j"
        return when {
            d > 0 && h > 0 -> "${d}$dd ${h}$hh"
            d > 0 -> "${d}$dd"
            h > 0 && m > 0 -> "${h}$hh ${m}m"
            h > 0 -> "${h}$hh"
            else -> "${m}m"
        }
    }

    fun durSec(sec: Long): String = dur(sec * 1000)

    /** Total jam builder yang besar → "4,5 hari" / "4.5 days". */
    fun bigDur(ms: Long): String {
        val days = ms / DAY.toDouble()
        return if (days >= 2) String.format(locale, if (Lang.en) "%.1f days" else "%.1f hari", days) else dur(ms)
    }

    fun at(ms: Long, zone: ZoneId): String = dayTime.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun date(ms: Long, zone: ZoneId): String = dayOnly.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun time(ms: Long, zone: ZoneId): String = timeOnly.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun dateShort(ms: Long, zone: ZoneId): String = dateShort.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun clock(minOfDay: Int): String = String.format(Locale.ROOT, "%02d:%02d", (minOfDay / 60) % 24, minOfDay % 60)

    /** 1.250.000 → "1,25 jt", 450.000 → "450 rb" (English: "1.25M", "450K"). */
    fun money(n: Long): String = when {
        n >= 1_000_000 -> {
            val v = n / 1_000_000.0
            val sep = if (Lang.en) '.' else ','
            (if (v >= 100) String.format(locale, "%.0f", v) else String.format(locale, "%.2f", v).trimEnd('0').trimEnd(sep)) + if (Lang.en) "M" else " jt"
        }
        n >= 1_000 -> String.format(locale, "%.0f", n / 1000.0) + if (Lang.en) "K" else " rb"
        else -> n.toString()
    }

    fun resource(r: String): String = when (r) {
        "Dark Elixir" -> "DE"
        "Builder Gold" -> if (Lang.en) "BB Gold" else "Gold BB"
        "Builder Elixir" -> if (Lang.en) "BB Elixir" else "Elixir BB"
        else -> r
    }

    fun costs(map: Map<String, Long>): String =
        map.entries.filter { it.value > 0 }.sortedByDescending { it.value }
            .joinToString(" · ") { "${money(it.value)} ${resource(it.key)}" }
}
