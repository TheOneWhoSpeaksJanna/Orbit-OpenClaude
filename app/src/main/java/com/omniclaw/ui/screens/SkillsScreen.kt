package com.omniclaw.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniclaw.ui.theme.OmniClawSuccess
import com.omniclaw.ui.theme.OmniClawWarning
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniclaw.ui.theme.OmniClawAccent
import com.omniclaw.ui.theme.OmniClawAccentSecondary
import com.omniclaw.ui.theme.OmniClawGlassOverlay
import com.omniclaw.ui.theme.OmniClawObsidianBase
import com.omniclaw.ui.theme.OmniClawSuccess
import com.omniclaw.ui.theme.OmniClawTextPrimary
import com.omniclaw.ui.theme.OmniClawTextSecondary
import com.omniclaw.ui.theme.OmniClawTextTertiary
import com.omniclaw.ui.viewmodels.SkillsViewModel

private const val SECTION_ACTIVE = "Active Capabilities"
private const val SECTION_AGENTS = "Agents"
private const val SECTION_TOOLS = "Registered Tools"
private const val SUBTITLE_CAPABILITIES = "Installed agents, tools, and extension modules"
private const val DEFAULT_AGENT_NAME = "Default Agent"
private const val DEFAULT_AGENT_DESC = "General-purpose AI orchestration agent"
private const val DEFAULT_PROMPT_FALLBACK = "General-purpose agent"
private const val STATUS_ACTIVE = "Active"
private const val STATUS_READY = "Ready"
private const val STATUS_AVAILABLE = "Available"

@Composable
fun SkillsScreen(
    viewModel: SkillsViewModel = viewModel(factory = SkillsViewModel.Factory)
) {
    val agents by viewModel.agents.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OmniClawObsidianBase)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = SECTION_ACTIVE,
                style = MaterialTheme.typography.headlineMedium,
                color = OmniClawTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = SUBTITLE_CAPABILITIES,
                style = MaterialTheme.typography.bodyMedium,
                color = OmniClawTextSecondary
            )
        }

        item {
            Text(
                text = SECTION_AGENTS,
                style = MaterialTheme.typography.titleMedium,
                color = OmniClawAccent,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(agents, key = { it.id }) { agent ->
            CapabilityCard(
                name = agent.name,
                description = agent.systemPrompt?.take(80) ?: DEFAULT_PROMPT_FALLBACK,
                icon = Icons.Default.Memory,
                accentColor = OmniClawAccent,
                status = STATUS_ACTIVE
            )
        }

        if (agents.isEmpty()) {
            item {
                CapabilityCard(
                    name = DEFAULT_AGENT_NAME,
                    description = DEFAULT_AGENT_DESC,
                    icon = Icons.Default.Memory,
                    accentColor = OmniClawAccent,
                    status = STATUS_READY
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = SECTION_TOOLS,
                style = MaterialTheme.typography.titleMedium,
                color = OmniClawAccentSecondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            val defaultTools = remember {
                listOf(
                    "Execute Command" to "Run shell commands via terminal",
                    "Read File" to "Read file contents from the filesystem",
                    "Glob Search" to "Pattern-based file discovery"
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                defaultTools.forEach { (name, desc) ->
                    CapabilityCard(
                        name = name,
                        description = desc,
                        icon = Icons.Default.Build,
                        accentColor = OmniClawAccentSecondary,
                        status = STATUS_AVAILABLE
                    )
                }
            }
        }
    }
}

@Composable
private fun CapabilityCard(
    name: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accentColor: androidx.compose.ui.graphics.Color,
    status: String
) {
    val shape = remember { RoundedCornerShape(14.dp) }
    val statusColor = when (status.lowercase()) {
        "active", "enabled", "ready", "available" -> OmniClawSuccess
        else -> OmniClawWarning
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(OmniClawGlassOverlay)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = accentColor
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OmniClawTextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniClawTextTertiary
                )
            }
        }
    }
}
