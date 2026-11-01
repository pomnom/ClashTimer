package id.clashtimer.core

/** Bukti Gold Pass dari satu upgrade yang waktu mulainya diketahui cukup tepat. */
class GpEvidence(
    /** true = Research Boost (Laboratory & Pet House), false = Builder Boost. */
    val research: Boolean,
    val pct: Int,
    /** Perkiraan waktu mulai upgrade (ms). */
    val startedAt: Long,
    /** Upgrade yang menjadi bukti, mis. "Army Camp → Lv 11". */
    val title: String,
)

/**
 * Deteksi Gold Pass dari timer. Export desa dan API resmi tidak memuat status Gold Pass, tetapi
 * lama upgrade memperlihatkannya: total = sisa saat export + (waktu export − waktu mulai),
 * dibandingkan dengan durasi dasar dengan potongan 0 / 10 / 15 / 20 %.
 *
 * Waktu mulai diambil dari tombol "Sudah dimulai" (± beberapa menit) atau dari upgrade yang baru
 * muncul sejak export sebelumnya (antara dua waktu export). Hanya dipakai bila tepat SATU
 * persentase cocok; upgrade Builder Base (Clock Tower sering dipakai tanpa dicatat), gear-up,
 * upgrade yang dibantu helper, dan upgrade pendek dilewati.
 */
object GoldPassDetect {
    val LEVELS = intArrayOf(0, 10, 15, 20)

    /** Upgrade pendek terlalu terpengaruh pembulatan dan selisih waktu menekan tombol. */
    private const val MIN_BASE_SEC = 8 * 3600L

    /** "Sudah dimulai" biasanya ditekan sesaat setelah (atau sebelum) memulai di game. */
    private const val MARK_EARLY = 15 * 60_000L
    private const val MARK_LATE = 5 * 60_000L

    private const val HELPER_WORK = 3600_000L
    private const val HELPER_CYCLE = 23 * 3600_000L

    private fun key(itemId: Int, from: Int, lane: Lane) = "$itemId|$from|$lane"

    private fun usable(t: RunningTimer) = !t.gear && !t.helped && t.cat != null &&
        (t.lane == Lane.BUILDER || t.lane == Lane.LAB || t.lane == Lane.PET)

    /**
     * [prev] = desa dari export sebelumnya (boleh null), [cur] = export baru,
     * [started] = upgrade yang ditandai "Sudah dimulai" sebelum export baru.
     * Kembalian: paling banyak satu bukti per jenis boost (yang upgradenya paling akhir dimulai).
     */
    fun detect(gd: GameData, prev: Village?, cur: Village, started: List<Started>, events: List<GameEvent>): List<GpEvidence> {
        val exportAt = cur.exportAt
        val before = prev?.takeIf { it.tag == cur.tag && it.exportAt < exportAt }
        val prevCount = HashMap<String, Int>()
        before?.timers?.forEach { t ->
            val k = key(t.dataId, t.fromLevel, t.lane)
            prevCount[k] = (prevCount[k] ?: 0) + 1
        }

        // Timer → rentang waktu mulai yang mungkin (ms)
        val windows = ArrayList<Pair<RunningTimer, LongRange>>()
        for ((k, group) in cur.timers.groupBy { key(it.dataId, it.fromLevel, it.lane) }) {
            // Upgrade yang paling baru dimulai punya sisa waktu terbesar.
            val pool = group.filter { usable(it) }.sortedByDescending { it.remaining }.toMutableList()
            if (pool.isEmpty()) continue
            val marks = started.filter { !it.gear && it.start <= exportAt && key(it.itemId, it.fromLevel, it.lane) == k }
                .sortedByDescending { it.start }
            for (m in marks) {
                val t = pool.removeFirstOrNull() ?: break
                windows += t to ((m.start - MARK_EARLY)..(m.start + MARK_LATE))
            }
            if (before != null) {
                val fresh = group.size - (prevCount[k] ?: 0) - marks.size
                repeat(fresh.coerceAtLeast(0)) {
                    pool.removeFirstOrNull()?.let { windows += it to (before.exportAt..exportAt) }
                }
            }
        }

        val out = ArrayList<GpEvidence>()
        for ((t, w) in windows) {
            val base = gd.item(t.dataId)?.lv(t.fromLevel + 1)?.time ?: continue
            if (base < MIN_BASE_SEC) continue
            if (helperBusy(cur, t.lane, w.first)) continue
            // Event yang memotong waktu harus sama di seluruh rentang mulai.
            val ev = Events.at(events, t.lane, w.first)?.timePct ?: 0
            if ((Events.at(events, t.lane, minOf(w.last, exportAt))?.timePct ?: 0) != ev) continue
            val minTotal = t.remaining + (exportAt - w.last) / 1000
            val maxTotal = t.remaining + (exportAt - w.first) / 1000
            val tol = if (base > 86_400) 1800L else 300L
            val fits = LEVELS.filter { pct ->
                val x = GoldPass(pct, pct).apply(base, t.lane, ev)
                x >= minTotal - tol && x <= maxTotal + tol
            }
            if (fits.size != 1) continue
            out += GpEvidence(t.lane != Lane.BUILDER, fits[0], minOf(w.last, exportAt), "${t.name} → Lv ${t.fromLevel + 1}")
        }
        return out.groupBy { it.research }.values.mapNotNull { list -> list.maxByOrNull { it.startedAt } }
    }

    /**
     * Helper (Builder's Apprentice / Lab Assistant) mungkin bekerja setelah [from] sampai export:
     * sisa waktu upgrade tidak lagi murni, jadi tidak dipakai sebagai bukti.
     */
    private fun helperBusy(v: Village, lane: Lane, from: Long): Boolean {
        val id = when (lane) {
            Lane.BUILDER -> GameData.APPRENTICE
            Lane.LAB -> GameData.LAB_ASSISTANT
            else -> return false
        }
        val h = v.helper(id) ?: return false
        if (h.cooldown <= 0) {
            // Siap dipakai: terakhir bekerja paling lambat 22 jam sebelum export.
            return from < v.exportAt - (HELPER_CYCLE - HELPER_WORK)
        }
        val used = v.exportAt + h.cooldown * 1000 - HELPER_CYCLE
        return used < v.exportAt && used + HELPER_WORK > from
    }
}
