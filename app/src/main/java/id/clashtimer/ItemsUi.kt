package id.clashtimer

import android.content.DialogInterface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.BoostKind
import id.clashtimer.core.Boosts
import id.clashtimer.core.ClockTower
import id.clashtimer.core.Fmt
import id.clashtimer.core.Inst
import id.clashtimer.core.Item
import id.clashtimer.core.Lane
import id.clashtimer.core.MagicItem
import id.clashtimer.core.Planner
import java.time.ZoneId

/** Dialog untuk mencatat pemakaian potion, snack, book dan hammer. */
object ItemsUi {

    private class Group(val title: String, val items: List<MagicItem>)

    private fun groups(bb: Boolean): List<Group> {
        val potions = listOf(MagicItem.BUILDER_POTION, MagicItem.RESEARCH_POTION, MagicItem.PET_POTION, MagicItem.CLOCK_TOWER_POTION)
        return listOf(
            Group("Potion", if (bb) listOf(MagicItem.CLOCK_TOWER_POTION) + (potions - MagicItem.CLOCK_TOWER_POTION) else potions),
            Group("Snack", listOf(MagicItem.BUILDER_BITE, MagicItem.STUDY_SOUP)),
            Group("Book", MagicItem.entries.filter { it.isBook }),
            Group("Hammer", MagicItem.entries.filter { it.isHammer }),
        )
    }

    /** Efek singkat untuk kotak grid. */
    private fun effect(mi: MagicItem): String = when {
        mi.boost != null -> "${mi.boost.speedLabel} · ${Fmt.dur(mi.boost.duration)}"
        mi.isBook -> tr("selesai instan", "instant finish")
        else -> tr("+1 level", "+1 level")
    }

    fun open(act: MainActivity, tag: String) {
        val owned = Store.items(act, tag)
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val active = Boosts.merged(Store.boosts(act, tag)).filter { it.start <= now && it.end > now }
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 14), Ui.dp(act, 4), Ui.dp(act, 14), Ui.dp(act, 8)) }
        var dialog: AlertDialog? = null

        col.addView(Ui.muted(act, tr("Ketuk item yang baru Anda pakai di game — timer langsung dihitung ulang.", "Tap the item you just used in the game — timers are recalculated right away.")).apply {
            setPadding(Ui.dp(act, 2), 0, Ui.dp(act, 2), Ui.dp(act, 4))
        })
        for (g in groups(act.showBB)) {
            col.addView(Ui.text(act, g.title, Ui.TITLE_S).apply { setPadding(Ui.dp(act, 2), Ui.dp(act, 10), 0, Ui.dp(act, 6)) })
            g.items.chunked(3).forEachIndexed { r, chunk ->
                val row = Ui.hbox(act).apply {
                    gravity = Gravity.TOP
                    if (r > 0) setPadding(0, Ui.dp(act, 6), 0, 0)
                }
                for (i in 0 until 3) {
                    val mi = chunk.getOrNull(i)
                    val lp = Ui.weight1().apply { if (i > 0) marginStart = Ui.dp(act, 6) }
                    if (mi == null) {
                        row.addView(Ui.space(act), lp)
                        continue
                    }
                    val boost = mi.boost?.let { k -> active.firstOrNull { it.kind.group == k.group } }
                    row.addView(tile(act, mi, owned[mi] ?: 0, boost?.end?.let { tr("aktif s/d ${Fmt.time(it, zone)}", "active until ${Fmt.time(it, zone)}") }) {
                        dialog?.dismiss()
                        use(act, tag, mi)
                    }, lp)
                }
                col.addView(row)
            }
        }
        val actions = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 14), 0, 0) }
        actions.addView(Ui.outlined(act, tr("📝 Atur jumlah", "📝 Set amounts")) { dialog?.dismiss(); editCounts(act, tag) }.apply { isAllCaps = false }, Ui.weight1())
        actions.addView(Ui.outlined(act, tr("↩️ Batalkan catatan", "↩️ Undo records")) { dialog?.dismiss(); undo(act, tag) }.apply { isAllCaps = false },
            Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
        col.addView(actions)

        dialog = MaterialAlertDialogBuilder(act)
            .setTitle(tr("Pakai item · ${Store.villageName(act, tag)}", "Use item · ${Store.villageName(act, tag)}"))
            .setView(ScrollView(act).apply { addView(col) })
            .setNegativeButton(tr("Tutup", "Close"), null)
            .show()
    }

    /** Satu kotak grid: ikon, nama, jumlah dimiliki, efek. */
    private fun tile(act: MainActivity, mi: MagicItem, owned: Int, activeText: String?, onClick: () -> Unit): View {
        val color = when {
            activeText != null -> Ui.GOLD
            owned > 0 -> Ui.GREEN
            else -> Ui.GREY
        }
        val t = Ui.vbox(act).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = Ui.tint(color, Ui.dp(act, 12).toFloat(), if (owned > 0 || activeText != null) 0x22 else 0x12)
            setPadding(Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 4), Ui.dp(act, 8))
            isClickable = true
            setOnClickListener { onClick() }
        }
        val icon = Icons.view(act, mi.icon, 46, mi.emoji, color, round = false)
        t.addView(icon, LinearLayout.LayoutParams(Ui.dp(act, 46), Ui.dp(act, 46)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        t.addView(TextView(act).apply {
            text = mi.label
            textSize = 11.5f
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Ui.onSurface(act))
            setPadding(0, Ui.dp(act, 4), 0, 0)
        }, Ui.matchWrap())
        t.addView(TextView(act).apply {
            text = activeText ?: if (owned > 0) tr("punya $owned · ${effect(mi)}", "have $owned · ${effect(mi)}") else effect(mi)
            textSize = 10f
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(if (activeText != null || owned > 0) color else Ui.secondary(act))
        }, Ui.matchWrap())
        return t
    }

    private fun consume(act: MainActivity, tag: String, mi: MagicItem) {
        val n = Store.items(act, tag)[mi] ?: 0
        if (n > 0) Store.setItem(act, tag, mi, n - 1)
    }

    fun use(act: MainActivity, tag: String, mi: MagicItem) {
        when {
            mi.boost != null -> usePotion(act, tag, mi)
            mi.isBook -> useBook(act, tag, mi)
            else -> useHammer(act, tag, mi)
        }
    }

    private fun usePotion(act: MainActivity, tag: String, mi: MagicItem) {
        val kind = mi.boost ?: return
        boost(act, tag, kind, mi.label, kind.duration, mi)
    }

    /** Boost gratis Clock Tower (10× selama 12 + 2×level menit). */
    fun useClockTower(act: MainActivity, tag: String, level: Int) {
        val min = ClockTower.boostMinutes(level)
        if (min <= 0) return
        boost(act, tag, BoostKind.CLOCK_TOWER, tr("Clock Tower Lv$level ($min menit)", "Clock Tower Lv$level ($min min)"), min * 60_000L, null)
    }

    private fun boost(act: MainActivity, tag: String, kind: BoostKind, label: String, duration: Long, mi: MagicItem?) {
        val offsets = intArrayOf(0, 5, 10, 20, 30, 45).filter { it * 60_000L < duration + 15 * 60_000L }
        val labels = offsets.map { (if (it == 0) tr("Baru saja", "Just now") else tr("$it menit lalu", "$it min ago")) as CharSequence }.toTypedArray()
        MaterialAlertDialogBuilder(act)
            .setTitle(tr("$label mulai kapan?", "When did $label start?"))
            .setItems(labels) { _, w ->
                val now = System.currentTimeMillis()
                val at = now - offsets[w] * 60_000L
                val before = Repo.finishes(act, tag)
                Store.setBoosts(act, tag, Boosts.add(Store.boosts(act, tag), kind, at, duration))
                // Boost gratis Clock Tower: cooldown 22 jam dihitung sejak boost selesai (+ pengingat "siap lagi")
                if (kind == BoostKind.CLOCK_TOWER) Store.setClockUsed(act, tag, at + duration)
                if (mi != null) consume(act, tag, mi)
                Repo.reproject(act, tag)
                val after = Store.upgrades(act).filter { it.tag == tag }
                val zone = ZoneId.systemDefault()
                val changes = after.mapNotNull { u ->
                    val b = before[u.id] ?: return@mapNotNull null
                    if (b - u.finishAt > 60_000) "• ${u.title()}: ${Fmt.time(b, zone)} → ${Fmt.at(u.finishAt, zone)} (−${Fmt.dur(b - u.finishAt)})" else null
                }
                act.dataChanged()
                val lanes = kind.lanes.joinToString(" & ") { it.label }
                val msg = if (changes.isEmpty()) tr("Tidak ada $lanes yang sedang berjalan, jadi tidak ada timer yang berubah.", "No $lanes running, so no timers changed.")
                else tr("Waktu selesai diperbarui dan notifikasi dijadwalkan ulang:\n\n", "Finish times updated and notifications rescheduled:\n\n") + changes.joinToString("\n")
                MaterialAlertDialogBuilder(act).setTitle(tr("⚡ $label dicatat", "⚡ $label recorded")).setMessage(msg)
                    .setPositiveButton(tr("OK", "OK"), null).show()
            }
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }

    // ---------------------------------------------------------------- daftar target dengan ikon

    private class Target(val item: Item, val level: Int, val title: String, val sub: String)

    /** Dialog daftar target (ikon bangunan/hero + judul + keterangan). */
    private fun pickTarget(act: MainActivity, mi: MagicItem, targets: List<Target>, onPick: (Int) -> Unit) {
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), Ui.dp(act, 4), Ui.dp(act, 12), Ui.dp(act, 4)) }
        var dialog: AlertDialog? = null
        targets.forEachIndexed { i, t ->
            if (i > 0) col.addView(Ui.divider(act))
            val row = Ui.hbox(act).apply {
                setPadding(Ui.dp(act, 4), Ui.dp(act, 10), Ui.dp(act, 4), Ui.dp(act, 10))
                isClickable = true
                setOnClickListener {
                    dialog?.dismiss()
                    onPick(i)
                }
            }
            row.addView(Icons.view(act, Icons.forItem(t.item, t.level), 40, Icons.initials(t.item.name)))
            val c = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
            c.addView(Ui.text(act, (if (i == 0) "★ " else "") + t.title, Ui.TITLE_S))
            c.addView(Ui.muted(act, t.sub))
            row.addView(c, Ui.weight1())
            col.addView(row)
        }
        val head = Ui.hbox(act).apply { setPadding(Ui.dp(act, 24), Ui.dp(act, 18), Ui.dp(act, 24), Ui.dp(act, 4)) }
        head.addView(Icons.view(act, mi.icon, 32, mi.emoji, round = false))
        head.addView(Ui.text(act, tr("${mi.label} untuk…", "${mi.label} for…"), Ui.TITLE_M).apply { setPadding(Ui.dp(act, 10), 0, 0, 0) }, Ui.weight1())
        val wrap = Ui.vbox(act)
        wrap.addView(head)
        wrap.addView(ScrollView(act).apply { addView(col) }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ))
        dialog = MaterialAlertDialogBuilder(act)
            .setView(wrap)
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }

    private fun useBook(act: MainActivity, tag: String, mi: MagicItem) {
        val v = Repo.village(act, tag) ?: return
        val fin = Repo.finishes(act, tag)
        val now = System.currentTimeMillis()
        val running = v.insts.filter { it.upgrading && mi.fits(it.item.cat) }
            .map { it to (fin[it.uid] ?: (v.exportAt + it.remaining * 1000)) }
            .filter { it.second > now }
            .sortedByDescending { it.second }
        if (running.isEmpty()) {
            MaterialAlertDialogBuilder(act).setTitle(mi.label)
                .setMessage(tr("Tidak ada upgrade yang cocok untuk ${mi.label} yang sedang berjalan.", "No running upgrade matches ${mi.label}."))
                .setPositiveButton(tr("OK", "OK"), null).show()
            return
        }
        fun name(inst: Inst) =
            if (inst.gearing) "${inst.item.name} · Gear-up" else "${inst.item.name} → Lv ${inst.level + 1}"
        val zone = ZoneId.systemDefault()
        val targets = running.map { (inst, f) ->
            Target(inst.item, inst.level, name(inst), tr("hemat ${Fmt.dur(f - now)} · selesai ${Fmt.at(f, zone)}", "saves ${Fmt.dur(f - now)} · done ${Fmt.at(f, zone)}"))
        }
        pickTarget(act, mi, targets) { w ->
            val inst = running[w].first
            Store.setDone(act, tag, Store.done(act, tag) + (inst.uid to System.currentTimeMillis()))
            consume(act, tag, mi)
            Repo.reproject(act, tag)
            act.dataChanged()
            MaterialAlertDialogBuilder(act).setTitle(tr("📘 Dicatat", "📘 Recorded"))
                .setMessage(tr("${name(inst)} ditandai selesai. Pekerjanya sekarang dihitung bebas.", "${name(inst)} marked as done. Its worker now counts as free."))
                .setPositiveButton(tr("OK", "OK"), null).show()
        }
    }

    private fun useHammer(act: MainActivity, tag: String, mi: MagicItem) {
        val v = Repo.village(act, tag) ?: return
        val gd = Repo.gameData(act)
        val ps = Store.planSettings(act, tag)
        val cands = listOf(Lane.BUILDER, Lane.LAB)
            .flatMap { Planner.dedupe(Planner.candidates(v, gd, it, ps.gold, includeHall = true, skip = ps.planSkip)) }
            .filter { !it.isNew && mi.fits(it.item.cat) }
            .sortedByDescending { it.duration }
            .take(40)
        if (cands.isEmpty()) {
            MaterialAlertDialogBuilder(act).setTitle(mi.label)
                .setMessage(tr("Tidak ada target yang tersedia (sudah max atau sedang di-upgrade).", "No targets available (already max or being upgraded)."))
                .setPositiveButton(tr("OK", "OK"), null).show()
            return
        }
        val targets = cands.map { c ->
            Target(c.item, c.fromLevel, "${c.item.name} → Lv ${c.toLevel}",
                tr("hemat ${Fmt.durSec(c.duration)}", "saves ${Fmt.durSec(c.duration)}") + if (c.cost > 0) " · ${Fmt.money(c.cost)} ${Fmt.resource(c.item.resource)}" else "")
        }
        pickTarget(act, mi, targets) { w ->
            val c = cands[w]
            Store.setBumps(act, tag, Store.bumps(act, tag) + Triple(c.item.id, c.fromLevel, System.currentTimeMillis()))
            consume(act, tag, mi)
            act.dataChanged()
            MaterialAlertDialogBuilder(act).setTitle(tr("🔨 Dicatat", "🔨 Recorded"))
                .setMessage(tr("${c.item.name} sekarang dihitung Lv ${c.toLevel}. Saat import berikutnya, data level diambil lagi dari game.", "${c.item.name} now counts as Lv ${c.toLevel}. On the next import, levels are read from the game again."))
                .setPositiveButton(tr("OK", "OK"), null).show()
        }
    }

    /** Atur jumlah item yang dimiliki dengan tombol − / +. */
    fun editCounts(act: MainActivity, tag: String) {
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 16), Ui.dp(act, 4), Ui.dp(act, 8), Ui.dp(act, 8)) }
        for (g in groups(act.showBB)) {
            col.addView(Ui.text(act, g.title, Ui.TITLE_S).apply { setPadding(Ui.dp(act, 4), Ui.dp(act, 10), 0, Ui.dp(act, 2)) })
            for (mi in g.items) {
                val row = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 2), 0, Ui.dp(act, 2)) }
                row.addView(Icons.view(act, mi.icon, 32, mi.emoji, round = false))
                row.addView(Ui.text(act, mi.label, Ui.BODY_M).apply { setPadding(Ui.dp(act, 10), 0, 0, 0) }, Ui.weight1())
                val count = TextView(act).apply {
                    text = (Store.items(act, tag)[mi] ?: 0).toString()
                    minWidth = Ui.dp(act, 32)
                    gravity = Gravity.CENTER
                    textSize = 15f
                }
                fun change(d: Int) {
                    val n = ((Store.items(act, tag)[mi] ?: 0) + d).coerceIn(0, 999)
                    Store.setItem(act, tag, mi, n)
                    count.text = n.toString()
                }
                row.addView(Ui.flat(act, "−") { change(-1) })
                row.addView(count)
                row.addView(Ui.flat(act, "+") { change(1) })
                col.addView(row)
            }
        }
        val scroll = ScrollView(act).apply { addView(col) }
        MaterialAlertDialogBuilder(act)
            .setTitle(tr("Item yang dimiliki · ${Store.villageName(act, tag)}", "Items owned · ${Store.villageName(act, tag)}"))
            .setView(scroll)
            .setPositiveButton(tr("Selesai", "Done"), null)
            .setOnDismissListener { act.dataChanged() }
            .show()
    }

    /** Hapus catatan item (kalau salah catat). */
    private fun undo(act: MainActivity, tag: String) {
        val zone = ZoneId.systemDefault()
        val gd = Repo.gameData(act)
        val names = Store.upgrades(act).filter { it.tag == tag }.associate { it.id to it.title() }
        class Entry(val label: String, val remove: () -> Unit)
        val entries = ArrayList<Entry>()
        for (b in Store.boosts(act, tag)) {
            entries += Entry("⚡ ${b.kind.label} · ${Fmt.at(b.start, zone)}") {
                Store.setBoosts(act, tag, Store.boosts(act, tag).filter { it != b })
            }
        }
        for ((uid, at) in Store.done(act, tag)) {
            entries += Entry(tr("✅ ${names[uid] ?: uid.substringAfterLast('|')} selesai · ${Fmt.at(at, zone)}", "✅ ${names[uid] ?: uid.substringAfterLast('|')} done · ${Fmt.at(at, zone)}")) {
                Store.setDone(act, tag, Store.done(act, tag) - uid)
            }
        }
        for ((uid, at) in Store.cancelled(act, tag)) {
            entries += Entry(tr("✖️ ${uid.split('|').getOrNull(2)?.toIntOrNull()?.let { gd.name(it) } ?: uid} dibatalkan · ${Fmt.at(at, zone)}", "✖️ ${uid.split('|').getOrNull(2)?.toIntOrNull()?.let { gd.name(it) } ?: uid} cancelled · ${Fmt.at(at, zone)}")) {
                Store.setCancelled(act, tag, Store.cancelled(act, tag) - uid)
            }
        }
        for (bm in Store.bumps(act, tag)) {
            entries += Entry("🔨 ${gd.name(bm.first)} Lv${bm.second}→${bm.second + 1} · ${Fmt.at(bm.third, zone)}") {
                Store.setBumps(act, tag, Store.bumps(act, tag).filter { it != bm })
            }
        }
        if (entries.isEmpty()) {
            MaterialAlertDialogBuilder(act).setTitle(tr("Belum ada catatan", "No records yet"))
                .setMessage(tr("Belum ada potion, book, hammer, atau tanda selesai yang dicatat sejak import terakhir.", "No potions, books, hammers, or done marks recorded since the last import."))
                .setPositiveButton(tr("OK", "OK"), null).show()
            return
        }
        MaterialAlertDialogBuilder(act)
            .setTitle(tr("Hapus catatan mana?", "Delete which record?"))
            .setItems(entries.map { it.label as CharSequence }.toTypedArray()) { _: DialogInterface, w: Int ->
                entries[w].remove()
                Repo.reproject(act, tag)
                act.dataChanged()
            }
            .setNegativeButton(tr("Tutup", "Close"), null)
            .show()
    }
}
