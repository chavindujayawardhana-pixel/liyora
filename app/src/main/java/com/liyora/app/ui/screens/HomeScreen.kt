package com.liyora.app.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.liyora.app.R
import com.liyora.app.accessibility.AllowedAppsStore
import com.liyora.app.ui.theme.SuccessGreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    hasKey: Boolean,
    accessibilityOn: Boolean,
    appsStore: AllowedAppsStore,
    onOpenGeminiKey: () -> Unit,
    onOpenPermission: () -> Unit,
    onOpenApps: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTranslate: () -> Unit,
) {
    val context = LocalContext.current
    var worksIn by remember { mutableStateOf<List<String>?>(null) }
    // Home re-enters composition when coming back from "Works in", so this refreshes the chips.
    LaunchedEffect(Unit) { worksIn = enabledAppLabels(context, appsStore) }

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
                HorizontalDivider()
                SetupRow(
                    title = "Accessibility permission",
                    status = if (accessibilityOn) "On" else "Off",
                    ok = accessibilityOn,
                    onClick = onOpenPermission,
                )
                HorizontalDivider()
                SetupRow(
                    title = "Works in",
                    status = worksIn?.let { if (it.size == 1) "1 app" else "${it.size} apps" }.orEmpty(),
                    ok = !worksIn.isNullOrEmpty(),
                    onClick = onOpenApps,
                )
            }

            Spacer(Modifier.height(20.dp))
            Text("Works in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                worksIn.orEmpty().forEach { name ->
                    SuggestionChip(onClick = onOpenApps, label = { Text(name) })
                }
                AssistChip(
                    onClick = onOpenApps,
                    label = { Text("+ Add app", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
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
