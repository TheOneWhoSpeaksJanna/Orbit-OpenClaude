package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.core.di.AppContainer
import com.omniclaw.core.di.ToolCallRecord
import com.omniclaw.domain.models.Agent
import com.omniclaw.domain.models.ChatSession
import com.omniclaw.domain.models.Project
import com.omniclaw.domain.models.TermuxLog
import com.omniclaw.domain.repository.OmniClawRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

private const val DEFAULT_AGENT = "OpenClaude"
private const val DEFAULT_PROVIDER = "Claude"

class DashboardViewModel(
    private val repository: OmniClawRepository,
    private val appContainer: AppContainer
) : ViewModel() {

    init {
        // Clean up empty sessions left behind when users navigate away
        // without sending a message (e.g. tapping New Session then going back)
        viewModelScope.launch {
            repository.deleteEmptySessions()
        }
    }

    val projects: StateFlow<List<Project>> = repository.getAllProjects()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sessions: StateFlow<List<ChatSession>> = repository.getAllSessions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(1000),
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
            initialValue = DEFAULT_AGENT
        )

    val activeProvider = appContainer.prefsManager.selectedProvider
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DEFAULT_PROVIDER
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
