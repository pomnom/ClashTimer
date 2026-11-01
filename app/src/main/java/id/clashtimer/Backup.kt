package id.clashtimer

import android.content.Context
import android.os.Build
import id.clashtimer.core.Fmt
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Pindah data antar perangkat (HP ↔ tablet) & file cadangan: semua desa, timer, tanda Sudah dimulai/selesai,
 * item, opsi per desa, pengaturan, event, riwayat, dan API key.
 */
object Backup {
    private const val PREFIX = "CLASHTIMER-DATA:"
    /** Kunci khusus perangkat ini (clipboard) tidak ikut dipindah. */
    private val LOCAL_KEYS = setOf("clip_seen", "clip_dismissed")

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE)

    /** Seluruh data sebagai JSON. */
    fun json(ctx: Context): JSONObject {
        val p = JSONObject()
        for ((k, v) in prefs(ctx).all) {
            if (k in LOCAL_KEYS || v == null) continue
            val e = when (v) {
                is String -> JSONArray().put("s").put(v)
                is Int -> JSONArray().put("i").put(v)
                is Long -> JSONArray().put("l").put(v)
                is Boolean -> JSONArray().put("b").put(v)
                is Float -> JSONArray().put("f").put(v.toDouble())
                is Set<*> -> JSONArray().put("S").put(JSONArray(v.map { it.toString() }))
                else -> continue
            }
            p.put(k, e)
        }
        return JSONObject().put("clashtimer", 1).put("at", System.currentTimeMillis())
            .put("device", "${Build.MANUFACTURER} ${Build.MODEL}").put("p", p)
    }

    /** Teks ringkas untuk clipboard (gzip + base64). */
    fun text(ctx: Context): String {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(json(ctx).toString().toByteArray(Charsets.UTF_8)) }
        return PREFIX + Base64.getEncoder().encodeToString(out.toByteArray())
    }

    /** Baca teks clipboard atau isi file cadangan (JSON biasa juga diterima). Null bila bukan data Clash Timer. */
    fun parse(raw: String): JSONObject? {
        val t = raw.trim()
        val jsonText = if (t.startsWith(PREFIX)) {
            runCatching {
                val bytes = Base64.getDecoder().decode(t.removePrefix(PREFIX).filter { !it.isWhitespace() })
                GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull() ?: return null
        } else t
        val o = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        return o.takeIf { it.optInt("clashtimer") == 1 && it.optJSONObject("p") != null }
    }

    fun looksLike(raw: String?): Boolean = raw != null && (raw.trim().startsWith(PREFIX) || raw.contains("\"clashtimer\""))

    /** Ringkasan untuk konfirmasi sebelum menimpa data. */
    fun describe(o: JSONObject): String {
        val p = o.optJSONObject("p") ?: JSONObject()
        val accounts = runCatching { JSONArray(p.optJSONArray("accounts")?.optString(1) ?: "[]").length() }.getOrDefault(0)
        val zone = java.time.ZoneId.systemDefault()
        return "Dari ${o.optString("device")} · ${Fmt.at(o.optLong("at"), zone)} · $accounts desa"
    }

    /** Ganti semua data di perangkat ini dengan [o], lalu pasang ulang alarm, pengingat & widget. */
    fun restore(ctx: Context, o: JSONObject) {
        val p = o.optJSONObject("p") ?: return
        val local = LOCAL_KEYS.associateWith { prefs(ctx).all[it] }
        val e = prefs(ctx).edit().clear()
        for (k in p.keys()) {
            val a = p.optJSONArray(k) ?: continue
            when (a.optString(0)) {
                "s" -> e.putString(k, a.optString(1))
                "i" -> e.putInt(k, a.optInt(1))
                "l" -> e.putLong(k, a.optLong(1))
                "b" -> e.putBoolean(k, a.optBoolean(1))
                "f" -> e.putFloat(k, a.optDouble(1).toFloat())
                "S" -> e.putStringSet(k, a.optJSONArray(1)?.let { s -> (0 until s.length()).map { s.optString(it) }.toSet() } ?: emptySet())
            }
        }
        for ((k, v) in local) if (v is String) e.putString(k, v) else if (v is Long) e.putLong(k, v)
        e.commit()
        // Rencana yang tersimpan di layar dihitung ulang.
        prefs(ctx).edit().putInt("plan_version", prefs(ctx).getInt("plan_version", 0) + 1).commit()
        // Timer yang waktunya sudah lewat tidak dinotifikasi ulang di perangkat ini.
        val now = System.currentTimeMillis()
        val list = Store.upgrades(ctx)
        list.forEach { if (it.finishAt <= now) { it.doneNotified = true; it.earlyNotified = true } }
        Store.saveUpgrades(ctx, list)
        Notifier.createChannels(ctx)
        AlarmScheduler.schedule(ctx)
        Attacks.schedule(ctx)
        Widgets.updateAll(ctx)
    }
}
