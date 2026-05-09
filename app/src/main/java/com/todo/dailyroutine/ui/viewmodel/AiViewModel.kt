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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart

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
    val testResult: String? = null,
    val dailyProtocol: DailyProtocol? = null,
    val protocolLoading: Boolean = false
)

data class DailyProtocol(
    val summary: String,
    val actions: List<ProtocolAction>
)

data class ProtocolAction(
    val taskId: String,
    val title: String,
    val suggestedTimeBlock: String,
    val reasoning: String
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
            
            // For tool-calling, we still use unary chat as it's easier to handle structured response
            var currentResult = aiRepository.chat(userContent, config, tools = tools)
            var iterations = 0
            var finalContent = ""
            
            while (currentResult.isSuccess && iterations < 3) {
                val raw = currentResult.getOrThrow()
                val apiKey = config?.apiKey ?: ""
                
                val toolCalls = aiRepository.extractToolCalls(raw, apiKey)
                if (toolCalls.isEmpty()) {
                    finalContent = aiRepository.extractContent(raw, apiKey)
                    break
                }

                // Execute tools
                val toolResults = toolController.handleToolCalls(toolCalls)
                val resultsText = toolResults.joinToString("\n") { it.content }
                val followUpPrompt = "System: Tools executed. Results:\n$resultsText\nSummarize the action taken for the user."
                
                currentResult = aiRepository.chat(followUpPrompt, config)
                iterations++
            }

            if (currentResult.isSuccess) {
                // Now stream the final response if it's the last iteration or no tools were used
                // Actually, if we already have finalContent from a unary call, we just show it.
                // To TRULY stream, we should have used chatStream from the start if no tools were expected.
                // For this improvement, let's implement a streaming-only path for non-tool queries.
                
                if (finalContent.isNotEmpty()) {
                    val aiMessage = ChatMessage("assistant", finalContent)
                    _uiState.value = _uiState.value.copy(
                        chatHistory = _uiState.value.chatHistory + aiMessage,
                        loading = false
                    )
                    contextManager.processNewMessage(userId, "assistant", finalContent)
                }
            } else if (currentResult.isFailure) {
                _uiState.value = _uiState.value.copy(
                    loading = false,
                    error = currentResult.exceptionOrNull()?.message
                )
            }
        }
    }

    /**
     * New Streaming-first send method (replaces sendMessage for basic chat)
     */
    fun sendStreamingMessage() {
        val userContent = _uiState.value.prompt.trim()
        if (userContent.isBlank()) return

        val newMessage = ChatMessage("user", userContent)
        val assistantMessage = ChatMessage("assistant", "")
        
        _uiState.value = _uiState.value.copy(
            chatHistory = _uiState.value.chatHistory + newMessage + assistantMessage,
            prompt = "",
            loading = true
        )

        viewModelScope.launch {
            val userId = "user_default"
            contextManager.processNewMessage(userId, "user", userContent)
            
            val config = _uiState.value.activeConfig
            var accumulatedText = ""
            
            aiRepository.chatStream(userContent, config)
                .onStart { _uiState.value = _uiState.value.copy(loading = false) }
                .collect { chunk ->
                    accumulatedText += chunk
                    val updatedHistory = _uiState.value.chatHistory.toMutableList()
                    if (updatedHistory.isNotEmpty() && updatedHistory.last().role == "assistant") {
                        updatedHistory[updatedHistory.size - 1] = ChatMessage("assistant", accumulatedText)
                    }
                    _uiState.value = _uiState.value.copy(chatHistory = updatedHistory)
                }
            
            contextManager.processNewMessage(userId, "assistant", accumulatedText)
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

    fun generateDailyProtocol(taskRepository: com.todo.dailyroutine.data.repository.TaskRepository) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(protocolLoading = true, error = null)
            val userId = "user_default"
            val context = contextManager.getOptimizedContext(userId)
            
            val prompt = """
                As the FlowOS Neural Oracle, analyze the user's current context, recent journal vibes, and pending tasks.
                Generate a 'Daily Protocol' that reorders their tasks for maximum energy alignment.
                Return ONLY a JSON object in this format:
                {
                  "summary": "Short neural briefing on why this schedule works",
                  "actions": [
                    { "taskId": "id", "title": "task title", "suggestedTimeBlock": "Morning/Deep Work/Evening", "reasoning": "why now?" }
                  ]
                }
                Context:
                $context
            """.trimIndent()

            val config = _uiState.value.activeConfig
            val result = aiRepository.chat(prompt, config)
            
            if (result.isSuccess) {
                try {
                    val rawJson = aiRepository.extractContent(result.getOrThrow(), config?.apiKey ?: "")
                    // Clean up potential markdown formatting
                    val cleanJson = rawJson.substringAfter("```json").substringBeforeLast("```").trim().ifBlank { rawJson }
                    val protocol = Gson().fromJson(cleanJson, DailyProtocol::class.java)
                    _uiState.value = _uiState.value.copy(dailyProtocol = protocol, protocolLoading = false)
                } catch (e: Exception) {
                    _uiState.value = _uiState.value.copy(protocolLoading = false, error = "Failed to parse protocol: ${e.message}")
                }
            } else {
                _uiState.value = _uiState.value.copy(protocolLoading = false, error = result.exceptionOrNull()?.message)
            }
        }
    }

    fun applyProtocol(taskRepository: com.todo.dailyroutine.data.repository.TaskRepository) {
        val protocol = _uiState.value.dailyProtocol ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(protocolLoading = true)
            val order = protocol.actions.map { it.taskId }
            taskRepository.applySortOrder(order).onSuccess {
                _uiState.value = _uiState.value.copy(dailyProtocol = null, protocolLoading = false)
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
