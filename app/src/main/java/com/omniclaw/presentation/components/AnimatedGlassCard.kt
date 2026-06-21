package com.omniclaw.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.omniclaw.ui.theme.OmniClawGlassBorder
import com.omniclaw.ui.theme.OmniClawGlassOverlay

@Composable
fun AnimatedGlassCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    radius: Int = 16,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scaleTarget = if (isPressed) 0.96f else 1.0f
    val animatedScale by animateFloatAsState(
        targetValue = scaleTarget,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "CardPressBounce"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                alpha = 0.95f
                clip = true
            }
            .clip(RoundedCornerShape(radius.dp))
            .background(OmniClawGlassOverlay)
            .border(1.dp, OmniClawGlassBorder, RoundedCornerShape(radius.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        content = content
    )
}
