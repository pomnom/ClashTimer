package id.clashtimer

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.ExportException
import id.clashtimer.core.Fmt
import id.clashtimer.core.Scored
import java.time.ZoneId
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    enum class Tab(val iconRes: Int, val label: String) {
        HOME(R.drawable.ic_nav_dashboard, "Dashboard"),
        BUILDERS(R.drawable.ic_nav_builder, "Tukang"),
        LAB(R.drawable.ic_flask, "Lab"),
        PROGRESS(R.drawable.ic_nav_progress, "Progres");

        /** Nama tab sesuai bahasa. */
        val display: String get() = when (this) {
            HOME -> "Dashboard"
            BUILDERS -> tr("Tukang", "Builders")
            LAB -> "Lab"
            PROGRESS -> tr("Progres", "Progress")
        }

        companion object {
            /** Nama tab dari widget / notifikasi / versi lama ("PLAN" = Tukang, "VILLAGES" = lembar Desa anda). */
            fun of(name: String?): Tab? = when (name) {
                null -> null
                "PLAN" -> BUILDERS
                "VILLAGES" -> HOME
                else -> runCatching { valueOf(name) }.getOrNull()
            }
        }
    }

    private var tab = Tab.HOME
    /** false = Home Village, true = Builder Base. */
    var showBB = false
        private set

    private lateinit var header: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var nav: LinearLayout
    /** Layar lebar (tablet / HP landscape): Dashboard di kiri, Tukang di kanan. */
    private var wide = false
    private var body2: LinearLayout? = null
    private var scroll2: ScrollView? = null

    private val handler = Handler(Looper.getMainLooper())
    private class Countdown(val view: TextView, val target: Long, val clock: Boolean, val prefix: String = "")
    private val countdowns = ArrayList<Countdown>()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    private val exec = Executors.newSingleThreadExecutor()
    private val planCache = HashMap<String, PlanBundle>()
    private var computing: String? = null
    private var planError: String? = null
    private var failedTag: String? = null
    private var failedVersion = -1

    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        renderBody()
    }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (countdowns.any { it.target <= now }) renderBody()
            else for (c in countdowns) c.view.text = c.prefix + if (c.clock) clock(c.target - now) else countdown(c.target - now)
            handler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000))
        }
    }

    // ---------------------------------------------------------------- lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannels(this)
        Updater.cleanup(this) // sisa unduhan update yang gagal / sudah terpasang
        savedInstanceState?.let { st ->
            Tab.of(st.getString("tab"))?.let { tab = it }
            showBB = st.getBoolean("bb", false)
        }

        Ui.init(this)
        wide = resources.configuration.screenWidthDp >= 720
        val main = Ui.vbox(this).apply { setBackgroundColor(Ui.BG) }
        header = Ui.vbox(this).apply { setPadding(dp(16), dp(8), dp(8), dp(6)) }
        main.addView(header, Ui.matchWrap())

        val content = FrameLayout(this)
        body = Ui.vbox(this).apply { setPadding(dp(16), dp(4), dp(16), dp(24)) }
        scroll = ScrollView(this).apply { isFillViewport = true; addView(body) }
        if (wide) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(scroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            val right = Ui.vbox(this).apply { setPadding(dp(8), dp(4), dp(20), dp(24)) }
            val rightScroll = ScrollView(this).apply { isFillViewport = true; addView(right) }
            row.addView(rightScroll, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.15f))
            body2 = right
            scroll2 = rightScroll
            content.addView(row)
        } else content.addView(scroll)
        main.addView(content, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        if (wide) {
            // Layar lebar: rel navigasi di kiri.
            nav = Ui.vbox(this).apply { setBackgroundColor(Ui.NAV); setPadding(0, dp(20), 0, 0) }
            val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Ui.BG) }
            root.addView(nav, LinearLayout.LayoutParams(dp(96), LinearLayout.LayoutParams.MATCH_PARENT))
            root.addView(View(this).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(dp(1), LinearLayout.LayoutParams.MATCH_PARENT))
            root.addView(main, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            setContentView(root)
        } else {
            main.addView(View(this).apply { setBackgroundColor(Ui.LINE) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
            nav = Ui.hbox(this).apply { setBackgroundColor(Ui.NAV); setPadding(0, dp(6), 0, dp(6)) }
            main.addView(nav, Ui.matchWrap())
            setContentView(main)
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        handleShare(intent)
        // Dibuka dari widget / notifikasi / shortcut (bukan saat layar dibuat ulang).
        if (savedInstanceState == null) {
            handleOpen(intent)
            if (intent?.action == ACTION_IMPORT) pendingImport = true
        }
    }

    /** Lembar Desa anda dibuka begitu layar siap (dari widget / notifikasi semua desa). */
    private var pendingPicker = false

    /** Widget & notifikasi membawa tag desa + sisi (Home/BB) + tab tujuan (atau "semua desa" = lembar Desa anda). */
    private fun handleOpen(intent: Intent?): Boolean {
        val raw = intent?.getStringExtra(Widgets.EXTRA_TAB)
        if (raw == "VILLAGES") {
            tab = Tab.HOME
            pendingPicker = Store.accounts(this).size > 1
            intent.removeExtra(Widgets.EXTRA_TAB)
            return true
        }
        val target = Tab.of(raw)
        val tag = intent?.getStringExtra(Widgets.EXTRA_TAG) ?: return false
        if (Store.accounts(this).none { it.tag == tag }) return false
        Store.setSelectedTag(this, tag)
        showBB = intent.getBooleanExtra(Widgets.EXTRA_BB, false)
        tab = target ?: Tab.HOME
        intent.removeExtra(Widgets.EXTRA_TAG)
        intent.removeExtra(Widgets.EXTRA_TAB)
        return true
    }

    // ---------------------------------------------------------------- update cepat dari clipboard

    /** Export desa yang terdeteksi di clipboard dan belum di-import. */
    private class ClipOffer(val text: String, val tag: String, val exportAt: Long, val isNew: Boolean)
    private var clipOffer: ClipOffer? = null
    /** Dibuka dari shortcut / tile "Update desa": import begitu jendela aktif (clipboard bisa dibaca). */
    private var pendingImport = false

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        if (pendingImport) {
            pendingImport = false
            importFromClipboard()
        } else checkClipboard()
    }

    /** Periksa clipboard hanya bila isinya berubah sejak terakhir dicek. */
    private fun checkClipboard() {
        val cm = getSystemService(ClipboardManager::class.java) ?: return
        val desc = runCatching { cm.primaryClipDescription }.getOrNull() ?: return
        val ts = desc.timestamp
        if (ts != 0L && ts == Store.clipSeen(this)) return
        Store.setClipSeen(this, ts)
        if (!desc.hasMimeType("text/*")) return
        val o = peekClip() ?: return
        if (Store.clipDismissed(this) == "${o.tag}|${o.exportAt}") return
        clipOffer = o
        renderBody()
    }

    /** Export desa di clipboard yang lebih baru dari data tersimpan (atau desa baru). */
    private fun peekClip(): ClipOffer? {
        val cm = getSystemService(ClipboardManager::class.java) ?: return null
        val text = runCatching { cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString() }
            .getOrNull() ?: return null
        val peek = id.clashtimer.core.ExportReader.peek(text) ?: return null
        val acc = Store.accounts(this).firstOrNull { it.tag == peek.tag }
        if (acc != null && peek.exportAt in 1..acc.exportedAt) return null
        return ClipOffer(text, peek.tag, peek.exportAt, acc == null)
    }

    /** Kartu "Update dari clipboard?" di atas halaman. */
    private fun addClipOffer(box: LinearLayout) {
        val o = clipOffer ?: return
        box.addView(clipView(o) { clipOffer = null })
    }

    /** Kartu clipboard untuk lembar "Tambah / update desa" (dibaca ulang dari clipboard). */
    fun clipCard(box: LinearLayout, onDone: () -> Unit) {
        val o = clipOffer ?: peekClip() ?: return
        box.addView(clipView(o) { clipOffer = null; onDone() })
    }

    private fun clipView(o: ClipOffer, onDone: () -> Unit): View {
        val card = Ui.panel(this, padH = 14, padV = 14, color = Ui.FREE_BG, stroke = Ui.FREE_LINE)
        val name = if (o.isNew) o.tag else Store.villageName(this, o.tag)
        val top = Ui.hbox(this)
        top.addView(Ui.icon(this, R.drawable.ic_import, 22, Ui.FREE_TEXT), LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(10) })
        val tc = Ui.vbox(this)
        tc.addView(Ui.text(this, if (o.isNew) tr("Desa baru di clipboard: $name", "New village in clipboard: $name") else tr("Export baru untuk $name", "New export for $name"), Ui.TITLE_S).apply {
            textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT)
        })
        val age = if (o.exportAt > 0) tr("di clipboard · diekspor ${HomeView.short(o.exportAt, System.currentTimeMillis(), zone)}", "in clipboard · exported ${HomeView.short(o.exportAt, System.currentTimeMillis(), zone)}") else tr("di clipboard", "in clipboard")
        tc.addView(Ui.muted(this, age).apply { textSize = 13f })
        top.addView(tc, Ui.weight1())
        card.addView(top)
        val row = Ui.hbox(this).apply { setPadding(0, dp(12), 0, 0) }
        row.addView(Ui.primary(this, if (o.isNew) tr("Tambahkan", "Add") else "Update") {
            onDone()
            doImport(o.text)
        }, Ui.weight1())
        row.addView(Ui.secondary(this, tr("Nanti", "Later")) {
            Store.setClipDismissed(this, "${o.tag}|${o.exportAt}")
            onDone()
            renderBody()
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        card.addView(row)
        card.layoutParams = Ui.matchWrap(12, this)
        return card
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("tab", tab.name)
        outState.putBoolean("bb", showBB)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
        if (handleOpen(intent)) {
            renderAll()
            scroll.scrollTo(0, 0)
        }
        if (intent.action == ACTION_IMPORT) pendingImport = true
    }

    override fun onResume() {
        super.onResume()
        // Aplikasi sudah dibuka: yang masuk diam-diam saat jam istirahat sudah terlihat, tidak perlu diringkas lagi.
        Store.clearQuietPending(this)
        AlarmReceiver.processDue(this) // jaga-jaga kalau ada alarm yang terlewat
        if (Store.quietPending(this).isNotEmpty()) {
            Store.clearQuietPending(this)
            AlarmScheduler.schedule(this)
        }
        renderAll()
        if (pendingPicker) {
            pendingPicker = false
            handler.post { if (!isFinishing) VillagesView.sheet(this) }
        }
        handler.removeCallbacks(tick)
        handler.post(tick)
        ApiSync.maybeSync(this)
        Updater.maybeCheck(this) { renderBody() }
        UpdateUi.maybeShowNews(this)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    override fun onStop() {
        super.onStop()
        // Kembali ke layar utama: widget langsung menampilkan keadaan terbaru.
        Widgets.updateAll(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdownNow()
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { doImport(it) }
            intent.action = null
        }
    }

    // ---------------------------------------------------------------- kerangka

    fun currentTag(): String? {
        val accounts = Store.accounts(this)
        if (accounts.isEmpty()) return null
        val sel = Store.selectedTag(this)
        return accounts.firstOrNull { it.tag == sel }?.tag ?: accounts.minByOrNull { it.tag }!!.tag
    }

    private fun renderAll() {
        renderHeader()
        renderNav()
        renderBody()
    }

    /**
     * Desa aktif (ketuk = lembar Desa anda) + update & opsi. Di bawahnya Home Village | Builder Base;
     * di Tukang & Lab: Agenda/Antrian | Timeline dan Home | BB.
     */
    private fun renderHeader() {
        header.removeAllViews()
        val sec = Ui.TEXT2
        val accounts = sortedAccounts()
        val tag = currentTag()
        val acc = accounts.firstOrNull { it.tag == tag }
        val row = Ui.hbox(this)
        header.addView(row, Ui.matchWrap())
        if (acc == null) {
            val col = Ui.vbox(this).apply { setPadding(0, dp(4), 0, dp(4)) }
            col.addView(Ui.text(this, "Clash Timer", Ui.TITLE_L).apply {
                textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT)
            })
            col.addView(Ui.muted(this, tr("Belum ada desa", "No village yet")))
            row.addView(col, Ui.weight1())
            row.addView(Ui.icon(this, R.drawable.ic_add, 24, sec, tr("Tambah desa dari export", "Add village from export")) { ImportUi.show(this) })
            row.addView(Ui.icon(this, R.drawable.ic_settings, 22, sec, tr("Pengaturan", "Settings")) { openSettings() })
            return
        }
        val name = Store.villageName(this, acc.tag)
        val workers = tab == Tab.BUILDERS || tab == Tab.LAB
        val col = Ui.vbox(this).apply {
            setPadding(0, dp(2), dp(4), dp(2))
            isClickable = true
            contentDescription = tr("Desa anda: ganti desa", "Your villages: switch village")
            setOnClickListener { VillagesView.sheet(this@MainActivity) }
        }
        val title = when (tab) {
            Tab.BUILDERS -> (if (showBB) "Master Builder" else tr("Tukang", "Builders")) + " · " + name
            Tab.LAB -> (if (showBB) "Star Lab" else "Lab") + " · " + name
            else -> name
        }
        col.addView(Ui.text(this, "$title ▾", Ui.TITLE_L).apply {
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        col.addView(Ui.muted(this, headerSub(acc)).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
        row.addView(col, Ui.weight1())
        val seg = if (workers) null else sideSwitch(acc)
        if (wide && seg != null) row.addView(seg, LinearLayout.LayoutParams(dp(380), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        row.addView(Ui.icon(this, R.drawable.ic_import, 22, sec, tr("Update desa dari export", "Update village from export")) { importFromClipboard() })
        row.addView(Ui.icon(this, R.drawable.ic_tune, 22, sec, tr("Opsi desa", "Village options")) { VillageOptions.sheet(this, acc.tag, showBB) })
        if (workers) header.addView(workerSwitch(acc), Ui.matchWrap().apply { topMargin = dp(8); marginEnd = dp(8) })
        else if (!wide && seg != null) header.addView(seg, Ui.matchWrap().apply { topMargin = dp(8); marginEnd = dp(8) })
    }

    /** Keterangan di bawah nama desa, sesuai tab. */
    private fun headerSub(acc: Account): String {
        val b = planCache[acc.tag]
        val sim = if (showBB) b?.simBB else b?.simHome
        val zone = zone
        fun eta(t: Long?) = t?.takeIf { it > System.currentTimeMillis() }?.let { tr("selesai ±", "done ±") + Fmt.dateShort(it, zone) }
        return when (tab) {
            Tab.BUILDERS -> {
                val (busy, total) = builderCounts(acc.tag, showBB)
                listOfNotNull(tr("$busy/$total bekerja", "$busy/$total working"), eta(if (showBB) sim?.bbBuilderDone else sim?.builderDone)).joinToString(" · ")
            }
            Tab.LAB -> {
                val now = System.currentTimeMillis()
                val kinds = if (showBB) setOf(Kind.STAR_LAB) else setOf(Kind.LAB, Kind.PET)
                val n = Store.upgrades(this).count { it.tag == acc.tag && it.kind in kinds && it.finishAt > now }
                listOfNotNull(if (n > 0) tr("$n riset berjalan", "$n research running") else tr("tidak ada riset berjalan", "no research running"),
                    eta(if (showBB) sim?.starLabDone else sim?.labDone)).joinToString(" · ")
            }
            else -> {
                val p = if (showBB) b?.bb else b?.home
                val cur = if (showBB) acc.bh else acc.th
                // Hall sedang di-upgrade: progres sudah menuju level berikutnya ("BH7 → 8").
                val hall = cur.takeIf { it > 0 }?.let { (if (showBB) "BH" else "TH") + it + (p?.hall?.takeIf { h -> h > it }?.let { h -> " → $h" } ?: "") }
                val pct = p?.pct ?: Store.lastPct(this, acc.tag).takeIf { it >= 0 && !showBB }?.toDouble()
                listOfNotNull(hall, pct?.let { tr("${Ui.pct(it)} menuju max", "${Ui.pct(it)} to max") }).joinToString(" · ")
            }
        }
    }

    /** Home Village | Builder Base, dengan jumlah pekerja bebas di sisi lain. */
    private fun sideSwitch(acc: Account): View {
        val freeHome = freeBuilders(acc.tag, false)
        val freeBB = if (acc.bh > 0) freeBuilders(acc.tag, true) else 0
        val home = "Home Village" + if (showBB && freeHome > 0) tr(" · $freeHome bebas", " · $freeHome free") else ""
        val bb = "Builder Base" + if (!showBB && freeBB > 0) tr(" · $freeBB bebas", " · $freeBB free") else ""
        return Ui.segmentedWide(this, listOf(home, bb), if (showBB) 1 else 0) { selectSide(it == 1) }
    }

    /** Tukang & Lab: Agenda (Antrian) | Timeline, lalu Home | BB (Star) yang ringkas. */
    private fun workerSwitch(acc: Account): View {
        val lab = tab == Tab.LAB
        val row = Ui.hbox(this)
        row.addView(Ui.segmentedWide(this, listOf(if (lab) tr("Antrian", "Queue") else "Agenda", "Timeline"), PlanView.mode(this, lab), lab = lab) {
            PlanView.setMode(this, lab, it)
            renderHeader()
            renderBody()
            scroll.scrollTo(0, 0)
        }, Ui.weight1())
        row.addView(Ui.segmentedWide(this, listOf("Home", if (lab) "Star" else "BB"), if (showBB) 1 else 0, lab = lab) { selectSide(it == 1) },
            LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        return row
    }

    /** Urutan desa untuk tombol ‹ ›: menurut nama. */
    private fun sortedAccounts() = Store.accounts(this).sortedBy { Store.villageName(this, it.tag).lowercase() }

    private fun cycleVillage(step: Int) {
        val list = sortedAccounts()
        if (list.size < 2) return
        val i = list.indexOfFirst { it.tag == currentTag() }.coerceAtLeast(0)
        selectVillage(list[(i + step + list.size) % list.size].tag)
    }

    /** Builder bebas sekarang untuk satu desa (dari timer tersimpan). */
    fun freeBuilders(tag: String, bb: Boolean): Int {
        val (busy, total) = builderCounts(tag, bb)
        return (total - busy).coerceAtLeast(0)
    }

    /** (builder yang bekerja, jumlah builder) — Goblin (event) tidak memakai builder tetap. */
    fun builderCounts(tag: String, bb: Boolean): Pair<Int, Int> {
        val now = System.currentTimeMillis()
        val kind = if (bb) Kind.BB_BUILDER else Kind.BUILDER
        val busy = Store.upgrades(this).count { it.tag == tag && it.kind == kind && it.finishAt > now && !it.extra }
        val total = if (bb) {
            planCache[tag]?.let { id.clashtimer.core.Planner.laneCount(it.village, id.clashtimer.core.Lane.BB_BUILDER, it.ps) }
                ?: maxOf(busy, 1)
        } else Store.accounts(this).firstOrNull { it.tag == tag }?.builders ?: 5
        return minOf(busy, total) to total
    }

    private fun selectSide(bb: Boolean) {
        if (bb == showBB) return
        showBB = bb
        renderHeader()
        renderBody()
        scroll.scrollTo(0, 0)
    }

    private fun renderNav() {
        nav.removeAllViews()
        for (t in Tab.entries) {
            val selected = t == tab
            val lab = t == Tab.LAB
            val color = if (!selected) Ui.TEXT2 else if (lab) Ui.LAB_TEXT else Ui.FREE_TEXT
            val item = Ui.vbox(this).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(if (wide) 10 else 4), 0, dp(if (wide) 10 else 4))
                isClickable = true
                contentDescription = t.display
                setOnClickListener { selectTab(t) }
            }
            val pill = FrameLayout(this).apply {
                if (selected) background = Ui.rounded(if (lab) Ui.LAB_BG else Ui.SELECTED_BG, dp(16).toFloat())
                setPadding(dp(18), dp(4), dp(18), dp(4))
                addView(Ui.icon(this@MainActivity, t.iconRes, 22, color), FrameLayout.LayoutParams(dp(22), dp(22)))
            }
            item.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            item.addView(TextView(this).apply {
                text = t.display
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(color)
                setPadding(0, dp(4), 0, 0)
                if (selected) setTypeface(typeface, Typeface.BOLD)
            })
            if (wide) nav.addView(item, Ui.matchWrap()) else nav.addView(item, Ui.weight1())
        }
    }

    fun openTab(t: Tab) = selectTab(t)

    fun zoneId(): ZoneId = ZoneId.systemDefault()

    private fun selectTab(t: Tab) {
        if (t == tab) return
        tab = t
        renderHeader()
        renderNav()
        renderBody()
        scroll.scrollTo(0, 0)
        scroll2?.scrollTo(0, 0)
    }

    /**
     * Pilih desa aktif; [bb] (opsional) mengganti sisi. Pindah ke desa lain selalu membuka Dashboard;
     * [open] hanya dipakai bila desanya tetap (pintas ke tab pekerja dari lembar Pilih desa).
     */
    fun selectVillage(tag: String, bb: Boolean? = null, open: Tab? = null) {
        val changed = tag != currentTag()
        Store.setSelectedTag(this, tag)
        if (bb != null) showBB = bb
        if (changed) tab = Tab.HOME else if (open != null) tab = open
        renderAll()
        scroll.scrollTo(0, 0)
        scroll2?.scrollTo(0, 0)
    }

    // ---------------------------------------------------------------- ringkasan semua desa (lembar Desa anda)

    private var villageRows: List<Widgets.VRow>? = null
    private var villageRowsAt = 0L
    private var villageRowsVersion = -1
    private var loadingRows = false

    /** Status semua desa (sama dengan widget); dihitung di background, null saat pertama dimuat. */
    internal fun villageRows(): List<Widgets.VRow>? {
        val now = System.currentTimeMillis()
        val r = villageRows
        val version = Store.planVersion(this)
        val stale = r == null || version != villageRowsVersion || now - villageRowsAt > 60_000L ||
            r.any { row -> row.cells.any { c -> c.free == 0 && c.next != null && c.next.finishAt <= now } }
        if (stale && !loadingRows) {
            loadingRows = true
            val app = applicationContext
            exec.execute {
                val res = runCatching { Widgets.rows(app, System.currentTimeMillis()) }.getOrNull()
                runOnUiThread {
                    loadingRows = false
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    villageRows = res ?: Store.accounts(this).map { Widgets.VRow(it.tag, Store.villageName(this, it.tag), it, emptyList()) }
                    villageRowsAt = System.currentTimeMillis()
                    villageRowsVersion = version
                    onVillageRows?.invoke()
                }
            }
        }
        return r
    }

    /** Lembar Desa anda yang sedang terbuka: isi ulang begitu status semua desa selesai dihitung. */
    var onVillageRows: (() -> Unit)? = null

    fun renderBody() {
        body.removeAllViews()
        body2?.removeAllViews()
        countdowns.clear()
        val tag = currentTag()
        val right = body2
        val split = wide && right != null && tab == Tab.HOME && tag != null
        scroll2?.visibility = if (split) View.VISIBLE else View.GONE
        if (tag == null) {
            addClipOffer(body)
            UpdateUi.offer(this, body)
            addWarnings(body)
            val card = Ui.panel(this, padH = 16, padV = 16)
            card.addView(Ui.text(this, tr("Belum ada desa", "No village yet"), Ui.TITLE_M).apply { textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT) })
            card.addView(Ui.muted(this, tr("Di Clash of Clans: Settings → More Settings → paling bawah \"Export village data\" → Copy, lalu kembali ke sini. ", "In Clash of Clans: Settings → More Settings → at the bottom \"Export village data\" → Copy, then come back here. ") +
                tr("Untuk desa lain, ulangi dengan akun lain — semua desa ada di lembar Desa anda (ketuk nama desa di atas).", "For another village, repeat with the other account — all villages are in Your villages (tap the village name at the top).")).apply { textSize = 14f; setPadding(0, dp(6), 0, dp(14)) })
            card.addView(Ui.primary(this, tr("Tambah desa", "Add village")) { ImportUi.show(this) }, Ui.matchWrap(8, this))
            card.addView(Ui.secondary(this, tr("Tempel dari clipboard", "Paste from clipboard")) { importFromClipboard() }, Ui.matchWrap())
            body.addView(card, Ui.matchWrap().apply { topMargin = dp(12) })
            return
        }
        val b = planFor(tag)
        addClipOffer(body)
        UpdateUi.offer(this, body)
        try {
            if (split && right != null) {
                HomeView.render(this, body, tag, showBB, b)
                PlanView.render(this, right, tag, showBB, lab = false, b = b, showFree = false)
            } else when (tab) {
                Tab.HOME -> HomeView.render(this, body, tag, showBB, b)
                Tab.BUILDERS -> PlanView.render(this, body, tag, showBB, lab = false, b = b)
                Tab.LAB -> PlanView.render(this, body, tag, showBB, lab = true, b = b)
                Tab.PROGRESS -> if (b == null) waiting(tag) else ProgressView.render(this, body, b, showBB)
            }
        } catch (e: Exception) {
            body.addView(Ui.text(this, tr("Gagal menampilkan: ${e.message}", "Could not display: ${e.message}"), Ui.BODY_M))
        }
    }

    fun waiting(tag: String, into: LinearLayout = body) {
        val msg = when {
            Store.exportText(this, tag) == null ->
                tr("Import ulang desa ini sekali (Export village data → Copy → 📋 Update desa) supaya rencana dan progres bisa dihitung.", "Re-import this village once (Export village data → Copy → 📋 Update village) so the plan and progress can be calculated.")
            planError != null -> tr("Gagal menghitung: $planError", "Calculation failed: $planError")
            else -> tr("⏳ Menghitung rencana & progres…", "⏳ Calculating plan & progress…")
        }
        into.addView(Ui.text(this, msg, Ui.BODY_M).apply { setPadding(dp(4), dp(24), dp(4), 0) })
    }

    /** Ambil hasil hitung (cache); minta hitung ulang di background bila perlu. */
    fun planFor(tag: String): PlanBundle? {
        if (Store.exportText(this, tag) == null) return null
        val b = planCache[tag]
        val now = System.currentTimeMillis()
        val version = Store.planVersion(this)
        val stale = b == null || b.version != version || now - b.computedAt > 5 * 60_000L
        val failedHere = failedTag == tag && failedVersion == version
        if (stale && !failedHere) requestPlan(tag)
        return b
    }

    private fun requestPlan(tag: String) {
        if (computing == tag) return
        computing = tag
        val app = applicationContext
        val version = Store.planVersion(this)
        exec.execute {
            val r = runCatching { Repo.computePlan(app, tag) }
            runOnUiThread {
                computing = null
                if (isFinishing || isDestroyed) return@runOnUiThread
                val bundle = r.getOrNull()
                if (bundle != null) {
                    planCache[tag] = bundle
                    planError = null
                    failedTag = null
                    onDataChanged?.invoke() // lembar opsi: tanggal target strategi
                } else {
                    planError = r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName }
                        ?: tr("data export tidak bisa dibaca, coba import ulang", "export data could not be read, try importing again")
                    failedTag = tag
                    failedVersion = version
                }
                if (tag == currentTag()) {
                    renderHeader()
                    renderBody()
                }
            }
        }
    }

    /** Data berubah (import, item, mulai, tandai selesai) → hitung ulang. */
    fun dataChanged() {
        planCache.clear()
        villageRowsVersion = -1
        onDataChanged?.invoke()
        planError = null
        failedTag = null
        renderAll()
        Widgets.updateAll(this)
    }

    fun registerCountdown(tv: TextView, target: Long, clockStyle: Boolean, prefix: String = "") {
        countdowns += Countdown(tv, target, clockStyle, prefix)
    }

    // ---------------------------------------------------------------- import

    fun importFromClipboard() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        if (text.isNullOrBlank() || !text.contains("{")) {
            MaterialAlertDialogBuilder(this)
                .setTitle(tr("Clipboard kosong", "Clipboard is empty"))
                .setMessage(tr("Di Clash of Clans buka Settings → More Settings → paling bawah \"Export village data\" → Copy. Lalu kembali ke sini dan tekan Update desa lagi.", "In Clash of Clans open Settings → More Settings → at the bottom \"Export village data\" → Copy. Then come back here and tap Update village again."))
                .setPositiveButton("OK", null)
                .setNeutralButton(tr("Tempel manual", "Paste manually")) { _, _ -> showPasteDialog() }
                .show()
            return
        }
        doImport(text)
    }

    fun showPasteDialog() {
        val input = EditText(this).apply {
            hint = tr("Tempel teks export di sini", "Paste the export text here")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            maxLines = 8
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, Ui.matchWrap())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(tr("Tempel export desa", "Paste village export"))
            .setView(box)
            .setPositiveButton("Import") { _, _ -> doImport(input.text.toString()) }
            .setNegativeButton(tr("Batal", "Cancel"), null)
            .show()
    }

    private fun doImport(text: String) {
        clipOffer = null
        val before = Store.accounts(this).map { it.tag }.toSet()
        val s = try {
            Repo.importText(this, text)
        } catch (e: ExportException) {
            MaterialAlertDialogBuilder(this).setTitle(tr("Gagal import", "Import failed")).setMessage(e.message)
                .setPositiveButton("OK", null).show()
            return
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this).setTitle(tr("Gagal import", "Import failed"))
                .setMessage(tr("Terjadi kesalahan: ${e.message}", "Something went wrong: ${e.message}")).setPositiveButton("OK", null).show()
            return
        }
        Store.setSelectedTag(this, s.tag)
        dataChanged()

        val now = System.currentTimeMillis()
        val name = Store.villageName(this, s.tag)
        val isNew = s.tag !in before
        val sb = StringBuilder(if (isNew) tr("Desa baru ditambahkan: $name.\n\n", "New village added: $name.\n\n") else "")
        sb.append(tr("${s.running} timer berjalan, notifikasi sudah dijadwalkan.", "${s.running} timers running, notifications scheduled."))
        s.next?.let { sb.append(tr("\n\nBerikutnya: ${it.title()} dalam ${Fmt.dur(it.finishAt - now)}.", "\n\nNext: ${it.title()} in ${Fmt.dur(it.finishAt - now)}.")) }
        if (s.helped > 0) sb.append(tr("\n\n🧑‍🔧 ${s.helped} upgrade dibantu helper berulang — waktu selesainya sudah diperkirakan.", "\n\n🧑‍🔧 ${s.helped} upgrades get recurring helper help — their finish times are already estimated."))
        val age = now - s.exportAt
        if (age > 6 * 3600_000L) sb.append(tr("\n\n⚠️ Data export ini sudah ${Fmt.dur(age)} lalu. Export ulang dari game agar akurat.", "\n\n⚠️ This export is ${Fmt.dur(age)} old. Export again from the game for accuracy."))
        if (s.unknown > 0) sb.append(tr("\n\n${s.unknown} item belum dikenal (data game baru), tapi timernya tetap dihitung.", "\n\n${s.unknown} items are not known yet (new game data), but their timers still count."))
        if (s.gold.isNotEmpty()) {
            sb.append(tr("\n\n🎫 Gold Pass terdeteksi otomatis dari timer: ", "\n\n🎫 Gold Pass detected automatically from timers: ") + s.gold.joinToString(" · ") {
                "${if (it.research) "Research" else "Builder"} ${it.pct}% (${it.title})"
            })
        }
        val dlg = MaterialAlertDialogBuilder(this).setTitle("✅ $name").setMessage(sb.toString())
            .setPositiveButton("OK", null)
        if (isNew) dlg.setNeutralButton(tr("Beri nama", "Name it")) { _, _ -> VillagesUi.rename(this, s.tag) }
        dlg.show()
        // Nama, clan, CWL, upgrade yang selesai lebih cepat: langsung diperbarui dari API.
        ApiSync.afterImport(this)
    }

    // ---------------------------------------------------------------- aksi

    /** Pengaturan untuk semua desa (opsi per desa ada di Beranda). */
    fun openSettings() {
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    fun openItems(tag: String) {
        if (Store.exportText(this, tag) == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle(tr("Import ulang dulu", "Re-import first"))
                .setMessage(tr("Desa ini di-import dengan versi lama. Import ulang sekali (Export village data → Copy → Update desa) supaya item bisa dicatat.", "This village was imported with an old version. Re-import once (Export village data → Copy → Update village) so items can be recorded."))
                .setPositiveButton("OK", null).show()
            return
        }
        ItemsUi.open(this, tag)
    }

    /** Saran ditandai "sudah dimulai di game". */
    fun startPick(tag: String, pick: Scored) {
        val u = Repo.startUpgrade(this, tag, pick)
        dataChanged()
        val msg = if (u != null) tr("${pick.c.shortTitle} dimulai · selesai ${Fmt.at(u.finishAt, zone)}", "${pick.c.shortTitle} started · done ${Fmt.at(u.finishAt, zone)}") else tr("Timer dipasang", "Timer set")
        snack(msg, if (u != null) tr("Batalkan", "Undo") else null) {
            if (u != null) {
                Repo.undoStart(this, tag, u.id)
                dataChanged()
            }
        }
    }

    /** Pesan singkat di bawah layar dengan tombol opsional (mis. Batalkan). */
    fun snack(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        val root = findViewById<View>(android.R.id.content) ?: return
        val sb = com.google.android.material.snackbar.Snackbar.make(root, text, com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
        if (action != null && onAction != null) sb.setAction(action) { onAction() }
        if (!wide) runCatching { sb.setAnchorView(nav) }
        sb.show()
    }

    /** Dipanggil setelah data berubah (lembar opsi memakainya untuk menyegarkan isinya). */
    var onDataChanged: (() -> Unit)? = null

    fun timerOptions(u: Upgrade) {
        val hasExport = Store.exportText(this, u.tag) != null
        if (u.kind == Kind.HELPER) {
            MaterialAlertDialogBuilder(this)
                .setTitle(u.title())
                .setMessage(tr("Timer helper berasal dari export. Hapus bila helper sudah Anda pakai.", "Helper timers come from the export. Remove it once you have used the helper."))
                .setPositiveButton(tr("Hapus", "Remove")) { _, _ ->
                    if (hasExport) {
                        Store.setCancelled(this, u.tag, Store.cancelled(this, u.tag) + (u.id to System.currentTimeMillis()))
                        Repo.reproject(this, u.tag)
                    } else {
                        Store.saveUpgrades(this, Store.upgrades(this).filter { it.id != u.id })
                        AlarmScheduler.schedule(this)
                    }
                    dataChanged()
                }
                .setNegativeButton(tr("Batal", "Cancel"), null).show()
            return
        }
        DetailSheet.show(this, u)
    }

    /** Tandai upgrade [u] selesai (true) atau batal (false). */
    fun markUpgrade(u: Upgrade, finished: Boolean) {
        val hasExport = Store.exportText(this, u.tag) != null
        val now = System.currentTimeMillis()
        if (hasExport) {
            if (finished) Store.setDone(this, u.tag, Store.done(this, u.tag) + (u.id to now))
            else Store.setCancelled(this, u.tag, Store.cancelled(this, u.tag) + (u.id to now))
            Repo.reproject(this, u.tag)
        } else {
            // Data versi lama (tanpa teks export): ubah timer langsung.
            val list = Store.upgrades(this)
            val updated = if (finished) list.map {
                if (it.id == u.id) it.copy(finishAt = now).also { c -> c.doneNotified = true; c.earlyNotified = true } else it
            } else list.filter { it.id != u.id }
            Store.saveUpgrades(this, updated)
            AlarmScheduler.schedule(this)
        }
        dataChanged()
    }


    // ---------------------------------------------------------------- peringatan

    /** Banner izin (notifikasi, alarm tepat, baterai) di bagian atas Beranda. */
    fun addWarnings(box: LinearLayout) {
        if (!Notifier.canNotify(this)) {
            box.addView(warningCard(
                tr("🔕 Notifikasi belum diizinkan", "🔕 Notifications not allowed"),
                tr("Tanpa izin ini, aplikasi tidak bisa memberi tahu saat upgrade selesai.", "Without this permission the app cannot tell you when upgrades finish."),
                tr("Izinkan", "Allow"),
            ) {
                val prefs = getPreferences(MODE_PRIVATE)
                if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) &&
                    prefs.getBoolean("asked_notif", false)
                ) openAppNotificationSettings()
                else if (Build.VERSION.SDK_INT >= 33) {
                    prefs.edit().putBoolean("asked_notif", true).apply()
                    notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else openAppNotificationSettings()
            })
        }
        if (!AlarmScheduler.canExact(this)) {
            box.addView(warningCard(
                tr("⏰ Izin alarm tepat waktu mati", "⏰ Exact alarm permission is off"),
                tr("Notifikasi bisa telat beberapa menit. Aktifkan \"Alarm & pengingat\" untuk aplikasi ini.", "Notifications may be a few minutes late. Enable \"Alarms & reminders\" for this app."),
                tr("Buka pengaturan", "Open settings"),
            ) {
                if (Build.VERSION.SDK_INT >= 31) {
                    startActivitySafe(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
                }
            })
        }
        val pm = getSystemService(PowerManager::class.java)
        val prefs = getPreferences(MODE_PRIVATE)
        if (Background.isXiaomi()) {
            // HyperOS: tanpa Mulai otomatis, widget & alarm tidak bisa berjalan saat aplikasi ditutup.
            if (!prefs.getBoolean("hide_xiaomi_tip", false)) {
                box.addView(warningCard(Background.TIP_TITLE, Background.TIP_BODY, tr("Buka info aplikasi", "Open app info"), hideKey = "hide_xiaomi_tip") {
                    Background.openAppInfo(this)
                })
            }
        } else if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("hide_battery_tip", false)) {
            box.addView(warningCard(
                tr("🔋 Tips: matikan optimasi baterai", "🔋 Tip: turn off battery optimization"),
                tr("Beberapa HP (Oppo, Vivo, Realme, Samsung) suka mematikan aplikasi. ", "Some phones (Oppo, Vivo, Realme, Samsung) like to kill apps. ") +
                    tr("Izinkan aplikasi ini berjalan tanpa batasan baterai, dan aktifkan Autostart bila ada.", "Let this app run without battery restrictions, and enable Autostart if available."),
                tr("Atur", "Set up"),
                hideKey = "hide_battery_tip",
            ) { requestIgnoreBattery() })
        }
    }

    @SuppressLint("BatteryLife")
    fun requestIgnoreBattery() {
        val ok = startActivitySafe(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        )
        if (!ok) startActivitySafe(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }

    fun openAppNotificationSettings() {
        startActivitySafe(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    private fun startActivitySafe(i: Intent): Boolean = try {
        startActivity(i); true
    } catch (_: Exception) {
        false
    }

    /** [hideKey]: kartu bisa disembunyikan permanen (disimpan dengan kunci ini). */
    private fun warningCard(
        title: String, body: String, action: String,
        hideKey: String? = null, onAction: () -> Unit,
    ): View {
        val (card, col) = Ui.card(this, padV = 12, tinted = true)
        col.setPadding(dp(14), dp(12), dp(14), dp(4))
        col.addView(Ui.text(this, title, Ui.TITLE_S))
        col.addView(Ui.text(this, body, Ui.BODY_S).apply { setPadding(0, dp(2), 0, 0) })
        val row = LinearLayout(this).apply { gravity = Gravity.END }
        if (hideKey != null) {
            row.addView(Ui.flat(this, tr("Sembunyikan", "Hide")) {
                getPreferences(MODE_PRIVATE).edit().putBoolean(hideKey, true).apply()
                renderBody()
            })
        }
        row.addView(Ui.flat(this, action) { onAction() })
        col.addView(row)
        return card
    }

    // ---------------------------------------------------------------- pembantu

    /** "12j 01m" / "1h 8j" / "45m 10d". */
    fun countdown(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        val d = s / 86400
        val h = (s % 86400) / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return when {
            d > 0 -> if (id.clashtimer.core.Lang.en) "${d}d ${h}h" else "${d}h ${h}j"
            h > 0 -> String.format(java.util.Locale.ROOT, if (id.clashtimer.core.Lang.en) "%dh %02dm" else "%dj %02dm", h, m)
            else -> String.format(java.util.Locale.ROOT, if (id.clashtimer.core.Lang.en) "%dm %02ds" else "%dm %02dd", m, sec)
        }
    }

    /** "12:01:27" (jam bisa lebih dari 24). */
    fun clock(ms: Long): String {
        val s = (ms.coerceAtLeast(0) + 999) / 1000
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun dp(v: Int) = Ui.dp(this, v)

    companion object {
        /** Shortcut ikon aplikasi & tile Quick Settings: update desa dari clipboard. */
        const val ACTION_IMPORT = "id.clashtimer.IMPORT_CLIPBOARD"
    }
}
