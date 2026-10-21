package com.liyora.app.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

sealed interface PanelState {
    data object Loading : PanelState
    data class Result(val english: String) : PanelState

    /** [offerOpenApp] adds an "Open Liyora" button, e.g. when no key is saved. */
    data class Message(val text: String, val offerOpenApp: Boolean = false) : PanelState
}

/** The card shown above the keys with the English text and the Apply / Dismiss buttons. */
class TranslatePanel(
    private val context: Context,
    private val style: KeyboardStyle,
    private val onApply: (String) -> Unit,
    private val onDismiss: () -> Unit,
    private val onOpenApp: () -> Unit,
) {
    private val loadingRow: LinearLayout
    private val bodyScroll: ScrollView
    private val bodyText: TextView
    private val openAppButton: TextView
    private val applyButton: TextView

    /** The English text waiting for Apply. Cleared when the panel closes. */
    private var english: String? = null

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(style.background)
        setPadding(style.dp(6), style.dp(6), style.dp(6), 0)
        visibility = View.GONE
    }

    init {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = style.rounded(style.panel, 14f)
            setPadding(style.dp(14), style.dp(10), style.dp(10), style.dp(10))
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(text("λ Liyora", 15f, style.red, bold = true))
        header.addView(
            text("Singlish to English", 12f, style.red).apply {
                background = style.rounded(style.chip, 10f)
                setPadding(style.dp(8), style.dp(3), style.dp(8), style.dp(3))
            },
            LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = style.dp(8) },
        )
        card.addView(header)

        loadingRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, style.dp(10), 0, style.dp(6))
            addView(
                ProgressBar(context).apply {
                    isIndeterminate = true
                    indeterminateTintList = ColorStateList.valueOf(style.red)
                },
                LinearLayout.LayoutParams(style.dp(20), style.dp(20)),
            )
            addView(
                text("Translating…", 14f, style.muted),
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { marginStart = style.dp(10) },
            )
        }
        card.addView(loadingRow)

        bodyText = text("", 16f, style.panelText).apply {
            setTextIsSelectable(false)
            setLineSpacing(0f, 1.15f)
        }
        bodyScroll = MaxHeightScrollView(context, style.dp(if (style.landscape) 56 else 120)).apply {
            addView(bodyText, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setPadding(0, style.dp(8), 0, style.dp(4))
        }
        card.addView(bodyScroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        val buttons = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(0, style.dp(6), 0, 0)
        }
        openAppButton = button("Open Liyora", filled = false) { onOpenApp() }
        val dismissButton = button("Dismiss", filled = false) { onDismiss() }
        applyButton = button("Apply", filled = true) { english?.let(onApply) }
        buttons.addView(openAppButton, buttonParams())
        buttons.addView(dismissButton, buttonParams())
        buttons.addView(applyButton, buttonParams())
        card.addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(card, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    val isShowing: Boolean get() = root.visibility == View.VISIBLE

    fun show(state: PanelState) {
        root.visibility = View.VISIBLE
        loadingRow.visibility = if (state is PanelState.Loading) View.VISIBLE else View.GONE
        bodyScroll.visibility = if (state is PanelState.Loading) View.GONE else View.VISIBLE
        applyButton.visibility = if (state is PanelState.Result) View.VISIBLE else View.GONE
        openAppButton.visibility = if (state is PanelState.Message && state.offerOpenApp) View.VISIBLE else View.GONE
        english = (state as? PanelState.Result)?.english
        when (state) {
            PanelState.Loading -> bodyText.text = ""
            is PanelState.Result -> {
                bodyText.text = state.english
                bodyText.setTextColor(style.panelText)
            }
            is PanelState.Message -> {
                bodyText.text = state.text
                bodyText.setTextColor(style.muted)
            }
        }
        bodyScroll.scrollTo(0, 0)
    }

    fun hide() {
        root.visibility = View.GONE
        english = null
        bodyText.text = ""
    }

    private fun text(value: String, sizeSp: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, filled: Boolean, action: () -> Unit) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        minWidth = style.dp(84)
        setPadding(style.dp(16), 0, style.dp(16), 0)
        if (filled) {
            setTextColor(Color.WHITE)
            background = style.pressable(style.red, style.redPressed, radiusDp = 20f)
        } else {
            setTextColor(style.red)
            background = style.pressable(style.panel, style.chip, radiusDp = 20f)
        }
        setOnClickListener { action() }
    }

    private fun buttonParams() = LinearLayout.LayoutParams(WRAP_CONTENT, style.dp(40)).apply {
        marginStart = style.dp(6)
    }

    /** A ScrollView that stops growing at [maxHeight], so a long translation can't push the keys away. */
    private class MaxHeightScrollView(context: Context, private val maxHeight: Int) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(maxHeight, MeasureSpec.AT_MOST))
        }
    }
}
