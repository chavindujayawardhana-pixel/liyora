package com.liyora.app.accessibility

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.liyora.app.R

/** Short toast-style bar shown after the bubble is dropped on the X. */
class ClosedBar(context: Context, onTurnOff: () -> Unit) {

    private val density = context.resources.displayMetrics.density

    val root: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = GradientDrawable().apply {
            setColor(0xF2303134.toInt())
            cornerRadius = 24 * density
        }
        setPadding(dp(18), dp(6), dp(6), dp(6))

        addView(
            TextView(context).apply {
                setText(R.string.bubble_closed)
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            },
        )
        addView(
            TextView(context).apply {
                setText(R.string.turn_off_for_app)
                setTextColor(0xFFFF8A96.toInt())
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                typeface = Typeface.DEFAULT_BOLD
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, null)
                setOnClickListener { onTurnOff() }
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { marginStart = dp(8) },
        )
    }

    private fun dp(value: Int) = (value * density).toInt()
}
