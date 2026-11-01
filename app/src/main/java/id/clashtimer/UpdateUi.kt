package id.clashtimer

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.Fmt
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean

/** Tampilan update aplikasi: kartu "versi baru", dialog unduhan, izin pemasangan. */
object UpdateUi {

    /** Rilis yang menunggu izin "Instal aplikasi tidak dikenal"; dilanjutkan saat kembali ke aplikasi. */
    private var pending: Updater.Release? = null

    /** Kartu di layar utama bila ada build baru di GitHub. */
    fun offer(act: MainActivity, box: LinearLayout) {
        val r = Updater.available(act) ?: return
        if (Updater.dismissed(act) >= r.build) return
        val (card, col) = Ui.card(act, padV = 12, tinted = true)
        col.addView(Ui.text(act, "⬆️ Clash Timer build ${r.build} tersedia", Ui.TITLE_S))
        val date = if (r.publishedAt > 0) " · ${Fmt.at(r.publishedAt, ZoneId.systemDefault())}" else ""
        col.addView(Ui.muted(act, "Terpasang build ${Updater.installedBuild(act)} · ${Updater.mb(r.size)}$date · diunduh & dipasang langsung dari aplikasi"))
        val row = LinearLayout(act).apply { gravity = Gravity.END }
        row.addView(Ui.flat(act, "Nanti") {
            Updater.dismiss(act, r.build)
            act.renderBody()
        }.apply { isAllCaps = false })
        row.addView(Ui.filled(act, "Update") { start(act, r) }.apply { isAllCaps = false })
        col.addView(row)
        box.addView(card)
    }

    /** Dipanggil di onResume: lanjutkan update setelah izin pemasangan diberikan. */
    fun resume(act: AppCompatActivity) {
        val r = pending ?: return
        if (!Updater.canInstall(act)) return
        pending = null
        start(act, r)
    }

    fun start(act: AppCompatActivity, r: Updater.Release) {
        if (Updater.busy.get()) {
            Toast.makeText(act, "Update sedang diunduh…", Toast.LENGTH_SHORT).show()
            return
        }
        if (!Updater.canInstall(act)) {
            pending = r
            MaterialAlertDialogBuilder(act)
                .setTitle("Izinkan pemasangan update")
                .setMessage("Android perlu izin \"Instal aplikasi tidak dikenal\" untuk Clash Timer (sekali saja). " +
                    "Aktifkan di halaman berikutnya lalu kembali ke Clash Timer — update langsung berlanjut.")
                .setPositiveButton("Buka pengaturan") { _, _ ->
                    runCatching {
                        act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
                    }.onFailure {
                        pending = null
                        Toast.makeText(act, "Buka Pengaturan HP → Aplikasi → Clash Timer → Instal aplikasi tidak dikenal", Toast.LENGTH_LONG).show()
                    }
                }
                .setNegativeButton("Batal") { _, _ -> pending = null }
                .show()
            return
        }

        val pad = Ui.dp(act, 20)
        val col = Ui.vbox(act).apply { setPadding(pad, Ui.dp(act, 8), pad, 0) }
        val label = Ui.muted(act, "Menghubungi GitHub…", Ui.BODY_M)
        val bar = ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000; isIndeterminate = true }
        col.addView(label)
        col.addView(bar, Ui.matchWrap().apply { topMargin = Ui.dp(act, 10) })
        col.addView(Ui.muted(act, "File APK dihapus otomatis setelah diserahkan ke pemasang Android.").apply {
            setPadding(0, Ui.dp(act, 10), 0, 0)
        })
        val cancelled = AtomicBoolean(false)
        val dialog = MaterialAlertDialogBuilder(act)
            .setTitle("Update ke build ${r.build}")
            .setView(col)
            .setNegativeButton("Batal") { _, _ -> cancelled.set(true) }
            .setCancelable(false)
            .show()
        val app = act.applicationContext
        Updater.busy.set(true)
        Thread {
            val res = runCatching {
                val apk = Updater.download(app, r, { done, total ->
                    act.runOnUiThread {
                        bar.isIndeterminate = total <= 0
                        if (total > 0) bar.progress = (done * 1000 / total).toInt()
                        label.text = "Mengunduh ${Updater.mb(done)}" + if (total > 0) " / ${Updater.mb(total)}" else ""
                    }
                }) { cancelled.get() }
                act.runOnUiThread {
                    bar.isIndeterminate = true
                    label.text = "Menyiapkan pemasangan…"
                }
                Updater.install(app, apk)
            }
            Updater.busy.set(false)
            act.runOnUiThread {
                runCatching { dialog.dismiss() }
                val e = res.exceptionOrNull() ?: return@runOnUiThread
                Updater.cleanup(app)
                if (e is InterruptedException || cancelled.get()) return@runOnUiThread
                if (act.isFinishing || act.isDestroyed) return@runOnUiThread
                MaterialAlertDialogBuilder(act)
                    .setTitle("Update gagal")
                    .setMessage((e.message ?: e.javaClass.simpleName) + "\n\nCoba lagi, atau unduh APK dari GitHub Releases (github.com/${Updater.REPO}).")
                    .setPositiveButton("Coba lagi") { _, _ -> start(act, r) }
                    .setNegativeButton("Tutup", null)
                    .show()
            }
        }.start()
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
