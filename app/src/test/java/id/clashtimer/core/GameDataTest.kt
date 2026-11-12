package id.clashtimer.core

import id.clashtimer.core.TestData.gd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arti baris data game. Troop, spell, siege, hero & pet: baris Lv N di file = upgrade DARI N ke N+1,
 * aplikasi menggesernya supaya [Item.lv] selalu berarti "untuk mencapai Lv N" seperti bangunan.
 */
class GameDataTest {
    private fun item(name: String, cat: Cat) = gd.items.values.first { it.name == name && it.cat == cat }

    @Test fun headhunterSesuaiGame() {
        // Dicek di game (TH13, Lab 11): Headhunter Lv2 → 3 = 90 rb DE, 7 hari.
        val lv3 = item("Headhunter", Cat.TROOP).lv(3)!!
        assertEquals(90_000L, lv3.cost)
        assertEquals(7 * 86_400L, lv3.time)
        assertEquals(11, lv3.req)
    }

    @Test fun levelPertamaGratis() {
        val bad = gd.items.values.filter { it.cat in setOf(Cat.TROOP, Cat.SPELL, Cat.HERO, Cat.PET, Cat.BB_TROOP, Cat.BB_HERO) }
            .filter { it.levels.isNotEmpty() }
            .filter { i -> i.levels.minByOrNull { it.level }!!.let { it.cost != 0L || it.time != 0L } }
        assertTrue("level pertama harus gratis: ${bad.map { it.name }}", bad.isEmpty())
    }

    @Test fun levelMaxPunyaBiaya() {
        // Sebelum digeser, baris level max bernilai 0 sehingga level terakhir tidak pernah direncanakan.
        val bad = gd.items.values.filter { it.cat in setOf(Cat.TROOP, Cat.SPELL, Cat.HERO, Cat.PET, Cat.BB_TROOP, Cat.BB_HERO) }
            .filter { it.levels.size > 1 }
            // Super troop & troop event tidak di-upgrade di lab: semua barisnya 0.
            .filter { i -> i.levels.any { it.cost > 0L || it.time > 0L } }
            .filter { i -> i.levels.maxByOrNull { it.level }!!.let { it.cost <= 0L && it.time <= 0L } }
        assertTrue("level max tanpa biaya: ${bad.map { it.name }}", bad.isEmpty())
    }

    @Test fun bangunanTidakDigeser() {
        val gs = gd.item(Capacity.HOME_GOLD_STORAGE)!!
        val top = gs.levels.maxByOrNull { it.level }!!
        assertTrue("Gold Storage level max harus punya biaya", top.cost > 0 && top.time > 0)
        assertEquals(0L, gd.item(GameData.TOWN_HALL)!!.lv(1)!!.cost)
    }

    @Test fun kapasitasDariData() {
        // TH13: Dark Elixir Storage Lv8 (280 rb) + Town Hall 20 rb (dicek di game).
        val v = TestData.village("th13_bh7")
        assertEquals(300_000L, Capacity.maxAt(v, gd, Capacity.H_DARK))
    }
}
