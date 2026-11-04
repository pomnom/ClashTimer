package id.clashtimer.core

/**
 * Kapasitas gudang: Builder Base (Builder Gold & Builder Elixir: Builder Hall + storage) dan Home Village
 * (Gold, Elixir & Dark Elixir: Town Hall + storage). Upgrade yang biayanya melebihi kapasitas tidak bisa
 * dibayar walau gudang penuh, jadi storage harus dinaikkan dulu.
 *
 * Angka kapasitas dibaca dari gamedata.json (bagian "cap", disusun tools/build_gamedata.py dari
 * tools/overrides.json) lewat [install]. Tanpa data itu kapasitas tidak dibatasi.
 */
object Capacity {
    const val BB_GOLD_STORAGE = 1000038   // menyimpan Builder Gold (dibayar Builder Elixir)
    const val BB_ELIXIR_STORAGE = 1000036 // menyimpan Builder Elixir (dibayar Builder Gold)
    const val GOLD = "Builder Gold"
    const val ELIXIR = "Builder Elixir"

    const val HOME_GOLD_STORAGE = 1000005   // Gold Storage (dibayar Elixir)
    const val HOME_ELIXIR_STORAGE = 1000003 // Elixir Storage (dibayar Gold)
    const val HOME_DE_STORAGE = 1000024     // Dark Elixir Storage (dibayar Elixir)
    const val H_GOLD = "Gold"
    const val H_ELIXIR = "Elixir"
    const val H_DARK = "Dark Elixir"

    /** Tabel kapasitas per level (indeks 0 = level 1). Town Hall: Gold = Elixir. */
    class Tables(
        val hall: LongArray, val hallDark: LongArray, val storage: LongArray, val darkStorage: LongArray,
        val bbHall: LongArray, val bbStorage: LongArray,
    )

    @Volatile private var t: Tables? = null

    /** Dipanggil [GameData.parse] dengan bagian "cap" gamedata.json. */
    fun install(tables: Tables?) { t = tables }

    /** Storage yang menyimpan [resource] (null bila bukan resource Builder Base). */
    fun storageFor(resource: String): Int? = when (resource) {
        GOLD -> BB_GOLD_STORAGE
        ELIXIR -> BB_ELIXIR_STORAGE
        H_GOLD -> HOME_GOLD_STORAGE
        H_ELIXIR -> HOME_ELIXIR_STORAGE
        H_DARK -> HOME_DE_STORAGE
        else -> null
    }

    /** Resource yang disimpan bangunan [itemId] (null bila bukan storage Builder Base). */
    fun storedBy(itemId: Int): String? = when (itemId) {
        BB_GOLD_STORAGE -> GOLD
        BB_ELIXIR_STORAGE -> ELIXIR
        HOME_GOLD_STORAGE -> H_GOLD
        HOME_ELIXIR_STORAGE -> H_ELIXIR
        HOME_DE_STORAGE -> H_DARK
        else -> null
    }

    private fun table(tb: Tables, resource: String): LongArray = when (resource) {
        GOLD, ELIXIR -> tb.bbStorage
        H_DARK -> tb.darkStorage
        else -> tb.storage
    }

    private fun hallTable(tb: Tables, resource: String): LongArray = when (resource) {
        GOLD, ELIXIR -> tb.bbHall
        H_DARK -> tb.hallDark
        else -> tb.hall
    }

    private fun bb(resource: String) = resource == GOLD || resource == ELIXIR

    private fun at(table: LongArray, level: Int): Long =
        if (level <= 0 || table.isEmpty()) 0 else table[(level - 1).coerceAtMost(table.size - 1)]

    /**
     * Kapasitas maksimum [resource] di desa [v] (null = tidak dibatasi / tidak dihitung, mis. resource
     * Home Village). Storage yang sedang di-upgrade tetap menampung sesuai level sekarang.
     */
    fun of(v: Village, resource: String): Long? {
        val tb = t ?: return null
        val id = storageFor(resource) ?: return null
        val hallLv = if (bb(resource)) v.bh else v.th
        if (hallLv <= 0) return null
        return at(hallTable(tb, resource), hallLv) + v.insts.filter { it.item.id == id }.sumOf { at(table(tb, resource), it.level) }
    }

    /** Tambahan kapasitas bila storage [resource] naik dari [from] ke [to]. */
    fun gain(resource: String, from: Int, to: Int): Long {
        val tb = t ?: return 0
        return at(table(tb, resource), to) - at(table(tb, resource), from)
    }

    /**
     * Kapasitas terbesar yang bisa dicapai di hall sekarang: semua storage yang boleh dibangun di level
     * tertingginya + hall. Biaya di atas ini tidak bisa dibayar sampai hall naik.
     */
    fun maxAt(v: Village, gd: GameData, resource: String): Long? {
        val tb = t ?: return null
        val id = storageFor(resource) ?: return null
        val bb = bb(resource)
        val hallLv = if (bb) v.bh else v.th
        if (hallLv <= 0) return null
        val item = gd.item(id) ?: return null
        val lvl = item.maxLevelFor(hallLv)
        val n = maxOf(gd.allowedCount(id, hallLv, bb), v.insts.count { it.item.id == id })
        return at(hallTable(tb, resource), hallLv) + n * at(table(tb, resource), lvl)
    }

    /** Biaya [c] tidak akan muat di hall ini walau semua storage sudah max. */
    fun never(v: Village, gd: GameData, c: Candidate): Boolean {
        if (c.gear) return false
        val max = maxAt(v, gd, c.item.resource) ?: return false
        return c.cost > max
    }

    /** Biaya [cost] untuk [c] melebihi kapasitas gudangnya. */
    fun blocked(v: Village, c: Candidate, cost: Long): Boolean {
        if (c.gear) return false
        val cap = of(v, c.item.resource) ?: return false
        return cost > cap
    }

    fun short(resource: String): String = when (resource) {
        GOLD -> "Gold BB"
        ELIXIR -> "Elixir BB"
        H_DARK -> "DE"
        else -> resource
    }
}
