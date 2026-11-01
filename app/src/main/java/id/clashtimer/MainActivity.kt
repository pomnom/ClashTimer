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
        HOME(R.drawable.ic_nav_now, "Sekarang"),
        PLAN(R.drawable.ic_nav_schedule, "Jadwal"),
        VILLAGES(R.drawable.ic_nav_villages, "Desa"),
        PROGRESS(R.drawable.ic_nav_progress, "Progres"),
    }

    private var tab = Tab.HOME
    /** false = Home Village, true = Builder Base. */
    var showBB = false
        private set

    private lateinit var header: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var body: LinearLayout
    private lateinit var nav: LinearLayout
    /** Layar lebar (tablet / HP landscape): Beranda di kiri, Rencana di kanan. */
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
            st.getString("tab")?.let { t -> runCatching { tab = Tab.valueOf(t) } }
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
            val opened = handleOpen(intent)
            if (intent?.action == ACTION_IMPORT) pendingImport = true
            // Lebih dari satu desa: mulai dari ringkasan semua desa.
            if (!opened && Store.accounts(this).size > 1) tab = Tab.VILLAGES
        }
    }

    /** Widget & notifikasi membawa tag desa + sisi (Home/BB) + tab tujuan (atau tab Desa saja). */
    private fun handleOpen(intent: Intent?): Boolean {
        val target = intent?.getStringExtra(Widgets.EXTRA_TAB)?.let { runCatching { Tab.valueOf(it) }.getOrNull() }
        if (target == Tab.VILLAGES) {
            tab = Tab.VILLAGES
            intent.removeExtra(Widgets.EXTRA_TAB)
            return true
        }
        val tag = intent?.getStringExtra(Widgets.EXTRA_TAG) ?: return false
        if (Store.accounts(this).none { it.tag == tag }) return false
        Store.setSelectedTag(this, tag)
        showBB = intent.getBooleanExtra(Widgets.EXTRA_BB, false)
        tab = if (wide && target == Tab.PLAN) Tab.HOME else target ?: Tab.HOME
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
        tc.addView(Ui.text(this, if (o.isNew) "Desa baru di clipboard: $name" else "Export baru untuk $name", Ui.TITLE_S).apply {
            textSize = 16f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT)
        })
        val age = if (o.exportAt > 0) "di clipboard · diekspor ${HomeView.short(o.exportAt, System.currentTimeMillis(), zone)}" else "di clipboard"
        tc.addView(Ui.muted(this, age).apply { textSize = 13f })
        top.addView(tc, Ui.weight1())
        card.addView(top)
        val row = Ui.hbox(this).apply { setPadding(0, dp(12), 0, 0) }
        row.addView(Ui.primary(this, if (o.isNew) "Tambahkan" else "Update") {
            onDone()
            doImport(o.text)
        }, Ui.weight1())
        row.addView(Ui.secondary(this, "Nanti") {
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
        AlarmReceiver.processDue(this) // jaga-jaga kalau ada alarm yang terlewat
        renderAll()
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
     * Desa aktif (ketuk = ganti desa) + update & opsi; di bawahnya Home Village | Builder Base.
     * Di tab Desa: judul "Semua desa" + tambah desa & pengaturan.
     */
    private fun renderHeader() {
        header.removeAllViews()
        val sec = Ui.TEXT2
        val accounts = sortedAccounts()
        val tag = currentTag()
        val acc = accounts.firstOrNull { it.tag == tag }
        val row = Ui.hbox(this)
        header.addView(row, Ui.matchWrap())
        if (tab == Tab.VILLAGES || acc == null) {
            val col = Ui.vbox(this).apply { setPadding(0, dp(4), 0, dp(4)) }
            col.addView(Ui.text(this, if (acc == null) "Clash Timer" else "Semua desa", Ui.TITLE_L).apply {
                textSize = 22f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT)
            })
            col.addView(Ui.muted(this, if (acc == null) "Belum ada desa" else "${accounts.size} desa · yang butuh perhatian di atas"))
            row.addView(col, Ui.weight1())
            if (acc != null) row.addView(Help.button(this, Help.Topic.VILLAGES))
            row.addView(Ui.icon(this, R.drawable.ic_add, 24, sec, "Tambah desa dari export") { ImportUi.show(this) })
            row.addView(Ui.icon(this, R.drawable.ic_settings, 22, sec, "Pengaturan") { openSettings() })
            return
        }
        val multi = accounts.size > 1
        val col = Ui.vbox(this).apply {
            setPadding(0, dp(2), dp(4), dp(2))
            isClickable = true
            contentDescription = "Ganti desa"
            setOnClickListener { VillagesUi.show(this@MainActivity) }
        }
        col.addView(Ui.text(this, Store.villageName(this, acc.tag) + " ▾", Ui.TITLE_L).apply {
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.TEXT)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val hall = if (showBB) acc.bh.takeIf { it > 0 }?.let { "BH$it" } else acc.th.takeIf { it > 0 }?.let { "TH$it" }
        val sub = listOfNotNull(hall, if (multi) "desa ${accounts.indexOf(acc) + 1} dari ${accounts.size}" else null).joinToString(" · ")
        col.addView(Ui.muted(this, sub).apply { maxLines = 1 })
        row.addView(col, Ui.weight1())
        val seg = sideSwitch(acc)
        if (wide) row.addView(seg, LinearLayout.LayoutParams(dp(380), LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = dp(8) })
        row.addView(Ui.icon(this, R.drawable.ic_import, 22, sec, "Update desa dari export") { importFromClipboard() })
        row.addView(Ui.icon(this, R.drawable.ic_tune, 22, sec, "Opsi desa") { VillageOptions.sheet(this, acc.tag, showBB) })
        if (!wide) header.addView(seg, Ui.matchWrap().apply { topMargin = dp(8); marginEnd = dp(8) })
    }

    /** Home Village | Builder Base, dengan jumlah pekerja bebas di sisi lain. */
    private fun sideSwitch(acc: Account): View {
        val freeHome = freeBuilders(acc.tag, false)
        val freeBB = if (acc.bh > 0) freeBuilders(acc.tag, true) else 0
        val home = "Home Village" + if (showBB && freeHome > 0) " · $freeHome bebas" else ""
        val bb = "Builder Base" + if (!showBB && freeBB > 0) " · $freeBB bebas" else ""
        return Ui.segmentedWide(this, listOf(home, bb), if (showBB) 1 else 0) { selectSide(it == 1) }
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
        val now = System.currentTimeMillis()
        val kind = if (bb) Kind.BB_BUILDER else Kind.BUILDER
        // Goblin (event) tidak memakai builder tetap.
        val busy = Store.upgrades(this).count { it.tag == tag && it.kind == kind && it.finishAt > now && !it.extra }
        val total = if (bb) {
            planCache[tag]?.let { id.clashtimer.core.Planner.laneCount(it.village, id.clashtimer.core.Lane.BB_BUILDER, it.ps) }
                ?: maxOf(busy, 1)
        } else Store.accounts(this).firstOrNull { it.tag == tag }?.builders ?: 5
        return (total - busy).coerceAtLeast(0)
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
        if (wide && tab == Tab.PLAN) tab = Tab.HOME
        for (t in Tab.entries) {
            // Layar lebar: Jadwal tampil di samping Sekarang.
            if (wide && t == Tab.PLAN) continue
            val selected = t == tab
            val color = if (selected) Ui.FREE_TEXT else Ui.TEXT2
            val item = Ui.vbox(this).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(if (wide) 10 else 4), 0, dp(if (wide) 10 else 4))
                isClickable = true
                contentDescription = t.label
                setOnClickListener { selectTab(t) }
            }
            val pill = FrameLayout(this).apply {
                if (selected) background = Ui.rounded(Ui.SELECTED_BG, dp(16).toFloat())
                setPadding(dp(18), dp(4), dp(18), dp(4))
                addView(Ui.icon(this@MainActivity, t.iconRes, 22, color), FrameLayout.LayoutParams(dp(22), dp(22)))
            }
            item.addView(pill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            item.addView(TextView(this).apply {
                text = t.label
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
        val target = if (wide && t == Tab.PLAN) Tab.HOME else t
        if (target == tab) return
        tab = target
        renderHeader()
        renderNav()
        renderBody()
        scroll.scrollTo(0, 0)
        scroll2?.scrollTo(0, 0)
    }

    /** Pilih desa aktif; [bb] / [open] (opsional) mengganti sisi dan tab sekaligus. */
    fun selectVillage(tag: String, bb: Boolean? = null, open: Tab? = null) {
        Store.setSelectedTag(this, tag)
        if (bb != null) showBB = bb
        if (open != null) tab = if (wide && open == Tab.PLAN) Tab.HOME else open
        renderAll()
        scroll.scrollTo(0, 0)
        scroll2?.scrollTo(0, 0)
    }

    // ---------------------------------------------------------------- ringkasan semua desa

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
                    if (tab == Tab.VILLAGES) renderBody()
                }
            }
        }
        return r
    }

    fun renderBody() {
        body.removeAllViews()
        body2?.removeAllViews()
        countdowns.clear()
        val tag = currentTag()
        val right = body2
        val split = wide && right != null && tab != Tab.PROGRESS && tab != Tab.VILLAGES && tag != null
        scroll2?.visibility = if (split) View.VISIBLE else View.GONE
        if (tag == null) {
            addClipOffer(body)
            UpdateUi.offer(this, body)
            addWarnings(body)
            val card = Ui.panel(this, padH = 16, padV = 16)
            card.addView(Ui.text(this, "Belum ada desa", Ui.TITLE_M).apply { textSize = 20f; setTypeface(typeface, Typeface.BOLD); setTextColor(Ui.TEXT) })
            card.addView(Ui.muted(this, "Di Clash of Clans: Settings → More Settings → paling bawah \"Export village data\" → Copy, lalu kembali ke sini. " +
                "Untuk desa lain, ulangi dengan akun lain — semua desa tampil di tab Desa.").apply { textSize = 14f; setPadding(0, dp(6), 0, dp(14)) })
            card.addView(Ui.primary(this, "Tambah desa") { ImportUi.show(this) }, Ui.matchWrap(8, this))
            card.addView(Ui.secondary(this, "Tempel dari clipboard") { importFromClipboard() }, Ui.matchWrap())
            body.addView(card, Ui.matchWrap().apply { topMargin = dp(12) })
            return
        }
        val b = planFor(tag)
        addClipOffer(body)
        UpdateUi.offer(this, body)
        try {
            if (split && right != null) {
                HomeView.render(this, body, tag, showBB, b)
                if (b == null) waiting(tag, right) else PlanView.render(this, right, b, showBB)
            } else when (tab) {
                Tab.VILLAGES -> VillagesView.render(this, body, villageRows(), wide)
                Tab.HOME -> HomeView.render(this, body, tag, showBB, b)
                Tab.PLAN -> if (b == null) waiting(tag) else PlanView.render(this, body, b, showBB)
                Tab.PROGRESS -> if (b == null) waiting(tag) else ProgressView.render(this, body, b, showBB)
            }
        } catch (e: Exception) {
            body.addView(Ui.text(this, "Gagal menampilkan: ${e.message}", Ui.BODY_M))
        }
    }

    private fun waiting(tag: String, into: LinearLayout = body) {
        val msg = when {
            Store.exportText(this, tag) == null ->
                "Import ulang desa ini sekali (Export village data → Copy → 📋 Update desa) supaya rencana dan progres bisa dihitung."
            planError != null -> "Gagal menghitung: $planError"
            else -> "⏳ Menghitung rencana & progres…"
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
                        ?: "data export tidak bisa dibaca, coba import ulang"
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
                .setTitle("Clipboard kosong")
                .setMessage("Di Clash of Clans buka Settings → More Settings → paling bawah \"Export village data\" → Copy. Lalu kembali ke sini dan tekan Update desa lagi.")
                .setPositiveButton("OK", null)
                .setNeutralButton("Tempel manual") { _, _ -> showPasteDialog() }
                .show()
            return
        }
        doImport(text)
    }

    fun showPasteDialog() {
        val input = EditText(this).apply {
            hint = "Tempel teks export di sini"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 4
            maxLines = 8
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, Ui.matchWrap())
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Tempel export desa")
            .setView(box)
            .setPositiveButton("Import") { _, _ -> doImport(input.text.toString()) }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun doImport(text: String) {
        clipOffer = null
        val before = Store.accounts(this).map { it.tag }.toSet()
        val s = try {
            Repo.importText(this, text)
        } catch (e: ExportException) {
            MaterialAlertDialogBuilder(this).setTitle("Gagal import").setMessage(e.message)
                .setPositiveButton("OK", null).show()
            return
        } catch (e: Exception) {
            MaterialAlertDialogBuilder(this).setTitle("Gagal import")
                .setMessage("Terjadi kesalahan: ${e.message}").setPositiveButton("OK", null).show()
            return
        }
        Store.setSelectedTag(this, s.tag)
        dataChanged()

        val now = System.currentTimeMillis()
        val name = Store.villageName(this, s.tag)
        val isNew = s.tag !in before
        val sb = StringBuilder(if (isNew) "Desa baru ditambahkan: $name.\n\n" else "")
        sb.append("${s.running} timer berjalan, notifikasi sudah dijadwalkan.")
        s.next?.let { sb.append("\n\nBerikutnya: ${it.title()} dalam ${Fmt.dur(it.finishAt - now)}.") }
        if (s.helped > 0) sb.append("\n\n🧑‍🔧 ${s.helped} upgrade dibantu helper berulang — waktu selesainya sudah diperkirakan.")
        val age = now - s.exportAt
        if (age > 6 * 3600_000L) sb.append("\n\n⚠️ Data export ini sudah ${Fmt.dur(age)} lalu. Export ulang dari game agar akurat.")
        if (s.unknown > 0) sb.append("\n\n${s.unknown} item belum dikenal (data game baru), tapi timernya tetap dihitung.")
        if (s.gold.isNotEmpty()) {
            sb.append("\n\n🎫 Gold Pass terdeteksi otomatis dari timer: " + s.gold.joinToString(" · ") {
                "${if (it.research) "Research" else "Builder"} ${it.pct}% (${it.title})"
            })
        }
        val dlg = MaterialAlertDialogBuilder(this).setTitle("✅ $name").setMessage(sb.toString())
            .setPositiveButton("OK", null)
        if (isNew) dlg.setNeutralButton("Beri nama") { _, _ -> VillagesUi.rename(this, s.tag) }
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
                .setTitle("Import ulang dulu")
                .setMessage("Desa ini di-import dengan versi lama. Import ulang sekali (Export village data → Copy → Update desa) supaya item bisa dicatat.")
                .setPositiveButton("OK", null).show()
            return
        }
        ItemsUi.open(this, tag)
    }

    /** Saran ditandai "sudah dimulai di game". */
    fun startPick(tag: String, pick: Scored) {
        val u = Repo.startUpgrade(this, tag, pick)
        dataChanged()
        val msg = if (u != null) "${pick.c.shortTitle} dimulai · selesai ${Fmt.at(u.finishAt, zone)}" else "Timer dipasang"
        snack(msg, if (u != null) "Batalkan" else null) {
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
                .setMessage("Timer helper berasal dari export. Hapus bila helper sudah Anda pakai.")
                .setPositiveButton("Hapus") { _, _ ->
                    if (hasExport) {
                        Store.setCancelled(this, u.tag, Store.cancelled(this, u.tag) + (u.id to System.currentTimeMillis()))
                        Repo.reproject(this, u.tag)
                    } else {
                        Store.saveUpgrades(this, Store.upgrades(this).filter { it.id != u.id })
                        AlarmScheduler.schedule(this)
                    }
                    dataChanged()
                }
                .setNegativeButton("Batal", null).show()
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
                "🔕 Notifikasi belum diizinkan",
                "Tanpa izin ini, aplikasi tidak bisa memberi tahu saat upgrade selesai.",
                "Izinkan",
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
                "⏰ Izin alarm tepat waktu mati",
                "Notifikasi bisa telat beberapa menit. Aktifkan \"Alarm & pengingat\" untuk aplikasi ini.",
                "Buka pengaturan",
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
                box.addView(warningCard(Background.TIP_TITLE, Background.TIP_BODY, "Buka info aplikasi", hideKey = "hide_xiaomi_tip") {
                    Background.openAppInfo(this)
                })
            }
        } else if (!pm.isIgnoringBatteryOptimizations(packageName) && !prefs.getBoolean("hide_battery_tip", false)) {
            box.addView(warningCard(
                "🔋 Tips: matikan optimasi baterai",
                "Beberapa HP (Oppo, Vivo, Realme, Samsung) suka mematikan aplikasi. " +
                    "Izinkan aplikasi ini berjalan tanpa batasan baterai, dan aktifkan Autostart bila ada.",
                "Atur",
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
            row.addView(Ui.flat(this, "Sembunyikan") {
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
            d > 0 -> "${d}h ${h}j"
            h > 0 -> String.format(java.util.Locale.ROOT, "%dj %02dm", h, m)
            else -> String.format(java.util.Locale.ROOT, "%dm %02dd", m, sec)
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
