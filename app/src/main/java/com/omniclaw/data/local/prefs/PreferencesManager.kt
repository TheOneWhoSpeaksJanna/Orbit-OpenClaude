package com.omniclaw.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class DownloadProgress(
    val url: String,
    val filePath: String,
    val bytesDownloaded: Long,
    val version: String
)

val Context.dataStore by preferencesDataStore("orbit_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_ONBOARDING_COMPLETE = booleanPreferencesKey("is_onboarding_complete")
        val SHIZUKU_ENABLED = booleanPreferencesKey("shizuku_enabled")
        val SELECTED_AGENT = stringPreferencesKey("selected_agent")
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")

        val AGENT_PERMISSION_LEVEL = stringPreferencesKey("agent_permission_level")
        val AGENT_RULES_ALLOWED = stringPreferencesKey("agent_rules_allowed")
        val AGENT_RULES_ASK = stringPreferencesKey("agent_rules_ask")
        val AGENT_RULES_DENIED = stringPreferencesKey("agent_rules_denied")

        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val DEEPSEEK_API_KEY = stringPreferencesKey("deepseek_api_key")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        // For Ollama, the "key" slot stores the base URL of the Ollama server
        // (default http://localhost:11434). Ollama does not require auth.
        val OLLAMA_BASE_URL = stringPreferencesKey("ollama_base_url")

        val DOWNLOAD_URL = stringPreferencesKey("download_url")
        val DOWNLOAD_FILE = stringPreferencesKey("download_file")
        val DOWNLOAD_BYTES = longPreferencesKey("download_bytes")
        val DOWNLOAD_VERSION = stringPreferencesKey("download_version")

        private const val PROVIDER_GEMINI = "gemini"
        private const val PROVIDER_OPENAI = "openai"
        private const val PROVIDER_CLAUDE = "claude"
        private const val PROVIDER_OPENROUTER = "openrouter"
        private const val PROVIDER_DEEPSEEK = "deepseek"
        private const val PROVIDER_GROQ = "groq"
        private const val PROVIDER_OLLAMA = "ollama"
    }

    val themeMode: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[THEME_MODE]
    }

    val isOnboardingComplete: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_ONBOARDING_COMPLETE] ?: false
    }

    val shizukuEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[SHIZUKU_ENABLED] ?: false
    }

    val selectedAgent: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_AGENT]
    }

    val selectedProvider: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_PROVIDER]
    }

    val selectedModel: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[SELECTED_MODEL]
    }

    val agentPermissionLevel: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AGENT_PERMISSION_LEVEL] ?: "NORMAL"
    }

    val agentRulesAllowed: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AGENT_RULES_ALLOWED] ?: ""
    }

    val agentRulesAsk: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AGENT_RULES_ASK] ?: ""
    }

    val agentRulesDenied: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[AGENT_RULES_DENIED] ?: ""
    }

    val geminiApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[GEMINI_API_KEY]
    }

    val openAiApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[OPENAI_API_KEY]
    }

    val claudeApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[CLAUDE_API_KEY]
    }

    val openRouterApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[OPENROUTER_API_KEY]
    }

    val deepSeekApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[DEEPSEEK_API_KEY]
    }

    val groqApiKey: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[GROQ_API_KEY]
    }

    /**
     * Ollama base URL (no auth required). Returns null when unset, in which
     * case the Ollama provider falls back to `http://localhost:11434`.
     */
    val ollamaBaseUrl: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[OLLAMA_BASE_URL]
    }

    fun getApiKeyForProvider(provider: String): Flow<String?> {
        return when (provider.lowercase()) {
            PROVIDER_GEMINI -> geminiApiKey
            PROVIDER_OPENAI -> openAiApiKey
            PROVIDER_CLAUDE -> claudeApiKey
            PROVIDER_OPENROUTER -> openRouterApiKey
            PROVIDER_DEEPSEEK -> deepSeekApiKey
            PROVIDER_GROQ -> groqApiKey
            PROVIDER_OLLAMA -> ollamaBaseUrl
            else -> geminiApiKey
        }
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { prefs -> prefs[THEME_MODE] = mode }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs -> prefs[IS_ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setShizukuEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[SHIZUKU_ENABLED] = enabled }
    }

    suspend fun setSelectedAgent(agentId: String) {
        context.dataStore.edit { prefs -> prefs[SELECTED_AGENT] = agentId }
    }

    suspend fun setSelectedProvider(provider: String) {
        context.dataStore.edit { prefs -> prefs[SELECTED_PROVIDER] = provider }
    }

    suspend fun setSelectedModel(model: String) {
        context.dataStore.edit { prefs -> prefs[SELECTED_MODEL] = model }
    }

    suspend fun setAgentPermissionLevel(level: String) {
        context.dataStore.edit { prefs -> prefs[AGENT_PERMISSION_LEVEL] = level }
    }

    suspend fun setAgentRulesAllowed(rules: String) {
        context.dataStore.edit { prefs -> prefs[AGENT_RULES_ALLOWED] = rules }
    }

    suspend fun setAgentRulesAsk(rules: String) {
        context.dataStore.edit { prefs -> prefs[AGENT_RULES_ASK] = rules }
    }

    suspend fun setAgentRulesDenied(rules: String) {
        context.dataStore.edit { prefs -> prefs[AGENT_RULES_DENIED] = rules }
    }

    suspend fun setGeminiApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[GEMINI_API_KEY] = key }
    }

    suspend fun setOpenAiApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[OPENAI_API_KEY] = key }
    }

    suspend fun setClaudeApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[CLAUDE_API_KEY] = key }
    }

    suspend fun setOpenRouterApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[OPENROUTER_API_KEY] = key }
    }

    suspend fun setDeepSeekApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[DEEPSEEK_API_KEY] = key }
    }

    suspend fun setGroqApiKey(key: String) {
        context.dataStore.edit { prefs -> prefs[GROQ_API_KEY] = key }
    }

    suspend fun setOllamaBaseUrl(url: String) {
        context.dataStore.edit { prefs -> prefs[OLLAMA_BASE_URL] = url }
    }

    suspend fun setApiKeyForProvider(provider: String, key: String) {
        when (provider.lowercase()) {
            PROVIDER_GEMINI -> setGeminiApiKey(key)
            PROVIDER_OPENAI -> setOpenAiApiKey(key)
            PROVIDER_CLAUDE -> setClaudeApiKey(key)
            PROVIDER_OPENROUTER -> setOpenRouterApiKey(key)
            PROVIDER_DEEPSEEK -> setDeepSeekApiKey(key)
            PROVIDER_GROQ -> setGroqApiKey(key)
            PROVIDER_OLLAMA -> setOllamaBaseUrl(key)
            else -> setGeminiApiKey(key)
        }
    }

    suspend fun removeApiKeyForProvider(provider: String) {
        setApiKeyForProvider(provider, "")
    }

    fun getDownloadProgress(): Flow<DownloadProgress?> = context.dataStore.data.map { prefs ->
        val url = prefs[DOWNLOAD_URL] ?: return@map null
        val file = prefs[DOWNLOAD_FILE] ?: return@map null
        val bytes = prefs[DOWNLOAD_BYTES] ?: 0L
        val version = prefs[DOWNLOAD_VERSION] ?: ""
        DownloadProgress(url, file, bytes, version)
    }

    suspend fun setDownloadProgress(url: String, filePath: String, bytes: Long, version: String) {
        context.dataStore.edit { prefs ->
            prefs[DOWNLOAD_URL] = url
            prefs[DOWNLOAD_FILE] = filePath
            prefs[DOWNLOAD_BYTES] = bytes
            prefs[DOWNLOAD_VERSION] = version
        }
    }

    suspend fun clearDownloadProgress() {
        context.dataStore.edit { prefs ->
            prefs.remove(DOWNLOAD_URL)
            prefs.remove(DOWNLOAD_FILE)
            prefs.remove(DOWNLOAD_BYTES)
            prefs.remove(DOWNLOAD_VERSION)
        }
    }
}
