package com.omniclaw.data.api.providers

import com.omniclaw.core.config.ApiConfig
import com.omniclaw.domain.api.AiEvent
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.api.AiResult
import com.omniclaw.domain.api.ProviderMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/**
 * Groq provider — OpenAI-compatible ultra-fast inference.
 *
 * Docs: https://console.groq.com/docs
 * Free API keys are available at https://console.groq.com.
 */
class GroqProvider(private val httpClient: OkHttpClient) : AiProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun generateContentStream(
        sessionId: String?,
        prompt: String,
        apiKey: String,
        provider: String,
        model: String
    ): Flow<AiEvent> = flow {
        val result = generateContent(prompt, apiKey, provider, model)
        when (result) {
            is AiResult.Success -> emit(AiEvent.Done(result.text))
            is AiResult.Error -> emit(AiEvent.Error(result.message))
        }
    }

    override suspend fun generateContent(
        prompt: String, apiKey: String, provider: String, model: String
    ): AiResult = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) return@withContext AiResult.Error("API Key is missing.")

            val requestModel = if (model.isNotBlank()) model else ApiConfig.GROQ_DEFAULT_MODEL

            val jsonBody = JSONObject().apply {
                put("model", requestModel)
                val messages = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                }
                put("messages", messages)
            }

            val request = Request.Builder()
                .url(ApiConfig.GROQ_CHAT_URL)
                .header("Authorization", "Bearer $apiKey")
                .post(jsonBody.toString().toRequestBody(jsonMediaType))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.optJSONArray("choices")
                val text = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                if (!text.isNullOrBlank()) {
                    AiResult.Success(text)
                } else {
                    AiResult.Error("Empty response from Groq.")
                }
            } else {
                AiResult.Error("HTTP Error ${response.code}: ${responseBody ?: response.message}")
            }
        } catch (e: Exception) {
            AiResult.Error("Network Error: ${e.message}")
        }
    }

    override suspend fun createSession(sessionId: String, systemPrompt: String?) { }
    override suspend fun deleteSession(sessionId: String) { }

    override fun getModels(providerName: String): List<String> = metadata.models

    override val metadata: ProviderMetadata = ProviderMetadata(
        name = "Groq",
        displayName = "Groq",
        models = listOf(
            ApiConfig.GROQ_DEFAULT_MODEL,
            "llama-3.1-8b-instant",
            "mixtral-8x7b-32768"
        ),
        supportsStreaming = true,
        requiresApiKey = true,
        defaultModel = ApiConfig.GROQ_DEFAULT_MODEL
    )

    override suspend fun testConnection(provider: String, apiKey: String, model: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) return@withContext false
                val request = Request.Builder()
                    .url(ApiConfig.GROQ_MODELS_URL)
                    .header("Authorization", "Bearer $apiKey")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                response.isSuccessful.also { response.close() }
            } catch (_: Exception) {
                false
            }
        }
}
