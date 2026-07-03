package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.core.di.AppContainer
import com.omniclaw.core.logging.FileLogger
import com.omniclaw.domain.models.TermuxLog
import com.omniclaw.domain.repository.OmniClawRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "TermuxViewModel"
private const val PKG_INSTALL_PREFIX = "omniclaw-pkg install "
private const val INSTALL_START_PREFIX = "Starting installation of "
private const val INSTALL_SUFFIX = " via OmniClaw Package Manager...\n"
private const val SUCCESS_PREFIX = "Successfully installed "
private const val FAILURE_PREFIX = "Failed to install "
private const val SUDO_PREFIX = "sudo "

private val HELP_TEXT = """
Orbit-AI Terminal — Available Commands:

SHELL COMMANDS (run via BusyBox):
  ls, cd, cp, mv, rm, mkdir, cat, grep, sed, awk, tar, wget, curl, find
  Type any command name + --help for usage (e.g. "ls --help")

PACKAGE MANAGEMENT:
  omniclaw-pkg install <name>   Install a package (git, python, nodejs, curl)
  omniclaw-pkg list             List installed packages

BUILT-IN COMMANDS:
  help                          Show this help message
  env                           Show environment variables (PATH, LD_LIBRARY_PATH, etc.)
  orbit-version                 Show app version and runtime info
  ls-bin                        List all binaries in orbit_runtime/bin/
  ls-packages                   List all installed packages

SYSTEM (via Shizuku if enabled):
  sudo <command>                Run command with elevated privileges

TIPS:
  - Long-press any output card to copy it to clipboard
  - Tap the copy icon (top-right) to copy command + output
  - If a command fails, check Settings → Diagnostics for the log path
  - Use "env" to see if PATH and LD_LIBRARY_PATH are set correctly
""".trimIndent()

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
        FileLogger.i(TAG, "installTool('$toolName') called")
        viewModelScope.launch(Dispatchers.IO) {
            // Ensure BusyBox is installed before any tool operations
            FileLogger.d(TAG, "installTool: ensuring BusyBox...")
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

            FileLogger.i(TAG, "installTool: calling packageInstaller.installPackage('$toolName')...")
            val success = appContainer.packageInstaller.installPackage(toolName) { progress, status ->
                FileLogger.d(TAG, "installTool progress: $progress — $status")
                _downloadProgress.value = DownloadProgress(
                    title = status,
                    progress = progress,
                    mbPerSecond = 0f,
                    timeRemainingSeconds = 0,
                    isActive = progress < 1f
                )
            }

            val finalStatus = if (success) "$SUCCESS_PREFIX$toolName." else "$FAILURE_PREFIX$toolName."
            FileLogger.i(TAG, "installTool result: $finalStatus")
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
        val trimmed = command.trim()
        FileLogger.i(TAG, "executeCommand: '$trimmed'")

        // Intercept built-in commands that aren't shell builtins
        if (trimmed == "help" || trimmed == "?") {
            viewModelScope.launch(Dispatchers.IO) {
                repository.insertTermuxLog(
                    TermuxLog(
                        id = java.util.UUID.randomUUID().toString(),
                        command = trimmed,
                        output = HELP_TEXT,
                        exitCode = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            return
        }

        if (trimmed == "omniclaw-pkg list" || trimmed == "ls-packages") {
            viewModelScope.launch(Dispatchers.IO) {
                val pkgDir = appContainer.runtimeManager.packagesDir
                val packages = pkgDir.listFiles { f -> f.isDirectory }?.map { it.name } ?: emptyList()
                val output = if (packages.isEmpty()) {
                    "No packages installed. Use 'omniclaw-pkg install <name>' to install."
                } else {
                    "Installed packages (${packages.size}):\n" + packages.joinToString("\n") { "  - $it" }
                }
                repository.insertTermuxLog(
                    TermuxLog(
                        id = java.util.UUID.randomUUID().toString(),
                        command = trimmed,
                        output = output,
                        exitCode = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            return
        }

        if (trimmed == "ls-bin") {
            viewModelScope.launch(Dispatchers.IO) {
                val binDir = appContainer.runtimeManager.binDir
                val bins = binDir.listFiles { f -> f.isFile }?.map { it.name }?.sorted() ?: emptyList()
                val output = if (bins.isEmpty()) {
                    "No binaries in ${binDir.absolutePath}"
                } else {
                    "Binaries in orbit_runtime/bin/ (${bins.size}):\n" + bins.joinToString("\n") { "  - $it" }
                }
                repository.insertTermuxLog(
                    TermuxLog(
                        id = java.util.UUID.randomUUID().toString(),
                        command = trimmed,
                        output = output,
                        exitCode = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            return
        }

        if (trimmed == "orbit-version") {
            viewModelScope.launch(Dispatchers.IO) {
                val output = buildString {
                    appendLine("Orbit-AI Runtime Info")
                    appendLine("  App version: ${com.omniclaw.BuildConfig.VERSION_NAME}")
                    appendLine("  Flavor: ${com.omniclaw.BuildConfig.FLAVOR_APP_LABEL}")
                    appendLine("  Runtime dir: ${appContainer.runtimeManager.runtimeDir.absolutePath}")
                    appendLine("  Bin dir: ${appContainer.runtimeManager.binDir.absolutePath}")
                    appendLine("  Packages dir: ${appContainer.runtimeManager.packagesDir.absolutePath}")
                    appendLine("  BusyBox: ${appContainer.runtimeManager.busyBoxPath() ?: "NOT INSTALLED"}")
                    appendLine("  Node binary: ${appContainer.runtimeManager.findNodeBinary() ?: "NOT INSTALLED"}")
                    appendLine("  PATH: ${appContainer.runtimeManager.buildPath()}")
                    appendLine("  LD_LIBRARY_PATH: ${appContainer.runtimeManager.buildLdLibraryPath()}")
                }
                repository.insertTermuxLog(
                    TermuxLog(
                        id = java.util.UUID.randomUUID().toString(),
                        command = trimmed,
                        output = output,
                        exitCode = 0,
                        timestamp = System.currentTimeMillis()
                    )
                )
            }
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // ── NEW: Execute inside the PRoot Alpine environment ─────────
            // The terminal now runs commands inside the Alpine Linux rootfs
            // via PRoot, giving the user a REAL Linux shell with node, git,
            // python, etc. — not the limited Android shell.
            val prootRuntime = appContainer.prootRuntime

            // Ensure rootfs is installed
            if (!prootRuntime.isRootfsInstalled) {
                repository.insertTermuxLog(
                    TermuxLog(
                        id = java.util.UUID.randomUUID().toString(),
                        command = trimmed,
                        output = "Initializing Linux environment (first launch, ~30s)...",
                        exitCode = -1,
                        timestamp = System.currentTimeMillis()
                    )
                )
                prootRuntime.installRootfs { progress, status ->
                    FileLogger.d(TAG, "Rootfs install: $progress — $status")
                }
            }

            val executionResult = prootRuntime.executeInRootfs(trimmed, "")
            val log = TermuxLog(
                id = java.util.UUID.randomUUID().toString(),
                command = trimmed,
                output = executionResult.output,
                exitCode = executionResult.exitCode,
                timestamp = System.currentTimeMillis()
            )
            repository.insertTermuxLog(log)
        }
    }

    fun executePrivilegedCommand(command: String) {
        FileLogger.i(TAG, "executePrivilegedCommand: '$command'")
        viewModelScope.launch(Dispatchers.IO) {
            // Sudo commands use Shizuku (Android system-level access)
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
