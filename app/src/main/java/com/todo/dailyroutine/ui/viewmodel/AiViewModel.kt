package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.notifications.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.todo.dailyroutine.data.model.ChatMessage
import com.todo.dailyroutine.data.model.UserApiConfig
import com.todo.dailyroutine.data.repository.AiConfigRepository
import com.todo.dailyroutine.domain.ai.AiContextManager
import com.google.gson.Gson
import com.todo.dailyroutine.data.model.AiToolCall

data class AiUiState(
    val loading: Boolean = false,
    val prompt: String = "",
    val chatHistory: List<ChatMessage> = emptyList(),
    val reminderSummary: String = "Flow intelligence active.",
    val error: String? = null,
    val apiConfigs: List<UserApiConfig> = emptyList(),
    val activeConfig: UserApiConfig? = null,
    val availableModels: List<String> = emptyList(),
    val selectedModel: String = "default",
    val testResult: String? = null
)

class AiViewModel(
    private val aiRepository: AiRepository,
    private val aiConfigRepository: AiConfigRepository,
    private val notificationScheduler: NotificationScheduler,
    private val contextManager: AiContextManager,
    private val toolController: com.todo.dailyroutine.domain.ai.AiToolController
) : ViewModel() {

    init {
        loadConfigs()
    }

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    private fun loadConfigs() {
        viewModelScope.launch {
            aiConfigRepository.getConfigs().onSuccess { configs ->
                val active = configs.find { it.isActive }
                _uiState.value = _uiState.value.copy(
                    apiConfigs = configs,
                    activeConfig = active,
                    availableModels = active?.let { aiRepository.fetchModels(it) } ?: emptyList(),
                    selectedModel = active?.model ?: "default"
                )
            }
        }
    }

    fun onPromptChanged(prompt: String) {
        _uiState.value = _uiState.value.copy(prompt = prompt)
    }

    fun sendMessage() {
        val userContent = _uiState.value.prompt.trim()
        if (userContent.isBlank()) return

        val newMessage = ChatMessage("user", userContent)
        _uiState.value = _uiState.value.copy(
            chatHistory = _uiState.value.chatHistory + newMessage,
            prompt = "",
            loading = true
        )

        viewModelScope.launch {
            val userId = "user_default"
            contextManager.processNewMessage(userId, "user", userContent)
            
            val config = _uiState.value.activeConfig
            aiRepository.chat(userContent, config).onSuccess { content ->
                val aiMessage = ChatMessage("assistant", content)
                _uiState.value = _uiState.value.copy(
                    chatHistory = _uiState.value.chatHistory + aiMessage,
                    loading = false
                )
                contextManager.processNewMessage(userId, "assistant", content)
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = it.message
                )
            }
        }
    }
}

class AiViewModelFactory(
    private val repository: AiRepository,
    private val configRepository: AiConfigRepository,
    private val scheduler: NotificationScheduler,
    private val contextManager: AiContextManager,
    private val toolController: com.todo.dailyroutine.domain.ai.AiToolController
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AiViewModel(repository, configRepository, scheduler, contextManager, toolController) as T
    }
}
