package com.omniclaw.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Orbit-AI motion design system.
 *
 * Single source of truth for durations, easing curves, and spring physics so every
 * screen feels consistent instead of each component inventing its own animation spec.
 */
object MotionTokens {
    // ---- Duration tiers ----
    const val DURATION_FAST = 150
    const val DURATION_NORMAL = 300
    const val DURATION_EXPRESSIVE = 500

    // ---- Easing curves (Material 3 motion spec) ----
    /** General purpose, the default for most transitions. */
    val EasingStandard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Entering elements - starts fast, settles gently. */
    val EasingDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

    /** Exiting elements - starts gentle, leaves quickly. */
    val EasingAccelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)

    /** Large or attention-grabbing motion (FAB morphs, hero transitions). */
    val EasingEmphasized = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    // ---- Spring presets ----
    /** Snappy, controlled - for press feedback. No overshoot wobble. */
    val SpringSnappy = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 600f
    )

    /** Soft bounce - for playful UI like page indicators, badges. */
    val SpringBouncy = spring<Float>(
        dampingRatio = 0.55f,
        stiffness = 380f
    )

    /** Slow, weighted - for large surfaces (sheets, cards expanding). */
    val SpringGentle = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = 200f
    )

    // ---- Common tween specs built from the tokens above ----
    val TweenFast = tween<Float>(durationMillis = DURATION_FAST, easing = EasingStandard)
    val TweenNormal = tween<Float>(durationMillis = DURATION_NORMAL, easing = EasingStandard)
    val TweenExpressive = tween<Float>(durationMillis = DURATION_EXPRESSIVE, easing = EasingEmphasized)
}

/**
 * Consistent press-scale feedback for any clickable surface. Replaces ad hoc
 * `animateFloatAsState(spring(...))` blocks duplicated across components.
 *
 * Usage: `Modifier.pressScale(interactionSource).clickable(interactionSource, ...) { }`
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MotionTokens.SpringSnappy,
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Staggered reveal for list items: fades + rises into place with a delay proportional
 * to list position, capped so long lists don't take forever to finish appearing.
 *
 * Usage inside a LazyColumn item: `Modifier.staggeredEntrance(index = indexInList)`
 */
@Composable
fun Modifier.staggeredEntrance(
    index: Int,
    baseDelayMs: Int = 35,
    maxDelayMs: Int = 320
): Modifier {
    var visible by remember(index) { mutableStateOf(false) }
    LaunchedEffect(index) {
        delay(minOf(index * baseDelayMs, maxDelayMs).toLong())
        visible = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(MotionTokens.DURATION_NORMAL, easing = MotionTokens.EasingDecelerate),
        label = "staggerAlpha"
    )
    val translateY by animateFloatAsState(
        targetValue = if (visible) 0f else 28f,
        animationSpec = MotionTokens.SpringGentle,
        label = "staggerTranslateY"
    )
    return this.graphicsLayer {
        this.alpha = alpha
        translationY = translateY
    }
}
