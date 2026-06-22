package com.omniclaw.data.local.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore("orbit_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val IS_ONBOARDING_COMPLETE = booleanPreferencesKey("is_onboarding_complete")
        val SHIZUKU_ENABLED = booleanPreferencesKey("shizuku_enabled")
        val SELECTED_AGENT = stringPreferencesKey("selected_agent")
        val SELECTED_PROVIDER = stringPreferencesKey("selected_provider")
        val SELECTED_MODEL = stringPreferencesKey("selected_model")

        // Per-provider API keys
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val CLAUDE_API_KEY = stringPreferencesKey("claude_api_key")
        val OPENROUTER_API_KEY = stringPreferencesKey("openrouter_api_key")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
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

    // Per-provider API key flows
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

    val githubToken: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[GITHUB_TOKEN]
    }

    // Unified: get API key for the currently selected provider
    fun getApiKeyForProvider(provider: String): Flow<String?> {
        return when (provider.lowercase()) {
            "gemini" -> geminiApiKey
            "openai" -> openAiApiKey
            "claude" -> claudeApiKey
            "openrouter" -> openRouterApiKey
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

    suspend fun setGithubToken(token: String) {
        context.dataStore.edit { prefs -> prefs[GITHUB_TOKEN] = token }
    }

    suspend fun setApiKeyForProvider(provider: String, key: String) {
        when (provider.lowercase()) {
            "gemini" -> setGeminiApiKey(key)
            "openai" -> setOpenAiApiKey(key)
            "claude" -> setClaudeApiKey(key)
            "openrouter" -> setOpenRouterApiKey(key)
            else -> setGeminiApiKey(key)
        }
    }
}
