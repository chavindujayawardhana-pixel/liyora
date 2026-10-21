package com.liyora.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liyora.app.R
import com.liyora.app.ui.theme.SuccessGreen

@Composable
fun HomeScreen(
    keyCount: Int,
    keyboardEnabled: Boolean,
    keyboardSelected: Boolean,
    onOpenGeminiKey: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTranslate: () -> Unit,
) {
    val context = LocalContext.current
    val hasKey = keyCount > 0
    // The test text lives only in memory while this screen is open; it is never saved.
    var tryText by remember { mutableStateOf("") }

    fun openKeyboardSettings() {
        try {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.liyora_logo), contentDescription = null, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Liyora", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "To English",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SetupStep(
                    number = 1,
                    title = "Add your Gemini key",
                    detail = when (keyCount) {
                        0 -> "Liyora uses your own free key from Google."
                        1 -> "1 key saved"
                        else -> "$keyCount keys saved"
                    },
                    done = hasKey,
                ) {
                    OutlinedButton(onClick = onOpenGeminiKey) { Text(if (hasKey) "Manage keys" else "Add key") }
                }
                HorizontalDivider()
                SetupStep(
                    number = 2,
                    title = "Turn on the Liyora keyboard",
                    detail = if (keyboardEnabled) {
                        "On"
                    } else {
                        "In the list, turn on Liyora. Android shows a standard warning for every keyboard; tap OK."
                    },
                    done = keyboardEnabled,
                ) {
                    if (!keyboardEnabled) OutlinedButton(onClick = ::openKeyboardSettings) { Text("Open settings") }
                }
                HorizontalDivider()
                SetupStep(
                    number = 3,
                    title = "Choose Liyora as your keyboard",
                    detail = when {
                        keyboardSelected -> "Liyora is your keyboard"
                        keyboardEnabled -> "Pick Liyora from the list."
                        else -> "Do step 2 first."
                    },
                    done = keyboardSelected,
                ) {
                    if (!keyboardSelected) {
                        OutlinedButton(
                            enabled = keyboardEnabled,
                            onClick = { context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker() },
                        ) { Text("Choose keyboard") }
                    }
                }
                HorizontalDivider()
                SetupStep(
                    number = 4,
                    title = "Try typing",
                    detail = "Type something, tap the red λ key, then tap Apply.",
                    done = keyboardSelected && tryText.isNotBlank(),
                ) {
                    OutlinedTextField(
                        value = tryText,
                        onValueChange = { tryText = it },
                        placeholder = { Text("e.g. mama heta enawa") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("How to use", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            NumberedStep(1, "In any app, type your message with the Liyora keyboard.")
            NumberedStep(2, "Tap the red λ key. Liyora shows the English translation above the keys.")
            NumberedStep(3, "Tap Apply to replace your text, or Dismiss to keep it. Then send as usual.")
            NumberedStep(4, "Tap the globe key to go back to your other keyboard.")

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onOpenTranslate,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Translate text in the app")
            }

            Spacer(Modifier.height(20.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenPrivacy)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Privacy", style = MaterialTheme.typography.bodyLarge)
                    Text("›", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    detail: String,
    done: Boolean,
    action: @Composable () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        StepBadge(number, done)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = if (done) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            action()
        }
    }
}

/** A green circle with a tick when the step is done, otherwise the step number. */
@Composable
private fun StepBadge(number: Int, done: Boolean) {
    val modifier = Modifier.size(28.dp)
    if (done) {
        Box(modifier.background(SuccessGreen, CircleShape), contentAlignment = Alignment.Center) {
            Text("✓", color = Color.White, fontWeight = FontWeight.Bold)
        }
    } else {
        Box(
            modifier.border(1.5.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        }
    }
}
