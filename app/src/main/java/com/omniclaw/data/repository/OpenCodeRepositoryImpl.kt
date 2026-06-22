package com.omniclaw.data.repository

import com.omniclaw.domain.models.AgentCategory
import com.omniclaw.domain.models.DownloadSource
import com.omniclaw.domain.models.DownloadableAgent
import com.omniclaw.domain.repository.OpenCodeRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class OpenCodeRepositoryImpl : OpenCodeRepository {

    private val _catalog = MutableStateFlow(defaultCatalog())
    override fun getAvailableAgents(): Flow<List<DownloadableAgent>> = _catalog.asStateFlow()

    override suspend fun refreshCatalog() {
        // Simulate network fetch delay; real impl would hit an OpenCode API
        delay(800)
        _catalog.value = defaultCatalog()
    }

    override suspend fun getAgentById(id: String): DownloadableAgent? {
        return _catalog.value.find { it.id == id }
    }

    private fun defaultCatalog() = listOf(
        DownloadableAgent(
            id = "oc-bash-automation",
            name = "Bash Automation Suite",
            description = "Advanced shell scripting and workflow automation toolkit for Termux",
            category = AgentCategory.AUTOMATION,
            downloadUrl = "https://opencode.omniclaw.ai/packages/bash-automation-v2.tar.gz",
            iconName = "terminal",
            version = "2.1.0",
            fileSize = 1_024_000L
        ),
        DownloadableAgent(
            id = "oc-code-analyzer",
            name = "Code Analyzer Pro",
            description = "Static analysis and code quality scanning across multiple languages",
            category = AgentCategory.DEVELOPER,
            downloadUrl = "https://opencode.omniclaw.ai/packages/code-analyzer-v1.tar.gz",
            iconName = "code",
            version = "1.3.0",
            fileSize = 2_560_000L
        ),
        DownloadableAgent(
            id = "oc-file-watcher",
            name = "File Watcher Daemon",
            description = "Real-time filesystem monitoring and event-driven action triggers",
            category = AgentCategory.UTILITY,
            downloadUrl = "https://opencode.omniclaw.ai/packages/file-watcher-v1.tar.gz",
            iconName = "folder",
            version = "1.0.2",
            fileSize = 512_000L
        ),
        DownloadableAgent(
            id = "oc-network-scanner",
            name = "Network Scanner",
            description = "Local network discovery, port scanning, and connectivity diagnostics",
            category = AgentCategory.SECURITY,
            downloadUrl = "https://opencode.omniclaw.ai/packages/network-scanner-v1.tar.gz",
            iconName = "network",
            version = "1.1.0",
            fileSize = 768_000L
        ),
        DownloadableAgent(
            id = "oc-data-pipeline",
            name = "Data Pipeline Engine",
            description = "Extract, transform, and load data with scheduled batch processing",
            category = AgentCategory.ANALYTICS,
            downloadUrl = "https://opencode.omniclaw.ai/packages/data-pipeline-v2.tar.gz",
            iconName = "analytics",
            version = "2.0.0",
            fileSize = 3_200_000L
        ),
        DownloadableAgent(
            id = "oc-custom-hooks",
            name = "Custom Hook Runner",
            description = "Define and execute custom business logic hooks triggered by system events",
            category = AgentCategory.CUSTOM_LOGIC,
            downloadUrl = "https://opencode.omniclaw.ai/packages/custom-hooks-v1.tar.gz",
            iconName = "hook",
            version = "1.0.5",
            fileSize = 384_000L
        )
    )
}
