package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HfcDarkColorScheme = darkColorScheme(
    primary = HfcOrangePrimary,
    onPrimary = Color.White,
    primaryContainer = HfcOrangeDark,
    onPrimaryContainer = Color.White,
    secondary = HfcAmberGlow,
    onSecondary = Color.Black,
    background = HfcDarkBackground,
    onBackground = HfcTextWhite,
    surface = HfcDarkSurface,
    onSurface = HfcTextWhite,
    surfaceVariant = HfcGlassCardBackground,
    onSurfaceVariant = HfcTextMuted,
    outline = HfcGlassBorder
)

@Composable
fun HfcTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HfcDarkColorScheme,
        typography = Typography,
        content = content
    )
}
