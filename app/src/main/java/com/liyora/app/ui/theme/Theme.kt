package com.liyora.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LiyoraRed = Color(0xFFE5233B)
val SuccessGreen = Color(0xFF1E8E3E)

private val LightColors = lightColorScheme(
    primary = LiyoraRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDADB),
    onPrimaryContainer = Color(0xFF410008),
    secondary = Color(0xFF775657),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF4DDDD),
)

@Composable
fun LiyoraTheme(content: @Composable () -> Unit) {
    // Always light, to match the Liyora design.
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
