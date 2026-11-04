package id.clashtimer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * HP yang menutup aplikasi di latar dengan ketat (Xiaomi / Redmi / POCO dengan HyperOS): tanpa izin
 * "Mulai otomatis", alarm dan pembaruan widget tidak bisa menjalankan aplikasi yang sudah ditutup,
 * sehingga widget baru diperbarui saat aplikasi dibuka.
 */
object Background {
    fun isXiaomi(): Boolean {
        val s = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return listOf("xiaomi", "redmi", "poco").any { it in s }
    }

    /** Halaman Info aplikasi — di HyperOS berisi "Mulai otomatis" dan "Penghemat baterai". */
    fun openAppInfo(ctx: Context): Boolean = try {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: Exception) {
        false
    }

    val TIP_TITLE: String get() = tr("📱 HyperOS: izinkan berjalan di latar", "📱 HyperOS: allow running in background")
    val TIP_BODY: String get() = tr(
        "Supaya widget & notifikasi tetap diperbarui walau aplikasi ditutup: buka Info aplikasi → " +
            "aktifkan Mulai otomatis (Autostart), lalu Penghemat baterai → Tidak ada batasan.",
        "To keep widgets & notifications updated while the app is closed: open App info → " +
            "turn on Autostart, then Battery saver → No restrictions.",
    )
}
