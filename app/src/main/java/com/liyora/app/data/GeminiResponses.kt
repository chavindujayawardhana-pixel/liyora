package com.liyora.app.data

import org.json.JSONObject

/** One Gemini model Liyora can use, and the lowest thinking level it accepts (faster answers). */
data class GeminiModel(val id: String, val thinkingLevel: String)

/**
 * Tried in this order, except that the model that last worked for a key is tried first.
 * All three are stable Flash / Flash-Lite models that are "Free of charge" on the Free tier
 * (ai.google.dev/gemini-api/docs/pricing, October 2026). Gemini 2.5 models are no longer offered
 * to new projects, and the 2.0 models are shut down, so neither is used.
 */
val GEMINI_MODELS = listOf(
    GeminiModel("gemini-3.8-flash", thinkingLevel = "low"),
    GeminiModel("gemini-3.6-flash", thinkingLevel = "minimal"),
    GeminiModel("gemini-3.5-flash-lite", thinkingLevel = "minimal"),
)

/** [GEMINI_MODELS] with [lastWorking] moved to the front, if it is still in the list. */
fun modelOrder(lastWorking: String?): List<GeminiModel> {
    val first = GEMINI_MODELS.firstOrNull { it.id == lastWorking } ?: return GEMINI_MODELS
    return listOf(first) + (GEMINI_MODELS - first)
}

sealed interface GeminiResult {
    data class Success(val text: String, val model: String) : GeminiResult

    /** [keyNumber] (1-based) names the key the error is about when more than one key is saved. */
    data class Failure(val error: GeminiError, val httpCode: Int? = null, val keyNumber: Int? = null) : GeminiResult {
        /** Plain words for the user. Never contains the key. */
        val message: String
            get() {
                val text = if (error == GeminiError.UNKNOWN && httpCode != null) "${error.message} (error $httpCode)" else error.message
                return if (keyNumber != null) "Key $keyNumber: $text" else text
            }
    }
}

/** [isNotice] errors are shown as a calm note rather than a red error. */
enum class GeminiError(val message: String, val isNotice: Boolean = false) {
    NO_INTERNET("No internet connection. Check your Wi-Fi or mobile data and try again."),
    NETWORK(
        "Liyora could not reach Gemini. Check your connection, and check that Liyora is allowed to use " +
            "Wi-Fi and mobile data in your phone's app settings.",
    ),
    SECURE_CONNECTION(
        "A secure connection to Gemini failed. Check that your phone's date and time are correct, " +
            "or try another network.",
    ),
    TIMEOUT("Gemini took too long to answer. Please try again."),
    SERVER_BUSY("Gemini is busy right now. Please try again in a moment."),
    BAD_FORMAT("This doesn't look like a Gemini API key. Copy the key from Google AI Studio and paste it again."),
    INVALID_KEY("This key is not valid. Copy the whole key again from Google AI Studio, or create a new one."),
    KEY_REFUSED(
        "Google refused this key. It may be restricted, turned off, or reported as leaked. " +
            "Create a new key in Google AI Studio.",
    ),
    ACCOUNT(
        "Google refused this key's project (account or billing setup). Check the project in " +
            "Google AI Studio, or use a key from another Google account.",
    ),
    REGION("The Gemini API is not available in your country or region. If you use a VPN, turn it off and try again."),
    MODEL_UNAVAILABLE(
        "The Gemini models Liyora uses are not available for this key right now. " +
            "Try again later, or check for a Liyora update.",
    ),
    DAILY_LIMIT("This key has reached today's free limit. It resets at midnight Pacific time."),
    RATE_LIMIT("Too many requests in a short time. Wait a minute and try again.", isNotice = true),
    ALL_KEYS_LIMIT("All your keys have reached today's free limit. They reset at midnight Pacific time."),
    NO_KEY("Add your Gemini key in the Liyora app first."),
    KEY_STORAGE("Liyora could not read your saved keys on this phone. Open Liyora and add your key again."),
    BLOCKED("Gemini could not translate this text. Try rewording it.", isNotice = true),
    UNKNOWN("Gemini gave an unexpected answer. Please try again."),
}

/** What one Gemini response means for the next step. */
internal sealed interface Attempt {
    data class Ok(val text: String) : Attempt

    /** This model can't be used with this key (not found, shut down, not on the free tier). Try the next model. */
    data object ModelUnavailable : Attempt

    /** A 403 without a clear reason. Try the next model; if every model says this, the key is refused. */
    data object Forbidden : Attempt

    /** A quota error. [daily] is false for per-minute limits, which clear by themselves. */
    data class Quota(val daily: Boolean) : Attempt

    /** Retry once after a short wait. If it fails again, try the next model only when [nextModelHelps]. */
    data class Temporary(val error: GeminiError, val nextModelHelps: Boolean) : Attempt

    /** The model rejected the thinking setting. Retry without it. */
    data object ThinkingRejected : Attempt

    /** A problem with the key itself. Other models won't help, another key might. */
    data class KeyProblem(val error: GeminiError) : Attempt

    /** A problem no other model or key will fix, such as no internet or blocked text. */
    data class Stop(val error: GeminiError) : Attempt

    /** An error Liyora doesn't recognise. Try the next model, and show the code if none works. */
    data class Unexpected(val httpCode: Int) : Attempt
}

/** Finish reasons that mean Gemini refused the text rather than failing. */
private val BLOCKING_FINISH_REASONS = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY")

/** "limit: 0" in a quota error means the model is not on this key's tier at all. */
private val ZERO_LIMIT = Regex("limit:\\s*0\\b")

/** Reads a 200 response from generateContent. */
internal fun parseSuccess(response: String): Attempt {
    val json = try {
        JSONObject(response)
    } catch (e: Exception) {
        // Not JSON, e.g. a page from a Wi-Fi login portal or a proxy.
        return Attempt.Stop(GeminiError.NETWORK)
    }
    if (json.optJSONObject("promptFeedback")?.has("blockReason") == true) return Attempt.Stop(GeminiError.BLOCKED)
    val candidate = json.optJSONArray("candidates")?.optJSONObject(0) ?: return Attempt.Stop(GeminiError.BLOCKED)
    if (candidate.optString("finishReason") in BLOCKING_FINISH_REASONS) return Attempt.Stop(GeminiError.BLOCKED)
    val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
    val text = buildString {
        if (parts != null) {
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                // Thought summaries are not part of the answer.
                if (!part.optBoolean("thought")) append(part.optString("text"))
            }
        }
    }.trim()
    return if (text.isEmpty()) Attempt.Stop(GeminiError.BLOCKED) else Attempt.Ok(text)
}

/**
 * Reads an error response. Gemini has used two body formats: {"error": {"code": 404, "status":
 * "NOT_FOUND", ...}} and the newer {"error": {"code": "model_not_found", ...}}. Both are handled,
 * and the HTTP status code decides when the body says nothing clear.
 */
internal fun classifyHttpError(httpCode: Int, response: String): Attempt {
    val error = try {
        JSONObject(response).optJSONObject("error")
    } catch (e: Exception) {
        null
    }
    val status = error?.optString("status").orEmpty().uppercase()
    val codeName = (error?.opt("code") as? String).orEmpty().lowercase()
    // The whole body, so reasons and quota ids inside "details" count too.
    val text = response.lowercase()

    return when {
        "location is not supported" in text || "region is not supported" in text -> Attempt.Stop(GeminiError.REGION)

        httpCode == 401 || codeName == "authentication" || "api_key_invalid" in text ||
            "api key not valid" in text || "api key expired" in text || "api key not found" in text ||
            "invalid api key" in text -> Attempt.KeyProblem(GeminiError.INVALID_KEY)

        httpCode == 404 || codeName == "model_not_found" || codeName == "not_found" ||
            "is not found for api version" in text || "not supported for generatecontent" in text ||
            "no longer available" in text || "has been deprecated" in text || "has been shut down" in text ->
            Attempt.ModelUnavailable

        httpCode == 429 || status == "RESOURCE_EXHAUSTED" || codeName == "quota_exceeded" ||
            codeName == "rate_limit_exceeded" || codeName == "too_many_requests" -> when {
            ZERO_LIMIT.containsMatchIn(text) -> Attempt.ModelUnavailable
            "perday" in text || "per day" in text || codeName == "quota_exceeded" -> Attempt.Quota(daily = true)
            "perminute" in text || "per minute" in text || "persecond" in text ||
                codeName == "rate_limit_exceeded" || codeName == "too_many_requests" -> Attempt.Quota(daily = false)
            // No detail about which limit: count it as the daily one, as Liyora always has.
            else -> Attempt.Quota(daily = true)
        }

        httpCode == 400 && "thinking" in text -> Attempt.ThinkingRejected

        httpCode == 402 || codeName == "payment_required" || "billing" in text || "prepay" in text ->
            Attempt.KeyProblem(GeminiError.ACCOUNT)

        httpCode == 403 -> if (
            "api key" in text || "api_key" in text || "leaked" in text || "service_disabled" in text ||
            "has not been used in project" in text || "are blocked" in text || "unrestricted" in text
        ) {
            Attempt.KeyProblem(GeminiError.KEY_REFUSED)
        } else {
            Attempt.Forbidden
        }

        status == "FAILED_PRECONDITION" || codeName == "failed_precondition" -> Attempt.KeyProblem(GeminiError.ACCOUNT)

        httpCode == 408 || httpCode == 500 || httpCode == 502 || httpCode == 503 || httpCode == 504 ->
            Attempt.Temporary(GeminiError.SERVER_BUSY, nextModelHelps = true)

        else -> Attempt.Unexpected(httpCode)
    }
}
