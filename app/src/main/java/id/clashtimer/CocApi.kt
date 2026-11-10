package id.clashtimer

import android.content.Context
import id.clashtimer.core.ClashKing
import id.clashtimer.core.DAY
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Status War / CWL dari API publik ClashKing (tanpa API key). Hanya ditampilkan sebagai info: tidak ada status
 * serangan, dan rencana tidak menahan hero karena war/CWL. Selama CWL, nama pemain, nama clan & liga diambil
 * dari grup CWL (paling sering sekali sehari, karena ukurannya besar); nama yang belum ada diambil dari pencarian pemain.
 */
object ApiSync {
    private val exec = Executors.newSingleThreadExecutor()
    /** Sudah ada sinkron yang menunggu giliran (yang sedang berjalan + satu antrean cukup). */
    private val queued = AtomicBoolean(false)

    /** Sinkron otomatis saat aplikasi dibuka, paling sering tiap 30 menit. */
    fun maybeSync(act: MainActivity) {
        Store.dropApiData(act)
        if (Store.accounts(act).isEmpty()) return
        if (System.currentTimeMillis() - Store.apiTried(act) < 30 * 60_000L) return
        syncAsync(act) { act.dataChanged() }
    }

    /** Setelah desa ditambah / di-update: langsung sinkron (tanpa menunggu 30 menit). */
    fun afterImport(act: MainActivity) {
        syncAsync(act) { act.dataChanged() }
    }

    /**
     * Jalankan di latar; [onDone] dipanggil di thread UI dengan pesan status. Bila sinkron lain sedang
     * berjalan, yang ini menunggu gilirannya (supaya desa yang baru ditambah ikut terbaca).
     */
    fun syncAsync(act: android.app.Activity, onDone: (String) -> Unit) {
        if (!queued.compareAndSet(false, true)) return
        val app = act.applicationContext
        exec.execute {
            queued.set(false)
            val msg = try {
                sync(app)
            } catch (e: Throwable) {
                e.message ?: e.javaClass.simpleName
            }
            act.runOnUiThread {
                if (!act.isFinishing && !act.isDestroyed) onDone(msg)
            }
        }
    }

    fun sync(ctx: Context): String {
        val now = System.currentTimeMillis()
        var ok = 0
        var failed: String? = null
        val groups = HashMap<String, String?>()   // tag clan → JSON grup CWL (sekali per sinkron)
        for (acc in Store.accounts(ctx)) {
            val timers = try {
                ClashKing.timers(acc.tag)
            } catch (e: java.io.IOException) {
                failed = e.message ?: e.javaClass.simpleName
                continue
            }
            ok++
            // Nama akun dari pencarian ClashKing bila belum ada (di luar CWL tidak ada sumber lain).
            if (!Store.hasName(ctx, acc.tag)) runCatching { ClashKing.player(acc.tag) }.getOrNull()?.let { Store.setCkName(ctx, acc.tag, it.name) }
            // Event yang sedang diikuti (war / CWL), urut dari yang paling dulu berakhir.
            val wars = timers.filter { (it.type == "war" || it.type == "cwl") && it.expiresAt > now }.sortedBy { it.expiresAt }
            val first = wars.firstOrNull()
            Store.setWarState(ctx, acc.tag, first?.let {
                Store.WarState(it.type, "", it.expiresAt, 0, true, wars.getOrNull(1)?.expiresAt ?: 0L, 0, 0, now)
            })
            // Clan: yang muncul di semua war; bila baru satu war CWL, dipastikan dari roster grup CWL.
            val cwlClans = wars.firstOrNull { it.type == "cwl" }?.clans.orEmpty()
            var clan = ClashKing.clanOf(timers)
            val needGroup = cwlClans.isNotEmpty() && now - Store.ckChecked(ctx, acc.tag) > DAY
            if ((clan == null && cwlClans.size == 2) || needGroup) {
                val key = clan ?: cwlClans.first()
                val json = groups.getOrPut(key) { runCatching { ClashKing.get("/cwl/${ClashKing.pathTag(key)}/group") }.getOrNull() }
                if (json != null) {
                    val me = "#" + ClashKing.pathTag(acc.tag)
                    if (clan == null) clan = cwlClans.firstOrNull { c -> ClashKing.parseCwlGroup(json, c)?.lineup?.contains(me) == true }
                    if (clan != null) ClashKing.parseCwlGroup(json, clan)?.let { g ->
                        Store.setCkInfo(ctx, acc.tag, g.names[me].orEmpty(), clan, g.clanName, g.league, now)
                    }
                }
            }
        }
        val msg = when {
            ok > 0 && failed == null -> tr("Status War/CWL diperbarui · $ok desa", "War/CWL status updated · $ok villages")
            ok > 0 -> tr("Diperbarui $ok desa · sebagian gagal: $failed", "Updated $ok villages · some failed: $failed")
            failed != null -> tr("ClashKing tidak bisa dihubungi: $failed", "Could not reach ClashKing: $failed")
            else -> tr("Belum ada desa", "No villages yet")
        }
        Store.setApiResult(ctx, msg, now)
        Repo.rebuildGlobalReminders(ctx)
        return msg
    }
}
