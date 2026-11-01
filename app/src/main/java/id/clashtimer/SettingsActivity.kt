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
        Toast.makeText(this, "Nada disimpan · ${Notifier.toneTitle(this, key)}", Toast.LENGTH_SHORT).show()
    }

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.openOutputStream(uri)?.use { it.write(Backup.json(this).toString(1).toByteArray(Charsets.UTF_8)) }
        }.onSuccess { Toast.makeText(this, "File cadangan disimpan", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(this, "Gagal menyimpan: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = runCatching { contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } }.getOrNull()
        confirmRestore(text)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Pengaturan"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        Ui.init(this)
        box = Ui.vbox(this).apply { setPadding(Ui.dp(this@SettingsActivity, 16), 0, Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 32)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(box) })
        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun note(text: String) {
        box.addView(Ui.muted(this, text).apply {
            setPadding(Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4))
        })
    }

    /** Judul kecil di dalam satu bagian. */
    /** Judul kecil di dalam satu bagian; [help] = tombol "?" berisi penjelasannya. */
    private fun sub(title: String, subtitle: String? = null, help: Help.Topic? = null) {
        val col = Ui.vbox(this).apply { setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 6)) }
        val head = Ui.hbox(this)
        head.addView(Ui.text(this, title.uppercase(id.clashtimer.core.Fmt.locale), Ui.BODY_S).apply {
            textSize = 12f
            letterSpacing = 0.06f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Ui.TEXT2)
        })
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

    private fun render() {
        box.removeAllViews()
        val ps = Store.planSettings(this, null)
        val s = ps.schedule

        box.addView(Ui.section(this, "🌐 Untuk semua desa", "Jam istirahat, API, event, ikon, nada & notifikasi"))
        note("Opsi per desa (strategi, Gold Pass, CWL, collector) ada di ikon opsi di layar utama.")

        apiSection()
        backupSection()

        sub("😴 Jam istirahat", "Saran memilih upgrade yang selesai saat Anda bisa membuka game")
        switchRow("Hitung jam tidur", s.sleep.enabled) { Store.putPlan(this, "sleep_on", it); render() }
        if (s.sleep.enabled) {
            row("Tidur", "${Fmt.clock(s.sleep.startMin)} – ${Fmt.clock(s.sleep.endMin)}") {
                pickRange("Jam tidur", "sleep_start", "sleep_end", s.sleep.startMin, s.sleep.endMin)
            }
        }
        switchRow("Hitung jam kerja / sekolah", s.work.enabled) { Store.putPlan(this, "work_on", it); render() }
        if (s.work.enabled) {
            row("Jam kerja", "${Fmt.clock(s.work.startMin)} – ${Fmt.clock(s.work.endMin)}") {
                pickRange("Jam kerja", "work_start", "work_end", s.work.startMin, s.work.endMin)
            }
            val days = DayOfWeek.entries.filter { it in s.work.days }.joinToString(", ") { dayName(it) }
            row("Hari kerja", days.ifBlank { "tidak ada" }) { pickDays() }
        }

        sub("🎉 Event", "Hammer Jam, Builder Base Bonanza, dll.", Help.Topic.EVENTS)
        EventsUi.renderList(this, box) { render() }
        box.addView(Ui.outlined(this, "+ Tambah event") { EventsUi.addPreset(this) { render() } }.apply { isAllCaps = false }, Ui.matchWrap())

        sub("🖼️ Ikon")
        switchRow("Tampilkan ikon bangunan & hero", Store.iconsOn(this)) { Store.setIconsOn(this, it) }
        val mb = Icons.cacheSize(this) / 1024.0 / 1024.0
        box.addView(Ui.outlined(this, String.format(java.util.Locale.ROOT, "Hapus cache ikon (%.1f MB)", mb)) {
            Icons.clearCache(this)
            render()
        }.apply { isAllCaps = false }, Ui.matchWrap())
        box.addView(Ui.muted(this, "Ikon diunduh sekali dari ClashKing Assets (assets.clashk.ing — terima kasih!) lalu disimpan di HP. " +
            "Gambar © Supercell. Clash Timer adalah fan content tidak resmi, tidak berafiliasi dengan dan tidak didukung oleh Supercell.").apply {
            setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4))
        })

        sub("🎵 Nada notifikasi", help = Help.Topic.NOTIFY)
        row("Upgrade selesai & helper", Notifier.toneTitle(this, Notifier.KEY_DONE)) { pickTone(Notifier.KEY_DONE) }
        row("Pengingat sebelum selesai", Notifier.toneTitle(this, Notifier.KEY_EARLY)) { pickTone(Notifier.KEY_EARLY) }
        val tests = Ui.hbox(this)
        tests.addView(Ui.outlined(this, "Tes nada selesai") { testTone(Notifier.KEY_DONE) }.apply { isAllCaps = false }, Ui.weight1())
        tests.addView(Ui.outlined(this, "Tes pengingat") { testTone(Notifier.KEY_EARLY) }.apply { isAllCaps = false },
            Ui.weight1().apply { marginStart = Ui.dp(this@SettingsActivity, 8) })
        box.addView(tests)
        box.addView(Ui.outlined(this, "Pengaturan notifikasi Android") {
            runCatching {
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
            }
        }.apply { isAllCaps = false }, Ui.matchWrap())

        sub("🔔 Notifikasi", help = Help.Topic.NOTIFY)
        val early = Store.earlyMinutes(this)
        row("Pengingat sebelum selesai", if (early == 0) "Tidak ada" else "$early menit sebelumnya") { pickEarly() }
        switchRow("Notifikasi helper", Store.notifyHelpers(this)) {
            Store.setNotifyHelpers(this, it)
            AlarmScheduler.schedule(this)
        }
        switchRow("Mode alarm kuat (paling tepat waktu)", Store.strongAlarm(this)) {
            Store.setStrongAlarm(this, it)
            AlarmScheduler.schedule(this)
        }

        sub("📱 Widget & berjalan di latar", help = Help.Topic.WIDGET)
        box.addView(Ui.outlined(this, "Buka info aplikasi") { Background.openAppInfo(this) }.apply { isAllCaps = false }, Ui.matchWrap())

        updateSection()
    }

    // ---------------------------------------------------------------- update aplikasi

    private fun updateSection() {
        sub("🔄 Update aplikasi", "github.com/${Updater.REPO}", Help.Topic.UPDATE)
        val zone = java.time.ZoneId.systemDefault()
        val installed = Updater.installedBuild(this)
        val latest = Updater.latest(this)
        val avail = Updater.available(this)
        val status = when {
            avail != null -> "build ${avail.build} tersedia (${Updater.mb(avail.size)})"
            latest != null -> "sudah versi terbaru"
            else -> "belum dicek"
        }
        val checked = Updater.lastChecked(this).takeIf { it > 0 }?.let { " · dicek ${Fmt.at(it, zone)}" } ?: ""
        val err = Updater.lastError(this)?.let { "\nCek terakhir gagal: $it" } ?: ""
        note("Terpasang: build $installed (versi ${Updater.installedName(this)}) · $status$checked$err")
        if (avail != null) {
            box.addView(Ui.filled(this, "⬆️ Unduh & pasang build ${avail.build}") { UpdateUi.start(this, avail) }
                .apply { isAllCaps = false }, Ui.matchWrap())
        }
        box.addView(Ui.outlined(this, "Cek update sekarang") { UpdateUi.checkNow(this) { render() } }
            .apply { isAllCaps = false }, Ui.matchWrap())
        switchRow("Cek otomatis saat aplikasi dibuka", Updater.autoCheck(this)) { Updater.setAutoCheck(this, it) }
        if (avail != null && avail.notes.isNotBlank()) {
            row("Perubahan build ${avail.build}", "lihat") { UpdateUi.notes(this, "Yang baru di build ${avail.build}", avail.notes.lines().filter { it.isNotBlank() }) }
        }
        Updater.bundledNotes(this)?.let { (title, lines) ->
            row("Yang baru di build $installed", title) { UpdateUi.notes(this, "Yang baru · build $installed", listOf(title) + lines) }
        }
    }

    // ---------------------------------------------------------------- pindah data

    private fun backupSection() {
        sub("📦 Pindah data & cadangan", "HP ↔ tablet: semua desa, timer, opsi, riwayat & API key", Help.Topic.BACKUP)
        val dp8 = Ui.dp(this, 8)
        val r = Ui.hbox(this)
        r.addView(Ui.outlined(this, "Salin semua data") { copyAll() }.apply { isAllCaps = false }, Ui.weight1())
        r.addView(Ui.outlined(this, "Tempel data") { pasteAll() }.apply { isAllCaps = false }, Ui.weight1().apply { marginStart = dp8 })
        box.addView(r)
        val r2 = Ui.hbox(this)
        r2.addView(Ui.outlined(this, "Simpan file") {
            val day = java.time.LocalDate.now().toString().replace("-", "")
            runCatching { saveFile.launch("clashtimer-$day.json") }
        }.apply { isAllCaps = false }, Ui.weight1())
        r2.addView(Ui.outlined(this, "Buka file") {
            runCatching { openFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) }
        }.apply { isAllCaps = false }, Ui.weight1().apply { marginStart = dp8 })
        box.addView(r2)
        note("Isinya termasuk API key — jangan dibagikan ke orang lain.")
    }

    private fun copyAll() {
        val text = Backup.text(this)
        val cm = getSystemService(android.content.ClipboardManager::class.java)
        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Clash Timer", text))
        Toast.makeText(this, "Data disalin (${text.length / 1024} KB). Tempel di Clash Timer perangkat lain.", Toast.LENGTH_LONG).show()
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
                .setTitle("Bukan data Clash Timer")
                .setMessage("Di perangkat asal buka Pengaturan → Salin semua data (atau Simpan file), lalu coba lagi di sini.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Ganti semua data di perangkat ini?")
            .setMessage(Backup.describe(o) + "\n\nSemua desa, timer, opsi, riwayat dan API key di perangkat ini diganti dengan data tersebut.")
            .setPositiveButton("Ganti") { _, _ ->
                Backup.restore(this, o)
                Toast.makeText(this, "Data dipulihkan", Toast.LENGTH_SHORT).show()
                render()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // ---------------------------------------------------------------- API resmi

    private fun apiSection() {
        sub("🔑 API resmi Clash of Clans", "Otomatis: nama akun & clan, ikut CWL & jadwal aslinya, akhir Gold Pass, upgrade yang selesai lebih cepat, cek export ketinggalan")
        val key = Store.apiKey(this)
        row("API key", if (key.isBlank()) "Belum diisi — ketuk untuk menempel" else "•••• ${key.takeLast(6)}") { askApiKey() }
        val status = Store.apiStatus(this)
        if (status.isNotBlank()) {
            note("Status: $status · ${Fmt.at(Store.apiTried(this), java.time.ZoneId.systemDefault())}")
        }
        val r = Ui.hbox(this)
        r.addView(Ui.outlined(this, "Tes & perbarui") { syncNow() }.apply { isAllCaps = false }, Ui.weight1())
        r.addView(Ui.outlined(this, "Salin IP proxy") {
            val cm = getSystemService(android.content.ClipboardManager::class.java)
            cm?.setPrimaryClip(android.content.ClipData.newPlainText("IP", CocApi.PROXY_IP))
            Toast.makeText(this, "Disalin: ${CocApi.PROXY_IP}", Toast.LENGTH_SHORT).show()
        }.apply { isAllCaps = false }, Ui.weight1().apply { marginStart = Ui.dp(this@SettingsActivity, 8) })
        box.addView(r)
        box.addView(Ui.outlined(this, "Buka developer.clashofclans.com") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(CocApi.DEV_SITE))) }
        }.apply { isAllCaps = false }, Ui.matchWrap())
        note("Cara membuat key: masuk ke developer.clashofclans.com → My Account → Create New Key → nama & deskripsi bebas → " +
            "IP address: ${CocApi.PROXY_IP} → Create Key → salin Token, lalu tempel di sini. Permintaan dikirim lewat proxy RoyaleAPI " +
            "(cocproxy.royaleapi.dev); key hanya disimpan di HP ini. Data diperbarui otomatis setiap desa ditambah / di-update dan saat aplikasi dibuka " +
            "(paling sering tiap 30 menit): CWL dihitung hanya bila akun ini ada di roster CWL clan (atau war preference IN sebelum CWL mulai), " +
            "dan upgrade hero, lab, pet, Star Lab, TH/BH yang di game sudah naik level (book, gem, potion) langsung ditandai selesai. " +
            "Data event seperti Hammer Jam tidak tersedia di API resmi.")
        if (key.isNotBlank()) {
            box.addView(Ui.flat(this, "Hapus API key") {
                Store.setApiKey(this, "")
                render()
            }.apply { isAllCaps = false })
        }
    }

    private fun askApiKey() {
        val input = android.widget.EditText(this).apply {
            hint = "eyJ0eXAiOiJKV1Qi…"
            setText(Store.apiKey(this@SettingsActivity))
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            minLines = 3
            maxLines = 6
        }
        val wrap = LinearLayout(this).apply {
            setPadding(Ui.dp(this@SettingsActivity, 20), Ui.dp(this@SettingsActivity, 8), Ui.dp(this@SettingsActivity, 20), 0)
            addView(input, Ui.matchWrap())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("API key (Token)")
            .setView(wrap)
            .setPositiveButton("Simpan & tes") { _, _ ->
                Store.setApiKey(this, input.text.toString().replace("\\s".toRegex(), ""))
                render()
                syncNow()
            }
            .setNeutralButton("Tempel") { _, _ ->
                val cm = getSystemService(android.content.ClipboardManager::class.java)
                val text = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
                Store.setApiKey(this, text.replace("\\s".toRegex(), ""))
                render()
                syncNow()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun syncNow() {
        if (Store.apiKey(this).isBlank()) {
            Toast.makeText(this, "Isi API key dulu", Toast.LENGTH_SHORT).show()
            return
        }
        Toast.makeText(this, "Menghubungi API…", Toast.LENGTH_SHORT).show()
        ApiSync.syncAsync(this) { msg ->
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            render()
        }
    }

    private fun testTone(key: String) {
        if (!Notifier.canNotify(this)) Toast.makeText(this, "Izinkan notifikasi dulu", Toast.LENGTH_SHORT).show()
        else Notifier.test(this, key)
    }

    private fun pickTone(key: String) {
        val all = ToneMode.entries
        val cur = Store.toneMode(this, key)
        val labels = all.map { "${it.label}\n${it.desc}" as CharSequence }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(if (key == Notifier.KEY_DONE) "Nada upgrade selesai" else "Nada pengingat")
            .setSingleChoiceItems(labels, all.indexOf(cur)) { d, w ->
                d.dismiss()
                val mode = all[w]
                when (mode) {
                    ToneMode.CUSTOM -> openPicker(key, mode, RingtoneManager.TYPE_NOTIFICATION or RingtoneManager.TYPE_RINGTONE or RingtoneManager.TYPE_ALARM)
                    ToneMode.ALARM -> {
                        MaterialAlertDialogBuilder(this)
                            .setTitle("Alarm keras")
                            .setMessage("Berbunyi berulang dengan volume alarm (biasanya tetap bunyi saat HP senyap/getar) sampai notifikasi dibuka, diusap, atau ditekan Matikan.\n\nPakai nada alarm bawaan HP, atau pilih nada lain?")
                            .setPositiveButton("Nada alarm bawaan") { _, _ ->
                                Notifier.changeTone(this, key, mode, null)
                                render()
                            }
                            .setNeutralButton("Pilih nada") { _, _ -> openPicker(key, mode, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE) }
                            .setNegativeButton("Batal", null)
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
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, if (mode == ToneMode.ALARM) "Nada alarm keras" else "Nada notifikasi")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        if (existing != null) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existing)
        try {
            ringtonePicker.launch(i)
        } catch (_: Exception) {
            pendingKey = null
            pendingMode = null
            Toast.makeText(this, "Pemilih nada tidak tersedia di HP ini", Toast.LENGTH_SHORT).show()
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
            }, end / 60, end % 60, true).apply { setTitle("$title: selesai") }.show()
        }, start / 60, start % 60, true).apply { setTitle("$title: mulai") }.show()
    }

    private fun dayName(d: DayOfWeek): String = when (d) {
        DayOfWeek.MONDAY -> "Sen"
        DayOfWeek.TUESDAY -> "Sel"
        DayOfWeek.WEDNESDAY -> "Rab"
        DayOfWeek.THURSDAY -> "Kam"
        DayOfWeek.FRIDAY -> "Jum"
        DayOfWeek.SATURDAY -> "Sab"
        DayOfWeek.SUNDAY -> "Min"
    }

    private fun pickDays() {
        val all = DayOfWeek.entries
        val mask = Store.getInt(this, "work_days", Store.DEFAULT_WORK_DAYS)
        val checked = BooleanArray(7) { mask and (1 shl it) != 0 }
        val labels = arrayOf<CharSequence>("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
        MaterialAlertDialogBuilder(this)
            .setTitle("Hari kerja / sekolah")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Simpan") { _, _ ->
                var m = 0
                for (i in all.indices) if (checked[i]) m = m or (1 shl i)
                Store.putPlan(this, "work_days", m)
                render()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun pickEarly() {
        val options = intArrayOf(0, 5, 10, 15, 30, 60)
        val labels = options.map { (if (it == 0) "Tidak ada (hanya saat selesai)" else "$it menit sebelumnya") as CharSequence }.toTypedArray()
        val current = options.indexOf(Store.earlyMinutes(this)).coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle("Pengingat tambahan")
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
