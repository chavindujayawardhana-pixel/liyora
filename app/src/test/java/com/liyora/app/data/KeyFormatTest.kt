package com.liyora.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFormatTest {

    // A made-up key in the usual shape (39 characters), built in pieces so secret scanners don't flag it.
    private val key = "AIza" + "SyFAKE" + "0".repeat(28) + "x"

    @Test fun keepsACleanKey() = assertEquals(key, cleanKey(key))

    @Test fun removesSpacesAndLineBreaks() = assertEquals(key, cleanKey("  \n${key.take(20)}\r\n${key.drop(20)} \t"))

    @Test fun removesInvisibleCharacters() = assertEquals(key, cleanKey("\uFEFF$key\u200B\u00A0\u2060"))

    @Test fun removesQuotes() {
        assertEquals(key, cleanKey("\"$key\""))
        assertEquals(key, cleanKey("“$key”"))
        assertEquals(key, cleanKey("'$key',"))
    }

    @Test fun removesVariableName() {
        assertEquals(key, cleanKey("GEMINI_API_KEY=\"$key\""))
        assertEquals(key, cleanKey("GOOGLE_API_KEY=$key"))
    }

    @Test fun acceptsKeyShapes() {
        assertTrue(isPlausibleKey(key))
        assertTrue(isPlausibleKey("AQ.Ab8RN6FAKE_fake-0000000000000000000000000"))
    }

    @Test fun rejectsWrongInput() {
        assertFalse(isPlausibleKey(""))
        assertFalse(isPlausibleKey("hello"))
        assertFalse(isPlausibleKey("https://aistudio.google.com/apikey"))
        assertFalse(isPlausibleKey(key.dropLast(1) + "ශ"))
    }

    @Test fun savedKeyNeverPrintsTheKey() {
        val text = SavedKey("id", key, 0L).toString()
        assertFalse(text.contains(key))
    }
}
