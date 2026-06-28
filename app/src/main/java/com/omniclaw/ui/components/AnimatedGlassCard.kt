package com.omniclaw.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.omniclaw.ui.theme.MotionTokens
import com.omniclaw.ui.theme.OmniClawColors
import com.omniclaw.ui.theme.pressScale

private const val DEFAULT_RADIUS = 16
private const val BORDER_DP = 1
private const val SELECTED_BORDER_DP = 1.5

/**
 * Foundational glass card used throughout the app.
 *
 * Theme-aware: pulls glass overlay/border from [OmniClawColors] so it renders correctly in both
 * dark and light mode, instead of the previous hardcoded white-alpha overlay that was invisible
 * on a light background.
 *
 * [onClick] is nullable - pass `null` for a purely informational card (e.g. a status display)
 * so it doesn't fake interactivity with a ripple/press-scale that goes nowhere.
 */
@Composable
fun AnimatedGlassCard(
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    radius: Int = DEFAULT_RADIUS,
    selected: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = remember(radius) { RoundedCornerShape(radius.dp) }
    val colors = OmniClawColors.current

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) colors.glassOverlayPressed else colors.glassOverlay,
        animationSpec = MotionTokens.TweenFast,
        label = "GlassBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else colors.glassBorder,
        animationSpec = MotionTokens.TweenFast,
        label = "GlassBorder"
    )

    var cardModifier = modifier
        .pressScale(interactionSource)
        .clip(shape)
        .background(backgroundColor)
        .border(
            width = if (selected) SELECTED_BORDER_DP.dp else BORDER_DP.dp,
            color = borderColor,
            shape = shape
        )

    if (onClick != null) {
        cardModifier = cardModifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

    Box(modifier = cardModifier, content = content)
}
