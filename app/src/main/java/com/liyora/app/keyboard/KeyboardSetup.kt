package com.liyora.app.keyboard

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.inputmethod.InputMethodManager

/** Reads whether the Liyora keyboard is turned on in Settings and whether it is the current keyboard. */
object KeyboardSetup {

    private fun liyoraId(context: Context): String? {
        val component = ComponentName(context, LiyoraKeyboardService::class.java)
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return null
        return imm.enabledInputMethodList.firstOrNull { it.component == component }?.id
    }

    fun isEnabled(context: Context): Boolean = liyoraId(context) != null

    fun isSelected(context: Context): Boolean {
        val id = liyoraId(context) ?: return false
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) == id
    }
}
