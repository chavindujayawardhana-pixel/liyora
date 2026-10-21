package com.liyora.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.liyora.app.accessibility.AllowedAppsStore
import com.liyora.app.accessibility.isLiyoraAccessibilityEnabled
import com.liyora.app.data.GeminiClient
import com.liyora.app.data.KeyStore
import com.liyora.app.ui.screens.AppsScreen
import com.liyora.app.ui.screens.GeminiKeyScreen
import com.liyora.app.ui.screens.HomeScreen
import com.liyora.app.ui.screens.PermissionScreen
import com.liyora.app.ui.screens.PrivacyScreen
import com.liyora.app.ui.screens.TranslateScreen
import com.liyora.app.ui.theme.LiyoraTheme

enum class Screen { Home, GeminiKey, Translate, Permission, Apps, Privacy }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The UI is always light, so keep dark system bar icons even in system dark mode.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        setContent {
            LiyoraTheme {
                val context = LocalContext.current
                val keyStore = remember { KeyStore(context) }
                val gemini = remember { GeminiClient(context) }
                val appsStore = remember { AllowedAppsStore(context) }
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                var hasKey by remember { mutableStateOf(keyStore.hasKey()) }
                var accessibilityOn by remember { mutableStateOf(isLiyoraAccessibilityEnabled(context)) }

                // Re-check when coming back from Settings or from the panel's "Open Liyora" button.
                LifecycleResumeEffect(Unit) {
                    accessibilityOn = isLiyoraAccessibilityEnabled(context)
                    hasKey = keyStore.hasKey()
                    onPauseOrDispose { }
                }

                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                when (screen) {
                    Screen.Home -> HomeScreen(
                        hasKey = hasKey,
                        accessibilityOn = accessibilityOn,
                        appsStore = appsStore,
                        onOpenGeminiKey = { screen = Screen.GeminiKey },
                        onOpenPermission = { screen = Screen.Permission },
                        onOpenApps = { screen = Screen.Apps },
                        onOpenPrivacy = { screen = Screen.Privacy },
                        onOpenTranslate = { screen = Screen.Translate },
                    )
                    Screen.GeminiKey -> GeminiKeyScreen(
                        keyStore = keyStore,
                        gemini = gemini,
                        onKeyChanged = { hasKey = keyStore.hasKey() },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Translate -> TranslateScreen(
                        keyStore = keyStore,
                        gemini = gemini,
                        onOpenGeminiKey = { screen = Screen.GeminiKey },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Permission -> PermissionScreen(
                        accessibilityOn = accessibilityOn,
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Apps -> AppsScreen(store = appsStore, onBack = { screen = Screen.Home })
                    Screen.Privacy -> PrivacyScreen(onBack = { screen = Screen.Home })
                }
            }
        }
    }
}
