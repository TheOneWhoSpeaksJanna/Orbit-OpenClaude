package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.core.di.AppContainer
import com.omniclaw.data.local.runner.LocalCommandRunner
import com.omniclaw.data.local.prefs.PreferencesManager
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.api.AiResult
import com.omniclaw.domain.models.ChatSession
import com.omniclaw.domain.models.DetailedModelInfo
import com.omniclaw.domain.models.Message
import com.omniclaw.domain.models.MessageRole
import com.omniclaw.domain.models.TermuxLog
import com.omniclaw.domain.repository.OmniClawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val repository: OmniClawRepository,
    private val aiProvider: AiProvider,
    private val localCommandRunner: LocalCommandRunner,
    private val prefsManager: PreferencesManager
) : ViewModel() {

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _detailedModels = MutableStateFlow<List<DetailedModelInfo>>(emptyList())
    val detailedModels: StateFlow<List<DetailedModelInfo>> = _detailedModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    val selectedModel: StateFlow<String> = prefsManager.selectedModel
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    fun loadModelsForCurrentProvider() {
        viewModelScope.launch {
            val provider = prefsManager.selectedProvider.firstOrNull() ?: "Gemini"
            _availableModels.value = aiProvider.getModels(provider)
        }
    }

    fun fetchDetailedModels() {
        viewModelScope.launch {
            val provider = prefsManager.selectedProvider.firstOrNull() ?: "Gemini"
            if (provider != "OpenRouter") return@launch
            _isFetchingModels.value = true
            val apiKey = prefsManager.getApiKeyForProvider(provider).firstOrNull() ?: ""
            if (apiKey.isBlank()) {
                _isFetchingModels.value = false
                return@launch
            }
            try {
                val models = aiProvider.fetchDetailedModels(provider, apiKey)
                _detailedModels.value = models
                _availableModels.value = models.map { it.id }
            } catch (_: Exception) {
                // Keep current list on failure
            } finally {
                _isFetchingModels.value = false
            }
        }
    }

    fun setSelectedModel(model: String) {
        viewModelScope.launch {
            prefsManager.setSelectedModel(model)
        }
    }

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            repository.getAllSessions().firstOrNull()?.find { it.id == sessionId }?.let {
                _currentSession.value = it
            }
            repository.getMessagesForSession(sessionId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun startNewSession(projectId: String?) {
        viewModelScope.launch {
            val session = ChatSession(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = "New Session",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertSession(session)
            _currentSession.value = session
            _messages.value = emptyList()
            loadSession(session.id)
        }
    }

    fun sendMessage(content: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val userMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                role = MessageRole.USER,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(userMsg)
            _isLoading.value = true

            val activeProvider = prefsManager.selectedProvider.firstOrNull() ?: "Gemini"
            val activeModelName = prefsManager.selectedModel.firstOrNull() ?: ""
            val activeAgentId = prefsManager.selectedAgent.firstOrNull()
                ?.lowercase()?.replace(" ", "_") ?: "hermes"

            // Get the correct API key for the selected provider
            val apiKey = prefsManager.getApiKeyForProvider(activeProvider).firstOrNull() ?: ""

            val agentEntity = repository.getAllAgents().firstOrNull()?.find { it.id == activeAgentId }
            val systemPrompt = agentEntity?.systemPrompt
                ?: "System: You are an expert AI assistant."

            // Build full prompt history
            val promptBuilder = StringBuilder()
            promptBuilder.append("$systemPrompt\n\n")

            _messages.value.forEach { msg ->
                promptBuilder.append("${msg.role.name}: ${msg.content}\n")
            }
            promptBuilder.append("USER: $content\nMODEL: ")

            var continueLooping = true
            while (continueLooping) {
                val result = aiProvider.generateContent(
                    promptBuilder.toString(), apiKey, activeProvider, activeModelName
                )

                val modelText = when (result) {
                    is AiResult.Success -> result.text
                    is AiResult.Error -> "Error: ${result.message}"
                }

                if (modelText.contains("[RUN: ") || modelText.contains("[SUDO: ")) {
                    val runMatch = "\\[RUN: (.+?)]".toRegex().find(modelText)
                    val sudoMatch = "\\[SUDO: (.+?)]".toRegex().find(modelText)

                    val actionModelMsg = Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = session.id,
                        role = MessageRole.MODEL,
                        content = modelText,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insertMessage(actionModelMsg)

                    if (runMatch != null) {
                        val cmd = runMatch.groupValues[1]
                        val execResult = localCommandRunner.executeCommand(cmd)
                        val toolOutput = "Output: ${execResult.output}\nExitCode: ${execResult.exitCode}"

                        repository.insertTermuxLog(
                            TermuxLog(
                                UUID.randomUUID().toString(), cmd, execResult.output,
                                execResult.exitCode, System.currentTimeMillis()
                            )
                        )

                        val toolMsg = Message(
                            id = UUID.randomUUID().toString(),
                            sessionId = session.id,
                            role = MessageRole.TOOL,
                            content = toolOutput,
                            timestamp = System.currentTimeMillis()
                        )
                        repository.insertMessage(toolMsg)
                        promptBuilder.append("$modelText\nTOOL: $toolOutput\nMODEL: ")
                    } else if (sudoMatch != null) {
                        val cmd = sudoMatch.groupValues[1]
                        val execResult = localCommandRunner.executePrivilegedCommand(cmd)
                        val toolOutput = "Output: ${execResult.output}\nExitCode: ${execResult.exitCode}"

                        repository.insertTermuxLog(
                            TermuxLog(
                                UUID.randomUUID().toString(), "sudo $cmd", execResult.output,
                                execResult.exitCode, System.currentTimeMillis()
                            )
                        )

                        val toolMsg = Message(
                            id = UUID.randomUUID().toString(),
                            sessionId = session.id,
                            role = MessageRole.TOOL,
                            content = toolOutput,
                            timestamp = System.currentTimeMillis()
                        )
                        repository.insertMessage(toolMsg)
                        promptBuilder.append("$modelText\nTOOL: $toolOutput\nMODEL: ")
                    }
                } else {
                    val modelMsg = Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = session.id,
                        role = MessageRole.MODEL,
                        content = modelText,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insertMessage(modelMsg)
                    continueLooping = false
                }
            }
            _isLoading.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return ChatViewModel(
                    application.container.repository,
                    application.container.aiProvider,
                    application.container.localCommandRunner,
                    application.container.prefsManager
                ) as T
            }
        }
    }
}
