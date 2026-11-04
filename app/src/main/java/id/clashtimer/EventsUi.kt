package id.clashtimer

import android.app.Activity
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.DAY
import id.clashtimer.core.Fmt
import id.clashtimer.core.GameEvent
import id.clashtimer.core.HOUR
import id.clashtimer.core.MIN
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Event game (Hammer Jam, Builder Base Bonanza, …). Supercell tidak menyediakan jadwal event
 * yang bisa dibaca aplikasi, jadi event diaktifkan & dicocokkan tanggalnya oleh pengguna.
 */
object EventsUi {

    fun summary(e: GameEvent, zone: ZoneId): String =
        tr("${Fmt.at(e.start, zone)} – ${Fmt.at(e.end, zone)}\n−${e.timePct}% waktu · −${e.costPct}% biaya · ${e.scopeLabel}", "${Fmt.at(e.start, zone)} – ${Fmt.at(e.end, zone)}\n−${e.timePct}% time · −${e.costPct}% cost · ${e.scopeLabel}") +
            if (e.cutRunning) tr(" · upgrade berjalan ikut dipotong", " · running upgrades are cut too") else ""

    /** Simpan (ganti bila id sama) lalu hitung ulang timer semua desa. */
    fun save(ctx: Context, e: GameEvent) {
        val list = Store.events(ctx).toMutableList()
        val i = list.indexOfFirst { it.id == e.id }
        if (i >= 0) list[i] = e else list += e
        Store.setEvents(ctx, list)
        reprojectAll(ctx)
    }

    private fun remove(ctx: Context, e: GameEvent) {
        Store.setEvents(ctx, Store.events(ctx).filter { it.id != e.id })
        reprojectAll(ctx)
    }

    private fun reprojectAll(ctx: Context) {
        for (a in Store.accounts(ctx)) Repo.reproject(ctx, a.tag)
        // Pengingat "event mulai" & "berakhir besok"
        Repo.rebuildGlobalReminders(ctx)
    }

    // ---------------------------------------------------------------- Beranda

    fun homeBanners(act: MainActivity, box: LinearLayout, bb: Boolean, now: Long) {
        val zone = ZoneId.systemDefault()
        for (e in Store.events(act)) {
            val relevant = if (bb) e.bb else (e.builder || e.lab)
            if (!relevant) continue
            val text = when {
                e.activeAt(now) ->
                    tr("🎉 ${e.name} berlangsung sampai ${Fmt.at(e.end, zone)} · waktu −${e.timePct}%, biaya −${e.costPct}%. " +
                        "Saran & rencana sudah memakai potongan ini.", "🎉 ${e.name} runs until ${Fmt.at(e.end, zone)} · time −${e.timePct}%, cost −${e.costPct}%. " +
                        "Suggestions & plan already use this discount.")
                e.enabled && e.start > now && e.start - now < 3 * DAY ->
                    tr("🎉 ${e.name} mulai ${Fmt.at(e.start, zone)} — upgrade panjang lebih hemat bila dimulai saat event.", "🎉 ${e.name} starts ${Fmt.at(e.start, zone)} — long upgrades are cheaper if started during the event.")
                !e.enabled && e.id.startsWith("hammer_") && now > e.start - 7 * DAY && now < e.end ->
                    tr("🔨 Hammer Jam biasanya awal November (perkiraan ${Fmt.dateShort(e.start, zone)}–${Fmt.dateShort(e.end, zone)}). " +
                        "Sudah muncul di game? Ketuk untuk mengaktifkan & mencocokkan tanggalnya.", "🔨 Hammer Jam is usually early November (estimate ${Fmt.dateShort(e.start, zone)}–${Fmt.dateShort(e.end, zone)}). " +
                        "Already in the game? Tap to enable it & match the dates.")
                else -> null
            } ?: continue
            box.addView(Ui.banner(act, text, Ui.VIOLET).apply {
                setOnClickListener { edit(act, if (e.enabled) e else e.copy(enabled = true)) { act.dataChanged() } }
            })
        }
    }

    // ---------------------------------------------------------------- Pengaturan

    fun renderList(act: Activity, box: LinearLayout, onChanged: () -> Unit) {
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()
        for (e in Store.events(act)) {
            val (card, col) = Ui.card(act)
            val head = Ui.hbox(act)
            val state = when {
                e.activeAt(now) -> tr(" · berlangsung", " · running")
                !e.enabled -> tr(" · nonaktif", " · off")
                e.end <= now -> tr(" · sudah lewat", " · over")
                else -> ""
            }
            head.addView(Ui.text(act, "🎉 ${e.name}$state", Ui.TITLE_S), Ui.weight1())
            val sw = SwitchCompat(act)
            sw.isChecked = e.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                save(act, e.copy(enabled = checked))
                onChanged()
            }
            head.addView(sw)
            col.addView(head)
            col.addView(Ui.muted(act, summary(e, zone)))
            card.setOnClickListener { edit(act, e, false, onChanged) }
            box.addView(card)
        }
    }

    fun addPreset(act: Activity, onChanged: () -> Unit) {
        val now = System.currentTimeMillis()
        val presets = listOf(GameEvent.hammerJam(now).copy(enabled = true), GameEvent.bonanza(now), GameEvent.custom(now))
        val labels = arrayOf<CharSequence>(
            tr("🔨 Hammer Jam\n−50% waktu & biaya · Builder, Lab & Pet", "🔨 Hammer Jam\n−50% time & cost · Builder, Lab & Pet"),
            tr("🛠️ Builder Base Bonanza\n−50% waktu & biaya · Builder Base", "🛠️ Builder Base Bonanza\n−50% time & cost · Builder Base"),
            tr("🎉 Event lain\natur sendiri", "🎉 Other event\nset it yourself"),
        )
        MaterialAlertDialogBuilder(act)
            .setTitle(tr("Tambah event", "Add event"))
            .setItems(labels) { _, w ->
                val e = presets[w]
                // Hammer Jam yang sama sudah ada → buka yang lama
                val existing = Store.events(act).firstOrNull { it.id == e.id }
                edit(act, existing?.copy(enabled = true) ?: e, existing == null, onChanged)
            }
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }

    /** Dialog pengaturan satu event. */
    fun edit(act: Activity, ev: GameEvent, isNew: Boolean = false, onChanged: () -> Unit) {
        val zone = ZoneId.systemDefault()
        var e = ev
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 20), Ui.dp(act, 4), Ui.dp(act, 20), Ui.dp(act, 4)) }
        lateinit var fill: () -> Unit

        fun row(label: String, value: String, onClick: () -> Unit): View {
            val r = Ui.vbox(act).apply {
                setPadding(0, Ui.dp(act, 8), 0, Ui.dp(act, 8))
                isClickable = true
                setOnClickListener { onClick() }
            }
            r.addView(Ui.muted(act, label))
            r.addView(Ui.text(act, "$value  ✎", Ui.BODY_M).apply { setTextColor(Ui.primary(act)) })
            return r
        }

        fun switchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
            val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6)) }
            r.addView(Ui.text(act, label, Ui.BODY_M), Ui.weight1())
            val sw = SwitchCompat(act)
            sw.isChecked = checked
            sw.setOnCheckedChangeListener { _, c -> onChange(c) }
            r.addView(sw)
            return r
        }

        fill = {
            col.removeAllViews()
            col.addView(row(tr("Nama", "Name"), e.name) {
                askText(act, tr("Nama event", "Event name"), e.name) { e = e.copy(name = it.ifBlank { e.name }); fill() }
            })
            col.addView(row(tr("Mulai", "Start"), Fmt.at(e.start, zone)) {
                pickDateTime(act, e.start) { t -> e = e.copy(start = t, end = maxOf(e.end, t + HOUR)); fill() }
            })
            col.addView(row(tr("Selesai", "End"), Fmt.at(e.end, zone)) {
                pickDateTime(act, e.end) { t -> e = e.copy(end = maxOf(t, e.start + HOUR)); fill() }
            })
            col.addView(Ui.flat(act, tr("⏱️ Sudah berjalan? Isi dari sisa waktu di game", "⏱️ Already running? Fill in from the time left in the game")) {
                askRemaining(act) { ms ->
                    val now = System.currentTimeMillis()
                    val end = (now + ms) / MIN * MIN
                    e = e.copy(start = minOf(e.start, now), end = end, enabled = true)
                    fill()
                }
            }.apply { isAllCaps = false })
            col.addView(row(tr("Potongan waktu", "Time discount"), "${e.timePct}%") { pickPct(act, tr("Potongan waktu", "Time discount"), e.timePct) { e = e.copy(timePct = it); fill() } })
            col.addView(row(tr("Potongan biaya", "Cost discount"), "${e.costPct}%") { pickPct(act, tr("Potongan biaya", "Cost discount"), e.costPct) { e = e.copy(costPct = it); fill() } })
            col.addView(row(tr("Berlaku untuk", "Applies to"), e.scopeLabel) {
                val checked = booleanArrayOf(e.builder, e.lab, e.bb)
                MaterialAlertDialogBuilder(act)
                    .setTitle(tr("Berlaku untuk", "Applies to"))
                    .setMultiChoiceItems(
                        arrayOf<CharSequence>(tr("Builder (bangunan, jebakan, hero)", "Builder (buildings, traps, heroes)"), tr("Laboratory & Pet House", "Laboratory & Pet House"), tr("Builder Base", "Builder Base")), checked,
                    ) { _, w, c -> checked[w] = c }
                    .setPositiveButton(tr("OK", "OK")) { _, _ -> e = e.copy(builder = checked[0], lab = checked[1], bb = checked[2]); fill() }
                    .setNegativeButton(tr("Batal", "Cancel"), null)
                    .show()
            })
            col.addView(switchRow(tr("Upgrade yang sudah berjalan ikut dipotong", "Running upgrades are cut too"), e.cutRunning) { e = e.copy(cutRunning = it) })
            col.addView(switchRow(tr("Aktif", "Active"), e.enabled) { e = e.copy(enabled = it) })
            col.addView(Ui.muted(act,
                tr("Potongan dipakai untuk upgrade yang DIMULAI selama event, dikalikan dengan Gold Pass (mis. 50% & 20% → 40% dari waktu asli). ", "The discount applies to upgrades STARTED during the event, multiplied with Gold Pass (e.g. 50% & 20% → 40% of the original time). ") +
                    tr("Supercell tidak menyebut upgrade yang sudah berjalan ikut dipotong; nyalakan opsi itu hanya bila Anda melihatnya di game. ", "Supercell does not say running upgrades are cut too; only turn that option on if you see it in the game. ") +
                    tr("Supercharge & Crafted Defense tidak ikut (tidak dihitung aplikasi ini).", "Supercharges & Crafted Defenses are not included (this app does not count them).")).apply {
                setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 4))
            })
        }
        fill()

        val dlg = MaterialAlertDialogBuilder(act)
            .setTitle(if (isNew) tr("Event baru", "New event") else e.name)
            .setView(ScrollView(act).apply { addView(col) })
            .setPositiveButton(tr("Simpan", "Save")) { _, _ ->
                save(act, e)
                onChanged()
            }
            .setNegativeButton(tr("Batal", "Cancel"), null)
        if (!isNew) dlg.setNeutralButton(tr("Hapus", "Delete")) { _, _ ->
            remove(act, e)
            onChanged()
        }
        dlg.show()
    }

    private fun pickDateTime(act: Activity, initial: Long, onPicked: (Long) -> Unit) {
        val zone = ZoneId.systemDefault()
        val z = Instant.ofEpochMilli(initial).atZone(zone)
        DatePickerDialog(act, { _, y, m, d ->
            TimePickerDialog(act, { _, h, min ->
                onPicked(ZonedDateTime.of(y, m + 1, d, h, min, 0, 0, zone).toInstant().toEpochMilli())
            }, z.hour, z.minute, true).show()
        }, z.year, z.monthValue - 1, z.dayOfMonth).show()
    }

    private fun pickPct(act: Activity, title: String, cur: Int, onPicked: (Int) -> Unit) {
        val options = intArrayOf(0, 10, 20, 25, 30, 50, 75)
        val labels = options.map { "$it%" as CharSequence }.toTypedArray()
        MaterialAlertDialogBuilder(act)
            .setTitle(title)
            .setSingleChoiceItems(labels, options.indexOf(cur).coerceAtLeast(0)) { d, w ->
                d.dismiss()
                onPicked(options[w])
            }
            .show()
    }

    private fun askText(act: Activity, title: String, cur: String, onDone: (String) -> Unit) {
        val input = EditText(act).apply {
            setText(cur)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(title)
            .setView(LinearLayout(act).apply {
                setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), 0)
                addView(input, Ui.matchWrap())
            })
            .setPositiveButton(tr("OK", "OK")) { _, _ -> onDone(input.text.toString().trim().take(40)) }
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }

    /** "Berakhir dalam 16h 3j" di game → hari & jam. */
    private fun askRemaining(act: Activity, onDone: (Long) -> Unit) {
        fun field(hint: String): EditText = EditText(act).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val days = field("hari")
        val hours = field("jam")
        val mins = field("menit")
        val row = Ui.hbox(act).apply { setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), 0) }
        row.addView(days, Ui.weight1())
        row.addView(hours, Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
        row.addView(mins, Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
        val col = Ui.vbox(act)
        col.addView(TextView(act).apply {
            text = tr("Lihat sisa waktu event di game (mis. \"Berakhir dalam 16h 3j\").", "Check the event time left in the game (e.g. \"Ends in 16h 3m\").")
            setPadding(Ui.dp(act, 20), Ui.dp(act, 4), Ui.dp(act, 20), 0)
        })
        col.addView(row)
        var dialog: AlertDialog? = null
        dialog = MaterialAlertDialogBuilder(act)
            .setTitle(tr("Sisa waktu event", "Event time left"))
            .setView(col)
            .setPositiveButton(tr("OK", "OK")) { _, _ ->
                val d = days.text.toString().toLongOrNull() ?: 0
                val h = hours.text.toString().toLongOrNull() ?: 0
                val m = mins.text.toString().toLongOrNull() ?: 0
                val ms = d * DAY + h * HOUR + m * MIN
                if (ms > 0) onDone(ms)
                dialog?.dismiss()
            }
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }
}
