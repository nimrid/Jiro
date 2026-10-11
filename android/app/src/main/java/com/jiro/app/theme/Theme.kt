package com.jiro.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JiroDarkColorScheme = darkColorScheme(
    primary = JiroColors.Amber,
    onPrimary = JiroColors.Ink,
    primaryContainer = JiroColors.SurfaceElevated,
    onPrimaryContainer = JiroColors.Text,
    secondary = JiroColors.Mint,
    onSecondary = JiroColors.Ink,
    background = JiroColors.Ink,
    onBackground = JiroColors.Text,
    surface = JiroColors.Surface,
    onSurface = JiroColors.Text,
    surfaceVariant = JiroColors.SurfaceElevated,
    onSurfaceVariant = JiroColors.Muted,
    outline = JiroColors.Line,
    error = JiroColors.Coral,
    onError = JiroColors.Text
)

@Composable
fun JiroTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JiroDarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
