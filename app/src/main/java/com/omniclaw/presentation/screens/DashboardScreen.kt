package com.omniclaw.presentation.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniclaw.R
import com.omniclaw.presentation.screens.dashboard.AgentCardsGrid
import com.omniclaw.presentation.screens.dashboard.CodexSection
import com.omniclaw.presentation.screens.dashboard.QuickActionsBar
import com.omniclaw.presentation.viewmodels.DashboardViewModel
import rikka.shizuku.Shizuku
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToSession: (String) -> Unit,
    onNavigateToNewSession: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToTermux: () -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)
) {
    val sessions by viewModel.sessions.collectAsState()
    val agents by viewModel.agents.collectAsState()
    val termuxLogs by viewModel.termuxLogs.collectAsState()
    val activeAgent by viewModel.activeAgent.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val shizukuEnabled by viewModel.shizukuEnabled.collectAsState()
    val activeSessionToolCalls by viewModel.activeSessionToolCalls.collectAsState()
    val gitDiffResult by viewModel.gitDiffResult.collectAsState()

    val context = LocalContext.current
    var isShizukuActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val isInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        if (isInstalled && Shizuku.pingBinder()) {
            isShizukuActive = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.omniclaw_workspace), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToTermux) {
                        Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.local_tools))
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToNewSession) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_session))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.agent), style = MaterialTheme.typography.labelMedium)
                            Text(activeAgent ?: "Hermes", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(activeProvider ?: stringResource(R.string.unknown), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = if (isShizukuActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.shizuku_status), style = MaterialTheme.typography.labelMedium)
                            Text(if (isShizukuActive) stringResource(R.string.active) else stringResource(R.string.unavailable), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            item {
                QuickActionsBar(
                    onNewSession = onNavigateToNewSession,
                    onOpenTerminal = onNavigateToTermux,
                    onOpenSettings = onNavigateToSettings
                )
            }

            if (agents.isNotEmpty()) {
                item {
                    Text(
                        text = "AI Agents",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                item {
                    AgentCardsGrid(
                        agents = agents,
                        activeAgentName = activeAgent,
                        onAgentSelected = { }
                    )
                }
            }

            item {
                CodexSection(
                    toolCallRecords = activeSessionToolCalls,
                    termuxLogs = termuxLogs,
                    gitDiffResult = gitDiffResult
                )
            }

            item {
                Text(
                    text = stringResource(R.string.recent_sessions),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
            }

            if (sessions.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.no_sessions), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                items(sessions) { session ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigateToSession(session.id) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(text = session.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(4.dp))
                            val dateStr = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(session.updatedAt))
                            Text(text = stringResource(R.string.updated_format, dateStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
