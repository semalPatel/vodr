package com.vodr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = AccentBlue,
    secondary = Slate700,
    tertiary = Slate500,
    surface = SurfaceLight,
)

private val DarkColors = darkColorScheme(
    primary = AccentBlue,
    secondary = Slate500,
    tertiary = Slate700,
    surface = SurfaceDark,
)

@Composable
fun VodrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = VodrTypography,
        content = content,
    )
}
