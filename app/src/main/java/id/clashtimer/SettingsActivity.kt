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
        box = Ui.vbox(this).apply { setPadding(Ui.dp(this@SettingsActivity, 16), 0, Ui.dp(this@SettingsActivity, 16), Ui.dp(this@SettingsActivity, 32)) }
        setContentView(ScrollView(this).apply { addView(box) })
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
    private fun sub(title: String, subtitle: String? = null) {
        val col = Ui.vbox(this).apply { setPadding(Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 12), Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 4)) }
        col.addView(Ui.text(this, title, Ui.TITLE_S))
        if (subtitle != null) col.addView(Ui.muted(this, subtitle))
        box.addView(col)
    }

    private fun render() {
        box.removeAllViews()
        val ps = Store.planSettings(this, null)
        val s = ps.schedule

        box.addView(Ui.section(this, "🌐 Untuk semua desa", "Jam istirahat, API, event, ikon, nada & notifikasi"))
        note("Gold Pass (dari lama timer) dan CWL (dari API) terdeteksi otomatis per desa; strategi saran & opsi collector dipilih di Beranda → kartu \"Opsi desa ini\".")

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

        sub("🎉 Event", "Hammer Jam, Builder Base Bonanza, dll.")
        box.addView(Ui.muted(this, "Supercell tidak menyediakan jadwal event yang bisa dibaca aplikasi. " +
            "Saat event muncul di game, aktifkan di sini dan cocokkan tanggalnya — saran, rencana dan timer langsung memakai potongannya.").apply {
            setPadding(Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 6))
        })
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

        sub("🎵 Nada notifikasi", "Alarm keras berbunyi berulang dengan volume alarm sampai notifikasi dibuka, diusap, atau ditekan Matikan")
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

        sub("🔔 Notifikasi")
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
        box.addView(Ui.muted(this, "Mode alarm kuat memakai alarm sistem sehingga tidak ditunda saat HP tidur; efeknya ada ikon jam kecil di status bar.").apply {
            setPadding(Ui.dp(this@SettingsActivity, 4), 0, Ui.dp(this@SettingsActivity, 4), Ui.dp(this@SettingsActivity, 8))
        })

        sub("📱 Widget & berjalan di latar", "Widget diperbarui saat data berubah, saat upgrade selesai, dan paling lambat tiap 30 menit")
        note(
            (if (Background.isXiaomi()) Background.TIP_BODY + " " else
                "Bila widget atau notifikasi sering telat: izinkan aplikasi berjalan tanpa batasan baterai (dan Autostart bila HP punya). ") +
                "Jam ↻ di widget menunjukkan kapan terakhir diperbarui — ketuk untuk memperbarui sekarang. " +
                "Data tiap perangkat terpisah: update di HP tidak mengubah widget di tablet (pakai Pindah data).",
        )
        box.addView(Ui.outlined(this, "Buka info aplikasi") { Background.openAppInfo(this) }.apply { isAllCaps = false }, Ui.matchWrap())
    }

    // ---------------------------------------------------------------- pindah data

    private fun backupSection() {
        sub("📦 Pindah data & cadangan", "HP ↔ tablet: semua desa, timer, opsi, riwayat & API key")
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
        note("Di perangkat lain: Pengaturan → Tempel data (atau Buka file). Data di perangkat tujuan diganti seluruhnya. " +
            "Isinya termasuk API key — jangan bagikan teks/file ini ke orang lain.")
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
        val (card, col) = Ui.card(this)
        card.setOnClickListener { onClick() }
        col.addView(Ui.text(this, title, Ui.TITLE_S))
        col.addView(Ui.text(this, value, Ui.BODY_M).apply { setTextColor(Ui.primary(this@SettingsActivity)) })
        box.addView(card)
    }

    private fun switchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val (card, col) = Ui.card(this)
        val line = Ui.hbox(this)
        val label: TextView = Ui.text(this, title, Ui.TITLE_S)
        line.addView(label, Ui.weight1())
        val sw = SwitchCompat(this)
        sw.isChecked = checked
        sw.setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        line.addView(sw)
        card.setOnClickListener { sw.isChecked = !sw.isChecked }
        col.addView(line)
        box.addView(card)
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
