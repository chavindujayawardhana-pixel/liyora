package com.liyora.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import javax.net.ssl.SSLHandshakeException

private const val API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/"

private const val TRANSLATE_PROMPT =
    "The following text is Sinhala, either in Sinhala script or in Singlish (Sinhala written with English letters). " +
        "Translate it into natural, grammatically correct English. Keep the tone the same. " +
        "Reply with only the English translation.\n\nText: "

private const val CHECK_PROMPT = "Reply with the single word: OK"

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 30_000

/** Wait before retrying a busy server, a timeout or a dropped connection. */
private const val RETRY_DELAY_MS = 1_500L

/** No new request starts after this long, so a tap on λ never spins for minutes. */
private const val TOTAL_BUDGET_MS = 50_000L

/** Errors where another saved key may still work. */
private val TRY_NEXT_KEY = setOf(
    GeminiError.DAILY_LIMIT,
    GeminiError.RATE_LIMIT,
    GeminiError.INVALID_KEY,
    GeminiError.KEY_REFUSED,
    GeminiError.ACCOUNT,
    GeminiError.MODEL_UNAVAILABLE,
)

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

class GeminiClient(context: Context) {

    private val appContext = context.applicationContext
    private val settings = AppSettings(appContext)

    /**
     * Sends one tiny request with [saved], trying every model, and returns exactly what happened.
     * The caller decides what to do with the key (mark it used up, or usable again).
     */
    suspend fun checkKey(saved: SavedKey): GeminiResult = withContext(Dispatchers.IO) {
        runKey(saved, CHECK_PROMPT, SystemClock.elapsedRealtime() + TOTAL_BUDGET_MS)
    }

    /**
     * Translates with the saved keys in order, skipping keys that reached their limit. Each key
     * tries every model (see [GEMINI_MODELS]). A key whose daily limit is used up on every model is
     * marked until midnight Pacific time, and the next key is tried. Errors that another key can't
     * fix (no internet, blocked text, busy server) are returned at once.
     */
    suspend fun translate(keyStore: KeyStore, text: String): GeminiResult = withContext(Dispatchers.IO) {
        if (!keyStore.isAvailable) return@withContext GeminiResult.Failure(GeminiError.KEY_STORAGE)
        val saved = keyStore.keys()
        if (saved.isEmpty()) return@withContext GeminiResult.Failure(GeminiError.NO_KEY)
        val ready = saved.filterNot { it.isExhausted() }
        if (ready.isEmpty()) return@withContext GeminiResult.Failure(GeminiError.ALL_KEYS_LIMIT)

        val deadline = SystemClock.elapsedRealtime() + TOTAL_BUDGET_MS
        var reported: GeminiResult.Failure? = null
        for (key in ready) {
            if (SystemClock.elapsedRealtime() > deadline) break
            val result = runKey(key, TRANSLATE_PROMPT + text, deadline)
            if (result !is GeminiResult.Failure) return@withContext result
            val numbered = if (saved.size > 1) result.copy(keyNumber = saved.indexOf(key) + 1) else result
            if (result.error !in TRY_NEXT_KEY) return@withContext numbered
            if (result.error == GeminiError.DAILY_LIMIT) keyStore.markExhausted(key.id)
            // A broken key is worth telling the user about, even if another key only hit its limit.
            if (reported == null || (reported.error == GeminiError.DAILY_LIMIT && result.error != GeminiError.DAILY_LIMIT)) {
                reported = numbered
            }
        }
        when {
            keyStore.availableKeys().isEmpty() -> GeminiResult.Failure(GeminiError.ALL_KEYS_LIMIT)
            else -> reported ?: GeminiResult.Failure(GeminiError.TIMEOUT)
        }
    }

    /** Tries the models one by one with one key, starting with the model that last worked for it. */
    private suspend fun runKey(saved: SavedKey, prompt: String, deadline: Long): GeminiResult {
        val models = modelOrder(settings.lastModel(saved.id))
        var tried = 0
        var dailyQuota = 0
        var minuteQuota = 0
        var forbidden = 0
        var temporary: GeminiError? = null
        var unexpected: Int? = null

        for (model in models) {
            if (SystemClock.elapsedRealtime() > deadline) break
            tried++
            when (val attempt = tryModel(saved.key, model, prompt)) {
                is Attempt.Ok -> {
                    settings.setLastModel(saved.id, model.id)
                    return GeminiResult.Success(attempt.text, model.id)
                }
                is Attempt.KeyProblem -> return GeminiResult.Failure(attempt.error)
                is Attempt.Stop -> return GeminiResult.Failure(attempt.error)
                Attempt.ModelUnavailable -> Unit
                Attempt.Forbidden -> forbidden++
                is Attempt.Quota -> if (attempt.daily) dailyQuota++ else minuteQuota++
                is Attempt.Temporary -> temporary = attempt.error
                is Attempt.Unexpected -> unexpected = attempt.httpCode
                Attempt.ThinkingRejected -> unexpected = 400
            }
        }

        val error = when {
            // Only a daily quota error on every model this key can use means the key is used up.
            dailyQuota > 0 && tried == models.size && minuteQuota == 0 && temporary == null && unexpected == null ->
                GeminiError.DAILY_LIMIT
            minuteQuota > 0 || dailyQuota > 0 -> GeminiError.RATE_LIMIT
            temporary != null -> temporary
            unexpected != null -> return GeminiResult.Failure(GeminiError.UNKNOWN, httpCode = unexpected)
            forbidden > 0 && forbidden == tried -> GeminiError.KEY_REFUSED
            tried == 0 -> GeminiError.TIMEOUT
            else -> GeminiError.MODEL_UNAVAILABLE
        }
        return GeminiResult.Failure(error)
    }

    /** One model: retries once for temporary errors, and once without thinking if the model rejects it. */
    private suspend fun tryModel(apiKey: String, model: GeminiModel, prompt: String): Attempt {
        var useThinking = true
        var retried = false
        while (true) {
            val attempt = request(apiKey, model, prompt, useThinking)
            when {
                attempt is Attempt.ThinkingRejected && useThinking -> useThinking = false
                attempt is Attempt.Temporary && !retried -> {
                    retried = true
                    delay(RETRY_DELAY_MS)
                }
                attempt is Attempt.Temporary && !attempt.nextModelHelps -> return Attempt.Stop(attempt.error)
                else -> return attempt
            }
        }
    }

    private fun request(apiKey: String, model: GeminiModel, prompt: String, useThinking: Boolean): Attempt {
        val generationConfig = JSONObject()
        if (useThinking) generationConfig.put("thinkingConfig", JSONObject().put("thinkingLevel", model.thinkingLevel))
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt)))))
            .put("generationConfig", generationConfig)
            .put("safetySettings", SAFETY_SETTINGS)

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("$API_BASE${model.id}:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                // Cleaned again: a space, line break or invisible character here makes the request fail.
                setRequestProperty("x-goog-api-key", cleanKey(apiKey))
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code in 200..299) parseSuccess(response) else classifyHttpError(code, response)
        } catch (e: SocketTimeoutException) {
            Attempt.Temporary(GeminiError.TIMEOUT, nextModelHelps = true)
        } catch (e: SSLHandshakeException) {
            // Usually a wrong date and time on the phone, or a network that intercepts secure traffic.
            Attempt.Stop(if (isOnline()) GeminiError.SECURE_CONNECTION else GeminiError.NO_INTERNET)
        } catch (e: IOException) {
            if (isOnline()) Attempt.Temporary(GeminiError.NETWORK, nextModelHelps = false) else Attempt.Stop(GeminiError.NO_INTERNET)
        } catch (e: Exception) {
            // Never rethrow: some exception messages can contain request headers, and so the key.
            Attempt.Stop(GeminiError.UNKNOWN)
        } finally {
            connection?.disconnect()
        }
    }

    private fun isOnline(): Boolean = try {
        val cm = appContext.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        cm == null || caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) {
        true
    }
}
