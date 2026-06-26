package com.omniclaw.ui.screens

import com.omniclaw.R
import com.omniclaw.data.local.updater.UpdateState
import com.omniclaw.data.local.updater.UpdateInfo
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniclaw.ui.viewmodels.SettingsViewModel
import com.omniclaw.domain.models.AgentPermissionLevel
import com.omniclaw.domain.models.Skill

private const val THEME_SYSTEM = "System"
private const val THEME_DARK = "Dark"
private const val THEME_LIGHT = "Light"
private const val SECTION_UPDATES = "Updates"
private const val VERSION_PREFIX = "Version "
private const val BTN_CHECK_UPDATES = "Check for Updates"
private const val BTN_CHECKING = "Checking for updates..."
private const val BTN_DOWNLOAD = "Download Update"
private const val BTN_CHECK_AGAIN = "Check Again"
private const val BTN_INSTALL = "Install Update"
private const val BTN_RETRY = "Retry"
private const val MSG_UP_TO_DATE = "You're up to date!"
private const val MSG_DOWNLOAD_COMPLETE = "Download complete!"
private const val MSG_DOWNLOADING = "Downloading... "
private const val SECTION_PERMISSION = "Agent Permissions"
private const val PERMISSION_LABEL = "Permission Level"
private const val RULES_LABEL = "Agent Rules"
private const val RULES_ALLOWED_LABEL = "Allowed"
private const val RULES_ASK_LABEL = "Ask"
private const val RULES_DENIED_LABEL = "Not Allowed"
private const val RULES_PLACEHOLDER = "One rule per line..."
private const val NORMAL_DESC = "Always asks before taking actions"
private const val RULES_DESC = "Follows defined rules"
private const val FULL_ACCESS_DESC = "Never asks, full autonomy"
private const val SECTION_SKILLS = "Skills"
private const val EDIT_SKILL = "Edit"
private const val SAVE = "Save"
private const val CANCEL = "Cancel"
private const val SKILL_CONTENT_HINT = "Enter skill instructions..."
private const val ENABLED = "Enabled"
private const val DISABLED = "Disabled"

private val THEME_OPTIONS = listOf(THEME_SYSTEM, THEME_DARK, THEME_LIGHT)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val agentPermissionLevel by viewModel.agentPermissionLevel.collectAsState()
    val agentRulesAllowed by viewModel.agentRulesAllowed.collectAsState()
    val agentRulesAsk by viewModel.agentRulesAsk.collectAsState()
    val agentRulesDenied by viewModel.agentRulesDenied.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val skills by viewModel.skills.collectAsState()
    val appVersion = viewModel.appVersion

    var editSkill by remember { mutableStateOf<Skill?>(null) }
    var editContent by remember { mutableStateOf("") }
    var expandedSkillId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings), fontWeight = FontWeight.Bold) },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(stringResource(R.string.appearance), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    var expanded by remember { mutableStateOf(false) }
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        OutlinedTextField(
                            value = themeMode,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.theme_mode)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            THEME_OPTIONS.forEach { selection ->
                                DropdownMenuItem(
                                    text = { Text(selection) },
                                    onClick = {
                                        viewModel.updateThemeMode(selection)
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(SECTION_PERMISSION, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))

                    var levelExpanded by remember { mutableStateOf(false) }
                    val permissionOptions = AgentPermissionLevel.entries.toList()
                    val currentLevel = AgentPermissionLevel.fromValue(agentPermissionLevel)

                    ExposedDropdownMenuBox(
                        expanded = levelExpanded,
                        onExpandedChange = { levelExpanded = !levelExpanded }
                    ) {
                        OutlinedTextField(
                            value = currentLevel.label,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text(PERMISSION_LABEL) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        )
                        ExposedDropdownMenu(
                            expanded = levelExpanded,
                            onDismissRequest = { levelExpanded = false }
                        ) {
                            permissionOptions.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = {
                                        viewModel.updateAgentPermissionLevel(option.name)
                                        levelExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    val desc = when (currentLevel) {
                        AgentPermissionLevel.NORMAL -> NORMAL_DESC
                        AgentPermissionLevel.RULES -> RULES_DESC
                        AgentPermissionLevel.FULL_ACCESS -> FULL_ACCESS_DESC
                    }
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    if (currentLevel == AgentPermissionLevel.RULES) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = agentRulesAllowed,
                            onValueChange = { viewModel.updateAgentRulesAllowed(it) },
                            label = { Text(RULES_ALLOWED_LABEL) },
                            placeholder = { Text(RULES_PLACEHOLDER) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            shape = MaterialTheme.shapes.medium,
                            maxLines = 6
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = agentRulesAsk,
                            onValueChange = { viewModel.updateAgentRulesAsk(it) },
                            label = { Text(RULES_ASK_LABEL) },
                            placeholder = { Text(RULES_PLACEHOLDER) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            shape = MaterialTheme.shapes.medium,
                            maxLines = 6
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = agentRulesDenied,
                            onValueChange = { viewModel.updateAgentRulesDenied(it) },
                            label = { Text(RULES_DENIED_LABEL) },
                            placeholder = { Text(RULES_PLACEHOLDER) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
                            shape = MaterialTheme.shapes.medium,
                            maxLines = 6
                        )
                    }
                }
            }

            // Skills Card
            if (skills.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.extraLarge
                ) {
                    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                        Text(SECTION_SKILLS, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))

                        skills.forEach { skill ->
                            SkillCard(
                                skill = skill,
                                isExpanded = expandedSkillId == skill.id,
                                onToggleExpanded = {
                                    expandedSkillId = if (expandedSkillId == skill.id) null else skill.id
                                },
                                onToggleEnabled = { enabled ->
                                    viewModel.toggleSkillEnabled(skill.id, enabled)
                                },
                                onEdit = {
                                    editSkill = skill
                                    editContent = skill.content
                                }
                            )
                            if (skill != skills.last()) {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
                    Text(SECTION_UPDATES, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "$VERSION_PREFIX$appVersion",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    when (val state = updateState) {
                        is UpdateState.Idle -> {
                            Button(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(BTN_CHECK_UPDATES)
                            }
                        }
                        is UpdateState.Checking -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(12.dp))
                                Text(BTN_CHECKING, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        is UpdateState.Available -> {
                            Text(
                                "Update v${state.info.latestVersion} available",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (state.info.releaseNotes.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    state.info.releaseNotes.take(200),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.downloadUpdate(state.info) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(BTN_DOWNLOAD)
                            }
                        }
                        is UpdateState.UpToDate -> {
                            Text(
                                MSG_UP_TO_DATE,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(BTN_CHECK_AGAIN)
                            }
                        }
                        is UpdateState.Downloading -> {
                            LinearProgressIndicator(
                                progress = { state.progress },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(MaterialTheme.shapes.small),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "$MSG_DOWNLOADING${(state.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is UpdateState.Downloaded -> {
                            Text(
                                MSG_DOWNLOAD_COMPLETE,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.installUpdate(state.filePath) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(BTN_INSTALL)
                            }
                        }
                        is UpdateState.Failed -> {
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { viewModel.checkForUpdates() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Text(BTN_RETRY)
                            }
                        }
                    }
                }
            }
        }

        // Skill edit dialog
        if (editSkill != null) {
            AlertDialog(
                onDismissRequest = { editSkill = null },
                title = { Text("Edit: ${editSkill?.name}") },
                text = {
                    Column {
                        Text("Skill content defines what the AI knows about this capability.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = editContent,
                            onValueChange = { editContent = it },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp),
                            shape = MaterialTheme.shapes.medium,
                            placeholder = { Text(SKILL_CONTENT_HINT) },
                            maxLines = 30
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        editSkill?.let { viewModel.updateSkillContent(it.id, editContent) }
                        editSkill = null
                    }) { Text(SAVE) }
                },
                dismissButton = {
                    TextButton(onClick = { editSkill = null }) { Text(CANCEL) }
                }
            )
        }
    }
}

@Composable
private fun SkillCard(
    skill: Skill,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(skill.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                    Text(
                        if (skill.enabled) ENABLED else DISABLED,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (skill.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = skill.enabled,
                    onCheckedChange = onToggleEnabled
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onEdit) { Text(EDIT_SKILL) }
                TextButton(onClick = onToggleExpanded) {
                    Text(if (isExpanded) "Hide Content" else "View Content")
                }
            }
            if (isExpanded) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = skill.content,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
    }
}
