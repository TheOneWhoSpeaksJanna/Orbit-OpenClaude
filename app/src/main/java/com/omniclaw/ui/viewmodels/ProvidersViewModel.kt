package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.data.local.prefs.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class ProviderConfig(
    val name: String,
    val apiKeyConfigured: Boolean,
    val connectionState: ConnectionState = ConnectionState.Idle
)

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Verifying : ConnectionState()
    data object Connected : ConnectionState()
    data class Unauthorized(val message: String) : ConnectionState()
    data object Offline : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

class ProvidersViewModel(
    private val prefsManager: PreferencesManager
) : ViewModel() {

    private val _providers = MutableStateFlow(
        listOf("Claude", "OpenAI", "Gemini", "OpenRouter", "DeepSeek", "Groq", "Ollama").map { name ->
            ProviderConfig(name = name, apiKeyConfigured = false)
        }
    )
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    init {
        loadApiKeyStatus()
    }

    private fun loadApiKeyStatus() {
        viewModelScope.launch {
            val updated = _providers.value.map { provider ->
                val key = prefsManager.getApiKeyForProvider(provider.name)
                provider.copy(apiKeyConfigured = !key.isNullOrBlank())
            }
            _providers.value = updated
        }
    }

    fun verifyConnection(providerName: String) {
        viewModelScope.launch {
            // Ollama is localhost — no API key needed
            if (providerName == "Ollama") {
                updateProviderState(providerName, ConnectionState.Verifying)
                val result = withContext(Dispatchers.IO) {
                    performHealthCheck(providerName, "")
                }
                updateProviderState(providerName, result)
                return@launch
            }

            val key = prefsManager.getApiKeyForProvider(providerName)

            if (key.isNullOrBlank()) {
                updateProviderState(providerName, ConnectionState.Unauthorized("No API key configured"))
                return@launch
            }

            updateProviderState(providerName, ConnectionState.Verifying)

            val result = withContext(Dispatchers.IO) {
                performHealthCheck(providerName, key)
            }

            updateProviderState(providerName, result)
        }
    }

    private suspend fun performHealthCheck(name: String, apiKey: String): ConnectionState {
        return try {
            val request = buildHealthCheckRequest(name, apiKey)
            val response = httpClient.newCall(request).execute()

            return when (response.code) {
                in 200..299 -> {
                    response.close()
                    ConnectionState.Connected
                }
                401, 403 -> {
                    response.close()
                    ConnectionState.Unauthorized("Invalid API key (${response.code})")
                }
                in 400..499 -> {
                    response.close()
                    ConnectionState.Error("Client error: ${response.code}")
                }
                in 500..599 -> {
                    response.close()
                    ConnectionState.Error("Server error: ${response.code}")
                }
                else -> {
                    response.close()
                    ConnectionState.Error("Unexpected: ${response.code}")
                }
            }
        } catch (e: SocketTimeoutException) {
            ConnectionState.Offline
        } catch (e: ConnectException) {
            ConnectionState.Offline
        } catch (e: UnknownHostException) {
            ConnectionState.Offline
        } catch (e: Exception) {
            ConnectionState.Error(e.message ?: "Unknown error")
        }
    }

    private fun buildHealthCheckRequest(name: String, apiKey: String): Request {
        return when (name) {
            "Claude" -> Request.Builder()
                .url("https://api.anthropic.com/v1/messages")
                .header("x-api-key", apiKey)
                .header("anthropic-version", "2023-06-01")
                .header("content-type", "application/json")
                .post(
                    """{"model":"claude-sonnet-4-20250514","max_tokens":1,"messages":[{"role":"user","content":"."}]}"""
                        .toRequestBody(jsonMediaType)
                )
                .build()

            "OpenAI" -> Request.Builder()
                .url("https://api.openai.com/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            "Gemini" -> Request.Builder()
                .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
                .get()
                .build()

            "OpenRouter" -> Request.Builder()
                .url("https://openrouter.ai/api/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            "DeepSeek" -> Request.Builder()
                .url("https://api.deepseek.com/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            "Groq" -> Request.Builder()
                .url("https://api.groq.com/openai/v1/models")
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            "Ollama" -> Request.Builder()
                .url("http://localhost:11434/api/tags")
                .get()
                .build()

            else -> throw IllegalArgumentException("Unknown provider: $name")
        }
    }

    private fun updateProviderState(providerName: String, state: ConnectionState) {
        _providers.value = _providers.value.map {
            if (it.name == providerName) it.copy(connectionState = state) else it
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return ProvidersViewModel(application.container.prefsManager) as T
            }
        }
    }
}
