package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.data.local.runner.LocalCommandRunner
import com.omniclaw.data.local.prefs.PreferencesManager
import com.omniclaw.data.local.runtime.PackageInstaller
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.api.AiResult
import com.omniclaw.domain.models.ChatSession
import com.omniclaw.domain.models.DetailedModelInfo
import com.omniclaw.domain.models.Message
import com.omniclaw.domain.models.MessageRole
import com.omniclaw.domain.models.TermuxLog
import com.omniclaw.domain.repository.OmniClawRepository
import com.omniclaw.domain.models.AgentPermissionLevel
import com.omniclaw.domain.repository.OpenCodeRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

private const val DEFAULT_PROVIDER = "Gemini"
private const val NEW_SESSION_TITLE = "New Session"
private const val DEFAULT_SYSTEM_PROMPT = "System: You are an expert AI assistant."
private const val NO_OUTPUT = "(no output)"
private const val ERROR_RUNNING_AGENT = "Error running agent: "
private const val UNKNOWN_ERROR = "Unknown error"
private const val ACTION_BLOCKED = "Action blocked by permission rules: "

enum class PermissionResult { ALLOWED, ASK, BLOCKED }

class ChatViewModel(
    private val repository: OmniClawRepository,
    private val aiProvider: AiProvider,
    private val localCommandRunner: LocalCommandRunner,
    private val prefsManager: PreferencesManager,
    private val openCodeRepository: OpenCodeRepository,
    private val packageInstaller: PackageInstaller
) : ViewModel() {

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _availableModels = MutableStateFlow<List<String>>(emptyList())
    val availableModels: StateFlow<List<String>> = _availableModels.asStateFlow()

    private val _useLocalMode = MutableStateFlow(false)
    val useLocalMode: StateFlow<Boolean> = _useLocalMode.asStateFlow()

    fun toggleLocalMode() {
        _useLocalMode.value = !_useLocalMode.value
    }

    data class PendingCommand(
        val command: String,
        val isSudo: Boolean
    )

    // AI loop state
    private var loopPromptBuilder = StringBuilder()
    private var loopContinueLooping = false
    private var loopSessionId = ""
    private var loopActiveProvider = ""
    private var loopActiveModelName = ""
    private var loopApiKey = ""

    private val _pendingCommand = MutableStateFlow<PendingCommand?>(null)
    val pendingCommand: StateFlow<PendingCommand?> = _pendingCommand.asStateFlow()

    fun confirmPendingCommand() {
        val pending = _pendingCommand.value ?: return
        _pendingCommand.value = null
        viewModelScope.launch {
            executeCommandAndContinue(pending.command, pending.isSudo)
        }
    }

    fun denyPendingCommand() {
        val pending = _pendingCommand.value ?: return
        _pendingCommand.value = null
        viewModelScope.launch {
            val blockMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = loopSessionId,
                role = MessageRole.TOOL,
                content = "$ACTION_BLOCKED${pending.command}",
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(blockMsg)
            loopPromptBuilder.append("TOOL: $ACTION_BLOCKED${pending.command}\nMODEL: ")
            loopContinueLooping = true
            continueAiLoop()
        }
    }

    private suspend fun isCommandAllowed(cmd: String, isSudo: Boolean): PermissionResult {
        val level = AgentPermissionLevel.fromValue(prefsManager.agentPermissionLevel.firstOrNull() ?: "NORMAL")
        return when (level) {
            AgentPermissionLevel.FULL_ACCESS -> PermissionResult.ALLOWED
            AgentPermissionLevel.NORMAL -> PermissionResult.ASK
            AgentPermissionLevel.RULES -> {
                val lower = cmd.lowercase()
                val allowedRules = prefsManager.agentRulesAllowed.firstOrNull() ?: ""
                val askRules = prefsManager.agentRulesAsk.firstOrNull() ?: ""
                val deniedRules = prefsManager.agentRulesDenied.firstOrNull() ?: ""

                val allowed = allowedRules.lines().filter { it.isNotBlank() }
                val ask = askRules.lines().filter { it.isNotBlank() }
                val denied = deniedRules.lines().filter { it.isNotBlank() }

                if (denied.any { lower.contains(it.lowercase()) }) return PermissionResult.BLOCKED
                if (ask.any { lower.contains(it.lowercase()) }) return PermissionResult.ASK
                if (allowed.isEmpty()) return PermissionResult.ASK
                if (allowed.any { lower.contains(it.lowercase()) }) return PermissionResult.ALLOWED
                PermissionResult.ASK
            }
        }
    }

    private val _detailedModels = MutableStateFlow<List<DetailedModelInfo>>(emptyList())
    val detailedModels: StateFlow<List<DetailedModelInfo>> = _detailedModels.asStateFlow()

    private val _isFetchingModels = MutableStateFlow(false)
    val isFetchingModels: StateFlow<Boolean> = _isFetchingModels.asStateFlow()

    val selectedModel: StateFlow<String> = prefsManager.selectedModel
        .map { it ?: "" }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val selectedAgent: StateFlow<String?> = prefsManager.selectedAgent
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasAgent: StateFlow<Boolean> = prefsManager.selectedAgent
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun loadModelsForCurrentProvider() {
        viewModelScope.launch {
            val provider = prefsManager.selectedProvider.firstOrNull() ?: DEFAULT_PROVIDER
            val models = aiProvider.getModels(provider)
            _availableModels.value = if (models.isNotEmpty()) models else DEFAULT_MODELS
        }
    }

    fun fetchDetailedModels() {
        viewModelScope.launch {
            val provider = prefsManager.selectedProvider.firstOrNull() ?: DEFAULT_PROVIDER
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

    private var loadSessionJob: Job? = null

    fun loadSession(sessionId: String) {
        loadSessionJob?.cancel()
        loadSessionJob = viewModelScope.launch {
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
                title = NEW_SESSION_TITLE,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            _currentSession.value = session
            _messages.value = emptyList()
            loadSession(session.id)
        }
    }

    fun sendMessage(content: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            // Persist session on first message (blank sessions never hit the DB)
            repository.insertSession(session)

            val userMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                role = MessageRole.USER,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(userMsg)

            val activeProvider = prefsManager.selectedProvider.firstOrNull() ?: DEFAULT_PROVIDER
            val activeModelName = prefsManager.selectedModel.firstOrNull() ?: ""
            val activeAgentId = prefsManager.selectedAgent.firstOrNull()
                ?.lowercase()?.replace(" ", "-")
                ?: return@launch

            _isLoading.value = true

            if (_useLocalMode.value) {
                var runCmd: String? = null
                val agentEntity = repository.getAllAgents().firstOrNull()?.find { it.id == activeAgentId }
                runCmd = agentEntity?.runCommand

                if (runCmd.isNullOrBlank()) {
                    val catalogAgent = openCodeRepository.getAgentById(activeAgentId)
                    runCmd = catalogAgent?.runCommand
                }

                if (runCmd.isNullOrBlank()) {
                    runCmd = activeAgentId
                }

                // Runtime safety net: ensure the agent binary exists and is executable
                runCmd?.let { cmd ->
                    val cmdFile = File(cmd)
                    if (!cmdFile.exists()) {
                        // Wrapper script missing (e.g. after APK update) — recreate from agent code
                        val agentDir = File(cmdFile.parentFile?.parentFile, "agents/${cmdFile.name}")
                        if (agentDir.exists()) {
                            try {
                                val entryPoint = listOf("dist/index.js", "index.js", "main.js")
                                    .firstOrNull { File(agentDir, it).exists() } ?: "index.js"
                                cmdFile.parentFile?.mkdirs()
                                cmdFile.writeText("#!/system/bin/sh\nexec node ${agentDir.absolutePath}/${entryPoint} \"\$@\"\n")
                                cmdFile.setExecutable(true)
                            } catch (_: Exception) { /* best effort */ }
                        }
                    } else if (cmdFile.isFile && !cmdFile.canExecute()) {
                        try {
                            cmdFile.setExecutable(true)
                            if (!cmdFile.canExecute()) {
                                // LocalCommandRunner has binDir in PATH so BusyBox chmod resolves
                                localCommandRunner.executeCommand("chmod +x " + cmdFile.absolutePath)
                            }
                        } catch (_: Exception) { /* best effort */ }
                    }
                }

                // Ensure Node.js is available — agents run via a node-based wrapper script
                try {
                    val nodeCheck = localCommandRunner.executeCommand("command -v node")
                    if (nodeCheck.exitCode != 0 || nodeCheck.output.isBlank()) {
                        packageInstaller.installPackage("nodejs") { _, _ -> }
                    }
                } catch (_: Exception) { /* best effort, let the command fail naturally */ }

                try {
                    val escaped = content.replace("\"", "\\\"")
                    // Run wrapper scripts via sh to bypass executable permission issues on Android.
                    // This works like "sh script.sh" vs "./script.sh" — sh reads the file directly
                    // without needing the +x permission bit or a filesystem that supports exec.
                    val safeRunCmd = if (runCmd.startsWith('/')) "sh ${runCmd}" else runCmd
                    val result = localCommandRunner.executeCommand("echo \"$escaped\" | $safeRunCmd")
                    val modelMsg = Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = session.id,
                        role = MessageRole.MODEL,
                        content = result.output.ifBlank { NO_OUTPUT },
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insertMessage(modelMsg)
                } catch (e: Exception) {
                    val errMsg = Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = session.id,
                        role = MessageRole.MODEL,
                        content = "$ERROR_RUNNING_AGENT${e.message ?: UNKNOWN_ERROR}",
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insertMessage(errMsg)
                }
                _isLoading.value = false
                return@launch
            }

            val apiKey = prefsManager.getApiKeyForProvider(activeProvider).firstOrNull() ?: ""

            val agentEntity = repository.getAllAgents().firstOrNull()?.find { it.id == activeAgentId }
            val systemPrompt = agentEntity?.systemPrompt ?: DEFAULT_SYSTEM_PROMPT

            val promptBuilder = StringBuilder()
            promptBuilder.append("$systemPrompt\n\n")

            // Append enabled skills
            val skills = repository.getAllSkills().firstOrNull().orEmpty()
            val enabledSkills = skills.filter { it.enabled && it.content.isNotBlank() }
            if (enabledSkills.isNotEmpty()) {
                promptBuilder.append("## Active Skills\n\n")
                for (skill in enabledSkills) {
                    promptBuilder.append("### ${skill.name}\n")
                    promptBuilder.append("${skill.content}\n\n")
                }
            }

            _messages.value.forEach { msg ->
                promptBuilder.append("${msg.role.name}: ${msg.content}\n")
            }
            promptBuilder.append("USER: $content\nMODEL: ")

            loopPromptBuilder = promptBuilder
            loopContinueLooping = true
            loopSessionId = session.id
            loopActiveProvider = activeProvider
            loopActiveModelName = activeModelName
            loopApiKey = apiKey

            continueAiLoop()
        }
    }

    private suspend fun executeCommandAndContinue(cmd: String, isSudo: Boolean) {
        loopContinueLooping = true
        val execResult = if (isSudo) {
            localCommandRunner.executePrivilegedCommand(cmd)
        } else {
            localCommandRunner.executeCommand(cmd)
        }
        val toolOutput = "Output: ${execResult.output}\nExitCode: ${execResult.exitCode}"

        repository.insertTermuxLog(
            TermuxLog(
                UUID.randomUUID().toString(),
                if (isSudo) "sudo $cmd" else cmd,
                execResult.output,
                execResult.exitCode,
                System.currentTimeMillis()
            )
        )

        val toolMsg = Message(
            id = UUID.randomUUID().toString(),
            sessionId = loopSessionId,
            role = MessageRole.TOOL,
            content = toolOutput,
            timestamp = System.currentTimeMillis()
        )
        repository.insertMessage(toolMsg)
        loopPromptBuilder.append("TOOL: $toolOutput\nMODEL: ")
        continueAiLoop()
    }

    private fun continueAiLoop() {
        viewModelScope.launch {
            while (loopContinueLooping) {
                val result = aiProvider.generateContent(
                    loopPromptBuilder.toString(), loopApiKey, loopActiveProvider, loopActiveModelName
                )

                val modelText = when (result) {
                    is AiResult.Success -> result.text
                    is AiResult.Error -> "Error: ${result.message}"
                }

                if (modelText.contains("[RUN: ") || modelText.contains("[SUDO: ")) {
                    val runMatch = RUN_COMMAND_REGEX.find(modelText)
                    val sudoMatch = SUDO_COMMAND_REGEX.find(modelText)

                    val actionModelMsg = Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = loopSessionId,
                        role = MessageRole.MODEL,
                        content = modelText,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insertMessage(actionModelMsg)

                    val pair = when {
                        runMatch != null -> runMatch.groupValues[1] to false
                        sudoMatch != null -> sudoMatch.groupValues[1] to true
                        else -> null
                    } ?: continue
                    val (cmd, isSudo) = pair

                    when (isCommandAllowed(cmd, isSudo)) {
                        PermissionResult.ALLOWED -> {
                            executeCommandAndContinue(cmd, isSudo)
                            return@launch
                        }
                        PermissionResult.ASK -> {
                            _pendingCommand.value = PendingCommand(cmd, isSudo)
                            loopContinueLooping = false
                            _isLoading.value = false
                            return@launch
                        }
                        PermissionResult.BLOCKED -> {
                            val blockMsg = Message(
                                id = UUID.randomUUID().toString(),
                                sessionId = loopSessionId,
                                role = MessageRole.TOOL,
                                content = "$ACTION_BLOCKED$cmd",
                                timestamp = System.currentTimeMillis()
                            )
                            repository.insertMessage(blockMsg)
                            loopPromptBuilder.append("TOOL: $ACTION_BLOCKED$cmd\nMODEL: ")
                        }
                    }
                } else {
                    val modelMsg = Message(
                        id = UUID.randomUUID().toString(),
                        sessionId = loopSessionId,
                        role = MessageRole.MODEL,
                        content = modelText,
                        timestamp = System.currentTimeMillis()
                    )
                    repository.insertMessage(modelMsg)
                    loopContinueLooping = false
                }
            }
            _isLoading.value = false
        }
    }

    companion object {
        private val RUN_COMMAND_REGEX = "\\[RUN: (.+?)]".toRegex()
        private val SUDO_COMMAND_REGEX = "\\[SUDO: (.+?)]".toRegex()
        val DEFAULT_MODELS = listOf("gemini-2.0-flash-exp", "gpt-4o", "claude-sonnet-4-20250514")

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return ChatViewModel(
                    application.container.repository,
                    application.container.aiProvider,
                    application.container.localCommandRunner,
                    application.container.prefsManager,
                    application.container.openCodeRepository,
                    application.container.packageInstaller
                ) as T
            }
        }
    }
}
