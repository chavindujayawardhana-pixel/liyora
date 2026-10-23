package com.liyora.app.ui.screens

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
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
import com.liyora.app.data.AddKeyResult
import com.liyora.app.data.AppSettings
import com.liyora.app.data.GeminiClient
import com.liyora.app.data.GeminiError
import com.liyora.app.data.GeminiResult
import com.liyora.app.data.KeyStore
import com.liyora.app.data.SavedKey
import com.liyora.app.data.cleanKey
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
    var keys by remember { mutableStateOf(keyStore.keys()) }
    var status by remember {
        mutableStateOf(
            when {
                !keyStore.isAvailable -> KeyStatus(GeminiError.KEY_STORAGE.message, ok = false)
                AppSettings(context).takeKeysWereReset() -> KeyStatus(
                    "Your saved keys could not be read on this phone, so they were removed. Please add them again.",
                    ok = false,
                )
                else -> null
            },
        )
    }

    fun refresh() {
        keys = keyStore.keys()
        onKeyChanged()
    }

    /** Sends a tiny request with the key and shows exactly what Gemini said, in plain words. */
    fun runCheck(saved: SavedKey) {
        busy = true
        status = null
        scope.launch {
            val number = keys.indexOfFirst { it.id == saved.id } + 1
            val name = "Key $number (${saved.masked})"
            status = when (val result = gemini.checkKey(saved)) {
                is GeminiResult.Success -> {
                    keyStore.clearExhausted(saved.id)
                    KeyStatus("$name works. Model: ${result.model}.", ok = true)
                }
                is GeminiResult.Failure -> {
                    if (result.error == GeminiError.DAILY_LIMIT) keyStore.markExhausted(saved.id)
                    KeyStatus("$name: ${result.message}", ok = false)
                }
            }
            refresh()
            busy = false
        }
    }

    SubScreen(title = "Gemini keys", onBack = onBack) {
        Text("How to get your free key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        NumberedStep(1, "Tap \"Get my key\". Google AI Studio opens.")
        NumberedStep(2, "Sign in, create a key, and copy it.")
        NumberedStep(3, "Come back here, tap \"Paste\", then \"Add key\".")
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

        Spacer(Modifier.height(16.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Text(
                "You can add more than one key. When a key reaches its daily limit, Liyora uses the next one. " +
                    "Keys from the same Google project share one limit. Use keys from different Google " +
                    "accounts/projects.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
        Text("Saved keys", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (keys.isEmpty()) {
            Text(
                "No keys saved yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Card(modifier = Modifier.fillMaxWidth()) {
                keys.forEachIndexed { index, saved ->
                    if (index > 0) HorizontalDivider()
                    SavedKeyRow(
                        number = index + 1,
                        saved = saved,
                        resetText = if (saved.isExhausted()) {
                            "Limit reached. Usable again after " + DateUtils.formatDateTime(
                                context,
                                saved.exhaustedUntil,
                                DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_ABBREV_WEEKDAY,
                            )
                        } else {
                            null
                        },
                        enabled = !busy,
                        onCheck = { runCheck(saved) },
                        onRemove = {
                            keyStore.removeKey(saved.id)
                            status = KeyStatus("Key ${saved.masked} removed from this phone.", ok = true)
                            refresh()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Add a key", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = cleanKey(it); status = null },
            label = { Text("Gemini API key") },
            singleLine = true,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { visible = !visible }) { Text(if (visible) "Hide" else "Show") }
            TextButton(onClick = {
                val pasted = readClipboardText(context)?.let(::cleanKey)
                if (pasted.isNullOrEmpty()) {
                    status = KeyStatus("The clipboard is empty. Copy your key first.", ok = false)
                } else {
                    input = pasted
                    status = null
                }
            }) { Text("Paste") }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            enabled = !busy && input.isNotBlank(),
            onClick = {
                when (keyStore.addKey(input)) {
                    AddKeyResult.ADDED -> {
                        val added = keyStore.keys().last()
                        input = ""
                        visible = false
                        refresh()
                        runCheck(added)
                    }
                    AddKeyResult.DUPLICATE -> status = KeyStatus("This key is already saved.", ok = false)
                    AddKeyResult.BAD_FORMAT -> status = KeyStatus(GeminiError.BAD_FORMAT.message, ok = false)
                    AddKeyResult.STORAGE_ERROR -> status = KeyStatus(GeminiError.KEY_STORAGE.message, ok = false)
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Add key") }

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
    }
}

@Composable
private fun SavedKeyRow(
    number: Int,
    saved: SavedKey,
    resetText: String?,
    enabled: Boolean,
    onCheck: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
        Text("Key $number   ${saved.masked}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Text(
            resetText ?: "Ready",
            style = MaterialTheme.typography.bodySmall,
            color = if (resetText == null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row {
            TextButton(enabled = enabled, onClick = onCheck) { Text("Check key") }
            TextButton(enabled = enabled, onClick = onRemove) { Text("Remove") }
        }
    }
}
