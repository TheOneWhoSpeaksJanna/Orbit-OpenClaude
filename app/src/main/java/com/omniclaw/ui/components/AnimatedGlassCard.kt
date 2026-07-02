package com.omniclaw.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.omniclaw.ui.theme.MotionTokens
import com.omniclaw.ui.theme.OmniClawColors
import com.omniclaw.ui.theme.pressScale

private const val DEFAULT_RADIUS = 16
private const val BORDER_DP = 1
private const val SELECTED_BORDER_DP = 1.5f

/**
 * Foundational glass card used throughout the app.
 *
 * Theme-aware: pulls glass overlay/border from [OmniClawColors] so it renders correctly in both
 * dark and light mode, instead of the previous hardcoded white-alpha overlay that was invisible
 * on a light background.
 *
 * [onClick] is nullable - pass `null` for a purely informational card (e.g. a status display)
 * so it doesn't fake interactivity with a ripple/press-scale that goes nowhere.
 *
 * Performance: background + border are drawn via a single [Modifier.drawWithCache] block.
 * This lets the Compose runtime cache the resulting Path objects (one fill + one stroke)
 * and only re-execute the draw lambda when the size or colors actually change. The
 * previous version used `Modifier.background(...).border(...)`, which created two
 * separate Modifier nodes that each invalidated their own draw cache and forced a
 * layout pass per color animation frame.
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
    val primaryColor = MaterialTheme.colorScheme.primary

    val backgroundColor by animateColorAsState(
        targetValue = if (selected) colors.glassOverlayPressed else colors.glassOverlay,
        animationSpec = MotionTokens.TweenFastColor,
        label = "GlassBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) primaryColor else colors.glassBorder,
        animationSpec = MotionTokens.TweenFastColor,
        label = "GlassBorder"
    )
    val borderWidthPx = (if (selected) SELECTED_BORDER_DP else BORDER_DP.toFloat())

    var cardModifier = modifier
        .pressScale(interactionSource)
        .clip(shape)
        .drawWithCache {
            // Build the outline Path once per size change.
            val outline = shape.createOutline(size, layoutDirection, this)
            val fillPath: Path? = when (outline) {
                is androidx.compose.ui.graphics.Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is androidx.compose.ui.graphics.Outline.Generic -> outline.path
                else -> null
            }
            val strokePath: Path? = when (outline) {
                is androidx.compose.ui.graphics.Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                is androidx.compose.ui.graphics.Outline.Generic -> outline.path
                else -> null
            }
            val strokePx = borderWidthPx * density
            val inset = strokePx / 2f
            val insetSize = Size(size.width - strokePx, size.height - strokePx)

            onDrawWithContent {
                // Background fill
                if (fillPath != null) {
                    drawPath(fillPath, backgroundColor)
                } else {
                    drawRect(backgroundColor)
                }
                // Border stroke (inset by half stroke width so it sits on the edge)
                if (strokePath != null && borderColor.alpha > 0f && strokePx > 0f) {
                    drawPath(
                        path = strokePath,
                        color = borderColor,
                        style = Stroke(width = strokePx)
                    )
                }
                // Content on top
                drawContent()
            }
        }

    if (onClick != null) {
        cardModifier = cardModifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )
    }

    Box(modifier = cardModifier, content = content)
}
