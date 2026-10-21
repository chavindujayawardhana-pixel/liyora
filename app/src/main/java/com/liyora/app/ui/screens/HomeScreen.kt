package com.liyora.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liyora.app.R
import com.liyora.app.ui.theme.SuccessGreen

@Composable
fun HomeScreen(
    hasKey: Boolean,
    onOpenGeminiKey: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTranslate: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.liyora_logo), contentDescription = null, modifier = Modifier.size(56.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("Liyora", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Sinhala and Singlish to English",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            Text("Setup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SetupRow(
                    title = "Gemini key",
                    status = if (hasKey) "Connected" else "Not set",
                    ok = hasKey,
                    onClick = onOpenGeminiKey,
                )
            }

            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onOpenTranslate,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Translate text")
            }
            if (!hasKey) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Add your Gemini key first to start translating.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(28.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                SetupRow(title = "Privacy", status = "", ok = false, onClick = onOpenPrivacy)
            }
        }
    }
}

@Composable
private fun SetupRow(title: String, status: String, ok: Boolean, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = when {
                onClick == null -> status
                status.isEmpty() -> "›"
                else -> "$status  ›"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (ok) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (ok) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
