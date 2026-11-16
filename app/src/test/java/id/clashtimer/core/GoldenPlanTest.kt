package id.clashtimer.core

import id.clashtimer.core.TestData.gd
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.util.Locale

/**
 * Rencana acuan: urutan langkah tiap strategi untuk contoh desa disimpan di `src/test/resources/golden/`.
 * Tes ini gagal bila urutan itu berubah, supaya perubahan prioritas yang tidak disengaja ketahuan sebelum rilis.
 *
 * - Perubahan yang DISENGAJA (aturan prioritas, strategi, perbaikan bug): buat ulang berkas acuan dengan
 *   menjalankan tes memakai variabel lingkungan `UPDATE_GOLDEN=1`
 *   (mis. `UPDATE_GOLDEN=1 ./gradlew testReleaseUnitTest`), periksa selisihnya, lalu ikutkan berkasnya.
 * - Bila `gamedata.json` berubah (update data game) rencana memang ikut berubah: tes ini DILEWATI — bukan gagal —
 *   sampai berkas acuan dibuat ulang untuk data yang baru. Baris pertama tiap berkas mencatat sidik data game.
 */
class GoldenPlanTest {
    private val dir = File("src/test/resources/golden")

    /** Sidik gamedata.json: acuan hanya dibandingkan bila data gamenya sama. */
    private val dataMark: String by lazy {
        val bytes = File("src/main/assets/gamedata.json").readBytes()
        MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private fun code(lane: Lane) = when (lane) {
        Lane.BUILDER -> "B"
        Lane.LAB -> "L"
        Lane.PET -> "P"
        Lane.BB_BUILDER -> "b"
        Lane.STAR_LAB -> "s"
    }

    /** Satu rencana sebagai teks: cara terpilih, tanggal penting (hari sejak export), lalu urutan langkah. */
    private fun plan(sb: StringBuilder, title: String, v: Village, ps: PlanSettings, lanes: Set<Lane>, bb: Boolean) {
        val now = v.exportAt
        fun day(t: Long?) = when {
            t == null -> "-"
            t == Long.MAX_VALUE -> "tidak tercapai"
            else -> "H+" + (t - now) / DAY
        }
        val o = Planner.optimize(v, gd, ps, now, TestData.finishes(v), lanes, keepSteps = 5000)
        val s = o.sim
        sb.append("== ").append(title).append(" · cara ")
            .append(lanes.sortedBy { it.ordinal }.joinToString(" ") { "${code(it)}=${o.policies[it]}" }).append('\n')
        sb.append("target ").append(day(Planner.target(s, ps, bb)))
        if (bb) {
            sb.append(" · builder ").append(day(s.bbBuilderDone)).append(" · star lab ").append(day(s.starLabDone))
                .append(" · B.O.B ").append(day(s.sixth?.doneAt))
        } else {
            sb.append(" · hero ").append(day(s.heroDone)).append(" · builder ").append(day(s.builderDone))
                .append(" · lab ").append(day(s.labDone)).append(" · pet ").append(day(s.petDone))
                .append(" · TH ").append(day(s.hallDone))
        }
        sb.append(" · ").append(s.steps.size).append(" langkah\n")
        for (st in s.steps) {
            val what = when {
                st.gear -> "${st.item?.name} gear-up"
                st.isNew -> "+${st.item?.name}"
                else -> "${st.item?.name} ${st.fromLevel}>${st.toLevel}"
            }
            sb.append(code(st.lane)).append(" H+").append(String.format(Locale.ROOT, "%03d", (st.start - now) / DAY))
                .append(' ').append(what).append('\n')
        }
    }

    private fun render(name: String): String {
        val v = TestData.village(name)
        val sb = StringBuilder()
        sb.append("# data game ").append(dataMark).append('\n')
        sb.append("# ").append(name).append(" — dibuat otomatis oleh GoldenPlanTest (UPDATE_GOLDEN=1); jangan diubah dengan tangan\n")
        for (strat in Strategy.entries) plan(sb, "Home ${strat.name}", v, TestData.settings(home = strat), TestData.HOME, false)
        if (v.bh > 0) for (bs in BBStrategy.entries) plan(sb, "Builder Base ${bs.name}", v, TestData.settings(bb = bs), TestData.BB, true)
        return sb.toString()
    }

    /** Baris pertama yang berbeda, dengan sedikit konteks (judul rencananya). */
    private fun firstDiff(name: String, old: List<String>, new: List<String>): String {
        val i = (0 until minOf(old.size, new.size)).firstOrNull { old[it] != new[it] } ?: minOf(old.size, new.size)
        val section = (i downTo 0).firstOrNull { it < new.size && new[it].startsWith("== ") }?.let { new[it] } ?: ""
        val changed = (0 until maxOf(old.size, new.size)).count { old.getOrNull(it) != new.getOrNull(it) }
        return "$name: $changed baris berbeda, pertama di baris ${i + 1} $section\n" +
            "   acuan   : ${old.getOrNull(i) ?: "(habis)"}\n   sekarang: ${new.getOrNull(i) ?: "(habis)"}"
    }

    @Test fun urutanRencanaSamaDenganAcuan() {
        val update = System.getenv("UPDATE_GOLDEN") == "1"
        val problems = ArrayList<String>()
        var outdated = 0
        for (name in TestData.villages) {
            val file = File(dir, "$name.txt")
            val now = render(name)
            if (update) {
                dir.mkdirs()
                file.writeText(now)
                continue
            }
            assertTrue("Berkas acuan ${file.path} belum ada — jalankan tes dengan UPDATE_GOLDEN=1", file.exists())
            val old = file.readText().replace("\r\n", "\n")
            // Data game sudah diperbarui sejak acuan dibuat: rencana wajar berubah, tidak dibandingkan.
            if (old.lineSequence().first() != now.lineSequence().first()) {
                outdated++
                continue
            }
            if (old != now) problems += firstDiff(name, old.lines(), now.lines())
        }
        if (problems.isNotEmpty()) {
            fail(
                "Urutan rencana berubah dari acuan:\n" + problems.joinToString("\n") +
                    "\nBila perubahan ini disengaja, buat ulang acuan: UPDATE_GOLDEN=1 ./gradlew testReleaseUnitTest",
            )
        }
        assumeTrue("Acuan dibuat untuk data game yang lama — buat ulang dengan UPDATE_GOLDEN=1", outdated == 0)
    }
}
