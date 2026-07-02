package com.omniclaw.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniclaw.ui.viewmodels.TermuxViewModel
import rikka.shizuku.Shizuku

private const val TITLE = "App Workspace & Execution"
private const val CD_BACK = "Back"
private const val CD_EXECUTE = "Execute"
private const val CD_COPY = "Copy"
private const val INPUT_LABEL = "Type a shell command..."
private const val SUDO_LABEL = "Sudo"
private const val CONFIRM_TITLE = "Execute Command?"
private const val CONFIRM_TITLE_PRIVILEGED = "Execute Privileged Command?"
private const val CONFIRM_NATIVE = "Execution happens directly on your device natively."
private const val CONFIRM_PRIVILEGED = "WARNING: This will execute via Shizuku with elevated privileges. Destructive actions can occur."
private const val CANCEL = "Cancel"
private const val COPIED_TOAST = "Copied to clipboard"
private const val MBPS_FORMAT = "%.1f MB/s"
private const val SECONDS_REMAINING_FORMAT = "%ds remaining"

private val CARD_BG = Color(0xFF0F172A)
private val CMD_COLOR = Color(0xFF00F2FE)
private val SUCCESS_TEXT = Color(0xFFE2E8F0)
private val ERROR_TEXT = Color(0xFFFCA5A5)
private val DIVIDER_COLOR = Color(0xFF1E293B)

private val QUICK_TOOLS = listOf("git", "python", "nodejs", "curl", "wget")

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

    val context = LocalContext.current
    var isShizukuActive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val isInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        isShizukuActive = isInstalled
                && Shizuku.pingBinder()
                && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(TITLE, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = CD_BACK)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUICK_TOOLS.forEach { tool ->
                    SuggestionChip(
                        onClick = { viewModel.installTool(tool) },
                        label = { Text("Install $tool", fontSize = 12.sp) },
                        icon = {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            if (progress != null && progress!!.isActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                progress!!.title,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${(progress!!.progress * 100).toInt()}%",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progress!!.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                String.format(MBPS_FORMAT, progress!!.mbPerSecond),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                SECONDS_REMAINING_FORMAT.format(progress!!.timeRemainingSeconds),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // Reverse once per logs change, not once per recomposition.
            // The previous version called logs.reversed() inline in the items()
            // call, which allocated a fresh ArrayList on every recomposition —
            // including every keystroke into the command input above.
            val reversedLogs = remember(logs) { logs.asReversed() }
            val clipboardManager = LocalClipboardManager.current
            val ctx = LocalContext.current

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                reverseLayout = true
            ) {
                items(reversedLogs, key = { it.id }) { log ->
                    TerminalLogCard(
                        command = log.command,
                        output = log.output,
                        exitCode = log.exitCode,
                        onCopy = {
                            // Copy the full command + output block so users can
                            // paste it into bug reports or share it elsewhere.
                            val text = "$ ${log.command}\n${log.output}"
                            clipboardManager.setText(AnnotatedString(text))
                            android.widget.Toast.makeText(ctx, COPIED_TOAST, android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
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
                        label = { Text(INPUT_LABEL) },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        shape = RoundedCornerShape(8.dp)
                    )

                    if (isShizukuActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (commandText.isNotBlank()) {
                                    executeAsShizuku = true
                                    showConfirmation = true
                                }
                            },
                            modifier = Modifier.background(
                                MaterialTheme.colorScheme.errorContainer,
                                RoundedCornerShape(8.dp)
                            )
                        ) {
                            Text(
                                SUDO_LABEL,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                        Icon(Icons.Default.PlayArrow, contentDescription = CD_EXECUTE)
                    }
                }
            }
        }
    }

    if (showConfirmation) {
        val isPrivileged = executeAsShizuku
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = {
                Text(if (isPrivileged) CONFIRM_TITLE_PRIVILEGED else CONFIRM_TITLE)
            },
            text = {
                Text(
                    "Are you sure you want to run this command locally?\n\n$ $commandText\n\n" +
                            if (isPrivileged) CONFIRM_PRIVILEGED else CONFIRM_NATIVE
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
                        containerColor = if (isPrivileged)
                            MaterialTheme.colorScheme.error
                        else
                            MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(CD_EXECUTE)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text(CANCEL)
                }
            }
        )
    }
}

/**
 * One terminal log entry. Shows the command, output, and a copy button.
 *
 * Copy is exposed two ways so it's discoverable for both touch users and
 * users who are used to long-press-to-copy from terminal apps:
 *  1. An explicit copy icon button in the top-right corner of each card.
 *  2. Long-press anywhere on the card body.
 *
 * Both paths copy "$ <command>\n<output>" to the clipboard and show a Toast.
 *
 * Performance: the Card's colors are read outside the combinedClickable lambda
 * so they don't trigger recomposition. The output Text uses `maxLines = 50`
 * to prevent a single huge log from blowing out the layout — users can still
 * copy the full output via the copy button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TerminalLogCard(
    command: String,
    output: String,
    exitCode: Int,
    onCopy: () -> Unit
) {
    val isSuccess = exitCode == 0
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(
                onClick = {},           // no-op tap; the card is informational
                onLongClick = onCopy    // long-press to copy
            ),
        colors = CardDefaults.cardColors(containerColor = CARD_BG),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header row: command on the left, copy button on the right.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "orbit> $command",
                    color = CMD_COLOR,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = CD_COPY,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = output,
                color = if (isSuccess) SUCCESS_TEXT else ERROR_TEXT,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 50,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = DIVIDER_COLOR, thickness = 1.dp)
        }
    }
}
