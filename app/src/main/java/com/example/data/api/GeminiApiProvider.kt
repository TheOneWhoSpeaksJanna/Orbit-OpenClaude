package com.example.data.api

import com.example.domain.api.AiProvider
import com.example.domain.api.AiResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class GeminiRequest(val contents: List<Content>) {
    data class Content(val parts: List<Part>)
    data class Part(val text: String)
}

data class GeminiResponse(val candidates: List<Candidate>?) {
    data class Candidate(val content: Content?)
    data class Content(val parts: List<Part>?)
    data class Part(val text: String?)
}

interface GeminiService {
    @POST("v1beta/models/gemini-1.5-pro-latest:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Header("Content-Type") contentType: String = "application/json",
        @Body request: GeminiRequest
    ): GeminiResponse
}

class GeminiApiProvider : AiProvider {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(MoshiConverterFactory.create())
        .client(OkHttpClient.Builder().build())
        .build()

    private val service = retrofit.create(GeminiService::class.java)

    override fun generateContentStream(prompt: String, apiKey: String): Flow<AiResult> = flow {
        // Basic fallback to non-streaming for the implementation
        val result = generateContent(prompt, apiKey)
        emit(result)
    }

    override suspend fun generateContent(prompt: String, apiKey: String): AiResult {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) {
                    return@withContext AiResult.Error("API Key is missing. Configure it in Settings.")
                }
                
                val request = GeminiRequest(
                    contents = listOf(
                        GeminiRequest.Content(
                            parts = listOf(GeminiRequest.Part(text = prompt))
                        )
                    )
                )
                
                val response = service.generateContent(apiKey = apiKey, request = request)
                val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                
                if (text != null) {
                    AiResult.Success(text)
                } else {
                    AiResult.Error("No valid response from model.")
                }
            } catch (e: Exception) {
                AiResult.Error(e.message ?: "Unknown error occurred")
            }
        }
    }
}
