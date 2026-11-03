package id.clashtimer

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import id.clashtimer.core.Lang
import java.util.Locale

/** Menerapkan bahasa & tema pilihan sejak aplikasi (atau notifikasi / widget) dimulai. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.apply(this)
    }
}

object Prefs {
    const val LANG_SYSTEM = 0
    const val LANG_ID = 1
    const val LANG_EN = 2
    const val THEME_SYSTEM = 0
    const val THEME_DARK = 1
    const val THEME_LIGHT = 2

    private fun sp(ctx: Context) = ctx.getSharedPreferences("ui", Context.MODE_PRIVATE)

    /** Bawaan: Bahasa Indonesia (seperti sebelumnya). */
    fun lang(ctx: Context) = sp(ctx).getInt("lang", LANG_ID)
    fun theme(ctx: Context) = sp(ctx).getInt("theme", THEME_SYSTEM)

    fun setLang(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("lang", v).apply()
        apply(ctx)
    }

    fun setTheme(ctx: Context, v: Int) {
        sp(ctx).edit().putInt("theme", v).apply()
        apply(ctx)
    }

    fun systemIsIndonesian(): Boolean = Locale.getDefault().language.let { it == "in" || it == "id" }

    fun apply(ctx: Context) {
        Lang.en = when (lang(ctx)) {
            LANG_EN -> true
            LANG_ID -> false
            else -> !systemIsIndonesian()
        }
        AppCompatDelegate.setDefaultNightMode(when (theme(ctx)) {
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        })
    }
}

/** Teks sesuai bahasa aktif: [id] = Bahasa Indonesia, [en] = English. */
fun tr(id: String, en: String): String = if (Lang.en) en else id
