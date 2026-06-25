package com.omniclaw.ui.viewmodels

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.BuildConfig
import com.omniclaw.OmniClawApplication
import com.omniclaw.R
import com.omniclaw.core.config.FlavorConfig
import com.omniclaw.data.local.prefs.PreferencesManager
import com.omniclaw.data.local.runner.LocalCommandRunner
import com.omniclaw.data.local.runtime.OmniClawRuntimeManager
import com.omniclaw.domain.models.Agent
import com.omniclaw.domain.models.Skill
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.api.AiResult
import com.omniclaw.domain.repository.OmniClawRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.net.URL
import java.util.zip.ZipInputStream

private const val DEFAULT_THEME = "System"
private const val DEFAULT_AGENT = "Hermes"
private const val DEFAULT_PROVIDER = "Gemini"
private const val AGENT_HERMES = "Hermes"
private const val AGENT_OPENCLAUDE = "OpenClaude"
private const val AGENT_CLAUDE_CODE = "Claude Code"
private const val AGENT_OPENCODE = "OpenCode"
private const val AGENT_CODEX = "Codex"
private const val GITHUB_REPO_URL = "https://github.com/Gitlawb/openclaude.git"
private const val CONNECT_TIMEOUT_MS = 15000
private const val READ_TIMEOUT_MS = 60000
private const val AGENT_DESC = "Agent provisioned during setup"
private const val STATUS_STARTING = "Starting installation..."
private const val STATUS_CHECKING = "Checking prerequisites..."
private const val STATUS_DOWNLOADING = "Downloading "
private const val STATUS_INSTALLING_DEPS = "Installing dependencies..."
private const val STATUS_BUILDING = "Building "
private const val STATUS_CREATING_SCRIPT = "Creating run script..."
private const val STATUS_INSTALLED = " installed successfully!"
private const val STATUS_FAILED = "Installation failed: "

private val DIST_CANDIDATES = listOf("dist/cli.js", "dist/index.js", "cli.js", "index.js", "bin/cli.js")

private const val SHIZUKU_SKILL_ID = "shizuku_phone_control"

private val SHIZUKU_SKILL_CONTENT = """
# Shizuku Phone Control Skill

You have Shizuku root-level access on this Android device. You can execute system commands
that normal apps cannot. This skill documents what commands are available for phone control.

## System Settings (settings command)
Settings are stored in three databases: global, system, secure.

### Dark Mode
  settings put secure ui_night_mode 0  (off / light mode)
  settings put secure ui_night_mode 1  (on / dark mode - battery saver)
  settings put secure ui_night_mode 2  (on / dark mode - always)
  To check current: settings get secure ui_night_mode

### Screen Brightness
  settings put system screen_brightness 0-255
  Auto-brightness: settings put system screen_brightness_mode 0 (manual) / 1 (auto)

### Display
  settings put global window_animation_scale 0.0-1.0
  settings put global transition_animation_scale 0.0-1.0
  settings put global animator_duration_scale 0.0-1.0
  wm density 420  (change DPI)
  wm size 1080x2400  (change resolution)

### Screen Timeout
  settings put system screen_off_timeout 30000  (milliseconds)

### Font Size
  settings put system font_scale 1.0  (default)
  settings put system font_scale 1.15 (large)

## Connectivity (svc command)
  svc wifi enable / svc wifi disable
  svc bluetooth enable / svc bluetooth disable
  svc data enable / svc data disable
  svc nfc enable / svc nfc disable  (if supported)

## Volume Control
  media volume --show --stream 3 --set 10  (media volume 0-15)
  Streams: 0=call, 1=system, 2=ring, 3=media, 4=alarm, 5=notification

## App Management (am / pm commands)
  am start -n com.package.name/.Activity  (open app)
  am start -a android.intent.action.VIEW -d url  (open URL)
  am force-stop com.package.name  (force stop app)
  pm list packages  (list installed packages)
  pm list packages | grep keyword  (search for app)

## Input Simulation (input command)
  input tap x y  (simulate tap)
  input swipe x1 y1 x2 y2  (simulate swipe)
  input keyevent KEYCODE_HOME  (home button)
  input keyevent KEYCODE_BACK  (back button)
  input keyevent KEYCODE_APP_SWITCH  (recent apps)
  input text "hello"  (type text - requires focused field)
  Keycodes: 3=HOME, 4=BACK, 5=CALL, 24=VOLUME_UP, 25=VOLUME_DOWN, 26=POWER, 187=APP_SWITCH

## Screenshot
  screencap /sdcard/Pictures/screenshot.png
  screenrecord /sdcard/Pictures/record.mp4

## Device Info
  getprop  (all properties)
  getprop ro.product.model
  getprop ro.build.version.sdk
  dumpsys battery  (battery status)
  dumpsys window displays  (display info)
  dumpsys connectivity  (network info)

## Date & Time
  settings put global auto_time 0 / 1  (auto time)
  settings put global auto_time_zone 0 / 1  (auto timezone)
  date +%s -s @TIMESTAMP  (set time - requires root)

## Usage Pattern
When the user asks you to control the phone:
1. Determine the appropriate command
2. Use [SUDO: command] to execute it
3. Report the result
""".trimIndent()


private val AGENT_INSTALL_DIRS = mapOf(
    AGENT_HERMES to "hermes",
    AGENT_OPENCLAUDE to "openclaude",
    AGENT_CLAUDE_CODE to "claude-code",
    AGENT_OPENCODE to "opencode",
    AGENT_CODEX to "codex"
)

private val AGENT_WRAPPER_NAMES = mapOf(
    AGENT_CLAUDE_CODE to "claude-code",
    AGENT_OPENCODE to "lildax"
)

private val SYSTEM_PROMPTS = mapOf(
    AGENT_HERMES to """You are Hermes, a local execution agent with Shizuku root access. You can control the Android device.

CAPABILITIES:
1. Execute shell commands: [RUN: command] - regular shell access
2. Execute privileged commands: [SUDO: command] - runs via Shizuku (root/system-level access)

PHONE CONTROL EXAMPLES (use [SUDO: ...]):
- Change dark mode: settings put secure ui_night_mode 2 (dark) / 1 (light)
- Set brightness: settings put system screen_brightness 200
- Toggle WiFi: svc wifi enable / svc wifi disable
- Toggle Bluetooth: svc bluetooth enable / svc bluetooth disable
- Toggle mobile data: svc data enable / svc data disable
- Set volume: media volume --stream 3 --set 10
- Open app: am start -n com.package.name/.ActivityName
- Take screenshot: screencap /sdcard/screenshot.png
- List installed apps: pm list packages
- Get device info: getprop ro.product.model
- Get battery: dumpsys battery
- Simulate tap: input tap x y
- Simulate swipe: input swipe x1 y1 x2 y2
- Simulate key: input keyevent KEYCODE_HOME
- Change display density: wm density 420
- Change display size: wm size 1080x2400

RULES:
- Output [RUN: ...] or [SUDO: ...] when you need to execute something
- Do not wrap the tag in markdown, just the raw tag
- For phone control tasks, prefer [SUDO: ...] since they need system privileges
- If you just want to talk, respond normally""",
    AGENT_OPENCLAUDE to "You are OpenClaude, an open-source Claude integration with full tool use.",
    AGENT_CLAUDE_CODE to "You are Claude Code, a specialized coding agent with codebase awareness.",
    AGENT_OPENCODE to "You are OpenCode, an open-source coding agent specialized in automated code generation and local execution.",
    AGENT_CODEX to "You are Codex, an AI coding agent powered by OpenAI with strong instruction-following capabilities."
)

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

    private val _theme = MutableStateFlow(DEFAULT_THEME)
    val theme: StateFlow<String> = _theme.asStateFlow()

    private val _shizukuEnabled = MutableStateFlow(false)
    val shizukuEnabled: StateFlow<Boolean> = _shizukuEnabled.asStateFlow()

    private val _selectedAgent = MutableStateFlow(
        if (FlavorConfig.presetAgentName.isNotBlank()) FlavorConfig.presetAgentName
        else DEFAULT_AGENT
    )
    val selectedAgent: StateFlow<String> = _selectedAgent.asStateFlow()

    private val _hasFlavorPreset = MutableStateFlow(FlavorConfig.presetAgentName.isNotBlank())
    val hasFlavorPreset: StateFlow<Boolean> = _hasFlavorPreset.asStateFlow()

    val filteredSteps: List<SetupStep>
        get() = if (_hasFlavorPreset.value) SetupStep.entries.filter { it != SetupStep.Agent }
        else SetupStep.entries

    private val _selectedProvider = MutableStateFlow(DEFAULT_PROVIDER)
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _selectedModel = MutableStateFlow("")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _apiKey = MutableStateFlow("")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _isTestingConnection = MutableStateFlow(false)
    val isTestingConnection: StateFlow<Boolean> = _isTestingConnection.asStateFlow()

    private val _testConnectionSuccess = MutableStateFlow<Boolean?>(null)
    val testConnectionSuccess: StateFlow<Boolean?> = _testConnectionSuccess.asStateFlow()

    private val _testConnectionError = MutableStateFlow<String?>(null)
    val testConnectionError: StateFlow<String?> = _testConnectionError.asStateFlow()

    data class AgentInstallState(
        val isInstalling: Boolean = false,
        val progress: Float = 0f,
        val status: String = "",
        val isInstalled: Boolean = false
    )

    private val _agentInstallStates = MutableStateFlow(
        mapOf(
            AGENT_HERMES to AgentInstallState(),
            AGENT_OPENCLAUDE to AgentInstallState(),
            AGENT_CLAUDE_CODE to AgentInstallState(),
            AGENT_OPENCODE to AgentInstallState(),
            AGENT_CODEX to AgentInstallState()
        )
    )
    val agentInstallStates: StateFlow<Map<String, AgentInstallState>> = _agentInstallStates.asStateFlow()

    val canAdvance: Boolean
        get() {
            val step = filteredSteps.getOrNull(_currentStep.value) ?: return false
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
            _testConnectionError.value = null

            val model = _selectedModel.value.ifBlank {
                aiProvider.getModels(_selectedProvider.value).firstOrNull() ?: ""
            }

            if (_apiKey.value.isBlank()) {
                _testConnectionSuccess.value = false
                _testConnectionError.value = "API key is blank"
                _isTestingConnection.value = false
                return@launch
            }

            val result = aiProvider.generateContent(
                prompt = "Reply with exactly: ok",
                apiKey = _apiKey.value,
                provider = _selectedProvider.value,
                model = model
            )

            when (result) {
                is AiResult.Success -> {
                    _testConnectionSuccess.value = true
                    _testConnectionError.value = null
                }
                is AiResult.Error -> {
                    _testConnectionSuccess.value = false
                    _testConnectionError.value = result.message
                }
            }
            _isTestingConnection.value = false
        }
    }

    fun installOpenClaude() = installAgent(AGENT_OPENCLAUDE)
    fun installHermes() = installAgent(AGENT_HERMES)
    fun installClaudeCode() = installAgent(AGENT_CLAUDE_CODE)
    fun installOpenCode() = installAgent(AGENT_OPENCODE)
    fun installCodex() = installAgent(AGENT_CODEX)

    fun installAgent(agentName: String) {
        val targetDirName = AGENT_INSTALL_DIRS[agentName] ?: agentName.lowercase().replace(" ", "-")
        val targetDir = File(runtimeManager.agentsDir, targetDirName)
        val binDir = runtimeManager.binDir
        val wrapperName = AGENT_WRAPPER_NAMES[agentName] ?: agentName.lowercase()
        val wrapperFile = File(binDir, wrapperName)

        viewModelScope.launch {
            _agentInstallStates.value = _agentInstallStates.value + (agentName to AgentInstallState(
                isInstalling = true,
                progress = 0f,
                status = STATUS_STARTING,
                isInstalled = false
            ))

            try {
                // Ensure POSIX runtime (BusyBox) is available before agent setup
                runtimeManager.installBusyBox()

                updateInstallState(agentName, status = STATUS_CHECKING)
                withContext(Dispatchers.IO) {
                    localCommandRunner.executeCommand(
                        "mkdir -p ${runtimeManager.agentsDir.absolutePath} ${binDir.absolutePath}"
                    )
                }

                // Try extracting from bundled assets first, fall back to GitHub download
                val installedFromAssets = withContext(Dispatchers.IO) {
                    tryInstallFromAssets(agentName, targetDir, binDir, wrapperName, wrapperFile)
                }

                if (!installedFromAssets) {
                    updateInstallState(agentName, progress = 0.1f, status = "$STATUS_DOWNLOADING$agentName...")

                    if (targetDir.exists()) {
                        targetDir.deleteRecursively()
                    }

                    withContext(Dispatchers.IO) {
                        val zipUrl = GITHUB_REPO_URL
                            .removeSuffix(".git") + "/archive/refs/heads/main.zip"

                        val connection = URL(zipUrl).openConnection()
                        connection.connectTimeout = CONNECT_TIMEOUT_MS
                        connection.readTimeout = READ_TIMEOUT_MS

                        val tempDir = File(runtimeManager.tmpDir, "agent_$targetDirName")
                        tempDir.deleteRecursively()
                        tempDir.mkdirs()

                        ZipInputStream(connection.getInputStream()).use { zis ->
                            var entry = zis.nextEntry
                            while (entry != null) {
                                val entryFile = File(tempDir, entry.name)
                                if (entry.isDirectory) {
                                    entryFile.mkdirs()
                                } else {
                                    entryFile.parentFile?.mkdirs()
                                    entryFile.outputStream().use { output ->
                                        zis.copyTo(output)
                                    }
                                }
                                zis.closeEntry()
                                entry = zis.nextEntry
                            }
                        }

                        val rootDir = tempDir.listFiles()?.firstOrNull { it.isDirectory }
                        if (rootDir != null) {
                            rootDir.copyRecursively(targetDir, overwrite = true)
                        } else {
                            tempDir.copyRecursively(targetDir, overwrite = true)
                        }
                        tempDir.deleteRecursively()
                    }

                    updateInstallState(agentName, progress = 0.4f, status = STATUS_INSTALLING_DEPS)

                    withContext(Dispatchers.IO) {
                        try {
                            localCommandRunner.executeCommand(
                                "cd ${targetDir.absolutePath} && npm install --production 2>/dev/null || true"
                            )
                        } catch (_: Exception) { }
                    }

                    updateInstallState(agentName, progress = 0.7f, status = "$STATUS_BUILDING$agentName...")

                    withContext(Dispatchers.IO) {
                        try {
                            localCommandRunner.executeCommand(
                                "cd ${targetDir.absolutePath} && npm run build 2>/dev/null || true"
                            )
                        } catch (_: Exception) { }
                    }

                    updateInstallState(agentName, progress = 0.9f, status = STATUS_CREATING_SCRIPT)

                    val entryPoint = DIST_CANDIDATES.firstOrNull { File(targetDir, it).exists() } ?: "index.js"

                    val wrapperScript = """
                        #!/system/bin/sh
                        exec node ${targetDir.absolutePath}/${entryPoint} "${'$'}@"
                    """.trimIndent()

                    withContext(Dispatchers.IO) {
                        wrapperFile.parentFile?.mkdirs()
                        wrapperFile.writeText(wrapperScript)
                        makeExecutable(wrapperFile)
                    }
                }

                updateInstallState(agentName, progress = 1f, status = "$agentName$STATUS_INSTALLED", isInstalled = true)

            } catch (e: Exception) {
                updateInstallState(agentName, status = "$STATUS_FAILED${e.message}", isInstalled = false)
            } finally {
                _agentInstallStates.value = _agentInstallStates.value + (agentName to _agentInstallStates.value[agentName]!!.copy(isInstalling = false))
            }
        }
    }

    /**
     * Try to install agent from pre-bundled APK assets (agent.tar.gz).
     * Returns true if extracted from assets, false if no bundled archive found.
     */
    private suspend fun tryInstallFromAssets(
        agentName: String,
        targetDir: File,
        binDir: File,
        wrapperName: String,
        wrapperFile: File
    ): Boolean {
        return try {
            val inputStream = runtimeManager.context.assets.open("agent.tar.gz")
            val archiveFile = File(runtimeManager.tmpDir, "agent_${wrapperName}_bundled.tar.gz")
            archiveFile.parentFile?.mkdirs()
            archiveFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            if (!archiveFile.exists() || archiveFile.length() == 0L) {
                archiveFile.delete()
                return false
            }

            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            targetDir.mkdirs()

            localCommandRunner.executeCommand(
                "tar -xzf ${archiveFile.absolutePath} -C ${targetDir.absolutePath}"
            )

            archiveFile.delete()

            val entryPoint = DIST_CANDIDATES.firstOrNull { File(targetDir, it).exists() }
            if (entryPoint == null) {
                // No entry point found; clean up and fall back to download
                targetDir.deleteRecursively()
                return false
            }

            val wrapperScript = """
                #!/data/data/com.termux/files/usr/bin/bash
                exec node ${targetDir.absolutePath}/${entryPoint} "${'$'}@"
            """.trimIndent()

            wrapperFile.parentFile?.mkdirs()
            wrapperFile.writeText(wrapperScript)
            makeExecutable(wrapperFile)

            true
        } catch (_: Exception) {
            // Assets not found or extraction failed; fall back to download
            false
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

    /**
     * Mark a file as executable. [File.setExecutable] can silently fail on
     * some Android versions due to SELinux policies, so we fall back to
     * shell-level chmod +x if the Java API doesn't stick.
     */
    private fun makeExecutable(file: File) {
        file.setExecutable(true)
        if (!file.canExecute()) {
            try {
                Runtime.getRuntime().exec(arrayOf("chmod", "+x", file.absolutePath)).waitFor()
            } catch (_: Exception) { /* best effort */ }
        }
    }

    fun completeSetup() {
        viewModelScope.launch {
            prefsManager.setThemeMode(_theme.value)
            prefsManager.setShizukuEnabled(_shizukuEnabled.value)
            prefsManager.setSelectedAgent(_selectedAgent.value)
            prefsManager.setSelectedProvider(_selectedProvider.value)
            prefsManager.setSelectedModel(_selectedModel.value)

            prefsManager.setApiKeyForProvider(_selectedProvider.value, _apiKey.value)

            val agentName = _selectedAgent.value
            val sysPrompt = SYSTEM_PROMPTS[agentName] ?: "You are an expert AI assistant."
            val wrapperName = AGENT_WRAPPER_NAMES[agentName] ?: agentName.lowercase()

            val agent = Agent(
                id = agentName.lowercase().replace(" ", "-"),
                name = agentName,
                description = AGENT_DESC,
                systemPrompt = sysPrompt,
                runCommand = File(runtimeManager.binDir, wrapperName).absolutePath
            )
            repository.insertAgent(agent)

            // Auto-install pre-bundled agent for this flavor
            if (FlavorConfig.presetAgentName.isNotBlank() && agentName == FlavorConfig.presetAgentName) {
                installAgent(agentName)
            }

            // Seed default Shizuku skill if not already present
            val existingSkills = repository.getAllSkills().firstOrNull().orEmpty()
            if (existingSkills.none { it.id == SHIZUKU_SKILL_ID }) {
                repository.insertSkill(Skill(
                    id = SHIZUKU_SKILL_ID,
                    name = "Shizuku Phone Control",
                    content = SHIZUKU_SKILL_CONTENT,
                    enabled = _shizukuEnabled.value
                ))
            }
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
