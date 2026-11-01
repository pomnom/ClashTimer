package id.clashtimer

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/** Pembantu kecil untuk menyusun tampilan lewat kode. */
object Ui {
    val TITLE_L = com.google.android.material.R.style.TextAppearance_Material3_TitleLarge
    val TITLE_M = com.google.android.material.R.style.TextAppearance_Material3_TitleMedium
    val TITLE_S = com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
    val BODY_M = com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
    val BODY_S = com.google.android.material.R.style.TextAppearance_Material3_BodySmall
    val LABEL_L = com.google.android.material.R.style.TextAppearance_Material3_LabelLarge

    const val GREEN = 0xFF2E7D32.toInt()
    const val AMBER = 0xFFE65100.toInt()
    const val GOLD = 0xFFBA7517.toInt()
    const val VIOLET = 0xFF7F77DD.toInt()
    const val BLUE = 0xFF378ADD.toInt()
    const val GREY = 0xFF888780.toInt()

    fun dp(ctx: Context, v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun text(ctx: Context, s: CharSequence, style: Int): TextView = TextView(ctx).apply {
        TextViewCompat.setTextAppearance(this, style)
        text = s
    }

    fun secondary(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)

    fun primary(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorPrimary, GREEN)

    fun onSurface(ctx: Context): Int =
        MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)

    /** Latar membulat berwarna transparan (untuk pill, banner). */
    fun tint(color: Int, radiusPx: Float, alpha: Int = 0x26): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor((color and 0x00FFFFFF) or (alpha shl 24))
            cornerRadius = radiusPx
        }

    fun outline(ctx: Context, radiusPx: Float): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = radiusPx
            setStroke(dp(ctx, 1), 0x55888888)
        }

    /** Label kecil berwarna, mis. "Apprentice +3j/hari". */
    fun pill(ctx: Context, s: String, color: Int): TextView = TextView(ctx).apply {
        text = s
        textSize = 11f
        setTextColor(color)
        background = tint(color, dp(ctx, 10).toFloat())
        setPadding(dp(ctx, 8), dp(ctx, 2), dp(ctx, 8), dp(ctx, 2))
    }

    /** Chip yang bisa ditekan (strategi, jam tidur, dll). */
    fun chip(ctx: Context, s: String, onClick: (() -> Unit)? = null): TextView = TextView(ctx).apply {
        text = s
        textSize = 13f
        setTextColor(onSurface(ctx))
        background = outline(ctx, dp(ctx, 16).toFloat())
        setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6))
        if (onClick != null) setOnClickListener { onClick() }
    }

    fun banner(ctx: Context, s: String, color: Int): TextView = TextView(ctx).apply {
        text = s
        textSize = 12f
        setTextColor(color)
        background = tint(color, dp(ctx, 8).toFloat(), 0x22)
        setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8))
        layoutParams = matchWrap(8, ctx)
    }

    /** Garis pemisah tipis antar baris dalam satu kartu. */
    fun divider(ctx: Context): View = View(ctx).apply {
        setBackgroundColor(0x22888888)
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 1).coerceAtLeast(1))
    }

    /** Judul bagian kecil: kiri judul (+ tombol "?" bila ada tutorial), kanan keterangan. */
    fun sectionRow(ctx: Context, left: String, right: String? = null, help: Help.Topic? = null, extra: (() -> String?)? = null): View {
        val row = hbox(ctx).apply { setPadding(dp(ctx, 4), dp(ctx, if (help != null) 8 else 16), dp(ctx, 4), dp(ctx, if (help != null) 0 else 6)) }
        if (help == null) row.addView(muted(ctx, left, BODY_M), weight1())
        else {
            row.addView(muted(ctx, left, BODY_M))
            row.addView(Help.button(ctx, help, extra))
            row.addView(space(ctx), weight1())
        }
        if (right != null) row.addView(muted(ctx, right, BODY_M))
        return row
    }

    fun muted(ctx: Context, s: CharSequence, style: Int = BODY_S): TextView =
        text(ctx, s, style).apply { setTextColor(secondary(ctx)) }

    /**
     * Pengisi ruang kosong. Bukan View biasa: View biasa ber-tinggi wrap_content mengambil SELURUH tinggi
     * yang tersedia (layar yang isinya pendek jadi kosong dan bagian di bawahnya hilang); Space tingginya 0.
     */
    fun space(ctx: Context): View = android.widget.Space(ctx)

    fun vbox(ctx: Context): LinearLayout = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    fun hbox(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

    fun matchWrap(bottomMarginDp: Int = 0, ctx: Context? = null): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            if (ctx != null) bottomMargin = dp(ctx, bottomMarginDp)
        }

    fun weight1(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

    /** Kartu dengan kolom isi di dalamnya. Kembalian: (kartu, kolom). */
    fun card(ctx: Context, padH: Int = 14, padV: Int = 10, tinted: Boolean = false): Pair<MaterialCardView, LinearLayout> {
        val card = MaterialCardView(ctx).apply {
            layoutParams = matchWrap(8, ctx)
            radius = dp(ctx, 16).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(if (tinted) FREE_BG else SURFACE)
            strokeWidth = if (tinted) dp(ctx, 1) else 0
            if (tinted) strokeColor = FREE_LINE
        }
        val col = vbox(ctx).apply { setPadding(dp(ctx, padH), dp(ctx, padV), dp(ctx, padH), dp(ctx, padV)) }
        card.addView(col)
        return card to col
    }

    fun section(ctx: Context, title: String, subtitle: String? = null): View {
        val col = vbox(ctx).apply { setPadding(dp(ctx, 4), dp(ctx, 18), dp(ctx, 4), dp(ctx, 6)) }
        col.addView(text(ctx, title, TITLE_M).apply { setTextColor(primary(ctx)) })
        if (subtitle != null) col.addView(muted(ctx, subtitle))
        return col
    }

    fun filled(ctx: Context, label: String, onClick: () -> Unit): MaterialButton = MaterialButton(ctx).apply {
        text = label
        setOnClickListener { onClick() }
    }

    fun outlined(ctx: Context, label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            setOnClickListener { onClick() }
        }

    fun flat(ctx: Context, label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(ctx, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = label
            setOnClickListener { onClick() }
        }

    fun bar(ctx: Context, pct: Double): ProgressBar =
        ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
            progress = (pct * 10).toInt().coerceIn(0, 1000)
            progressTintList = ColorStateList.valueOf(primary(ctx))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 8)).apply {
                topMargin = dp(ctx, 6)
                bottomMargin = dp(ctx, 4)
            }
        }

    fun pct(p: Double): String = String.format(java.util.Locale.ROOT, "%.0f%%", p.coerceIn(0.0, 100.0))

    // ---------------------------------------------------------------- palet (mockup)

    /** Tema gelap aktif (diatur [init] di onCreate tiap activity). */
    var dark = false
        private set

    fun init(ctx: Context) {
        dark = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun pick(night: Long, day: Long): Int = (if (dark) night else day).toInt()

    /** Warna status yang dipakai di semua layar: bebas, builder, lab, menganggur. */
    val FREE get() = pick(0xFF5AD08E, 0xFF2E7D32)
    val BUSY get() = pick(0xFFF0B44C, 0xFFA8650F)
    val LAB get() = pick(0xFFA895FF, 0xFF5F57C2)
    val IDLE get() = pick(0xFFFF8F5E, 0xFFD84315)
    /** Teks hijau lembut (keterangan "pas bangun"). */
    val FREE_TEXT get() = pick(0xFF7FD7A6, 0xFF2E7D32)
    val BG get() = pick(0xFF121317, 0xFFF4F5F2)
    val SURFACE get() = pick(0xFF1B1D23, 0xFFFFFFFF)
    val SURFACE2 get() = pick(0xFF23262E, 0xFFEEF0EC)
    val NAV get() = pick(0xFF17191E, 0xFFFFFFFF)
    val LINE get() = pick(0xFF262A32, 0xFFE3E5E0)
    val OUTLINE get() = pick(0xFF3A3F49, 0xFFC9CDC5)
    val FREE_BG get() = pick(0xFF1F2A23, 0xFFE6F4EA)
    val FREE_LINE get() = pick(0xFF2B3A31, 0xFFBFE0C8)
    val SELECTED_BG get() = pick(0xFF2B3A31, 0xFFD7EEDD)
    val BUSY_BG get() = pick(0xFF3A3020, 0xFFFFF0D6)
    val LAB_BG get() = pick(0xFF2C2846, 0xFFECE9FF)
    val TEXT get() = pick(0xFFEEF0F3, 0xFF1B1C18)
    val TEXT2 get() = pick(0xFFA7ADB8, 0xFF5F6159)
    val ON_FREE get() = pick(0xFF0D1F15, 0xFFFFFFFF)

    /** Panel membulat (kartu ringan). [stroke] = garis tepi opsional. */
    fun panel(ctx: Context, padH: Int = 14, padV: Int = 12, color: Int = SURFACE, stroke: Int? = null, radiusDp: Int = 16): LinearLayout =
        vbox(ctx).apply {
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(color)
                cornerRadius = dp(ctx, radiusDp).toFloat()
                if (stroke != null) setStroke(dp(ctx, 1), stroke)
            }
            setPadding(dp(ctx, padH), dp(ctx, padV), dp(ctx, padH), dp(ctx, padV))
        }

    fun rounded(color: Int, radiusPx: Float, stroke: Int? = null, strokePx: Int = 0) =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
            if (stroke != null) setStroke(strokePx, stroke)
        }

    /** Efek sentuh standar untuk view yang bisa diketuk. */
    fun ripple(v: View) {
        val tv = android.util.TypedValue()
        if (v.context.theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)) v.foreground = v.context.getDrawable(tv.resourceId)
    }

    /** Label pekerja: "B4", "Lab", "M1", "Star". */
    fun badge(ctx: Context, text: String, color: Int, bg: Int): TextView = TextView(ctx).apply {
        this.text = text
        textSize = 11f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
        setTextColor(color)
        gravity = Gravity.CENTER
        minWidth = dp(ctx, 26)
        minimumWidth = dp(ctx, 26)
        includeFontPadding = false
        background = rounded(bg, dp(ctx, 6).toFloat())
        setPadding(dp(ctx, 5), dp(ctx, 3), dp(ctx, 5), dp(ctx, 3))
    }

    fun builderBadge(ctx: Context, text: String) = badge(ctx, text, BUSY, BUSY_BG)
    fun labBadge(ctx: Context, text: String) = badge(ctx, text, LAB, LAB_BG)

    /** Teks angka (hitung mundur, jam) dengan lebar digit tetap. */
    fun monoText(ctx: Context, s: CharSequence, sp: Float, bold: Boolean = true): TextView = TextView(ctx).apply {
        text = s
        textSize = sp
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        setTextColor(TEXT)
        includeFontPadding = false
    }

    /** Tombol utama (hijau, 44dp) dan tombol kedua (garis). */
    fun primary(ctx: Context, label: String, onClick: () -> Unit): MaterialButton = MaterialButton(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        cornerRadius = dp(ctx, 12)
        backgroundTintList = ColorStateList.valueOf(FREE)
        setTextColor(ON_FREE)
        minHeight = dp(ctx, 48)
        insetTop = 0
        insetBottom = 0
        setOnClickListener { onClick() }
    }

    fun secondary(ctx: Context, label: String, onClick: () -> Unit): MaterialButton =
        MaterialButton(ctx, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            cornerRadius = dp(ctx, 12)
            strokeColor = ColorStateList.valueOf(OUTLINE)
            setTextColor(TEXT)
            minHeight = dp(ctx, 48)
            insetTop = 0
            insetBottom = 0
            setOnClickListener { onClick() }
        }

    /** Tombol lembut (latar hijau tipis), mis. "Pakai boost", "Update dari export". */
    fun tonal(ctx: Context, label: String, onClick: () -> Unit): MaterialButton = MaterialButton(ctx).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        cornerRadius = dp(ctx, 12)
        backgroundTintList = ColorStateList.valueOf(SELECTED_BG)
        setTextColor(FREE_TEXT)
        minHeight = dp(ctx, 48)
        insetTop = 0
        insetBottom = 0
        setOnClickListener { onClick() }
    }

    /** Judul bagian tebal: kiri judul (+ ?), kanan keterangan kecil. */
    fun heading(ctx: Context, left: CharSequence, right: String? = null, help: Help.Topic? = null): View {
        val row = hbox(ctx).apply { setPadding(dp(ctx, 2), dp(ctx, 14), dp(ctx, 2), dp(ctx, if (help != null) 0 else 6)) }
        row.addView(text(ctx, left, TITLE_S).apply { textSize = 15f; setTypeface(typeface, android.graphics.Typeface.BOLD); setTextColor(TEXT) })
        if (help != null) row.addView(Help.button(ctx, help))
        row.addView(space(ctx), weight1())
        if (right != null) row.addView(muted(ctx, right).apply { textSize = 12f })
        return row
    }

    /** Segmented penuh lebar: pilihan sama lebar, yang aktif hijau tipis. */
    fun segmentedWide(ctx: Context, labels: List<String>, selected: Int, heightDp: Int = 40, onSelect: (Int) -> Unit): LinearLayout {
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            background = rounded(SURFACE, dp(ctx, 12).toFloat())
            setPadding(dp(ctx, 4), dp(ctx, 4), dp(ctx, 4), dp(ctx, 4))
        }
        labels.forEachIndexed { i, l ->
            val on = i == selected
            box.addView(TextView(ctx).apply {
                text = l
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(if (on) FREE_TEXT else TEXT2)
                if (on) {
                    background = rounded(SELECTED_BG, dp(ctx, 9).toFloat())
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                isClickable = true
                setOnClickListener { if (!on) onSelect(i) }
            }, LinearLayout.LayoutParams(0, dp(ctx, heightDp - 8), 1f))
        }
        return box
    }

    /** Ikon game (ClashKing) dalam kotak + label pekerja di pojok kiri bawah. */
    fun gameIcon(ctx: Context, path: String?, name: String, sizeDp: Int, badge: TextView? = null): View {
        // Bingkai sedikit lebih besar dari ikon (kiri & bawah) supaya label pekerja menonjol tanpa terpotong.
        val padL = if (badge != null) 4 else 0
        val padB = if (badge != null) 5 else 0
        val frame = android.widget.FrameLayout(ctx)
        val box = android.widget.FrameLayout(ctx).apply { background = rounded(SURFACE2, dp(ctx, if (sizeDp >= 48) 12 else 10).toFloat()) }
        val inner = (sizeDp * 0.84).toInt()
        box.addView(Icons.view(ctx, path, inner, Icons.initials(name), round = false),
            android.widget.FrameLayout.LayoutParams(dp(ctx, inner), dp(ctx, inner), Gravity.CENTER))
        frame.addView(box, android.widget.FrameLayout.LayoutParams(dp(ctx, sizeDp), dp(ctx, sizeDp), Gravity.TOP or Gravity.END))
        if (badge != null) {
            badge.background = (badge.background as? android.graphics.drawable.GradientDrawable)?.apply { setStroke(dp(ctx, 2), SURFACE) }
            frame.addView(badge, android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.WRAP_CONTENT, android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.START,
            ))
        }
        frame.layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp + padL), dp(ctx, sizeDp + padB))
        return frame
    }

    /** Baris kecil berikon: centang hijau ("pas bangun") atau peringatan oranye ("menganggur"). */
    fun outcome(ctx: Context, s: String, good: Boolean): TextView = TextView(ctx).apply {
        text = (if (good) "✓ " else "⚠ ") + s
        textSize = 13f
        setTextColor(if (good) FREE_TEXT else IDLE)
    }

    /** Ikon vektor kecil yang diwarnai (opsional bisa ditekan). */
    fun icon(ctx: Context, res: Int, sizeDp: Int, color: Int, desc: String? = null, onClick: (() -> Unit)? = null): android.widget.ImageView =
        android.widget.ImageView(ctx).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(color)
            contentDescription = desc
            val pad = if (onClick != null) dp(ctx, 8) else 0
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(dp(ctx, sizeDp) + 2 * pad, dp(ctx, sizeDp) + 2 * pad)
            if (onClick != null) {
                isClickable = true
                setOnClickListener { onClick() }
                val tv = android.util.TypedValue()
                if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)) setBackgroundResource(tv.resourceId)
            }
        }

    /** Tombol pilihan kecil berdampingan, mis. Home | BB. */
    fun segmented(ctx: Context, labels: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val box = hbox(ctx).apply { background = outline(ctx, dp(ctx, 8).toFloat()) }
        labels.forEachIndexed { i, l ->
            val on = i == selected
            box.addView(TextView(ctx).apply {
                text = l
                textSize = 12f
                gravity = Gravity.CENTER
                setPadding(dp(ctx, 10), dp(ctx, 5), dp(ctx, 10), dp(ctx, 5))
                setTextColor(if (on) FREE else secondary(ctx))
                if (on) {
                    background = tint(FREE, dp(ctx, 8).toFloat(), 0x2E)
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                }
                setOnClickListener { if (!on) onSelect(i) }
            })
        }
        return box
    }

    /** Latar kotak status (grid pekerja, Semua desa). */
    fun tileBg(ctx: Context, color: Int? = null): android.graphics.drawable.GradientDrawable =
        if (color != null) tint(color, dp(ctx, 10).toFloat(), 0x22) else tint(GREY, dp(ctx, 10).toFloat(), 0x14)

    /** Bar progres tipis berwarna. */
    fun thinBar(ctx: Context, frac: Double, color: Int): View {
        val track = android.widget.FrameLayout(ctx).apply {
            background = tint(GREY, dp(ctx, 2).toFloat(), 0x33)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, 4)).apply { topMargin = dp(ctx, 6) }
        }
        val fill = View(ctx).apply { background = tint(color, dp(ctx, 2).toFloat(), 0xFF) }
        track.addView(fill, android.widget.FrameLayout.LayoutParams(0, android.widget.FrameLayout.LayoutParams.MATCH_PARENT))
        track.addOnLayoutChangeListener { v, l, _, r, _, _, _, _, _ ->
            val w = ((r - l) * frac.coerceIn(0.0, 1.0)).toInt()
            if (fill.layoutParams.width != w) { fill.layoutParams.width = w; v.post { fill.requestLayout() } }
        }
        return track
    }
}
