package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.core.di.AppContainer
import com.omniclaw.domain.models.TermuxLog
import com.omniclaw.domain.repository.OmniClawRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val PKG_INSTALL_PREFIX = "omniclaw-pkg install "
private const val INSTALL_START_PREFIX = "Starting installation of "
private const val INSTALL_SUFFIX = " via OmniClaw Package Manager...\n"
private const val SUCCESS_PREFIX = "Successfully installed "
private const val FAILURE_PREFIX = "Failed to install "
private const val SUDO_PREFIX = "sudo "

data class DownloadProgress(
    val title: String,
    val progress: Float,
    val mbPerSecond: Float,
    val timeRemainingSeconds: Int,
    val isActive: Boolean
)

class TermuxViewModel(
    private val repository: OmniClawRepository,
    private val appContainer: AppContainer
) : ViewModel() {

    val logs: StateFlow<List<TermuxLog>> = repository.getAllTermuxLogs()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    fun installTool(toolName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Ensure BusyBox is installed before any tool operations
            appContainer.runtimeManager.installBusyBox()

            val logId = java.util.UUID.randomUUID().toString()
            var currentLogOutput = "$INSTALL_START_PREFIX$toolName$INSTALL_SUFFIX"

            repository.insertTermuxLog(
                TermuxLog(
                    id = logId,
                    command = "$PKG_INSTALL_PREFIX$toolName",
                    output = currentLogOutput,
                    exitCode = -1,
                    timestamp = System.currentTimeMillis()
                )
            )

            val success = appContainer.packageInstaller.installPackage(toolName) { progress, status ->
                _downloadProgress.value = DownloadProgress(
                    title = status,
                    progress = progress,
                    mbPerSecond = 0f,
                    timeRemainingSeconds = 0,
                    isActive = progress < 1f
                )
            }

            val finalStatus = if (success) "$SUCCESS_PREFIX$toolName." else "$FAILURE_PREFIX$toolName."
            currentLogOutput += finalStatus

            repository.insertTermuxLog(
                TermuxLog(
                    id = logId,
                    command = "$PKG_INSTALL_PREFIX$toolName",
                    output = currentLogOutput,
                    exitCode = if (success) 0 else 1,
                    timestamp = System.currentTimeMillis()
                )
            )

            _downloadProgress.value = null
        }
    }

    fun executeCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            // Ensure BusyBox is available for command execution
            appContainer.runtimeManager.installBusyBox()

            val executionResult = appContainer.localCommandRunner.executeCommand(command)
            val log = TermuxLog(
                id = java.util.UUID.randomUUID().toString(),
                command = command,
                output = executionResult.output,
                exitCode = executionResult.exitCode,
                timestamp = System.currentTimeMillis()
            )
            repository.insertTermuxLog(log)
        }
    }

    fun executePrivilegedCommand(command: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val executionResult = appContainer.localCommandRunner.executePrivilegedCommand(command)
            val log = TermuxLog(
                id = java.util.UUID.randomUUID().toString(),
                command = "$SUDO_PREFIX$command",
                output = executionResult.output,
                exitCode = executionResult.exitCode,
                timestamp = System.currentTimeMillis()
            )
            repository.insertTermuxLog(log)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return TermuxViewModel(application.container.repository, application.container) as T
            }
        }
    }
}
