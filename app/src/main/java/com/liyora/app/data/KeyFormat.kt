package com.liyora.app.data

/**
 * Characters that come along invisibly when a key is copied from a web page or a chat app:
 * soft hyphen, zero-width spaces and joiners, direction marks, word joiner and the BOM.
 * Sent in the request header, any of these makes the request fail before it reaches Gemini.
 */
private val INVISIBLE = Regex("[\\u00AD\\u180E\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u2064\\uFEFF]")

/** "GEMINI_API_KEY=..." pasted from a .env file or a code sample. */
private val NAME_PREFIX = Regex("^(GEMINI|GOOGLE)_API_KEY[=:]", RegexOption.IGNORE_CASE)

private const val WRAPPING = "\"'`‘’“”«»,;"

/** Letters, digits and the few symbols API keys use. Never spaces or anything outside ASCII. */
private val KEY_CHARS = Regex("[A-Za-z0-9._~+/=-]+")

/**
 * Cleans a pasted key: removes spaces and line breaks anywhere, invisible characters, a variable
 * name in front, and quotes or commas around it.
 */
fun cleanKey(raw: String): String =
    raw.replace(INVISIBLE, "")
        .filterNot { it.isWhitespace() || it.isISOControl() }
        .trim { it in WRAPPING }
        .replace(NAME_PREFIX, "")
        .trim { it in WRAPPING }

/**
 * True if [key] (already cleaned) can be a Gemini API key. Google has more than one key format,
 * so this checks only what every key has in common, and the real check is a request to Gemini.
 */
fun isPlausibleKey(key: String): Boolean = key.length in 30..512 && KEY_CHARS.matches(key)
