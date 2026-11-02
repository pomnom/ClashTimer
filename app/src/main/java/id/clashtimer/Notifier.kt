package id.clashtimer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import id.clashtimer.core.Fmt
import id.clashtimer.core.Lane
import id.clashtimer.core.Scored

/** Jenis nada notifikasi yang bisa dipilih. */
enum class ToneMode(val label: String, val desc: String) {
    DEFAULT("Nada bawaan HP", "Suara notifikasi standar dengan volume notifikasi"),
    CUSTOM("Pilih nada sendiri", "Pilih dari nada yang ada di HP"),
    ALARM("Alarm keras", "Berbunyi berulang dengan volume alarm sampai dibuka, diusap, atau ditekan Matikan"),
    VIBRATE("Getar saja", "Tanpa suara"),
    SILENT("Senyap", "Hanya muncul di panel notifikasi"),
}

object Notifier {
    const val KEY_DONE = "done"
    const val KEY_EARLY = "early"
    /** Konfirmasi tanpa suara (mis. timer dipasang dari notifikasi). */
    private const val INFO_CHANNEL = "info_v1"
    /** Notifikasi yang masuk saat jam istirahat: tanpa suara, getar, maupun pop-up. */
    const val QUIET_CHANNEL = "quiet_v1"

    // ---------------------------------------------------------------- jam istirahat

    /** [t] jatuh di jam istirahat yang dibuat senyap (jam tidur, dan jam kerja bila dipilih). */
    fun isQuiet(ctx: Context, t: Long = System.currentTimeMillis()): Boolean {
        val sleepOn = Store.quietSleep(ctx)
        val workOn = Store.quietWork(ctx)
        if (!sleepOn && !workOn) return false
        return quietAt(Store.planSettings(ctx, null).schedule, t, sleepOn, workOn)
    }

    private fun quietAt(s: id.clashtimer.core.OfflineSchedule, t: Long, sleepOn: Boolean, workOn: Boolean): Boolean {
        if (!s.isOffline(t)) return false
        val sleep = id.clashtimer.core.Logins.inSleep(s, t)
        return (sleep && sleepOn) || (!sleep && workOn)
    }

    /** Kapan jam istirahat yang senyap berakhir (null = sekarang tidak senyap). */
    fun quietEnd(ctx: Context, t: Long = System.currentTimeMillis()): Long? {
        val sleepOn = Store.quietSleep(ctx)
        val workOn = Store.quietWork(ctx)
        if (!sleepOn && !workOn) return null
        val s = Store.planSettings(ctx, null).schedule
        if (!quietAt(s, t, sleepOn, workOn)) return null
        var x = (t / 60_000L + 1) * 60_000L
        repeat(36 * 60) {
            if (!quietAt(s, x, sleepOn, workOn)) return x
            x += 60_000L
        }
        return null
    }

    /** Awal jam istirahat senyap berikutnya setelah [t] (dalam 3 hari), atau null. */
    fun quietStart(ctx: Context, t: Long = System.currentTimeMillis()): Long? {
        val sleepOn = Store.quietSleep(ctx)
        val workOn = Store.quietWork(ctx)
        if (!sleepOn && !workOn) return null
        val s = Store.planSettings(ctx, null).schedule
        var x = t
        repeat(6) {
            val st = s.nextOfflineStart(x) ?: return null
            if (quietAt(s, st, sleepOn, workOn)) return st
            x = st
        }
        return null
    }

    /** Ada notifikasi "Alarm keras" yang masih berbunyi berulang. */
    fun hasRinging(ctx: Context): Boolean = ringing(ctx).isNotEmpty()

    private fun ringing(ctx: Context): List<android.service.notification.StatusBarNotification> {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return emptyList()
        return runCatching { nm.activeNotifications.filter { it.notification.flags and Notification.FLAG_INSISTENT != 0 } }
            .getOrDefault(emptyList())
    }

    /** Jam istirahat mulai: alarm keras yang masih berbunyi dihentikan, isinya tetap di panel tanpa suara. */
    fun hushRinging(ctx: Context) {
        val list = ringing(ctx)
        if (list.isEmpty()) return
        createChannels(ctx)
        val nmc = NotificationManagerCompat.from(ctx)
        for (sbn in list) {
            val n = sbn.notification
            val ex = n.extras
            val title = ex.getCharSequence(Notification.EXTRA_TITLE) ?: "Clash Timer"
            val text = ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT) ?: ""
            val b = NotificationCompat.Builder(ctx, QUIET_CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(n.contentIntent)
                .setWhen(n.`when`)
            try {
                nmc.cancel(sbn.tag, sbn.id) // menghentikan bunyi berulang
                nmc.notify(sbn.tag, sbn.id, b.build())
            } catch (_: SecurityException) {
            }
        }
    }

    /**
     * Jam istirahat selesai: satu notifikasi bersuara berisi yang masuk diam-diam. Dipanggil dari
     * AlarmReceiver; tidak melakukan apa-apa bila masih jam istirahat atau tidak ada yang tertunda.
     */
    fun flushQuiet(ctx: Context) {
        val pending = Store.quietPending(ctx)
        if (pending.isEmpty() || isQuiet(ctx)) return
        Store.clearQuietPending(ctx)
        if (!Store.quietSummary(ctx)) return
        val body = pending.takeLast(6).joinToString("\n") + if (pending.size > 6) "\n… dan ${pending.size - 6} lagi" else ""
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .setAction("id.clashtimer.OPEN_VILLAGES").putExtra(Widgets.EXTRA_TAB, "VILLAGES")
        val pi = PendingIntent.getActivity(ctx, 7337, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        post(ctx, 7337, KEY_DONE, "Selama jam istirahat · ${pending.size} notifikasi", body, pi, allowQuiet = false)
    }

    fun channelId(ctx: Context, key: String): String = "${key}_v${Store.channelVersion(ctx, key)}"

    /** Pekerja yang bebas setelah timer jenis ini selesai (null = bukan pekerja). */
    fun laneOf(kind: Kind): Lane? = when (kind) {
        Kind.BUILDER -> Lane.BUILDER
        Kind.LAB -> Lane.LAB
        Kind.PET -> Lane.PET
        Kind.BB_BUILDER -> Lane.BB_BUILDER
        Kind.STAR_LAB -> Lane.STAR_LAB
        Kind.HELPER, Kind.REMINDER -> null
    }

    private fun defaultUri(alarm: Boolean): Uri? =
        if (alarm) RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    /** Buat channel sesuai nada pilihan; hapus channel lama (termasuk dari versi sebelumnya). */
    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val wanted = listOf(KEY_DONE, KEY_EARLY).associateWith { channelId(ctx, it) }
        for (ch in nm.notificationChannels) {
            if (ch.id !in wanted.values && ch.id != INFO_CHANNEL && ch.id != QUIET_CHANNEL && ch.id != Attacks.CHANNEL) nm.deleteNotificationChannel(ch.id)
        }
        for ((key, id) in wanted) {
            if (nm.getNotificationChannel(id) == null) nm.createNotificationChannel(buildChannel(ctx, key, id))
        }
        if (nm.getNotificationChannel(QUIET_CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(QUIET_CHANNEL, "Saat jam istirahat (senyap)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Upgrade yang selesai saat jam tidur: masuk ke panel tanpa suara & getar"
                setSound(null, null)
                enableVibration(false)
            })
        }
        if (nm.getNotificationChannel(INFO_CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(INFO_CHANNEL, "Konfirmasi (tanpa suara)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Mis. timer yang dipasang dari tombol di notifikasi"
                setSound(null, null)
                enableVibration(false)
            })
        }
    }

    private fun buildChannel(ctx: Context, key: String, id: String): NotificationChannel {
        val mode = Store.toneMode(ctx, key)
        val title = if (key == KEY_DONE) "Upgrade selesai" else "Pengingat sebelum selesai"
        val importance = when (mode) {
            ToneMode.SILENT -> NotificationManager.IMPORTANCE_LOW
            else -> if (key == KEY_DONE || mode == ToneMode.ALARM) NotificationManager.IMPORTANCE_HIGH
            else NotificationManager.IMPORTANCE_DEFAULT
        }
        val ch = NotificationChannel(id, "$title · ${mode.label}", importance)
        ch.description = if (key == KEY_DONE) "Builder, lab, pet, atau helper sudah bebas" else "Beberapa menit sebelum upgrade selesai"
        val notifAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val alarmAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val custom = Store.toneUri(ctx, key)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        when (mode) {
            ToneMode.DEFAULT -> {
                ch.setSound(defaultUri(false), notifAttrs)
                ch.enableVibration(true)
            }
            ToneMode.CUSTOM -> {
                ch.setSound(custom ?: defaultUri(false), notifAttrs)
                ch.enableVibration(true)
            }
            ToneMode.ALARM -> {
                ch.setSound(custom ?: defaultUri(true), alarmAttrs)
                ch.enableVibration(true)
                ch.vibrationPattern = longArrayOf(0, 800, 400, 800, 400, 800)
            }
            ToneMode.VIBRATE -> {
                ch.setSound(null, null)
                ch.enableVibration(true)
            }
            ToneMode.SILENT -> {
                ch.setSound(null, null)
                ch.enableVibration(false)
            }
        }
        return ch
    }

    /** Simpan nada baru: channel lama dihapus, channel baru dibuat dengan ID baru. */
    fun changeTone(ctx: Context, key: String, mode: ToneMode, uri: String?) {
        Store.setTone(ctx, key, mode, uri)
        createChannels(ctx)
    }

    fun toneTitle(ctx: Context, key: String): String {
        val mode = Store.toneMode(ctx, key)
        val uri = Store.toneUri(ctx, key)
        if ((mode == ToneMode.CUSTOM || mode == ToneMode.ALARM) && uri != null) {
            val t = runCatching { RingtoneManager.getRingtone(ctx, Uri.parse(uri))?.getTitle(ctx) }.getOrNull()
            if (!t.isNullOrBlank()) return "${mode.label} · $t"
        }
        return mode.label
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    /** Buka aplikasi di desa [tag] (Beranda, sisi Home/BB). */
    private fun openApp(ctx: Context, tag: String? = null, bb: Boolean = false, req: Int = 0): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (!tag.isNullOrBlank()) {
            i.setAction("id.clashtimer.OPEN_HOME").putExtra(Widgets.EXTRA_TAG, tag).putExtra(Widgets.EXTRA_BB, bb)
                .putExtra(Widgets.EXTRA_TAB, "HOME")
        }
        return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun stopIntent(ctx: Context, id: Int): PendingIntent = PendingIntent.getBroadcast(
        ctx, id,
        Intent(ctx, StopAlarmReceiver::class.java).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * Notifikasi selesai. [pick] = saran untuk pekerja yang baru bebas: ditampilkan di isi
     * notifikasi dengan tombol "Sudah dimulai" (timer langsung dipasang tanpa membuka aplikasi).
     */
    fun done(ctx: Context, u: Upgrade, pick: Scored? = null) {
        val id = u.id.hashCode()
        if (u.kind == Kind.REMINDER) {
            if (u.id.contains("|session|")) {
                // Satu notifikasi per sesi login: siapa yang bebas + saran yang dimulai.
                post(ctx, id, KEY_DONE, "Waktunya login · ${Store.villageName(ctx, u.tag)}", u.note, openApp(ctx, u.tag, false, id))
                return
            }
            val where = if (u.tag.isNotBlank()) " · ${Store.villageName(ctx, u.tag)}" else ""
            post(ctx, id, KEY_DONE, u.title(), u.note + where, openApp(ctx, u.tag.ifBlank { null }, u.id.contains("|clock|"), id))
            return
        }
        val village = Store.villageName(ctx, u.tag)
        var body = when (u.kind) {
            Kind.HELPER -> u.note.ifBlank { "Helper sudah bisa ditugaskan lagi" }
            Kind.LAB, Kind.STAR_LAB -> "${u.kind.label} sekarang kosong"
            Kind.PET -> "Pet House sekarang kosong"
            Kind.BB_BUILDER -> "Master Builder sekarang bebas"
            else -> "1 builder sekarang bebas"
        } + " · $village"
        if (pick != null) {
            val c = pick.c
            val cost = if (pick.cost > 0) " · ${Fmt.money(pick.cost)} ${Fmt.resource(c.item.resource)}" else ""
            body += "\n💡 Saran: ${c.title} · ${Fmt.durSec(pick.duration)}$cost"
        }
        if (u.helped && u.kind != Kind.HELPER) body += "\nWaktu ini perkiraan dengan bantuan helper — cek di game."
        val actions = ArrayList<Pair<String, PendingIntent>>()
        if (pick != null) {
            val lane = laneOf(u.kind)
            if (lane != null) {
                val i = Intent(ctx, NotifyActionReceiver::class.java)
                    .setAction(NotifyActionReceiver.ACTION_START)
                    .putExtra("notif", id).putExtra("tag", u.tag).putExtra("lane", lane.name)
                    .putExtra("item", pick.c.item.id).putExtra("from", pick.c.fromLevel).putExtra("gear", pick.c.gear)
                    .putExtra("title", pick.c.title)
                actions += "Sudah dimulai" to PendingIntent.getBroadcast(
                    ctx, id xor 0x1F1F, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            }
        }
        // Sesi login yang diingatkan (lonceng) pada saat yang sama sudah berbunyi: yang ini cukup masuk tanpa suara.
        val covered = coveredBySession(ctx, u)
        post(ctx, id, KEY_DONE, "${u.kind.emoji} ${u.title()}", body, openApp(ctx, u.tag, u.kind.builderBase, id), actions, silent = covered)
    }

    /** Upgrade [u] selesai tepat di sesi login yang diingatkan (lonceng): pengingat sesi yang berbunyi. */
    fun coveredBySession(ctx: Context, u: Upgrade): Boolean =
        u.tag.isNotBlank() && u.kind != Kind.REMINDER &&
            Store.sessionReminders(ctx, u.tag).any { u.finishAt in (it.at - 60_000L)..(it.at + 5 * 60_000L) }

    /** Ganti notifikasi [id] dengan konfirmasi tanpa suara. */
    fun info(ctx: Context, id: Int, title: String, body: String, tag: String?, bb: Boolean) {
        if (!canNotify(ctx)) return
        createChannels(ctx)
        val n = NotificationCompat.Builder(ctx, INFO_CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx, tag, bb, id))
            .setWhen(System.currentTimeMillis())
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }

    fun early(ctx: Context, u: Upgrade, minutes: Int) {
        post(
            ctx, u.id.hashCode() xor 0x5A5A, KEY_EARLY,
            "⏳ ${u.title()}",
            "Selesai dalam $minutes menit · ${Store.villageName(ctx, u.tag)}",
        )
    }

    fun test(ctx: Context, key: String = KEY_DONE) {
        val mode = Store.toneMode(ctx, key)
        val body = when (mode) {
            ToneMode.ALARM -> "Alarm keras: berbunyi sampai notifikasi ini dibuka, diusap, atau ditekan Matikan."
            ToneMode.SILENT -> "Mode senyap: notifikasi hanya muncul di panel."
            else -> "Kalau ini muncul, notifikasi sudah berfungsi."
        }
        post(ctx, 4242 + key.length, key, "🔔 Tes: ${mode.label}", body, allowQuiet = false)
    }

    private fun post(
        ctx: Context, id: Int, key: String, title: String, body: String,
        content: PendingIntent = openApp(ctx),
        actions: List<Pair<String, PendingIntent>> = emptyList(),
        allowQuiet: Boolean = true,
        /** Tanpa suara (mis. sudah tercakup pengingat sesi login), tanpa ikut ringkasan jam istirahat. */
        silent: Boolean = false,
    ) {
        if (!canNotify(ctx)) return
        createChannels(ctx)
        val quiet = allowQuiet && isQuiet(ctx)
        if (quiet || silent) {
            // Jam istirahat: masuk ke panel tanpa bunyi; diringkas sekali saat jam istirahat selesai.
            val n = NotificationCompat.Builder(ctx, QUIET_CHANNEL)
                .setSmallIcon(R.drawable.ic_notif)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .setAutoCancel(true)
                .setContentIntent(content)
                .setWhen(System.currentTimeMillis())
            for ((label, pi) in actions) n.addAction(R.drawable.ic_notif, label, pi)
            if (quiet && !silent) Store.addQuietPending(ctx, title)
            try {
                NotificationManagerCompat.from(ctx).notify(id, n.build())
            } catch (_: SecurityException) {
            }
            return
        }
        val mode = Store.toneMode(ctx, key)
        val b = NotificationCompat.Builder(ctx, channelId(ctx, key))
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(
                if (mode == ToneMode.SILENT) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH
            )
            .setCategory(if (mode == ToneMode.ALARM) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(content)
            .setWhen(System.currentTimeMillis())
        for ((label, pi) in actions) b.addAction(R.drawable.ic_notif, label, pi)
        if (mode == ToneMode.ALARM) b.addAction(R.drawable.ic_notif, "Matikan", stopIntent(ctx, id))
        val n = b.build()
        // Alarm keras: suara diulang terus sampai notifikasi dibuka / diusap / dimatikan.
        if (mode == ToneMode.ALARM) n.flags = n.flags or Notification.FLAG_INSISTENT
        try {
            NotificationManagerCompat.from(ctx).notify(id, n)
        } catch (_: SecurityException) {
        }
    }
}

/** Tombol "Matikan" pada notifikasi Alarm keras. */
class StopAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra("id", 0)
        NotificationManagerCompat.from(context).cancel(id)
    }
}

/** Tombol "Sudah dimulai" di notifikasi: pasang timer saran tanpa membuka aplikasi. */
class NotifyActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_START) return
        val notif = intent.getIntExtra("notif", 0)
        val tag = intent.getStringExtra("tag") ?: return
        val lane = runCatching { Lane.valueOf(intent.getStringExtra("lane") ?: "") }.getOrNull() ?: return
        val item = intent.getIntExtra("item", 0)
        val from = intent.getIntExtra("from", 0)
        val gear = intent.getBooleanExtra("gear", false)
        val title = intent.getStringExtra("title") ?: "upgrade"
        // Hentikan bunyi (mis. Alarm keras) segera, lalu pasang timer di thread latar.
        NotificationManagerCompat.from(context).cancel(notif)
        val pending = goAsync()
        val app = context.applicationContext
        Thread {
            try {
                val u = runCatching { Repo.startById(app, tag, lane, item, from, gear) }.getOrNull()
                val zone = java.time.ZoneId.systemDefault()
                if (u != null) {
                    Notifier.info(app, notif, "✅ Timer dipasang: ${u.title()}",
                        "Selesai ${Fmt.at(u.finishAt, zone)} · ${Store.villageName(app, tag)}", tag, lane.bb)
                } else {
                    Notifier.info(app, notif, "Saran sudah berubah",
                        "$title tidak bisa dipasang otomatis (mungkin sudah dimulai). Buka aplikasi untuk memilih.", tag, lane.bb)
                }
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_START = "id.clashtimer.ACTION_START_PICK"
    }
}
