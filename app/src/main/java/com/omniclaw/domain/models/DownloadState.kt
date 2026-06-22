package com.omniclaw.domain.models

sealed class DownloadState {
    data object Idle : DownloadState()
    data object Requesting : DownloadState()
    data class Transferring(val progress: Float) : DownloadState()
    data class Complete(val filePath: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
