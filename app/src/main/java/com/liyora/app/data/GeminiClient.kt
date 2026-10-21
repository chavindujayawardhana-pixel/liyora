package com.liyora.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Change the model here; nothing else references the model name. */
const val GEMINI_MODEL = "gemini-2.5-flash"

private const val ENDPOINT =
    "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_MODEL:generateContent"

private const val TRANSLATE_PROMPT =
    "The following text is Sinhala, either in Sinhala script or in Singlish (Sinhala written with English letters). " +
        "Translate it into natural, grammatically correct English. Keep the tone the same. " +
        "Reply with only the English translation.\n\nText: "

/**
 * Casual chat often trips the default filters, so these three are set to the least strict level
 * the API allows ("OFF"). The model's own hard limits still apply.
 */
private val SAFETY_SETTINGS: JSONArray
    get() = JSONArray().apply {
        listOf("HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH", "HARM_CATEGORY_SEXUALLY_EXPLICIT").forEach {
            put(JSONObject().put("category", it).put("threshold", "OFF"))
        }
    }

/** Finish reasons that mean Gemini refused the text rather than failing. */
private val BLOCKING_FINISH_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII")

sealed interface GeminiResult {
    data class Success(val text: String) : GeminiResult
    data class Failure(val error: GeminiError) : GeminiResult
}

/** [isNotice] errors are shown as a calm note rather than a red error. */
enum class GeminiError(val message: String, val isNotice: Boolean = false) {
    NO_INTERNET("No internet connection. Check your Wi-Fi or mobile data and try again."),
    INVALID_KEY("This key doesn't work. Check that you copied the whole key from Google AI Studio."),
    DAILY_LIMIT("Today's free limit is used up. Please try again tomorrow."),
    ALL_KEYS_LIMIT("All keys have reached their limit. Try again later."),
    NEXT_KEY_LIMIT("This key has reached its limit too. Tap λ again to try your next key."),
    NO_KEY("Add your Gemini key in the Liyora app first."),
    BLOCKED("Gemini could not translate this text. Try rewording it.", isNotice = true),
    SERVER("Gemini is having trouble right now. Please try again in a moment."),
    UNKNOWN("Something went wrong. Please try again."),
}

class GeminiClient(private val context: Context) {

    /** Sends one tiny request to confirm the key is accepted. */
    suspend fun testKey(apiKey: String): GeminiResult = generate(apiKey, "Reply with the single word: OK")

    /**
     * Translates with the first saved key that has not reached its limit. If Gemini says that key's
     * quota is used up, the key is marked until midnight Pacific time and the request is retried
     * once with the next free key. Other errors are returned as they are, without trying another key.
     */
    suspend fun translate(keyStore: KeyStore, text: String): GeminiResult = withContext(Dispatchers.IO) {
        if (!keyStore.hasKey()) return@withContext GeminiResult.Failure(GeminiError.NO_KEY)
        val first = keyStore.availableKeys().firstOrNull()
            ?: return@withContext GeminiResult.Failure(GeminiError.ALL_KEYS_LIMIT)
        val result = generate(first.key, TRANSLATE_PROMPT + text)
        if (!result.isQuotaError()) return@withContext result

        keyStore.markExhausted(first.id)
        val next = keyStore.availableKeys().firstOrNull()
            ?: return@withContext GeminiResult.Failure(GeminiError.ALL_KEYS_LIMIT)
        val retry = generate(next.key, TRANSLATE_PROMPT + text)
        if (!retry.isQuotaError()) return@withContext retry

        keyStore.markExhausted(next.id)
        val error = if (keyStore.availableKeys().isEmpty()) GeminiError.ALL_KEYS_LIMIT else GeminiError.NEXT_KEY_LIMIT
        GeminiResult.Failure(error)
    }

    private fun GeminiResult.isQuotaError() = this is GeminiResult.Failure && error == GeminiError.DAILY_LIMIT

    private suspend fun generate(apiKey: String, prompt: String): GeminiResult = withContext(Dispatchers.IO) {
        if (!isOnline()) return@withContext GeminiResult.Failure(GeminiError.NO_INTERNET)

        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", JSONObject().put("thinkingConfig", JSONObject().put("thinkingBudget", 0)))
            .put("safetySettings", SAFETY_SETTINGS)

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) parseSuccess(response) else GeminiResult.Failure(mapError(code, response))
        } catch (e: IOException) {
            GeminiResult.Failure(if (isOnline()) GeminiError.SERVER else GeminiError.NO_INTERNET)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSuccess(response: String): GeminiResult {
        val json = try {
            JSONObject(response)
        } catch (e: Exception) {
            return GeminiResult.Failure(GeminiError.UNKNOWN)
        }
        if (json.optJSONObject("promptFeedback")?.has("blockReason") == true) {
            return GeminiResult.Failure(GeminiError.BLOCKED)
        }
        val candidate = json.optJSONArray("candidates")?.optJSONObject(0)
            ?: return GeminiResult.Failure(GeminiError.BLOCKED)
        if (candidate.optString("finishReason") in BLOCKING_FINISH_REASONS) {
            return GeminiResult.Failure(GeminiError.BLOCKED)
        }
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) for (i in 0 until parts.length()) append(parts.optJSONObject(i)?.optString("text").orEmpty())
        }.trim()
        return if (text.isEmpty()) GeminiResult.Failure(GeminiError.BLOCKED) else GeminiResult.Success(text)
    }

    private fun mapError(code: Int, response: String): GeminiError = when {
        // 429 is Gemini's quota / rate limit error (status RESOURCE_EXHAUSTED).
        code == 429 || response.contains("RESOURCE_EXHAUSTED") -> GeminiError.DAILY_LIMIT
        code == 401 || code == 403 -> GeminiError.INVALID_KEY
        code == 400 && (response.contains("API_KEY_INVALID") || response.contains("API key not valid")) ->
            GeminiError.INVALID_KEY
        code >= 500 -> GeminiError.SERVER
        else -> GeminiError.UNKNOWN
    }

    private fun isOnline(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
