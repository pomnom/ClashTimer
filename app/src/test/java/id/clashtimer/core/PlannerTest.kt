package id.clashtimer.core

import id.clashtimer.core.TestData.gd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rencana dari contoh desa harus masuk akal: syarat terpenuhi, gudang cukup, target tercapai. */
class PlannerTest {
    private class Run(val name: String, val v: Village, val sim: SimResult) {
        val steps get() = (sim.running + sim.steps).sortedBy { it.start }
    }

    private val runs: List<Run> by lazy {
        TestData.villages.flatMap { name ->
            val v = TestData.village(name)
            val fin = TestData.finishes(v)
            val ps = TestData.settings()
            listOf(
                Run("$name Home", v, Planner.optimize(v, gd, ps, v.exportAt, fin, TestData.HOME, keepSteps = 5000).sim),
                Run("$name BB", v, Planner.optimize(v, gd, ps, v.exportAt, fin, TestData.BB, keepSteps = 5000).sim),
            )
        }
    }

    @Test fun targetTercapai() {
        for (r in runs) {
            val bb = r.name.endsWith("BB")
            val t = Planner.target(r.sim, TestData.settings(), bb)
            assertTrue("${r.name}: target tidak tercapai", t < r.v.exportAt + 3 * 365 * DAY)
            assertTrue("${r.name}: rencana kosong", r.sim.steps.isNotEmpty())
        }
    }

    @Test fun gudangSelaluCukup() {
        // Kapasitas berjalan: storage & hall yang selesai dalam rencana menambah kapasitas.
        for (r in runs) {
            val vv = r.v.copy()
            var bhNow = r.v.bh
            val growth = r.steps.filter { s ->
                s.item?.let { it.id == GameData.BUILDER_HALL || Capacity.storedBy(it.id) != null } == true
            }.sortedBy { it.finish }
            var k = 0
            val bad = ArrayList<String>()
            for (s in r.steps) {
                while (k < growth.size && growth[k].finish <= s.start) {
                    val d = growth[k++]
                    if (d.item!!.id == GameData.BUILDER_HALL) bhNow = d.toLevel
                    else {
                        vv.insts.firstOrNull { it.item.id == d.item!!.id && it.level == (if (d.isNew) 0 else d.fromLevel) }?.level = d.toLevel
                    }
                }
                if (s.running || s.gear) continue
                val res = s.item?.resource ?: continue
                val bb = res == Capacity.GOLD || res == Capacity.ELIXIR
                val cap = Capacity.of(vv, res, if (bb) bhNow else vv.th) ?: continue
                if (s.cost > cap) bad += "${s.title} butuh ${s.cost} $res, gudang $cap"
            }
            assertTrue("${r.name}: ${bad.take(5)}", bad.isEmpty())
        }
    }

    @Test fun risetStarLabMenungguLevelStarLab() {
        for (r in runs.filter { it.name.endsWith("BB") }) {
            val slDone = HashMap<Int, Long>().apply { put(r.v.starLab, r.v.exportAt) }
            r.steps.filter { it.item?.id == GameData.STAR_LAB }.forEach { slDone[it.toLevel] = it.finish }
            val bad = r.steps.filter { !it.running && it.lane == Lane.STAR_LAB }.filter { s ->
                val need = s.item?.lv(s.toLevel)?.req ?: 0
                val ready = slDone.filterKeys { it >= need }.values.minOrNull()
                ready == null || ready > s.start
            }
            assertTrue("${r.name}: ${bad.take(3).map { it.title }}", bad.isEmpty())
        }
    }

    @Test fun hanyaSatuGearUp() {
        // Hanya satu Master Builder yang bisa bekerja di Home Village.
        for (r in runs.filter { it.name.endsWith("BB") }) {
            val gears = r.steps.filter { it.gear }
            for (a in gears) for (b in gears) if (a !== b) {
                assertTrue("${r.name}: ${a.title} & ${b.title} bersamaan", a.finish <= b.start || b.finish <= a.start)
            }
        }
    }

    private fun PlannedStep.essential() = item?.cat == Cat.BUILDING && item?.type == "Army"

    /**
     * Bangunan esensial yang belum dimulai pada saat [t] padahal sudah bisa dikerjakan (level sebelumnya dari
     * bangunan yang sama tidak sedang berjalan).
     */
    private fun essentialWaiting(steps: List<PlannedStep>, t: Long): List<PlannedStep> {
        val ess = steps.filter { it.lane == Lane.BUILDER && it.essential() }
        return ess.filter { e -> e.start > t && ess.none { it.item?.id == e.item?.id && it.start <= t && it.finish > t } }
    }

    @Test fun bangunanEsensialSebelumPertahanan() {
        // Semua strategi: builder yang tidak menaikkan hero mengerjakan Army Camp, Barracks, Workshop, dll.
        // sebelum pertahanan & trap (Rush TH: Town Hall & bangunan baru tetap paling dulu; trap baru yang
        // pemasangannya instan dipasang dulu di semua strategi).
        for (name in listOf("th12_bh9", "th14_bh8")) {
            val v = TestData.village(name)
            for (strat in Strategy.entries) {
                val ps = TestData.settings(home = strat)
                val s = Planner.optimize(v, gd, ps, v.exportAt, TestData.finishes(v), TestData.HOME, keepSteps = 5000).sim
                val all = s.running + s.steps
                val other = s.steps.filter { it.lane == Lane.BUILDER && it.item?.cat != Cat.HERO && it.item?.isTownHall != true && !it.essential() }
                    // Bangunan baru di Rush TH & trap baru yang instan memang dipasang lebih dulu.
                    .filter { !(it.isNew && (strat == Strategy.RUSH || it.finish == it.start)) }
                assertTrue("$name $strat: tidak ada bangunan esensial tersisa di contoh desa", s.steps.any { it.essential() })
                val early = other.filter { essentialWaiting(all, it.start).isNotEmpty() }
                assertTrue("$name $strat: menyalip bangunan esensial: ${early.take(5).map { it.title }}", early.isEmpty())
            }
        }
    }

    @Test fun esensialSebelumHeroDiSeimbangDanRush() {
        // Seimbang & Rush TH: hero baru dimulai setelah tidak ada lagi bangunan esensial yang bisa dikerjakan.
        for (name in listOf("th12_bh9", "th14_bh8")) {
            val v = TestData.village(name)
            for (strat in listOf(Strategy.MAX, Strategy.RUSH)) {
                val ps = TestData.settings(home = strat)
                val s = Planner.optimize(v, gd, ps, v.exportAt, TestData.finishes(v), TestData.HOME, keepSteps = 5000).sim
                val all = s.running + s.steps
                val early = s.steps.filter { it.lane == Lane.BUILDER && it.item?.cat == Cat.HERO && !it.gear }
                    .filter { essentialWaiting(all, it.start).isNotEmpty() }
                assertTrue("$name $strat: hero menyalip bangunan esensial: ${early.take(5).map { it.title }}", early.isEmpty())
            }
        }
    }

    @Test fun heroDuluHeroTetapPertama() {
        // Hero dulu: tanggal hero selesai tidak boleh lebih lambat daripada strategi Seimbang.
        for (name in listOf("th12_bh9", "th14_bh8")) {
            val v = TestData.village(name)
            fun heroDone(strat: Strategy) =
                Planner.optimize(v, gd, TestData.settings(home = strat), v.exportAt, TestData.finishes(v), TestData.HOME, keepSteps = 10).sim.heroDone!!
            assertTrue("$name: Hero dulu lebih lambat daripada Seimbang", heroDone(Strategy.HERO) <= heroDone(Strategy.MAX))
        }
    }

    @Test fun trapBaruLangsungDipasang() {
        // Trap baru (instan) dipasang sebelum upgrade lain yang memakai waktu builder dimulai — hari yang sama.
        val v = TestData.village("th14_bh8")
        for (strat in Strategy.entries) {
            val ps = TestData.settings(home = strat)
            val s = Planner.optimize(v, gd, ps, v.exportAt, TestData.finishes(v), TestData.HOME, keepSteps = 5000).sim
            val traps = s.steps.filter { it.lane == Lane.BUILDER && it.isNew && it.item?.cat == Cat.TRAP }
            assertTrue("$strat: contoh desa harus punya trap baru", traps.isNotEmpty())
            val first = s.steps.filter { it.lane == Lane.BUILDER }.minOf { it.start }
            assertTrue("$strat: trap baru terlambat dipasang", traps.all { it.start - first < DAY })
        }
    }

    @Test fun jalurPetDibukaSetelahPetHouseDibangun() {
        // TH14 tanpa Pet House: pet ikut direncanakan begitu Pet House selesai dibangun.
        val v = TestData.village("th14_bh8")
        assertEquals("contoh desa harus belum punya Pet House", 0, v.petHouse)
        for (strat in Strategy.entries) {
            val ps = TestData.settings(home = strat)
            val s = Planner.optimize(v, gd, ps, v.exportAt, TestData.finishes(v), TestData.HOME, keepSteps = 5000).sim
            val house = s.steps.first { it.item?.id == GameData.PET_HOUSE }
            val pets = s.steps.filter { it.lane == Lane.PET }
            assertTrue("$strat: pet tidak direncanakan", pets.isNotEmpty())
            assertTrue("$strat: pet dimulai sebelum Pet House selesai", pets.all { it.start >= house.finish })
            assertTrue("$strat: tanggal pet selesai kosong", s.petDone != null)
        }
    }

    @Test fun rencanaSamaBilaDiulang() {
        val v = TestData.village("th13_bh7")
        val fin = TestData.finishes(v)
        val a = Planner.simulate(v, gd, TestData.settings(), v.exportAt, fin, TestData.HOME, keepSteps = 50)
        val b = Planner.simulate(v, gd, TestData.settings(), v.exportAt, fin, TestData.HOME, keepSteps = 50)
        assertTrue(a.steps.map { it.title to it.start } == b.steps.map { it.title to it.start })
    }
}
