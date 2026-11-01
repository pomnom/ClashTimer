package id.clashtimer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import id.clashtimer.core.GameData
import id.clashtimer.core.Item
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Ikon bangunan, hero, troop & item dari ClashKing Assets (https://assets.clashk.ing).
 * Gambar diunduh saat pertama dibutuhkan lalu disimpan di cache HP — tidak dibundel di APK.
 * Gambar adalah milik Supercell; aplikasi ini fan content tidak resmi.
 */
object Icons {
    const val BASE = "https://assets.clashk.ing/"

    private val mem = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val exec = Executors.newFixedThreadPool(3)
    private val main = Handler(Looper.getMainLooper())

    /** Hanya diakses dari main thread. */
    private val pending = HashMap<String, MutableList<Pair<ImageView, View?>>>()
    private val failed = HashMap<String, Long>()

    fun enabled(ctx: Context): Boolean = Store.iconsOn(ctx)

    fun forItem(item: Item?, level: Int): String? = item?.icon(level.coerceAtLeast(1))

    fun forHelper(gd: GameData, id: Int): String? = gd.extraIcons[id.toString()]

    /** Huruf pengganti saat ikon belum ada, mis. "Cannon" → "C", "Battle Copter" → "BC". */
    fun initials(name: String): String {
        val words = name.split(' ', '-', '.').filter { it.isNotBlank() && it[0].isLetter() }
        return when {
            words.isEmpty() -> name.take(1)
            words.size == 1 -> words[0].take(1).uppercase()
            else -> (words[0].take(1) + words[1].take(1)).uppercase()
        }
    }

    /**
     * Kotak ikon persegi [sizeDp]. [fallback] tampil bila ikon dimatikan, belum terunduh, atau gagal.
     */
    fun view(ctx: Context, path: String?, sizeDp: Int, fallback: String, color: Int = Ui.GREY, round: Boolean = true): FrameLayout {
        val px = Ui.dp(ctx, sizeDp)
        val box = FrameLayout(ctx)
        val fb = TextView(ctx).apply {
            text = fallback
            gravity = Gravity.CENTER
            textSize = if (fallback.length > 2) sizeDp * 0.30f else sizeDp * 0.36f
            setTextColor(color)
            if (round) background = Ui.tint(color, px / 2f, 0x22)
        }
        box.addView(fb, FrameLayout.LayoutParams(px, px))
        val img = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        box.addView(img, FrameLayout.LayoutParams(px, px))
        box.layoutParams = LinearLayout.LayoutParams(px, px)
        if (path != null && enabled(ctx)) load(ctx, path, img, fb)
        return box
    }

    /** Pasang gambar [path] ke [img] (dari memori, cache file, atau unduh). */
    fun load(ctx: Context, path: String, img: ImageView, fallback: View?) {
        img.tag = path
        val cached = mem.get(path)
        if (cached != null) {
            img.setImageBitmap(cached)
            fallback?.visibility = View.INVISIBLE
            return
        }
        val f = failed[path]
        if (f != null && System.currentTimeMillis() - f < 10 * 60_000L) return
        val waiting = pending[path]
        if (waiting != null) {
            waiting += img to fallback
            return
        }
        pending[path] = mutableListOf(img to fallback)
        val app = ctx.applicationContext
        exec.execute {
            val bmp = runCatching { fetch(app, path) }.getOrNull()
            main.post {
                val list = pending.remove(path).orEmpty()
                if (bmp == null) {
                    failed[path] = System.currentTimeMillis()
                    return@post
                }
                mem.put(path, bmp)
                for ((iv, fb) in list) {
                    if (iv.tag == path) {
                        iv.setImageBitmap(bmp)
                        fb?.visibility = View.INVISIBLE
                    }
                }
            }
        }
    }

    /** Untuk widget (thread latar): ambil dari memori, cache file, atau unduh. Null bila ikon dimatikan/gagal. */
    fun bitmapBlocking(ctx: Context, path: String?): Bitmap? {
        if (path == null || !enabled(ctx)) return null
        mem.get(path)?.let { return it }
        val bmp = runCatching { fetch(ctx.applicationContext, path) }.getOrNull() ?: return null
        mem.put(path, bmp)
        return bmp
    }

    private fun cacheFile(ctx: Context, path: String): File {
        val dir = File(ctx.cacheDir, "icons")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, path.replace(Regex("[^A-Za-z0-9._-]"), "_"))
    }

    private fun fetch(ctx: Context, path: String): Bitmap? {
        val file = cacheFile(ctx, path)
        if (file.exists() && file.length() > 0) {
            decode(file.readBytes())?.let { return it }
        }
        val conn = URL(BASE + path).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 12000
        conn.setRequestProperty("User-Agent", "ClashTimer-Android")
        try {
            if (conn.responseCode != 200) return null
            val bytes = conn.inputStream.use { it.readBytes() }
            if (bytes.isEmpty() || bytes.size > 3_000_000) return null
            val bmp = decode(bytes) ?: return null
            runCatching { file.writeBytes(bytes) }
            return bmp
        } finally {
            conn.disconnect()
        }
    }

    /** Decode dengan pengecilan ke ±160 px supaya hemat memori. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 160 && bounds.outHeight / (sample * 2) >= 160) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    fun clearCache(ctx: Context) {
        mem.evictAll()
        failed.clear()
        File(ctx.cacheDir, "icons").deleteRecursively()
    }

    /** Ukuran cache ikon di HP (byte). */
    fun cacheSize(ctx: Context): Long =
        File(ctx.cacheDir, "icons").listFiles()?.sumOf { it.length() } ?: 0L
}
