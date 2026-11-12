package id.clashtimer.core

import java.io.File
import java.time.ZoneId

/** Data bersama untuk tes: gamedata.json aplikasi dan contoh export desa (tag disamarkan). */
object TestData {
    /** Gradle menjalankan tes dari folder modul `app/`. */
    val gd: GameData by lazy { GameData.parse(File("src/main/assets/gamedata.json").readText()) }

    val zone: ZoneId = ZoneId.of("Asia/Makassar")

    /** TH11 / BH9 (B.O.B Control sudah dibangun) dan TH13 / BH7 (Builder Hall 8 sedang di-upgrade). */
    val villages = listOf("th11_bh9", "th13_bh7")

    fun village(name: String): Village =
        ExportReader.read(File("src/test/resources/villages/$name.json").readText(), gd, Long.MAX_VALUE / 4)

    fun finishes(v: Village): Map<String, Long> = v.timers.associate {
        it.uid to Projector.project(v.exportAt, it.remaining, it.lane, emptyList(), Projector.helperFor(v, it.lane, it.helped)).finishAt
    }

    /** Pengaturan umum: tidur 23–06, Gold Pass 20%, strategi Max & Builder ke-6, tanpa event & CWL. */
    fun settings(bb: BBStrategy = BBStrategy.SIXTH): PlanSettings = PlanSettings(
        OfflineSchedule(DailyWindow(true, 23 * 60, 6 * 60), DailyWindow(false, 0, 0), zone),
        CwlRule(false), GoldPass(20, 20), Strategy.MAX, 0, bb,
    )

    val HOME = setOf(Lane.BUILDER, Lane.LAB, Lane.PET)
    val BB = setOf(Lane.BB_BUILDER, Lane.STAR_LAB)
}
