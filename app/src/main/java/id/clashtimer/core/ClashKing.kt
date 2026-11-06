package id.clashtimer.core

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Klien API publik ClashKing (https://api.clashk.ing, tanpa key). Data war/CWL di sini dikumpulkan pelacak
 * ClashKing, jadi bisa sedikit terlambat dibanding API resmi; sumbernya wajib disebut di app ("data dari ClashKing").
 *
 * Jalur publik yang dipakai (kontrak: github.com/ClashKingInc/ClashKingAPI, packages/api-contracts):
 * - `/v2/player/search?query=` — nama, TH & clan pemain (untuk nama otomatis).
 * - `/v2/player/{tag}/timers` — war / CWL / Raid yang sedang diikuti pemain, kapan berakhir, dan clannya.
 * - `/v2/war/{clanTag}/basic` — penunjuk war clan saat ini (jam persiapan & berakhir, jenis war).
 * - `/v2/cwl/{clanTag}/group` — grup CWL; tiap putaran bisa memuat war lengkap (anggota & serangannya).
 * Tag dikirim tanpa `#` (server menerima keduanya dan menormalkannya).
 */
object ClashKing {
    const val BASE = "https://api.clashk.ing/v2"
    const val CREDIT = "Data war & CWL: ClashKing (clashk.ing)"

    /** Clan pemain: clan yang muncul di semua war/CWL yang sedang diikuti (yang lain lawan). Null bila belum pasti. */
    fun clanOf(timers: List<Timer>): String? {
        val wars = timers.filter { it.type == "war" || it.type == "cwl" }.map { it.clans.toSet() }.filter { it.isNotEmpty() }
        if (wars.isEmpty()) return null
        val common = wars.reduce { a, b -> a intersect b }
        return common.singleOrNull()
    }

    class Player(val tag: String, val name: String, val townHall: Int, val clanTag: String?, val clanName: String?)

    /** Event yang sedang diikuti pemain: [type] = war, cwl atau capital. */
    class Timer(val type: String, val expiresAt: Long, val warTag: String?, val clans: List<String>)

    class WarPointer(val clanTag: String, val opponentTag: String, val preparationStart: Long, val end: Long, val type: String, val warTag: String?)

    /** Satu war di grup CWL. [attacks] = jumlah serangan per tag anggota clan kita (null bila datanya belum ada). */
    class CwlWar(
        val tag: String?, val state: String, val preparationStart: Long, val start: Long, val end: Long,
        val clanTag: String, val opponentTag: String, val attacks: Map<String, Int>?,
    )

    class CwlGroup(
        val state: String, val season: String, val clanTags: List<String>, val rounds: List<List<CwlWar>>, val lineup: Set<String>,
        /** Nama clan [clanTag] & liga CWL-nya. */
        val clanName: String, val league: String,
        /** Nama & TH anggota semua clan di grup (dari daftar anggota CWL). */
        val names: Map<String, String>,
    )

    // ------------------------------------------------------------------ jaringan

    /** Tag dalam bentuk jalur URL: tanpa `#`, huruf besar, O → 0 (seperti server). */
    fun pathTag(tag: String): String = tag.trim().uppercase().removePrefix("#").replace('O', '0')

    /** GET [path] di bawah [BASE]; null = 404 / data tidak ada. Galat jaringan dilempar sebagai IOException. */
    fun get(path: String, timeoutMs: Int = 15_000): String? {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        c.connectTimeout = timeoutMs
        c.readTimeout = timeoutMs
        c.setRequestProperty("Accept", "application/json")
        c.setRequestProperty("User-Agent", "ClashTimer (github.com/pomnom/ClashTimer)")
        try {
            val code = c.responseCode
            if (code == 404) return null
            val body = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw java.io.IOException("ClashKing $code: ${body.take(200)}")
            return body
        } finally {
            c.disconnect()
        }
    }

    fun player(tag: String): Player? =
        get("/player/search?query=" + URLEncoder.encode("#" + pathTag(tag), "UTF-8") + "&limit=5")?.let { parseSearch(it, tag) }

    fun timers(tag: String): List<Timer> = get("/player/${pathTag(tag)}/timers")?.let { parseTimers(it) } ?: emptyList()

    fun currentWar(clanTag: String): WarPointer? = get("/war/${pathTag(clanTag)}/basic")?.let { parseWarPointer(it) }

    fun cwlGroup(clanTag: String): CwlGroup? = get("/cwl/${pathTag(clanTag)}/group")?.let { parseCwlGroup(it, clanTag) }

    // ------------------------------------------------------------------ pembaca respons

    private fun norm(tag: String) = "#" + pathTag(tag)

    /** Waktu ClashKing: format Clash API (20261105T080000.000Z) atau ISO-8601. 0 bila kosong/tidak terbaca. */
    fun time(s: String?): Long {
        if (s.isNullOrBlank()) return 0
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(s.removeSuffix("Z"), DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS"))
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
            }.getOrDefault(0)
    }

    /** Hasil pencarian yang tagnya persis sama dengan [tag]. */
    fun parseSearch(json: String, tag: String): Player? {
        val want = norm(tag)
        val item = Json.parse(json).obj()?.get("items").arr().orEmpty().mapNotNull { it.obj() }
            .firstOrNull { norm(it["tag"].str()) == want } ?: return null
        val clan = item["clan"].obj()
        return Player(want, item["name"].str(), item["townHallLevel"].int(), clan?.get("tag")?.str()?.let(::norm), clan?.get("name")?.str())
    }

    fun parseTimers(json: String): List<Timer> =
        Json.parse(json).obj()?.get("items").arr().orEmpty().mapNotNull { it.obj() }.map { o ->
            Timer(o["type"].str(), time(o["expiresAt"].str()), o["warTag"]?.str()?.ifBlank { null },
                o["clans"].arr().orEmpty().map { norm(it.str()) })
        }

    fun parseWarPointer(json: String): WarPointer? {
        val o = Json.parse(json).obj() ?: return null
        return WarPointer(
            norm(o["clan"].obj()?.get("tag").str()), norm(o["opponent"].obj()?.get("tag").str()),
            time(o["preparationStartTime"].str()), time(o["endTime"].str()), o["type"].str(), o["warTag"]?.str()?.ifBlank { null },
        )
    }

    /** Grup CWL dilihat dari sisi [clanTag]; [CwlGroup.lineup] = anggota clan kita yang ada di war mana pun. */
    fun parseCwlGroup(json: String, clanTag: String): CwlGroup? {
        val o = Json.parse(json).obj() ?: return null
        val me = norm(clanTag)
        val lineup = HashSet<String>()
        val rounds = o["rounds"].arr().orEmpty().map { r ->
            r.obj()?.get("warTags").arr().orEmpty().mapNotNull { w ->
                val war = w.obj() ?: return@mapNotNull null
                val a = war["clan"].obj()
                val b = war["opponent"].obj() ?: return@mapNotNull null
                if (a == null) return@mapNotNull null       // hanya tag war, datanya belum ada
                val (ours, theirs) = when (me) {
                    norm(a["tag"].str()) -> a to b
                    norm(b["tag"].str()) -> b to a
                    else -> return@mapNotNull null
                }
                val members = ours["members"].arr().orEmpty().mapNotNull { it.obj() }
                members.forEach { lineup += norm(it["tag"].str()) }
                CwlWar(
                    war["tag"]?.str()?.ifBlank { null }, war["state"].str(), time(war["preparationStartTime"].str()),
                    time(war["startTime"].str()), time(war["endTime"].str()), norm(ours["tag"].str()), norm(theirs["tag"].str()),
                    if (members.isEmpty()) null else members.associate { norm(it["tag"].str()) to it["attacks"].arr().orEmpty().size },
                )
            }
        }
        val clans = o["clans"].arr().orEmpty().mapNotNull { it.obj() }
        val names = HashMap<String, String>()
        clans.forEach { c -> c["members"].arr().orEmpty().mapNotNull { it.obj() }.forEach { m -> names[norm(m["tag"].str())] = m["name"].str() } }
        val mine = clans.firstOrNull { norm(it["tag"].str()) == me }
        return CwlGroup(o["state"].str(), o["season"].str(), clans.map { norm(it["tag"].str()) }, rounds, lineup,
            mine?.get("name").str(), o["warLeague"].obj()?.get("name").str(), names)
    }
}
