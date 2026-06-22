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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniclaw.domain.models.AgentCategory
import com.omniclaw.domain.models.DownloadState
import com.omniclaw.domain.models.DownloadableAgent
import com.omniclaw.ui.theme.OmniClawAccent
import com.omniclaw.ui.theme.OmniClawAccentSecondary
import com.omniclaw.ui.theme.OmniClawGlassOverlay
import com.omniclaw.ui.theme.OmniClawObsidianBase
import com.omniclaw.ui.theme.OmniClawSuccess
import com.omniclaw.ui.theme.OmniClawTextPrimary
import com.omniclaw.ui.theme.OmniClawTextSecondary
import com.omniclaw.ui.theme.OmniClawTextTertiary
import com.omniclaw.ui.theme.OmniClawWarning
import com.omniclaw.ui.viewmodels.DownloadViewModel
import com.omniclaw.ui.viewmodels.SkillsViewModel

@Composable
fun SkillsScreen(
    viewModel: SkillsViewModel = viewModel(factory = SkillsViewModel.Factory),
    downloadViewModel: DownloadViewModel = viewModel(factory = DownloadViewModel.Factory)
) {
    val agents by viewModel.agents.collectAsState()
    val downloadCatalog by downloadViewModel.agents.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(OmniClawObsidianBase)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Active Capabilities",
                style = MaterialTheme.typography.headlineMedium,
                color = OmniClawTextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Installed agents, tools, and extension modules",
                style = MaterialTheme.typography.bodyMedium,
                color = OmniClawTextSecondary
            )
        }

        item {
            Text(
                text = "Agents",
                style = MaterialTheme.typography.titleMedium,
                color = OmniClawAccent,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(agents) { agent ->
            CapabilityCard(
                name = agent.name,
                description = agent.systemPrompt?.take(80) ?: "General-purpose agent",
                icon = Icons.Default.Memory,
                accentColor = OmniClawAccent,
                status = "Active"
            )
        }

        if (agents.isEmpty()) {
            item {
                CapabilityCard(
                    name = "Default Agent",
                    description = "General-purpose AI orchestration agent",
                    icon = Icons.Default.Memory,
                    accentColor = OmniClawAccent,
                    status = "Ready"
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Registered Tools",
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
                        status = "Available"
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "OpenCode Marketplace",
                style = MaterialTheme.typography.titleLarge,
                color = OmniClawAccent,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Download community agents and tools from the OpenCode registry",
                style = MaterialTheme.typography.bodyMedium,
                color = OmniClawTextSecondary
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        items(downloadCatalog) { entry ->
            DownloadableAgentCard(
                agent = entry.agent,
                downloadState = entry.downloadState,
                onDownload = { downloadViewModel.downloadAgent(entry.agent.id) }
            )
        }
    }
}

@Composable
private fun DownloadableAgentCard(
    agent: DownloadableAgent,
    downloadState: DownloadState,
    onDownload: () -> Unit
) {
    val shape = remember { RoundedCornerShape(14.dp) }
    val accent = categoryAccent(agent.category)

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
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(agent.category),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = accent
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = agent.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OmniClawTextPrimary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = agent.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniClawTextTertiary,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "v${agent.version}",
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniClawTextTertiary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = agent.source.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = OmniClawAccentSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            DownloadButton(downloadState = downloadState, onDownload = onDownload)
        }
    }
}

@Composable
private fun DownloadButton(
    downloadState: DownloadState,
    onDownload: () -> Unit
) {
    when (downloadState) {
        is DownloadState.Idle -> {
            Button(
                onClick = onDownload,
                colors = ButtonDefaults.buttonColors(containerColor = OmniClawAccent),
                shape = RoundedCornerShape(10.dp),
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = "Download",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Get", style = MaterialTheme.typography.labelMedium)
            }
        }
        is DownloadState.Requesting -> {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = OmniClawAccent,
                strokeWidth = 2.dp
            )
        }
        is DownloadState.Transferring -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    progress = { downloadState.progress },
                    modifier = Modifier.size(36.dp),
                    color = OmniClawAccent,
                    strokeWidth = 3.dp
                )
                Text(
                    text = "${(downloadState.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniClawAccent
                )
            }
        }
        is DownloadState.Complete -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    modifier = Modifier.size(28.dp),
                    tint = OmniClawSuccess
                )
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniClawSuccess
                )
            }
        }
        is DownloadState.Error -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = OmniClawWarning
                )
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(containerColor = OmniClawWarning),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding
                ) {
                    Text("Retry", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun categoryAccent(category: AgentCategory): androidx.compose.ui.graphics.Color = when (category) {
    AgentCategory.UTILITY -> OmniClawSuccess
    AgentCategory.AUTOMATION -> OmniClawAccent
    AgentCategory.CUSTOM_LOGIC -> androidx.compose.ui.graphics.Color(0xFF8B5CF6)
    AgentCategory.DEVELOPER -> androidx.compose.ui.graphics.Color(0xFF3B82F6)
    AgentCategory.ANALYTICS -> androidx.compose.ui.graphics.Color(0xFFF59E0B)
    AgentCategory.SECURITY -> androidx.compose.ui.graphics.Color(0xFFEF4444)
}

private fun categoryIcon(category: AgentCategory): androidx.compose.ui.graphics.vector.ImageVector = when (category) {
    AgentCategory.UTILITY -> Icons.Default.Extension
    AgentCategory.AUTOMATION -> Icons.Default.Build
    AgentCategory.CUSTOM_LOGIC -> Icons.Default.Star
    AgentCategory.DEVELOPER -> Icons.Default.Memory
    AgentCategory.ANALYTICS -> Icons.Default.Favorite
    AgentCategory.SECURITY -> Icons.Default.Security
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
