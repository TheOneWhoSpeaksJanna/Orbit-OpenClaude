package com.omniclaw.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class OmniClawThemeMode {
    SYSTEM, LIGHT, DARK
}

private val DarkColorScheme = darkColorScheme(
    primary = OmniClawAccent,
    onPrimary = Color(0xFF07090E),
    background = OmniClawBgDark,
    onBackground = OmniClawTextPrimary,
    surface = OmniClawSurfaceDark,
    onSurface = OmniClawTextPrimary,
    surfaceVariant = OmniClawSurfaceElevated,
    onSurfaceVariant = OmniClawTextSecondary,
    outline = OmniClawGlassBorder,
    error = OmniClawError
)

private val LightColorScheme = lightColorScheme(
    primary = OmniClawAccent,
    onPrimary = Color.White,
    background = OmniClawBgLight,
    onSurface = OmniClawTextPrimaryLight,
    surfaceVariant = OmniClawSurfaceLightElevated,
    onSurfaceVariant = OmniClawTextSecondaryLight,
    error = OmniClawError
)

@Composable
fun OmniClawTheme(
    themeMode: OmniClawThemeMode = OmniClawThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themeMode) {
        OmniClawThemeMode.SYSTEM -> isSystemInDarkTheme()
        OmniClawThemeMode.LIGHT -> false
        OmniClawThemeMode.DARK -> true
    }

    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content
    )
}
