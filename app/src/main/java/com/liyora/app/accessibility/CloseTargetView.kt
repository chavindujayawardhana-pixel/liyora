package com.liyora.app.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Round dark "X" drop target shown at the bottom while the bubble is dragged. Grows when active. */
@SuppressLint("ViewConstructor")
class CloseTargetView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    val radius = 28 * density
    /** Window size leaves room for the 1.2x grow and the shadow. */
    val sizePx = (84 * density).toInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xE63C4043.toInt()
        setShadowLayer(6 * density, 0f, 2 * density, 0x44000000)
    }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        strokeCap = Paint.Cap.ROUND
    }
    private var active = false

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setActive(value: Boolean) {
        if (value == active) return
        active = value
        val scale = if (value) 1.2f else 1f
        animate().scaleX(scale).scaleY(scale).setDuration(120).start()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDraw(canvas: Canvas) {
        val c = sizePx / 2f
        canvas.drawCircle(c, c, radius, fillPaint)
        val arm = 8 * density
        canvas.drawLine(c - arm, c - arm, c + arm, c + arm, xPaint)
        canvas.drawLine(c + arm, c - arm, c - arm, c + arm, xPaint)
    }
}
