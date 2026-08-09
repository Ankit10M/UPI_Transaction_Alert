package com.upivoicealert.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = ShoutPayIndigo,
    onPrimary = OnIndigo,
    primaryContainer = IndigoContainer,
    onPrimaryContainer = OnIndigoContainer,
    secondary = AccentBlue,
    onSecondary = OnIndigo,
    secondaryContainer = AccentBlueContainer,
    onSecondaryContainer = OnAccentBlueContainer,
    tertiary = PeachAccent,
    onTertiary = OnIndigo,
    tertiaryContainer = PeachContainer,
    onTertiaryContainer = OnPeachContainer,
    background = AppBackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineStrongLight,
    outlineVariant = OutlineLight,
    error = ErrorRed,
    onError = OnIndigo,
    errorContainer = ErrorRedLight,
    onErrorContainer = OnErrorRedLight
)

private val DarkColors = darkColorScheme(
    primary = IndigoContainer,
    onPrimary = ShoutPayIndigoDark,
    primaryContainer = ShoutPayIndigo,
    onPrimaryContainer = OnIndigo,
    secondary = AccentBlueContainer,
    onSecondary = OnAccentBlueContainer,
    secondaryContainer = ShoutPayIndigo,
    onSecondaryContainer = OnIndigo,
    tertiary = PeachAccent,
    onTertiary = OnIndigo,
    tertiaryContainer = OnPeachContainer,
    onTertiaryContainer = PeachContainer,
    background = AppBackgroundDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineStrongDark,
    outlineVariant = OutlineDark,
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

private val ShoutPayShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun ShoutPayTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ShoutPayTypography,
        shapes = ShoutPayShapes,
        content = content
    )
}
