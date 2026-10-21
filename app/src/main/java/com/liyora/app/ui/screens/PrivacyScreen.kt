package com.liyora.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Keep this text identical to PRIVACY.md in the project root. */
private val PRIVACY_INTRO =
    "Liyora is a keyboard that translates your text to English. This page explains what " +
        "happens to your text."

private val PRIVACY_SECTIONS = listOf(
    "What Liyora reads" to
        "Liyora is a keyboard. Like any keyboard, the letters you tap go straight to the app you are typing in. " +
        "Liyora reads the whole text in the text box only when you tap the red λ key. It never reads or " +
        "translates password fields.",
    "Where your text goes" to
        "When you tap λ, the text in the text box is sent to Google Gemini, using one of your own Gemini API " +
        "keys, to get the English translation. The same happens when you tap Translate inside the Liyora " +
        "app. Nothing is sent at any other time. Liyora has no server of its own.",
    "What Liyora stores" to
        "Nothing you type is stored. Liyora does not save, log, or share your text or the translations. The " +
        "English text is kept in memory only until you tap Apply or Dismiss.",
    "Free and paid Gemini keys" to
        "If you use a free Gemini key, Google may use the text you send to improve its products, so avoid " +
        "translating private or sensitive text. Paid keys follow Google's paid terms.",
    "Your API keys" to
        "Your Gemini API keys are stored encrypted on your phone only, and are excluded from backups. A key is " +
        "sent only to Google Gemini, with a translation request. If a key reaches its daily limit, Liyora notes " +
        "that on your phone and uses your next saved key.",
    "No ads, analytics, or accounts" to
        "Liyora has no ads, no analytics, and no accounts. You can remove your key in the app, switch to " +
        "another keyboard, or uninstall Liyora at any time.",
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    SubScreen(title = "Privacy", onBack = onBack) {
        Text(PRIVACY_INTRO, style = MaterialTheme.typography.bodyLarge)
        PRIVACY_SECTIONS.forEach { (heading, body) ->
            Spacer(Modifier.height(20.dp))
            Text(heading, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}
