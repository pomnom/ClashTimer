package id.clashtimer.core

import id.clashtimer.core.TestData.gd
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

    @Test fun rencanaSamaBilaDiulang() {
        val v = TestData.village("th13_bh7")
        val fin = TestData.finishes(v)
        val a = Planner.simulate(v, gd, TestData.settings(), v.exportAt, fin, TestData.HOME, keepSteps = 50)
        val b = Planner.simulate(v, gd, TestData.settings(), v.exportAt, fin, TestData.HOME, keepSteps = 50)
        assertTrue(a.steps.map { it.title to it.start } == b.steps.map { it.title to it.start })
    }
}
