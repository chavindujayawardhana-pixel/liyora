package com.liyora.app.keyboard

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.liyora.app.R

/**
 * The keys, built from plain Views (no XML, no Compose). Keys fire when the finger lifts, except
 * backspace, which fires on touch and repeats while held.
 */
@SuppressLint("ViewConstructor")
class KeyboardView(
    context: Context,
    private val style: KeyboardStyle,
    private val actions: Actions,
) : LinearLayout(context) {

    interface Actions {
        fun onText(text: String)
        fun onBackspace()
        fun onEnter()
        fun onTranslate()
        fun onSwitchKeyboard()
    }

    private enum class Shift { OFF, ON, LOCKED }

    private var shift = Shift.OFF
    private var symbols = false
    private var lastShiftTap = 0L

    /** Null shows the return arrow; otherwise the field's action, e.g. "Send". */
    private var enterLabel: String? = null

    private val letterKeys = mutableListOf<Pair<TextView, Char>>()
    private var shiftKey: ImageView? = null

    private val handler = Handler(Looper.getMainLooper())
    private var repeat: Runnable? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(style.background)
        setPadding(style.dp(3), style.dp(4), style.dp(3), style.dp(6))
        build()
    }

    /** Called for every field. [newField] goes back to the letter keys. */
    fun configure(enterLabel: String?, newField: Boolean) {
        this.enterLabel = enterLabel
        if (newField) {
            symbols = false
            shift = Shift.OFF
        }
        build()
    }

    /** Capitalizes the next letter at the start of a sentence, unless caps lock is on. */
    fun setAutoShift(on: Boolean) {
        if (shift == Shift.LOCKED) return
        val next = if (on) Shift.ON else Shift.OFF
        if (next != shift) {
            shift = next
            updateShift()
        }
    }

    fun stopRepeat() {
        repeat?.let(handler::removeCallbacks)
        repeat = null
    }

    override fun onDetachedFromWindow() {
        stopRepeat()
        super.onDetachedFromWindow()
    }

    private fun build() {
        stopRepeat()
        removeAllViews()
        letterKeys.clear()
        shiftKey = null
        if (symbols) buildSymbols() else buildLetters()
        addView(bottomRow())
        updateShift()
    }

    private fun buildLetters() {
        addView(row(*"qwertyuiop".map(::letterKey).toTypedArray()))
        addView(row(spacer(0.5f), *"asdfghjkl".map(::letterKey).toTypedArray(), spacer(0.5f)))
        val shiftView = iconKey(R.drawable.ic_kb_shift, "Shift", special = true) { onShiftTap() }
        shiftKey = shiftView
        addView(row(shiftView.weighted(1.5f), *"zxcvbnm".map(::letterKey).toTypedArray(), backspaceKey().weighted(1.5f)))
    }

    private fun buildSymbols() {
        addView(row(*"1234567890".map { textKey(it.toString()) }.toTypedArray()))
        addView(row(*listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/").map { textKey(it) }.toTypedArray()))
        addView(row(*listOf("*", "\"", "'", ":", ";", "!", "?", "_").map { textKey(it) }.toTypedArray(), backspaceKey().weighted(2f)))
    }

    private fun bottomRow(): LinearLayout {
        val modeKey = labelKey(if (symbols) "ABC" else "?123", special = true, description = if (symbols) "Letters" else "Numbers and symbols") {
            symbols = !symbols
            if (!symbols) shift = Shift.OFF
            build()
        }
        val globe = iconKey(R.drawable.ic_kb_globe, "Switch keyboard", special = true) { actions.onSwitchKeyboard() }
        val lambda = labelKey("λ", description = "Translate to English with Liyora", textColor = android.graphics.Color.WHITE,
            normal = style.red, pressed = style.redPressed, bold = true, sizeSp = 22f) { actions.onTranslate() }
        val comma = textKey(",", special = true)
        val space = labelKey("", description = "Space", normal = style.key, pressed = style.keyPressed) { actions.onText(" ") }
        val period = textKey(".", special = true)
        val label = enterLabel
        val enter = if (label == null) {
            iconKey(R.drawable.ic_kb_enter, "Enter", special = true) { actions.onEnter() }
        } else {
            labelKey(label, special = true, sizeSp = 15f) { actions.onEnter() }
        }
        return row(
            modeKey.weighted(1.5f), globe, lambda, comma, space.weighted(4f), period, enter.weighted(1.5f),
        )
    }

    // ---- Keys ----

    private fun letterKey(letter: Char): View {
        val view = baseText(letter.toString(), style.keyText, sizeSp = 22f, bold = false)
        view.background = style.pressable(style.key, style.keyPressed)
        letterKeys += view to letter
        onRelease(view) {
            val upper = shift != Shift.OFF
            if (shift == Shift.ON) {
                shift = Shift.OFF
                updateShift()
            }
            actions.onText(if (upper) letter.uppercaseChar().toString() else letter.toString())
        }
        return view
    }

    /** Types its own label. */
    private fun textKey(text: String, special: Boolean = false): View =
        labelKey(text, special = special, sizeSp = 20f) { actions.onText(text) }

    private fun labelKey(
        label: String,
        special: Boolean = false,
        description: String? = null,
        textColor: Int = style.keyText,
        normal: Int = if (special) style.specialKey else style.key,
        pressed: Int = if (special) style.specialKeyPressed else style.keyPressed,
        bold: Boolean = false,
        sizeSp: Float = 16f,
        action: () -> Unit,
    ): View {
        val view = baseText(label, textColor, sizeSp, bold)
        view.background = style.pressable(normal, pressed)
        if (description != null) view.contentDescription = description
        onRelease(view, action)
        return view
    }

    private fun iconKey(icon: Int, description: String, special: Boolean, action: () -> Unit): ImageView {
        val view = ImageView(context).apply {
            setImageResource(icon)
            imageTintList = ColorStateList.valueOf(style.keyText)
            scaleType = ImageView.ScaleType.CENTER
            contentDescription = description
            background = if (special) {
                style.pressable(style.specialKey, style.specialKeyPressed)
            } else {
                style.pressable(style.key, style.keyPressed)
            }
        }
        onRelease(view, action)
        return view
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun backspaceKey(): View {
        val view = iconKey(R.drawable.ic_kb_backspace, "Delete", special = true) { }
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    actions.onBackspace()
                    startRepeat()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeat()
                }
            }
            true
        }
        return view
    }

    private fun startRepeat() {
        stopRepeat()
        val runnable = object : Runnable {
            override fun run() {
                actions.onBackspace()
                handler.postDelayed(this, REPEAT_INTERVAL_MS)
            }
        }
        repeat = runnable
        handler.postDelayed(runnable, REPEAT_DELAY_MS)
    }

    private fun onShiftTap() {
        val now = System.currentTimeMillis()
        shift = when {
            shift == Shift.ON && now - lastShiftTap < DOUBLE_TAP_MS -> Shift.LOCKED
            shift == Shift.OFF -> Shift.ON
            else -> Shift.OFF
        }
        lastShiftTap = now
        updateShift()
    }

    private fun updateShift() {
        val upper = shift != Shift.OFF
        letterKeys.forEach { (view, letter) -> view.text = (if (upper) letter.uppercaseChar() else letter).toString() }
        shiftKey?.apply {
            setImageResource(
                when (shift) {
                    Shift.OFF -> R.drawable.ic_kb_shift
                    Shift.ON -> R.drawable.ic_kb_shift_on
                    Shift.LOCKED -> R.drawable.ic_kb_shift_lock
                },
            )
            contentDescription = when (shift) {
                Shift.OFF -> "Shift"
                Shift.ON -> "Shift on"
                Shift.LOCKED -> "Caps lock on"
            }
        }
    }

    // ---- Layout helpers ----

    @SuppressLint("ClickableViewAccessibility")
    private fun onRelease(view: View, action: () -> Unit) {
        view.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                MotionEvent.ACTION_UP -> {
                    v.isPressed = false
                    action()
                }
                MotionEvent.ACTION_CANCEL -> v.isPressed = false
            }
            true
        }
    }

    private fun baseText(label: String, color: Int, sizeSp: Float, bold: Boolean) = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        includeFontPadding = false
        isAllCaps = false
        maxLines = 1
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun spacer(weight: Float) = View(context).weighted(weight)

    private fun View.weighted(weight: Float): View = apply { setTag(R.id.key_weight, weight) }

    private fun row(vararg keys: View) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        keys.forEach { key ->
            val weight = key.getTag(R.id.key_weight) as? Float ?: 1f
            addView(
                key,
                LayoutParams(0, style.keyHeight, weight).apply {
                    val h = style.dp(3)
                    val v = style.dp(4)
                    setMargins(h, v, h, v)
                },
            )
        }
    }

    private companion object {
        const val REPEAT_DELAY_MS = 400L
        const val REPEAT_INTERVAL_MS = 50L
        const val DOUBLE_TAP_MS = 350L
    }
}
