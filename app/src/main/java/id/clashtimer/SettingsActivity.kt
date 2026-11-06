package id.clashtimer

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.Fmt
import java.time.DayOfWeek

class SettingsActivity : AppCompatActivity() {

    private lateinit var box: LinearLayout

    /** Nada yang sedang dipilih lewat pemilih nada sistem. */
    private var pendingKey: String? = null
    private var pendingMode: ToneMode? = null

    private val ringtonePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val key = pendingKey
        val mode = pendingMode
        pendingKey = null
        pendingMode = null
        if (key == null || mode == null || result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
        }
        Notifier.changeTone(this, key, mode, uri?.toString())
        render()
        Toast.makeText(this, tr("Nada disimpan · ", "Sound saved · ") + Notifier.toneTitle(this, key), Toast.LENGTH_SHORT).show()
    }

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(Backup.json(this).toString(1).toByteArray(Charsets.UTF_8)) }
        }.onSuccess { Toast.makeText(this, tr("File cadangan disimpan", "Backup file saved"), Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, tr("Gagal menyimpan: ", "Could not save: ") + it.message, Toast.LENGTH_LONG).show() }
    }

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        confirmRestore(text)
    }

    /** Halaman yang tampil: "main" = daftar kategori, selain itu satu kategori. */
    private var page = "main"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        page = savedInstanceState?.getString("page") ?: "main"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        Ui.init(this)
        box = Ui.vbox(this).apply { setPadding(Ui.dp(this@SettingsActivity, 16), 0, Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 32)) }
        scroll = ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(box) }
        setContentView(scroll)
        render()
    }

    private lateinit var scroll: ScrollView

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("page", page)
    }

    override fun onSupportNavigateUp(): Boolean {
        back()
        return true
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (page != "main") back() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun back() {
        if (page == "main") finish()
        else {
            page = if (page == "lang") "general" else "main"
            render()
            scroll.scrollTo(0, 0)
        }
    }

    private fun open(p: String) {
        page = p
        render()
        scroll.scrollTo(0, 0)
    }

    private fun note(text: String) {
        box.addView(Ui.muted(this, text).apply {
            setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4))
        })
    }

    /** Judul kecil di dalam satu bagian; [help] = tombol "?" berisi penjelasannya. */
    private fun sub(title: String, subtitle: String? = null, help: Help.Topic? = null) {
        val col = Ui.vbox(this).apply { setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 6)) }
        val head = Ui.hbox(this)
        head.addView(Ui.text(this, title.uppercase(id.clashtimer.core.Fmt.locale), Ui.BODY_S).apply {
            textSize = 12f
            letterSpacing = 0.06f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Ui.TEXT2)
        }, Ui.weight1())
        if (help != null) head.addView(Help.button(this, help))
        col.addView(head)
        if (subtitle != null) col.addView(Ui.muted(this, subtitle).apply { textSize = 12f })
        box.addView(col)
        group = null
    }

    /** Panel kelompok: baris berurutan dalam satu bagian digabung dalam satu panel bergaris pemisah. */
    private var group: LinearLayout? = null

    private fun groupPanel(): LinearLayout {
        val g = group
        if (g != null && box.childCount > 0 && box.getChildAt(box.childCount - 1) === g) {
            g.addView(android.view.View(this).apply { setBackgroundColor(Ui.LINE) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)))
            return g
        }
        val p = Ui.panel(this, padH = 14, padV = 0)
        box.addView(p, Ui.matchWrap(8, this))
        group = p
        return p
    }

    private fun pageTitle(p: String): String = when (p) {
        "general" -> tr("Umum", "General")
        "lang" -> tr("Bahasa aplikasi", "App language")
        "rest" -> tr("Jam istirahat", "Rest hours")
        "notify" -> tr("Notifikasi & nada", "Notifications & sounds")
        "events" -> tr("Event", "Events")
        "data" -> tr("Data & akun", "Data & account")
        "widget" -> tr("Widget & berjalan di latar", "Widget & background")
        "about" -> tr("Tentang", "About")
        else -> tr("Pengaturan", "Settings")
    }

    private fun render() {
        box.removeAllViews()
        group = null
        title = pageTitle(page)
        when (page) {
            "general" -> generalPage()
            "lang" -> langPage()
            "rest" -> restPage()
            "notify" -> notifyPage()
            "events" -> eventsPage()
            "data" -> { apiSection(); backupSection() }
            "widget" -> widgetPage()
            "about" -> { updateSection(); creditSection() }
            else -> mainPage()
        }
    }

    // ---------------------------------------------------------------- halaman utama

    private fun langName(): String = when (Prefs.lang(this)) {
        Prefs.LANG_EN -> "English"
        Prefs.LANG_ID -> "Bahasa Indonesia"
        else -> tr("Ikuti sistem", "Follow system")
    }

    private fun themeName(): String = when (Prefs.theme(this)) {
        Prefs.THEME_DARK -> tr("gelap", "dark")
        Prefs.THEME_LIGHT -> tr("terang", "light")
        else -> tr("ikut sistem", "system")
    }

    private fun mainPage() {
        val s = Store.planSettings(this, null).schedule
        sub(tr("Untuk semua desa", "For all villages"))
        category(R.drawable.ic_settings, Ui.TEXT2, Ui.SURFACE2, tr("Umum", "General"),
            "${langName()} · ${tr("tema", "theme")} ${themeName()}") { open("general") }
        val rest = if (s.sleep.enabled) tr("Tidur", "Sleep") + " ${Fmt.clock(s.sleep.startMin)}–${Fmt.clock(s.sleep.endMin)}" else tr("Jam tidur tidak dihitung", "Sleep hours not used")
        val quiet = if (s.sleep.enabled && Store.quietSleep(this)) tr(" · senyap aktif", " · quiet on") else ""
        category(R.drawable.ic_moon, Ui.TEXT2, Ui.SURFACE2, tr("Jam istirahat", "Rest hours"), rest + quiet) { open("rest") }
        val early = Store.earlyMinutes(this)
        category(R.drawable.ic_bell, Ui.BUSY, Ui.BUSY_BG, tr("Notifikasi & nada", "Notifications & sounds"),
            Notifier.toneTitle(this, Notifier.KEY_DONE) + " · " + if (early == 0) tr("tanpa pengingat", "no reminder") else tr("pengingat $early menit", "$early min reminder")) { open("notify") }
        val now = System.currentTimeMillis()
        val ev = Store.planSettings(this, null).events.filter { it.enabled && it.end > now }
        category(R.drawable.ic_star, Ui.LAB, Ui.LAB_BG, tr("Event", "Events"),
            if (ev.isEmpty()) tr("Tidak ada event aktif", "No active events") else ev.joinToString(", ") { it.name }) { open("events") }
        group = null
        box.addView(android.view.View(this), LinearLayout.LayoutParams(1, Ui.dp(this, 4)))
        category(R.drawable.ic_import, Ui.LAB, Ui.LAB_BG, tr("Data & akun", "Data & account"), tr("Status War/CWL · pindah data & cadangan", "War/CWL status · move data & backup")) { open("data") }
        val bgOk = !Background.isXiaomi() && (getSystemService(android.os.PowerManager::class.java)?.isIgnoringBatteryOptimizations(packageName) == true)
        category(R.drawable.ic_nav_dashboard, if (bgOk) Ui.TEXT2 else Ui.IDLE, Ui.SURFACE2, tr("Widget & berjalan di latar", "Widget & background"),
            if (bgOk) tr("Berjalan tanpa batasan baterai", "Running without battery limits") else tr("Periksa izin berjalan di latar", "Check background permissions")) { open("widget") }
        val avail = Updater.available(this)
        category(R.drawable.ic_help, if (avail != null) Ui.FREE_TEXT else Ui.TEXT2, Ui.SURFACE2, tr("Tentang", "About"),
            tr("Build", "Build") + " ${Updater.installedBuild(this)} · " + if (avail != null) tr("build ${avail.build} tersedia", "build ${avail.build} available") else tr("versi terbaru", "up to date")) { open("about") }
        group = null
        note(tr("Strategi, Gold Pass, CWL & collector diatur per desa: ketuk ringkasan strategi di Dashboard (atau ikon opsi di atas).",
            "Strategy, Gold Pass, CWL & collectors are set per village: tap the strategy summary on the Dashboard (or the options icon at the top)."))
    }

    /** Baris kategori: ikon berwarna, judul, ringkasan isinya, panah. */
    private fun category(icon: Int, color: Int, bg: Int, title: String, summary: String, onClick: () -> Unit) {
        val line = Ui.hbox(this).apply {
            minimumHeight = Ui.dp(this@SettingsActivity, 64)
            setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, Ui.dp(this@SettingsActivity, 10))
            isClickable = true
            setOnClickListener { onClick() }
            Ui.ripple(this)
        }
        val tile = android.widget.FrameLayout(this).apply { background = Ui.rounded(bg, Ui.dp(this@SettingsActivity, 12).toFloat()) }
        tile.addView(Ui.icon(this, icon, 22, color), android.widget.FrameLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22), android.view.Gravity.CENTER))
        line.addView(tile, LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40)).apply { marginEnd = Ui.dp(this@SettingsActivity, 14) })
        val col = Ui.vbox(this)
        col.addView(Ui.text(this, title, Ui.BODY_M).apply { textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(Ui.TEXT) })
        col.addView(Ui.muted(this, summary).apply { textSize = 13f; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        line.addView(col, Ui.weight1())
        line.addView(Ui.icon(this, R.drawable.ic_chevron_right, 18, Ui.TEXT2))
        groupPanel().addView(line, Ui.matchWrap())
    }

    // ---------------------------------------------------------------- Umum & bahasa

    private fun generalPage() {
        sub(tr("Bahasa", "Language"))
        row(tr("Bahasa aplikasi", "App language"), langName()) { open("lang") }
        sub(tr("Tema", "Theme"))
        val seg = Ui.segmentedWide(this, listOf(tr("Sistem", "System"), tr("Gelap", "Dark"), tr("Terang", "Light")),
            when (Prefs.theme(this)) { Prefs.THEME_DARK -> 1; Prefs.THEME_LIGHT -> 2; else -> 0 }) {
            Prefs.setTheme(this, when (it) { 1 -> Prefs.THEME_DARK; 2 -> Prefs.THEME_LIGHT; else -> Prefs.THEME_SYSTEM })
            Widgets.updateAll(this)
            recreate()
        }
        box.addView(seg, Ui.matchWrap(4, this))
        note(tr("Sistem = gelap saat HP memakai mode gelap.", "System = dark when the phone uses dark mode."))
        sub(tr("🖼️ Ikon", "🖼️ Icons"))
        switchRow(tr("Tampilkan ikon bangunan & hero", "Show building & hero icons"), Store.iconsOn(this)) { Store.setIconsOn(this, it) }
        val mb = Icons.cacheSize(this) / 1024.0 / 1024.0
        box.addView(Ui.outlined(this, String.format(java.util.Locale.ROOT, tr("Hapus cache ikon (%.1f MB)", "Clear icon cache (%.1f MB)"), mb)) {
            Icons.clearCache(this)
            render()
        }.apply { isAllCaps = false }, Ui.matchWrap())
        note(tr("Ikon diunduh sekali dari ClashKing Assets (assets.clashk.ing — terima kasih!) lalu disimpan di HP. " +
            "Gambar © Supercell. Clash Timer adalah fan content tidak resmi, tidak berafiliasi dengan dan tidak didukung oleh Supercell.",
            "Icons are downloaded once from ClashKing Assets (assets.clashk.ing — thank you!) and stored on the phone. " +
                "Images © Supercell. Clash Timer is unofficial fan content, not affiliated with or endorsed by Supercell."))
    }

    private fun langPage() {
        val cur = Prefs.lang(this)
        val sys = tr("Sekarang: ", "Now: ") + if (Prefs.systemIsIndonesian()) "Bahasa Indonesia" else "English"
        val opts = listOf(
            Triple(Prefs.LANG_SYSTEM, tr("Ikuti sistem", "Follow system"), sys),
            Triple(Prefs.LANG_ID, "Bahasa Indonesia", "Indonesian"),
            Triple(Prefs.LANG_EN, "English", "Bahasa Inggris"),
        )
        box.addView(android.view.View(this), LinearLayout.LayoutParams(1, Ui.dp(this, 8)))
        for ((v, label, subLabel) in opts) {
            val line = Ui.hbox(this).apply {
                minimumHeight = Ui.dp(this@SettingsActivity, 60)
                isClickable = true
                setOnClickListener {
                    if (v == cur) return@setOnClickListener
                    Prefs.setLang(this@SettingsActivity, v)
                    Widgets.updateAll(this@SettingsActivity)
                    AlarmScheduler.schedule(this@SettingsActivity)
                    render()
                }
                Ui.ripple(this)
            }
            val on = v == cur
            line.addView(android.widget.FrameLayout(this).apply {
                background = Ui.rounded(android.graphics.Color.TRANSPARENT, Ui.dp(this@SettingsActivity, 11).toFloat(), if (on) Ui.FREE else Ui.OUTLINE, Ui.dp(this@SettingsActivity, 2))
                if (on) addView(android.view.View(this@SettingsActivity).apply { background = Ui.rounded(Ui.FREE, Ui.dp(this@SettingsActivity, 5).toFloat()) },
                    android.widget.FrameLayout.LayoutParams(Ui.dp(this@SettingsActivity, 10), Ui.dp(this@SettingsActivity, 10), android.view.Gravity.CENTER))
            }, LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22)).apply { marginEnd = Ui.dp(this@SettingsActivity, 14) })
            val col = Ui.vbox(this)
            col.addView(Ui.text(this, label, Ui.BODY_M).apply { textSize = 15f; setTextColor(Ui.TEXT) })
            col.addView(Ui.muted(this, subLabel).apply { textSize = 12f })
            line.addView(col, Ui.weight1())
            groupPanel().addView(line, Ui.matchWrap())
        }
        note(tr("Nama bangunan, troop & hero tetap seperti di game. Teks bantuan (?) sebagian masih berbahasa Indonesia.",
            "Building, troop & hero names stay as in the game. Some help texts (?) are still in Indonesian."))
    }

    // ---------------------------------------------------------------- jam istirahat

    private fun restPage() {
        val s = Store.planSettings(this, null).schedule
        sub(tr("😴 Jam tidur & kerja", "😴 Sleep & work hours"), tr("Saran memilih upgrade yang selesai saat Anda bisa membuka game",
            "Suggestions pick upgrades that finish when you can open the game"))
        switchRow(tr("Hitung jam tidur", "Use sleep hours"), s.sleep.enabled) { Store.putPlan(this, "sleep_on", it); render() }
        if (s.sleep.enabled) {
            row(tr("Tidur", "Sleep"), "${Fmt.clock(s.sleep.startMin)} – ${Fmt.clock(s.sleep.endMin)}") {
                pickRange(tr("Jam tidur", "Sleep hours"), "sleep_start", "sleep_end", s.sleep.startMin, s.sleep.endMin)
            }
        }
        switchRow(tr("Hitung jam kerja / sekolah", "Use work / school hours"), s.work.enabled) { Store.putPlan(this, "work_on", it); render() }
        if (s.work.enabled) {
            row(tr("Jam kerja", "Work hours"), "${Fmt.clock(s.work.startMin)} – ${Fmt.clock(s.work.endMin)}") {
                pickRange(tr("Jam kerja", "Work hours"), "work_start", "work_end", s.work.startMin, s.work.endMin)
            }
            val days = DayOfWeek.entries.filter { it in s.work.days }.joinToString(", ") { dayName(it) }
            row(tr("Hari kerja", "Work days"), days.ifBlank { tr("tidak ada", "none") }) { pickDays() }
        }
        if (s.sleep.enabled || s.work.enabled) {
            sub(tr("🔕 Senyap saat jam istirahat", "🔕 Quiet during rest hours"),
                tr("Alarm & notifikasi tidak berbunyi; tetap masuk ke panel tanpa suara & getar", "Alarms & notifications stay silent; they still arrive without sound or vibration"), Help.Topic.NOTIFY)
            if (s.sleep.enabled) switchRow(tr("Senyap saat jam tidur", "Quiet while sleeping"), Store.quietSleep(this)) {
                Store.setQuietSleep(this, it); AlarmScheduler.schedule(this); render()
            }
            if (s.work.enabled) switchRow(tr("Senyap saat jam kerja / sekolah", "Quiet during work / school"), Store.quietWork(this)) {
                Store.setQuietWork(this, it); AlarmScheduler.schedule(this); render()
            }
            if ((s.sleep.enabled && Store.quietSleep(this)) || (s.work.enabled && Store.quietWork(this))) {
                switchRow(tr("Ringkasan saat jam istirahat selesai", "Summary when rest hours end"), Store.quietSummary(this)) {
                    Store.setQuietSummary(this, it); AlarmScheduler.schedule(this)
                }
            }
        }
    }

    // ---------------------------------------------------------------- notifikasi & nada

    private fun notifyPage() {
        sub(tr("🎵 Nada notifikasi", "🎵 Notification sounds"), help = Help.Topic.NOTIFY)
        row(tr("Upgrade selesai & helper", "Upgrade done & helpers"), Notifier.toneTitle(this, Notifier.KEY_DONE)) { pickTone(Notifier.KEY_DONE) }
        row(tr("Pengingat sebelum selesai", "Reminder before done"), Notifier.toneTitle(this, Notifier.KEY_EARLY)) { pickTone(Notifier.KEY_EARLY) }
        val tests = Ui.hbox(this)
        tests.addView(Ui.outlined(this, tr("Tes nada selesai", "Test done sound")) { testTone(Notifier.KEY_DONE) }.apply { isAllCaps = false }, Ui.weight1())
        tests.addView(Ui.outlined(this, tr("Tes pengingat", "Test reminder")) { testTone(Notifier.KEY_EARLY) }.apply { isAllCaps = false },
            Ui.weight1().apply { marginStart = Ui.dp(this@SettingsActivity, 8) })
        box.addView(tests)
        box.addView(Ui.outlined(this, tr("Pengaturan notifikasi Android", "Android notification settings")) {
            runCatching {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }.apply { isAllCaps = false }, Ui.matchWrap())

        sub(tr("🔔 Notifikasi", "🔔 Notifications"), help = Help.Topic.NOTIFY)
        val early = Store.earlyMinutes(this)
        row(tr("Pengingat sebelum selesai", "Reminder before done"), if (early == 0) tr("Tidak ada", "None") else tr("$early menit sebelumnya", "$early min before")) { pickEarly() }
        switchRow(tr("Notifikasi helper", "Helper notifications"), Store.notifyHelpers(this)) {
            Store.setNotifyHelpers(this, it)
            AlarmScheduler.schedule(this)
        }
        switchRow(tr("Mode alarm kuat (paling tepat waktu)", "Strong alarm mode (most punctual)"), Store.strongAlarm(this)) {
            Store.setStrongAlarm(this, it)
            AlarmScheduler.schedule(this)
        }
    }

    // ---------------------------------------------------------------- event, widget

    private fun eventsPage() {
        sub(tr("🎉 Event", "🎉 Events"), tr("Hammer Jam, Builder Base Bonanza, dll. Berlaku untuk semua desa.", "Hammer Jam, Builder Base Bonanza, etc. Applies to all villages."), Help.Topic.EVENTS)
        EventsUi.renderList(this, box) { render() }
        box.addView(Ui.outlined(this, tr("+ Tambah event", "+ Add event")) { EventsUi.addPreset(this) { render() } }.apply { isAllCaps = false }, Ui.matchWrap())
    }

    private fun widgetPage() {
        sub(tr("📱 Widget & berjalan di latar", "📱 Widget & background"), help = Help.Topic.WIDGET)
        note(if (Background.isXiaomi()) Background.TIP_BODY else tr(
            "Bila widget atau notifikasi sering telat: izinkan aplikasi berjalan tanpa batasan baterai (dan Autostart bila HP punya).",
            "If widgets or notifications are often late: let the app run without battery restrictions (and enable Autostart if your phone has it)."))
        box.addView(Ui.outlined(this, tr("Buka info aplikasi", "Open app info")) { Background.openAppInfo(this) }.apply { isAllCaps = false }, Ui.matchWrap())
        note(tr("Widget diperbarui saat data berubah, saat upgrade selesai, dan paling lambat tiap 30 menit.",
            "The widget refreshes when data changes, when an upgrade finishes, and at least every 30 minutes."))
    }

    // ---------------------------------------------------------------- update aplikasi

    private fun updateSection() {
        sub(tr("🔄 Update aplikasi", "🔄 App updates"), "github.com/${Updater.REPO}", Help.Topic.UPDATE)
        val zone = java.time.ZoneId.systemDefault()
        val installed = Updater.installedBuild(this)
        val latest = Updater.latest(this)
        val avail = Updater.available(this)
        val status = when {
            avail != null -> tr("build ${avail.build} tersedia", "build ${avail.build} available") + " (${Updater.mb(avail.size)})"
            latest != null -> tr("sudah versi terbaru", "already up to date")
            else -> tr("belum dicek", "not checked yet")
        }
        val checked = Updater.lastChecked(this).takeIf { it > 0 }?.let { tr(" · dicek ", " · checked ") + Fmt.at(it, zone) } ?: ""
        val err = Updater.lastError(this)?.let { tr("\nCek terakhir gagal: ", "\nLast check failed: ") + it } ?: ""
        note(tr("Terpasang: build $installed (versi ${Updater.installedName(this)})", "Installed: build $installed (version ${Updater.installedName(this)})") + " · $status$checked$err")
        if (avail != null) {
            box.addView(Ui.filled(this, tr("⬆️ Unduh & pasang build ${avail.build}", "⬆️ Download & install build ${avail.build}")) { UpdateUi.start(this, avail) }
                .apply { isAllCaps = false }, Ui.matchWrap())
        }
        box.addView(Ui.outlined(this, tr("Cek update sekarang", "Check for updates now")) { UpdateUi.checkNow(this) { render() } }
            .apply { isAllCaps = false }, Ui.matchWrap())
        switchRow(tr("Cek otomatis saat aplikasi dibuka", "Check automatically when the app opens"), Updater.autoCheck(this)) { Updater.setAutoCheck(this, it) }
        if (avail != null && avail.notes.isNotBlank()) {
            row(tr("Perubahan build ${avail.build}", "Changes in build ${avail.build}"), tr("lihat", "view")) { UpdateUi.notes(this, tr("Yang baru di build ${avail.build}", "New in build ${avail.build}"), avail.notes.lines().filter { it.isNotBlank() }) }
        }
        Updater.bundledNotes(this)?.let { (title, lines) ->
            row(tr("Yang baru di build $installed", "New in build $installed"), title) { UpdateUi.notes(this, tr("Yang baru · build $installed", "What's new · build $installed"), listOf(title) + lines) }
        }
    }

    // ---------------------------------------------------------------- pindah data

    private fun backupSection() {
        sub(tr("📦 Pindah data & cadangan", "📦 Move data & backup"), tr("HP ↔ tablet: semua desa, timer, opsi, riwayat", "Phone ↔ tablet: all villages, timers, options & history"), Help.Topic.BACKUP)
        val dp8 = Ui.dp(this, 8)
        val r = Ui.hbox(this)
        r.addView(Ui.outlined(this, tr("Salin semua data", "Copy all data")) { copyAll() }.apply { isAllCaps = false }, Ui.weight1())
        r.addView(Ui.outlined(this, tr("Tempel data", "Paste data")) { pasteAll() }.apply { isAllCaps = false }, Ui.weight1().apply { marginStart = dp8 })
        box.addView(r)
        val r2 = Ui.hbox(this)
        r2.addView(Ui.outlined(this, tr("Simpan file", "Save file")) {
            val day = java.time.LocalDate.now().toString().replace("-", "")
            runCatching { saveFile.launch("clashtimer-$day.json") }
        }.apply { isAllCaps = false }, Ui.weight1())
        r2.addView(Ui.outlined(this, tr("Buka file", "Open file")) {
            runCatching { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
        }.apply { isAllCaps = false }, Ui.weight1().apply { marginStart = dp8 })
        box.addView(r2)
    }

    private fun copyAll() {
        val text = Backup.text(this)
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Clash Timer", text))
        Toast.makeText(this, tr("Data disalin (${text.length / 1024} KB). Tempel di Clash Timer perangkat lain.", "Data copied (${text.length / 1024} KB). Paste it in Clash Timer on the other device."), Toast.LENGTH_LONG).show()
    }

    private fun pasteAll() {
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        confirmRestore(text)
    }

    private fun confirmRestore(text: String?) {
        val o = text?.let { Backup.parse(it) }
        if (o == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle(tr("Bukan data Clash Timer", "Not Clash Timer data"))
                .setMessage(tr("Di perangkat asal buka Pengaturan → Salin semua data (atau Simpan file), lalu coba lagi di sini.", "On the other device open Settings → Copy all data (or Save file), then try again here."))
                .setPositiveButton("OK", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(tr("Ganti semua data di perangkat ini?", "Replace all data on this device?"))
            .setMessage(Backup.describe(o) + tr("\n\nSemua desa, timer, opsi, riwayat di perangkat ini diganti dengan data tersebut.", "\n\nAll villages, timers, options and history on this device will be replaced."))
            .setPositiveButton(tr("Ganti", "Replace")) { _, _ ->
                Backup.restore(this, o)
                Toast.makeText(this, tr("Data dipulihkan", "Data restored"), Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }

    // ---------------------------------------------------------------- API resmi

    private fun apiSection() {
        sub(tr("⚔️ Status War & CWL", "⚔️ War & CWL status"), tr("Dari ClashKing, tanpa API key", "From ClashKing, no API key needed"))
        note(tr("Ditampilkan di Dashboard sebagai info: war/CWL yang sedang diikuti dan kapan hari perangnya berakhir. " +
            "Selama CWL, nama akun, clan & liga diisi otomatis. Rencana tidak menahan hero karena war. " +
            "Tag pemain dikirim ke ClashKing (api.clashk.ing) untuk mengambil status ini.",
            "Shown on the Dashboard as info: the war/CWL you are in and when its war day ends. " +
            "During CWL, account name, clan & league are filled in automatically. The plan does not hold heroes for wars. " +
            "Player tags are sent to ClashKing (api.clashk.ing) to fetch this status."))
        val status = Store.apiStatus(this)
        if (status.isNotBlank()) note("Status: $status · ${Fmt.at(Store.apiTried(this), java.time.ZoneId.systemDefault())}")
        box.addView(Ui.outlined(this, tr("Perbarui sekarang", "Refresh now")) { syncNow() }.apply { isAllCaps = false }, Ui.matchWrap())
    }

    private fun syncNow() {
        Toast.makeText(this, tr("Menghubungi ClashKing…", "Contacting ClashKing…"), Toast.LENGTH_SHORT).show()
        ApiSync.syncAsync(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            render()
        }
    }

    /** Sumber data & pernyataan (Fan Content Policy Supercell). */
    private fun creditSection() {
        sub(tr("ℹ️ Sumber & pernyataan", "ℹ️ Sources & disclaimer"), null)
        note(tr("Clash Timer tidak berafiliasi dengan, didukung, disponsori, atau disetujui secara khusus oleh Supercell, " +
            "dan Supercell tidak bertanggung jawab atasnya. Lihat Fan Content Policy Supercell: supercell.com/fan-content-policy.",
            "Clash Timer is not affiliated with, endorsed, sponsored, or specifically approved by Supercell and Supercell " +
            "is not responsible for it. See Supercell's Fan Content Policy: supercell.com/fan-content-policy."))
        note(tr("Data game, gambar, serta status war & CWL: ClashKing (clashk.ing).",
            "Game data, images, and war & CWL status: ClashKing (clashk.ing)."))
    }

    private fun testTone(key: String) {
        if (!Notifier.canNotify(this)) Toast.makeText(this, tr("Izinkan notifikasi dulu", "Allow notifications first"), Toast.LENGTH_SHORT).show()
        else Notifier.test(this, key)
    }

    private fun pickTone(key: String) {
        val all = ToneMode.entries
        val cur = Store.toneMode(this, key)
        val labels = all.map { "${it.label}\n${it.desc}" as CharSequence }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(if (key == Notifier.KEY_DONE) tr("Nada upgrade selesai", "Upgrade done sound") else tr("Nada pengingat", "Reminder sound"))
            .setSingleChoiceItems(labels, all.indexOf(cur)) { d, w ->
                d.dismiss()
                val mode = all[w]
                when (mode) {
                    ToneMode.CUSTOM -> openPicker(key, mode, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_ALARM)
                    ToneMode.ALARM -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle(tr("Alarm keras", "Loud alarm"))
                            .setMessage(tr("Berbunyi berulang dengan volume alarm (biasanya tetap bunyi saat HP senyap/getar) sampai notifikasi dibuka, diusap, atau ditekan Matikan.\n\nPakai nada alarm bawaan HP, atau pilih nada lain?", "Rings repeatedly at alarm volume (usually even when the phone is silent/vibrate) until the notification is opened, swiped, or Stop is pressed.\n\nUse the phone's default alarm sound, or pick another?"))
                            .setPositiveButton(tr("Nada alarm bawaan", "Default alarm sound")) { _, _ ->
                                Notifier.changeTone(this, key, mode, null)
                                render()
                            }
                            .setNeutralButton(tr("Pilih nada", "Pick a sound")) { _, _ -> openPicker(key, mode, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE) }
                            .setNegativeButton(tr("Batal", "Cancel"), null)
                            .show()
                    }
                    else -> {
                        Notifier.changeTone(this, key, mode, null)
                        render()
                    }
                }
            }
            .show()
    }

    private fun openPicker(key: String, mode: ToneMode, type: Int) {
        pendingKey = key
        pendingMode = mode
        val existing = Store.toneUri(this, key)?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, if (mode == ToneMode.ALARM) tr("Nada alarm keras", "Loud alarm sound") else tr("Nada notifikasi", "Notification sound"))
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        if (existing != null) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        try {
            ringtonePicker.launch(i)
        } catch (_: Exception) {
            pendingKey = null
            pendingMode = null
            Toast.makeText(this, tr("Pemilih nada tidak tersedia di HP ini", "No sound picker on this phone"), Toast.LENGTH_SHORT).show()
        }
    }

    // ---------------------------------------------------------------- baris

    private fun row(title: String, value: String, onClick: () -> Unit) {
        val line = Ui.hbox(this).apply {
            minimumHeight = Ui.dp(this@SettingsActivity, 52)
            setPadding(0, Ui.dp(this@SettingsActivity, 10), 0, Ui.dp(this@SettingsActivity, 10))
            isClickable = true
            setOnClickListener { onClick() }
            Ui.ripple(this)
        }
        line.addView(Ui.text(this, title, Ui.BODY_M).apply { textSize = 15f; setTextColor(Ui.TEXT) }, Ui.weight1())
        line.addView(Ui.text(this, value, Ui.BODY_M).apply {
            textSize = 13f
            setTextColor(Ui.FREE_TEXT)
            gravity = android.view.Gravity.END
            maxLines = 2
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = Ui.dp(this@SettingsActivity, 12) })
        groupPanel().addView(line, Ui.matchWrap())
    }

    private fun switchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val line = Ui.hbox(this).apply {
            minimumHeight = Ui.dp(this@SettingsActivity, 52)
            setPadding(0, Ui.dp(this@SettingsActivity, 6), 0, Ui.dp(this@SettingsActivity, 6))
        }
        val label: TextView = Ui.text(this, title, Ui.BODY_M).apply { textSize = 15f; setTextColor(Ui.TEXT) }
        line.addView(label, Ui.weight1())
        val sw = SwitchCompat(this)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        line.addView(sw)
        line.isClickable = true
        line.setOnClickListener { sw.isChecked = !sw.isChecked }
        groupPanel().addView(line, Ui.matchWrap())
    }

    // ---------------------------------------------------------------- pemilih

    private fun pickRange(title: String, startKey: String, endKey: String, start: Int, end: Int) {
        TimePickerDialog(this, { _, h1, m1 ->
            val newStart = h1 * 60 + m1
            TimePickerDialog(this, { _, h2, m2 ->
                Store.putPlan(this, startKey, newStart)
                Store.putPlan(this, endKey, h2 * 60 + m2)
                render()
            }, end / 60, end % 60, true).apply { setTitle(title + tr(": selesai", ": end")) }.show()
        }, start / 60, start % 60, true).apply { setTitle(title + tr(": mulai", ": start")) }.show()
    }

    private fun dayName(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> tr("Sen", "Mon")
        DayOfWeek.TUESDAY -> tr("Sel", "Tue")
        DayOfWeek.WEDNESDAY -> tr("Rab", "Wed")
        DayOfWeek.THURSDAY -> tr("Kam", "Thu")
        DayOfWeek.FRIDAY -> tr("Jum", "Fri")
        DayOfWeek.SATURDAY -> tr("Sab", "Sat")
        DayOfWeek.SUNDAY -> tr("Min", "Sun")
    }

    private fun pickDays() {
        val all = DayOfWeek.entries
        val mask = Store.getInt(this, "work_days", Store.DEFAULT_WORK_DAYS)
        val checked = BooleanArray(7) { mask and (1 shl it) != 0 }
        val labels = if (id.clashtimer.core.Lang.en) arrayOf<CharSequence>("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday") else arrayOf<CharSequence>("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        MaterialAlertDialogBuilder(this)
            .setTitle(tr("Hari kerja / sekolah", "Work / school days"))
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton(tr("Simpan", "Save")) { _, _ ->
                var m = 0
                for (i in all.indices) if (checked[i]) m = m or (1 shl i)
                Store.putPlan(this, "work_days", m)
                render()
            }
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }

    private fun pickEarly() {
        val options = intArrayOf(0, 5, 10, 15, 30, 60)
        val labels = options.map { (if (it == 0) tr("Tidak ada (hanya saat selesai)", "None (only when done)") else tr("$it menit sebelumnya", "$it min before")) as CharSequence }.toTypedArray()
        val current = options.indexOf(Store.earlyMinutes(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(tr("Pengingat tambahan", "Extra reminder"))
            .setSingleChoiceItems(labels, current) { d, which ->
                val min = options[which]
                Store.setEarlyMinutes(this, min)
                // Yang sudah masuk jendela pengingat tidak perlu diberi pengingat mendadak.
                val now = System.currentTimeMillis()
                val list = Store.upgrades(this)
                list.forEach { u -> u.earlyNotified = u.doneNotified || (min > 0 && u.finishAt - min * 60_000L <= now) }
                Store.saveUpgrades(this, list)
                AlarmScheduler.schedule(this)
                d.dismiss()
                render()
            }
            .show()
    }
}
