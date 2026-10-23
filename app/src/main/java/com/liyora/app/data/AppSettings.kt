package com.liyora.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Plain (not encrypted) app settings. Holds no keys: saved keys are referred to only by their
 * random id, so nothing here reveals a key.
 */
class AppSettings(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The Gemini model that last worked with the key [keyId], tried first next time. */
    fun lastModel(keyId: String): String? = prefs.getString(MODEL_PREFIX + keyId, null)

    fun setLastModel(keyId: String, model: String) {
        prefs.edit().putString(MODEL_PREFIX + keyId, model).apply()
    }

    fun forgetKey(keyId: String) {
        prefs.edit().remove(MODEL_PREFIX + keyId).apply()
    }

    /** Set when saved keys could not be decrypted and were cleared, so the keys screen can say why. */
    fun setKeysWereReset() {
        prefs.edit().putBoolean(KEYS_RESET, true).apply()
    }

    /** True once after the keys were cleared. */
    fun takeKeysWereReset(): Boolean {
        val reset = prefs.getBoolean(KEYS_RESET, false)
        if (reset) prefs.edit().remove(KEYS_RESET).apply()
        return reset
    }

    private companion object {
        const val PREFS_NAME = "liyora_settings"
        const val MODEL_PREFIX = "last_model_"
        const val KEYS_RESET = "keys_were_reset"
    }
}
