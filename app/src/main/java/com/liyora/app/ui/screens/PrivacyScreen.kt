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
    "Liyora translates Sinhala and Singlish into English. This page explains what happens to your text."

private val PRIVACY_SECTIONS = listOf(
    "What Liyora reads" to
        "Liyora reads the text in a text field only when you tap the Liyora bubble, and only in apps you " +
        "turned on in \"Works in\". It never reads password fields.",
    "Where your text goes" to
        "When you tap the bubble, the text is sent to Google's Gemini API, using your own API key, to get the " +
        "English translation. Liyora has no server of its own. Liyora does not store, log, or share your text.",
    "Free and paid Gemini keys" to
        "If you use a free Gemini key, Google may use the text you send to improve its products, so avoid " +
        "translating private or sensitive text. Paid keys follow Google's paid terms.",
    "Your API key" to
        "Your Gemini API key is stored encrypted on your phone only. It is excluded from backups.",
    "Accessibility permission" to
        "Liyora uses the Accessibility permission only to read the focused text field when you tap the bubble, " +
        "to show the bubble, and to replace the text when you tap Apply.",
    "No ads, analytics, or accounts" to
        "Liyora has no ads, no analytics, and no accounts. You can remove your key in the app or uninstall " +
        "Liyora at any time.",
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
