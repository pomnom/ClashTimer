package id.clashtimer.core

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object Fmt {
    val locale: Locale = Locale.forLanguageTag("id-ID")
    private val dayTime = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", locale)
    private val dayOnly = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale)
    private val timeOnly = DateTimeFormatter.ofPattern("HH:mm", locale)
    private val dateShort = DateTimeFormatter.ofPattern("d MMM", locale)

    /** Durasi ringkas: "2h 3j", "5j 10m", "12m". (h = hari, j = jam, m = menit) */
    fun dur(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 59_999) / 60_000 * 60 // bulatkan ke atas per menit
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        return when {
            d > 0 && h > 0 -> "${d}h ${h}j"
            d > 0 -> "${d}h"
            h > 0 && m > 0 -> "${h}j ${m}m"
            h > 0 -> "${h}j"
            else -> "${m}m"
        }
    }

    fun durSec(sec: Long): String = dur(sec * 1000)

    /** Total jam builder yang besar → "123 jam" / "4,5 hari". */
    fun bigDur(ms: Long): String {
        val days = ms / DAY.toDouble()
        return if (days >= 2) String.format(locale, "%.1f hari", days) else dur(ms)
    }

    fun at(ms: Long, zone: ZoneId): String = dayTime.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun date(ms: Long, zone: ZoneId): String = dayOnly.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun time(ms: Long, zone: ZoneId): String = timeOnly.format(Instant.ofEpochMilli(ms).atZone(zone))
    fun dateShort(ms: Long, zone: ZoneId): String = dateShort.format(Instant.ofEpochMilli(ms).atZone(zone))

    fun clock(minOfDay: Int): String = String.format(Locale.ROOT, "%02d:%02d", (minOfDay / 60) % 24, minOfDay % 60)

    /** 1.250.000 → "1,25 jt", 450.000 → "450 rb". */
    fun money(n: Long): String = when {
        n >= 1_000_000 -> {
            val v = n / 1_000_000.0
            (if (v >= 100) String.format(locale, "%.0f", v) else String.format(locale, "%.2f", v).trimEnd('0').trimEnd(',')) + " jt"
        }
        n >= 1_000 -> String.format(locale, "%.0f", n / 1000.0) + " rb"
        else -> n.toString()
    }

    fun resource(r: String): String = when (r) {
        "Dark Elixir" -> "DE"
        "Builder Gold" -> "Gold BB"
        "Builder Elixir" -> "Elixir BB"
        else -> r
    }

    fun costs(map: Map<String, Long>): String =
        map.entries.filter { it.value > 0 }.sortedByDescending { it.value }
            .joinToString(" · ") { "${money(it.value)} ${resource(it.key)}" }
}
