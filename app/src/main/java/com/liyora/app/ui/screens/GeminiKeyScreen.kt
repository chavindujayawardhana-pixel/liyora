package com.liyora.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.liyora.app.data.GeminiClient
import com.liyora.app.data.GeminiResult
import com.liyora.app.data.KeyStore
import com.liyora.app.ui.theme.SuccessGreen
import kotlinx.coroutines.launch

private const val AI_STUDIO_URL = "https://aistudio.google.com/apikey"

private data class KeyStatus(val text: String, val ok: Boolean)

@Composable
fun GeminiKeyScreen(
    keyStore: KeyStore,
    gemini: GeminiClient,
    onKeyChanged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(keyStore.hasKey()) }
    var status by remember { mutableStateOf<KeyStatus?>(null) }

    fun runTest(key: String) {
        busy = true
        status = null
        scope.launch {
            status = when (val result = gemini.testKey(key)) {
                is GeminiResult.Success -> KeyStatus("Connected. The key works.", ok = true)
                is GeminiResult.Failure -> KeyStatus(result.error.message, ok = false)
            }
            busy = false
        }
    }

    SubScreen(title = "Gemini key", onBack = onBack) {
        Text("How to get your free key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NumberedStep(1, "Tap \"Get my key\". Google AI Studio opens.")
        NumberedStep(2, "Sign in, create a key, and copy it.")
        NumberedStep(3, "Come back here, tap \"Paste\", then \"Save key\".")
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_URL)))
                } catch (e: ActivityNotFoundException) {
                    status = KeyStatus("No web browser found. Open $AI_STUDIO_URL manually.", ok = false)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Get my key") }

        Spacer(Modifier.height(24.dp))
        if (saved) {
            Text(
                "A key is saved on this phone. Paste a new one below to replace it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.trim(); status = null },
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
            TextButton(onClick = {
                val pasted = readClipboardText(context)?.trim()
                if (pasted.isNullOrEmpty()) {
                    status = KeyStatus("The clipboard is empty. Copy your key first.", ok = false)
                } else {
                    input = pasted
                    status = null
                }
            }) { Text("Paste") }
            TextButton(
                enabled = !busy && (input.isNotBlank() || saved),
                onClick = { runTest(input.ifBlank { keyStore.getKey().orEmpty() }) },
            ) { Text("Test key") }
        }

        Spacer(Modifier.height(8.dp))
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Checking the key…")
            }
        }
        status?.let {
            Text(
                it.text,
                color = if (it.ok) SuccessGreen else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(20.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                "Note: Google may use text sent with free keys to improve its products. " +
                    "Avoid translating private or sensitive text.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Button(
            enabled = !busy && input.isNotBlank(),
            onClick = {
                keyStore.saveKey(input)
                saved = true
                onKeyChanged()
                val key = input
                input = ""
                visible = false
                runTest(key)
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save key") }
        if (saved) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    keyStore.removeKey()
                    saved = false
                    onKeyChanged()
                    status = KeyStatus("Key removed from this phone.", ok = true)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Remove key") }
        }
        Spacer(Modifier.height(24.dp))
    }
}

