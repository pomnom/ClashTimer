package id.clashtimer

import android.view.View
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Tahan lama sebuah item (Beranda, Rencana, Progres): Prioritaskan / Sembunyikan untuk desa ini.
 * Berlaku untuk semua level & semua bangunan dengan nama yang sama. Item yang disembunyikan hilang
 * dari saran, rencana, widget & notifikasi, tetapi tetap dihitung di progres menuju max.
 */
object ItemMenu {

    fun attach(act: MainActivity, view: View, tag: String, itemId: Int?) {
        if (itemId == null || itemId <= 0) return
        view.isLongClickable = true
        view.setOnLongClickListener {
            show(act, tag, itemId)
            true
        }
    }

    private fun nameOf(act: MainActivity, id: Int) = Repo.gameData(act).name(id)

    fun show(act: MainActivity, tag: String, itemId: Int) {
        val name = nameOf(act, itemId)
        val pins = Store.pins(act, tag)
        val hides = Store.hides(act, tag)
        val pinned = itemId in pins
        val hidden = itemId in hides
        val opts = arrayOf<CharSequence>(
            if (pinned) "📌 Lepas prioritas" else "📌 Prioritaskan — didahulukan di saran & rencana",
            if (hidden) "👁️ Tampilkan lagi di saran" else "🙈 Sembunyikan dari saran & rencana",
            "⚙️ Kelola semua prioritas & item tersembunyi",
        )
        MaterialAlertDialogBuilder(act)
            .setTitle("$name · ${Store.villageName(act, tag)}")
            .setItems(opts) { _, w ->
                when (w) {
                    0 -> {
                        Store.setPins(act, tag, if (pinned) pins - itemId else pins + itemId)
                        if (!pinned) Store.setHides(act, tag, hides - itemId)
                        toast(act, if (pinned) "$name tidak lagi diprioritaskan" else "📌 $name didahulukan")
                    }
                    1 -> {
                        Store.setHides(act, tag, if (hidden) hides - itemId else hides + itemId)
                        if (!hidden) Store.setPins(act, tag, pins - itemId)
                        toast(act, if (hidden) "$name tampil lagi di saran" else "🙈 $name disembunyikan (tetap dihitung di progres)")
                    }
                    else -> { manage(act, tag); return@setItems }
                }
                act.dataChanged()
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    /** Daftar item yang diprioritaskan & disembunyikan; ketuk untuk melepas. */
    fun manage(act: MainActivity, tag: String) {
        val pins = Store.pins(act, tag).sortedBy { nameOf(act, it) }
        val hides = Store.hides(act, tag).sortedBy { nameOf(act, it) }
        if (pins.isEmpty() && hides.isEmpty()) {
            MaterialAlertDialogBuilder(act)
                .setTitle("📌 Prioritas & tersembunyi")
                .setMessage("Belum ada. Tahan lama sebuah upgrade di Dashboard, Tukang, Lab, atau Progres untuk " +
                    "mendahulukannya atau menyembunyikannya dari saran desa ${Store.villageName(act, tag)}.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val labels = pins.map { "📌 ${nameOf(act, it)} — lepas prioritas" as CharSequence } +
            hides.map { "🙈 ${nameOf(act, it)} — tampilkan lagi" as CharSequence }
        MaterialAlertDialogBuilder(act)
            .setTitle("📌 Prioritas & tersembunyi · ${Store.villageName(act, tag)}")
            .setItems(labels.toTypedArray()) { _, w ->
                if (w < pins.size) Store.setPins(act, tag, Store.pins(act, tag) - pins[w])
                else Store.setHides(act, tag, Store.hides(act, tag) - hides[w - pins.size])
                act.dataChanged()
                manage(act, tag)
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    private fun toast(act: MainActivity, msg: String) = Toast.makeText(act, msg, Toast.LENGTH_SHORT).show()
}
