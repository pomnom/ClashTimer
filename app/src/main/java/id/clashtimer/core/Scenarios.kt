package id.clashtimer.core

/**
 * Simulasi "bagaimana jika": tanggal semua upgrade selesai (Home: builder & lab, Builder Base:
 * builder) untuk beberapa skenario dibanding pengaturan sekarang.
 */
object Scenarios {

    class Row(
        val label: String,
        val note: String,
        /** Builder Home Village selesai semua (null = tidak tercapai dalam simulasi / tidak dihitung). */
        val home: Long?,
        /** Bangunan, jebakan & tembok non-hero selesai (builder ke-6 & strategi terlihat di sini). */
        val buildings: Long?,
        /** Semua level hero selesai (rantai satu level per hero, biasanya penentu tanggal akhir). */
        val heroes: Long?,
        val lab: Long?,
        /** Builder Base selesai semua (strategi Max). Null bila skenario tidak memengaruhi Builder Base. */
        val bb: Long?,
        val current: Boolean = false,
    )

    private val HOME = setOf(Lane.BUILDER, Lane.LAB, Lane.PET)
    private val BB = setOf(Lane.BB_BUILDER, Lane.STAR_LAB)

    fun compute(v: Village, gd: GameData, ps: PlanSettings, now: Long, fin: Map<String, Long>, strategyLabel: (Strategy) -> String): List<Row> {
        val hasBB = v.hasBB && v.bh > 0
        fun run(label: String, note: String, p: PlanSettings, village: Village = v, withBB: Boolean = false, current: Boolean = false): Row {
            // Strategi lain: cari juga urutan tercepatnya (dua cara) supaya perbandingannya adil.
            val h = if (p.strategy != ps.strategy) {
                Planner.optimize(village, gd, p, now, fin, HOME, keepSteps = 100_000, policies = listOf(Policy.VALUE, Policy.CHAIN)).sim
            } else Planner.simulate(village, gd, p, now, fin, HOME, keepSteps = 100_000)
            val b = if (withBB && hasBB) Planner.simulate(village, gd, p.withBB(BBStrategy.MAX), now, fin, BB, keepSteps = 1) else null
            val builder = (h.running + h.steps).filter { it.lane == Lane.BUILDER }
            val buildings = builder.filter { it.item?.cat != Cat.HERO }.maxOfOrNull { it.finish }
            val heroes = builder.filter { it.item?.cat == Cat.HERO }.maxOfOrNull { it.finish }
            return Row(label, note, h.builderDone, buildings, heroes, h.labDone, b?.bbBuilderDone, current)
        }

        val rows = ArrayList<Row>()
        val gp = ps.gold.builderPct > 0 || ps.gold.researchPct > 0
        val gpNote = if (gp) "Gold Pass ${ps.gold.builderPct}/${ps.gold.researchPct}%" else "tanpa Gold Pass"
        rows += run("Pengaturan sekarang", "${strategyLabel(ps.strategy)} · $gpNote", ps, withBB = true, current = true)

        rows += if (gp) run("Tanpa Gold Pass", "Builder & Research Boost 0%", ps.with(gold = GoldPass.NONE), withBB = true)
        else run("Dengan Gold Pass", "Builder & Research Boost 20%", ps.with(gold = GoldPass(20, 20)), withBB = true)

        val hj = ps.events.firstOrNull { it.enabled && it.end > now && it.name.contains("Hammer", ignoreCase = true) }
        rows += if (hj != null) run("Tanpa ${hj.name}", "event dimatikan", ps.with(events = ps.events - hj))
        else {
            val ev = GameEvent.hammerJam(now).copy(enabled = true)
            run("Dengan Hammer Jam", "−50% waktu & biaya, perkiraan ${Fmt.dateShort(ev.start, ps.schedule.zone)}–${Fmt.dateShort(ev.end, ps.schedule.zone)}",
                ps.with(events = ps.events + ev))
        }

        for (s in Strategy.entries) if (s != ps.strategy) rows += run("Strategi ${strategyLabel(s)}", "saran memakai strategi ini", ps.with(strategy = s))

        if (v.homeBuilders < 6) {
            val six = Village(
                v.tag, v.exportAt, v.insts, v.walls, v.bbWalls, v.helpers, v.timers, v.unknown,
                v.homeBuilders + 1, v.bbBuildersAuto, v.hasHome, v.hasBB, v.equipment, v.crafted, v.clock, v.potions,
            )
            rows += run("Dengan builder ke-6", "B.O.B terbuka sekarang (${v.homeBuilders + 1} builder)", ps, six)
        }
        return rows
    }
}
