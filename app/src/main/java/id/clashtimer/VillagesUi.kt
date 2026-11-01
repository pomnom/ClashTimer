package id.clashtimer

import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.Fmt
import java.time.ZoneId

/** Daftar desa: pilih desa aktif, ganti nama, hapus, atau tambah desa baru. */
object VillagesUi {

    fun show(act: MainActivity) {
        val zone = ZoneId.systemDefault()
        val accounts = Store.accounts(act).sortedBy { Store.villageName(act, it.tag).lowercase() }
        val current = act.currentTag()
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 16), Ui.dp(act, 8), Ui.dp(act, 16), Ui.dp(act, 4)) }
        var dialog: AlertDialog? = null
        val now = System.currentTimeMillis()

        if (accounts.isEmpty()) {
            col.addView(Ui.muted(act, "Belum ada desa. Tambahkan dengan export desa dari game.", Ui.BODY_M))
        }
        for (acc in accounts) {
            val (card, c) = Ui.card(act, padV = 10)
            val head = Ui.hbox(act)
            val sel = if (acc.tag == current) "✓ " else ""
            head.addView(Ui.text(act, sel + Store.villageName(act, acc.tag), Ui.TITLE_S), Ui.weight1())
            head.addView(Ui.flat(act, "⋯") { dialog?.dismiss(); options(act, acc.tag) })
            c.addView(head)
            val ups = Store.upgrades(act).filter {
                it.tag == acc.tag && it.kind != Kind.HELPER && it.kind != Kind.REMINDER && it.finishAt > now
            }
            val free = act.freeBuilders(acc.tag, false)
            val hall = listOfNotNull(
                acc.th.takeIf { it > 0 }?.let { "TH$it" },
                acc.bh.takeIf { it > 0 }?.let { "BH$it" },
            ).joinToString(" · ")
            val pct = Store.lastPct(act, acc.tag).takeIf { it >= 0 }?.let { " · ${Ui.pct(it.toDouble())} menuju max" } ?: ""
            c.addView(Ui.muted(act, listOf(hall, "🔨 $free bebas", "${ups.size} berjalan").filter { it.isNotBlank() }.joinToString(" · ") + pct))
            val tagLine = if (Store.villageName(act, acc.tag) != acc.tag) "${acc.tag} · " else ""
            c.addView(Ui.muted(act, "${tagLine}import ${Fmt.at(acc.exportedAt, zone)}"))
            Store.apiPlayer(act, acc.tag)?.takeIf { it.clan.isNotBlank() }?.let { p ->
                c.addView(Ui.muted(act, "🛡️ Clan ${p.clan}"))
            }
            val ps = Store.planSettings(act, acc.tag)
            val gp = if (ps.gold.builderPct > 0 || ps.gold.researchPct > 0) "🎫 Gold Pass ${ps.gold.builderPct}/${ps.gold.researchPct}%" else "🎫 tanpa Gold Pass"
            val cwl = if (ps.cwl.enabled) "⚔️ CWL" else "CWL mati"
            c.addView(Ui.muted(act, "$gp · $cwl · ${ps.strategy.label.substringBefore(" (")} / BB ${ps.bbStrategy.label}"))
            card.setOnClickListener {
                dialog?.dismiss()
                act.selectVillage(acc.tag)
            }
            col.addView(card)
        }
        val add = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 6), 0, 0) }
        add.addView(Ui.filled(act, "+ Tambah dari clipboard") { dialog?.dismiss(); act.importFromClipboard() }.apply { isAllCaps = false }, Ui.weight1())
        add.addView(Ui.outlined(act, "Tempel") { dialog?.dismiss(); act.showPasteDialog() }.apply { isAllCaps = false },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = Ui.dp(act, 8)
            })
        col.addView(add)
        col.addView(Ui.muted(act, "Desa ditambah otomatis saat Anda meng-import export dari akun lain. Notifikasi semua desa tetap berjalan.").apply {
            setPadding(Ui.dp(act, 2), Ui.dp(act, 8), Ui.dp(act, 2), 0)
            gravity = Gravity.START
        })
        dialog = MaterialAlertDialogBuilder(act)
            .setTitle("Desa Anda")
            .setView(ScrollView(act).apply { addView(col) })
            .setNegativeButton("Tutup", null)
            .show()
    }

    fun options(act: MainActivity, tag: String) {
        val name = Store.villageName(act, tag)
        val opts = arrayOf<CharSequence>(
            "✏️ Ganti nama", "⚙️ Opsi desa (strategi, Gold Pass, CWL)", "📋 Update dari clipboard", "🗑️ Hapus desa",
        )
        MaterialAlertDialogBuilder(act)
            .setTitle(name)
            .setItems(opts) { _, w ->
                when (w) {
                    0 -> rename(act, tag)
                    1 -> { act.selectVillage(tag); VillageOptions.sheet(act, tag, act.showBB) }
                    2 -> { act.selectVillage(tag); act.importFromClipboard() }
                    else -> confirmDelete(act, tag)
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    fun rename(act: MainActivity, tag: String) {
        val input = EditText(act).apply {
            setText(Store.villageName(act, tag).takeIf { it != tag } ?: "")
            hint = "mis. Akun utama"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setSingleLine(true)
        }
        val box = LinearLayout(act).apply {
            setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), 0)
            addView(input, Ui.matchWrap())
        }
        MaterialAlertDialogBuilder(act)
            .setTitle("Nama desa $tag")
            .setView(box)
            .setPositiveButton("Simpan") { _, _ ->
                Store.setVillageName(act, tag, input.text.toString().take(30))
                act.dataChanged()
            }
            .setNeutralButton("Pakai tag") { _, _ ->
                Store.setVillageName(act, tag, "")
                act.dataChanged()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    fun confirmDelete(act: MainActivity, tag: String) {
        MaterialAlertDialogBuilder(act)
            .setTitle("Hapus ${Store.villageName(act, tag)}?")
            .setMessage("Semua timer, catatan item, dan notifikasi desa ini akan dihapus dari aplikasi.")
            .setPositiveButton("Hapus") { _, _ ->
                Store.removeAccount(act, tag)
                AlarmScheduler.schedule(act)
                act.dataChanged()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
