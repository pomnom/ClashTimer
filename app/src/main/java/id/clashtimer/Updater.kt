package id.clashtimer

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Update aplikasi dari GitHub Releases (github.com/pomnom/ClashTimer). Setiap build Actions membuat rilis
 * "build-N" berisi ClashTimer.apk, dan nomor versi aplikasi = N, jadi cukup dibandingkan.
 *
 * APK diunduh ke cache lalu diserahkan ke PackageInstaller Android; file langsung dihapus begitu isinya
 * tersalin ke pemasang (salinan milik Android dibersihkan sistem sendiri). Sisa unduhan yang gagal dihapus
 * saat aplikasi dibuka berikutnya atau setelah update terpasang.
 */
object Updater {
    const val REPO = "pomnom/ClashTimer"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    /** Paling sering cek otomatis (saat aplikasi dibuka). */
    private const val CHECK_EVERY = 3 * 3_600_000L
    const val ACTION_STATUS = "id.clashtimer.UPDATE_STATUS"

    class Release(
        val build: Int,
        val tag: String,
        val name: String,
        val url: String,
        val size: Long,
        val publishedAt: Long,
        /** "sha256:…" dari GitHub bila tersedia. */
        val digest: String?,
    )

    /** Sedang mengunduh / menyerahkan ke pemasang (jangan bersihkan cache). */
    val busy = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("update", Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- versi

    fun installedBuild(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L)

    fun installedName(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

    fun autoCheck(ctx: Context) = prefs(ctx).getBoolean("auto", true)
    fun setAutoCheck(ctx: Context, on: Boolean) = prefs(ctx).edit().putBoolean("auto", on).apply()

    fun lastChecked(ctx: Context) = prefs(ctx).getLong("checked_at", 0L)
    fun lastError(ctx: Context): String? = prefs(ctx).getString("error", null)

    /** Rilis terbaru yang terakhir terbaca dari GitHub. */
    fun latest(ctx: Context): Release? {
        val p = prefs(ctx)
        val build = p.getInt("build", 0)
        val url = p.getString("url", null) ?: return null
        if (build <= 0) return null
        return Release(build, p.getString("tag", "") ?: "", p.getString("name", "") ?: "", url, p.getLong("size", 0L),
            p.getLong("published", 0L), p.getString("digest", null))
    }

    /** Rilis yang lebih baru dari yang terpasang (null = sudah terbaru / belum dicek). */
    fun available(ctx: Context): Release? = latest(ctx)?.takeIf { it.build > installedBuild(ctx) }

    fun dismissed(ctx: Context) = prefs(ctx).getInt("dismissed", 0)
    fun dismiss(ctx: Context, build: Int) = prefs(ctx).edit().putInt("dismissed", build).apply()

    // ---------------------------------------------------------------- cek

    /** Cek otomatis (paling sering tiap 3 jam); [onNew] dipanggil di thread utama bila ada rilis baru. */
    fun maybeCheck(ctx: Context, onNew: () -> Unit) {
        if (!autoCheck(ctx)) return
        val now = System.currentTimeMillis()
        if (now - lastChecked(ctx) in 0 until CHECK_EVERY) return
        val app = ctx.applicationContext
        prefs(app).edit().putLong("checked_at", now).apply()
        Thread {
            val before = available(app)?.build ?: 0
            val r = check(app)
            val after = available(app)?.build ?: 0
            if (r.isSuccess && after > before) main.post(onNew)
        }.start()
    }

    /** Baca rilis terbaru dari GitHub (memblokir — jalankan di background). */
    fun check(ctx: Context): Result<Release?> = runCatching {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "ClashTimer-Android")
        try {
            val code = conn.responseCode
            if (code == 404) throw IllegalStateException("belum ada rilis di GitHub")
            if (code == 403 || code == 429) throw IllegalStateException("GitHub membatasi permintaan, coba lagi nanti")
            if (code !in 200..299) throw IllegalStateException("GitHub HTTP $code")
            val o = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            val tag = o.optString("tag_name")
            val build = Regex("""(\d+)\s*$""").find(tag)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val assets = o.optJSONArray("assets")
            var apk: JSONObject? = null
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk", ignoreCase = true) && a.optString("state", "uploaded") == "uploaded") { apk = a; break }
            }
            val published = runCatching { java.time.Instant.parse(o.optString("published_at")).toEpochMilli() }.getOrDefault(0L)
            val e = prefs(ctx).edit().putLong("checked_at", System.currentTimeMillis()).remove("error")
            val rel = if (build > 0 && apk != null) Release(
                build, tag, o.optString("name").ifBlank { tag }, apk.getString("browser_download_url"), apk.optLong("size"),
                published, apk.optString("digest").takeIf { it.startsWith("sha256:") },
            ) else null
            if (rel != null) {
                e.putInt("build", rel.build).putString("tag", rel.tag).putString("name", rel.name).putString("url", rel.url)
                    .putLong("size", rel.size).putLong("published", rel.publishedAt).putString("digest", rel.digest)
            }
            e.apply()
            rel
        } finally {
            conn.disconnect()
        }
    }.onFailure { prefs(ctx).edit().putString("error", it.message ?: it.javaClass.simpleName).apply() }

    // ---------------------------------------------------------------- unduh & pasang

    private fun apkFile(ctx: Context) = File(ctx.cacheDir, "update.apk")

    /** Hapus sisa unduhan (bila tidak sedang mengunduh). */
    fun cleanup(ctx: Context) {
        if (busy.get()) return
        runCatching {
            ctx.cacheDir.listFiles()?.filter { it.name.startsWith("update") && (it.name.endsWith(".apk") || it.name.endsWith(".part")) }
                ?.forEach { it.delete() }
        }
    }

    /** Boleh memasang aplikasi (Android 8+: izin "Instal aplikasi tidak dikenal" untuk Clash Timer). */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    /**
     * Unduh APK ke cache (memblokir). [progress] dipanggil dengan (terunduh, total); [cancelled] dicek tiap
     * potongan. Ukuran (dan SHA-256 bila GitHub memberikannya) diperiksa.
     */
    fun download(ctx: Context, r: Release, progress: (Long, Long) -> Unit, cancelled: () -> Boolean): File {
        val part = File(ctx.cacheDir, "update.part")
        val out = apkFile(ctx)
        part.delete()
        out.delete()
        val conn = URL(r.url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "ClashTimer-Android")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("unduhan gagal (HTTP $code)")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: r.size
            val md = MessageDigest.getInstance("SHA-256")
            var done = 0L
            var last = 0L
            conn.inputStream.use { input ->
                part.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled()) throw InterruptedException("dibatalkan")
                        val n = input.read(buf)
                        if (n < 0) break
                        o.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        val now = System.currentTimeMillis()
                        if (now - last > 150) { last = now; progress(done, total) }
                    }
                }
            }
            progress(done, total)
            if (r.size > 0 && done != r.size) throw IllegalStateException("ukuran file tidak cocok, coba lagi")
            r.digest?.let { d ->
                val hex = md.digest().joinToString("") { b -> "%02x".format(b) }
                if (!d.removePrefix("sha256:").equals(hex, ignoreCase = true)) throw IllegalStateException("file rusak (checksum beda), coba lagi")
            }
            if (!part.renameTo(out)) throw IllegalStateException("tidak bisa menyimpan file")
            return out
        } catch (e: Exception) {
            part.delete()
            out.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Serahkan APK ke pemasang Android lalu hapus file-nya. Android menampilkan konfirmasi "Update"
     * (lewat [UpdateReceiver]); hasilnya juga dikirim ke sana.
     */
    fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Android 12+: tanpa konfirmasi bila Clash Timer sudah tercatat sebagai pemasangnya sendiri.
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("ClashTimer.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out, 64 * 1024) }
                    session.fsync(out)
                }
                // Isi sudah ada di pemasang Android: file unduhan tidak dibutuhkan lagi.
                apk.delete()
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val status = PendingIntent.getBroadcast(ctx, 7001,
                    Intent(ctx, UpdateReceiver::class.java).setAction(ACTION_STATUS), flags)
                session.commit(status.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        } finally {
            apk.delete()
        }
    }

    fun mb(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0).replace('.', ',')
}

/** Hasil pemasangan dari PackageInstaller: minta konfirmasi pengguna, atau laporkan gagal. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                if (confirm != null) runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> Updater.cleanup(context)
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                Updater.cleanup(context)
                Toast.makeText(context, "Update dibatalkan", Toast.LENGTH_SHORT).show()
            }
            else -> {
                Updater.cleanup(context)
                val code = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, 0)
                val why = when (code) {
                    PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                        "kunci tanda tangan APK berbeda dengan yang terpasang"
                    PackageInstaller.STATUS_FAILURE_STORAGE -> "penyimpanan HP penuh"
                    PackageInstaller.STATUS_FAILURE_BLOCKED -> "diblokir oleh sistem"
                    else -> intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "kode $code"
                }
                Toast.makeText(context, "Update gagal: $why", Toast.LENGTH_LONG).show()
            }
        }
    }
}
