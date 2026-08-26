package com.aistudio.cinemios.fxtyr.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

private val ComfortDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnPrimary,
    tertiary = DarkTertiary,
    onTertiary = DarkOnBackground,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnBackground,
    error = PaletteAppleRed,
    onError = DarkOnPrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) ComfortDarkColorScheme else ComfortBeigeColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
