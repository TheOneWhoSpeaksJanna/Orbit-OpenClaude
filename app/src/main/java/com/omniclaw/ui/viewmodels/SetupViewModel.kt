package com.omniclaw.ui.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.R
import com.omniclaw.data.local.prefs.PreferencesManager
import com.omniclaw.data.local.runner.LocalCommandRunner
import com.omniclaw.data.local.runtime.OmniClawRuntimeManager
import com.omniclaw.domain.models.Agent
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.repository.OmniClawRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class SetupStep(@StringRes val labelResId: Int) {
    Welcome(R.string.step_welcome),
    Theme(R.string.step_theme),
    Agent(R.string.step_agent),
    Provider(R.string.step_provider),
    Shizuku(R.string.step_shizuku),
    Summary(R.string.step_summary);
}

class SetupViewModel(
    private val prefsManager: PreferencesManager,
    private val repository: OmniClawRepository,
    private val aiProvider: AiProvider,
    private val localCommandRunner: LocalCommandRunner,
    private val runtimeManager: OmniClawRuntimeManager
) : ViewModel() {

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _theme = MutableStateFlow("System")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _shizukuEnabled = MutableStateFlow(false)
    val shizukuEnabled: StateFlow<Boolean> = _shizukuEnabled.asStateFlow()

    private val _selectedAgent = MutableStateFlow("Hermes")
    val selectedAgent: StateFlow<String> = _selectedAgent.asStateFlow()

    private val _selectedProvider = MutableStateFlow("Gemini")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testConnectionSuccess = MutableStateFlow<Boolean?>(null)
    val testConnectionSuccess: StateFlow<Boolean?> = _testConnectionSuccess.asStateFlow()

    // Per-agent install state
    data class AgentInstallState(
        val isInstalling: Boolean = false,
        val progress: Float = 0f,
        val status: String = "",
        val isInstalled: Boolean = false
    )

    private val _agentInstallStates = MutableStateFlow(
        mapOf(
            "Hermes" to AgentInstallState(),
            "OpenClaude" to AgentInstallState(),
            "Claude Code" to AgentInstallState()
        )
    )
    val agentInstallStates: StateFlow<Map<String, AgentInstallState>> = _agentInstallStates.asStateFlow()

    val canAdvance: Boolean
        get() {
            val step = SetupStep.entries[_currentStep.value]
            return when (step) {
                SetupStep.Welcome -> true
                SetupStep.Theme -> _theme.value.isNotBlank()
                SetupStep.Agent -> _selectedAgent.value.isNotBlank()
                SetupStep.Provider -> _apiKey.value.isNotBlank()
                SetupStep.Shizuku -> true
                SetupStep.Summary -> true
            }
        }

    fun nextStep() { _currentStep.value += 1 }

    fun previousStep() {
        if (_currentStep.value > 0) _currentStep.value -= 1
    }

    fun setTheme(mode: String) {
        _theme.value = mode
        viewModelScope.launch { prefsManager.setThemeMode(mode) }
    }
    fun setShizukuEnabled(enabled: Boolean) { _shizukuEnabled.value = enabled }
    fun setSelectedAgent(agent: String) { _selectedAgent.value = agent }
    fun setSelectedProvider(provider: String) { _selectedProvider.value = provider }
    fun setSelectedModel(model: String) { _selectedModel.value = model }
    fun setApiKey(key: String) { _apiKey.value = key }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testConnectionSuccess.value = null

            val model = _selectedModel.value.ifBlank {
                aiProvider.getModels(_selectedProvider.value).firstOrNull() ?: ""
            }

            val success = aiProvider.testConnection(
                provider = _selectedProvider.value,
                apiKey = _apiKey.value,
                model = model
            )

            _testConnectionSuccess.value = success
            _isTestingConnection.value = false
        }
    }

    fun installOpenClaude() = installAgent("OpenClaude")
    fun installHermes() = installAgent("Hermes")
    fun installClaudeCode() = installAgent("Claude Code")

    fun installAgent(agentName: String) {
        val repoUrl = "https://github.com/Gitlawb/openclaude.git"
        val targetDirName = when (agentName) {
            "OpenClaude" -> "openclaude"
            "Hermes" -> "hermes"
            "Claude Code" -> "claude_code"
            else -> agentName.lowercase().replace(" ", "_")
        }
        val targetDir = File(runtimeManager.agentsDir, targetDirName)
        val binDir = runtimeManager.binDir
        val wrapperName = when (agentName) {
            "Claude Code" -> "claude-code"
            else -> agentName.lowercase()
        }
        val wrapperFile = File(binDir, wrapperName)

        viewModelScope.launch {
            _agentInstallStates.value = _agentInstallStates.value + (agentName to AgentInstallState(
                isInstalling = true,
                progress = 0f,
                status = "Starting installation...",
                isInstalled = false
            ))

            try {
                // Step 1: Check prerequisites
                updateInstallState(agentName, status = "Checking prerequisites...")
                withContext(Dispatchers.IO) {
                    localCommandRunner.executeCommandStreamed(
                        "command -v node || echo MISSING_NODE",
                        onOutput = { }
                    )
                    localCommandRunner.executeCommandStreamed(
                        "command -v npm || echo MISSING_NPM",
                        onOutput = { }
                    )
                }

                // Step 2: Clone the repository
                updateInstallState(agentName, progress = 0.1f, status = "Cloning $agentName repository...")

                if (targetDir.exists()) {
                    targetDir.deleteRecursively()
                }

                val cloneOutput = StringBuilder()
                val cloneResult = withContext(Dispatchers.IO) {
                    localCommandRunner.executeCommandStreamed(
                        "git clone --depth 1 $repoUrl ${targetDir.absolutePath} 2>&1",
                        onOutput = { line ->
                            cloneOutput.appendLine(line)
                            updateInstallState(agentName, status = line.take(80))
                        }
                    )
                }

                if (cloneResult.exitCode != 0) {
                    throw Exception("Git clone failed: ${cloneOutput.toString().take(200)}")
                }

                updateInstallState(agentName, progress = 0.4f, status = "Installing dependencies...")

                // Step 3: Install npm dependencies
                val npmOutput = StringBuilder()
                val npmResult = withContext(Dispatchers.IO) {
                    localCommandRunner.executeCommandStreamed(
                        "cd ${targetDir.absolutePath} && npm install 2>&1",
                        onOutput = { line ->
                            npmOutput.appendLine(line)
                            if (line.contains("added", ignoreCase = true) || line.contains("saved", ignoreCase = true)) {
                                updateInstallState(agentName, status = line.take(80))
                            }
                        }
                    )
                }

                if (npmResult.exitCode != 0) {
                    throw Exception("npm install failed: ${npmOutput.toString().take(200)}")
                }

                updateInstallState(agentName, progress = 0.7f, status = "Building $agentName...")

                // Step 4: Build
                val buildOutput = StringBuilder()
                val buildResult = withContext(Dispatchers.IO) {
                    localCommandRunner.executeCommandStreamed(
                        "cd ${targetDir.absolutePath} && npm run build 2>&1",
                        onOutput = { line ->
                            buildOutput.appendLine(line)
                            if (line.contains("success", ignoreCase = true) || line.contains("built", ignoreCase = true)) {
                                updateInstallState(agentName, status = line.take(80))
                            }
                        }
                    )
                }

                if (buildResult.exitCode != 0) {
                    throw Exception("Build failed: ${buildOutput.toString().take(200)}")
                }

                updateInstallState(agentName, progress = 0.9f, status = "Creating run script...")

                // Step 5: Create wrapper script
                val wrapperScript = """
                    #!/data/data/com.termux/files/usr/bin/bash
                    exec node ${targetDir.absolutePath}/dist/cli.js "${'$'}@"
                """.trimIndent()

                withContext(Dispatchers.IO) {
                    wrapperFile.parentFile?.mkdirs()
                    wrapperFile.writeText(wrapperScript)
                    wrapperFile.setExecutable(true)
                }

                updateInstallState(agentName, progress = 1f, status = "$agentName installed successfully!", isInstalled = true)

            } catch (e: Exception) {
                updateInstallState(agentName, status = "Installation failed: ${e.message}", isInstalled = false)
            } finally {
                _agentInstallStates.value = _agentInstallStates.value + (agentName to _agentInstallStates.value[agentName]!!.copy(isInstalling = false))
            }
        }
    }

    private fun updateInstallState(agentName: String, progress: Float? = null, status: String? = null, isInstalled: Boolean? = null) {
        val current = _agentInstallStates.value[agentName] ?: AgentInstallState()
        _agentInstallStates.value = _agentInstallStates.value + (agentName to current.copy(
            progress = progress ?: current.progress,
            status = status ?: current.status,
            isInstalled = isInstalled ?: current.isInstalled
        ))
    }

    fun completeSetup() {
        viewModelScope.launch {
            prefsManager.setThemeMode(_theme.value)
            prefsManager.setShizukuEnabled(_shizukuEnabled.value)
            prefsManager.setSelectedAgent(_selectedAgent.value)
            prefsManager.setSelectedProvider(_selectedProvider.value)
            prefsManager.setSelectedModel(_selectedModel.value)

            // Save API key to the correct provider slot
            prefsManager.setApiKeyForProvider(_selectedProvider.value, _apiKey.value)

            val agentName = _selectedAgent.value
            val sysPrompt = when (agentName) {
                "Hermes" -> "You are Hermes, a local execution agent. You can execute shell commands locally. If the user asks you to run a command, output exactly [RUN: command] or [SUDO: command] to execute as root via Shizuku. Do not wrap in markdown, just the raw tag if you need to execute. If you just want to talk, respond normally."
                "OpenClaude" -> "You are OpenClaude, an open-source Claude integration with full tool use."
                "Claude Code" -> "You are Claude Code, a specialized coding agent with codebase awareness."
                else -> "You are an expert AI assistant."
            }

            val agent = Agent(
                id = agentName.lowercase().replace(" ", "_"),
                name = agentName,
                description = "Agent provisioned during setup",
                systemPrompt = sysPrompt
            )
            repository.insertAgent(agent)
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            prefsManager.setOnboardingComplete(true)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return SetupViewModel(
                    application.container.prefsManager,
                    application.container.repository,
                    application.container.aiProvider,
                    application.container.localCommandRunner,
                    application.container.runtimeManager
                ) as T
            }
        }
    }
}
