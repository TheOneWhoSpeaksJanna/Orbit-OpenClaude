package com.omniclaw.ui.screens

import android.content.pm.PackageManager
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.omniclaw.R
import com.omniclaw.ui.components.AnimatedGlassCard
import com.omniclaw.ui.viewmodels.SetupStep
import com.omniclaw.ui.viewmodels.SetupViewModel
import com.omniclaw.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SetupWizardScreen(
    onFinishSetup: () -> Unit,
    viewModel: SetupViewModel = viewModel(factory = SetupViewModel.Factory)
) {
    val currentStep by viewModel.currentStep.collectAsState()
    val currentStepDef = SetupStep.entries[currentStep]
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = OmniClawObsidianBase,
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
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = OmniClawObsidianBase
                )
            )
        },
        bottomBar = {
            BottomAppBar(
                containerColor = OmniClawObsidianSurface,
                tonalElevation = 0.dp
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
                        scope.launch {
                            delay(400)
                            viewModel.finishOnboarding()
                            onFinishSetup()
                        }
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
                .background(OmniClawObsidianBase)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        val slideDir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(
                            animationSpec = tween(400),
                            initialOffsetX = { fullWidth -> slideDir * fullWidth }
                        ) + fadeIn(animationSpec = tween(300)))
                            .togetherWith(
                                slideOutHorizontally(
                                    animationSpec = tween(400),
                                    targetOffsetX = { fullWidth -> -slideDir * fullWidth }
                                ) + fadeOut(animationSpec = tween(300))
                            )
                    },
                    label = "SetupWizardTransition"
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
            GlassPageIndicator(
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
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.omniclaw_ai_title),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            stringResource(R.string.omniclaw_ai_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = OmniClawTextSecondary
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
        Text(
            stringResource(R.string.appearance),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        themes.forEach { text ->
            val isSelected = text == theme
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedGlassCard(
                onClick = { viewModel.setTheme(text) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
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
        Text(
            stringResource(R.string.select_agent),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        agents.forEach { agent ->
            val isSelected = agent == selectedAgent
            Spacer(modifier = Modifier.height(12.dp))
            AnimatedGlassCard(
                onClick = { viewModel.setSelectedAgent(agent) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = agent,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val desc = when (agent) {
                        "Hermes" -> stringResource(R.string.agent_hermes)
                        "OpenClaude" -> stringResource(R.string.agent_openclaude)
                        "Claude Code" -> stringResource(R.string.agent_claude_code)
                        else -> stringResource(R.string.agent_default)
                    }
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = OmniClawTextSecondary
                    )
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
        Text(
            stringResource(R.string.select_provider_for, agent),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        providers.forEach { provider ->
            val isSelected = provider == selectedProvider
            Spacer(modifier = Modifier.height(10.dp))
            AnimatedGlassCard(
                onClick = { viewModel.setSelectedProvider(provider) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = provider,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text(
            stringResource(R.string.api_key),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = apiKey,
            onValueChange = { viewModel.setApiKey(it) },
            label = { Text(stringResource(R.string.api_key)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = OmniClawGlassBorder
            )
        )
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { viewModel.testConnection() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isTesting
        ) {
            if (isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(stringResource(R.string.test_connection))
            }
        }

        if (success != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (success == true) stringResource(R.string.connection_successful)
                else stringResource(R.string.connection_failed),
                color = if (success == true) OmniClawSuccess else OmniClawError,
                style = MaterialTheme.typography.bodyMedium
            )
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
                shizukuStatus = if (hasPermission) context.getString(R.string.shizuku_status_running)
                else context.getString(R.string.shizuku_status_no_permission)
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
        Text(
            stringResource(R.string.elevated_permissions),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.shizuku_description),
            style = MaterialTheme.typography.bodyMedium,
            color = OmniClawTextSecondary
        )
        Spacer(modifier = Modifier.height(16.dp))

        AnimatedGlassCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.shizuku_status_format, shizukuStatus),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (!hasPermission && Shizuku.pingBinder()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        try { Shizuku.requestPermission(1000) }
                        catch (e: Exception) { e.printStackTrace() }
                    }) {
                        Text(stringResource(R.string.request_permission))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedGlassCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.enable_shizuku),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        stringResource(R.string.shizuku_required),
                        style = MaterialTheme.typography.labelMedium,
                        color = OmniClawTextSecondary
                    )
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
fun SummaryStep() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            stringResource(R.string.step_summary),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            stringResource(R.string.runtime_description),
            style = MaterialTheme.typography.bodyMedium,
            color = OmniClawTextSecondary
        )
        Spacer(modifier = Modifier.height(24.dp))

        AnimatedGlassCard(
            onClick = {},
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    stringResource(R.string.runtime_active),
                    fontWeight = FontWeight.Bold,
                    color = OmniClawSuccess
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    stringResource(R.string.runtime_ready),
                    style = MaterialTheme.typography.bodySmall,
                    color = OmniClawTextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text(
            stringResource(R.string.summary),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.summary_ui_configured),
                style = MaterialTheme.typography.bodyMedium,
                color = OmniClawTextSecondary
            )
            Text(
                stringResource(R.string.summary_agent_selected),
                style = MaterialTheme.typography.bodyMedium,
                color = OmniClawTextSecondary
            )
            Text(
                stringResource(R.string.summary_api_keys_active),
                style = MaterialTheme.typography.bodyMedium,
                color = OmniClawTextSecondary
            )
            Text(
                stringResource(R.string.summary_command_ready),
                style = MaterialTheme.typography.bodyMedium,
                color = OmniClawTextSecondary
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            stringResource(R.string.ready_enter_workspace),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun GlassPageIndicator(totalSteps: Int, currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OmniClawObsidianBase)
            .padding(vertical = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(totalSteps) { index ->
            val isActive = index == currentStep
            val isCompleted = index < currentStep

            // Animated dot
            val animatedSize by animateFloatAsState(
                targetValue = if (isActive) 12f else 8f,
                animationSpec = spring(dampingRatio = 0.5f),
                label = "DotSize"
            )
            val animatedAlpha by animateFloatAsState(
                targetValue = when {
                    isActive -> 1f
                    isCompleted -> 0.6f
                    else -> 0.25f
                },
                animationSpec = tween(300),
                label = "DotAlpha"
            )

            val dotColor = when {
                isActive -> MaterialTheme.colorScheme.primary
                isCompleted -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                else -> OmniClawGlassBorder
            }

            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(animatedSize.dp)
                    .graphicsLayer { alpha = animatedAlpha }
                    .clip(CircleShape)
                    .background(dotColor)
            )
        }
    }
}
