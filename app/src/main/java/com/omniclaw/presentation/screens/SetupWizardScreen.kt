package com.omniclaw.presentation.screens

import com.omniclaw.R
import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniclaw.presentation.viewmodels.SetupStep
import com.omniclaw.presentation.viewmodels.SetupViewModel
import rikka.shizuku.Shizuku

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onFinishSetup: () -> Unit,
    viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory)
) {
    val currentStep by viewModel.currentStep.collectAsState()

    val currentStepDef = SetupStep.entries[currentStep]

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.step_n_of_m, currentStep + 1, SetupStep.entries.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = stringResource(id = currentStepDef.labelResId),
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                if (currentStep > 0) {
                    TextButton(onClick = { viewModel.previousStep() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.back))
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (currentStep < SetupStep.entries.lastIndex) {
                    Button(
                        onClick = { viewModel.nextStep() },
                        enabled = viewModel.canAdvance
                    ) {
                        Text(stringResource(R.string.next))
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.next))
                    }
                } else {
                    Button(onClick = {
                        viewModel.completeSetup()
                        onFinishSetup()
                    }) {
                        Text(stringResource(R.string.finish_setup))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> width } + fadeOut()
                        }
                    }, label = "SetupWizardTransition"
                ) { step ->
                    when (step) {
                        0 -> WelcomeStep()
                        1 -> ThemeSelectionStep(viewModel)
                        2 -> AgentSelectionStep(viewModel)
                        3 -> ProviderSelectionStep(viewModel)
                        4 -> ShizukuStep(viewModel)
                        5 -> SummaryStep()
                    }
                }
            }
            PageIndicator(
                totalSteps = SetupStep.entries.size,
                currentStep = currentStep
            )
        }
    }
}

@Composable
fun WelcomeStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.omniclaw_ai_title), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.omniclaw_ai_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ThemeSelectionStep(viewModel: SetupViewModel) {
    val theme by viewModel.theme.collectAsState()
    val themes = listOf("System", "Dark", "Light")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.appearance), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        
        Column(Modifier.selectableGroup()) {
            themes.forEach { text ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .selectable(
                            selected = (text == theme),
                            onClick = { viewModel.setTheme(text) },
                            role = Role.RadioButton
                        )
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = (text == theme),
                        onClick = null 
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AgentSelectionStep(viewModel: SetupViewModel) {
    val selectedAgent by viewModel.selectedAgent.collectAsState()
    val agents = listOf("Hermes", "OpenClaude", "Claude Code")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.select_agent), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(Modifier.selectableGroup()) {
            agents.forEach { agent ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .selectable(
                            selected = (agent == selectedAgent),
                            onClick = { viewModel.setSelectedAgent(agent) }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (agent == selectedAgent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(agent == selectedAgent)
                ) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (agent == selectedAgent),
                            onClick = null
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(text = agent, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            val desc = when (agent) {
                                "Hermes" -> stringResource(R.string.agent_hermes)
                                "OpenClaude" -> stringResource(R.string.agent_openclaude)
                                "Claude Code" -> stringResource(R.string.agent_claude_code)
                                else -> stringResource(R.string.agent_default)
                            }
                            Text(text = desc, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProviderSelectionStep(viewModel: SetupViewModel) {
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val agent by viewModel.selectedAgent.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val isTesting by viewModel.isTestingConnection.collectAsState()
    val success by viewModel.testConnectionSuccess.collectAsState()
    val providers = listOf("Claude", "OpenRouter", "OpenAI", "Gemini")
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.select_provider_for, agent), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Column(Modifier.selectableGroup()) {
            providers.forEach { provider ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .selectable(
                            selected = (provider == selectedProvider),
                            onClick = { viewModel.setSelectedProvider(provider) }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (provider == selectedProvider) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ),
                    border = CardDefaults.outlinedCardBorder(provider == selectedProvider)
                ) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = (provider == selectedProvider),
                            onClick = null
                        )
                        Column(modifier = Modifier.padding(start = 16.dp)) {
                            Text(text = provider, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        Text(stringResource(R.string.api_key), style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = apiKey,
            onValueChange = { viewModel.setApiKey(it) },
            label = { Text(stringResource(R.string.api_key)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = { viewModel.testConnection() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            if (isTesting) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.test_connection))
            }
        }
        
        if (success != null) {
            Spacer(modifier = Modifier.height(8.dp))
            if (success == true) {
                Text(stringResource(R.string.connection_successful), color = MaterialTheme.colorScheme.primary)
            } else {
                Text(stringResource(R.string.connection_failed), color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun ShizukuStep(viewModel: SetupViewModel) {
    val shizukuEnabled by viewModel.shizukuEnabled.collectAsState()
    val context = LocalContext.current
    var shizukuStatus by remember { mutableStateOf(context.getString(R.string.shizuku_status_checking)) }
    var hasPermission by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val isInstalled = try {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
        
        if (isInstalled) {
            if (Shizuku.pingBinder()) {
                hasPermission = Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                shizukuStatus = if (hasPermission) context.getString(R.string.shizuku_status_running) else context.getString(R.string.shizuku_status_no_permission)
            } else {
                shizukuStatus = context.getString(R.string.shizuku_status_not_running)
            }
        } else {
            shizukuStatus = context.getString(R.string.shizuku_status_not_installed)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.elevated_permissions), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.shizuku_description),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.shizuku_status_format, shizukuStatus), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                if (!hasPermission && Shizuku.pingBinder()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        try {
                            Shizuku.requestPermission(1000)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }) {
                        Text(stringResource(R.string.request_permission))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.enable_shizuku), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.shizuku_required), style = MaterialTheme.typography.labelMedium)
                }
                Switch(
                    checked = shizukuEnabled,
                    onCheckedChange = { viewModel.setShizukuEnabled(it) },
                    enabled = hasPermission
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(totalSteps: Int, currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep
            val color = when {
                isActive -> MaterialTheme.colorScheme.primary
                isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val size = if (isActive) 10.dp else 8.dp
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(size)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

@Composable
fun SummaryStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(stringResource(R.string.step_summary), style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
                Text(
                    stringResource(R.string.runtime_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(stringResource(R.string.runtime_active), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(modifier = Modifier.height(4.dp))
                Text(stringResource(R.string.runtime_ready), style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.summary), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.summary_ui_configured), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.summary_agent_selected), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.summary_api_keys_active), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.summary_command_ready), style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text(stringResource(R.string.ready_enter_workspace), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
    }
}
