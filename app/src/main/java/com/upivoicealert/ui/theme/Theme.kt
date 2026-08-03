package com.upivoicealert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = TealPrimary,
    secondary = GoldAccent,
    background = SlateBackground,
    onBackground = OnSlateBackground
)

private val DarkColors = darkColorScheme(
    primary = TealContainer,
    secondary = GoldAccent,
    background = Color(0xFF121212),
    onBackground = Color(0xFFE6E1E5)
)

@Composable
fun UPIVoiceAlertTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}