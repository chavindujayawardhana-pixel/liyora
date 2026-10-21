package com.liyora.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.liyora.app.MainActivity
import com.liyora.app.data.GeminiClient
import com.liyora.app.data.GeminiResult
import com.liyora.app.data.KeyStore
import com.liyora.app.ui.screens.copyToClipboard
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Shows the Liyora bubble above the keyboard in allow-listed apps and translates the focused
 * field when the bubble is tapped.
 *
 * Privacy: event contents are never read. Field text is read only inside [onBubbleTapped],
 * sent to Gemini, and never stored or logged.
 */
class LiyoraAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private val scope = MainScope()
    private val evaluateRunnable = Runnable { evaluate() }

    private var overlay: OverlayController? = null
    private lateinit var keyStore: KeyStore
    private lateinit var gemini: GeminiClient
    private lateinit var allowedApps: AllowedAppsStore
    private lateinit var positions: BubblePositionStore

    /** Re-check as soon as the user changes "Works in". Held strongly: prefs keep only weak refs. */
    private val allowListListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> scheduleEvaluate() }

    /** The app the bubble is currently shown for; drag positions are saved per app. */
    private var bubblePackage: String? = null

    /** App whose bubble the user dropped on the X; it stays hidden there for now. */
    private var closedForPackage: String? = null

    /** The field the open panel belongs to; Apply writes into it. */
    private var target: AccessibilityNodeInfo? = null
    private var translateJob: Job? = null

    override fun onServiceConnected() {
        keyStore = KeyStore(this)
        gemini = GeminiClient(this)
        allowedApps = AllowedAppsStore(this)
        positions = BubblePositionStore(this)
        allowedApps.prefs.registerOnSharedPreferenceChangeListener(allowListListener)
        overlay = OverlayController(
            context = this,
            onBubbleTap = ::onBubbleTapped,
            onBubbleMoved = { position -> bubblePackage?.let { positions.save(it, position) } },
            onBubbleClosed = {
                closedForPackage = bubblePackage
                this.overlay?.showClosedBar(keyboardTop() ?: 0)
            },
            onTurnOffForApp = { closedForPackage?.let { allowedApps.setAllowed(it, false) } },
            onClose = ::closePanel,
            onApply = ::applyTranslation,
            onOpenApp = ::openApp,
        )
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Events only trigger a re-check of keyboard and focus state; coalesce bursts.
        scheduleEvaluate()
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (::allowedApps.isInitialized) {
            allowedApps.prefs.unregisterOnSharedPreferenceChangeListener(allowListListener)
        }
        handler.removeCallbacks(evaluateRunnable)
        translateJob?.cancel()
        scope.cancel()
        overlay?.removeAll()
        overlay = null
        target = null
        super.onDestroy()
    }

    private fun evaluate() {
        val overlay = overlay ?: return
        val keyboardTop = keyboardTop()
        val focused = if (keyboardTop != null) focusedInput() else null

        // A bubble dropped on the X stays hidden until the keyboard closes or the user moves to
        // another app. Only the focused node's package name is used here, never its text.
        val focusedPackage = focused?.packageName?.toString()
        if (keyboardTop == null || (focusedPackage != null && focusedPackage != closedForPackage)) {
            closedForPackage = null
        }

        val field = focused?.takeIf(::isAllowedField)
        if (keyboardTop == null || field == null) {
            overlay.hideBubble()
            if (overlay.isPanelShown) closePanel()
            return
        }
        if (overlay.isPanelShown) {
            if (field == target) {
                overlay.movePanel(keyboardTop)
                return
            }
            closePanel()
        }
        val pkg = field.packageName.toString()
        if (pkg == closedForPackage) {
            overlay.hideBubble()
            return
        }
        bubblePackage = pkg
        overlay.showBubble(keyboardTop, bubbleAnchor(field, keyboardTop), positions.get(pkg))
    }

    private fun scheduleEvaluate() {
        handler.removeCallbacks(evaluateRunnable)
        handler.postDelayed(evaluateRunnable, 80)
    }

    private fun onBubbleTapped() {
        val overlay = overlay ?: return
        val keyboardTop = keyboardTop()
        val field = allowedFocusedField()
        overlay.hideBubble()
        if (keyboardTop == null || field == null) return

        target = field
        field.refresh()
        val text = if (field.isShowingHintText) "" else field.text?.toString().orEmpty()
        val key = keyStore.getKey()
        when {
            text.isBlank() -> overlay.showPanel(keyboardTop, PanelState.Message("Type something first."))
            key == null -> overlay.showPanel(keyboardTop, PanelState.NeedsKey)
            else -> {
                overlay.showPanel(keyboardTop, PanelState.Loading)
                translateJob?.cancel()
                translateJob = scope.launch {
                    val state = when (val result = gemini.translate(key, text.trim())) {
                        is GeminiResult.Success -> PanelState.Result(result.text)
                        is GeminiResult.Failure -> PanelState.Message(result.error.message)
                    }
                    this@LiyoraAccessibilityService.overlay?.updatePanel(state)
                }
            }
        }
    }

    private fun applyTranslation(english: String) {
        val field = target
        if (field == null || !field.refresh()) {
            copyToClipboard(this, english)
            overlay?.updatePanel(PanelState.Message("The text box is gone. The English text is copied, so you can paste it."))
            return
        }
        val setText = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, english)
        }
        if (field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, setText)) {
            val cursorAtEnd = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, english.length)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, english.length)
            }
            field.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, cursorAtEnd)
            closePanel()
        } else {
            copyToClipboard(this, english)
            overlay?.updatePanel(
                PanelState.Message("Couldn't replace the text in this app. The English text is copied, so you can paste it."),
            )
        }
    }

    private fun closePanel() {
        translateJob?.cancel()
        translateJob = null
        target = null
        overlay?.hidePanel()
        scheduleEvaluate()
    }

    private fun openApp() {
        closePanel()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Top edge of the soft keyboard in screen pixels, or null when no keyboard is showing. */
    private fun keyboardTop(): Int? {
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } ?: return null
        val bounds = Rect()
        ime.getBoundsInScreen(bounds)
        return if (bounds.height() > 0) bounds.top else null
    }

    private fun focusedInput(): AccessibilityNodeInfo? = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

    /** Editable, not a password, and in an app turned on in "Works in". */
    private fun isAllowedField(node: AccessibilityNodeInfo): Boolean =
        allowedApps.isAllowed(node.packageName?.toString()) && node.isEditable && !node.isPassword

    /** The focused editable, non-password field, only if it belongs to an allow-listed app. */
    private fun allowedFocusedField(): AccessibilityNodeInfo? = focusedInput()?.takeIf(::isAllowedField)

    /**
     * Where the bubble's bottom should sit: above a compact field (a chat box) so it never covers
     * the field or the Send button, otherwise just above the keyboard.
     */
    private fun bubbleAnchor(field: AccessibilityNodeInfo, keyboardTop: Int): Int {
        val bounds = Rect()
        field.getBoundsInScreen(bounds)
        val density = resources.displayMetrics.density
        val compact = bounds.height() < 160 * density && bounds.bottom >= keyboardTop - 120 * density
        return if (compact) bounds.top else keyboardTop
    }
}
