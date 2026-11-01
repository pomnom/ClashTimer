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

    /** Judul bagian kecil: kiri judul, kanan keterangan. */
    fun sectionRow(ctx: Context, left: String, right: String? = null): View {
        val row = hbox(ctx).apply { setPadding(dp(ctx, 4), dp(ctx, 16), dp(ctx, 4), dp(ctx, 6)) }
        row.addView(muted(ctx, left, BODY_M), weight1())
        if (right != null) row.addView(muted(ctx, right, BODY_M))
        return row
    }

    fun muted(ctx: Context, s: CharSequence, style: Int = BODY_S): TextView =
        text(ctx, s, style).apply { setTextColor(secondary(ctx)) }

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
            layoutParams = matchWrap(6, ctx)
            if (tinted) {
                setCardBackgroundColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorSecondaryContainer))
                strokeWidth = 0
            }
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
}
