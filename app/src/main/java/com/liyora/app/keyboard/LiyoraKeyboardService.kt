package com.liyora.app.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.liyora.app.MainActivity
import com.liyora.app.data.GeminiClient
import com.liyora.app.data.GeminiError
import com.liyora.app.data.GeminiResult
import com.liyora.app.data.KeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Most characters read from a field before and after the cursor when the app gives no full text. */
private const val MAX_FIELD_CHARS = 10_000

/**
 * The Liyora keyboard. The field text is read only when the user taps λ, sent to Gemini with the
 * user's own key, and kept only in memory until the panel closes. Nothing is stored or logged.
 */
class LiyoraKeyboardService : InputMethodService(), KeyboardView.Actions {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var translateJob: Job? = null

    private lateinit var keyStore: KeyStore
    private lateinit var gemini: GeminiClient

    private var keyboard: KeyboardView? = null
    private var panel: TranslatePanel? = null

    override fun onCreate() {
        super.onCreate()
        keyStore = KeyStore(this)
        gemini = GeminiClient(this)
    }

    override fun onCreateInputView(): View {
        // Called again after rotation or a dark mode change, so the style is always current.
        val style = KeyboardStyle.from(this)
        val keys = KeyboardView(this, style, this)
        val translatePanel = TranslatePanel(
            context = this,
            style = style,
            onApply = ::applyTranslation,
            onDismiss = ::closePanel,
            onOpenApp = ::openApp,
        )
        keyboard = keys
        panel = translatePanel
        translateJob?.cancel()

        @Suppress("DEPRECATION")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) window.window?.navigationBarColor = style.background

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(style.background)
            addView(translatePanel.root, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(keys, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            // Keep the keys above the navigation bar when the window draws behind it.
            ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
                val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                view.setPadding(0, 0, 0, bottom)
                insets
            }
        }
    }

    /** Never take over the whole screen in landscape; the app stays visible above the keys. */
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!restarting) closePanel()
        keyboard?.configure(enterLabel(info), newField = !restarting)
        updateAutoShift()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        keyboard?.stopRepeat()
        super.onFinishInputView(finishingInput)
    }

    override fun onFinishInput() {
        // The field is gone, so a translation for it must not be applied anywhere else.
        closePanel()
        super.onFinishInput()
    }

    override fun onDestroy() {
        scope.cancel()
        keyboard = null
        panel = null
        super.onDestroy()
    }

    // ---- Keys ----

    override fun onText(text: String) {
        currentInputConnection?.commitText(text, 1)
        updateAutoShift()
    }

    override fun onBackspace() {
        // A key event (rather than deleteSurroundingText) also deletes a selection and works in every app.
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateAutoShift()
    }

    override fun onEnter() {
        val info = currentInputEditorInfo ?: return
        val action = editorAction(info)
        if (action != null) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendKeyChar('\n')
        }
        updateAutoShift()
    }

    override fun onSwitchKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        val switched = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            switchToPreviousInputMethod()
        } else {
            val token = window.window?.attributes?.token
            @Suppress("DEPRECATION")
            token != null && imm?.switchToLastInputMethod(token) == true
        }
        // No previous keyboard (e.g. Liyora is the only one used so far): let the user pick one.
        if (!switched) imm?.showInputMethodPicker()
    }

    override fun onTranslate() {
        val info = currentInputEditorInfo
        val ic = currentInputConnection
        if (info == null || ic == null) return
        if (isPasswordField(info)) {
            show(PanelState.Message("Liyora does not translate password fields."))
            return
        }
        val text = readFieldText(ic).trim()

        translateJob?.cancel()
        show(PanelState.Loading)
        translateJob = scope.launch {
            val hasKey = withContext(Dispatchers.IO) { runCatching { keyStore.hasKey() }.getOrDefault(false) }
            if (!hasKey) {
                show(PanelState.Message(GeminiError.NO_KEY.message, offerOpenApp = true))
                return@launch
            }
            if (text.isEmpty()) {
                show(PanelState.Message("The text box is empty. Type some Sinhala or Singlish first."))
                return@launch
            }
            // Picks a key that has not reached its limit, and moves to the next key on a quota error.
            val result = try {
                gemini.translate(keyStore, text)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                GeminiResult.Failure(GeminiError.UNKNOWN)
            }
            when (result) {
                is GeminiResult.Success -> show(PanelState.Result(result.text))
                is GeminiResult.Failure -> show(
                    PanelState.Message(
                        result.error.message,
                        offerOpenApp = result.error == GeminiError.INVALID_KEY || result.error == GeminiError.NO_KEY,
                    ),
                )
            }
        }
    }

    // ---- Panel ----

    private fun show(state: PanelState) {
        panel?.show(state)
    }

    private fun closePanel() {
        translateJob?.cancel()
        translateJob = null
        panel?.hide()
    }

    /** Replaces the whole field with the English text: select all, then type over it. */
    private fun applyTranslation(english: String) {
        val ic = currentInputConnection
        if (ic == null) {
            closePanel()
            return
        }
        ic.beginBatchEdit()
        if (!ic.performContextMenuAction(android.R.id.selectAll)) {
            ic.setSelection(0, readFieldText(ic).length)
        }
        ic.commitText(english, 1)
        ic.endBatchEdit()
        closePanel()
        updateAutoShift()
    }

    private fun openApp() {
        closePanel()
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---- Field helpers ----

    /** The whole text of the field, or as much as the app shares around the cursor. */
    private fun readFieldText(ic: InputConnection): String {
        val extracted = ic.getExtractedText(ExtractedTextRequest().apply { hintMaxChars = MAX_FIELD_CHARS }, 0)?.text
        if (!extracted.isNullOrEmpty()) return extracted.toString()
        val before = ic.getTextBeforeCursor(MAX_FIELD_CHARS, 0) ?: ""
        val selected = ic.getSelectedText(0) ?: ""
        val after = ic.getTextAfterCursor(MAX_FIELD_CHARS, 0) ?: ""
        return "$before$selected$after"
    }

    private fun updateAutoShift() {
        val info = currentInputEditorInfo ?: return
        val ic = currentInputConnection
        val caps = info.inputType != InputType.TYPE_NULL && ic != null && ic.getCursorCapsMode(info.inputType) != 0
        keyboard?.setAutoShift(caps)
    }

    private fun enterLabel(info: EditorInfo): String? = when (editorAction(info)) {
        EditorInfo.IME_ACTION_GO -> "Go"
        EditorInfo.IME_ACTION_SEARCH -> "Search"
        EditorInfo.IME_ACTION_SEND -> "Send"
        EditorInfo.IME_ACTION_NEXT -> "Next"
        EditorInfo.IME_ACTION_DONE -> "Done"
        EditorInfo.IME_ACTION_PREVIOUS -> "Back"
        else -> null
    }

    /** The field's own action for the Enter key, or null when Enter should type a new line. */
    private fun editorAction(info: EditorInfo): Int? {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        return when (val action = info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_NONE, EditorInfo.IME_ACTION_UNSPECIFIED -> null
            else -> action
        }
    }

    private fun isPasswordField(info: EditorInfo): Boolean {
        val type = info.inputType
        val variation = type and InputType.TYPE_MASK_VARIATION
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_TEXT -> variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }
}
