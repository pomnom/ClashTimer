package id.clashtimer

import android.content.Context
import id.clashtimer.core.BBStrategy
import id.clashtimer.core.Boost
import id.clashtimer.core.BoostKind
import id.clashtimer.core.CwlRule
import id.clashtimer.core.DailyWindow
import id.clashtimer.core.GameEvent
import id.clashtimer.core.GoldPass
import id.clashtimer.core.Lane
import id.clashtimer.core.MagicItem
import id.clashtimer.core.OfflineSchedule
import id.clashtimer.core.PlanSettings
import id.clashtimer.core.PlanTarget
import id.clashtimer.core.Policy
import id.clashtimer.core.Started
import id.clashtimer.core.Strategy
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZoneId

/** Jenis antrean untuk timer & notifikasi. */
enum class Kind(val label: String, val emoji: String, val builderBase: Boolean) {
    BUILDER("Builder", "🔨", false),
    LAB("Laboratory", "🧪", false),
    PET("Pet House", "🐾", false),
    HELPER("Helper", "🧑‍🔧", false),
    BB_BUILDER("Builder Base", "🛠️", true),
    STAR_LAB("Star Lab", "⭐", true),
    /** Pengingat (Clock Tower siap, event, Gold Pass) — bukan pekerja. */
    REMINDER("Pengingat", "⏰", false);

    companion object {
        fun of(lane: Lane): Kind = when (lane) {
            Lane.BUILDER -> BUILDER
            Lane.LAB -> LAB
            Lane.PET -> PET
            Lane.BB_BUILDER -> BB_BUILDER
            Lane.STAR_LAB -> STAR_LAB
        }
    }
}

data class Upgrade(
    val id: String,
    val tag: String,
    val kind: Kind,
    val name: String,
    /** Level tujuan (level sekarang + 1). 0 untuk helper. */
    val targetLevel: Int,
    val finishAt: Long,
    /** Upgrade ini dibantu helper berulang. */
    val helped: Boolean,
    var earlyNotified: Boolean = false,
    var doneNotified: Boolean = false,
    /** Waktu selesai menurut export saja (tanpa helper & potion). */
    val baseFinishAt: Long = finishAt,
    /** Keterangan tambahan (helper, potion, dll). */
    val note: String = "",
    val customTitle: String = "",
    /**
     * Sesi helper berulang yang diperkirakan: (mulai sesi, waktu selesai bila sesi ini & sesudahnya belum
     * terjadi). Dipakai untuk menampilkan waktu yang terlihat di game sekarang.
     */
    val helperSteps: List<Pair<Long, Long>> = emptyList(),
    /** Dikerjakan Goblin Builder / Goblin Researcher (event), bukan builder/lab tetap. */
    val extra: Boolean = false,
) {
    fun title(): String = when {
        customTitle.isNotBlank() -> customTitle
        kind == Kind.HELPER -> "$name siap dipakai"
        targetLevel > 0 -> "$name → Lv $targetLevel"
        else -> name
    }

    /**
     * Waktu selesai menurut timer di game pada [now]: game belum menghitung sesi helper yang belum mulai,
     * sedangkan [finishAt] sudah memperhitungkannya.
     */
    fun gameFinishAt(now: Long): Long = helperSteps.firstOrNull { it.first > now }?.second ?: finishAt

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("tag", tag).put("kind", kind.name).put("name", name)
        .put("lvl", targetLevel).put("finishAt", finishAt).put("helped", helped)
        .put("early", earlyNotified).put("done", doneNotified)
        .put("base", baseFinishAt).put("note", note).put("title", customTitle)
        .apply {
            if (extra) put("x", true)
            if (helperSteps.isNotEmpty()) {
                put("hs", JSONArray().apply { helperSteps.forEach { (s, f) -> put(JSONArray().put(s).put(f)) } })
            }
        }

    companion object {
        fun fromJson(o: JSONObject): Upgrade {
            val finish = o.getLong("finishAt")
            val hs = o.optJSONArray("hs")
            val steps = if (hs == null) emptyList() else (0 until hs.length()).mapNotNull { i ->
                hs.optJSONArray(i)?.takeIf { it.length() >= 2 }?.let { it.optLong(0) to it.optLong(1) }
            }
            return Upgrade(
                id = o.getString("id"),
                tag = o.getString("tag"),
                kind = runCatching { Kind.valueOf(o.getString("kind")) }.getOrDefault(Kind.BUILDER),
                name = o.getString("name"),
                targetLevel = o.optInt("lvl"),
                finishAt = finish,
                helped = o.optBoolean("helped"),
                earlyNotified = o.optBoolean("early"),
                doneNotified = o.optBoolean("done"),
                baseFinishAt = o.optLong("base", finish),
                note = o.optString("note", ""),
                customTitle = o.optString("title", ""),
                helperSteps = steps,
                extra = o.optBoolean("x"),
            )
        }
    }
}

data class Account(
    val tag: String,
    val builders: Int,
    val exportedAt: Long,
    val th: Int = 0,
    val bh: Int = 0,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("tag", tag).put("builders", builders).put("exportedAt", exportedAt)
        .put("th", th).put("bh", bh)

    companion object {
        fun fromJson(o: JSONObject) = Account(
            o.getString("tag"), o.optInt("builders", 5), o.optLong("exportedAt"),
            o.optInt("th"), o.optInt("bh"),
        )
    }
}

/** Penyimpanan sederhana di SharedPreferences (data hanya ada di HP ini). */
object Store {
    const val PREFS = "clash_timer"
    private val lock = Any()

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- timer

    fun upgrades(ctx: Context): MutableList<Upgrade> = synchronized(lock) {
        val raw = prefs(ctx).getString("upgrades", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val list = mutableListOf<Upgrade>()
        for (i in 0 until arr.length()) {
            runCatching { list += Upgrade.fromJson(arr.getJSONObject(i)) }
        }
        list
    }

    fun saveUpgrades(ctx: Context, list: List<Upgrade>) = synchronized(lock) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString("upgrades", arr.toString()).commit()
    }

    fun accounts(ctx: Context): MutableList<Account> = synchronized(lock) {
        val raw = prefs(ctx).getString("accounts", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val list = mutableListOf<Account>()
        for (i in 0 until arr.length()) {
            runCatching { list += Account.fromJson(arr.getJSONObject(i)) }
        }
        list
    }

    fun saveAccounts(ctx: Context, list: List<Account>) = synchronized(lock) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs(ctx).edit().putString("accounts", arr.toString()).commit()
    }

    /** Ganti semua timer milik akun ini dengan hasil import terbaru. */
    fun replaceAccount(ctx: Context, account: Account, fresh: List<Upgrade>, earlyMinutes: Int) = synchronized(lock) {
        val now = System.currentTimeMillis()
        val earlyMs = earlyMinutes * 60_000L
        fresh.forEach {
            // Jangan kirim notifikasi untuk hal yang sudah lewat saat di-import.
            if (it.finishAt <= now) { it.doneNotified = true; it.earlyNotified = true }
            else if (earlyMs > 0 && it.finishAt - earlyMs <= now) it.earlyNotified = true
        }
        val others = upgrades(ctx).filter { it.tag != account.tag }
        saveUpgrades(ctx, others + fresh)
        saveAccounts(ctx, accounts(ctx).filter { it.tag != account.tag } + account)
    }

    /**
     * Perbarui waktu selesai (setelah potion / book / tandai selesai) dengan tetap
     * menghormati notifikasi yang sudah terkirim.
     */
    fun mergeProjections(ctx: Context, tag: String, fresh: List<Upgrade>, doneIds: Set<String>) = synchronized(lock) {
        val all = upgrades(ctx)
        val old = all.filter { it.tag == tag }.associateBy { it.id }
        val merged = fresh.map { u ->
            val o = old[u.id]
            when {
                u.id in doneIds -> u.also { it.doneNotified = true; it.earlyNotified = true }
                o != null && o.finishAt == u.finishAt -> u.also { it.doneNotified = o.doneNotified; it.earlyNotified = o.earlyNotified }
                o != null -> u.also { it.doneNotified = false; it.earlyNotified = o.earlyNotified && u.finishAt >= o.finishAt }
                else -> u
            }
        }
        saveUpgrades(ctx, all.filter { it.tag != tag } + merged)
    }

    fun removeAccount(ctx: Context, tag: String) = synchronized(lock) {
        saveUpgrades(ctx, upgrades(ctx).filter { it.tag != tag })
        saveAccounts(ctx, accounts(ctx).filter { it.tag != tag })
        val e = prefs(ctx).edit()
            .remove("export_$tag").remove("boosts_$tag").remove("done_$tag")
            .remove("cancel_$tag").remove("bumps_$tag").remove("items_$tag")
            .remove("name_$tag").remove("started_$tag").remove("pct_$tag")
            .remove("clock_used_$tag").remove("api_player_$tag").remove("cwl_s_$tag").remove("cwl_e_$tag")
            .remove("cwl_auto_$tag").remove("war_$tag").remove("gpdet_b_$tag").remove("gpdet_r_$tag").remove("bob_$tag")
            .remove("pins_$tag").remove("hides_$tag").remove("api_stats_$tag")
            .remove("hist_$tag").remove("ulog_$tag").remove("hist_start_$tag").remove("ore_$tag").remove("opt_$tag")
            .remove("sessrem_$tag").remove("gp_pts_$tag").remove("gp_rate_$tag").remove("fix_$tag")
        VILLAGE_KEYS.forEach { e.remove("${it}_$tag") }
        e.commit()
        if (selectedTag(ctx) == tag) prefs(ctx).edit().remove("selected_tag").apply()
    }

    /** Buang upgrade yang sudah selesai lebih dari 24 jam lalu. */
    fun pruneOld(ctx: Context) = synchronized(lock) {
        val cutoff = System.currentTimeMillis() - 24 * 3600_000L
        val list = upgrades(ctx)
        val kept = list.filter { it.finishAt > cutoff }
        if (kept.size != list.size) saveUpgrades(ctx, kept)
    }

    // ---------------------------------------------------------------- data per akun

    /** Nama desa: pilihan pengguna, lalu nickname dari API, lalu tag. */
    fun villageName(ctx: Context, tag: String): String =
        prefs(ctx).getString("name_$tag", null)?.takeIf { it.isNotBlank() }
            ?: apiPlayer(ctx, tag)?.name?.takeIf { it.isNotBlank() }
            ?: tag

    fun setVillageName(ctx: Context, tag: String, name: String) {
        prefs(ctx).edit().putString("name_$tag", name.trim()).apply()
    }

    /** Persentase menuju max terakhir yang dihitung (untuk daftar desa). */
    fun lastPct(ctx: Context, tag: String): Float = prefs(ctx).getFloat("pct_$tag", -1f)
    fun setLastPct(ctx: Context, tag: String, pct: Float) = prefs(ctx).edit().putFloat("pct_$tag", pct).apply()

    /** Upgrade yang ditandai "sudah dimulai" di aplikasi sejak export terakhir. */
    fun started(ctx: Context, tag: String): List<Started> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("started_$tag", "[]")) }.getOrDefault(JSONArray())
        val out = ArrayList<Started>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val lane = runCatching { Lane.valueOf(o.getString("l")) }.getOrNull() ?: continue
            out += Started(o.getString("u"), o.getInt("i"), o.getInt("f"), lane, o.optString("n"), o.getLong("s"), o.getLong("d"),
                o.optBoolean("g"))
        }
        return out
    }

    fun setStarted(ctx: Context, tag: String, list: List<Started>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("u", it.uid).put("i", it.itemId).put("f", it.fromLevel).put("l", it.lane.name)
                .put("n", it.name).put("s", it.start).put("d", it.durationSec).put("g", it.gear))
        }
        prefs(ctx).edit().putString("started_$tag", arr.toString()).commit()
    }

    fun exportText(ctx: Context, tag: String): String? = prefs(ctx).getString("export_$tag", null)
    fun saveExport(ctx: Context, tag: String, text: String) {
        prefs(ctx).edit().putString("export_$tag", text).commit()
    }

    fun boosts(ctx: Context, tag: String): List<Boost> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("boosts_$tag", "[]")) }.getOrDefault(JSONArray())
        val out = ArrayList<Boost>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val k = runCatching { BoostKind.valueOf(o.getString("k")) }.getOrNull() ?: continue
            out += Boost(k, o.getLong("s"), o.getLong("e"))
        }
        return out
    }

    fun setBoosts(ctx: Context, tag: String, list: List<Boost>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("k", it.kind.name).put("s", it.start).put("e", it.end)) }
        prefs(ctx).edit().putString("boosts_$tag", arr.toString()).commit()
    }

    /** uid timer → kapan ditandai selesai (book, gem, dsb). */
    fun done(ctx: Context, tag: String): Map<String, Long> = readLongMap(ctx, "done_$tag")
    fun setDone(ctx: Context, tag: String, m: Map<String, Long>) = writeLongMap(ctx, "done_$tag", m)

    /** uid timer → kapan dibatalkan. */
    fun cancelled(ctx: Context, tag: String): Map<String, Long> = readLongMap(ctx, "cancel_$tag")
    fun setCancelled(ctx: Context, tag: String, m: Map<String, Long>) = writeLongMap(ctx, "cancel_$tag", m)

    /** Level yang naik karena hammer: (id item, level awal, waktu). */
    fun bumps(ctx: Context, tag: String): List<Triple<Int, Int, Long>> {
        val arr = runCatching { JSONArray(prefs(ctx).getString("bumps_$tag", "[]")) }.getOrDefault(JSONArray())
        val out = ArrayList<Triple<Int, Int, Long>>()
        for (i in 0 until arr.length()) {
            val a = arr.optJSONArray(i) ?: continue
            out += Triple(a.optInt(0), a.optInt(1), a.optLong(2))
        }
        return out
    }

    fun setBumps(ctx: Context, tag: String, list: List<Triple<Int, Int, Long>>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONArray().put(it.first).put(it.second).put(it.third)) }
        prefs(ctx).edit().putString("bumps_$tag", arr.toString()).commit()
    }

    fun items(ctx: Context, tag: String): Map<MagicItem, Int> {
        val o = runCatching { JSONObject(prefs(ctx).getString("items_$tag", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val m = HashMap<MagicItem, Int>()
        for (mi in MagicItem.entries) {
            val n = o.optInt(mi.name, 0)
            if (n > 0) m[mi] = n
        }
        return m
    }

    fun setItem(ctx: Context, tag: String, item: MagicItem, count: Int) {
        val o = runCatching { JSONObject(prefs(ctx).getString("items_$tag", "{}") ?: "{}") }.getOrDefault(JSONObject())
        o.put(item.name, count.coerceIn(0, 999))
        prefs(ctx).edit().putString("items_$tag", o.toString()).commit()
    }

    private fun readLongMap(ctx: Context, key: String): Map<String, Long> {
        val o = runCatching { JSONObject(prefs(ctx).getString(key, "{}") ?: "{}") }.getOrDefault(JSONObject())
        val m = HashMap<String, Long>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            m[k] = o.optLong(k)
        }
        return m
    }

    private fun writeLongMap(ctx: Context, key: String, m: Map<String, Long>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, v) }
        prefs(ctx).edit().putString(key, o.toString()).commit()
    }

    // ---------------------------------------------------------------- pengaturan notifikasi

    fun earlyMinutes(ctx: Context) = prefs(ctx).getInt("early_min", 0)
    fun setEarlyMinutes(ctx: Context, v: Int) = prefs(ctx).edit().putInt("early_min", v).apply()

    /** true = setAlarmClock (paling tepat waktu, ada ikon jam di status bar). */
    fun strongAlarm(ctx: Context) = prefs(ctx).getBoolean("strong_alarm", true)
    fun setStrongAlarm(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("strong_alarm", v).apply()

    /** Jam istirahat: notifikasi masuk tanpa suara & getar (jam tidur aktif bawaan, jam kerja tidak). */
    fun quietSleep(ctx: Context) = prefs(ctx).getBoolean("quiet_sleep", true)
    fun setQuietSleep(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("quiet_sleep", v).apply()
    fun quietWork(ctx: Context) = prefs(ctx).getBoolean("quiet_work", false)
    fun setQuietWork(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("quiet_work", v).apply()
    /** Satu notifikasi bersuara saat jam istirahat selesai, berisi yang masuk diam-diam. */
    fun quietSummary(ctx: Context) = prefs(ctx).getBoolean("quiet_summary", true)
    fun setQuietSummary(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("quiet_summary", v).apply()

    fun quietPending(ctx: Context): List<String> = runCatching {
        val a = JSONArray(prefs(ctx).getString("quiet_pending", "[]"))
        (0 until a.length()).map { a.getString(it) }
    }.getOrDefault(emptyList())

    fun addQuietPending(ctx: Context, line: String) {
        val list = (quietPending(ctx) + line).takeLast(20)
        prefs(ctx).edit().putString("quiet_pending", JSONArray(list).toString()).apply()
    }

    fun clearQuietPending(ctx: Context) = prefs(ctx).edit().remove("quiet_pending").apply()

    fun notifyHelpers(ctx: Context) = prefs(ctx).getBoolean("notify_helpers", true)
    fun setNotifyHelpers(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("notify_helpers", v).apply()

    // ---------------------------------------------------------------- versi data & migrasi

    /** Naikkan bila cara membangun timer berubah (timer lama dibangun ulang dari export). */
    const val DATA_VERSION = 4

    fun dataVersion(ctx: Context) = prefs(ctx).getInt("data_version", 0)
    fun setDataVersion(ctx: Context, v: Int) = prefs(ctx).edit().putInt("data_version", v).commit()

    // ---------------------------------------------------------------- Clock Tower

    /** Kapan boost gratis Clock Tower terakhir dipakai (0 = tidak diketahui). */
    /** Waktu boost gratis Clock Tower terakhir selesai (cooldown 22 jam dimulai dari sini); 0 = siap. */
    fun clockUsed(ctx: Context, tag: String): Long = prefs(ctx).getLong("clock_used_$tag", 0L)
    fun setClockUsed(ctx: Context, tag: String, t: Long) = prefs(ctx).edit().putLong("clock_used_$tag", t).commit()

    // ---------------------------------------------------------------- clipboard

    /** Timestamp clipboard terakhir yang sudah diperiksa. */
    fun clipSeen(ctx: Context): Long = prefs(ctx).getLong("clip_seen", 0L)
    fun setClipSeen(ctx: Context, t: Long) = prefs(ctx).edit().putLong("clip_seen", t).apply()

    /** Export di clipboard yang sudah ditolak ("Nanti"): "tag|waktu". */
    fun clipDismissed(ctx: Context): String = prefs(ctx).getString("clip_dismissed", "") ?: ""
    fun setClipDismissed(ctx: Context, key: String) = prefs(ctx).edit().putString("clip_dismissed", key).apply()

    // ---------------------------------------------------------------- API resmi (lewat proxy RoyaleAPI)

    fun apiKey(ctx: Context): String = prefs(ctx).getString("api_key", "") ?: ""
    fun setApiKey(ctx: Context, key: String) = prefs(ctx).edit().putString("api_key", key.trim()).commit()

    fun apiStatus(ctx: Context): String = prefs(ctx).getString("api_status", "") ?: ""
    fun apiTried(ctx: Context): Long = prefs(ctx).getLong("api_tried", 0L)
    fun setApiResult(ctx: Context, status: String, at: Long) =
        prefs(ctx).edit().putString("api_status", status).putLong("api_tried", at).commit()

    /** Data pemain dari API: nama, clan, level hall & hero. */
    class ApiPlayer(
        val name: String, val clan: String, val clanTag: String, val th: Int, val bh: Int,
        val heroes: Map<String, Int>, val at: Long,
        /** Level troop, spell, pet & hero: "h:Nama" (Home Village) atau "b:Nama" (Builder Base). */
        val units: Map<String, Int> = emptyMap(),
        /** War preference di game: "in" / "out". */
        val warPref: String = "",
    )

    fun apiPlayer(ctx: Context, tag: String): ApiPlayer? {
        val o = runCatching { JSONObject(prefs(ctx).getString("api_player_$tag", null) ?: return null) }.getOrNull() ?: return null
        val heroes = HashMap<String, Int>()
        o.optJSONObject("h")?.let { h -> h.keys().forEach { k -> heroes[k] = h.optInt(k) } }
        val units = HashMap<String, Int>()
        o.optJSONObject("u")?.let { u -> u.keys().forEach { k -> units[k] = u.optInt(k) } }
        return ApiPlayer(o.optString("n"), o.optString("c"), o.optString("ct"), o.optInt("th"), o.optInt("bh"), heroes, o.optLong("at"),
            units, o.optString("w"))
    }

    fun setApiPlayer(ctx: Context, tag: String, p: ApiPlayer) {
        val h = JSONObject()
        p.heroes.forEach { (k, v) -> h.put(k, v) }
        val u = JSONObject()
        p.units.forEach { (k, v) -> u.put(k, v) }
        val o = JSONObject().put("n", p.name).put("c", p.clan).put("ct", p.clanTag).put("th", p.th).put("bh", p.bh)
            .put("h", h).put("at", p.at).put("u", u).put("w", p.warPref)
        prefs(ctx).edit().putString("api_player_$tag", o.toString()).commit()
    }

    /**
     * Status CWL desa ini menurut API: "roster" (terdaftar di CWL clan), "notroster" (clan ikut CWL
     * tapi akun ini tidak), "open" (di clan, belum ada CWL → perkiraan tgl 1), "optout" (war
     * preference out), "noclan" (tidak di clan).
     */
    class CwlAuto(val state: String, val clan: String, val at: Long) {
        /** Upgrade hero yang bentrok dengan CWL dilewati. */
        val counts: Boolean get() = state == "roster" || state == "open"
    }

    fun cwlAuto(ctx: Context, tag: String): CwlAuto? {
        val s = prefs(ctx).getString("cwl_auto_$tag", null) ?: return null
        return runCatching { JSONObject(s).let { CwlAuto(it.getString("s"), it.optString("c"), it.optLong("at")) } }.getOrNull()
    }

    fun setCwlAuto(ctx: Context, tag: String, a: CwlAuto) {
        val before = cwlAuto(ctx, tag)?.state
        val e = prefs(ctx).edit().putString("cwl_auto_$tag", JSONObject().put("s", a.state).put("c", a.clan).put("at", a.at).toString())
        if (before != a.state) e.putInt("plan_version", prefs(ctx).getInt("plan_version", 0) + 1)
        e.commit()
    }

    /** Gold Pass yang terdeteksi dari lama timer saat import (per desa; Builder atau Research Boost). */
    class GoldDetected(val pct: Int, val at: Long, val title: String, val guessed: Boolean = false)

    private fun goldKey(tag: String, research: Boolean) = (if (research) "gpdet_r_" else "gpdet_b_") + tag

    fun goldDetected(ctx: Context, tag: String, research: Boolean): GoldDetected? {
        val s = prefs(ctx).getString(goldKey(tag, research), null) ?: return null
        return runCatching {
            JSONObject(s).let { GoldDetected(it.getInt("p"), it.getLong("a"), it.optString("t"), it.optBoolean("g")) }
        }.getOrNull()
    }

    fun setGoldDetected(ctx: Context, tag: String, research: Boolean, d: GoldDetected) {
        val o = JSONObject().put("p", d.pct).put("a", d.at).put("t", d.title).put("g", d.guessed)
        prefs(ctx).edit().putString(goldKey(tag, research), o.toString()).commit()
    }

    // ---------------------------------------------------------------- prioritas & sembunyikan

    private fun idSet(ctx: Context, key: String): Set<Int> =
        prefs(ctx).getString(key, "").orEmpty().split(',').mapNotNull { it.trim().toIntOrNull() }.toSet()

    private fun putIdSet(ctx: Context, key: String, ids: Set<Int>) {
        prefs(ctx).edit().putString(key, ids.sorted().joinToString(","))
            .putInt("plan_version", prefs(ctx).getInt("plan_version", 0) + 1).commit()
    }

    /** Item yang didahulukan di saran desa ini. */
    fun pins(ctx: Context, tag: String): Set<Int> = idSet(ctx, "pins_$tag")
    fun setPins(ctx: Context, tag: String, ids: Set<Int>) = putIdSet(ctx, "pins_$tag", ids)

    /** Item yang disembunyikan dari saran & rencana desa ini (tetap dihitung di progres). */
    fun hides(ctx: Context, tag: String): Set<Int> = idSet(ctx, "hides_$tag")
    fun setHides(ctx: Context, tag: String, ids: Set<Int>) = putIdSet(ctx, "hides_$tag", ids)

    /** Statistik akun & clan dari API (JSON ringkas, lihat ApiSync). */
    fun apiStats(ctx: Context, tag: String): JSONObject? =
        prefs(ctx).getString("api_stats_$tag", null)?.let { runCatching { JSONObject(it) }.getOrNull() }

    fun setApiStats(ctx: Context, tag: String, o: JSONObject) = prefs(ctx).edit().putString("api_stats_$tag", o.toString()).apply()

    /** Isian kalkulator ore (ore dimiliki, pemasukan per minggu, equipment terpilih & level target). */
    fun oreState(ctx: Context, tag: String): JSONObject =
        prefs(ctx).getString("ore_$tag", null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

    fun setOreState(ctx: Context, tag: String, o: JSONObject) = prefs(ctx).edit().putString("ore_$tag", o.toString()).apply()

    /** B.O.B sudah terbuka (dari export): strategi Builder Base otomatis Max. */
    fun bobDone(ctx: Context, tag: String): Boolean = prefs(ctx).getBoolean("bob_$tag", false)

    fun setBobDone(ctx: Context, tag: String, done: Boolean) {
        if (bobDone(ctx, tag) == done) return
        prefs(ctx).edit().putBoolean("bob_$tag", done).putInt("plan_version", prefs(ctx).getInt("plan_version", 0) + 1).commit()
    }

    /**
     * Status serangan war / CWL dari API saat sinkron terakhir. [kind] "cwl" atau "war"; [state]
     * "preparation" / "inWar"; [end] = akhir hari perang (CWL: hari ini, atau hari 1 saat persiapan);
     * [left] = serangan tersisa; [lineup] = akun ini ikut war itu; [needEnd] = akhir hari perang berikutnya
     * yang masih butuh serangan akun ini (0 = tidak ada lagi); [day]/[days] = ronde CWL.
     */
    class WarState(
        val kind: String, val state: String, val end: Long, val left: Int, val lineup: Boolean,
        val needEnd: Long, val day: Int, val days: Int, val at: Long,
    ) {
        fun json(): String = JSONObject().put("k", kind).put("s", state).put("e", end).put("l", left).put("u", lineup)
            .put("n", needEnd).put("d", day).put("ds", days).put("at", at).toString()
    }

    fun warState(ctx: Context, tag: String): WarState? {
        val s = prefs(ctx).getString("war_$tag", null) ?: return null
        return runCatching {
            JSONObject(s).let {
                WarState(it.getString("k"), it.optString("s"), it.optLong("e"), it.optInt("l"), it.optBoolean("u"),
                    it.optLong("n"), it.optInt("d"), it.optInt("ds"), it.optLong("at"))
            }
        }.getOrNull()
    }

    /** Simpan status war; rencana dihitung ulang bila batas hero CWL berubah. */
    fun setWarState(ctx: Context, tag: String, w: WarState?) {
        val before = warState(ctx, tag)
        val e = prefs(ctx).edit()
        if (w == null) e.remove("war_$tag") else e.putString("war_$tag", w.json())
        val changed = (before?.kind == "cwl" || w?.kind == "cwl") &&
            (before?.kind != w?.kind || before?.needEnd != w?.needEnd || before?.left != w?.left)
        if (changed) e.putInt("plan_version", prefs(ctx).getInt("plan_version", 0) + 1)
        e.commit()
    }

    /** Helper yang Anda tugaskan dari Dashboard: kapan & ke upgrade mana (uid, bisa kosong). */
    fun helperUsed(ctx: Context, tag: String, id: Int): Pair<Long, String?>? {
        val s = prefs(ctx).getString("helper_used_${tag}_$id", null) ?: return null
        return runCatching { JSONObject(s).let { it.getLong("at") to it.optString("u").takeIf { u -> u.isNotBlank() } } }.getOrNull()
    }

    fun setHelperUsed(ctx: Context, tag: String, id: Int, at: Long, uid: String?) =
        prefs(ctx).edit().putString("helper_used_${tag}_$id", JSONObject().put("at", at).put("u", uid ?: "").toString()).commit()

    /** Jadwal CWL asli clan desa ini (ms): mulai persiapan ronde 1 – akhir ronde 7. */
    fun cwlKnown(ctx: Context, tag: String): LongRange? {
        val s = prefs(ctx).getLong("cwl_s_$tag", 0L)
        val e = prefs(ctx).getLong("cwl_e_$tag", 0L)
        return if (s > 0 && e > s) s until e else null
    }

    fun setCwlKnown(ctx: Context, tag: String, start: Long, end: Long) =
        prefs(ctx).edit().putLong("cwl_s_$tag", start).putLong("cwl_e_$tag", end)
            .putInt("plan_version", prefs(ctx).getInt("plan_version", 0) + 1).commit()

    /** Musim Gold Pass (dari API, sama untuk semua akun). */
    // ---------------------------------------------------------------- Gold Pass bertahap (poin track)

    /** Ambang poin boost 15% / 20% musim ini (sama untuk semua desa). */
    fun gpTrack(ctx: Context): id.clashtimer.core.GpTrack = runCatching {
        val o = JSONObject(prefs(ctx).getString("gp_track", null) ?: return id.clashtimer.core.GpTrack.DEFAULT)
        id.clashtimer.core.GpTrack(o.getInt("r15"), o.getInt("b15"), o.getInt("r20"), o.getInt("b20"))
    }.getOrDefault(id.clashtimer.core.GpTrack.DEFAULT)

    fun setGpTrack(ctx: Context, t: id.clashtimer.core.GpTrack) = prefs(ctx).edit().putString(
        "gp_track", JSONObject().put("r15", t.r15).put("b15", t.b15).put("r20", t.r20).put("b20", t.b20).toString(),
    ).putInt("plan_version", planVersion(ctx) + 1).apply()

    // ---------------------------------------------------------------- koreksi sisa waktu

    /** Sisa waktu yang dikoreksi pengguna: uid timer → (kapan, sisa detik seperti di game). Dihapus saat import. */
    fun fixes(ctx: Context, tag: String): Map<String, Pair<Long, Long>> = runCatching {
        val o = JSONObject(prefs(ctx).getString("fix_$tag", "{}"))
        o.keys().asSequence().associateWith { k -> o.getJSONArray(k).let { it.getLong(0) to it.getLong(1) } }
    }.getOrDefault(emptyMap())

    fun setFix(ctx: Context, tag: String, uid: String, at: Long, remainingSec: Long) {
        val o = runCatching { JSONObject(prefs(ctx).getString("fix_$tag", "{}")) }.getOrDefault(JSONObject())
        o.put(uid, JSONArray().put(at).put(remainingSec))
        prefs(ctx).edit().putString("fix_$tag", o.toString()).putInt("plan_version", planVersion(ctx) + 1).apply()
    }

    fun clearFixes(ctx: Context, tag: String) = prefs(ctx).edit().remove("fix_$tag").apply()

    /** Poin Gold Pass yang diisi pengguna (waktu, poin) — hanya musim ini. */
    fun gpPoints(ctx: Context, tag: String): List<Pair<Long, Int>> {
        val start = goldPassStart(ctx)
        return runCatching {
            val a = JSONArray(prefs(ctx).getString("gp_pts_$tag", "[]"))
            (0 until a.length()).map { a.getJSONArray(it).let { e -> e.getLong(0) to e.getInt(1) } }
        }.getOrDefault(emptyList()).filter { it.first >= start }.sortedBy { it.first }
    }

    fun addGpPoints(ctx: Context, tag: String, points: Int) {
        val list = (gpPoints(ctx, tag) + (System.currentTimeMillis() to points)).takeLast(12)
        val a = JSONArray()
        for ((t, p) in list) a.put(JSONArray().put(t).put(p))
        prefs(ctx).edit().putString("gp_pts_$tag", a.toString()).putInt("plan_version", planVersion(ctx) + 1).apply()
    }

    /** Poin per hari yang diisi pengguna (0 = otomatis dari riwayat poin). */
    fun gpRateManual(ctx: Context, tag: String): Int = prefs(ctx).getInt("gp_rate_$tag", 0)
    fun setGpRateManual(ctx: Context, tag: String, perDay: Int) =
        prefs(ctx).edit().putInt("gp_rate_$tag", perDay).putInt("plan_version", planVersion(ctx) + 1).apply()

    /** Perkiraan poin per hari: isian pengguna, atau dari dua catatan poin yang berjarak ≥ 1 hari, atau 90. */
    fun gpRate(ctx: Context, tag: String): Double {
        gpRateManual(ctx, tag).takeIf { it > 0 }?.let { return it.toDouble() }
        val pts = gpPoints(ctx, tag)
        val last = pts.lastOrNull() ?: return GP_DEFAULT_RATE
        val first = pts.firstOrNull { last.first - it.first >= DAY_MS } ?: return GP_DEFAULT_RATE
        val days = (last.first - first.first).toDouble() / DAY_MS
        return ((last.second - first.second) / days).takeIf { it > 0 } ?: GP_DEFAULT_RATE
    }

    private const val GP_DEFAULT_RATE = 90.0
    private const val DAY_MS = 86_400_000L

    /**
     * Gold Pass dengan kenaikan 15% / 20% yang diperkirakan dari poin terakhir (bila diisi musim ini dan
     * desa punya Gold Pass). Persen sekarang = yang terbesar dari isian/deteksi dan perkiraan dari poin.
     */
    private fun staged(ctx: Context, tag: String?, base: GoldPass): GoldPass {
        if (tag == null || (base.builderPct == 0 && base.researchPct == 0)) return base
        val last = gpPoints(ctx, tag).lastOrNull() ?: return base
        val track = gpTrack(ctx)
        val steps = track.steps(last.second, last.first, gpRate(ctx, tag), goldPassEnd(ctx))
        val all = GoldPass(maxOf(base.builderPct, track.builderAt(last.second)), maxOf(base.researchPct, track.researchAt(last.second)), steps)
        val now = System.currentTimeMillis()
        val cur = all.at(now)
        return GoldPass(cur.builderPct, cur.researchPct, steps.filter { it.at > now })
    }

    fun goldPassEnd(ctx: Context): Long = prefs(ctx).getLong("gp_end", 0L)
    fun setGoldPassEnd(ctx: Context, end: Long) = prefs(ctx).edit().putLong("gp_end", end).commit()
    fun goldPassStart(ctx: Context): Long = prefs(ctx).getLong("gp_start", 0L)
    fun setGoldPassStart(ctx: Context, start: Long) = prefs(ctx).edit().putLong("gp_start", start).commit()

    // ---------------------------------------------------------------- tampilan

    /** Ikon bangunan dari internet (assets.clashk.ing). */
    fun iconsOn(ctx: Context) = prefs(ctx).getBoolean("icons_on", true)
    fun setIconsOn(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("icons_on", v).apply()

    // ---------------------------------------------------------------- event

    /** Daftar event (Hammer Jam, dll). Default: Hammer Jam perkiraan, belum aktif. */
    fun events(ctx: Context): List<GameEvent> {
        val raw = prefs(ctx).getString("events", null) ?: return listOf(GameEvent.hammerJam(System.currentTimeMillis()))
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        val out = ArrayList<GameEvent>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            runCatching {
                out += GameEvent(
                    o.getString("id"), o.optString("n", "Event"), o.getLong("s"), o.getLong("e"),
                    o.optInt("tp", 50), o.optInt("cp", 50), o.optBoolean("b", true), o.optBoolean("l", true),
                    o.optBoolean("bb", false), o.optBoolean("cut", false), o.optBoolean("on", true),
                )
            }
        }
        return out
    }

    fun setEvents(ctx: Context, list: List<GameEvent>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("id", it.id).put("n", it.name).put("s", it.start).put("e", it.end)
                .put("tp", it.timePct).put("cp", it.costPct).put("b", it.builder).put("l", it.lab).put("bb", it.bb)
                .put("cut", it.cutRunning).put("on", it.enabled))
        }
        prefs(ctx).edit().putString("events", arr.toString())
            .putInt("plan_version", prefs(ctx).getInt("plan_version", 0) + 1).commit()
    }

    // ---------------------------------------------------------------- nada notifikasi

    /** key: "done" (upgrade selesai & helper) atau "early" (pengingat sebelum selesai). */
    fun toneMode(ctx: Context, key: String): ToneMode =
        runCatching { ToneMode.valueOf(prefs(ctx).getString("tone_$key", "DEFAULT") ?: "DEFAULT") }.getOrDefault(ToneMode.DEFAULT)

    fun toneUri(ctx: Context, key: String): String? = prefs(ctx).getString("toneuri_$key", null)

    fun setTone(ctx: Context, key: String, mode: ToneMode, uri: String?) {
        prefs(ctx).edit()
            .putString("tone_$key", mode.name)
            .putString("toneuri_$key", uri)
            .putInt("chver_$key", channelVersion(ctx, key) + 1)
            .commit()
    }

    /** Versi channel notifikasi; dinaikkan setiap nada diganti (channel Android tidak bisa diubah). */
    fun channelVersion(ctx: Context, key: String): Int = prefs(ctx).getInt("chver_$key", 1)

    // ---------------------------------------------------------------- pengaturan perencana

    /**
     * Pengaturan yang berlaku per desa: Gold Pass, CWL, strategi saran, builder BB, collector.
     * Desa yang belum punya nilai sendiri memakai nilai global lama (versi sebelumnya) sebagai awal.
     * Jam tidur/kerja, event, nada & notifikasi tetap untuk semua desa.
     */
    val VILLAGE_KEYS = setOf(
        "gp_builder", "gp_research", "cwl_on", "cwl_days",
        "strategy", "bb_strategy", "bb_builders", "skip_collectors",
    )

    private fun keyFor(key: String, tag: String?): String = if (tag != null && key in VILLAGE_KEYS) "${key}_$tag" else key

    fun getInt(ctx: Context, key: String, def: Int, tag: String? = null): Int {
        val p = prefs(ctx)
        val k = keyFor(key, tag)
        return if (p.contains(k)) p.getInt(k, def) else p.getInt(key, def)
    }

    fun getBool(ctx: Context, key: String, def: Boolean, tag: String? = null): Boolean {
        val p = prefs(ctx)
        val k = keyFor(key, tag)
        return if (p.contains(k)) p.getBoolean(k, def) else p.getBoolean(key, def)
    }

    fun getStr(ctx: Context, key: String, def: String, tag: String? = null): String {
        val p = prefs(ctx)
        val k = keyFor(key, tag)
        return (if (p.contains(k)) p.getString(k, def) else p.getString(key, def)) ?: def
    }

    /**
     * Simpan pengaturan perencana dan naikkan nomor versinya (supaya saran dihitung ulang).
     * Untuk kunci per desa, [tag] menentukan desanya (null = nilai awal untuk desa baru).
     */
    fun putPlan(ctx: Context, key: String, value: Any, tag: String? = null) {
        val k = keyFor(key, tag)
        val e = prefs(ctx).edit()
        when (value) {
            is Int -> e.putInt(k, value)
            is Boolean -> e.putBoolean(k, value)
            is String -> e.putString(k, value)
        }
        e.putInt("plan_version", prefs(ctx).getInt("plan_version", 0) + 1).apply()
    }

    fun planVersion(ctx: Context) = prefs(ctx).getInt("plan_version", 0)

    /** Pengingat sesi login yang dinyalakan pengguna (jam + isi notifikasi). */
    class SessionReminder(val at: Long, val text: String)

    fun sessionReminders(ctx: Context, tag: String): List<SessionReminder> = runCatching {
        val a = JSONArray(prefs(ctx).getString("sessrem_$tag", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> SessionReminder(o.getLong("t"), o.optString("x")) } }
    }.getOrDefault(emptyList())

    fun setSessionReminders(ctx: Context, tag: String, list: List<SessionReminder>) {
        val a = JSONArray()
        for (r in list) a.put(JSONObject().put("t", r.at).put("x", r.text))
        prefs(ctx).edit().putString("sessrem_$tag", a.toString()).apply()
    }

    /** Urutan tercepat terakhir (Planner.optimize) satu desa; [sig] = data & pengaturan saat dihitung. */
    class OptCache(val sig: String, val at: Long, val policies: Map<Lane, Policy>, val home: PlanTarget?, val bb: PlanTarget?)

    private fun policiesJson(m: Map<Lane, Policy>) = JSONObject().apply { for ((k, v) in m) put(k.name, v.name) }

    private fun policiesOf(o: JSONObject?): Map<Lane, Policy> {
        if (o == null) return emptyMap()
        val out = HashMap<Lane, Policy>()
        for (k in o.keys()) {
            val lane = runCatching { Lane.valueOf(k) }.getOrNull() ?: continue
            out[lane] = runCatching { Policy.valueOf(o.getString(k)) }.getOrNull() ?: continue
        }
        return out
    }

    private fun targetJson(t: PlanTarget) = JSONObject().apply {
        put("t", t.target ?: 0L)
        put("l", t.legacy ?: 0L)
        put("n", t.tried)
        put("p", policiesJson(t.policies))
    }

    private fun targetOf(o: JSONObject?): PlanTarget? = o?.let {
        PlanTarget(it.optLong("t").takeIf { x -> x > 0 }, it.optLong("l").takeIf { x -> x > 0 }, it.optInt("n"), policiesOf(it.optJSONObject("p")))
    }

    fun optCache(ctx: Context, tag: String): OptCache? = runCatching {
        val o = JSONObject(prefs(ctx).getString("opt_$tag", null) ?: return null)
        OptCache(o.getString("sig"), o.getLong("at"), policiesOf(o.optJSONObject("pol")), targetOf(o.optJSONObject("home")), targetOf(o.optJSONObject("bb")))
    }.getOrNull()

    /** Disimpan tanpa menaikkan versi rencana (hanya hasil hitungan). */
    fun setOptCache(ctx: Context, tag: String, c: OptCache) {
        val o = JSONObject()
            .put("sig", c.sig).put("at", c.at).put("pol", policiesJson(c.policies))
        c.home?.let { o.put("home", targetJson(it)) }
        c.bb?.let { o.put("bb", targetJson(it)) }
        prefs(ctx).edit().putString("opt_$tag", o.toString()).apply()
    }

    fun selectedTag(ctx: Context): String? = prefs(ctx).getString("selected_tag", null)
    fun setSelectedTag(ctx: Context, tag: String) = prefs(ctx).edit().putString("selected_tag", tag).apply()

    /** Bit 0 = Senin … bit 6 = Minggu. */
    fun daysFromMask(mask: Int): Set<DayOfWeek> = DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

    const val DEFAULT_WORK_DAYS = 0b0011111

    /** Pengaturan perencana untuk desa [tag] (null = nilai awal untuk desa baru). */
    /**
     * Batas hero CWL dari status serangan: hero yang selesai sebelum waktu ini masih sempat dipakai untuk
     * serangan berikutnya (akhir hari perang itu − 3 jam). Tidak ada serangan lagi → akhir CWL.
     */
    fun cwlFreeUntil(ctx: Context, tag: String, window: LongRange?): Long {
        val w = warState(ctx, tag)?.takeIf { it.kind == "cwl" } ?: return 0L
        val cwlEnd = window?.let { it.last + 1 } ?: return 0L
        if (w.at !in window.first - 2 * id.clashtimer.core.DAY until cwlEnd) return 0L
        return if (w.needEnd <= 0L) cwlEnd else (w.needEnd - CWL_MARGIN).coerceAtMost(cwlEnd)
    }

    /** Hero harus sudah selesai paling lambat 3 jam sebelum hari perang berakhir (sama dengan pengingat serangan). */
    const val CWL_MARGIN = 3 * 3_600_000L

    fun planSettings(ctx: Context, tag: String?): PlanSettings {
        val p = prefs(ctx)
        val sleep = DailyWindow(p.getBoolean("sleep_on", true), p.getInt("sleep_start", 23 * 60), p.getInt("sleep_end", 6 * 60))
        val work = DailyWindow(
            p.getBoolean("work_on", false), p.getInt("work_start", 8 * 60), p.getInt("work_end", 16 * 60),
            daysFromMask(p.getInt("work_days", DEFAULT_WORK_DAYS)),
        )
        val known = if (tag != null) listOfNotNull(cwlKnown(ctx, tag)) else emptyList()
        // Dengan API: ikut CWL atau tidak ditentukan otomatis (roster clan & war preference).
        val auto = if (tag != null) cwlAuto(ctx, tag) else null
        val cwl = CwlRule(auto?.counts ?: getBool(ctx, "cwl_on", true, tag), 1, 8, getInt(ctx, "cwl_days", 11, tag), known,
            if (tag != null) cwlFreeUntil(ctx, tag, known.firstOrNull()) else 0L)
        val gold = staged(ctx, tag, GoldPass(getInt(ctx, "gp_builder", 0, tag), getInt(ctx, "gp_research", 0, tag)))
        val strategy = runCatching { Strategy.valueOf(getStr(ctx, "strategy", "MAX", tag)) }.getOrDefault(Strategy.MAX)
        val bbChosen = runCatching { BBStrategy.valueOf(getStr(ctx, "bb_strategy", "SIXTH", tag)) }.getOrDefault(BBStrategy.SIXTH)
        // B.O.B sudah terbuka: jalur builder ke-6 selesai, jadi otomatis Max.
        val bbStrategy = if (tag != null && bobDone(ctx, tag)) BBStrategy.MAX else bbChosen
        return PlanSettings(
            OfflineSchedule(sleep, work, ZoneId.systemDefault()), cwl, gold, strategy, getInt(ctx, "bb_builders", 0, tag),
            bbStrategy, getBool(ctx, "skip_collectors", false, tag), events(ctx),
            if (tag != null) pins(ctx, tag) else emptySet(), if (tag != null) hides(ctx, tag) else emptySet(),
            if (tag != null) optCache(ctx, tag)?.policies ?: emptyMap() else emptyMap(),
        )
    }
}
