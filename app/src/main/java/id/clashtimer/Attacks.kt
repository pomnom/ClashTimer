package id.clashtimer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Pengingat serangan war / CWL / Raid sudah dihapus: status serangan tidak tersedia tanpa API key (ClashKing hanya
 * memberi jadwal). Yang tersisa hanya pembersih alarm lama dan nama channel supaya notifikasinya tidak terhapus paksa.
 */
object Attacks {
    const val CHANNEL = "attack_v1"

    private fun alarmIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 77, Intent(ctx, AttackReceiver::class.java).setAction("id.clashtimer.ATTACK_CHECK"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Batalkan alarm pengingat serangan yang mungkin masih terpasang dari versi lama. */
    fun schedule(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(ctx))
    }
}

/** Penerima alarm lama: cukup membatalkannya. */
class AttackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Attacks.schedule(context)
    }
}
