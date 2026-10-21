package com.liyora.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liyora.app.ui.theme.SuccessGreen

@Composable
fun PermissionScreen(
    accessibilityOn: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    fun open(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    SubScreen(title = "Turn on Liyora", onBack = onBack) {
        Text(
            "Accessibility permission: ${if (accessibilityOn) "On" else "Off"}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (accessibilityOn) SuccessGreen else MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp)) {
                Text("What Liyora does with this permission", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Bullet("Shows a small Liyora bubble while you type, only in the apps you turn on in \"Works in\".")
                Bullet("Reads the text box only when you tap the bubble, and sends it to Gemini to translate.")
                Bullet("Every other app is ignored. Password fields are never read.")
                Bullet("Your text is never stored or saved.")
                Bullet("Nothing is sent automatically. You choose Apply, and you tap Send yourself.")
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NumberedStep(1, "Tap \"Open settings\".")
        NumberedStep(2, "Find Liyora (it may be under \"Downloaded apps\" or \"Installed apps\").")
        NumberedStep(3, "Turn Liyora on, then tap Allow.")
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { open(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Open settings") }

        Spacer(Modifier.height(28.dp))
        Text("Is the switch greyed out?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Android blocks this setting for apps installed from an APK file. To unblock it:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        NumberedStep(1, "Try to turn Liyora on once, so Android shows the \"Restricted setting\" message.")
        NumberedStep(2, "Open Settings > Apps > Liyora (or tap \"Open app info\" below).")
        NumberedStep(3, "Tap the three-dot menu (⋮) at the top right, then \"Allow restricted settings\".")
        NumberedStep(4, "Come back, tap \"Open settings\", turn Liyora on, and tap Allow.")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                open(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null)),
                )
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Open app info") }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun NumberedStep(number: Int, text: String) {
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("$number.", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
