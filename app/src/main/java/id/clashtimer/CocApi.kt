package id.clashtimer

import android.content.Context
import id.clashtimer.core.ClashKing
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.Json
import id.clashtimer.core.arr
import id.clashtimer.core.bool
import id.clashtimer.core.int
import id.clashtimer.core.long
import id.clashtimer.core.obj
import id.clashtimer.core.str
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * API resmi Clash of Clans lewat proxy RoyaleAPI (cocproxy.royaleapi.dev). Key dibuat & ditempel tiap pengguna
 * sendiri (developer.clashofclans.com, IP [PROXY_IP]); key hanya disimpan di HP itu dan tidak ikut cadangan.
 * API ini tidak menyediakan data event (Hammer Jam dll).
 */
object CocApi {
    const val BASE = "https://cocproxy.royaleapi.dev/v1"
    const val PROXY_IP = "45.79.218.79"
    const val DEV_SITE = "https://developer.clashofclans.com"

    class ApiError(val code: Int, message: String) : Exception(message)

    fun enc(tag: String): String = URLEncoder.encode(tag.trim().uppercase(), "UTF-8")

    /** GET [path] → objek JSON. 404 → null bila [allow404]. */
    fun get(key: String, path: String, allow404: Boolean = false): Map<String, Any?>? {
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Authorization", "Bearer $key")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code == 404 && allow404) return null
            if (code !in 200..299) {
                val reason = runCatching { Json.parse(text).obj()?.get("reason").str() }.getOrDefault("")
                throw ApiError(code, explain(code, reason))
            }
            return Json.parse(text).obj() ?: throw ApiError(code, tr("Jawaban API tidak bisa dibaca", "Could not read the API response"))
        } finally {
            conn.disconnect()
        }
    }

    private fun explain(code: Int, reason: String): String = when {
        code == 403 && reason.contains("invalidIp", true) ->
            tr("Akses ditolak: key tidak dibuat untuk IP $PROXY_IP. Buat key baru dengan IP itu.", "Access denied: the key was not created for IP $PROXY_IP. Create a new key with that IP.")
        code == 403 -> tr("Akses ditolak (403): token salah, kedaluwarsa, atau IP key bukan $PROXY_IP.", "Access denied (403): wrong or expired token, or the key IP is not $PROXY_IP.")
        code == 404 -> tr("Tidak ditemukan (404): periksa tag desa.", "Not found (404): check the village tag.")
        code == 429 -> tr("Terlalu banyak permintaan (429). Coba lagi beberapa menit lagi.", "Too many requests (429). Try again in a few minutes.")
        code == 503 -> tr("Server Clash of Clans sedang maintenance (503).", "Clash of Clans servers are under maintenance (503).")
        else -> tr("Gagal", "Failed") + " ($code${if (reason.isNotBlank()) " $reason" else ""})."
    }

    private val timeFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSSX")

    /** "20261101T080000.000Z" → ms (0 bila gagal). */
    fun time(s: String): Long =
        runCatching { ZonedDateTime.parse(s, timeFmt).toInstant().toEpochMilli() }.getOrDefault(0L)
}

/**
 * Sinkronisasi data akun. Sumbernya dipilih otomatis:
 * - **Ada API key** (diisi pengguna di Pengaturan → Data & akun): API resmi — nama & clan, status serangan war/CWL
 *   & pengingatnya, jadwal CWL asli (hero ditahan selama CWL), akhir Gold Pass, upgrade yang selesai lebih cepat,
 *   statistik akun. Bila API resmi gagal (key salah / IP / maintenance), status War/CWL tetap diambil dari ClashKing.
 * - **Tanpa API key**: ClashKing (tanpa key) — status War/CWL sebagai info, nama, clan & liga CWL.
 */
object ApiSync {
    private val exec = Executors.newSingleThreadExecutor()
    /** Sudah ada sinkron yang menunggu giliran (yang sedang berjalan + satu antrean cukup). */
    private val queued = AtomicBoolean(false)

    /** Sinkron otomatis saat aplikasi dibuka, paling sering tiap 30 menit. */
    fun maybeSync(act: MainActivity) {
        if (Store.accounts(act).isEmpty()) return
        if (System.currentTimeMillis() - Store.apiTried(act) < 30 * 60_000L) return
        syncAsync(act) { act.dataChanged() }
    }

    /** Setelah desa ditambah / di-update: langsung sinkron (tanpa menunggu 30 menit). */
    fun afterImport(act: MainActivity) {
        syncAsync(act) { msg ->
            act.dataChanged()
            if (Store.apiKey(act).isNotBlank()) {
                android.widget.Toast.makeText(act, tr("🔑 API diperbarui · $msg", "🔑 API updated · $msg"), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Jalankan di latar; [onDone] dipanggil di thread UI dengan pesan status. Bila sinkron lain sedang
     * berjalan, yang ini menunggu gilirannya (supaya desa yang baru ditambah ikut terbaca).
     */
    fun syncAsync(act: android.app.Activity, onDone: (String) -> Unit) {
        if (!queued.compareAndSet(false, true)) return
        val app = act.applicationContext
        exec.execute {
            queued.set(false)
            val msg = try {
                sync(app)
            } catch (e: Throwable) {
                e.message ?: e.javaClass.simpleName
            }
            act.runOnUiThread {
                if (!act.isFinishing && !act.isDestroyed) onDone(msg)
            }
        }
    }

    /** Ambil semua data (thread latar). Kembalian: pesan status untuk ditampilkan. */
    fun sync(ctx: Context): String {
        val key = Store.apiKey(ctx).trim()
        if (key.isBlank()) return syncClashKing(ctx)
        val now = System.currentTimeMillis()
        val err = try {
            return syncOfficial(ctx, key)
        } catch (e: CocApi.ApiError) {
            e.message ?: tr("Gagal", "Failed")
        } catch (e: java.io.IOException) {
            tr("Tidak bisa terhubung ke proxy (${e.javaClass.simpleName}). Periksa internet.", "Could not connect to the proxy (${e.javaClass.simpleName}). Check your internet.")
        }
        // API resmi gagal: status War/CWL tetap dari ClashKing supaya kartu Dashboard tidak kosong.
        val ck = runCatching { syncClashKing(ctx) }.getOrNull()
        val msg = tr("API resmi gagal: $err", "Official API failed: $err") + (ck?.let { tr(" · War/CWL dari ClashKing", " · War/CWL from ClashKing") } ?: "")
        Store.setApiResult(ctx, msg, now)
        return msg
    }

    // ---------------------------------------------------------------- tanpa API key: ClashKing

    /**
     * Status War / CWL dari API publik ClashKing (tanpa API key). Hanya info: tidak ada status serangan, dan rencana
     * tidak menahan hero karena war/CWL. Selama CWL, nama pemain, nama clan & liga diambil dari grup CWL (paling sering
     * sekali sehari, karena ukurannya besar); nama yang belum ada diambil dari pencarian pemain.
     */
    private fun syncClashKing(ctx: Context): String {
        val now = System.currentTimeMillis()
        var ok = 0
        var failed: String? = null
        val groups = HashMap<String, String?>()   // tag clan → JSON grup CWL (sekali per sinkron)
        for (acc in Store.accounts(ctx)) {
            val timers = try {
                ClashKing.timers(acc.tag)
            } catch (e: java.io.IOException) {
                failed = e.message ?: e.javaClass.simpleName
                continue
            }
            ok++
            // Nama akun dari pencarian ClashKing bila belum ada (di luar CWL tidak ada sumber lain).
            if (!Store.hasName(ctx, acc.tag)) runCatching { ClashKing.player(acc.tag) }.getOrNull()?.let { Store.setCkName(ctx, acc.tag, it.name) }
            // Event yang sedang diikuti (war / CWL), urut dari yang paling dulu berakhir.
            val wars = timers.filter { (it.type == "war" || it.type == "cwl") && it.expiresAt > now }.sortedBy { it.expiresAt }
            val first = wars.firstOrNull()
            Store.setWarState(ctx, acc.tag, first?.let {
                Store.WarState(it.type, "", it.expiresAt, 0, true, wars.getOrNull(1)?.expiresAt ?: 0L, 0, 0, now)
            })
            // Clan: yang muncul di semua war; bila baru satu war CWL, dipastikan dari roster grup CWL.
            val cwlClans = wars.firstOrNull { it.type == "cwl" }?.clans.orEmpty()
            var clan = ClashKing.clanOf(timers)
            val needGroup = cwlClans.isNotEmpty() && now - Store.ckChecked(ctx, acc.tag) > DAY
            if ((clan == null && cwlClans.size == 2) || needGroup) {
                val key = clan ?: cwlClans.first()
                val json = groups.getOrPut(key) { runCatching { ClashKing.get("/cwl/${ClashKing.pathTag(key)}/group") }.getOrNull() }
                if (json != null) {
                    val me = "#" + ClashKing.pathTag(acc.tag)
                    if (clan == null) clan = cwlClans.firstOrNull { c -> ClashKing.parseCwlGroup(json, c)?.lineup?.contains(me) == true }
                    if (clan != null) ClashKing.parseCwlGroup(json, clan)?.let { g ->
                        Store.setCkInfo(ctx, acc.tag, g.names[me].orEmpty(), clan, g.clanName, g.league, now)
                    }
                }
            }
        }
        val msg = when {
            ok > 0 && failed == null -> tr("Status War/CWL diperbarui · $ok desa", "War/CWL status updated · $ok villages")
            ok > 0 -> tr("Diperbarui $ok desa · sebagian gagal: $failed", "Updated $ok villages · some failed: $failed")
            failed != null -> tr("ClashKing tidak bisa dihubungi: $failed", "Could not reach ClashKing: $failed")
            else -> tr("Belum ada desa", "No villages yet")
        }
        Store.setApiResult(ctx, msg, now)
        Repo.rebuildGlobalReminders(ctx)
        return msg
    }

    // ---------------------------------------------------------------- dengan API key: API resmi

    private fun roleLabel(r: String) = when (r) {
        "leader" -> "Leader"
        "coLeader" -> "Co-leader"
        "admin" -> "Elder"
        "member" -> "Member"
        else -> r
    }

    /** Statistik akun, achievements, super troop aktif, equipment & info clan untuk tab Progres. */
    private fun stats(key: String, p: Map<String, Any?>, clanTag: String): JSONObject {
        val st = JSONObject().put("at", System.currentTimeMillis())
            .put("exp", p["expLevel"].int()).put("trophies", p["trophies"].int()).put("bestTrophies", p["bestTrophies"].int())
            .put("league", p["leagueTier"].obj()?.get("name").str().ifBlank { p["league"].obj()?.get("name").str() })
            .put("bbTrophies", p["builderBaseTrophies"].int()).put("bbBest", p["bestBuilderBaseTrophies"].int())
            .put("bbLeague", p["builderBaseLeague"].obj()?.get("name").str())
            .put("warStars", p["warStars"].int()).put("attackWins", p["attackWins"].int()).put("defenseWins", p["defenseWins"].int())
            .put("donations", p["donations"].int()).put("donationsReceived", p["donationsReceived"].int())
            .put("capital", p["clanCapitalContributions"].int()).put("role", roleLabel(p["role"].str()))
        val ach = p["achievements"].arr().orEmpty().mapNotNull { it.obj() }
        if (ach.isNotEmpty()) {
            st.put("achStars", "${ach.sumOf { it["stars"].int() }}/${ach.size * 3}")
            st.put("achDone", "${ach.count { it["stars"].int() >= 3 }}/${ach.size} " + tr("selesai", "done"))
            val next = ach.filter { it["stars"].int() < 3 && it["target"].long() > 0 && it["value"].long() < it["target"].long() }
                .sortedByDescending { it["value"].long().toDouble() / it["target"].long() }.take(3)
            st.put("achNext", JSONArray().apply {
                next.forEach { a -> put(JSONObject().put("n", a["name"].str()).put("v", a["value"].long()).put("t", a["target"].long())) }
            })
        }
        val troops = p["troops"].arr().orEmpty().mapNotNull { it.obj() }
        st.put("supers", JSONArray(troops.filter { it["superTroopIsActive"].bool() }.map { it["name"].str() }))
        val eq = JSONObject()
        p["heroEquipment"].arr().orEmpty().mapNotNull { it.obj() }.forEach { eq.put(it["name"].str(), it["level"].int()) }
        st.put("equipment", eq)
        val equipped = JSONArray()
        p["heroes"].arr().orEmpty().mapNotNull { it.obj() }.forEach { h ->
            h["equipment"].arr().orEmpty().mapNotNull { it.obj() }.forEach { equipped.put(it["name"].str()) }
        }
        st.put("equipped", equipped)
        if (clanTag.isNotBlank()) {
            runCatching { CocApi.get(key, "/clans/${CocApi.enc(clanTag)}") }.getOrNull()?.let { c ->
                st.put("clan", JSONObject().put("name", c["name"].str()).put("level", c["clanLevel"].int())
                    .put("members", c["members"].int())
                    .put("warLeague", c["warLeague"].obj()?.get("name").str())
                    .put("capitalLeague", c["capitalLeague"].obj()?.get("name").str())
                    .put("capitalHall", c["clanCapital"].obj()?.get("capitalHallLevel").int())
                    .put("winStreak", c["warWinStreak"].int())
                    .put("warLogPublic", c["isWarLogPublic"].bool()))
            }
        }
        return st
    }

    /** Anggota [me] di sisi clan [clanTag] pada war CWL [w], atau null bila tidak ikut lineup. */
    private fun cwlMember(w: Map<String, Any?>, clanTag: String, me: String): Map<String, Any?>? =
        cwlSide(w, clanTag)?.get("members").arr().orEmpty().mapNotNull { it.obj() }
            .firstOrNull { it["tag"].str().equals(me, ignoreCase = true) }

    private fun cwlSide(w: Map<String, Any?>, clanTag: String): Map<String, Any?>? =
        listOfNotNull(w["clan"].obj(), w["opponent"].obj()).firstOrNull { it["tag"].str().equals(clanTag, true) }

    /**
     * Status serangan CWL akun [me]: apakah serangan hari ini sudah dipakai, dan kapan serangan
     * berikutnya yang masih dibutuhkan berakhir (hari besok bila ikut lineup; lusa bila tidak; 0 bila
     * tidak ada lagi). [ours] = (indeks ronde, war) dua ronde terakhir.
     */
    fun cwlWarState(ours: List<Pair<Int, Map<String, Any?>>>, totalRounds: Int, clanTag: String, me: String): Store.WarState? {
        val cur = ours.lastOrNull { it.second["state"].str() == "inWar" }
        val nxt = ours.lastOrNull { it.second["state"].str() == "preparation" }
        if (cur == null && nxt == null) return null
        fun last(ri: Int) = ri >= totalRounds - 1
        val curMe = cur?.let { cwlMember(it.second, clanTag, me) }
        val curEnd = cur?.let { CocApi.time(it.second["endTime"].str()) } ?: 0L
        val left = if (curMe != null && curMe["attacks"].arr().isNullOrEmpty()) 1 else 0
        val needEnd = when {
            cur != null && left > 0 -> curEnd
            nxt != null -> {
                val nEnd = CocApi.time(nxt.second["endTime"].str())
                // Lineup besok belum terlihat → dianggap ikut.
                val known = cwlSide(nxt.second, clanTag)?.get("members").arr().orEmpty().isNotEmpty()
                val inNext = !known || cwlMember(nxt.second, clanTag, me) != null
                when {
                    inNext -> nEnd
                    last(nxt.first) -> 0L
                    else -> nEnd + DAY
                }
            }
            cur != null && last(cur.first) -> 0L
            else -> curEnd + DAY
        }
        return if (cur != null) {
            Store.WarState("cwl", "inWar", curEnd, left, curMe != null, needEnd, cur.first + 1, totalRounds, System.currentTimeMillis())
        } else {
            val n = nxt!!
            val inN = cwlMember(n.second, clanTag, me) != null
            Store.WarState("cwl", "preparation", CocApi.time(n.second["endTime"].str()), if (inN) 1 else 0, inN, needEnd,
                n.first + 1, totalRounds, System.currentTimeMillis())
        }
    }

    /** API resmi; melempar [CocApi.ApiError] / IOException bila gagal. */
    private fun syncOfficial(ctx: Context, key: String): String {
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val notes = ArrayList<String>()
        // Musim Gold Pass (sama untuk semua akun)
        CocApi.get(key, "/goldpass/seasons/current")?.let { gp ->
            val start = CocApi.time(gp["startTime"].str())
            if (start > 0) Store.setGoldPassStart(ctx, start)
            val end = CocApi.time(gp["endTime"].str())
            if (end > 0) {
                Store.setGoldPassEnd(ctx, end)
                notes += tr("Gold Pass s/d ${Fmt.dateShort(end, zone)}", "Gold Pass until ${Fmt.dateShort(end, zone)}")
            }
        }
        var ok = 0
        for (acc in Store.accounts(ctx)) {
            val p = CocApi.get(key, "/players/${CocApi.enc(acc.tag)}") ?: continue
            val clan = p["clan"].obj()
            val heroes = HashMap<String, Int>()
            for (h in p["heroes"].arr().orEmpty()) {
                val o = h.obj() ?: continue
                val name = o["name"].str()
                if (name.isNotBlank()) heroes[name] = o["level"].int()
            }
            // Level troop, spell, pet & hero per sisi desa: untuk mendeteksi upgrade yang selesai lebih cepat.
            val units = HashMap<String, Int>()
            for (section in listOf("troops", "spells", "heroes")) {
                for (x in p[section].arr().orEmpty()) {
                    val o = x.obj() ?: continue
                    val name = o["name"].str()
                    if (name.isBlank()) continue
                    units[(if (o["village"].str() == "builderBase") "b:" else "h:") + name] = o["level"].int()
                }
            }
            val warPref = p["warPreference"].str()
            Store.setApiPlayer(ctx, acc.tag, Store.ApiPlayer(
                p["name"].str(), clan?.get("name").str(), clan?.get("tag").str(),
                p["townHallLevel"].int(), p["builderHallLevel"].int(), heroes, System.currentTimeMillis(),
                units, warPref,
            ))
            ok++
            // CWL otomatis: terdaftar di roster CWL clan? Jadwal asli: hari perang ronde 1 sampai akhir ronde 7.
            val clanTag = clan?.get("tag").str()
            var cwlState = if (clanTag.isBlank()) "noclan" else "open"
            val cwlWars = HashMap<String, Long>()
            var cwlActive = false
            if (clanTag.isNotBlank()) {
                val lg = runCatching { CocApi.get(key, "/clans/${CocApi.enc(clanTag)}/currentwar/leaguegroup", allow404 = true) }.getOrNull()
                val groupClans = lg?.get("clans").arr().orEmpty()
                if (lg != null && lg["state"].str() != "ended" && groupClans.isNotEmpty()) {
                    val inRoster = groupClans.any { c ->
                        c.obj()?.get("members").arr().orEmpty().any { m -> m.obj()?.get("tag").str().equals(acc.tag, ignoreCase = true) }
                    }
                    cwlState = if (inRoster) "roster" else "notroster"
                }
                if (cwlState == "open" && warPref == "out") cwlState = "optout"
                // Pengingat serangan CWL: war clan ini pada dua ronde terakhir yang sudah punya war tag.
                val lgState = lg?.get("state").str()
                cwlActive = lg != null && (lgState == "preparation" || lgState == "inWar")
                var warState: Store.WarState? = null
                if (cwlActive && cwlState == "roster") {
                    val allRounds = lg?.get("rounds").arr().orEmpty().map { r ->
                        r.obj()?.get("warTags").arr()?.map { it.str() }?.filter { it.isNotBlank() && it != "#0" }.orEmpty()
                    }
                    val rounds = allRounds.withIndex().filter { it.value.isNotEmpty() }
                    // Ronde → war clan ini (dua ronde terakhir: hari perang sekarang & persiapan besok).
                    val ours = ArrayList<Pair<Int, Map<String, Any?>>>()
                    for ((ri, tags) in rounds.takeLast(2)) {
                        for (wt in tags) {
                            val w = runCatching { CocApi.get(key, "/clanwarleagues/wars/${CocApi.enc(wt)}") }.getOrNull() ?: continue
                            val isOurs = w["clan"].obj()?.get("tag").str().equals(clanTag, true) ||
                                w["opponent"].obj()?.get("tag").str().equals(clanTag, true)
                            if (!isOurs) continue
                            val ws = w["state"].str()
                            val end = CocApi.time(w["endTime"].str())
                            if ((ws == "inWar" || ws == "preparation") && end > 0) cwlWars[wt] = end
                            ours += ri to w
                            break
                        }
                    }
                    warState = cwlWarState(ours, allRounds.size.coerceAtLeast(1), clanTag, acc.tag)
                }
                Store.setWarState(ctx, acc.tag, warState)
                val firstTag = lg?.get("rounds").arr()?.firstOrNull()?.obj()?.get("warTags").arr()
                    ?.map { it.str() }?.firstOrNull { it.isNotBlank() && it != "#0" }
                if (firstTag != null) {
                    val w = runCatching { CocApi.get(key, "/clanwarleagues/wars/${CocApi.enc(firstTag)}") }.getOrNull()
                    val prep = CocApi.time(w?.get("preparationStartTime").str())
                    val battle = CocApi.time(w?.get("startTime").str()).takeIf { it > 0 } ?: (prep + DAY)
                    if (prep > 0) {
                        val end = prep + 8 * DAY
                        Store.setCwlKnown(ctx, acc.tag, battle, end)
                        notes += "CWL ${Store.villageName(ctx, acc.tag)} ${Fmt.dateShort(battle, zone)}–${Fmt.dateShort(end, zone)}"
                    }
                }
            }
            Store.setCwlAuto(ctx, acc.tag, Store.CwlAuto(cwlState, clan?.get("name").str(), System.currentTimeMillis()))
            runCatching { Store.setApiStats(ctx, acc.tag, stats(key, p, clanTag)) }
            runCatching { Attacks.plan(ctx, key, acc.tag, clanTag, cwlWars, cwlActive) }
            // Upgrade yang menurut API sudah selesai (book, gem, potion) langsung ditandai selesai.
            val early = runCatching { Repo.applyApiProgress(ctx, acc.tag) }.getOrDefault(emptyList())
            if (early.isNotEmpty()) notes += "${Store.villageName(ctx, acc.tag)}: ${early.joinToString(", ")} " + tr("sudah selesai", "already done")
        }
        val msg = tr("API resmi OK · $ok desa", "Official API OK · $ok villages") + if (notes.isNotEmpty()) " · " + notes.joinToString(" · ") else ""
        Store.setApiResult(ctx, msg, now)
        Repo.rebuildGlobalReminders(ctx)
        return msg
    }
}
