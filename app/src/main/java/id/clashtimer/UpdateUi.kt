package id.clashtimer

import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.Fmt
import java.time.ZoneId

/** Tampilan update aplikasi: kartu "versi baru", dialog unduhan, langkah pasang. */
object UpdateUi {

    /** Kartu di layar utama bila ada build baru di GitHub. */
    fun offer(act: MainActivity, box: LinearLayout) {
        val r = Updater.available(act) ?: return
        if (Updater.dismissed(act) >= r.build) return
        val (card, col) = Ui.card(act, padV = 12, tinted = true)
        col.addView(Ui.text(act, "⬆️ Clash Timer build ${r.build} tersedia", Ui.TITLE_S))
        val date = if (r.publishedAt > 0) " · ${Fmt.at(r.publishedAt, ZoneId.systemDefault())}" else ""
        col.addView(Ui.muted(act, "Terpasang build ${Updater.installedBuild(act)} · ${Updater.mb(r.size)}$date · diunduh di aplikasi, APK dihapus otomatis"))
        val row = LinearLayout(act).apply { gravity = Gravity.END }
        row.addView(Ui.flat(act, "Nanti") {
            Updater.dismiss(act, r.build)
            act.renderBody()
        }.apply { isAllCaps = false })
        row.addView(Ui.filled(act, "Update") { start(act, r) }.apply { isAllCaps = false })
        col.addView(row)
        box.addView(card)
    }

    /**
     * Unduh lewat pengunduh Android (notifikasi progres), lalu pasang dari Unduhan. Aplikasi ini sengaja tidak
     * memasang APK sendiri: izin itu membuat Play Protect menandai Clash Timer sebagai tidak aman.
     */
    fun start(act: AppCompatActivity, r: Updater.Release) {
        val app = act.applicationContext
        // Sudah diunduh sebelumnya: langsung ke langkah pasang.
        val prev = Updater.download(app)
        if (prev != null && prev.second == r.build) {
            val p = Updater.progress(app, prev.first)
            when (p?.status) {
                android.app.DownloadManager.STATUS_SUCCESSFUL -> { ready(act, r); return }
                android.app.DownloadManager.STATUS_RUNNING, android.app.DownloadManager.STATUS_PENDING,
                android.app.DownloadManager.STATUS_PAUSED -> { watch(act, r, prev.first); return }
            }
        }
        val id = runCatching { Updater.enqueue(app, r) }.getOrElse { e ->
            failed(act, r, e.message ?: "tidak bisa mulai mengunduh")
            return
        }
        watch(act, r, id)
    }

    /** Dialog progres: membaca status unduhan tiap ½ detik. */
    private fun watch(act: AppCompatActivity, r: Updater.Release, id: Long) {
        val app = act.applicationContext
        val pad = Ui.dp(act, 20)
        val col = Ui.vbox(act).apply { setPadding(pad, Ui.dp(act, 8), pad, 0) }
        val label = Ui.muted(act, "Menyiapkan unduhan…", Ui.BODY_M)
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; isIndeterminate = true }
        col.addView(label)
        col.addView(bar, Ui.matchWrap().apply { topMargin = Ui.dp(act, 10) })
        col.addView(Ui.muted(act, "Unduhan tetap berjalan walau dialog ini ditutup (lihat notifikasi).").apply {
            setPadding(0, Ui.dp(act, 10), 0, 0)
        })
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var stopped = false
        val dialog = MaterialAlertDialogBuilder(act)
            .setTitle("Mengunduh build ${r.build}")
            .setView(col)
            .setPositiveButton("Sembunyikan") { _, _ -> stopped = true }
            .setNegativeButton("Batalkan") { _, _ ->
                stopped = true
                Updater.forget(app)
            }
            .setCancelable(false)
            .show()
        val poll = object : Runnable {
            override fun run() {
                if (stopped || act.isFinishing || act.isDestroyed) return
                val p = Updater.progress(app, id)
                when (p?.status) {
                    null -> {
                        stopped = true
                        runCatching { dialog.dismiss() }
                        failed(act, r, "unduhan dihapus atau dibatalkan")
                        return
                    }
                    android.app.DownloadManager.STATUS_SUCCESSFUL -> {
                        stopped = true
                        bar.isIndeterminate = true
                        label.text = "Memeriksa file…"
                        Updater.busy.set(true)
                        Thread {
                            val ok = Updater.verify(app, id, r)
                            Updater.busy.set(false)
                            act.runOnUiThread {
                                runCatching { dialog.dismiss() }
                                if (ok) ready(act, r)
                                else {
                                    Updater.forget(app)
                                    failed(act, r, "file rusak (checksum beda)")
                                }
                            }
                        }.start()
                        return
                    }
                    android.app.DownloadManager.STATUS_FAILED -> {
                        stopped = true
                        runCatching { dialog.dismiss() }
                        Updater.forget(app)
                        failed(act, r, "pengunduh Android gagal (kode ${p.reason})")
                        return
                    }
                    else -> {
                        val total = p.total.takeIf { it > 0 } ?: r.size
                        bar.isIndeterminate = total <= 0 || p.done <= 0
                        if (total > 0) bar.progress = (p.done * 1000 / total).toInt()
                        label.text = if (p.status == android.app.DownloadManager.STATUS_PAUSED) "Menunggu jaringan…"
                        else "Mengunduh ${Updater.mb(p.done)}" + if (total > 0) " / ${Updater.mb(total)}" else ""
                    }
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(poll)
    }

    /** Unduhan selesai: pasang dari notifikasi atau daftar Unduhan. */
    private fun ready(act: AppCompatActivity, r: Updater.Release) {
        if (act.isFinishing || act.isDestroyed) return
        MaterialAlertDialogBuilder(act)
            .setTitle("Build ${r.build} siap dipasang")
            .setMessage("Ketuk notifikasi \"Unduhan selesai\", atau tekan Pasang lalu pilih ${Updater.fileName(r.build)} di Unduhan → Update.\n\n" +
                "File APK dihapus otomatis begitu build baru dibuka (atau setelah 3 hari bila tidak dipasang).")
            .setPositiveButton("Pasang") { _, _ ->
                if (!Updater.openDownloads(act)) Toast.makeText(act, "Buka aplikasi File / Unduhan lalu pilih ${Updater.fileName(r.build)}", Toast.LENGTH_LONG).show()
            }
            .setNegativeButton("Nanti", null)
            .show()
    }

    private fun failed(act: AppCompatActivity, r: Updater.Release, why: String) {
        if (act.isFinishing || act.isDestroyed) return
        MaterialAlertDialogBuilder(act)
            .setTitle("Update gagal")
            .setMessage("$why\n\nCoba lagi, atau unduh APK dari GitHub Releases (github.com/${Updater.REPO}).")
            .setPositiveButton("Coba lagi") { _, _ -> start(act, r) }
            .setNegativeButton("Tutup", null)
            .show()
    }

    /** Tombol "Cek update sekarang" di Pengaturan. */
    fun checkNow(act: AppCompatActivity, onDone: () -> Unit) {
        Toast.makeText(act, "Mengecek GitHub…", Toast.LENGTH_SHORT).show()
        val app = act.applicationContext
        Thread {
            val r = Updater.check(app)
            act.runOnUiThread {
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                val msg = when {
                    r.isFailure -> "Gagal mengecek: ${r.exceptionOrNull()?.message}"
                    Updater.available(app) == null -> "Sudah versi terbaru (build ${Updater.installedBuild(app)})"
                    else -> null
                }
                if (msg != null) Toast.makeText(act, msg, Toast.LENGTH_LONG).show()
                onDone()
            }
        }.start()
    }
}
