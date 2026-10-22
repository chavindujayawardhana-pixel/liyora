@file:Suppress("DEPRECATION")

package com.liyora.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/** Gemini free limits reset at midnight Pacific time. */
private val QUOTA_ZONE: ZoneId = ZoneId.of("America/Los_Angeles")

/** One saved Gemini key. [exhaustedUntil] is 0, or the time (epoch ms) its limit resets. */
data class SavedKey(val id: String, val key: String, val exhaustedUntil: Long) {

    /** Shows only the last 4 characters, e.g. "••••Ab3x". */
    val masked: String get() = "••••" + key.takeLast(4)

    fun isExhausted(now: Long = System.currentTimeMillis()): Boolean = exhaustedUntil > now
}

/** The next midnight in Pacific time after [now], when a used-up key can be tried again. */
fun nextQuotaReset(now: Long = System.currentTimeMillis()): Long =
    Instant.ofEpochMilli(now).atZone(QUOTA_ZONE).toLocalDate().plusDays(1)
        .atStartOfDay(QUOTA_ZONE).toInstant().toEpochMilli()

/**
 * Holds the user's Gemini API keys, encrypted at rest with a key from the Android Keystore.
 * Keys are never logged and never leave the device except in the request header to Gemini.
 *
 * EncryptedSharedPreferences is deprecated upstream but still works, and existing saved keys depend on it.
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

    /** All saved keys, in the order they are tried. */
    fun keys(): List<SavedKey> = synchronized(LOCK) { read() }

    fun hasKey(): Boolean = keys().isNotEmpty()

    /** Saved keys whose limit has not been reached (or has reset since). */
    fun availableKeys(now: Long = System.currentTimeMillis()): List<SavedKey> = keys().filterNot { it.isExhausted(now) }

    /** Adds a key at the end of the list. Returns false if it is already saved. */
    fun addKey(key: String): Boolean = synchronized(LOCK) {
        val trimmed = key.trim()
        val list = read()
        if (trimmed.isEmpty() || list.any { it.key == trimmed }) return false
        write(list + SavedKey(UUID.randomUUID().toString(), trimmed, 0L))
        true
    }

    fun removeKey(id: String) = synchronized(LOCK) {
        write(read().filterNot { it.id == id })
    }

    /** Marks a key as used up until the next midnight Pacific time. */
    fun markExhausted(id: String, now: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        val until = nextQuotaReset(now)
        write(read().map { if (it.id == id) it.copy(exhaustedUntil = until) else it })
    }

    private fun read(): List<SavedKey> {
        migrateSingleKey()
        val json = prefs.getString(KEY_LIST, null) ?: return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val key = o.optString("key").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SavedKey(o.optString("id").ifBlank { UUID.randomUUID().toString() }, key, o.optLong("exhaustedUntil", 0L))
        }
    }

    private fun write(list: List<SavedKey>) {
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("key", it.key).put("exhaustedUntil", it.exhaustedUntil)) }
        prefs.edit().putString(KEY_LIST, array.toString()).apply()
    }

    /** Version 1.1.0 and earlier saved one key on its own. Move it into the list once. */
    private fun migrateSingleKey() {
        val old = prefs.getString(KEY_GEMINI_SINGLE, null) ?: return
        val array = JSONArray()
        if (old.isNotBlank()) {
            array.put(JSONObject().put("id", UUID.randomUUID().toString()).put("key", old.trim()).put("exhaustedUntil", 0L))
        }
        prefs.edit().putString(KEY_LIST, array.toString()).remove(KEY_GEMINI_SINGLE).apply()
    }

    private companion object {
        /** Shared by every KeyStore in the process (the app and the keyboard), so updates don't overwrite each other. */
        val LOCK = Any()
        const val PREFS_NAME = "liyora_secure_prefs"
        const val KEY_GEMINI_SINGLE = "gemini_api_key"
        const val KEY_LIST = "gemini_api_keys"
    }
}
