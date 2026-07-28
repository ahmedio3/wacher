package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

// Creates a color scheme with a custom accent color
private fun lightColorSchemeWithAccent(accent: Color) = lightColorScheme(
    primary = accent,
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

private fun darkColorSchemeWithAccent(accent: Color) = darkColorScheme(
    primary = accent,
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
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = if (accentColor != null) {
        if (darkTheme) darkColorSchemeWithAccent(accentColor)
        else lightColorSchemeWithAccent(accentColor)
    } else {
        if (darkTheme) ComfortDarkColorScheme else ComfortBeigeColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
