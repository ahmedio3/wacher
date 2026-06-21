package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ComfortBeigeColorScheme = lightColorScheme(
    primary = PalettePrimary,
    onPrimary = PaletteOnPrimary,
    secondary = PaletteSecondary,
    onSecondary = PaletteOnPrimary,
    tertiary = PaletteTertiary,
    onTertiary = PaletteOnBackground,
    background = PaletteBackground,
    onBackground = PaletteOnBackground,
    surface = PaletteSurface,
    onSurface = PaletteOnSurface,
    surfaceVariant = PaletteSurfaceVariant,
    onSurfaceVariant = PaletteOnBackground,
    error = PaletteAppleRed,
    onError = PaletteOnPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ComfortBeigeColorScheme,
        typography = Typography,
        content = content
    )
}
