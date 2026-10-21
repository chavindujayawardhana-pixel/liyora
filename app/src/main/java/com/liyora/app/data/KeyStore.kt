@file:Suppress("DEPRECATION")

package com.liyora.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Holds the user's Gemini API key, encrypted at rest with a key from the Android Keystore.
 * The key is never logged and never leaves the device except in the request header to Gemini.
 *
 * EncryptedSharedPreferences is deprecated upstream but still works and is what SPEC.md requires.
 */
class KeyStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getKey(): String? = prefs.getString(KEY_GEMINI, null)?.takeIf { it.isNotBlank() }

    fun hasKey(): Boolean = getKey() != null

    fun saveKey(key: String) {
        prefs.edit().putString(KEY_GEMINI, key.trim()).apply()
    }

    fun removeKey() {
        prefs.edit().remove(KEY_GEMINI).apply()
    }

    private companion object {
        const val PREFS_NAME = "liyora_secure_prefs"
        const val KEY_GEMINI = "gemini_api_key"
    }
}
