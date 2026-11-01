package id.clashtimer.core

/**
 * Kapasitas gudang Builder Base (Builder Gold & Builder Elixir): Builder Hall + kedua storage.
 * Upgrade yang biayanya melebihi kapasitas tidak bisa dibayar walau gudang penuh, jadi storage
 * harus dinaikkan dulu.
 *
 * Data: Gold/Elixir Storage Builder Base (cocmap.com) dan kapasitas Builder Hall (coc.guide; BH10
 * 1,5 jt sesuai pengumuman Supercell).
 */
object Capacity {
    const val BB_GOLD_STORAGE = 1000038   // menyimpan Builder Gold (dibayar Builder Elixir)
    const val BB_ELIXIR_STORAGE = 1000036 // menyimpan Builder Elixir (dibayar Builder Gold)
    const val GOLD = "Builder Gold"
    const val ELIXIR = "Builder Elixir"

    private val STORAGE = longArrayOf(70_000, 150_000, 250_000, 350_000, 600_000, 800_000, 1_200_000, 1_600_000, 2_000_000, 2_500_000)
    private val HALL = longArrayOf(50_000, 75_000, 100_000, 150_000, 900_000, 900_000, 1_100_000, 1_300_000, 1_500_000, 1_500_000)

    /** Storage yang menyimpan [resource] (null bila bukan resource Builder Base). */
    fun storageFor(resource: String): Int? = when (resource) {
        GOLD -> BB_GOLD_STORAGE
        ELIXIR -> BB_ELIXIR_STORAGE
        else -> null
    }

    /** Resource yang disimpan bangunan [itemId] (null bila bukan storage Builder Base). */
    fun storedBy(itemId: Int): String? = when (itemId) {
        BB_GOLD_STORAGE -> GOLD
        BB_ELIXIR_STORAGE -> ELIXIR
        else -> null
    }

    private fun at(table: LongArray, level: Int): Long =
        if (level <= 0) 0 else table[(level - 1).coerceAtMost(table.size - 1)]

    /**
     * Kapasitas maksimum [resource] di desa [v] (null = tidak dibatasi / tidak dihitung, mis. resource
     * Home Village). Storage yang sedang di-upgrade tetap menampung sesuai level sekarang.
     */
    fun of(v: Village, resource: String): Long? {
        val id = storageFor(resource) ?: return null
        if (v.bh <= 0) return null
        return at(HALL, v.bh) + v.insts.filter { it.item.id == id }.sumOf { at(STORAGE, it.level) }
    }

    /** Tambahan kapasitas bila storage naik dari [from] ke [to]. */
    fun gain(from: Int, to: Int): Long = at(STORAGE, to) - at(STORAGE, from)

    /** Biaya [cost] untuk [c] melebihi kapasitas gudangnya. */
    fun blocked(v: Village, c: Candidate, cost: Long): Boolean {
        if (c.gear) return false
        val cap = of(v, c.item.resource) ?: return false
        return cost > cap
    }

    fun short(resource: String): String = if (resource == GOLD) "Gold BB" else if (resource == ELIXIR) "Elixir BB" else resource
}
