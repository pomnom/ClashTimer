package id.clashtimer

import id.clashtimer.core.Logins
import id.clashtimer.core.OfflineSchedule
import id.clashtimer.core.PlannedStep
import id.clashtimer.core.Scored
import id.clashtimer.core.SimResult
import java.time.ZoneId

typealias Session = id.clashtimer.core.Session

/** Sesi login ([Logins]) + label & lencana pekerja untuk tampilan. */
object Sessions {
    fun build(sim: SimResult?, bb: Boolean, now: Long, until: Long) = Logins.build(sim, bb, now, until)
    fun label(s: PlannedStep) = Logins.label(s)
    fun isLab(s: PlannedStep) = Logins.isLab(s)
    fun workers(session: Session) = Logins.workers(session)
    fun note(session: Session) = Logins.note(session)
    fun title(s: PlannedStep) = Logins.title(s)
    fun mood(schedule: OfflineSchedule, at: Long) = Logins.mood(schedule, at)
    fun inSleep(schedule: OfflineSchedule, t: Long) = Logins.inSleep(schedule, t)

    fun outcome(p: Scored, schedule: OfflineSchedule, now: Long, zone: ZoneId): Pair<String, Int> =
        Logins.outcome(p, schedule, now) { HomeView.short(it, now, zone) }

    /**
     * Label pekerja untuk timer yang tercatat: urutan sama dengan rencana (B1 = yang paling cepat bebas,
     * termasuk yang sudah selesai tapi belum ditandai). Goblin = "G".
     */
    fun labels(ups: List<Upgrade>, now: Long): Map<String, String> {
        val out = HashMap<String, String>()
        for ((kind, prefix) in listOf(Kind.BUILDER to "B", Kind.BB_BUILDER to "M")) {
            val list = ups.filter { it.kind == kind && !it.extra }.sortedBy { maxOf(it.finishAt, now) }
            list.forEachIndexed { i, u -> out[u.id] = "$prefix${i + 1}" }
            ups.filter { it.kind == kind && it.extra }.forEach { out[it.id] = "G" }
        }
        for (u in ups) when (u.kind) {
            Kind.LAB -> out[u.id] = if (u.extra) "G" else "Lab"
            Kind.PET -> out[u.id] = "Pet"
            Kind.STAR_LAB -> out[u.id] = "Star"
            else -> {}
        }
        return out
    }

    fun isLabKind(k: Kind) = k == Kind.LAB || k == Kind.PET || k == Kind.STAR_LAB

    /** Lencana pekerja: kuning untuk builder, ungu untuk lab. */
    fun badge(ctx: android.content.Context, label: String, lab: Boolean) = if (lab) Ui.labBadge(ctx, label) else Ui.builderBadge(ctx, label)
}
