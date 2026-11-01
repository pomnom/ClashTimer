package id.clashtimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        processDue(context) { pending.finish() }
    }

    companion object {
        /**
         * Kirim semua notifikasi yang sudah waktunya, pasang alarm berikutnya, lalu perbarui widget.
         * [done] dipanggil setelah widget selesai diperbarui (untuk goAsync).
         */
        fun processDue(ctx: Context, done: (() -> Unit)? = null) {
            Repo.migrate(ctx)
            Notifier.createChannels(ctx)
            val now = System.currentTimeMillis()
            val slack = 2_000L // toleransi kalau alarm berbunyi sepersekian detik lebih awal
            val earlyMin = Store.earlyMinutes(ctx)
            val earlyMs = earlyMin * 60_000L
            val helpers = Store.notifyHelpers(ctx)

            val list = Store.upgrades(ctx)
            var changed = false
            // Saran berikutnya per desa & pekerja; builder ke-2 yang bebas bersamaan dapat saran ke-2, dst.
            val options = HashMap<String, List<id.clashtimer.core.Scored>>()
            val used = HashMap<String, Int>()
            for (u in list) {
                val muted = u.kind == Kind.HELPER && !helpers
                if (!u.doneNotified && now + slack >= u.finishAt) {
                    if (!muted) {
                        val lane = Notifier.laneOf(u.kind)
                        val pick = if (lane != null && u.tag.isNotBlank()) {
                            val key = "${u.tag}|$lane"
                            val opts = options.getOrPut(key) { runCatching { Repo.freeOptions(ctx, u.tag, lane) }.getOrDefault(emptyList()) }
                            val k = used[key] ?: 0
                            used[key] = k + 1
                            opts.getOrNull(k)
                        } else null
                        Notifier.done(ctx, u, pick)
                    }
                    u.doneNotified = true
                    u.earlyNotified = true
                    changed = true
                } else if (earlyMs > 0 && !u.earlyNotified && u.kind != Kind.HELPER && u.kind != Kind.REMINDER &&
                    now + slack >= u.finishAt - earlyMs
                ) {
                    val left = ((u.finishAt - now + 59_999) / 60_000).toInt().coerceAtLeast(1)
                    Notifier.early(ctx, u, left)
                    u.earlyNotified = true
                    changed = true
                }
            }
            if (changed) Store.saveUpgrades(ctx, list)
            Store.pruneOld(ctx)
            AlarmScheduler.schedule(ctx)
            Widgets.updateAll(ctx, done)
        }
    }
}

/** Pasang ulang alarm setelah HP restart, aplikasi di-update, atau jam diubah. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        runCatching { Attacks.schedule(context) }
        val pending = goAsync()
        AlarmReceiver.processDue(context) { pending.finish() }
    }
}
