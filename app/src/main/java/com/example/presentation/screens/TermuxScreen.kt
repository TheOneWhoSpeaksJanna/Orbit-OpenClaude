package com.example.presentation.screens

import com.example.R
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.presentation.viewmodels.TermuxViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxScreen(
    onNavigateBack: () -> Unit,
    viewModel: TermuxViewModel = viewModel(factory = TermuxViewModel.Factory)
) {
    val logs by viewModel.logs.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()
    var commandText by remember { mutableStateOf("") }
    var showConfirmation by remember { mutableStateOf(false) }
    var executeAsShizuku by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_workspace), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Quick Installs Section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("git", "python", "nodejs", "curl", "wget").forEach { tool ->
                    SuggestionChip(
                        onClick = { viewModel.installTool(tool) },
                        label = { Text(stringResource(R.string.install_tool, tool), fontSize = 12.sp) },
                        icon = { Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }

            // Progress Bar
            if (progress != null && progress!!.isActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(progress!!.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("${(progress!!.progress * 100).toInt()}%", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress!!.progress },
                            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.speed_format, progress!!.mbPerSecond), style = MaterialTheme.typography.bodySmall)
                            Text(stringResource(R.string.time_remaining, progress!!.timeRemainingSeconds), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(12.dp),
                reverseLayout = true
            ) {
                items(logs) { log ->
                    Column(modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth()) {
                        Text(
                            text = "orbit> ${log.command}",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = log.output,
                            color = if (log.exitCode == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                    }
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandText,
                        onValueChange = { commandText = it },
                        label = { Text(stringResource(R.string.type_shell_command)) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { 
                            if (commandText.isNotBlank()) {
                                executeAsShizuku = true
                                showConfirmation = true 
                            }
                        },
                        modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp))
                    ) {
                        Text(stringResource(R.string.sudo), color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = { 
                            if (commandText.isNotBlank()) {
                                executeAsShizuku = false
                                showConfirmation = true 
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.execute))
                    }
                }
            }
        }
    }

    if (showConfirmation) {
        val isPrivileged = executeAsShizuku
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text(if (isPrivileged) stringResource(R.string.execute_privileged) else stringResource(R.string.execute_normal)) },
            text = { 
                Text(
                    stringResource(R.string.confirm_execute, commandText) + "\n\n" + 
                    (if (isPrivileged) stringResource(R.string.warning_privileged) else stringResource(R.string.warning_normal))
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isPrivileged) {
                            viewModel.executePrivilegedCommand(commandText)
                        } else {
                            viewModel.executeCommand(commandText)
                        }
                        commandText = ""
                        showConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPrivileged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(stringResource(R.string.execute))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
