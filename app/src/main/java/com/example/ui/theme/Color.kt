package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// HFC Dark Glassmorphism Color Palette
val HfcDarkBackground = Color(0xFF100E10)
val HfcDarkSurface = Color(0xFF1A1618)
val HfcDarkSurfaceElevated = Color(0xFF241F22)

// Amber / Orange Primary Accents
val HfcOrangePrimary = Color(0xFFFF7A00)
val HfcOrangeLight = Color(0xFFFF9E3D)
val HfcOrangeDark = Color(0xFFE65100)
val HfcAmberGlow = Color(0xFFFFB300)

// Glassmorphism translucent layers
val HfcGlassBackground = Color(0x33282226)
val HfcGlassCardBackground = Color(0x28FFFFFF)
val HfcGlassBorder = Color(0x35FFFFFF)
val HfcGlassSubtle = Color(0x18FFFFFF)

// Accent badges
val HfcRedSpicy = Color(0xFFFF334B)
val HfcYellowRating = Color(0xFFFFC107)

// Text tokens
val HfcTextWhite = Color(0xFFFFFFFF)
val HfcTextMuted = Color(0xFFA09AA2)
val HfcTextDim = Color(0xFF6B656C)

// Gradient Brushes
val HfcOrangeGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFF8C00), Color(0xFFFF4E00))
)

val HfcDarkVignetteGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF1E1719),
        Color(0xFF120F11),
        Color(0xFF0C0A0B)
    )
)
