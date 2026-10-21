package com.liyora.app.ui.screens

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.liyora.app.accessibility.AllowedAppsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LauncherApp(val packageName: String, val label: String, val icon: ImageBitmap?)

@Composable
fun AppsScreen(store: AllowedAppsStore, onBack: () -> Unit) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LauncherApp>?>(null) }
    var allowed by remember { mutableStateOf(store.allowed()) }
    var query by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val initiallyOn = store.allowed()
        // Apps that are on go first; the order is fixed at load so rows don't jump when toggled.
        apps = loadLauncherApps(context, store).sortedByDescending { it.packageName in initiallyOn }
    }

    SubScreen(title = "Works in", onBack = onBack, scrollable = false) {
        Text(
            "The Liyora bubble appears only in the apps you turn on here. Every other app is ignored.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search apps") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        val list = apps
        if (list == null) {
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Loading apps…")
            }
            return@SubScreen
        }

        val filtered = remember(list, query) {
            val q = query.trim()
            list.filter { q.isEmpty() || it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
        }
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(filtered, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = app.packageName in allowed,
                    onCheckedChange = { on ->
                        store.setAllowed(app.packageName, on)
                        allowed = store.allowed()
                    },
                )
            }
            if (filtered.isEmpty()) {
                item {
                    Text("No apps match \"${query.trim()}\".", modifier = Modifier.padding(vertical = 16.dp))
                }
            }
            item {
                Text(
                    "Banking apps and password managers are not shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun AppRow(app: LauncherApp, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (app.icon != null) {
            Image(app.icon, contentDescription = null, modifier = Modifier.size(40.dp))
        } else {
            Box(Modifier.size(40.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(app.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Installed apps with a launcher icon (visible through the manifest's launcher <queries>), minus blocked apps. */
private suspend fun loadLauncherApps(context: Context, store: AllowedAppsStore): List<LauncherApp> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val iconPx = (40 * context.resources.displayMetrics.density).toInt()
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
        }
        resolved
            .map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filterNot { store.isBlocked(it.packageName) }
            .map { info ->
                LauncherApp(
                    packageName = info.packageName,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info).toBitmap(iconPx, iconPx).asImageBitmap() }.getOrNull(),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

/** Names of the enabled apps that are still installed, for the Home chips. */
suspend fun enabledAppLabels(context: Context, store: AllowedAppsStore): List<String> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        store.allowed()
            .mapNotNull { pkg -> applicationInfo(pm, pkg)?.let { pm.getApplicationLabel(it).toString() } }
            .sortedBy { it.lowercase() }
    }

private fun applicationInfo(pm: PackageManager, pkg: String): ApplicationInfo? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getApplicationInfo(pkg, PackageManager.ApplicationInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getApplicationInfo(pkg, 0)
    }
} catch (e: PackageManager.NameNotFoundException) {
    null
}
