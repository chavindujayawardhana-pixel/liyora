package com.liyora.app.accessibility

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.hypot

/** Round red Liyora bubble: white lambda, white ring, soft shadow and a small dark "1" badge. */
@SuppressLint("ViewConstructor")
class BubbleView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val radius = 26 * density
    /** Room around the circle for the shadow and the badge. */
    val shadowPad = 8 * density
    val sizePx = ((radius + shadowPad) * 2).toInt()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = LIYORA_RED
        setShadowLayer(6 * density, 0f, 2 * density, 0x55000000)
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val lambdaScale = radius * 1.15f / 334f
    private val lambdaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 46 * lambdaScale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF202124.toInt() }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10f, resources.displayMetrics)
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val lambdaPath = Path()

    /** Drag callbacks; deltas are in screen pixels. A drag never counts as a tap. */
    var onDragStart: (() -> Unit)? = null
    var onDrag: ((dx: Float, dy: Float) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false

    init {
        // Software layer so the paint shadow is drawn.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        contentDescription = "Translate with Liyora"
        isClickable = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = downX
                lastY = downY
                dragging = false
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging && hypot(event.rawX - downX, event.rawY - downY) > touchSlop) {
                    dragging = true
                    onDragStart?.invoke()
                }
                if (dragging) {
                    onDrag?.invoke(event.rawX - lastX, event.rawY - lastY)
                    lastX = event.rawX
                    lastY = event.rawY
                }
            }
            MotionEvent.ACTION_UP -> {
                if (dragging) onDragEnd?.invoke() else performClick()
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd?.invoke()
                dragging = false
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDraw(canvas: Canvas) {
        val c = radius + shadowPad
        canvas.drawCircle(c, c, radius, fillPaint)
        canvas.drawCircle(c, c, radius - ringPaint.strokeWidth / 2, ringPaint)

        // Lambda from liyora_logo.svg, centred on (258, 256) in its 512 viewBox.
        fun x(v: Float) = c + (v - 258f) * lambdaScale
        fun y(v: Float) = c + (v - 256f) * lambdaScale
        lambdaPath.reset()
        lambdaPath.moveTo(x(176f), y(112f))
        lambdaPath.lineTo(x(348f), y(400f))
        lambdaPath.moveTo(x(262f), y(256f))
        lambdaPath.lineTo(x(168f), y(400f))
        canvas.drawPath(lambdaPath, lambdaPaint)

        val bx = c + radius * 0.72f
        val by = c - radius * 0.72f
        canvas.drawCircle(bx, by, 8 * density, badgePaint)
        val textY = by - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
        canvas.drawText("1", bx, textY, badgeTextPaint)
    }

    companion object {
        const val LIYORA_RED = 0xFFE5233B.toInt()
    }
}
