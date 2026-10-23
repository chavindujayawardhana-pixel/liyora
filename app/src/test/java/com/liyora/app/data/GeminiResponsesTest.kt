package com.liyora.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class GeminiResponsesTest {

    private fun error(code: Int, status: String, message: String, details: String = "") =
        """{"error":{"code":$code,"message":"$message","status":"$status"${if (details.isEmpty()) "" else ",\"details\":[$details]"}}}"""

    @Test fun invalidKey() {
        val body = error(
            400, "INVALID_ARGUMENT", "API key not valid. Please pass a valid API key.",
            """{"@type":"type.googleapis.com/google.rpc.ErrorInfo","reason":"API_KEY_INVALID"}""",
        )
        assertEquals(Attempt.KeyProblem(GeminiError.INVALID_KEY), classifyHttpError(400, body))
        assertEquals(Attempt.KeyProblem(GeminiError.INVALID_KEY), classifyHttpError(401, """{"error":{"code":"authentication","message":"x"}}"""))
    }

    @Test fun modelNotFound() {
        val body = error(404, "NOT_FOUND", "models/gemini-x is not found for API version v1beta, or is not supported for generateContent.")
        assertEquals(Attempt.ModelUnavailable, classifyHttpError(404, body))
        assertEquals(Attempt.ModelUnavailable, classifyHttpError(404, """{"error":{"code":"model_not_found","message":"x"}}"""))
        assertEquals(Attempt.ModelUnavailable, classifyHttpError(400, error(400, "INVALID_ARGUMENT", "This model is no longer available to new users.")))
    }

    @Test fun modelNotOnFreeTier() {
        val body = error(
            429, "RESOURCE_EXHAUSTED",
            "Quota exceeded for metric: generativelanguage.googleapis.com/generate_content_free_tier_requests, limit: 0, model: gemini-x",
        )
        assertEquals(Attempt.ModelUnavailable, classifyHttpError(429, body))
    }

    @Test fun dailyQuota() {
        val body = error(
            429, "RESOURCE_EXHAUSTED", "You exceeded your current quota, please check your plan and billing details.",
            """{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerDayPerProjectPerModel-FreeTier","quotaValue":"250"}]}""",
        )
        assertEquals(Attempt.Quota(daily = true), classifyHttpError(429, body))
        assertEquals(Attempt.Quota(daily = true), classifyHttpError(429, """{"error":{"code":"quota_exceeded","message":"x"}}"""))
    }

    @Test fun perMinuteQuotaIsNotDaily() {
        val body = error(
            429, "RESOURCE_EXHAUSTED", "You exceeded your current quota.",
            """{"@type":"type.googleapis.com/google.rpc.QuotaFailure","violations":[{"quotaId":"GenerateRequestsPerMinutePerProjectPerModel-FreeTier","quotaValue":"10"}]}""",
        )
        assertEquals(Attempt.Quota(daily = false), classifyHttpError(429, body))
        assertEquals(Attempt.Quota(daily = false), classifyHttpError(429, """{"error":{"code":"rate_limit_exceeded","message":"x"}}"""))
    }

    @Test fun serverBusy() {
        for (code in listOf(500, 503, 504)) {
            assertEquals(Attempt.Temporary(GeminiError.SERVER_BUSY, nextModelHelps = true), classifyHttpError(code, error(code, "UNAVAILABLE", "The model is overloaded.")))
        }
        assertEquals(Attempt.Temporary(GeminiError.SERVER_BUSY, nextModelHelps = true), classifyHttpError(503, "<html>Service Unavailable</html>"))
    }

    @Test fun regionNotSupported() {
        val body = error(400, "FAILED_PRECONDITION", "User location is not supported for the API use.")
        assertEquals(Attempt.Stop(GeminiError.REGION), classifyHttpError(400, body))
    }

    @Test fun refusedKey() {
        assertEquals(Attempt.KeyProblem(GeminiError.KEY_REFUSED), classifyHttpError(403, error(403, "PERMISSION_DENIED", "Your API key was reported as leaked.")))
        assertEquals(Attempt.KeyProblem(GeminiError.KEY_REFUSED), classifyHttpError(403, error(403, "PERMISSION_DENIED", "x", """{"reason":"SERVICE_DISABLED"}""")))
        assertEquals(Attempt.Forbidden, classifyHttpError(403, error(403, "PERMISSION_DENIED", "Permission denied on resource.")))
    }

    @Test fun thinkingRejected() {
        val body = error(400, "INVALID_ARGUMENT", "Invalid JSON payload received. Unknown name \\\"thinkingLevel\\\" at 'generation_config.thinking_config'")
        assertEquals(Attempt.ThinkingRejected, classifyHttpError(400, body))
    }

    @Test fun unknownError() = assertEquals(Attempt.Unexpected(418), classifyHttpError(418, "teapot"))

    @Test fun parsesAnswer() {
        val body = """{"candidates":[{"content":{"parts":[{"text":"thinking...","thought":true},{"text":" I will come tomorrow. "}]},"finishReason":"STOP"}]}"""
        assertEquals(Attempt.Ok("I will come tomorrow."), parseSuccess(body))
    }

    @Test fun blockedAnswer() {
        assertEquals(Attempt.Stop(GeminiError.BLOCKED), parseSuccess("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        assertEquals(Attempt.Stop(GeminiError.BLOCKED), parseSuccess("""{"candidates":[{"finishReason":"SAFETY"}]}"""))
    }

    @Test fun notJsonIsANetworkProblem() = assertEquals(Attempt.Stop(GeminiError.NETWORK), parseSuccess("<html>Sign in to Wi-Fi</html>"))

    @Test fun modelOrderStartsWithLastWorking() {
        assertEquals(GEMINI_MODELS, modelOrder(null))
        assertEquals(GEMINI_MODELS, modelOrder("gemini-2.5-flash"))
        val last = GEMINI_MODELS.last()
        val order = modelOrder(last.id)
        assertEquals(last, order.first())
        assertEquals(GEMINI_MODELS.toSet(), order.toSet())
        assertEquals(3, order.size)
    }

    @Test fun messagesNameTheKeyNumberOnly() {
        val failure = GeminiResult.Failure(GeminiError.INVALID_KEY, keyNumber = 2)
        assertEquals("Key 2: " + GeminiError.INVALID_KEY.message, failure.message)
        assertEquals(GeminiError.UNKNOWN.message + " (error 418)", GeminiResult.Failure(GeminiError.UNKNOWN, httpCode = 418).message)
        assertFalse(GeminiError.entries.any { it.message.contains("Something", ignoreCase = true) })
    }
}
