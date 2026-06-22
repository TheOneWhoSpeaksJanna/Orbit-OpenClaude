package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.data.local.prefs.PreferencesManager
import com.omniclaw.data.local.updater.UpdateManager
import com.omniclaw.data.local.updater.UpdateState
import com.omniclaw.data.local.updater.UpdateInfo
import com.omniclaw.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val prefsManager: PreferencesManager,
    private val updateManager: UpdateManager
) : ViewModel() {

    private val _geminiApiKey = MutableStateFlow("")
    val geminiApiKey: StateFlow<String> = _geminiApiKey.asStateFlow()

    private val _openAiApiKey = MutableStateFlow("")
    val openAiApiKey: StateFlow<String> = _openAiApiKey.asStateFlow()

    private val _claudeApiKey = MutableStateFlow("")
    val claudeApiKey: StateFlow<String> = _claudeApiKey.asStateFlow()

    private val _openRouterApiKey = MutableStateFlow("")
    val openRouterApiKey: StateFlow<String> = _openRouterApiKey.asStateFlow()

    private val _githubToken = MutableStateFlow("")
    val githubToken: StateFlow<String> = _githubToken.asStateFlow()

    private val _themeMode = MutableStateFlow("system")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    init {
        loadApiKeys()
    }

    private fun loadApiKeys() {
        viewModelScope.launch {
            _geminiApiKey.value = prefsManager.getApiKeyForProvider("Gemini").firstOrNull() ?: ""
            _openAiApiKey.value = prefsManager.getApiKeyForProvider("OpenAI").firstOrNull() ?: ""
            _claudeApiKey.value = prefsManager.getApiKeyForProvider("Claude").firstOrNull() ?: ""
            _openRouterApiKey.value = prefsManager.getApiKeyForProvider("OpenRouter").firstOrNull() ?: ""
            _githubToken.value = prefsManager.githubToken.firstOrNull() ?: ""
            _themeMode.value = prefsManager.themeMode.firstOrNull() ?: "system"
        }
    }

    fun updateGeminiApiKey(key: String) {
        _geminiApiKey.value = key
        viewModelScope.launch { prefsManager.setApiKeyForProvider("Gemini", key) }
    }

    fun updateOpenAiApiKey(key: String) {
        _openAiApiKey.value = key
        viewModelScope.launch { prefsManager.setApiKeyForProvider("OpenAI", key) }
    }

    fun updateClaudeApiKey(key: String) {
        _claudeApiKey.value = key
        viewModelScope.launch { prefsManager.setApiKeyForProvider("Claude", key) }
    }

    fun updateOpenRouterApiKey(key: String) {
        _openRouterApiKey.value = key
        viewModelScope.launch { prefsManager.setApiKeyForProvider("OpenRouter", key) }
    }

    fun updateGithubToken(token: String) {
        _githubToken.value = token
        viewModelScope.launch { prefsManager.setGithubToken(token) }
    }

    fun updateThemeMode(mode: String) {
        _themeMode.value = mode
        viewModelScope.launch { prefsManager.setThemeMode(mode) }
    }

    // Update management
    val appVersion: String = BuildConfig.VERSION_NAME
    val updateState: StateFlow<UpdateState> = updateManager.updateState

    fun checkForUpdates() {
        viewModelScope.launch { updateManager.checkForUpdates() }
    }

    fun downloadUpdate(info: UpdateInfo) {
        viewModelScope.launch { updateManager.downloadUpdate(info) }
    }

    fun installUpdate(filePath: String) {
        updateManager.installApk(filePath)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return SettingsViewModel(
                    application.container.prefsManager,
                    application.container.updateManager
                ) as T
            }
        }
    }
}
