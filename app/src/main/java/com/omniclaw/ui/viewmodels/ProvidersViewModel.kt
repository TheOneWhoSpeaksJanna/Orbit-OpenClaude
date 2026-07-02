package com.omniclaw.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.omniclaw.OmniClawApplication
import com.omniclaw.core.config.ApiConfig
import com.omniclaw.data.local.prefs.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
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

private const val CONTENT_TYPE_JSON = "application/json; charset=utf-8"
private const val NO_API_KEY_MSG = "No API key configured"
private const val INVALID_API_KEY = "Invalid API key"
private const val CLIENT_ERROR = "Client error"
private const val SERVER_ERROR = "Server error"
private const val UNEXPECTED_CODE = "Unexpected"
private const val OLLAMA_PROVIDER = "Ollama"

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
        KNOWN_PROVIDERS.map { name ->
            ProviderConfig(name = name, apiKeyConfigured = false)
        }
    )
    val providers: StateFlow<List<ProviderConfig>> = _providers.asStateFlow()

    private val _editingProvider = MutableStateFlow<String?>(null)
    val editingProvider: StateFlow<String?> = _editingProvider.asStateFlow()

    private val _editApiKeyValue = MutableStateFlow("")
    val editApiKeyValue: StateFlow<String> = _editApiKeyValue.asStateFlow()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(ApiConfig.HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(ApiConfig.HEALTH_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = CONTENT_TYPE_JSON.toMediaType()

    init {
        loadApiKeyStatus()
    }

    private fun loadApiKeyStatus() {
        viewModelScope.launch {
            val updated = _providers.value.map { provider ->
                val key = prefsManager.getApiKeyForProvider(provider.name).firstOrNull()
                provider.copy(apiKeyConfigured = !key.isNullOrBlank())
            }
            _providers.value = updated
        }
    }

    fun verifyConnection(providerName: String) {
        viewModelScope.launch {
            if (providerName == OLLAMA_PROVIDER) {
                updateProviderState(providerName, ConnectionState.Verifying)
                // For Ollama, the "key" slot holds the base URL — pass it through.
                val baseUrl = prefsManager.getApiKeyForProvider(providerName).firstOrNull().orEmpty()
                val result = withContext(Dispatchers.IO) {
                    performHealthCheck(providerName, baseUrl)
                }
                updateProviderState(providerName, result)
                return@launch
            }

            val keyFlow = prefsManager.getApiKeyForProvider(providerName)
            val key = keyFlow.firstOrNull()

            if (key.isNullOrBlank()) {
                updateProviderState(providerName, ConnectionState.Unauthorized(NO_API_KEY_MSG))
                return@launch
            }

            updateProviderState(providerName, ConnectionState.Verifying)

            val result = withContext(Dispatchers.IO) {
                performHealthCheck(providerName, key)
            }

            updateProviderState(providerName, result)
        }
    }

    fun startEditApiKey(providerName: String) {
        viewModelScope.launch {
            val currentKey = prefsManager.getApiKeyForProvider(providerName).firstOrNull() ?: ""
            _editApiKeyValue.value = currentKey
            _editingProvider.value = providerName
        }
    }

    fun saveApiKey() {
        val provider = _editingProvider.value ?: return
        viewModelScope.launch {
            prefsManager.setApiKeyForProvider(provider, _editApiKeyValue.value)
            _editingProvider.value = null
            _editApiKeyValue.value = ""
            loadApiKeyStatus()
        }
    }

    fun removeApiKey() {
        val provider = _editingProvider.value ?: return
        viewModelScope.launch {
            prefsManager.removeApiKeyForProvider(provider)
            _editingProvider.value = null
            _editApiKeyValue.value = ""
            loadApiKeyStatus()
        }
    }

    fun cancelEditApiKey() {
        _editingProvider.value = null
        _editApiKeyValue.value = ""
    }

    fun updateEditApiKey(value: String) {
        _editApiKeyValue.value = value
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
                    ConnectionState.Unauthorized("$INVALID_API_KEY (${response.code})")
                }
                in 400..499 -> {
                    response.close()
                    ConnectionState.Error("$CLIENT_ERROR: ${response.code}")
                }
                in 500..599 -> {
                    response.close()
                    ConnectionState.Error("$SERVER_ERROR: ${response.code}")
                }
                else -> {
                    response.close()
                    ConnectionState.Error("$UNEXPECTED_CODE: ${response.code}")
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

    /**
     * Build a health-check request per provider. All URLs come from [ApiConfig]
     * so adding/changing a provider only needs an [ApiConfig] entry + a branch
     * here — no scattered string literals anywhere else in the codebase.
     */
    private fun buildHealthCheckRequest(name: String, apiKey: String): Request {
        return when (name) {
            "Claude" -> Request.Builder()
                .url(ApiConfig.CLAUDE_API_URL)
                .header("x-api-key", apiKey)
                .header("anthropic-version", ApiConfig.CLAUDE_API_VERSION)
                .header("content-type", "application/json")
                .post(CLAUDE_HEALTH_CHECK_BODY.toRequestBody(jsonMediaType))
                .build()

            "OpenAI" -> Request.Builder()
                .url(ApiConfig.OPENAI_MODELS_URL)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            "Gemini" -> Request.Builder()
                .url("${ApiConfig.GEMINI_BASE_URL}v1beta/models?key=$apiKey")
                .get()
                .build()

            "OpenRouter" -> Request.Builder()
                .url(ApiConfig.OPENROUTER_KEY_CHECK_URL)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            "DeepSeek" -> Request.Builder()
                .url(ApiConfig.DEEPSEEK_MODELS_URL)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            "Groq" -> Request.Builder()
                .url(ApiConfig.GROQ_MODELS_URL)
                .header("Authorization", "Bearer $apiKey")
                .get()
                .build()

            OLLAMA_PROVIDER -> {
                // apiKey holds the base URL when set, otherwise default.
                val base = apiKey.trim().trimEnd('/').ifBlank { ApiConfig.OLLAMA_DEFAULT_BASE_URL }
                Request.Builder()
                    .url("$base${ApiConfig.OLLAMA_TAGS_PATH}")
                    .get()
                    .build()
            }

            else -> throw IllegalArgumentException("Unknown provider: $name")
        }
    }

    private fun updateProviderState(providerName: String, state: ConnectionState) {
        _providers.value = _providers.value.map {
            if (it.name == providerName) it.copy(connectionState = state) else it
        }
    }

    companion object {
        /** All provider names the UI knows about. MUST match AiProviderSelector keys. */
        val KNOWN_PROVIDERS = listOf(
            "Claude", "OpenAI", "Gemini", "OpenRouter",
            "DeepSeek", "Groq", "Ollama"
        )
        private const val CLAUDE_HEALTH_CHECK_BODY =
            """{"model":"${ApiConfig.CLAUDE_HEALTH_CHECK_MODEL}","max_tokens":1,"messages":[{"role":"user","content":"."}]}"""

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OmniClawApplication
                return ProvidersViewModel(application.container.prefsManager) as T
            }
        }
    }
}
