package com.vodr.ui.theme

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.IntSize

@Immutable
data class VodrMotion(
    val quickDurationMillis: Int = 180,
    val standardDurationMillis: Int = 280,
    val emphasizedDurationMillis: Int = 420,
)

internal val LocalVodrMotion = staticCompositionLocalOf { VodrMotion() }

object VodrMotionSpecs {
    @Composable
    fun contentSize(): FiniteAnimationSpec<IntSize> {
        return spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow,
        )
    }

    @Composable
    fun progressFloat(): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = VodrUiTheme.motion.quickDurationMillis,
            easing = LinearOutSlowInEasing,
        )
    }

    @Composable
    fun crossfadeFloat(): FiniteAnimationSpec<Float> {
        return tween(
            durationMillis = VodrUiTheme.motion.standardDurationMillis,
            easing = FastOutSlowInEasing,
        )
    }

    @Composable
    fun sectionEnter(): EnterTransition {
        return fadeIn(animationSpec = crossfadeFloat()) +
            expandVertically(
                animationSpec = tween(
                    durationMillis = VodrUiTheme.motion.standardDurationMillis,
                    easing = FastOutSlowInEasing,
                ),
                expandFrom = Alignment.Top,
            )
    }

    @Composable
    fun sectionExit(): ExitTransition {
        return fadeOut(
            animationSpec = tween(
                durationMillis = VodrUiTheme.motion.quickDurationMillis,
                easing = FastOutLinearInEasing,
            ),
        ) + shrinkVertically(
            animationSpec = tween(
                durationMillis = VodrUiTheme.motion.quickDurationMillis,
                easing = FastOutLinearInEasing,
            ),
            shrinkTowards = Alignment.Top,
        )
    }
}

fun Modifier.vodrAnimateContentSize(): Modifier = composed {
    animateContentSize(animationSpec = VodrMotionSpecs.contentSize())
}

@Composable
fun VodrAnimatedVisibility(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable AnimatedVisibilityScope.() -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = VodrMotionSpecs.sectionEnter(),
        exit = VodrMotionSpecs.sectionExit(),
        content = content,
    )
}

@Composable
fun <T> VodrCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String,
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = VodrMotionSpecs.crossfadeFloat(),
        label = label,
        content = content,
    )
}
