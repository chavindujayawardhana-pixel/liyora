package com.liyora.app.accessibility

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

sealed interface PanelState {
    data object Loading : PanelState
    data class Result(val english: String) : PanelState
    data class Message(val text: String) : PanelState
    data object NeedsKey : PanelState
}

/** The panel shown just above the keyboard. Built from plain Views because it lives in a service window. */
class TranslationPanel(
    private val context: Context,
    private val onClose: () -> Unit,
    private val onApply: (String) -> Unit,
    private val onOpenApp: () -> Unit,
) {
    private val density = context.resources.displayMetrics.density

    private val loadingRow: LinearLayout
    private val bodyScroll: ScrollView
    private val bodyText: TextView
    private val primaryButton: TextView

    val root: FrameLayout = FrameLayout(context).apply {
        setPadding(dp(8), dp(10), dp(8), dp(6))
        clipToPadding = false
    }

    init {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Color.WHITE, 20f)
            elevation = dp(10).toFloat()
            setPadding(dp(16), dp(8), dp(8), dp(14))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("Liyora", 16f, BubbleView.LIYORA_RED, bold = true))
        header.addView(
            text("Singlish to English", 12f, BubbleView.LIYORA_RED).apply {
                background = rounded(CHIP_BG, 12f)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            },
            LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(10) },
        )
        header.addView(View(context), LinearLayout.LayoutParams(0, 1, 1f))
        header.addView(
            text("✕", 18f, MUTED).apply {
                gravity = Gravity.CENTER
                contentDescription = "Close"
                background = RippleDrawable(ColorStateList.valueOf(RIPPLE), null, null)
                setOnClickListener { onClose() }
            },
            LinearLayout.LayoutParams(dp(44), dp(44)),
        )
        card.addView(header)

        loadingRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
            addView(
                ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
                    indeterminateTintList = ColorStateList.valueOf(BubbleView.LIYORA_RED)
                },
                LinearLayout.LayoutParams(dp(20), dp(20)),
            )
            addView(
                text("Translating…", 15f, MUTED),
                LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(10) },
            )
        }
        card.addView(loadingRow)

        bodyText = text("", 16f, TEXT).apply {
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(4), dp(8), dp(4))
        }
        bodyScroll = MaxHeightScrollView(context, dp(180)).apply { addView(bodyText) }
        card.addView(bodyScroll, LinearLayout.LayoutParams(MATCH, WRAP))

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, dp(12), dp(8), 0)
        }
        buttons.addView(
            button("Dismiss", MUTED, filled = false).apply { setOnClickListener { onClose() } },
        )
        primaryButton = button("Apply", Color.WHITE, filled = true)
        buttons.addView(primaryButton, LinearLayout.LayoutParams(WRAP, WRAP).apply { marginStart = dp(8) })
        card.addView(buttons, LinearLayout.LayoutParams(MATCH, WRAP))

        root.addView(card, FrameLayout.LayoutParams(MATCH, WRAP))
    }

    fun render(state: PanelState) {
        loadingRow.visibility = if (state == PanelState.Loading) View.VISIBLE else View.GONE
        bodyScroll.visibility = if (state == PanelState.Loading) View.GONE else View.VISIBLE
        primaryButton.visibility = View.VISIBLE
        primaryButton.isEnabled = true
        primaryButton.alpha = 1f
        when (state) {
            PanelState.Loading -> {
                primaryButton.text = "Apply"
                primaryButton.isEnabled = false
                primaryButton.alpha = 0.4f
            }
            is PanelState.Result -> {
                bodyText.text = state.english
                primaryButton.text = "Apply"
                primaryButton.setOnClickListener { onApply(state.english) }
            }
            is PanelState.Message -> {
                bodyText.text = state.text
                primaryButton.visibility = View.GONE
            }
            PanelState.NeedsKey -> {
                bodyText.text = "Add your Gemini key in the Liyora app first."
                primaryButton.text = "Open Liyora"
                primaryButton.setOnClickListener { onOpenApp() }
            }
        }
        bodyScroll.scrollTo(0, 0)
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, textColor: Int, filled: Boolean) = text(label, 15f, textColor, bold = true).apply {
        gravity = Gravity.CENTER
        minWidth = dp(96)
        setPadding(dp(18), dp(10), dp(18), dp(10))
        val shape = if (filled) {
            rounded(BubbleView.LIYORA_RED, 22f)
        } else {
            rounded(Color.WHITE, 22f).apply { setStroke(dp(1), OUTLINE) }
        }
        background = RippleDrawable(ColorStateList.valueOf(RIPPLE), shape, null)
    }

    private fun rounded(color: Int, radiusDp: Float): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * density
    }

    private fun dp(value: Int) = (value * density).toInt()

    /** ScrollView that wraps its content but never grows beyond [maxHeightPx]. */
    private class MaxHeightScrollView(context: Context, private val maxHeightPx: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST))
        }
    }

    private companion object {
        const val WRAP = LinearLayout.LayoutParams.WRAP_CONTENT
        const val MATCH = LinearLayout.LayoutParams.MATCH_PARENT
        const val TEXT = 0xFF1C1B1F.toInt()
        const val MUTED = 0xFF5F6368.toInt()
        const val OUTLINE = 0xFFDADCE0.toInt()
        const val CHIP_BG = 0xFFFDE8EA.toInt()
        const val RIPPLE = 0x22000000
    }
}
