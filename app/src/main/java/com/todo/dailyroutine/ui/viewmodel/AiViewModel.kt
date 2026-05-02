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
            val tools = toolController.getToolSchemas()
            
            var currentResult = aiRepository.chat(userContent, config, tools = tools)
            var iterations = 0
            
            while (currentResult.isSuccess && iterations < 3) {
                val raw = currentResult.getOrThrow()
                val apiKey = config?.apiKey ?: ""
                
                val toolCalls = aiRepository.extractToolCalls(raw, apiKey)
                if (toolCalls.isEmpty()) {
                    val content = aiRepository.extractContent(raw, apiKey)
                    val aiMessage = ChatMessage("assistant", content)
                    _uiState.value = _uiState.value.copy(
                        chatHistory = _uiState.value.chatHistory + aiMessage,
                        loading = false
                    )
                    contextManager.processNewMessage(userId, "assistant", content)
                    break
                }

                // Execute tools
                val toolResults = toolController.handleToolCalls(toolCalls)
                
                // For simplicity in this demo, we add the results as a system message and ask AI to summarize
                val resultsText = toolResults.joinToString("\n") { it.content }
                val followUpPrompt = "System: Tools executed. Results:\n$resultsText\nSummarize the action taken for the user."
                
                currentResult = aiRepository.chat(followUpPrompt, config)
                iterations++
            }

            if (currentResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = currentResult.exceptionOrNull()?.message
                )
            }
        }
    }

    fun updateConfigField(provider: String, apiKey: String? = null, baseUrl: String? = null, model: String? = null) {
        val currentConfigs = _uiState.value.apiConfigs.toMutableList()
        
        // Auto-detect provider if apiKey is provided
        val detectedProvider = apiKey?.let { aiRepository.detectProvider(it) } ?: provider
        val targetProvider = if (apiKey != null) detectedProvider else provider

        val index = currentConfigs.indexOfFirst { it.providerName == targetProvider }
        
        val updatedConfig = if (index != -1) {
            currentConfigs[index].copy(
                apiKey = apiKey ?: currentConfigs[index].apiKey,
                baseUrl = baseUrl ?: currentConfigs[index].baseUrl,
                model = model ?: currentConfigs[index].model
            )
        } else {
            UserApiConfig(
                providerName = targetProvider,
                apiKey = apiKey ?: "",
                baseUrl = baseUrl ?: "",
                model = model ?: "",
                isActive = false
            )
        }
        
        if (index != -1) currentConfigs[index] = updatedConfig else currentConfigs.add(updatedConfig)
        _uiState.value = _uiState.value.copy(apiConfigs = currentConfigs)
    }

    fun testConnection(provider: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, testResult = null)
            val config = _uiState.value.apiConfigs.find { it.providerName == provider } ?: return@launch
            val result = aiRepository.testConnection(config)
            
            _uiState.value = _uiState.value.copy(
                loading = false,
                testResult = if (result.isSuccess) "Connection Success" else "Connection Failed: ${result.exceptionOrNull()?.message}"
            )
        }
    }

    fun saveAndActivateConfig(provider: String) {
        viewModelScope.launch {
            val config = _uiState.value.apiConfigs.find { it.providerName == provider } ?: return@launch
            // Deactivate others
            val updatedConfigs = _uiState.value.apiConfigs.map { it.copy(isActive = it.providerName == provider) }
            updatedConfigs.forEach { aiConfigRepository.saveConfig(it) }
            loadConfigs()
        }
    }

    fun fetchAvailableModels(provider: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            val config = _uiState.value.apiConfigs.find { it.providerName == provider } ?: return@launch
            val models = aiRepository.fetchModels(config)
            _uiState.value = _uiState.value.copy(
                availableModels = models,
                loading = false
            )
        }
    }

    fun selectModel(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
        viewModelScope.launch {
            _uiState.value.activeConfig?.let { active ->
                aiConfigRepository.saveConfig(active.copy(model = model))
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
