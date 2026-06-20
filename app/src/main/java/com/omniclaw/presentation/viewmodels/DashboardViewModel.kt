package com.omniclaw.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.core.di.ToolCallRecord
import com.omniclaw.domain.model.Agent
import com.omniclaw.domain.model.ChatSession
import com.omniclaw.domain.model.Project
import com.omniclaw.domain.model.TermuxLog
import com.omniclaw.domain.repository.OmniClawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class DashboardViewModel(
    private val repository: OmniClawRepository,
    private val appContainer: com.omniclaw.core.di.AppContainer
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val agents: StateFlow<List<Agent>> = repository.getAllAgents()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val termuxLogs: StateFlow<List<TermuxLog>> = repository.getAllTermuxLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val activeAgent = appContainer.prefsManager.selectedAgent
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Hermes"
        )

    val activeProvider = appContainer.prefsManager.selectedProvider
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "Claude"
        )

    val shizukuEnabled = appContainer.prefsManager.shizukuEnabled
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    val activeSessionToolCalls: StateFlow<List<ToolCallRecord>> = combine(
        sessions, appContainer.toolCallRecorder.records
    ) { sessionList, records ->
        val lastSessionId = sessionList.maxByOrNull { it.updatedAt }?.id ?: return@combine emptyList()
        records.filter { it.sessionId == lastSessionId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _gitDiffResult = MutableStateFlow<String?>(null)
    val gitDiffResult: StateFlow<String?> = _gitDiffResult.asStateFlow()

    init {
        loadGitDiff()
    }

    private fun loadGitDiff() {
        viewModelScope.launch {
            val result = appContainer.localCommandRunner.executeCommand(
                "git -C /data/data/com.termux/files/home/project\\ omniclaw diff --stat"
            )
            _gitDiffResult.value = if (result.exitCode == 0 && result.output.isNotBlank()) {
                result.output
            } else {
                null
            }
        }
    }

    fun createNewProject(name: String, description: String) {
        viewModelScope.launch {
            val project = Project(
                id = UUID.randomUUID().toString(),
                name = name,
                description = description,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertProject(project)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return DashboardViewModel(application.container.repository, application.container) as T
            }
        }
    }
}
