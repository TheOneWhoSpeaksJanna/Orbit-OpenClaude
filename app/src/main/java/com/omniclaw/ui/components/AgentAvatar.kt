package com.omniclaw.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val AVATAR_SIZE_DP = 48
private const val STROKE_BORDER = 1.5f
private const val STROKE_REGULAR = 2f
private const val STROKE_LARGE = 2.5f

/**
 * Unique illustrative avatar for each downloadable agent.
 * Draws a gradient-background circle with a distinctive agent icon on top.
 *
 * Performance: the radial gradient [Brush] is cached via [remember] so we
 * don't allocate a new Brush on every draw pass. The agent-icon Path
 * objects are still allocated per draw inside the icon functions — these
 * are small (~5 vertices each) and only run once per avatar per frame,
 * so the overhead is acceptable for the typical "few avatars per screen"
 * case. If you start rendering dozens of avatars simultaneously, consider
 * memoizing the Paths too via a LruCache keyed on iconName.
 */
@Composable
fun AgentAvatar(
    iconName: String,
    accentColor: Color,
    size: Dp = AVATAR_SIZE_DP.dp,
    modifier: Modifier = Modifier
) {
    // Cache the radial gradient Brush per (color, size) — the previous
    // version allocated a fresh Brush.radialGradient on every draw call,
    // which is the kind of thing that quietly shows up as jank in flame
    // charts when many avatars are visible at once.
    val radiusPx = remember(size) { size.value }
    val backgroundBrush = remember(accentColor, radiusPx) {
        Brush.radialGradient(
            colors = listOf(
                accentColor.copy(alpha = 0.25f),
                accentColor.copy(alpha = 0.08f)
            ),
            center = Offset(radiusPx / 2f, radiusPx / 2f),
            radius = radiusPx / 2f
        )
    }
    val borderStrokeColor = remember(accentColor) { accentColor.copy(alpha = 0.2f) }

    Canvas(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
    ) {
        drawCircle(brush = backgroundBrush)
        drawCircle(
            color = borderStrokeColor,
            style = Stroke(width = STROKE_BORDER)
        )

        val iconScale = size.toPx() / AVATAR_SIZE_DP.toFloat()
        when (iconName) {
            "terminal" -> drawTerminalIcon(accentColor, iconScale)
            "code" -> drawCodeIcon(accentColor, iconScale)
            "eye" -> drawEyeIcon(accentColor, iconScale)
            "radar" -> drawRadarIcon(accentColor, iconScale)
            "database" -> drawDatabaseIcon(accentColor, iconScale)
            "hook" -> drawHookIcon(accentColor, iconScale)
            "opencode" -> drawOpenCodeIcon(accentColor, iconScale)
        }
    }
}

private fun DrawScope.drawTerminalIcon(color: Color, s: Float) {
    drawLine(color, Offset(12f * s, 18f * s), Offset(30f * s, 18f * s), strokeWidth = STROKE_REGULAR * s)
    drawLine(color, Offset(12f * s, 28f * s), Offset(24f * s, 28f * s), strokeWidth = STROKE_REGULAR * s)
    drawLine(color, Offset(16f * s, 14f * s), Offset(20f * s, 18f * s), strokeWidth = STROKE_REGULAR * s)
    drawLine(color, Offset(16f * s, 22f * s), Offset(20f * s, 18f * s), strokeWidth = STROKE_REGULAR * s)
    drawRect(
        color = color.copy(alpha = 0.7f),
        topLeft = Offset(32f * s, 14f * s),
        size = Size(3f * s, 7f * s)
    )
}

private fun DrawScope.drawCodeIcon(color: Color, s: Float) {
    drawCircle(
        color = color,
        radius = 10f * s,
        center = Offset(20f * s, 18f * s),
        style = Stroke(width = STROKE_LARGE * s)
    )
    drawLine(
        color,
        Offset(27f * s, 25f * s),
        Offset(34f * s, 32f * s),
        strokeWidth = STROKE_LARGE * s,
        cap = StrokeCap.Round
    )
    val cx = 20f * s
    val cy = 18f * s
    val codeStroke = 1.8f * s
    drawLine(color, Offset(cx - 4f * s, cy - 3f * s), Offset(cx - 4f * s, cy), strokeWidth = codeStroke)
    drawLine(color, Offset(cx - 4f * s, cy), Offset(cx - 4f * s, cy + 3f * s), strokeWidth = codeStroke)
    drawLine(color, Offset(cx + 4f * s, cy - 3f * s), Offset(cx + 4f * s, cy), strokeWidth = codeStroke)
    drawLine(color, Offset(cx + 4f * s, cy), Offset(cx + 4f * s, cy + 3f * s), strokeWidth = codeStroke)
    drawLine(color, Offset(cx - 1f * s, cy - 1f * s), Offset(cx + 1f * s, cy), strokeWidth = codeStroke)
    drawLine(color, Offset(cx - 1f * s, cy + 1f * s), Offset(cx + 1f * s, cy), strokeWidth = codeStroke)
}

private fun DrawScope.drawEyeIcon(color: Color, s: Float) {
    val docPath = Path().apply {
        moveTo(14f * s, 10f * s)
        lineTo(28f * s, 10f * s)
        lineTo(28f * s, 34f * s)
        lineTo(12f * s, 34f * s)
        lineTo(12f * s, 16f * s)
        close()
    }
    drawPath(docPath, color, style = Stroke(width = STROKE_REGULAR * s, join = StrokeJoin.Round))

    drawOval(
        color,
        topLeft = Offset(14f * s, 19f * s),
        size = Size(12f * s, 8f * s),
        style = Stroke(width = STROKE_REGULAR * s)
    )
    drawCircle(color, radius = 2.5f * s, center = Offset(20f * s, 23f * s))

    val foldPath = Path().apply {
        moveTo(14f * s, 16f * s)
        lineTo(20f * s, 16f * s)
        lineTo(20f * s, 10f * s)
        close()
    }
    drawPath(foldPath, color.copy(alpha = 0.5f), style = Fill)
}

private fun DrawScope.drawRadarIcon(color: Color, s: Float) {
    val cx = 20f * s
    val cy = 22f * s

    for (i in 1..3) {
        val r = i * 7f * s
        drawArc(
            color.copy(alpha = 0.6f - i * 0.15f),
            startAngle = -120f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(cx - r, cy - r),
            size = Size(r * 2, r * 2),
            style = Stroke(width = 1.5f * s)
        )
    }

    drawLine(color, Offset(cx, cy), Offset(cx + 17f * s, cy - 10f * s), strokeWidth = 1.5f * s)
    drawCircle(color, radius = 2.5f * s, center = Offset(cx + 10f * s, cy - 5f * s))
    drawCircle(color.copy(alpha = 0.6f), radius = 2f * s, center = Offset(cx - 6f * s, cy + 11f * s))
    drawCircle(color, radius = 2f * s, center = Offset(cx, cy))
}

private fun DrawScope.drawDatabaseIcon(color: Color, s: Float) {
    val dbLeft = 13f * s
    val dbTop = 12f * s
    val dbW = 16f * s
    val dbH = 8f * s

    for (i in 0..2) {
        val y = dbTop + i * 7f * s
        drawOval(
            color.copy(alpha = 0.9f - i * 0.2f),
            topLeft = Offset(dbLeft, y),
            size = Size(dbW, dbH * 0.5f),
            style = Stroke(width = 1.8f * s)
        )
        if (i == 0) {
            drawLine(color.copy(alpha = 0.7f), Offset(dbLeft, y + dbH * 0.25f * s), Offset(dbLeft, y + 2f * s), strokeWidth = 1.5f * s)
            drawLine(color.copy(alpha = 0.7f), Offset(dbLeft + dbW, y + dbH * 0.25f * s), Offset(dbLeft + dbW, y + 2f * s), strokeWidth = 1.5f * s)
        }
    }

    val arrowY = 22f * s
    drawLine(color, Offset(32f * s, arrowY), Offset(40f * s, arrowY), strokeWidth = STROKE_REGULAR * s)
    drawLine(color, Offset(37f * s, arrowY - 3f * s), Offset(40f * s, arrowY), strokeWidth = STROKE_REGULAR * s)
    drawLine(color, Offset(37f * s, arrowY + 3f * s), Offset(40f * s, arrowY), strokeWidth = STROKE_REGULAR * s)
}

private fun DrawScope.drawHookIcon(color: Color, s: Float) {
    val stroke = Stroke(width = STROKE_LARGE * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val hookPath = Path().apply {
        moveTo(14f * s, 12f * s)
        cubicTo(14f * s, 12f * s, 32f * s, 12f * s, 32f * s, 20f * s)
        cubicTo(32f * s, 28f * s, 22f * s, 30f * s, 20f * s, 24f * s)
    }
    drawPath(hookPath, color, style = stroke)

    drawLine(color, Offset(20f * s, 24f * s), Offset(16f * s, 24f * s), strokeWidth = STROKE_LARGE * s, cap = StrokeCap.Round)

    drawOval(
        color.copy(alpha = 0.7f),
        topLeft = Offset(10f * s, 9f * s),
        size = Size(10f * s, 6f * s),
        style = Stroke(width = STROKE_REGULAR * s)
    )
}

private fun DrawScope.drawOpenCodeIcon(color: Color, s: Float) {
    val infinityPath = Path().apply {
        moveTo(14f * s, 24f * s)
        cubicTo(12f * s, 22f * s, 12f * s, 18f * s, 16f * s, 16f * s)
        cubicTo(20f * s, 14f * s, 24f * s, 18f * s, 24f * s, 24f * s)
        cubicTo(24f * s, 18f * s, 28f * s, 14f * s, 32f * s, 16f * s)
        cubicTo(36f * s, 18f * s, 36f * s, 22f * s, 34f * s, 24f * s)
    }
    drawPath(
        infinityPath,
        color,
        style = Stroke(width = STROKE_LARGE * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )

    drawLine(color, Offset(26f * s, 12f * s), Offset(26f * s, 12f * s), strokeWidth = STROKE_REGULAR * s, cap = StrokeCap.Round)
    drawLine(color, Offset(24f * s, 10f * s), Offset(28f * s, 10f * s), strokeWidth = STROKE_REGULAR * s, cap = StrokeCap.Round)
    drawLine(color, Offset(24f * s, 14f * s), Offset(28f * s, 14f * s), strokeWidth = STROKE_REGULAR * s, cap = StrokeCap.Round)

    drawCircle(color.copy(alpha = 0.5f), radius = 1.5f * s, center = Offset(20f * s, 22f * s))
    drawCircle(color.copy(alpha = 0.5f), radius = 1.5f * s, center = Offset(28f * s, 22f * s))
}
