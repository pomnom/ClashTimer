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

    /** Lembar Desa anda (pilih desa aktif, ganti nama, hapus, tambah desa). */
    fun show(act: MainActivity) = VillagesView.sheet(act)

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
