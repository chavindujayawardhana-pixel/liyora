package com.liyora.app.keyboard

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable

/** Colors for the keyboard and the translation panel, picked once per view from the system dark mode. */
class KeyboardStyle private constructor(val night: Boolean, val landscape: Boolean, val density: Float) {

    val background = if (night) 0xFF1B1B1D.toInt() else 0xFFE4E6EA.toInt()
    val key = if (night) 0xFF3A3A3E.toInt() else Color.WHITE
    val keyPressed = if (night) 0xFF56565C.toInt() else 0xFFC9CCD2.toInt()
    val specialKey = if (night) 0xFF2A2A2E.toInt() else 0xFFC4C8CF.toInt()
    val specialKeyPressed = if (night) 0xFF46464C.toInt() else 0xFFA9AEB6.toInt()
    val keyText = if (night) 0xFFEDEDF0.toInt() else 0xFF1F1F22.toInt()
    val panel = if (night) 0xFF2A2A2E.toInt() else Color.WHITE
    val panelText = keyText
    val muted = if (night) 0xFFB5B5BC.toInt() else 0xFF5F6368.toInt()
    val chip = if (night) 0xFF4A2A2E.toInt() else 0xFFFFE3E5.toInt()
    val red = LIYORA_RED
    val redPressed = 0xFFB81A2E.toInt()

    /** Height of one key row. Shorter in landscape so the app keeps some room. */
    val keyHeight = dp(if (landscape) 40 else 52)

    fun dp(value: Int): Int = (value * density + 0.5f).toInt()

    fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusDp * density
            if (strokeColor != null) setStroke(dp(1), strokeColor)
        }

    fun pressable(normal: Int, pressed: Int, radiusDp: Float = 7f): StateListDrawable =
        StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(pressed, radiusDp))
            addState(intArrayOf(), rounded(normal, radiusDp))
        }

    companion object {
        const val LIYORA_RED = 0xFFE5233B.toInt()

        fun from(context: Context): KeyboardStyle {
            val config = context.resources.configuration
            return KeyboardStyle(
                night = config.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES,
                landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE,
                density = context.resources.displayMetrics.density,
            )
        }
    }
}
