package id.clashtimer

import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import id.clashtimer.core.Fmt
import id.clashtimer.core.Json
import id.clashtimer.core.arr
import id.clashtimer.core.int
import id.clashtimer.core.obj
import id.clashtimer.core.str
import org.json.JSONObject

/**
 * Kalkulator ore & equipment hero: level equipment dari export (dan API bila lebih baru), batas level dari
 * Blacksmith & Town Hall, jumlah ore yang dimiliki + pemasukan per minggu → kebutuhan & perkiraan minggu.
 */
class OreActivity : AppCompatActivity() {

    /** Biaya ore per level: [shiny, glowy, starry] untuk naik DARI level itu; [req] = Blacksmith & TH untuk MEMILIKI level itu. */
    private class Lvl(val level: Int, val cost: IntArray, val blacksmith: Int, val th: Int)
    private class Equip(val id: Int, val name: String, val hero: String, val rarity: String, val levels: List<Lvl>)

    private lateinit var box: LinearLayout
    private lateinit var tag: String
    private val equips = ArrayList<Equip>()
    private val current = HashMap<Int, Int>()
    private var blacksmith = 0
    private var th = 0
    private lateinit var state: JSONObject
    private lateinit var summary: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        tag = intent.getStringExtra(EXTRA_TAG) ?: Store.selectedTag(this) ?: run { finish(); return }
        title = "⚒️ Ore · ${Store.villageName(this, tag)}"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        box = Ui.vbox(this).apply { setPadding(Ui.dp(this@OreActivity, 16), 0, Ui.dp(this@OreActivity, 16), Ui.dp(this@OreActivity, 32)) }
        setContentView(ScrollView(this).apply { setBackgroundColor(Ui.BG); addView(box) })
        load()
        render()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun load() {
        val root = runCatching { Json.parse(assets.open("equipment.json").bufferedReader().readText()).obj() }.getOrNull()
        for (raw in root?.get("items").arr().orEmpty()) {
            val o = raw.obj() ?: continue
            val lv = o["lv"].arr().orEmpty().mapNotNull { l ->
                val a = l.arr() ?: return@mapNotNull null
                if (a.size < 6) null else Lvl(a[0].int(), intArrayOf(a[1].int(), a[2].int(), a[3].int()), a[4].int(), a[5].int())
            }
            equips += Equip(o["id"].int(), o["n"].str(), o["h"].str(), o["r"].str(), lv)
        }
        val v = Repo.village(this, tag)
        th = v?.th ?: 0
        blacksmith = v?.level(BLACKSMITH) ?: 0
        v?.equipment?.let { current.putAll(it) }
        // API bisa lebih baru dari export: ambil level tertinggi.
        val api = Store.apiStats(this, tag)?.optJSONObject("equipment")
        if (api != null) for (e in equips) {
            val l = api.optInt(e.name, 0)
            if (l > (current[e.id] ?: 0)) current[e.id] = l
        }
        state = Store.oreState(this, tag)
        if (!state.has("sel")) {
            // Awal: equipment yang sedang dipakai hero (dari API), bila ada.
            val sel = JSONObject()
            Store.apiStats(this, tag)?.optJSONArray("equipped")?.let { a ->
                for (i in 0 until a.length()) equips.firstOrNull { it.name == a.optString(i) }?.let { sel.put(it.id.toString(), 0) }
            }
            state.put("sel", sel)
        }
    }

    private fun save() = Store.setOreState(this, tag, state)

    /** Level tertinggi yang bisa dicapai dengan Blacksmith & Town Hall sekarang. */
    private fun maxFor(e: Equip): Int =
        e.levels.filter { it.blacksmith <= blacksmith && it.th <= th }.maxOfOrNull { it.level } ?: (current[e.id] ?: 1)

    private fun targetOf(e: Equip): Int {
        val t = state.optJSONObject("sel")?.optInt(e.id.toString(), 0) ?: 0
        val max = maxFor(e)
        return if (t <= 0) max else t.coerceAtMost(max)
    }

    private fun need(e: Equip, from: Int, to: Int): IntArray {
        val sum = IntArray(3)
        for (l in e.levels) if (l.level in from until to) for (k in 0..2) sum[k] += l.cost[k]
        return sum
    }

    private fun render() {
        box.removeAllViews()
        box.addView(Ui.muted(this, tr("Level equipment dari export", "Equipment levels from the export") +
            (if (Store.apiStats(this, tag) != null) " & API" else "") + " · Blacksmith Lv$blacksmith · TH$th. " +
            tr("Centang equipment yang ingin dinaikkan, ketuk barisnya untuk memilih level target.",
                "Tick the equipment you want to upgrade, tap its row to pick the target level.")).apply {
            setPadding(Ui.dp(this@OreActivity, 4), Ui.dp(this@OreActivity, 12), Ui.dp(this@OreActivity, 4), Ui.dp(this@OreActivity, 4))
        })
        summary = Ui.vbox(this)
        box.addView(summary)
        inputs()
        val owned = equips.filter { (current[it.id] ?: 0) > 0 }
        if (owned.isEmpty()) {
            box.addView(Ui.muted(this, tr("Belum ada data equipment. Import ulang export desa (atau isi API key) lalu buka lagi.", "No equipment data yet. Re-import the village export (or set an API key) and open this again."), Ui.BODY_M))
            return
        }
        for ((hero, list) in owned.groupBy { it.hero }) {
            box.addView(Ui.sectionRow(this, hero, null))
            val (card, col) = Ui.card(this, padV = 4)
            list.sortedBy { it.name }.forEachIndexed { i, e ->
                if (i > 0) col.addView(Ui.divider(this))
                col.addView(row(e))
            }
            box.addView(card)
        }
        updateSummary()
    }

    private fun row(e: Equip): LinearLayout {
        val cur = current[e.id] ?: 1
        val max = maxFor(e)
        val sel = state.optJSONObject("sel")?.has(e.id.toString()) == true
        val r = Ui.hbox(this).apply { setPadding(0, Ui.dp(this@OreActivity, 6), 0, Ui.dp(this@OreActivity, 6)) }
        val cb = CheckBox(this).apply {
            isChecked = sel
            isEnabled = cur < max
            setOnCheckedChangeListener { _, on ->
                val s = state.optJSONObject("sel") ?: JSONObject().also { state.put("sel", it) }
                if (on) s.put(e.id.toString(), 0) else s.remove(e.id.toString())
                save()
                updateSummary()
            }
        }
        r.addView(cb)
        val col = Ui.vbox(this)
        col.addView(Ui.text(this, "${e.name}${if (e.rarity == "Epic") " ✦" else ""}", Ui.TITLE_S))
        val target = targetOf(e)
        val n = need(e, cur, target)
        col.addView(Ui.muted(this, if (cur >= max) tr("Lv $cur · max untuk Blacksmith/TH sekarang", "Lv $cur · max for the current Blacksmith/TH")
        else "Lv $cur → $target (max $max) · ${ore(n)}"))
        r.addView(col, Ui.weight1())
        r.isClickable = cur < max
        r.setOnClickListener {
            val opts = ((cur + 1)..max).toList()
            MaterialAlertDialogBuilder(this)
                .setTitle(tr("${e.name}: level target", "${e.name}: target level"))
                .setItems(opts.map { "Lv $it · ${ore(need(e, cur, it))}" as CharSequence }.toTypedArray()) { _, w ->
                    val s = state.optJSONObject("sel") ?: JSONObject().also { state.put("sel", it) }
                    s.put(e.id.toString(), opts[w])
                    save()
                    render()
                }
                .setNegativeButton(tr("Batal", "Cancel"), null)
                .show()
        }
        return r
    }

    private fun ore(n: IntArray): String =
        listOfNotNull(
            n[0].takeIf { it > 0 }?.let { "${fmt(it)} Shiny" },
            n[1].takeIf { it > 0 }?.let { "${fmt(it)} Glowy" },
            n[2].takeIf { it > 0 }?.let { "${fmt(it)} Starry" },
        ).joinToString(" · ").ifBlank { "0" }

    private fun fmt(n: Int) = String.format(Fmt.locale, "%,d", n)

    private val keys = listOf("shiny", "glowy", "starry")
    private val labels = listOf("Shiny", "Glowy", "Starry")

    private fun inputs() {
        box.addView(Ui.sectionRow(this, tr("Ore Anda", "Your ore"), tr("dimiliki · per minggu", "owned · per week")))
        val (card, col) = Ui.card(this)
        for (k in 0..2) {
            val r = Ui.hbox(this)
            r.addView(Ui.text(this, labels[k], Ui.BODY_M), LinearLayout.LayoutParams(Ui.dp(this, 64), LinearLayout.LayoutParams.WRAP_CONTENT))
            r.addView(field("own_${keys[k]}", "dimiliki"), Ui.weight1())
            r.addView(field("inc_${keys[k]}", tr("per minggu", "per week")), Ui.weight1().apply { marginStart = Ui.dp(this@OreActivity, 8) })
            col.addView(r)
        }
        col.addView(Ui.muted(this, tr("Pemasukan per minggu: perkiraan Anda (Star Bonus, toko Raid, event, Trader).", "Income per week: your estimate (Star Bonus, Raid shop, events, Trader).")))
        box.addView(card)
    }

    private fun field(key: String, hint: String): EditText = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER
        setSingleLine(true)
        val v = state.optInt(key, 0)
        if (v > 0) setText(v.toString())
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                state.put(key, s?.toString()?.toIntOrNull() ?: 0)
                save()
                updateSummary()
            }
        })
    }

    private fun updateSummary() {
        summary.removeAllViews()
        val sel = state.optJSONObject("sel") ?: JSONObject()
        val total = IntArray(3)
        var count = 0
        for (e in equips) {
            if (!sel.has(e.id.toString())) continue
            val cur = current[e.id] ?: continue
            val n = need(e, cur, targetOf(e))
            for (k in 0..2) total[k] += n[k]
            count++
        }
        val (card, col) = Ui.card(this, tinted = true)
        col.addView(Ui.text(this, if (count == 0) tr("Belum ada equipment yang dicentang", "No equipment ticked yet") else tr("Butuh untuk $count equipment", "Needed for $count equipment"), Ui.TITLE_S))
        if (count > 0) {
            col.addView(Ui.text(this, ore(total), Ui.BODY_M))
            var weeks = 0.0
            var unknown = false
            val lack = IntArray(3)
            for (k in 0..2) {
                val own = state.optInt("own_${keys[k]}", 0)
                lack[k] = (total[k] - own).coerceAtLeast(0)
                if (lack[k] > 0) {
                    val inc = state.optInt("inc_${keys[k]}", 0)
                    if (inc <= 0) unknown = true else weeks = maxOf(weeks, lack[k].toDouble() / inc)
                }
            }
            col.addView(Ui.muted(this, if (lack.all { it == 0 }) tr("✓ Ore yang dimiliki sudah cukup", "✓ The ore you own is enough") else tr("Kurang: ${ore(lack)}", "Missing: ${ore(lack)}")))
            if (lack.any { it > 0 }) {
                col.addView(Ui.text(this, if (unknown) tr("Isi pemasukan per minggu untuk perkiraan waktu", "Fill in income per week for a time estimate")
                else tr("Perkiraan: ±${Math.ceil(weeks).toInt()} minggu lagi", "Estimate: ±${Math.ceil(weeks).toInt()} more weeks"), Ui.BODY_M).apply { setTextColor(Ui.primary(this@OreActivity)) })
            }
        }
        summary.addView(card, Ui.matchWrap().apply { topMargin = Ui.dp(this@OreActivity, 8) })
    }

    companion object {
        const val EXTRA_TAG = "ore_tag"
        const val BLACKSMITH = 1000070
    }
}
