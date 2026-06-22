package com.omniclaw.domain.repository

import com.omniclaw.domain.models.DownloadableAgent
import kotlinx.coroutines.flow.Flow

interface OpenCodeRepository {
    fun getAvailableAgents(): Flow<List<DownloadableAgent>>
    suspend fun refreshCatalog()
    suspend fun getAgentById(id: String): DownloadableAgent?
}
