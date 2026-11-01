package id.clashtimer.core

class ExportException(msg: String) : Exception(msg)

/** Satu bangunan/hero/troop di desa. Instance yang sedang upgrade punya [uid] = id timer. */
class Inst(
    var uid: String,
    val item: Item,
    var level: Int,
    var upgrading: Boolean,
    /** Sisa detik saat export (0 bila tidak sedang upgrade). */
    var remaining: Long,
    val helped: Boolean,
    var geared: Boolean,
    /** Sedang di-gear-up oleh Master Builder dari Builder Base. */
    var gearing: Boolean = false,
) {
    /** Pekerja yang dipakai: gear-up dikerjakan builder Builder Base. */
    val lane: Lane get() = if (gearing) Lane.BB_BUILDER else item.lane

    fun copy() = Inst(uid, item, level, upgrading, remaining, helped, geared, gearing)

    /** Selesaikan pekerjaan yang sedang berjalan. */
    fun complete() {
        if (gearing) {
            gearing = false
            geared = true
        } else level += 1
        upgrading = false
        remaining = 0
    }
}

class HelperState(val id: Int, val level: Int, val cooldown: Long)

/** Satu pertahanan rakitan: id pertahanan + (id modul, level). */
class CraftedState(val defenseId: Int, val modules: List<Pair<Int, Int>>)

/** Upgrade yang ditandai "sudah dimulai" di aplikasi setelah export terakhir. */
data class Started(
    val uid: String,
    val itemId: Int,
    /** Level sebelum upgrade (0 = bangunan/hero/pet baru). */
    val fromLevel: Int,
    val lane: Lane,
    val name: String,
    val start: Long,
    val durationSec: Long,
    val gear: Boolean = false,
) {
    val finish: Long get() = start + durationSec * 1000
}

/** Timer yang sedang berjalan menurut export (termasuk item yang tidak ada di data game). */
class RunningTimer(
    val uid: String,
    val dataId: Int,
    val name: String,
    val lane: Lane,
    val cat: Cat?,
    /** Level saat ini (sebelum upgrade selesai). */
    val fromLevel: Int,
    val remaining: Long,
    val helped: Boolean,
    /** Gear-up pertahanan Home Village oleh Master Builder. */
    val gear: Boolean = false,
)

class Village(
    val tag: String,
    /** Waktu export (ms). */
    val exportAt: Long,
    val insts: MutableList<Inst>,
    /** level → jumlah tembok. */
    val walls: MutableMap<Int, Int>,
    val bbWalls: MutableMap<Int, Int>,
    val helpers: List<HelperState>,
    val timers: List<RunningTimer>,
    val unknown: Int,
    val homeBuilders: Int,
    val bbBuildersAuto: Int,
    val hasHome: Boolean,
    val hasBB: Boolean,
    /** Equipment hero: id → level (dari export). */
    val equipment: Map<Int, Int> = emptyMap(),
    /** Pertahanan rakitan Crafting Station dan level modulnya. */
    val crafted: List<CraftedState> = emptyList(),
) {
    fun level(id: Int): Int = insts.filter { it.item.id == id }.maxOfOrNull { it.level } ?: 0
    fun isUpgrading(id: Int): Boolean = insts.any { it.item.id == id && it.upgrading }
    fun has(id: Int): Boolean = insts.any { it.item.id == id }
    fun count(id: Int): Int = insts.count { it.item.id == id }

    val th: Int get() = level(GameData.TOWN_HALL).coerceAtLeast(1)
    val bh: Int get() = level(GameData.BUILDER_HALL)
    val labLevel: Int get() = level(GameData.LAB)
    val heroHall: Int get() = level(GameData.HERO_HALL)
    val petHouse: Int get() = level(GameData.PET_HOUSE)
    val starLab: Int get() = level(GameData.STAR_LAB)

    fun helper(id: Int): HelperState? = helpers.firstOrNull { it.id == id }

    fun copy(): Village = Village(
        tag, exportAt, insts.map { it.copy() }.toMutableList(), walls.toMutableMap(), bbWalls.toMutableMap(),
        helpers, timers, unknown, homeBuilders, bbBuildersAuto, hasHome, hasBB, equipment, crafted,
    )

    /** Tandai upgrade yang dimulai lewat aplikasi (tanpa import ulang). */
    fun applyStarted(gd: GameData, list: List<Started>) {
        for (s in list) {
            val item = gd.item(s.itemId) ?: continue
            val rem = ((s.finish - exportAt) / 1000).coerceAtLeast(0)
            if (s.gear) {
                val g = insts.firstOrNull { it.item.id == s.itemId && !it.upgrading && !it.geared && it.level >= s.fromLevel } ?: continue
                g.uid = s.uid
                g.upgrading = true
                g.gearing = true
                g.remaining = rem
                continue
            }
            if (s.fromLevel == 0) {
                insts += Inst(s.uid, item, 0, true, rem, false, false)
                continue
            }
            val i = insts.firstOrNull { it.item.id == s.itemId && it.level == s.fromLevel && !it.upgrading }
            if (i == null) {
                // Hero yang baru dibuka (mis. Battle Copter mulai di Lv15)
                if (!has(s.itemId)) insts += Inst(s.uid, item, s.fromLevel, true, rem, false, false)
                continue
            }
            i.uid = s.uid
            i.upgrading = true
            i.remaining = rem
        }
    }

    /**
     * Terapkan perubahan sejak export: upgrade yang sudah diselesaikan lebih awal
     * (book / gem / ditandai selesai) dan level yang naik karena hammer.
     */
    fun applyOverrides(completed: Collection<String>, bumps: List<Pair<Int, Int>>) {
        for (uid in completed) {
            insts.firstOrNull { it.uid == uid && it.upgrading }?.complete()
        }
        for ((id, from) in bumps) {
            val i = insts.firstOrNull { it.item.id == id && it.level == from && !it.upgrading } ?: continue
            i.level += 1
        }
    }
}

/** Membaca teks "Export village data" dari game. */
object ExportReader {

    /** Tag & waktu export dari teks, tanpa membaca seluruh isinya. */
    class Peek(val tag: String, val exportAt: Long)

    private val tagRe = Regex("\"tag\"\\s*:\\s*\"(#[0-9A-Za-z]+)\"")
    private val tsRe = Regex("\"timestamp\"\\s*:\\s*(\\d+)")

    /** Cek cepat apakah teks adalah export desa (untuk deteksi clipboard). */
    fun peek(text: String): Peek? {
        if (text.length < 40 || !text.contains("\"buildings")) return null
        val tag = tagRe.find(text)?.groupValues?.get(1) ?: return null
        val ts = tsRe.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        return Peek(tag, ts * 1000)
    }

    private val sections: List<Pair<String, Cat?>> = listOf(
        "buildings" to Cat.BUILDING,
        "traps" to Cat.TRAP,
        "heroes" to Cat.HERO,
        "units" to Cat.TROOP,
        "siege_machines" to Cat.TROOP,
        "spells" to Cat.SPELL,
        "pets" to Cat.PET,
        "buildings2" to Cat.BB_BUILDING,
        "traps2" to Cat.BB_TRAP,
        "heroes2" to Cat.BB_HERO,
        "units2" to Cat.BB_TROOP,
    )

    private fun laneOfSection(section: String): Lane = when (section) {
        "units", "siege_machines", "spells" -> Lane.LAB
        "pets" -> Lane.PET
        "buildings2", "traps2", "heroes2" -> Lane.BB_BUILDER
        "units2" -> Lane.STAR_LAB
        else -> Lane.BUILDER
    }

    fun read(rawText: String, gd: GameData, nowMs: Long): Village {
        val text = rawText.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) {
            throw ExportException("Teks ini bukan export desa. Di game: Settings → More Settings → Export village data → Copy.")
        }
        val root = try {
            Json.parse(text.substring(start, end + 1)).obj()
        } catch (e: Exception) {
            null
        } ?: throw ExportException("JSON export tidak lengkap atau rusak. Coba Copy ulang dari game.")

        if (root["buildings"] == null && root["buildings2"] == null) {
            throw ExportException("Tidak ditemukan data bangunan. Pastikan yang di-copy adalah \"Export village data\".")
        }

        val tag = root["tag"].str().ifBlank { "#DESA" }
        val tsSec = root["timestamp"].long()
        val exportAt = if (tsSec > 1_500_000_000L && tsSec * 1000 <= nowMs + 5 * 60_000L) tsSec * 1000 else nowMs

        val insts = ArrayList<Inst>()
        val timers = ArrayList<RunningTimer>()
        val walls = HashMap<Int, Int>()
        val bbWalls = HashMap<Int, Int>()
        var unknown = 0
        var huts = 0
        val crafted = ArrayList<CraftedState>()

        for ((section, cat) in sections) {
            val arr = root[section].arr() ?: continue
            val timerCounter = HashMap<Int, Int>()
            for ((index, raw) in arr.withIndex()) {
                val e = raw.obj() ?: continue
                // Crafting Station: tidak punya level sendiri, berisi pertahanan rakitan & modulnya.
                if (section == "buildings" && e["types"] != null) {
                    for (tr in e["types"].arr().orEmpty()) {
                        val t = tr.obj() ?: continue
                        val mods = ArrayList<Pair<Int, Int>>()
                        for (mr in t["modules"].arr().orEmpty()) {
                            val m = mr.obj() ?: continue
                            val mid = m["data"].int()
                            val ml = m["lvl"].int(1)
                            mods += mid to ml
                            val mt = m["timer"].long()
                            if (mt > 0) {
                                // Upgrade modul memakai builder: hitung sebagai pekerja sibuk.
                                timers += RunningTimer("$tag|crafted|$mid", mid, gd.name(mid), Lane.BUILDER, null, ml, mt, false)
                            }
                        }
                        crafted += CraftedState(t["data"].int(), mods)
                    }
                    continue
                }
                if (e["data"] == null || e["lvl"] == null) continue
                val dataId = e["data"].int()
                val lvl = e["lvl"].int()
                val cnt = e["cnt"].int(1).coerceIn(0, 2000)
                val timer = e["timer"].long()
                val helped = e["helper_recurrent"].bool()
                val geared = e["gear_up"].bool()
                val item = gd.item(dataId)?.takeIf { cat != null && it.cat.bb == cat.bb }

                if (section == "buildings" && dataId == GameData.HUT) huts += if (timer > 0) 1 else cnt

                if (item != null && item.isWall) {
                    val target = if (item.cat.bb) bbWalls else walls
                    target[lvl] = (target[lvl] ?: 0) + cnt
                    continue
                }

                // Senjata Town Hall yang masih punya level (TH17): item tersendiri. Timer di Town Hall dianggap
                // milik senjata bila tidak lebih lama dari durasi level senjata berikutnya.
                if (section == "buildings" && dataId == GameData.TOWN_HALL && e.containsKey("weapon")) {
                    val wItem = gd.weaponFor(lvl)
                    if (wItem != null) {
                        val wLvl = e["weapon"].int().coerceAtLeast(1)
                        val wNext = wItem.lv(wLvl + 1)
                        if (timer > 0 && wNext != null && timer <= wNext.time) {
                            val uid = "$tag|weapon|${wItem.id}"
                            timers += RunningTimer(uid, wItem.id, wItem.name, Lane.BUILDER, wItem.cat, wLvl, timer, helped)
                            insts += Inst(uid, wItem, wLvl, true, timer, helped, false)
                            if (item != null) insts += Inst("$section|$dataId|$index|0", item, lvl, false, 0, false, geared)
                            continue
                        }
                        insts += Inst("$section|weapon|${wItem.id}", wItem, wLvl, false, 0, false, false)
                    }
                }

                // Gear-up yang sedang berjalan: ada timer dan "gear_up": 0 (selesai → 1).
                val gearRunning = section == "buildings" && timer > 0 && e.containsKey("gear_up") && !e["gear_up"].bool()
                if (gearRunning && item != null) {
                    val n = (timerCounter[-dataId] ?: 0) + 1
                    timerCounter[-dataId] = n
                    val uid = "$tag|gear|$dataId|$n"
                    timers += RunningTimer(uid, dataId, item.name, Lane.BB_BUILDER, item.cat, lvl, timer, false, gear = true)
                    insts += Inst(uid, item, lvl, true, timer, false, false, gearing = true)
                    continue
                }

                if (timer > 0) {
                    val n = (timerCounter[dataId] ?: 0) + 1
                    timerCounter[dataId] = n
                    val uid = "$tag|$section|$dataId|$n"
                    val lane = item?.lane ?: laneOfSection(section)
                    timers += RunningTimer(uid, dataId, gd.name(dataId), lane, item?.cat, lvl, timer, helped)
                    if (item != null) insts += Inst(uid, item, lvl, true, timer, helped, geared)
                    else unknown++
                } else if (item != null) {
                    repeat(cnt) { k -> insts += Inst("$section|$dataId|$index|$k", item, lvl, false, 0, false, geared) }
                } else if (cat != null) {
                    unknown++
                }
            }
        }

        val helpers = root["helpers"].arr().orEmpty().mapNotNull { raw ->
            val e = raw.obj() ?: return@mapNotNull null
            if (e["data"] == null) null
            else HelperState(e["data"].int(), e["lvl"].int(1), e["helper_cooldown"].long())
        }

        val hasBob = insts.any { it.item.id == GameData.BOB_HUT }
        val homeBuilders = (if (huts > 0) huts else 5) + if (hasBob) 1 else 0
        val bbBusy = timers.count { it.lane == Lane.BB_BUILDER }
        val bbAuto = maxOf(
            1 + (if (insts.any { it.item.id == GameData.BOTO_SHACK }) 1 else 0) +
                (if (insts.any { it.item.id == GameData.OTTO_OUTPOST }) 1 else 0),
            bbBusy,
        )

        val equipment = HashMap<Int, Int>()
        for (raw in root["equipment"].arr().orEmpty()) {
            val e = raw.obj() ?: continue
            if (e["data"] != null) equipment[e["data"].int()] = e["lvl"].int(1)
        }

        return Village(
            tag, exportAt, insts, walls, bbWalls, helpers, timers, unknown,
            homeBuilders.coerceAtMost(6), bbAuto,
            hasHome = root["buildings"] != null,
            hasBB = root["buildings2"].arr()?.isNotEmpty() == true,
            equipment = equipment,
            crafted = crafted,
        )
    }
}
