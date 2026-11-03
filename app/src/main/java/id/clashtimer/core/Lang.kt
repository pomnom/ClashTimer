package id.clashtimer.core

import java.util.Locale

/** Bahasa tampilan: Bahasa Indonesia (bawaan) atau English. Diatur dari Pengaturan → Umum → Bahasa. */
object Lang {
    @Volatile
    var en: Boolean = false

    val locale: Locale get() = if (en) Locale.ENGLISH else Locale.forLanguageTag("id-ID")
}

/** Teks sesuai bahasa aktif: [id] = Bahasa Indonesia, [en] = English. */
fun tr(id: String, en: String): String = if (Lang.en) en else id
