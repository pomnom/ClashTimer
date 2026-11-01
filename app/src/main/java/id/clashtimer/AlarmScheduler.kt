package id.clashtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Hanya satu alarm yang dipasang: untuk kejadian berikutnya yang paling dekat.
 * Saat alarm berbunyi, AlarmReceiver mengirim semua notifikasi yang sudah
 * waktunya, lalu memasang alarm untuk kejadian berikutnya.
 *
 * setAlarmClock() dipakai karena tidak ditunda oleh mode Doze / hemat daya
 * dan tidak dibatasi frekuensinya — ini yang membuat notifikasi tepat waktu,
 * berbeda dengan notifikasi browser.
 */
object AlarmScheduler {

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1,
        Intent(ctx, AlarmReceiver::class.java).setAction("id.clashtimer.FIRE"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun canExact(ctx: Context): Boolean {
        val am = ctx.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
    }

    /** Waktu (ms) kejadian berikutnya, atau null bila tidak ada. */
    fun nextTrigger(ctx: Context): Long? {
        val earlyMs = Store.earlyMinutes(ctx) * 60_000L
        val helpers = Store.notifyHelpers(ctx)
        var next: Long? = null
        for (u in Store.upgrades(ctx)) {
            if (u.kind == Kind.HELPER && !helpers) continue
            if (!u.doneNotified) next = minOf(next ?: Long.MAX_VALUE, u.finishAt)
            if (earlyMs > 0 && !u.earlyNotified && u.kind != Kind.HELPER && u.kind != Kind.REMINDER) {
                next = minOf(next ?: Long.MAX_VALUE, u.finishAt - earlyMs)
            }
        }
        // Ringkasan notifikasi yang masuk diam-diam: dikirim saat jam istirahat selesai.
        val now = System.currentTimeMillis()
        if (Store.quietPending(ctx).isNotEmpty()) {
            val end = Notifier.quietEnd(ctx) ?: if (Notifier.isQuiet(ctx)) now + 6 * 3_600_000L else now
            next = minOf(next ?: Long.MAX_VALUE, end)
        }
        // Alarm keras masih berbunyi: hentikan begitu jam istirahat mulai.
        if (Notifier.hasRinging(ctx)) Notifier.quietStart(ctx)?.let { next = minOf(next ?: Long.MAX_VALUE, it) }
        return next
    }

    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = alarmIntent(ctx)
        val next = nextTrigger(ctx)
        if (next == null) {
            am.cancel(pi)
            return
        }
        val at = maxOf(next, System.currentTimeMillis() + 1000)

        if (!canExact(ctx)) {
            // Tanpa izin alarm tepat, ini cadangan terbaik (bisa telat beberapa menit).
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            return
        }
        try {
            if (Store.strongAlarm(ctx)) {
                val show = PendingIntent.getActivity(
                    ctx, 2, Intent(ctx, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
