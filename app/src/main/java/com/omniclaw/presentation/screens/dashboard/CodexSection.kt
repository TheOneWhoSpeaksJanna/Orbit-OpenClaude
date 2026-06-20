package com.omniclaw.presentation.screens.dashboard

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniclaw.core.di.ToolCallRecord
import com.omniclaw.domain.model.TermuxLog
import com.omniclaw.ui.theme.OmniClawAccent
import com.omniclaw.ui.theme.OmniClawError
import com.omniclaw.ui.theme.OmniClawSuccess
import com.omniclaw.ui.theme.OmniClawSurfaceElevated
import com.omniclaw.ui.theme.OmniClawTextPrimary
import com.omniclaw.ui.theme.OmniClawTextSecondary

@Composable
fun CodexSection(
    toolCallRecords: List<ToolCallRecord>,
    termuxLogs: List<TermuxLog>,
    gitDiffResult: String?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Codex",
            style = MaterialTheme.typography.titleMedium,
            color = OmniClawAccent,
            fontWeight = FontWeight.Bold
        )

        if (gitDiffResult != null) {
            val lines = gitDiffResult.lines().filter { it.isNotBlank() }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = OmniClawAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Uncommitted changes (${lines.size} files)",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniClawTextSecondary
                    )
                }
                lines.forEach { line ->
                    DiffChip(diffLine = line)
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = OmniClawTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "No uncommitted changes",
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniClawTextSecondary
                )
            }
        }

        if (toolCallRecords.isNotEmpty()) {
            Text(
                text = "Tool Executions",
                style = MaterialTheme.typography.labelLarge,
                color = OmniClawTextPrimary
            )
            toolCallRecords.takeLast(5).reversed().forEach { record ->
                ToolExecutionCard(record)
            }
        }

        if (termuxLogs.isNotEmpty()) {
            Text(
                text = "Terminal History",
                style = MaterialTheme.typography.labelLarge,
                color = OmniClawTextPrimary
            )
            termuxLogs.takeLast(3).reversed().forEach { log ->
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
    val preview = record.output.take(60).replace("\n", " ")

    Card(
        colors = CardDefaults.cardColors(
            containerColor = OmniClawSurfaceElevated
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.Code,
                    contentDescription = null,
                    tint = if (isError) OmniClawError else OmniClawSuccess,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "\$ ${record.command}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = OmniClawAccent,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = "exit ${record.exitCode}",
                    fontSize = 10.sp,
                    color = if (isError) OmniClawError else OmniClawTextSecondary
                )
            }
            if (preview.isNotEmpty()) {
                Text(
                    text = preview.ifEmpty { "(empty)" },
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniClawTextSecondary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
