package com.omniclaw.domain.api

import com.omniclaw.domain.models.DownloadState
import kotlinx.coroutines.flow.Flow

interface AgentDownloader {
    fun download(
        url: String,
        destinationFileName: String
    ): Flow<DownloadState>
}
