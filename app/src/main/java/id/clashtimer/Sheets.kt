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
            head.addView(Ui.icon(act, R.drawable.ic_close, 22, Ui.TEXT2, "Tutup") { dialog.dismiss() })
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
        cat == Cat.TRAP || cat == Cat.BB_TRAP -> "Jebakan"
        type == "Defense" -> "Pertahanan"
        type == "Wall" -> "Tembok"
        type == "Resource" -> "Sumber daya"
        type == "Army" -> "Pasukan"
        else -> "Bangunan"
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
                pr.addView(Ui.muted(act, "${(frac * 100).toInt()}% selesai").apply { textSize = 13f }, Ui.weight1())
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
            if (done) add(Sheets.stat(act, "Status", "Selesai", Ui.FREE))
            else {
                val sisa = Sheets.stat(act, "Sisa", act.countdown(u.finishAt - now))
                act.registerCountdown(sisa.second, u.finishAt, clockStyle = false)
                add(sisa)
            }
            add(Sheets.stat(act, "Selesai", HomeView.short(u.finishAt, now, zone)))
            val game = u.gameFinishAt(now)
            if (!done && game - u.finishAt >= 60_000L && game > now) {
                val g = Sheets.stat(act, "Di game", act.countdown(game - now), Ui.TEXT2)
                act.registerCountdown(g.second, game, clockStyle = false)
                add(g)
            }
            col.addView(grid, Ui.matchWrap())
            if (!done && game - u.finishAt >= 60_000L) {
                val next = u.helperSteps.firstOrNull { it.first > now }?.first
                col.addView(Ui.muted(act, "Waktu di aplikasi sudah memotong sesi helper" +
                    (next?.let { " berikutnya ${HomeView.short(it, now, zone)}" } ?: "") + ".").apply {
                    textSize = 12f
                    setPadding(0, Ui.dp(act, 8), 0, 0)
                })
            }
            if (u.note.isNotBlank() && !u.helped) col.addView(Ui.muted(act, u.note).apply { textSize = 12f; setPadding(0, Ui.dp(act, 6), 0, 0) })

            val acts = Ui.vbox(act).apply { setPadding(0, Ui.dp(act, 10), 0, 0) }
            val heroItem = item?.cat == Cat.HERO || item?.cat == Cat.BB_HERO
            acts.addView(Sheets.action(act, if (done) "Tandai selesai" else "Tandai selesai sekarang",
                if (done) "upgrade sudah selesai di game" else if (heroItem) "gem atau Book of Heroes" else "gem, book, atau sudah selesai di game") {
                dismiss(); act.markUpgrade(u, true)
            })
            if (!done && u.kind != Kind.REMINDER) {
                val potion = potionFor(u.kind)
                val owned = potion?.let { Store.items(act, u.tag)[it] } ?: 0
                acts.addView(Sheets.action(act, "Pakai potion",
                    potion?.let { "${it.label}" + if (owned > 0) " · punya $owned" else "" }) {
                    dismiss(); act.openItems(u.tag)
                })
            }
            if (itemId != null && !gear) {
                val pins = Store.pins(act, u.tag)
                val pinned = itemId in pins
                val name = item?.name ?: u.name
                acts.addView(Sheets.action(act, if (pinned) "Lepas prioritas $name" else "Prioritaskan $name",
                    if (pinned) null else "didahulukan di saran & rencana") {
                    dismiss()
                    Store.setPins(act, u.tag, if (pinned) pins - itemId else pins + itemId)
                    if (!pinned) Store.setHides(act, u.tag, Store.hides(act, u.tag) - itemId)
                    act.dataChanged()
                    act.snack(if (pinned) "$name tidak lagi diprioritaskan" else "$name didahulukan")
                })
            }
            acts.addView(Sheets.action(act, "Batalkan upgrade ini", color = Ui.IDLE, last = true) {
                dismiss(); act.markUpgrade(u, false)
            })
            col.addView(acts, Ui.matchWrap())
        }
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
        return "dibantu $name" + (lvl?.let { " +${it}j/hari" } ?: "")
    }
}

/** Pilih upgrade untuk pekerja yang bebas (pengganti dialog "Lihat opsi"). */
object PickSheet {
    private enum class Filter(val label: String) { ALL("Semua"), WAKE("Selesai pas bangun"), HERO("Hero"), DEF("Pertahanan"), CHEAP("Murah") }

    /** [label] = pekerja ("B4", "Lab"); [canStart] = false untuk pekerja yang baru bebas nanti. */
    fun show(act: MainActivity, tag: String, slot: LaneSlot, label: String?, canStart: Boolean = true) {
        val zone = act.zoneId()
        val now = System.currentTimeMillis()
        val schedule = Store.planSettings(act, tag).schedule
        val all = slot.ranked.take(60)
        val who = label ?: slot.lane.label
        val title = if (canStart) "$who bebas — pilih yang Anda mulai" else "Opsi saat $who bebas ${HomeView.short(slot.start, now, zone)}"
        var filter = Filter.ALL
        Sheets.open(act, title, "Paling atas = paling cepat menuju target rencana") { col, dismiss ->
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
                if (shown.isEmpty()) list.addView(Ui.muted(act, if (all.isEmpty()) "Tidak ada upgrade yang tersedia." else "Tidak ada yang cocok dengan filter ini.", Ui.BODY_M).apply {
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
            text = "TERBAIK"
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
        Sheets.open(act, "Tambah / update desa", help = Help.Topic.VILLAGES) { col, dismiss ->
            act.clipCard(col) { dismiss() }
            val steps = Ui.panel(act, padH = 16, padV = 14, color = Ui.SURFACE2)
            steps.addView(Ui.text(act, "Desa baru dalam 3 langkah", Ui.TITLE_S).apply {
                textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT)
            })
            val items = listOf(
                "Buka Clash of Clans dengan akun itu" to "Settings → More Settings → paling bawah",
                "Ketuk Export village data → Copy" to "isinya teks JSON, tidak berisi kata sandi",
                "Kembali ke Clash Timer" to "export langsung terdeteksi; atau ketuk Tempel di bawah",
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
            if (launch != null) steps.addView(Ui.primary(act, "Buka Clash of Clans") {
                dismiss()
                runCatching { act.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }, Ui.matchWrap().apply { topMargin = Ui.dp(act, 14) })
            col.addView(steps, Ui.matchWrap().apply { topMargin = Ui.dp(act, 12) })

            val r = Ui.hbox(act).apply { setPadding(0, Ui.dp(act, 12), 0, 0) }
            r.addView(Ui.tonal(act, "Tempel dari clipboard") { dismiss(); act.importFromClipboard() }, Ui.weight1())
            r.addView(Ui.secondary(act, "Tempel manual") { dismiss(); act.showPasteDialog() },
                Ui.weight1().apply { marginStart = Ui.dp(act, 8) })
            col.addView(r, Ui.matchWrap())
            col.addView(Ui.muted(act, "Pintasan: tahan ikon Clash Timer → Update desa, atau tile \"Update desa\" di Quick Settings.").apply {
                textSize = 13f
                setPadding(Ui.dp(act, 4), Ui.dp(act, 14), Ui.dp(act, 4), 0)
            })
        }
    }
}
