package id.clashtimer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.arr
import id.clashtimer.core.obj
import id.clashtimer.core.str
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Pengingat serangan yang belum dipakai: hari perang CWL, war biasa, dan Raid Weekend.
 * Jadwal cek disusun saat sinkron API; 3 jam sebelum berakhir aplikasi menghubungi API lagi dan hanya
 * memberi notifikasi bila akun itu masih punya serangan.
 */
object Attacks {
    const val BEFORE = 3 * 3600_000L
    const val CHANNEL = "attack_v1"
    private const val KEY = "attack_checks"
    private const val COC_PACKAGE = "com.supercell.clashofclans"

    /** [kind]: "cwl" (key = warTag), "war" (key = clanTag), "raid" (key = clanTag). */
    class Check(val id: String, val tag: String, val kind: String, val key: String, val at: Long, val end: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(Store.PREFS, Context.MODE_PRIVATE)

    fun checks(ctx: Context): List<Check> {
        val arr = runCatching { JSONArray(prefs(ctx).getString(KEY, "[]")) }.getOrDefault(JSONArray())
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Check(it.optString("i"), it.optString("t"), it.optString("k"), it.optString("y"), it.optLong("a"), it.optLong("e")) }
        }
    }

    private fun save(ctx: Context, list: List<Check>) {
        val arr = JSONArray()
        list.distinctBy { it.id }.sortedBy { it.at }.forEach {
            arr.put(JSONObject().put("i", it.id).put("t", it.tag).put("k", it.kind).put("y", it.key).put("a", it.at).put("e", it.end))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).commit()
    }

    /** Akhir Raid Weekend berikutnya (Senin 07:00 UTC). */
    private fun nextRaidEnd(now: Long): Long {
        val z = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneOffset.UTC)
        var mon = z.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY)).withHour(7).withMinute(0).withSecond(0).withNano(0)
        if (!mon.isAfter(z)) mon = mon.plusWeeks(1)
        return mon.toInstant().toEpochMilli()
    }

    /**
     * Saat sinkron API (thread latar): susun ulang jadwal cek akun [tag]. [cwlWars] = war CWL clan ini pada
     * ronde yang sedang/akan berjalan (warTag → waktu berakhir).
     */
    fun plan(ctx: Context, key: String, tag: String, clanTag: String, cwlWars: Map<String, Long>, cwlActive: Boolean) {
        val now = System.currentTimeMillis()
        val keep = checks(ctx).filter { it.tag != tag && it.end > now }
        val out = ArrayList<Check>(keep)
        if (clanTag.isNotBlank()) {
            for ((warTag, end) in cwlWars) if (end - BEFORE > now - 60_000) out += Check("$tag|cwl|$warTag", tag, "cwl", warTag, end - BEFORE, end)
            if (!cwlActive) {
                runCatching { CocApi.get(key, "/clans/${CocApi.enc(clanTag)}/currentwar", allow404 = true) }.getOrNull()?.let { w ->
                    val state = w["state"].str()
                    val end = CocApi.time(w["endTime"].str())
                    if ((state == "inWar" || state == "preparation") && end - BEFORE > now - 60_000) {
                        out += Check("$tag|war|$end", tag, "war", clanTag, end - BEFORE, end)
                    }
                }
            }
            val raid = runCatching { CocApi.get(key, "/clans/${CocApi.enc(clanTag)}/capitalraidseasons?limit=1", allow404 = true) }.getOrNull()
                ?.get("items").arr()?.firstOrNull()?.obj()
            val end = if (raid != null && raid["state"].str() == "ongoing") CocApi.time(raid["endTime"].str()) else nextRaidEnd(now)
            if (end - BEFORE > now - 60_000) out += Check("$tag|raid|$end", tag, "raid", clanTag, end - BEFORE, end)
        }
        save(ctx, out)
        schedule(ctx)
    }

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 77, Intent(ctx, AttackReceiver::class.java).setAction("id.clashtimer.ATTACK_CHECK"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(ctx)
        val next = checks(ctx).minOfOrNull { it.at }
        if (next == null || Store.apiKey(ctx).isBlank()) {
            am.cancel(pi)
            return
        }
        val at = maxOf(next, System.currentTimeMillis() + 5_000)
        try {
            if (AlarmScheduler.canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Alarm berbunyi (thread latar): cek API untuk jadwal yang sudah tiba, beri tahu bila masih ada serangan. */
    fun runDue(ctx: Context) {
        val key = Store.apiKey(ctx).trim()
        val now = System.currentTimeMillis()
        val all = checks(ctx)
        val due = all.filter { it.at <= now + 60_000 }
        val rest = all.filter { it.at > now + 60_000 && it.end > now }.toMutableList()
        val tags = Store.accounts(ctx).map { it.tag }.toSet()
        if (key.isNotBlank()) for (c in due) {
            if (c.end <= now || c.tag !in tags) continue
            val left = runCatching { attacksLeft(key, c) }.getOrNull() ?: continue
            if (left > 0) notify(ctx, c, left)
        }
        // Raid berikutnya: cek lagi minggu depan; CWL hari berikutnya ikut terjadwal saat sinkron API berikutnya.
        for (c in due) if (c.kind == "raid") {
            val end = nextRaidEnd(maxOf(now, c.end) + DAY)
            rest += Check("${c.tag}|raid|$end", c.tag, "raid", c.key, end - BEFORE, end)
        }
        save(ctx, rest)
        schedule(ctx)
    }

    /** Sisa serangan akun [Check.tag]; 0 bila sudah habis atau tidak ikut, null bila tidak bisa dicek. */
    private fun attacksLeft(key: String, c: Check): Int? {
        val me = c.tag.uppercase()
        fun memberOf(list: List<Any?>?) = list.orEmpty().mapNotNull { it.obj() }.firstOrNull { it["tag"].str().uppercase() == me }
        when (c.kind) {
            "cwl" -> {
                val w = CocApi.get(key, "/clanwarleagues/wars/${CocApi.enc(c.key)}") ?: return null
                if (w["state"].str() != "inWar") return 0
                val m = memberOf(w["clan"].obj()?.get("members").arr()) ?: memberOf(w["opponent"].obj()?.get("members").arr()) ?: return 0
                return if (m["attacks"].arr().isNullOrEmpty()) 1 else 0
            }
            "war" -> {
                val w = CocApi.get(key, "/clans/${CocApi.enc(c.key)}/currentwar", allow404 = true) ?: return null
                if (w["state"].str() != "inWar") return 0
                val m = memberOf(w["clan"].obj()?.get("members").arr()) ?: return 0
                val per = (w["attacksPerMember"] as? Number)?.toInt() ?: 2
                return (per - m["attacks"].arr().orEmpty().size).coerceAtLeast(0)
            }
            "raid" -> {
                val r = CocApi.get(key, "/clans/${CocApi.enc(c.key)}/capitalraidseasons?limit=1", allow404 = true)
                    ?.get("items").arr()?.firstOrNull()?.obj() ?: return null
                if (r["state"].str() != "ongoing") return 0
                val m = memberOf(r["members"].arr()) ?: return 5
                val limit = ((m["attackLimit"] as? Number)?.toInt() ?: 5) + ((m["bonusAttackLimit"] as? Number)?.toInt() ?: 0)
                return (limit - ((m["attacks"] as? Number)?.toInt() ?: 0)).coerceAtLeast(0)
            }
        }
        return null
    }

    fun createChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Serangan war, CWL & Raid", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "3 jam sebelum berakhir, bila serangan belum dipakai"
            })
        }
    }

    private fun notify(ctx: Context, c: Check, left: Int) {
        if (!Notifier.canNotify(ctx)) return
        createChannel(ctx)
        val zone = ZoneId.systemDefault()
        val name = Store.villageName(ctx, c.tag)
        val (title, body) = when (c.kind) {
            "cwl" -> "⚔️ CWL: $name belum menyerang" to "Hari perang ini berakhir ${Fmt.time(c.end, zone)} (3 jam lagi)."
            "war" -> "⚔️ War: $name masih punya $left serangan" to "War berakhir ${Fmt.time(c.end, zone)} (3 jam lagi)."
            else -> "🏰 Raid Weekend: $name masih punya $left serangan" to "Raid berakhir ${Fmt.at(c.end, zone)} (3 jam lagi)."
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(COC_PACKAGE)
            ?: Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(ctx, c.id.hashCode(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        // Jam istirahat: masuk tanpa suara, diringkas saat jam istirahat selesai.
        val quiet = Notifier.isQuiet(ctx)
        if (quiet) {
            Notifier.createChannels(ctx)
            Store.addQuietPending(ctx, title)
        }
        val n = NotificationCompat.Builder(ctx, if (quiet) Notifier.QUIET_CHANNEL else CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$body Ketuk untuk membuka Clash of Clans."))
            .setPriority(if (quiet) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setSilent(quiet)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(c.id.hashCode(), n)
        } catch (_: SecurityException) {
        }
        if (quiet) AlarmScheduler.schedule(ctx)
    }
}

/** Alarm cek serangan: menghubungi API di thread latar. */
class AttackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                Attacks.runDue(app)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
