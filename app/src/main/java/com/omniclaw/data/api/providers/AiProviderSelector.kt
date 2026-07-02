package com.omniclaw.data.api.providers

import com.omniclaw.domain.api.AiEvent
import com.omniclaw.domain.api.AiProvider
import com.omniclaw.domain.api.AiResult
import com.omniclaw.domain.api.ProviderMetadata
import com.omniclaw.domain.models.DetailedModelInfo
import kotlinx.coroutines.flow.Flow
import okhttp3.OkHttpClient

/**
 * Routes calls to the active provider implementation.
 *
 * The `providers` map keys MUST match the provider name strings used in
 * [com.omniclaw.ui.viewmodels.ProvidersViewModel.KNOWN_PROVIDERS] and in
 * [com.omniclaw.data.local.prefs.PreferencesManager.getApiKeyForProvider].
 * Mismatches cause silent fallback to Gemini, which historically made
 * DeepSeek/Groq/Ollama unusable even though the UI listed them.
 */
class AiProviderSelector(okHttpClient: OkHttpClient) : AiProvider {

    private val providers: Map<String, AiProvider> = mapOf(
        "Gemini" to GeminiProvider(okHttpClient),
        "OpenAI" to OpenAIProvider(okHttpClient),
        "Claude" to ClaudeProvider(okHttpClient),
        "OpenRouter" to OpenRouterProvider(okHttpClient),
        "DeepSeek" to DeepSeekProvider(okHttpClient),
        "Groq" to GroqProvider(okHttpClient),
        "Ollama" to OllamaProvider(okHttpClient)
    )

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
