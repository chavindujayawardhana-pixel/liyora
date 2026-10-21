package com.liyora.app.accessibility

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot

/**
 * Adds and positions the bubble, close target, panel and closed bar as TYPE_ACCESSIBILITY_OVERLAY
 * windows. None are focusable, so the keyboard and the app's text field keep focus.
 * Positions are in screen coordinates, matching AccessibilityWindowInfo.getBoundsInScreen.
 */
class OverlayController(
    private val context: Context,
    onBubbleTap: () -> Unit,
    private val onBubbleMoved: (BubblePosition) -> Unit,
    private val onBubbleClosed: () -> Unit,
    onTurnOffForApp: () -> Unit,
    onClose: () -> Unit,
    onApply: (String) -> Unit,
    onOpenApp: () -> Unit,
) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val density = context.resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())

    /**
     * Drop target for closing the bubble. Its window is added just before the bubble's so the
     * bubble stays on top of it; it is invisible and never touchable, and only shown while dragging.
     */
    private val closeTarget = CloseTargetView(context).apply { visibility = View.INVISIBLE }
    private val closeParams = overlayParams(closeTarget.sizePx, closeTarget.sizePx).apply {
        flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
    }
    private var capturedByClose = false

    private val closedBar = ClosedBar(context) {
        hideClosedBar()
        onTurnOffForApp()
    }
    private val barParams = overlayParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT)
        .apply { gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL }
    private var barShown = false
    private val hideBarRunnable = Runnable { hideClosedBar() }

    private val bubble = BubbleView(context).apply {
        setOnClickListener { onBubbleTap() }
        onDragStart = ::startDrag
        onDrag = ::dragBy
        onDragEnd = ::endDrag
    }
    private val bubbleParams = overlayParams(bubble.sizePx, bubble.sizePx)
    private var bubbleShown = false
    private var keyboardTop = 0

    /** While dragging or snapping, event-driven repositioning must not move the bubble. */
    private var dragging = false
    private var dragX = 0f
    private var dragY = 0f
    private var snapAnimator: ValueAnimator? = null

    private val panel = TranslationPanel(context, onClose, onApply, onOpenApp)
    private val panelParams = overlayParams(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
    private var panelAnchorTop = 0

    var isPanelShown = false
        private set

    /**
     * Shows the bubble at the user's [saved] position for this app, or by default at the right
     * edge with its circle ending just above [defaultAnchor]. Always kept on screen and above
     * the keyboard.
     */
    fun showBubble(keyboardTop: Int, defaultAnchor: Int, saved: BubblePosition?) {
        this.keyboardTop = keyboardTop
        if (dragging || snapAnimator?.isRunning == true) return
        val circleBottom = if (saved != null) {
            keyboardTop - (saved.aboveKeyboardDp * density).toInt()
        } else {
            defaultAnchor - dp(6)
        }
        val x = edgeX(saved?.side ?: Side.RIGHT)
        val y = clampY(circleBottom - circleBottomOffset())
        if (bubbleShown && bubbleParams.x == x && bubbleParams.y == y) return
        bubbleParams.x = x
        bubbleParams.y = y
        if (bubbleShown) {
            wm.updateViewLayout(bubble, bubbleParams)
        } else {
            wm.addView(closeTarget, closeParams)
            wm.addView(bubble, bubbleParams)
            bubbleShown = true
        }
    }

    fun hideBubble() {
        snapAnimator?.cancel()
        dragging = false
        capturedByClose = false
        if (!bubbleShown) return
        wm.removeViewImmediate(bubble)
        wm.removeViewImmediate(closeTarget)
        bubbleShown = false
    }

    private fun startDrag() {
        snapAnimator?.cancel()
        dragging = true
        capturedByClose = false
        dragX = bubbleParams.x.toFloat()
        dragY = bubbleParams.y.toFloat()
        if (bubbleShown) {
            // Bottom centre, just above the keyboard (or the navigation bar if no keyboard).
            closeParams.x = (displaySize().x - closeTarget.sizePx) / 2
            closeParams.y = (bottomAnchor() - dp(24) - closeTarget.radius - closeTarget.sizePx / 2f).toInt()
            wm.updateViewLayout(closeTarget, closeParams)
            closeTarget.setActive(false)
            closeTarget.visibility = View.VISIBLE
        }
    }

    /**
     * [dragX]/[dragY] follow the finger. Within 80dp of the X, the bubble snaps onto it instead,
     * with one haptic tick when it is captured.
     */
    private fun dragBy(dx: Float, dy: Float) {
        if (!bubbleShown) return
        val pad = bubble.shadowPad.toInt()
        dragX = (dragX + dx).coerceIn(-pad.toFloat(), (displaySize().x - bubble.sizePx + pad).toFloat())
        dragY = clampY((dragY + dy).toInt()).toFloat()

        val half = bubble.sizePx / 2f
        val targetX = closeParams.x + closeTarget.sizePx / 2f
        val targetY = closeParams.y + closeTarget.sizePx / 2f
        val near = hypot(dragX + half - targetX, dragY + half - targetY) <= dp(80)
        if (near != capturedByClose) {
            capturedByClose = near
            closeTarget.setActive(near)
            if (near) bubble.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if (near) {
            bubbleParams.x = (targetX - half).toInt()
            bubbleParams.y = (targetY - half).toInt()
        } else {
            bubbleParams.x = dragX.toInt()
            bubbleParams.y = dragY.toInt()
        }
        wm.updateViewLayout(bubble, bubbleParams)
    }

    /**
     * Dropped on the X: close the bubble without translating. Otherwise snap to the nearest
     * side edge, keep the height, and report the new position.
     */
    private fun endDrag() {
        dragging = false
        closeTarget.visibility = View.INVISIBLE
        closeTarget.setActive(false)
        if (!bubbleShown) return
        if (capturedByClose) {
            hideBubble()
            onBubbleClosed()
            return
        }
        val side = if (bubbleParams.x + bubble.sizePx / 2 < displaySize().x / 2) Side.LEFT else Side.RIGHT
        val circleBottom = bubbleParams.y + circleBottomOffset()
        onBubbleMoved(BubblePosition(side, (keyboardTop - circleBottom) / density))
        snapAnimator = ValueAnimator.ofInt(bubbleParams.x, edgeX(side)).apply {
            duration = 150
            addUpdateListener {
                if (bubbleShown) {
                    bubbleParams.x = it.animatedValue as Int
                    wm.updateViewLayout(bubble, bubbleParams)
                }
            }
            start()
        }
    }

    private fun edgeX(side: Side): Int = when (side) {
        Side.LEFT -> dp(12) - bubble.shadowPad.toInt()
        Side.RIGHT -> displaySize().x - bubble.sizePx - dp(12) + bubble.shadowPad.toInt()
    }

    /** Distance from the bubble window's top to the bottom of the visible circle. */
    private fun circleBottomOffset() = bubble.sizePx - bubble.shadowPad.toInt()

    /** Keeps the circle below the status bar area and fully above the keyboard. */
    private fun clampY(y: Int): Int {
        val minY = dp(24) - bubble.shadowPad.toInt()
        val maxY = keyboardTop - dp(6) - circleBottomOffset()
        return if (maxY < minY) minY else y.coerceIn(minY, maxY)
    }

    /** Shows the panel so that its bottom edge sits on the keyboard's top edge. */
    fun showPanel(keyboardTop: Int, state: PanelState) {
        panelAnchorTop = keyboardTop
        panel.render(state)
        if (!isPanelShown) {
            placePanel(add = true)
            isPanelShown = true
        } else {
            placePanel(add = false)
        }
    }

    fun updatePanel(state: PanelState) {
        if (!isPanelShown) return
        panel.render(state)
        placePanel(add = false)
    }

    fun movePanel(keyboardTop: Int) {
        if (!isPanelShown || keyboardTop == panelAnchorTop) return
        panelAnchorTop = keyboardTop
        placePanel(add = false)
    }

    fun hidePanel() {
        if (!isPanelShown) return
        wm.removeViewImmediate(panel.root)
        isPanelShown = false
    }

    /** Shows "Liyora bubble closed · Turn off for this app" above the keyboard for 4 seconds. */
    fun showClosedBar(keyboardTop: Int) {
        this.keyboardTop = keyboardTop
        closedBar.root.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        barParams.y = bottomAnchor() - dp(16) - closedBar.root.measuredHeight
        if (barShown) {
            wm.updateViewLayout(closedBar.root, barParams)
        } else {
            wm.addView(closedBar.root, barParams)
            barShown = true
        }
        handler.removeCallbacks(hideBarRunnable)
        handler.postDelayed(hideBarRunnable, 4_000)
    }

    private fun hideClosedBar() {
        handler.removeCallbacks(hideBarRunnable)
        if (!barShown) return
        wm.removeViewImmediate(closedBar.root)
        barShown = false
    }

    fun removeAll() {
        hideBubble()
        hidePanel()
        hideClosedBar()
    }

    /** Keyboard top, or roughly above the navigation bar when no keyboard is open. */
    private fun bottomAnchor(): Int = if (keyboardTop > 0) keyboardTop else displaySize().y - dp(72)

    private fun placePanel(add: Boolean) {
        val width = displaySize().x
        panel.root.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(panelAnchorTop.coerceAtLeast(0), View.MeasureSpec.AT_MOST),
        )
        panelParams.x = 0
        panelParams.y = (panelAnchorTop - panel.root.measuredHeight).coerceAtLeast(0)
        if (add) wm.addView(panel.root, panelParams) else wm.updateViewLayout(panel.root, panelParams)
    }

    private fun displaySize(): Point =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            Point(bounds.width(), bounds.height())
        } else {
            Point().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealSize(it)
            }
        }

    private fun overlayParams(width: Int, height: Int) = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    private fun dp(value: Int) = (value * density).toInt()
}
