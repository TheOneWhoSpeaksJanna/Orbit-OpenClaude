package com.example.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.OrbitApplication
import com.example.domain.api.AiProvider
import com.example.domain.api.AiResult
import com.example.domain.model.ChatSession
import com.example.domain.model.Message
import com.example.domain.model.MessageRole
import com.example.domain.repository.OrbitRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.UUID

class ChatViewModel(
    private val repository: OrbitRepository,
    private val aiProvider: AiProvider,
    private val getApiKey: suspend () -> String?
) : ViewModel() {

    private val _currentSession = MutableStateFlow<ChatSession?>(null)
    val currentSession: StateFlow<ChatSession?> = _currentSession.asStateFlow()

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadSession(sessionId: String) {
        viewModelScope.launch {
            repository.getAllSessions().firstOrNull()?.find { it.id == sessionId }?.let {
                _currentSession.value = it
            }
            repository.getMessagesForSession(sessionId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun startNewSession(projectId: String?) {
        viewModelScope.launch {
            val session = ChatSession(
                id = UUID.randomUUID().toString(),
                projectId = projectId,
                title = "New Session",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            repository.insertSession(session)
            _currentSession.value = session
            _messages.value = emptyList()
            loadSession(session.id)
        }
    }

    fun sendMessage(content: String) {
        val session = _currentSession.value ?: return
        viewModelScope.launch {
            val userMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                role = MessageRole.USER,
                content = content,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(userMsg)
            _isLoading.value = true

            val apiKey = getApiKey() ?: ""
            
            // Build full prompt history
            val promptBuilder = StringBuilder()
            _messages.value.forEach { msg ->
                promptBuilder.append("${msg.role.name}: ${msg.content}\n")
            }
            promptBuilder.append("USER: $content\nMODEL: ")

            val result = aiProvider.generateContent(promptBuilder.toString(), apiKey)
            
            val modelText = when (result) {
                is AiResult.Success -> result.text
                is AiResult.Error -> "Error: ${result.message}"
            }
            
            val modelMsg = Message(
                id = UUID.randomUUID().toString(),
                sessionId = session.id,
                role = MessageRole.MODEL,
                content = modelText,
                timestamp = System.currentTimeMillis()
            )
            repository.insertMessage(modelMsg)
            _isLoading.value = false
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val application = checkNotNull(extras[APPLICATION_KEY]) as OrbitApplication
                return ChatViewModel(
                    application.container.repository,
                    application.container.aiProvider,
                    { application.container.prefsManager.geminiApiKey.firstOrNull() }
                ) as T
            }
        }
    }
}
