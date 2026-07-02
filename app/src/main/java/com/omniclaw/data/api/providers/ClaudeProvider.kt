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

class ClaudeProvider(private val httpClient: OkHttpClient) : AiProvider {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun generateContentStream(sessionId: String?, prompt: String, apiKey: String, provider: String, model: String): Flow<AiEvent> = flow {
        val result = generateContent(prompt, apiKey, provider, model)
        when (result) {
            is AiResult.Success -> emit(AiEvent.Done(result.text))
            is AiResult.Error -> emit(AiEvent.Error(result.message))
        }
    }

    override suspend fun generateContent(prompt: String, apiKey: String, provider: String, model: String): AiResult {
        return withContext(Dispatchers.IO) {
            try {
                if (apiKey.isBlank()) return@withContext AiResult.Error("API Key is missing.")

                val requestModel = if (model.isNotBlank()) model else ApiConfig.CLAUDE_DEFAULT_MODEL

                val jsonBody = JSONObject().apply {
                    put("model", requestModel)
                    put("max_tokens", MAX_TOKENS)
                    val messages = JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", prompt)
                        })
                    }
                    put("messages", messages)
                }

                val request = Request.Builder()
                    .url(ApiConfig.CLAUDE_API_URL)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ApiConfig.CLAUDE_API_VERSION)
                    .header("content-type", "application/json")
                    .post(jsonBody.toString().toRequestBody(jsonMediaType))
                    .build()

                val response = httpClient.newCall(request).execute()
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val jsonResponse = JSONObject(responseBody)
                    val contents = jsonResponse.optJSONArray("content")
                    val text = contents?.optJSONObject(0)?.optString("text")

                    if (!text.isNullOrBlank()) {
                        AiResult.Success(text)
                    } else {
                        AiResult.Error("Empty response from Claude.")
                    }
                } else {
                    AiResult.Error("HTTP Error ${response.code}: ${responseBody ?: response.message}")
                }
            } catch (e: Exception) {
                AiResult.Error("Network Error: ${e.message}")
            }
        }
    }

    override suspend fun createSession(sessionId: String, systemPrompt: String?) { }
    override suspend fun deleteSession(sessionId: String) { }

    override fun getModels(providerName: String): List<String> = metadata.models

    override val metadata: ProviderMetadata = ProviderMetadata(
        name = "Claude",
        displayName = "Anthropic Claude",
        models = listOf(
            ApiConfig.CLAUDE_DEFAULT_MODEL,
            "claude-haiku-3-5-20241022",
            "claude-opus-4-20250514"
        ),
        supportsStreaming = true,
        requiresApiKey = true,
        defaultModel = ApiConfig.CLAUDE_DEFAULT_MODEL
    )

    override suspend fun testConnection(provider: String, apiKey: String, model: String): Boolean {
        return withContext(Dispatchers.IO) {
            val result = generateContent("Hi", apiKey, provider, model)
            result is AiResult.Success
        }
    }

    companion object {
        private const val MAX_TOKENS = 1024
    }
}
