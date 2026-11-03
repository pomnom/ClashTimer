package id.clashtimer

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import id.clashtimer.core.Cat
import id.clashtimer.core.Fmt
import id.clashtimer.core.GameData
import id.clashtimer.core.LaneSlot
import id.clashtimer.core.MagicItem
import id.clashtimer.core.Scored

/** Lembar bawah bergaya mockup: pegangan, judul + tombol tutup, isi bisa digulir. */
object Sheets {

    fun open(
        act: MainActivity, title: CharSequence?, subtitle: CharSequence? = null, help: Help.Topic? = null,
        fill: (col: LinearLayout, dismiss: () -> Unit) -> Unit,
    ): BottomSheetDialog {
        val dialog = BottomSheetDialog(act)
        val r = Ui.dp(act, 22).toFloat()
        val root = Ui.vbox(act).apply {
            background = GradientDrawable().apply {
                setColor(Ui.SURFACE)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            setPadding(Ui.dp(act, 16), Ui.dp(act, 8), Ui.dp(act, 16), Ui.dp(act, 20))
        }
        root.addView(View(act).apply { background = Ui.rounded(Ui.OUTLINE, Ui.dp(act, 2).toFloat()) },
            LinearLayout.LayoutParams(Ui.dp(act, 36), Ui.dp(act, 4)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = Ui.dp(act, 10)
            })
        if (title != null) {
            val head = Ui.hbox(act).apply { gravity = Gravity.TOP }
            val tc = Ui.vbox(act)
            tc.addView(Ui.text(act, title, Ui.TITLE_M).apply {
                textSize = 19f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.TEXT)
            })
            if (subtitle != null) tc.addView(Ui.muted(act, subtitle).apply { textSize = 13f; setPadding(0, Ui.dp(act, 2), 0, 0) })
            head.addView(tc, Ui.weight1())
            if (help != null) head.addView(Help.button(act, help))
            head.addView(Ui.icon(act, R.drawable.ic_close, 22, Ui.TEXT2, tr("Tutup", "Close")) { dialog.dismiss() })
            root.addView(head, Ui.matchWrap())
        }
        val col = Ui.vbox(act)
        root.addView(col, Ui.matchWrap())
        fill(col) { dialog.dismiss() }
        val scroll = ScrollView(act).apply { addView(root) }
        dialog.setContentView(scroll)
        dialog.setOnShowListener { (scroll.parent as? View)?.setBackgroundColor(Color.TRANSPARENT) }
        runCatching {
            dialog.behavior.skipCollapsed = true
            dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        dialog.show()
        return dialog
    }

    /** Baris aksi di lembar: judul (+ keterangan kecil), garis bawah tipis. */
    fun action(act: MainActivity, title: String, sub: String? = null, color: Int = Ui.TEXT, last: Boolean = false, onClick: () -> Unit): View {
        val wrap = Ui.vbox(act)
        val row = Ui.vbox(act).apply {
            minimumHeight = Ui.dp(act, 52)
            gravity = Gravity.CENTER_VERTICAL
            setPadding(Ui.dp(act, 2), Ui.dp(act, 10), Ui.dp(act, 2), Ui.dp(act, 10))
            isClickable = true
            setOnClickListener { onClick() }
            Ui.ripple(this)
        }
        row.addView(Ui.text(act, title, Ui.BODY_M).apply {
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color)
        })
        if (sub != null) row.addView(Ui.muted(act, sub).apply { textSize = 12f })
        wrap.addView(row, Ui.matchWrap())
        if (!last) wrap.addView(View(act).apply { setBackgroundColor(Ui.LINE) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 1)))
        return wrap
    }

    /**
     * Hitung mundur yang terus berjalan selama [tv] tampil (tidak bergantung pada penyegaran layar di
     * belakang lembar).
     */
    fun live(tv: TextView, target: Long, text: (Long) -> String) {
        val r = object : Runnable {
            override fun run() {
                val left = target - System.currentTimeMillis()
                tv.text = text(left)
                if (left > 0) tv.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000))
            }
        }
        tv.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) { tv.removeCallbacks(r); r.run() }
            override fun onViewDetachedFromWindow(v: View) { tv.removeCallbacks(r) }
        })
    }

    /** Kotak angka kecil: "Sisa / 1h 22j". */
    fun stat(act: MainActivity, label: String, value: String, valueColor: Int = Ui.TEXT): Pair<LinearLayout, TextView> {
        val box = Ui.panel(act, padH = 10, padV = 8, color = Ui.SURFACE2, radiusDp = 12)
        box.addView(Ui.muted(act, label).apply { textSize = 11f })
        val v = Ui.monoText(act, value, 15f).apply { setTextColor(valueColor); setPadding(0, Ui.dp(act, 3), 0, 0) }
        box.addView(v)
        return box to v
    }

    /** Pil filter yang bisa dipilih. */
    fun chip(act: MainActivity, label: String, on: Boolean, onClick: () -> Unit): TextView = TextView(act).apply {
        text = label
        textSize = 13f
        gravity = Gravity.CENTER
        setTextColor(if (on) Ui.FREE_TEXT else Ui.TEXT)
        if (on) setTypeface(typeface, Typeface.BOLD)
        background = if (on) Ui.rounded(Ui.SELECTED_BG, Ui.dp(act, 16).toFloat())
        else Ui.rounded(Color.TRANSPARENT, Ui.dp(act, 16).toFloat(), Ui.OUTLINE, Ui.dp(act, 1))
        setPadding(Ui.dp(act, 14), 0, Ui.dp(act, 14), 0)
        minHeight = Ui.dp(act, 34)
        isClickable = true
        setOnClickListener { onClick() }
    }

    /** "Hero", "Pertahanan", "Troop", … */
    fun catLabel(cat: Cat?, type: String?): String = when {
        cat == Cat.HERO || cat == Cat.BB_HERO -> "Hero"
        cat == Cat.PET -> "Pet"
        cat == Cat.TROOP || cat == Cat.BB_TROOP -> "Troop"
        cat == Cat.SPELL -> "Spell"
        cat == Cat.TRAP || cat == Cat.BB_TRAP -> tr("Jebakan", "Trap")
        type == "Defense" -> tr("Pertahanan", "Defense")
        type == "Wall" -> tr("Tembok", "Wall")
        type == "Resource" -> tr("Sumber daya", "Resource")
        type == "Army" -> tr("Pasukan", "Army")
        else -> tr("Bangunan", "Building")
    }
}

/** Detail satu upgrade yang berjalan: progres, sisa waktu, lalu aksi. */
object DetailSheet {

    fun show(act: MainActivity, u: Upgrade) {
        val now = System.currentTimeMillis()
        val zone = act.zoneId()
        val gd = Repo.gameData(act)
        val itemId = Repo.itemIdOf(u.id)
        val item = itemId?.let { gd.item(it) }
        val gear = u.id.contains("|gear|")
        val done = u.finishAt <= now
        val lab = Sessions.isLabKind(u.kind)
        val label = Sessions.labels(Store.upgrades(act).filter { it.tag == u.tag }, now)[u.id]
        val color = if (lab) Ui.LAB else Ui.BUSY

        Sheets.open(act, null) { col, dismiss ->
            val head = Ui.hbox(act)
            val iconLevel = if (gear) u.targetLevel else (u.targetLevel - 1).coerceAtLeast(1)
            head.addView(Ui.gameIcon(act, Icons.forItem(item, iconLevel), u.name, 64, label?.let { Sessions.badge(act, it, lab) }))
            val tc = Ui.vbox(act).apply { setPadding(Ui.dp(act, 14), 0, 0, 0) }
            tc.addView(Ui.text(act, u.title(), Ui.TITLE_M).apply {
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.TEXT)
            })
            val lv = item?.lv(u.targetLevel)
            val cost = if (!gear && lv != null && lv.cost > 0) " · ${Fmt.money(lv.cost)} ${Fmt.resource(item.resource)}" else ""
            val what = if (gear) "Gear-up" else Sheets.catLabel(item?.cat, item?.type)
            tc.addView(Ui.muted(act, what + cost + if (u.extra) " · Goblin (event)" else "").apply { textSize = 13f })
            head.addView(tc, Ui.weight1())
            col.addView(head, Ui.matchWrap().apply { bottomMargin = Ui.dp(act, 14) })

            val frac = HomeView.progress(u, gd, now)
            if (!done && frac != null) {
                val pr = Ui.hbox(act)
                pr.addView(Ui.muted(act, tr("${(frac * 100).toInt()}% selesai", "${(frac * 100).toInt()}% done")).apply { textSize = 13f }, Ui.weight1())
                helperNote(act, u)?.let { pr.addView(Ui.muted(act, it).apply { textSize = 13f }) }
                col.addView(pr)
                col.addView(Ui.thinBar(act, frac, color).apply {
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 6)).apply { topMargin = Ui.dp(act, 6) }
                })
            }

            val grid = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 14), 0, 0) }
            fun add(p: Pair<LinearLayout, TextView>) {
                grid.addView(p.first, Ui.weight1().apply { if (grid.childCount > 0) marginStart = Ui.dp(act, 8) })
            }
            if (done) add(Sheets.stat(act, "Status", tr("Selesai", "Done"), Ui.FREE))
            else {
                val sisa = Sheets.stat(act, tr("Sisa", "Left"), act.countdown(u.finishAt - now))
                Sheets.live(sisa.second, u.finishAt) { if (it > 0) act.countdown(it) else "selesai" }
                add(sisa)
            }
            add(Sheets.stat(act, tr("Selesai", "Done"), HomeView.short(u.finishAt, now, zone)))
            val game = u.gameFinishAt(now)
            if (!done && game - u.finishAt >= 60_000L && game > now) {
                val g = Sheets.stat(act, tr("Di game", "In game"), act.countdown(game - now), Ui.TEXT2)
                Sheets.live(g.second, game) { if (it > 0) act.countdown(it) else "selesai" }
                add(g)
            }
            col.addView(grid, Ui.matchWrap())
            if (!done && game - u.finishAt >= 60_000L) {
                val next = u.helperSteps.firstOrNull { it.first > now }?.first
                col.addView(Ui.muted(act, tr("Waktu di aplikasi sudah memotong sesi helper", "App time already includes the helper session") +
                    (next?.let { tr(" berikutnya ${HomeView.short(it, now, zone)}", " next ${HomeView.short(it, now, zone)}") } ?: "") + ".").apply {
                    textSize = 12f
                    setPadding(0, Ui.dp(act, 8), 0, 0)
                })
            }
            if (u.note.isNotBlank() && !u.helped) col.addView(Ui.muted(act, u.note).apply { textSize = 12f; setPadding(0, Ui.dp(act, 6), 0, 0) })

            val acts = Ui.vbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
            val heroItem = item?.cat == Cat.HERO || item?.cat == Cat.BB_HERO
            acts.addView(Sheets.action(act, if (done) tr("Tandai selesai", "Mark done") else tr("Tandai selesai sekarang", "Mark done now"),
                if (done) tr("upgrade sudah selesai di game", "the upgrade already finished in the game") else if (heroItem) tr("gem atau Book of Heroes", "gems or Book of Heroes") else tr("gem, book, atau sudah selesai di game", "gems, book, or already done in the game")) {
                dismiss(); act.markUpgrade(u, true)
            })
            if (!done && u.kind != Kind.REMINDER) {
                val potion = potionFor(u.kind)
                val owned = potion?.let { Store.items(act, u.tag)[it] } ?: 0
                acts.addView(Sheets.action(act, tr("Pakai potion", "Use potion"),
                    potion?.let { "${it.label}" + if (owned > 0) tr(" · punya $owned", " · owned $owned") else "" }) {
                    dismiss(); act.openItems(u.tag)
                })
            }
            if (!done && u.kind != Kind.REMINDER && u.kind != Kind.HELPER && Store.exportText(act, u.tag) != null) {
                acts.addView(Sheets.action(act, tr("Ubah sisa waktu", "Edit time left"), tr("isi sisa waktu yang terlihat di game", "enter the time left shown in the game")) {
                    dismiss(); editRemaining(act, u)
                })
            }
            if (itemId != null && !gear) {
                val pins = Store.pins(act, u.tag)
                val pinned = itemId in pins
                val name = item?.name ?: u.name
                acts.addView(Sheets.action(act, if (pinned) tr("Lepas prioritas $name", "Unprioritize $name") else tr("Prioritaskan $name", "Prioritize $name"),
                    if (pinned) null else tr("didahulukan di saran & rencana", "put first in suggestions & plan")) {
                    dismiss()
                    Store.setPins(act, u.tag, if (pinned) pins - itemId else pins + itemId)
                    if (!pinned) Store.setHides(act, u.tag, Store.hides(act, u.tag) - itemId)
                    act.dataChanged()
                    act.snack(if (pinned) tr("$name tidak lagi diprioritaskan", "$name no longer prioritized") else tr("$name didahulukan", "$name prioritized"))
                })
            }
            acts.addView(Sheets.action(act, tr("Batalkan upgrade ini", "Cancel this upgrade"), color = Ui.IDLE, last = true) {
                dismiss(); act.markUpgrade(u, false)
            })
            col.addView(acts, Ui.matchWrap())
        }
    }

    /** Koreksi sisa waktu: isi seperti yang terlihat di game (hari, jam, menit). Berlaku sampai import berikutnya. */
    fun editRemaining(act: MainActivity, u: Upgrade) {
        val now = System.currentTimeMillis()
        val left = ((u.gameFinishAt(now) - now) / 1000).coerceAtLeast(0)
        val row = Ui.hbox(act).apply { setPadding(Ui.dp(act, 20), Ui.dp(act, 8), Ui.dp(act, 20), 0) }
        fun field(label: String, value: Long): android.widget.EditText {
            val c = Ui.vbox(act)
            val e = android.widget.EditText(act).apply {
                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                setText(value.toString())
                gravity = Gravity.CENTER
                setSelectAllOnFocus(true)
                textSize = 20f
            }
            c.addView(e, Ui.matchWrap())
            c.addView(Ui.muted(act, label).apply { gravity = Gravity.CENTER }, Ui.matchWrap())
            row.addView(c, Ui.weight1().apply { if (row.childCount > 0) marginStart = Ui.dp(act, 8) })
            return e
        }
        val d = field("hari", left / 86_400)
        val h = field("jam", left % 86_400 / 3600)
        val m = field("menit", left % 3600 / 60)
        val box = Ui.vbox(act)
        box.addView(row)
        box.addView(Ui.muted(act, tr("Lihat sisa waktu upgrade ini di game lalu isi di sini. Helper & potion berikutnya tetap ", "Check this upgrade's time left in the game and enter it here. Upcoming helpers & potions still ") +
            tr("diperhitungkan. Berlaku sampai import berikutnya.", "count. Applies until the next import.")).apply { setPadding(Ui.dp(act, 24), Ui.dp(act, 10), Ui.dp(act, 24), 0) })
        com.google.android.material.dialog.MaterialAlertDialogBuilder(act)
            .setTitle(tr("Sisa waktu · ${u.title()}", "Time left · ${u.title()}"))
            .setView(box)
            .setPositiveButton(tr("Simpan", "Save")) { _, _ ->
                fun n(e: android.widget.EditText) = e.text.toString().trim().toLongOrNull()?.coerceAtLeast(0) ?: 0L
                val sec = n(d) * 86_400 + n(h) * 3600 + n(m) * 60
                if (sec <= 0) {
                    act.markUpgrade(u, true)
                    return@setPositiveButton
                }
                Store.setFix(act, u.tag, u.id, System.currentTimeMillis(), sec)
                Repo.reproject(act, u.tag)
                act.dataChanged()
                val fin = Store.upgrades(act).firstOrNull { it.id == u.id }?.finishAt
                act.snack(tr("Sisa waktu diperbarui", "Time left updated") + (fin?.let { tr(" · selesai ${HomeView.short(it, System.currentTimeMillis(), act.zoneId())}", " · done ${HomeView.short(it, System.currentTimeMillis(), act.zoneId())}") } ?: ""))
            }
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }

    private fun potionFor(k: Kind): MagicItem? = when {
        k == Kind.BUILDER -> MagicItem.BUILDER_POTION
        k == Kind.LAB -> MagicItem.RESEARCH_POTION
        k == Kind.PET -> MagicItem.PET_POTION
        k == Kind.BB_BUILDER || k == Kind.STAR_LAB -> MagicItem.CLOCK_TOWER_POTION
        else -> null
    }

    /** "dibantu Apprentice +3j/hari". */
    private fun helperNote(act: MainActivity, u: Upgrade): String? {
        if (!u.helped) return null
        val lab = u.kind == Kind.LAB
        val name = if (lab) "Lab Assistant" else "Apprentice"
        val lvl = act.planFor(u.tag)?.village?.helper(if (lab) GameData.LAB_ASSISTANT else GameData.APPRENTICE)?.level
        return tr("dibantu $name", "helped by $name") + (lvl?.let { tr(" +${it}j/hari", " +${it}h/day") } ?: "")
    }
}

/** Pilih upgrade untuk pekerja yang bebas (pengganti dialog "Lihat opsi"). */
object PickSheet {
    private enum class Filter(private val id: String, private val en: String) {
        ALL("Semua", "All"), WAKE("Selesai pas bangun", "Done at wake-up"), HERO("Hero", "Hero"), DEF("Pertahanan", "Defense"), CHEAP("Murah", "Cheap");
        val label: String get() = tr(id, en)
    }

    /** [label] = pekerja ("B4", "Lab"); [canStart] = false untuk pekerja yang baru bebas nanti. */
    fun show(act: MainActivity, tag: String, slot: LaneSlot, label: String?, canStart: Boolean = true) {
        val zone = act.zoneId()
        val now = System.currentTimeMillis()
        val schedule = Store.planSettings(act, tag).schedule
        val all = slot.ranked.take(60)
        val who = label ?: slot.lane.label
        val title = if (canStart) tr("$who bebas — pilih yang Anda mulai", "$who free — pick what you start") else tr("Opsi saat $who bebas ${HomeView.short(slot.start, now, zone)}", "Options when $who is free ${HomeView.short(slot.start, now, zone)}")
        var filter = Filter.ALL
        Sheets.open(act, title, tr("Paling atas = paling cepat menuju target rencana", "Top = fastest toward the plan target")) { col, dismiss ->
            val chips = Ui.hbox(act)
            val list = Ui.vbox(act)
            fun render() {
                chips.removeAllViews()
                for (f in Filter.entries) {
                    if (f == Filter.HERO && all.none { it.c.item.cat == Cat.HERO || it.c.item.cat == Cat.BB_HERO }) continue
                    if (f == Filter.DEF && all.none { isDefense(it) }) continue
                    chips.addView(Sheets.chip(act, f.label, f == filter) { filter = f; render() },
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, Ui.dp(act, 34)).apply { marginEnd = Ui.dp(act, 8) })
                }
                list.removeAllViews()
                val minCost = all.minOfOrNull { it.cost } ?: 0
                val shown = when (filter) {
                    Filter.ALL -> all
                    Filter.WAKE -> all.filter { Sessions.outcome(it, schedule, now, zone).second == 1 }
                    Filter.HERO -> all.filter { it.c.item.cat == Cat.HERO || it.c.item.cat == Cat.BB_HERO }
                    Filter.DEF -> all.filter { isDefense(it) }
                    Filter.CHEAP -> all.sortedBy { it.cost }.take(15).filter { it.cost <= maxOf(minCost * 4, 1) || it.cost == 0L }
                }
                val best = all.firstOrNull()
                shown.forEach { p ->
                    list.addView(row(act, tag, p, p === best, schedule, now, zone) {
                        if (canStart) {
                            dismiss()
                            act.startPick(tag, p)
                        }
                    })
                }
                if (shown.isEmpty()) list.addView(Ui.muted(act, if (all.isEmpty()) tr("Tidak ada upgrade yang tersedia.", "No upgrades available.") else tr("Tidak ada yang cocok dengan filter ini.", "Nothing matches this filter."), Ui.BODY_M).apply {
                    setPadding(Ui.dp(act, 4), Ui.dp(act, 16), 0, Ui.dp(act, 16))
                })
            }
            render()
            col.addView(HorizontalScrollView(act).apply {
                isHorizontalScrollBarEnabled = false
                addView(chips)
            }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12); bottomMargin = Ui.dp(act, 4) })
            col.addView(list, Ui.matchWrap())
        }
    }

    private fun isDefense(p: Scored) = p.c.item.type == "Defense" || p.c.item.cat == Cat.TRAP || p.c.item.cat == Cat.BB_TRAP

    private fun row(
        act: MainActivity, tag: String, p: Scored, best: Boolean, schedule: id.clashtimer.core.OfflineSchedule, now: Long, zone: java.time.ZoneId,
        onClick: () -> Unit,
    ): View {
        val c = p.c
        val row = Ui.hbox(act).apply {
            setPadding(Ui.dp(act, 10), Ui.dp(act, 12), Ui.dp(act, 10), Ui.dp(act, 12))
            if (best) background = Ui.rounded(Ui.FREE_BG, Ui.dp(act, 12).toFloat())
            isClickable = true
            setOnClickListener { onClick() }
            Ui.ripple(this)
        }
        ItemMenu.attach(act, row, tag, c.item.id)
        row.addView(Ui.gameIcon(act, Icons.forItem(c.item, c.fromLevel), c.item.name, 48))
        val col = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
        val top = Ui.hbox(act)
        val name = Ui.text(act, c.title, Ui.BODY_M).apply {
            textSize = 15f
            setTextColor(Ui.TEXT)
            if (best) setTypeface(typeface, Typeface.BOLD)
        }
        if (best) top.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT).apply { weight = 1f; width = 0 })
        else top.addView(name, Ui.weight1())
        if (best) top.addView(TextView(act).apply {
            text = tr("TERBAIK", "BEST")
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.ON_FREE)
            background = Ui.rounded(Ui.FREE, Ui.dp(act, 6).toFloat())
            setPadding(Ui.dp(act, 6), Ui.dp(act, 1), Ui.dp(act, 6), Ui.dp(act, 1))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(act, 8) })
        col.addView(top)
        col.addView(Ui.muted(act, HomeView.meta(p)).apply { textSize = 13f; setPadding(0, Ui.dp(act, 2), 0, 0) })
        val (txt, kind) = Sessions.outcome(p, schedule, now, zone)
        col.addView(TextView(act).apply {
            text = txt
            textSize = 12f
            setTextColor(when (kind) { 1 -> Ui.FREE_TEXT; -1 -> Ui.IDLE; else -> Ui.TEXT2 })
            setPadding(0, Ui.dp(act, 2), 0, 0)
        })
        HomeView.tags(p)?.let { col.addView(Ui.muted(act, it).apply { textSize = 12f; setTextColor(Ui.BUSY) }) }
        row.addView(col, Ui.weight1())
        return Ui.vbox(act).apply {
            addView(row, Ui.matchWrap())
            if (!best) addView(View(act).apply { setBackgroundColor(Ui.LINE) },
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(act, 1)))
            else (row.layoutParams as LinearLayout.LayoutParams).setMargins(0, Ui.dp(act, 4), 0, Ui.dp(act, 4))
        }
    }
}

/** Tambah / update desa: kartu clipboard bila ada, lalu 3 langkah export. */
object ImportUi {
    const val COC_PACKAGE = "com.supercell.clashofclans"

    fun show(act: MainActivity) {
        Sheets.open(act, tr("Tambah / update desa", "Add / update village"), help = Help.Topic.VILLAGES) { col, dismiss ->
            act.clipCard(col) { dismiss() }
            val steps = Ui.panel(act, padH = 16, padV = 14, color = Ui.SURFACE2)
            steps.addView(Ui.text(act, tr("Desa baru dalam 3 langkah", "New village in 3 steps"), Ui.TITLE_S).apply {
                textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT)
            })
            val items = listOf(
                tr("Buka Clash of Clans dengan akun itu", "Open Clash of Clans with that account") to tr("Settings → More Settings → paling bawah", "Settings → More Settings → at the bottom"),
                tr("Ketuk Export village data → Copy", "Tap Export village data → Copy") to tr("isinya teks JSON, tidak berisi kata sandi", "it is JSON text, no password inside"),
                tr("Kembali ke Clash Timer", "Return to Clash Timer") to tr("export langsung terdeteksi; atau ketuk Tempel di bawah", "the export is detected right away; or tap Paste below"),
            )
            items.forEachIndexed { i, (a, b) ->
                val r = Ui.hbox(act).apply { gravity = Gravity.TOP; setPadding(0, Ui.dp(act, 12), 0, 0) }
                r.addView(TextView(act).apply {
                    text = "${i + 1}"
                    textSize = 13f
                    gravity = Gravity.CENTER
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Ui.FREE_TEXT)
                    background = Ui.rounded(Ui.SELECTED_BG, Ui.dp(act, 13).toFloat())
                }, LinearLayout.LayoutParams(Ui.dp(act, 26), Ui.dp(act, 26)))
                val t = Ui.vbox(act).apply { setPadding(Ui.dp(act, 12), 0, 0, 0) }
                t.addView(Ui.text(act, a, Ui.BODY_M).apply { textSize = 15f; setTextColor(Ui.TEXT) })
                t.addView(Ui.muted(act, b).apply { textSize = 13f })
                r.addView(t, Ui.weight1())
                steps.addView(r)
            }
            val launch = act.packageManager.getLaunchIntentForPackage(COC_PACKAGE)
            if (launch != null) steps.addView(Ui.primary(act, tr("Buka Clash of Clans", "Open Clash of Clans")) {
                dismiss()
                runCatching { act.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 14) })
            col.addView(steps, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })

            val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 12), 0, 0) }
            r.addView(Ui.tonal(act, tr("Tempel dari clipboard", "Paste from clipboard")) { dismiss(); act.importFromClipboard() }, Ui.weight1())
            r.addView(Ui.secondary(act, tr("Tempel manual", "Paste manually")) { dismiss(); act.showPasteDialog() },
                Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
            col.addView(r, Ui.matchWrap())
            col.addView(Ui.muted(act, tr("Pintasan: tahan ikon Clash Timer → Update desa, atau tile \"Update desa\" di Quick Settings.", "Shortcut: hold the Clash Timer icon → Update village, or the \"Update village\" tile in Quick Settings.")).apply {
                textSize = 13f
                setPadding(Ui.dp(act, 4), Ui.dp(act, 14), Ui.dp(act, 4), 0)
            })
        }
    }
}
