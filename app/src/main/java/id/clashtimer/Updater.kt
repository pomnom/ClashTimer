package id.clashtimer

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Update aplikasi dari GitHub Releases (github.com/pomnom/ClashTimer). Setiap build Actions membuat rilis
 * "build-N" berisi ClashTimer.apk, dan nomor versi aplikasi = N, jadi cukup dibandingkan.
 *
 * APK diunduh lewat DownloadManager lalu dipasang dari Unduhan; file-nya dihapus otomatis setelah build baru
 * berjalan (lihat [cleanup]).
 */
object Updater {
    const val REPO = "pomnom/ClashTimer"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    /** Paling sering cek otomatis (saat aplikasi dibuka). */
    private const val CHECK_EVERY = 3 * 3_600_000L

    class Release(
        val build: Int,
        val tag: String,
        val name: String,
        val url: String,
        val size: Long,
        val publishedAt: Long,
        /** "sha256:…" dari GitHub bila tersedia. */
        val digest: String?,
        /** Catatan rilis (isi rilis GitHub), tanpa baris otomatis "Full Changelog". */
        val notes: String = "",
    )

    /** Sedang memeriksa unduhan (jangan dibersihkan). */
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
            p.getLong("published", 0L), p.getString("digest", null), p.getString("notes", "") ?: "")
    }

    /** Buang baris otomatis GitHub ("**Full Changelog**: …") dan baris kosong berlebih. */
    private fun cleanNotes(body: String?): String = body.orEmpty().lines()
        .filterNot { it.contains("Full Changelog", ignoreCase = true) }
        .joinToString("\n").trim()

    // ---------------------------------------------------------------- yang baru (catatan di dalam APK)

    /** Isi assets/changelog.txt: judul ("# …") + butir ("- …"). */
    fun bundledNotes(ctx: Context): Pair<String, List<String>>? = runCatching {
        val lines = ctx.assets.open("changelog.txt").bufferedReader().use { it.readLines() }
        val title = lines.firstOrNull { it.startsWith("#") }?.trimStart('#', ' ') ?: "Yang baru"
        title to lines.filter { it.startsWith("-") }.map { it.removePrefix("-").trim() }
    }.getOrNull()

    /** Catatan "Yang baru" belum pernah ditampilkan untuk build yang terpasang. */
    fun shouldShowNews(ctx: Context): Boolean {
        val build = installedBuild(ctx)
        val seen = prefs(ctx).getLong("news_seen", -1L)
        if (seen < 0) {
            // Pemasangan baru (belum ada desa): tidak perlu ditampilkan. Sudah ada desa = update dari build lama.
            if (Store.accounts(ctx).isEmpty()) {
                prefs(ctx).edit().putLong("news_seen", build).apply()
                return false
            }
            return bundledNotes(ctx) != null
        }
        return build > seen && bundledNotes(ctx) != null
    }

    fun markNewsSeen(ctx: Context) = prefs(ctx).edit().putLong("news_seen", installedBuild(ctx)).apply()

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
                published, apk.optString("digest").takeIf { it.startsWith("sha256:") }, cleanNotes(o.optString("body")),
            ) else null
            if (rel != null) {
                e.putInt("build", rel.build).putString("tag", rel.tag).putString("name", rel.name).putString("url", rel.url)
                    .putLong("size", rel.size).putLong("published", rel.publishedAt).putString("digest", rel.digest)
                    .putString("notes", rel.notes)
            }
            e.apply()
            rel
        } finally {
            conn.disconnect()
        }
    }.onFailure { prefs(ctx).edit().putString("error", it.message ?: it.javaClass.simpleName).apply() }

    // ---------------------------------------------------------------- unduh

    /*
     * Tanpa izin memasang aplikasi (REQUEST_INSTALL_PACKAGES): aplikasi yang bisa mengunduh DAN memasang APK
     * ditandai Play Protect sebagai berbahaya. APK diunduh lewat DownloadManager Android ke folder Unduhan,
     * lalu dipasang dari notifikasi "Unduhan selesai" / aplikasi Unduhan. File-nya dihapus otomatis begitu
     * build baru berjalan (atau setelah 3 hari bila tidak dipasang).
     */

    private const val KEEP = 3 * 24 * 3_600_000L

    private fun dm(ctx: Context): DownloadManager? = ctx.getSystemService(DownloadManager::class.java)

    fun fileName(build: Int) = "ClashTimer-build-$build.apk"

    /** Unduhan update yang tercatat: (id DownloadManager, build). */
    fun download(ctx: Context): Pair<Long, Int>? {
        val p = prefs(ctx)
        val id = p.getLong("dl_id", -1L)
        return if (id > 0) id to p.getInt("dl_build", 0) else null
    }

    class Progress(val status: Int, val done: Long, val total: Long, val reason: Int)

    fun progress(ctx: Context, id: Long): Progress? = runCatching {
        dm(ctx)?.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) return@use null
            Progress(
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)),
            )
        }
    }.getOrNull()

    /** Mulai unduh APK [r] (unduhan update sebelumnya dihapus). */
    fun enqueue(ctx: Context, r: Release): Long {
        val dm = dm(ctx) ?: throw IllegalStateException("pengunduh Android tidak tersedia")
        forget(ctx)
        val req = DownloadManager.Request(Uri.parse(r.url))
            .setTitle("Clash Timer build ${r.build}")
            .setDescription("Ketuk untuk memasang update")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .addRequestHeader("User-Agent", "ClashTimer-Android")
        if (Build.VERSION.SDK_INT >= 29) req.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName(r.build))
        else req.setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, fileName(r.build))
        val id = dm.enqueue(req)
        prefs(ctx).edit().putLong("dl_id", id).putInt("dl_build", r.build).putLong("dl_at", System.currentTimeMillis()).apply()
        return id
    }

    /** Periksa SHA-256 file unduhan bila GitHub memberikannya (memblokir). */
    fun verify(ctx: Context, id: Long, r: Release): Boolean {
        val d = r.digest ?: return true
        val dm = dm(ctx) ?: return true
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            dm.openDownloadedFile(id).use { pfd ->
                java.io.FileInputStream(pfd.fileDescriptor).use { input ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        md.update(buf, 0, n)
                    }
                }
            }
            val hex = md.digest().joinToString("") { b -> "%02x".format(b) }
            d.removePrefix("sha256:").equals(hex, ignoreCase = true)
        }.getOrDefault(true)
    }

    /** Hapus unduhan update yang tercatat (file APK ikut terhapus). */
    fun forget(ctx: Context) {
        val (id, _) = download(ctx) ?: return
        runCatching { dm(ctx)?.remove(id) }
        prefs(ctx).edit().remove("dl_id").remove("dl_build").remove("dl_at").apply()
    }

    /**
     * Hapus APK update yang sudah tidak dibutuhkan: build-nya sudah terpasang, atau tidak dipasang dalam 3 hari.
     * Dipanggil saat aplikasi dibuka dan setelah update terpasang (MY_PACKAGE_REPLACED).
     */
    fun cleanup(ctx: Context) {
        if (busy.get()) return
        // Sisa versi sebelumnya (unduhan ke cache).
        runCatching {
            ctx.cacheDir.listFiles()?.filter { it.name.startsWith("update") && (it.name.endsWith(".apk") || it.name.endsWith(".part")) }
                ?.forEach { it.delete() }
        }
        val (_, build) = download(ctx) ?: return
        val at = prefs(ctx).getLong("dl_at", 0L)
        if (installedBuild(ctx) >= build || System.currentTimeMillis() - at > KEEP) forget(ctx)
    }

    /** Daftar Unduhan Android (tempat memasang APK yang sudah diunduh). */
    fun openDownloads(ctx: Context): Boolean = runCatching {
        ctx.startActivity(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess

    fun mb(bytes: Long): String = String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1024.0 / 1024.0).replace('.', ',')
}
