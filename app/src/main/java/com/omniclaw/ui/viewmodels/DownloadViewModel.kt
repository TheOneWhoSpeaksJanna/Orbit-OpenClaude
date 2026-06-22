package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.domain.api.AgentDownloader
import com.omniclaw.domain.models.DownloadState
import com.omniclaw.domain.models.DownloadableAgent
import com.omniclaw.domain.repository.OpenCodeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DownloadableAgentUi(
    val agent: DownloadableAgent,
    val downloadState: DownloadState = DownloadState.Idle
)

class DownloadViewModel(
    private val openCodeRepository: OpenCodeRepository,
    private val agentDownloader: AgentDownloader
) : ViewModel() {

    private val _agents = MutableStateFlow<List<DownloadableAgentUi>>(emptyList())
    val agents: StateFlow<List<DownloadableAgentUi>> = _agents.asStateFlow()

    init {
        viewModelScope.launch {
            openCodeRepository.getAvailableAgents().collect { catalog ->
                val current = _agents.value.toMutableList()
                val existingIds = current.map { it.agent.id }.toSet()
                val merged = catalog.map { agent ->
                    val existing = current.find { it.agent.id == agent.id }
                    existing ?: DownloadableAgentUi(agent)
                }
                _agents.value = merged
            }
        }
    }

    fun downloadAgent(agentId: String) {
        val index = _agents.value.indexOfFirst { it.agent.id == agentId }
        if (index == -1) return

        val entry = _agents.value[index]
        if (entry.downloadState !is DownloadState.Idle &&
            entry.downloadState !is DownloadState.Error
        ) return

        _agents.value = _agents.value.toMutableList().also { list ->
            list[index] = entry.copy(downloadState = DownloadState.Idle)
        }

        viewModelScope.launch {
            agentDownloader.download(
                url = entry.agent.downloadUrl,
                destinationFileName = "${entry.agent.id}-${entry.agent.version}.tar.gz"
            ).collect { state ->
                val idx = _agents.value.indexOfFirst { it.agent.id == agentId }
                if (idx == -1) return@collect
                _agents.value = _agents.value.toMutableList().also { list ->
                    list[idx] = list[idx].copy(downloadState = state)
                }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return DownloadViewModel(
                    openCodeRepository = app.container.openCodeRepository,
                    agentDownloader = app.container.agentDownloader
                ) as T
            }
        }
    }
}
