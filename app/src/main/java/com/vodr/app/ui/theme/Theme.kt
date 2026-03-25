package com.vodr.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.vodr.ui.theme.ProvideVodrUiTheme
import com.vodr.ui.theme.VodrDarkColorScheme
import com.vodr.ui.theme.VodrLightColorScheme
import com.vodr.ui.theme.VodrShapes
import com.vodr.ui.theme.VodrTypography

@Composable
fun VodrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) VodrDarkColorScheme else VodrLightColorScheme
    MaterialTheme(
        colorScheme = colors,
        typography = VodrTypography,
        shapes = VodrShapes,
    ) {
        ProvideVodrUiTheme(content = content)
    }
}
