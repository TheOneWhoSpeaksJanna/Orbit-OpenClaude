package com.omniclaw.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniclaw.core.di.ToolCallRecord
import com.omniclaw.domain.models.TermuxLog
import com.omniclaw.ui.theme.OmniClawColors

private const val SECTION_SPACING_DP = 10
private const val CARD_PADDING_DP = 12
private const val ROW_SPACING_DP = 8
private const val ROW_SPACING_SMALL_DP = 6
private const val ICON_SIZE_SMALL_DP = 14
private const val ICON_SIZE_TOOL_DP = 16
private const val TOP_PADDING_DP = 4
private const val PREVIEW_MAX_LENGTH = 60
private const val TOOL_TAKE_LAST = 5
private const val TERMINAL_TAKE_LAST = 3
private const val FONT_SIZE_OUTPUT_SP = 12
private const val FONT_SIZE_EXIT_SP = 10
private const val FONT_SIZE_PREVIEW_SP = 10

private const val HEADING_CODEX = "Codex"
private const val HEADING_TOOL_EXECUTIONS = "Tool Executions"
private const val HEADING_TERMINAL_HISTORY = "Terminal History"
private const val LABEL_UNCOMMITTED_CHANGES = "Uncommitted changes"
private const val LABEL_NO_CHANGES = "No uncommitted changes"

@Composable
fun CodexSection(
    toolCallRecords: List<ToolCallRecord>,
    termuxLogs: List<TermuxLog>,
    gitDiffResult: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(SECTION_SPACING_DP.dp)
    ) {
        Text(
            text = HEADING_CODEX,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.Bold
        )

        if (gitDiffResult != null) {
            val lines = gitDiffResult.lines().filter { it.isNotBlank() }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ROW_SPACING_SMALL_DP.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(ICON_SIZE_SMALL_DP.dp)
                    )
                    Text(
                        text = "$LABEL_UNCOMMITTED_CHANGES (${lines.size} files)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                lines.forEach { line ->
                    DiffChip(diffLine = line)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ROW_SPACING_SMALL_DP.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(ICON_SIZE_SMALL_DP.dp)
                )
                Text(
                    text = LABEL_NO_CHANGES,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (toolCallRecords.isNotEmpty()) {
            Text(
                text = HEADING_TOOL_EXECUTIONS,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            toolCallRecords.takeLast(TOOL_TAKE_LAST).reversed().forEach { record ->
                ToolExecutionCard(record)
            }
        }

        if (termuxLogs.isNotEmpty()) {
            Text(
                text = HEADING_TERMINAL_HISTORY,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            termuxLogs.takeLast(TERMINAL_TAKE_LAST).reversed().forEach { log ->
                TerminalBlock(log = log)
            }
        }
    }
}

@Composable
private fun ToolExecutionCard(
    record: ToolCallRecord
) {
    val isError = record.exitCode != 0
    val preview = record.output.take(PREVIEW_MAX_LENGTH).replace("\n", " ")
    val extended = OmniClawColors.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(CARD_PADDING_DP.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ROW_SPACING_DP.dp)
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = if (isError) MaterialTheme.colorScheme.error else extended.success,
                    modifier = Modifier.size(ICON_SIZE_TOOL_DP.dp)
                )
                Text(
                    text = "\$ ${record.command}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = FONT_SIZE_OUTPUT_SP.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "exit ${record.exitCode}",
                    fontSize = FONT_SIZE_EXIT_SP.sp,
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (preview.isNotEmpty()) {
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                    fontSize = FONT_SIZE_PREVIEW_SP.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(top = TOP_PADDING_DP.dp)
                )
            }
        }
    }
}
