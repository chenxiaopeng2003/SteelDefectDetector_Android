package com.example.steeldefectdetector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlueLight,
    secondary = PrimaryBlue,
    tertiary = PrimaryBlueDark,
    background = SurfaceDark,
    surface = SurfaceMedium,
    onPrimary = SurfaceWhite,
    onSecondary = SurfaceWhite,
    onTertiary = SurfaceWhite,
    onBackground = OnSurface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineVariant,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = PrimaryBlueDark,
    tertiary = PrimaryBlueLight,
    background = BackgroundLight,
    surface = SurfaceWhite,
    onPrimary = SurfaceWhite,
    onSecondary = SurfaceWhite,
    onTertiary = SurfaceWhite,
    onBackground = OnSurface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceLight,
    onSurfaceVariant = OnSurfaceVariant,
    outline = OutlineVariant,
    error = ErrorRed
)

@Composable
fun SteelDefectDetectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}