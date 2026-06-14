package com.example.domain.api

import kotlinx.coroutines.flow.Flow

interface AiProvider {
    fun generateContentStream(prompt: String, apiKey: String): Flow<AiResult>
    suspend fun generateContent(prompt: String, apiKey: String): AiResult
}

sealed class AiResult {
    data class Success(val text: String) : AiResult()
    data class Error(val message: String) : AiResult()
}
