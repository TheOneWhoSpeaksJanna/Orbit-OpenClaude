package com.omniclaw.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniclaw.domain.models.TermuxLog
import com.omniclaw.ui.theme.OrbitSuccess
import com.omniclaw.ui.theme.OrbitError

private const val BLOCK_SHAPE_RADIUS_DP = 8
private const val BLOCK_PADDING_DP = 10
private const val ICON_SIZE_DP = 14
private const val SPACER_SIZE_DP = 6
private const val SPACER_HEIGHT_DP = 4
private const val PREVIEW_MAX_LENGTH = 80
private const val STATUS_FONT_SIZE_SP = 11
private const val COMMAND_FONT_SIZE_SP = 11
private const val PREVIEW_FONT_SIZE_SP = 10
private const val EXPANDED_TOP_PADDING_DP = 6
private const val EXPANDED_FONT_SIZE_SP = 10

private const val MESSAGE_COMMAND_FAILED = "Command failed"
private const val MESSAGE_NO_OUTPUT = "No output"
private const val MESSAGE_EMPTY = "(empty)"

@Composable
fun TerminalBlock(
    log: TermuxLog,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val isError = log.exitCode != 0
    val preview = log.output.take(PREVIEW_MAX_LENGTH).replace("\n", " ")

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(BLOCK_SHAPE_RADIUS_DP.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { expanded = !expanded }
            .padding(BLOCK_PADDING_DP.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isError) Icons.Default.Error else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = if (isError) OrbitError else OrbitSuccess,
                modifier = Modifier.size(ICON_SIZE_DP.dp)
            )
            Spacer(Modifier.size(SPACER_SIZE_DP.dp))
            Text(
                text = "${log.exitCode}",
                color = if (isError) OrbitError else OrbitSuccess,
                fontSize = STATUS_FONT_SIZE_SP.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "\$ ${log.command}",
                fontFamily = FontFamily.Monospace,
                fontSize = COMMAND_FONT_SIZE_SP.sp,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(SPACER_HEIGHT_DP.dp))
        Text(
            text = preview.ifEmpty { if (isError) MESSAGE_COMMAND_FAILED else MESSAGE_NO_OUTPUT },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            fontSize = PREVIEW_FONT_SIZE_SP.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 2
        )
        AnimatedVisibility(visible = expanded) {
            Text(
                text = log.output.ifEmpty { MESSAGE_EMPTY },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.Monospace,
                fontSize = EXPANDED_FONT_SIZE_SP.sp,
                modifier = Modifier.padding(top = EXPANDED_TOP_PADDING_DP.dp)
            )
        }
    }
}
