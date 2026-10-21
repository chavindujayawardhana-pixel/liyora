package com.liyora.app.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liyora.app.data.GeminiClient
import com.liyora.app.data.GeminiResult
import com.liyora.app.data.KeyStore
import kotlinx.coroutines.launch

@Composable
fun TranslateScreen(
    keyStore: KeyStore,
    gemini: GeminiClient,
    onOpenGeminiKey: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var input by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf<String?>(null) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var errorIsNotice by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }
    val hasKey = remember { keyStore.hasKey() }

    SubScreen(title = "Translate", onBack = onBack) {
        if (!hasKey) {
            Text("You need a Gemini key before you can translate.", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenGeminiKey, modifier = Modifier.fillMaxWidth()) { Text("Add Gemini key") }
            return@SubScreen
        }

        OutlinedTextField(
            value = input,
            onValueChange = { input = it; error = null },
            label = { Text("Your text") },
            placeholder = { Text("e.g. mama heta enawa") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
        )
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = !loading && input.isNotBlank(),
            onClick = {
                focus.clearFocus()
                loading = true
                error = null
                result = null
                copied = false
                scope.launch {
                    when (val r = gemini.translate(keyStore, input.trim())) {
                        is GeminiResult.Success -> result = r.text
                        is GeminiResult.Failure -> {
                            error = r.error.message
                            errorIsNotice = r.error.isNotice
                        }
                    }
                    loading = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Translate") }

        Spacer(Modifier.height(16.dp))
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Translating…")
            }
        }
        error?.let {
            if (errorIsNotice) {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
            }
        }
        result?.let { english ->
            Text("English", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SelectionContainer {
                    Text(english, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(16.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedButton(onClick = {
                    copyToClipboard(context, english)
                    copied = true
                }) { Text(if (copied) "Copied" else "Copy") }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { input = ""; result = null; copied = false }) { Text("Clear") }
            }
        }
    }
}
