package com.omniclaw.presentation.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.data.local.prefs.PreferencesManager
import com.omniclaw.domain.model.Agent
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.repository.OmniClawRepository
import com.omniclaw.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SetupStep(@StringRes val labelResId: Int) {
    WELCOME(R.string.step_welcome),
    THEME(R.string.step_theme),
    AGENT(R.string.step_agent),
    PROVIDER(R.string.step_provider),
    SHIZUKU(R.string.step_shizuku),
    SUMMARY(R.string.step_summary)
}

class SetupViewModel(
    private val prefsManager: PreferencesManager,
    private val repository: OmniClawRepository,
    private val aiProvider: AiProvider
) : ViewModel() {

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    val stepCount: Int get() = SetupStep.entries.size
    val isLastStep: Boolean get() = _currentStep.value >= SetupStep.entries.lastIndex

    val currentStepDef: SetupStep get() = SetupStep.entries[_currentStep.value]

    private val _theme = MutableStateFlow("System")
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _shizukuEnabled = MutableStateFlow(false)
    val shizukuEnabled: StateFlow<Boolean> = _shizukuEnabled.asStateFlow()

    private val _selectedAgent = MutableStateFlow("Hermes")
    val selectedAgent: StateFlow<String> = _selectedAgent.asStateFlow()

    private val _selectedProvider = MutableStateFlow("Claude")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testConnectionSuccess = MutableStateFlow<Boolean?>(null)
    val testConnectionSuccess: StateFlow<Boolean?> = _testConnectionSuccess.asStateFlow()

    val canAdvance: Boolean
        get() = when (currentStepDef) {
            SetupStep.SUMMARY -> false
            SetupStep.PROVIDER -> _apiKey.value.isNotBlank()
            else -> true
        }

    fun nextStep() {
        _currentStep.value += 1
        _testConnectionSuccess.value = null
    }

    fun previousStep() {
        if (_currentStep.value > 0) {
            _currentStep.value -= 1
            _testConnectionSuccess.value = null
        }
    }

    fun setTheme(mode: String) {
        _theme.value = mode
    }

    fun setShizukuEnabled(enabled: Boolean) {
        _shizukuEnabled.value = enabled
    }

    fun setSelectedAgent(agent: String) {
        _selectedAgent.value = agent
    }

    fun setSelectedProvider(provider: String) {
        _selectedProvider.value = provider
    }

    fun setApiKey(key: String) {
        _apiKey.value = key
    }

    fun testConnection() {
        viewModelScope.launch {
            _isTestingConnection.value = true
            _testConnectionSuccess.value = null
            
            val success = try {
                aiProvider.testConnection(
                    provider = _selectedProvider.value,
                    apiKey = _apiKey.value,
                    model = ""
                )
            } catch (e: Exception) {
                false
            }
            
            _testConnectionSuccess.value = success
            _isTestingConnection.value = false
        }
    }

    fun completeSetup() {
        viewModelScope.launch {
            prefsManager.setThemeMode(_theme.value)
            prefsManager.setShizukuEnabled(_shizukuEnabled.value)
            prefsManager.setSelectedAgent(_selectedAgent.value)
            prefsManager.setSelectedProvider(_selectedProvider.value)
            prefsManager.setApiKeyForProvider(_selectedProvider.value.lowercase(), _apiKey.value)
            
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
                    application.container.aiProvider
                ) as T
            }
        }
    }
}
