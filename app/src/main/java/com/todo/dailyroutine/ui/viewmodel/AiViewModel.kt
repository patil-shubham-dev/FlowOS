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
        loadChatHistory()
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
                    availableModels = active?.let { getModelsForProvider(it.providerName) } ?: emptyList(),
                    selectedModel = active?.model ?: "default"
                )
            }
        }
    }

    private fun loadChatHistory() {
        // Implementation for chat history recovery
    }

    fun getModelsForProvider(provider: String): List<String> {
        return when (provider) {
            "Claude" -> listOf("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
            "OpenAI" -> listOf("gpt-4-turbo", "gpt-4", "gpt-3.5-turbo")
            "Groq" -> listOf("llama3-70b-8192", "llama3-8b-8219", "mixtral-8x7b-32768")
            else -> listOf("default")
        }
    }

    fun autoDetectProvider(apiKey: String): UserApiConfig {
        val provider = when {
            apiKey.startsWith("sk-ant") -> "Claude"
            apiKey.startsWith("sk-") -> "OpenAI"
            apiKey.startsWith("gsk_") -> "Groq"
            else -> "Generic"
        }
        val baseUrl = when(provider) {
            "Claude" -> "https://api.anthropic.com/v1/messages"
            "OpenAI" -> "https://api.openai.com/v1/chat/completions"
            "Groq" -> "https://api.groq.com/openai/v1/chat/completions"
            else -> ""
        }
        val models = getModelsForProvider(provider)
        return UserApiConfig(
            providerName = provider, 
            baseUrl = baseUrl, 
            apiKey = apiKey, 
            model = models.firstOrNull() ?: "default",
            isActive = true
        )
    }

    fun updateSelectedModel(model: String) {
        _uiState.value = _uiState.value.copy(selectedModel = model)
        val currentConfig = _uiState.value.activeConfig
        if (currentConfig != null) {
            saveConfig(currentConfig.copy(model = model))
        }
    }

    fun saveConfig(config: UserApiConfig) {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            aiConfigRepository.saveConfig(config).onSuccess {
                loadConfigs()
                _uiState.value = _uiState.value.copy(loading = false, testResult = "Intelligence synchronized")
            }.onFailure {
                _uiState.value = _uiState.value.copy(loading = false, error = "Mirroring failed: ${it.message}")
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
            
            // Intelligence Loop for tool calling
            processAiResponse(userId, userContent)
        }
    }

    private suspend fun processAiResponse(userId: String, input: String) {
        val config = _uiState.value.activeConfig
        val tools = toolController.getToolSchemas()
        
        aiRepository.chatWithTools(input, tools, config).onSuccess { raw ->
            val gson = Gson()
            try {
                val json = gson.fromJson(raw, Map::class.java)
                val choices = json["choices"] as? List<*>
                val message = (choices?.firstOrNull() as? Map<*, *>)?.get("message") as? Map<*, *>
                val content = message?.get("content") as? String
                val toolCallsJson = message?.get("tool_calls") as? List<*>
                
                content?.let {
                    val aiMessage = ChatMessage("assistant", it)
                    _uiState.value = _uiState.value.copy(chatHistory = _uiState.value.chatHistory + aiMessage)
                    contextManager.processNewMessage(userId, "assistant", it)
                }

                if (toolCallsJson != null) {
                    val toolCalls = toolCallsJson.map { 
                        gson.fromJson(gson.toJson(it), AiToolCall::class.java) 
                    }
                    val results = toolController.handleToolCalls(toolCalls)
                    
                    // Recursive call to AI for "Tool Execution Successful" or similar logic could go here
                    val executionSummary = "The following actions were performed: ${results.joinToString { it.toolCallId }}"
                    val systemAck = ChatMessage("system", executionSummary)
                    _uiState.value = _uiState.value.copy(chatHistory = _uiState.value.chatHistory + systemAck)
                }
            } catch (e: Exception) {
                // Fallback to extraction
                val content = raw // Simplification
                _uiState.value = _uiState.value.copy(chatHistory = _uiState.value.chatHistory + ChatMessage("assistant", content))
            }
            _uiState.value = _uiState.value.copy(loading = false)
        }.onFailure {
            _uiState.value = _uiState.value.copy(loading = false, error = it.message)
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
