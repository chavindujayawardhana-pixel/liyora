package com.liyora.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.liyora.app.data.GeminiClient
import com.liyora.app.data.KeyStore
import com.liyora.app.keyboard.KeyboardSetup
import com.liyora.app.ui.screens.GeminiKeyScreen
import com.liyora.app.ui.screens.HomeScreen
import com.liyora.app.ui.screens.PrivacyScreen
import com.liyora.app.ui.screens.TranslateScreen
import com.liyora.app.ui.theme.LiyoraTheme

enum class Screen { Home, GeminiKey, Translate, Privacy }

class MainActivity : ComponentActivity() {

    private var keyboardEnabled by mutableStateOf(false)
    private var keyboardSelected by mutableStateOf(false)

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
                var screen by rememberSaveable { mutableStateOf(Screen.Home) }
                var keyCount by remember { mutableIntStateOf(keyStore.keys().size) }

                LifecycleResumeEffect(Unit) {
                    keyCount = keyStore.keys().size
                    onPauseOrDispose { }
                }

                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

                when (screen) {
                    Screen.Home -> HomeScreen(
                        keyCount = keyCount,
                        keyboardEnabled = keyboardEnabled,
                        keyboardSelected = keyboardSelected,
                        onOpenGeminiKey = { screen = Screen.GeminiKey },
                        onOpenPrivacy = { screen = Screen.Privacy },
                        onOpenTranslate = { screen = Screen.Translate },
                    )
                    Screen.GeminiKey -> GeminiKeyScreen(
                        keyStore = keyStore,
                        gemini = gemini,
                        onKeyChanged = { keyCount = keyStore.keys().size },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Translate -> TranslateScreen(
                        keyStore = keyStore,
                        gemini = gemini,
                        onOpenGeminiKey = { screen = Screen.GeminiKey },
                        onBack = { screen = Screen.Home },
                    )
                    Screen.Privacy -> PrivacyScreen(onBack = { screen = Screen.Home })
                }
            }
        }
    }

    // Steps 2 and 3 change outside the app: in Settings (the activity resumes) or in the
    // keyboard picker dialog (the activity only gets its window focus back).
    override fun onResume() {
        super.onResume()
        refreshKeyboardState()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) refreshKeyboardState()
    }

    private fun refreshKeyboardState() {
        keyboardEnabled = KeyboardSetup.isEnabled(this)
        keyboardSelected = KeyboardSetup.isSelected(this)
    }
}
