package com.vodr.ui.theme

import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val Slate900 = Color(0xFF111827)
private val Slate700 = Color(0xFF374151)
private val Slate500 = Color(0xFF6B7280)
private val SurfaceLight = Color(0xFFF8FAFC)
private val SurfaceDark = Color(0xFF0B1220)
private val AccentBlue = Color(0xFF2563EB)

val VodrLightColorScheme = lightColorScheme(
    primary = AccentBlue,
    secondary = Slate700,
    tertiary = Slate500,
    surface = SurfaceLight,
)

val VodrDarkColorScheme = darkColorScheme(
    primary = AccentBlue,
    secondary = Slate500,
    tertiary = Slate700,
    surface = SurfaceDark,
)

val VodrTypography = Typography(
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.2).sp,
    ),
    headlineSmall = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    ),
)

val VodrShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Immutable
data class VodrSpacing(
    val xxxs: Dp = 2.dp,
    val xxs: Dp = 4.dp,
    val xs: Dp = 8.dp,
    val sm: Dp = 12.dp,
    val md: Dp = 16.dp,
    val lg: Dp = 20.dp,
    val xl: Dp = 24.dp,
    val miniPlayerInsetVertical: Dp = 12.dp,
    val miniPlayerInsetHorizontal: Dp = 16.dp,
    val miniPlayerContentVertical: Dp = 14.dp,
    val bottomBarClearance: Dp = 92.dp,
)

@Immutable
data class VodrSizes(
    val actionMinHeight: Dp = 48.dp,
    val actionIcon: Dp = 18.dp,
    val compactActionIcon: Dp = 20.dp,
    val libraryShelfCardWidth: Dp = 260.dp,
    val playerSessionCardWidth: Dp = 240.dp,
    val playerSessionArtworkWidth: Dp = 52.dp,
    val playerSessionArtworkHeight: Dp = 72.dp,
    val miniPlayerArtworkWidth: Dp = 52.dp,
    val miniPlayerArtworkHeight: Dp = 68.dp,
    val sessionArtworkWidth: Dp = 56.dp,
    val sessionArtworkHeight: Dp = 76.dp,
    val documentArtworkWidth: Dp = 56.dp,
    val documentArtworkHeight: Dp = 72.dp,
    val continueArtworkWidth: Dp = 70.dp,
    val continueArtworkHeight: Dp = 96.dp,
    val heroArtworkWidth: Dp = 144.dp,
    val heroArtworkHeight: Dp = 192.dp,
    val heroPlaceholderSize: Dp = 128.dp,
    val timelineMarkerWidth: Dp = 34.dp,
    val timelineMarkerHeight: Dp = 12.dp,
    val timelineMarkerSelectedHeight: Dp = 18.dp,
)

@Immutable
data class VodrAlpha(
    val subtleSurface: Float = 0.35f,
    val mutedSurface: Float = 0.45f,
    val accentSurface: Float = 0.68f,
    val heroSurface: Float = 0.72f,
    val elevatedSurface: Float = 0.82f,
    val inactiveMarker: Float = 0.28f,
)

@Immutable
data class VodrElevation(
    val floatingCard: Dp = 8.dp,
)

private val LocalVodrSpacing = staticCompositionLocalOf { VodrSpacing() }
private val LocalVodrSizes = staticCompositionLocalOf { VodrSizes() }
private val LocalVodrAlpha = staticCompositionLocalOf { VodrAlpha() }
private val LocalVodrElevation = staticCompositionLocalOf { VodrElevation() }

object VodrUiTheme {
    val spacing: VodrSpacing
        @Composable get() = LocalVodrSpacing.current

    val sizes: VodrSizes
        @Composable get() = LocalVodrSizes.current

    val alpha: VodrAlpha
        @Composable get() = LocalVodrAlpha.current

    val elevation: VodrElevation
        @Composable get() = LocalVodrElevation.current

    val motion: VodrMotion
        @Composable get() = LocalVodrMotion.current
}

@Composable
fun ProvideVodrUiTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalVodrSpacing provides VodrSpacing(),
        LocalVodrSizes provides VodrSizes(),
        LocalVodrAlpha provides VodrAlpha(),
        LocalVodrElevation provides VodrElevation(),
        LocalVodrMotion provides VodrMotion(),
        content = content,
    )
}

object VodrSurfaceStyles {
    @Composable
    fun subtleCardColors(): CardColors {
        return CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = VodrUiTheme.alpha.subtleSurface,
            ),
        )
    }

    @Composable
    fun mutedCardColors(): CardColors {
        return CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = VodrUiTheme.alpha.mutedSurface,
            ),
        )
    }

    @Composable
    fun heroCardColors(): CardColors {
        return CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(
                alpha = VodrUiTheme.alpha.heroSurface,
            ),
        )
    }

    @Composable
    fun accentCardColors(): CardColors {
        return CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = VodrUiTheme.alpha.accentSurface,
            ),
        )
    }

    @Composable
    fun sessionCardColors(
        isCurrent: Boolean,
        isFavorite: Boolean,
    ): CardColors {
        val containerColor = when {
            isCurrent -> MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = VodrUiTheme.alpha.heroSurface,
            )
            isFavorite -> MaterialTheme.colorScheme.tertiaryContainer.copy(
                alpha = VodrUiTheme.alpha.heroSurface,
            )
            else -> MaterialTheme.colorScheme.surface.copy(
                alpha = VodrUiTheme.alpha.elevatedSurface,
            )
        }
        return CardDefaults.cardColors(containerColor = containerColor)
    }

    @Composable
    fun shelfCardColors(
        emphasized: Boolean,
    ): CardColors {
        val containerColor = if (emphasized) {
            MaterialTheme.colorScheme.secondaryContainer.copy(
                alpha = VodrUiTheme.alpha.accentSurface,
            )
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(
                alpha = VodrUiTheme.alpha.subtleSurface,
            )
        }
        return CardDefaults.cardColors(containerColor = containerColor)
    }
}
