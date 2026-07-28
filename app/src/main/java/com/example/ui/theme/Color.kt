package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Light mode comfort beige palette
val PaletteBackground = Color(0xFFF9F5EE)   // Soft beige/alabaster background
val PaletteSurface = Color(0xFFEFECE4)      // Oatmeal surface card background
val PaletteSurfaceVariant = Color(0xFFE5E0D5) // Slightly darker borders/dividers
val PalettePrimary = Color(0xFF8C6D4F)      // Elegant warm caramel-brown primary
val PaletteSecondary = Color(0xFFAC8B6A)    // Softer auxiliary brown accent
val PaletteTertiary = Color(0xFFD6A45C)     // Warm gold rating indicator / accent
val PaletteOnBackground = Color(0xFF2C241E) // Premium dark espresso-charcoal text
val PaletteOnSurface = Color(0xFF352B24)    // Accent body/title text
val PaletteOnPrimary = Color(0xFFFDFBF7)    // Cozy white/cream text on buttons
val PaletteNeutralGray = Color(0xFFE5E5EA)  // Neutral system-gray (reference header circles/pills)

// Dark mode palette — GitHub-dark inspired
val DarkBackground = Color(0xFF0D1117)       // GitHub dark bg
val DarkSurface = Color(0xFF161B22)          // GitHub dark card/surface
val DarkSurfaceVariant = Color(0xFF21262D)   // Slightly lighter for borders/dividers
val DarkPrimary = Color(0xFF3B4A6B)           // Custom dark blue primary
val DarkSecondary = Color(0xFF8B7A6A)         // Muted warm taupe secondary
val DarkTertiary = Color(0xFFE3B86E)          // Warm gold accent remains
val DarkOnBackground = Color(0xFFE1D9D0)     // Off-white text on dark bg
val DarkOnSurface = Color(0xFFF0EAE2)         // Brighter text for titles
val DarkOnPrimary = Color(0xFFFDFBF7)         // Cream text on dark primary
val DarkNeutralGray = Color(0xFF404854)       // Dark mode neutral gray

// Accent colors
val PaletteAppleBlue = Color(0xFF007AFF)    // Classic iOS style blue highlight
val PaletteAppleRed = Color(0xFFFF3B30)     // Classic iOS error/delete red
val PaletteMutedRed = Color(0xFFB35447)      // Muted warm clay/brick red — less jarring delete action
val PaletteSuccess = Color(0xFF34C759)      // Classic iOS success green

// Dynamic accent color options for user customization
val AccentColors = mapOf(
    "ذهبي" to Color(0xFFD6A45C),
    "أزرق" to Color(0xFF007AFF),
    "أخضر" to Color(0xFF34C759),
    "بنفسجي" to Color(0xFF6C5CE7),
    "أحمر" to Color(0xFFFF3B30),
    "وردي" to Color(0xFFFF2D55),
    "برتقالي" to Color(0xFFFF9500),
    "تركواز" to Color(0xFF00C7BE)
)

// Secondary accent colors (muted versions)
val AccentColorsSecondary = mapOf(
    "ذهبي" to Color(0xFFE3B86E),
    "أزرق" to Color(0xFF5AC8FA),
    "أخضر" to Color(0xFF30D158),
    "بنفسجي" to Color(0xFF8B7CF6),
    "أحمر" to Color(0xFFFF6961),
    "وردي" to Color(0xFFFF6482),
    "برتقالي" to Color(0xFFFFB340),
    "تركواز" to Color(0xFF66D4D1)
)
