@file:Suppress("DEPRECATION")

package com.liyora.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.UserManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.GeneralSecurityException
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

    /** Never print the key, even by accident in a log or a crash report. */
    override fun toString(): String = "SavedKey(id=$id, key=$masked, exhaustedUntil=$exhaustedUntil)"
}

enum class AddKeyResult { ADDED, DUPLICATE, BAD_FORMAT, STORAGE_ERROR }

/** The next midnight in Pacific time after [now], when a used-up key can be tried again. */
fun nextQuotaReset(now: Long = System.currentTimeMillis()): Long =
    Instant.ofEpochMilli(now).atZone(QUOTA_ZONE).toLocalDate().plusDays(1)
        .atStartOfDay(QUOTA_ZONE).toInstant().toEpochMilli()

/**
 * Holds the user's Gemini API keys, encrypted at rest with a key from the Android Keystore.
 * Keys are never logged and never leave the device except in the request header to Gemini.
 *
 * EncryptedSharedPreferences is deprecated upstream but still works, and existing saved keys depend on it.
 * On some phones the Keystore entry behind it can be lost, and then every read fails. Instead of
 * failing (or crashing) every time, the unreadable keys are cleared once and the user is asked to
 * add them again. No method here throws.
 */
class KeyStore(context: Context) {

    private val appContext = context.applicationContext
    private val settings = AppSettings(appContext)

    @Volatile
    private var cached: SharedPreferences? = null

    /** False when the phone can't store keys at all right now (e.g. before the first unlock). */
    val isAvailable: Boolean get() = prefs() != null

    /** All saved keys, in the order they are tried. */
    fun keys(): List<SavedKey> = synchronized(LOCK) { read() }

    fun hasKey(): Boolean = keys().isNotEmpty()

    /** Saved keys whose limit has not been reached (or has reset since). */
    fun availableKeys(now: Long = System.currentTimeMillis()): List<SavedKey> = keys().filterNot { it.isExhausted(now) }

    /** Cleans [raw] (see [cleanKey]), checks its format and adds it at the end of the list. */
    fun addKey(raw: String): AddKeyResult = synchronized(LOCK) {
        val key = cleanKey(raw)
        if (!isPlausibleKey(key)) return AddKeyResult.BAD_FORMAT
        val list = read()
        if (list.any { it.key == key }) return AddKeyResult.DUPLICATE
        if (write(list + SavedKey(UUID.randomUUID().toString(), key, 0L))) AddKeyResult.ADDED else AddKeyResult.STORAGE_ERROR
    }

    fun removeKey(id: String) {
        synchronized(LOCK) { write(read().filterNot { it.id == id }) }
        settings.forgetKey(id)
    }

    /** Marks a key as used up until the next midnight Pacific time. */
    fun markExhausted(id: String, now: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        val until = nextQuotaReset(now)
        write(read().map { if (it.id == id) it.copy(exhaustedUntil = until) else it })
    }

    /** Makes a key usable again, e.g. after "Check key" shows it works. */
    fun clearExhausted(id: String) = synchronized(LOCK) {
        val list = read()
        if (list.any { it.id == id && it.exhaustedUntil != 0L }) {
            write(list.map { if (it.id == id) it.copy(exhaustedUntil = 0L) else it })
        }
    }

    private fun prefs(): SharedPreferences? {
        cached?.let { return it }
        synchronized(LOCK) {
            cached?.let { return it }
            // Encrypted storage can't be opened before the phone is unlocked once after a restart.
            val unlocked = appContext.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
            if (!unlocked) return null
            cached = try {
                open()
            } catch (e: Exception) {
                if (isDecryptionFailure(e)) resetAndOpen() else null
            }
            return cached
        }
    }

    private fun open(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    /** Deletes the unreadable file and its Keystore entry, then starts with an empty list. */
    private fun resetAndOpen(): SharedPreferences? = try {
        // clear() rather than deleting the file: Android keeps a copy of the file in memory.
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
        java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .deleteEntry(MasterKey.DEFAULT_MASTER_KEY_ALIAS)
        settings.setKeysWereReset()
        open()
    } catch (e: Exception) {
        null
    }

    private fun isDecryptionFailure(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.take(10).any { it is GeneralSecurityException }

    private fun read(): List<SavedKey> {
        val prefs = prefs() ?: return emptyList()
        val json = try {
            migrateSingleKey(prefs)
            prefs.getString(KEY_LIST, null)
        } catch (e: Exception) {
            if (isDecryptionFailure(e)) {
                cached = resetAndOpen()
            }
            null
        } ?: return emptyList()
        val array = try {
            JSONArray(json)
        } catch (e: Exception) {
            return emptyList()
        }
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            // Keys saved by 1.1.0 were only trimmed, so clean them again here.
            val key = cleanKey(o.optString("key")).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            SavedKey(o.optString("id").ifBlank { UUID.randomUUID().toString() }, key, o.optLong("exhaustedUntil", 0L))
        }
    }

    private fun write(list: List<SavedKey>): Boolean {
        val prefs = prefs() ?: return false
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("key", it.key).put("exhaustedUntil", it.exhaustedUntil)) }
        return try {
            prefs.edit().putString(KEY_LIST, array.toString()).apply()
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Version 1.1.0 and earlier saved one key on its own. Move it into the list once. */
    private fun migrateSingleKey(prefs: SharedPreferences) {
        val old = prefs.getString(KEY_GEMINI_SINGLE, null) ?: return
        val array = JSONArray()
        val key = cleanKey(old)
        if (key.isNotEmpty()) {
            array.put(JSONObject().put("id", UUID.randomUUID().toString()).put("key", key).put("exhaustedUntil", 0L))
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
