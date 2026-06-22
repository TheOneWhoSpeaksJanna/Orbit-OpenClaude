package com.omniclaw.data.api.providers

import com.omniclaw.domain.api.AiEvent
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.api.AiResult
import com.omniclaw.domain.api.ProviderMetadata
import com.omniclaw.domain.models.DetailedModelInfo
import kotlinx.coroutines.flow.Flow

class AiProviderSelector : AiProvider {
    
    private val providers = mutableMapOf<String, AiProvider>()

    init {
        providers["Gemini"] = GeminiProvider()
        providers["OpenAI"] = OpenAIProvider()
        providers["Claude"] = ClaudeProvider()
        providers["OpenRouter"] = OpenRouterProvider()
    }

    private fun getProvider(name: String): AiProvider {
        return providers[name] ?: providers["Gemini"]!!
    }

    override fun generateContentStream(sessionId: String?, prompt: String, apiKey: String, provider: String, model: String): Flow<AiEvent> {
        return getProvider(provider).generateContentStream(sessionId, prompt, apiKey, provider, model)
    }

    override suspend fun generateContent(prompt: String, apiKey: String, provider: String, model: String): AiResult {
        return getProvider(provider).generateContent(prompt, apiKey, provider, model)
    }

    override suspend fun testConnection(provider: String, apiKey: String, model: String): Boolean {
        return getProvider(provider).testConnection(provider, apiKey, model)
    }

    override suspend fun createSession(sessionId: String, systemPrompt: String?) {
        providers.values.forEach { it.createSession(sessionId, systemPrompt) }
    }

    override suspend fun deleteSession(sessionId: String) {
        providers.values.forEach { it.deleteSession(sessionId) }
    }

    override fun getModels(providerName: String): List<String> = getProvider(providerName).getModels(providerName)

    override suspend fun fetchDetailedModels(providerName: String, apiKey: String): List<DetailedModelInfo> =
        getProvider(providerName).fetchDetailedModels(providerName, apiKey)

    override val metadata: ProviderMetadata = ProviderMetadata(
        name = "All",
        displayName = "All Providers",
        models = emptyList(),
        supportsStreaming = true,
        requiresApiKey = true
    )
}
