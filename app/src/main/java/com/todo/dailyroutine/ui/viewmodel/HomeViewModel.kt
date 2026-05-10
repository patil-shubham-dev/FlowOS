package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.todo.dailyroutine.data.model.DashboardStats
import com.todo.dailyroutine.data.model.HabitItem
import com.todo.dailyroutine.data.model.TaskItem
import com.todo.dailyroutine.data.model.AiProviderConfig
import com.todo.dailyroutine.data.model.ModelInfo
import com.todo.dailyroutine.data.model.ChatMessage
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.data.repository.HabitRepository
import com.todo.dailyroutine.data.repository.TaskRepository
import com.todo.dailyroutine.data.repository.JournalRepository
import com.todo.dailyroutine.data.repository.ChatRepository
import kotlinx.coroutines.flow.*
import com.todo.dailyroutine.data.local.entity.LocalJournalEntry
import com.todo.dailyroutine.data.local.toEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.todo.dailyroutine.domain.gamification.GamificationManager
import com.todo.dailyroutine.domain.scheduling.AiScheduler
import com.todo.dailyroutine.domain.scheduling.ScheduleConflictDetector
import org.burnoutcrew.reorderable.ItemPosition
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

data class HomeUiState(
    val loading: Boolean = false,
    val tasks: List<TaskItem> = emptyList(),
    val habits: List<HabitItem> = emptyList(),
    val stats: DashboardStats = DashboardStats(0, 0, 0, "Beginner"),
    val nextBestAction: String = "Start with one small high-impact task now.",
    val oracleInsight: String? = null,
    val collapsedSections: Map<String, Boolean> = emptyMap(),
    val celebrationMessage: String? = null,
    val chatHistory: List<ChatMessage> = listOf(ChatMessage("assistant", "How can I help you optimize your day today?")),
    val journalEntries: List<LocalJournalEntry> = emptyList(),
    val isTyping: Boolean = false,
    val displayName: String = "Shubham Patil",
    val coreGoal: String = "Master Full-Stack Dev",
    val appearance: String = "Obsidian Dark",
    val aiConfig: AiProviderConfig = AiProviderConfig("google", "Google Gemini", "••••••••", ""),
    val availableModels: List<ModelInfo> = emptyList(),
    val notificationsEnabled: Boolean = true,
    val appLockEnabled: Boolean = false,
    val isVoiceEnabled: Boolean = false,
    val journalSearchQuery: String = "",
    val journalFilterDate: String? = null,
    val isAiProcessing: Boolean = false,
    val error: String? = null
)

class HomeViewModel(
    val taskRepository: TaskRepository,
    val habitRepository: HabitRepository,
    private val journalRepository: JournalRepository,
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val sessionManager: com.todo.dailyroutine.data.session.SessionManager,
    private val aiScheduler: AiScheduler,
    private val aiContextManager: com.todo.dailyroutine.domain.ai.AiContextManager,
    private val toolExecutionManager: com.todo.dailyroutine.domain.agent.ToolExecutionManager,
    private val ttsManager: com.todo.dailyroutine.util.TtsManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        initializeSettings()
        observeData()
        loadChatHistory()
        precalculateInsights()
    }

    private fun precalculateInsights() {
        viewModelScope.launch {
            // Wait for data to be observed
            delay(1000)
            val config = aiRepository.getFastConfig(_uiState.value.aiConfig)
            if (config != null) {
                aiRepository.generateNextBestAction(
                    _uiState.value.tasks,
                    _uiState.value.habits,
                    _uiState.value.stats.level,
                    _uiState.value.stats.xpPercent,
                    config
                ).onSuccess { action ->
                    _uiState.value = _uiState.value.copy(oracleInsight = action)
                }
            }
        }
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            val history = chatRepository.getRecentMessages()
            if (history.isNotEmpty()) {
                _uiState.value = _uiState.value.copy(chatHistory = history)
            }
        }
    }

    private fun formatDataForPrompt(): String {
        val state = _uiState.value
        val activeTasks = state.tasks.filter { !it.completed }
        val tasksText = activeTasks.joinToString { "${it.title} [${it.category}]" }
        val habitsText = state.habits.joinToString { "${it.name} (Streak: ${it.streak})" }
        val recentJournals = state.journalEntries.takeLast(3).joinToString("\n") { "- ${it.content}" }
        
        return """
            --- SYSTEM MEMORY ---
            ACTIVE TASKS (${activeTasks.size}): ${if (tasksText.isBlank()) "None" else tasksText}
            ACTIVE HABITS: ${if (habitsText.isBlank()) "None" else habitsText}
            RECENT REFLECTIONS:
            ${if (recentJournals.isBlank()) "No recent journals" else recentJournals}
            CURRENT METRICS: ${state.stats.progressPercent}% sync, Level: ${state.stats.level}
            --- END MEMORY ---
        """.trimIndent()
    }

    private fun observeData() {
        viewModelScope.launch {
            // Re-trigger flow when filter changes
            combine(
                taskRepository.tasks,
                habitRepository.habits,
                _uiState.map { it.journalSearchQuery }.distinctUntilChanged(),
                _uiState.map { it.journalFilterDate }.distinctUntilChanged()
            ) { tasks, habits, query, date ->
                val journals = when {
                    !date.isNullOrBlank() -> journalRepository.getEntriesByDate(date).first()
                    query.isNotBlank() -> journalRepository.searchEntries(query).first()
                    else -> journalRepository.getAllEntries().first()
                }
                Triple(tasks, habits, journals)
            }.collect { (tasks, habits, journals) ->
                val completedTasks = tasks.count { t -> t.completed }
                
                // Refined Sync %
                val currentHour = java.time.LocalTime.now().hour
                val currentBlock = when (currentHour) {
                    in 5..11 -> "Morning"
                    in 12..16 -> "Deep Work"
                    in 17..21 -> "Evening"
                    else -> "Night"
                }
                
                val adherenceBonus = tasks.count { t -> t.completed && t.timeBlock == currentBlock } * 5
                val baseProgress = if (tasks.isNotEmpty()) (completedTasks * 100) / tasks.size else 0
                val syncPercent = (baseProgress + adherenceBonus).coerceAtMost(100)
                
                val totalXp = (completedTasks * 10) + habits.sumOf { h -> h.streak * 5 }
                val level = GamificationManager.calculateLevel(totalXp)
                
                _uiState.update { currentState ->
                    currentState.copy(
                        tasks = tasks,
                        habits = habits,
                        stats = DashboardStats(
                            progressPercent = syncPercent,
                            pendingTasks = tasks.count { t -> !t.completed },
                            totalXp = totalXp,
                            level = GamificationManager.getLevelTitle(level)
                        ),
                        journalEntries = journals,
                        oracleInsight = when (currentHour) {
                            in 5..11 -> "Morning Protocol: ${tasks.count { !it.completed && it.timeBlock == "Morning" }} high-focus tasks pending. Start with the most difficult one."
                            in 12..16 -> "Deep Work Session: Energy levels typically peak now. Focus on ${tasks.firstOrNull { !it.completed }?.title ?: "your primary goal"}."
                            in 17..21 -> "Evening Reflection: ${tasks.count { it.completed }} tasks synced today. Time to capture your reflections."
                            else -> "Recharge Mode: Prepare for tomorrow. Review your schedule for a clean start."
                        }
                    )
                }
            }
        }
    }

    private fun initializeSettings() {
        _uiState.update { currentState ->
            currentState.copy(
                aiConfig = sessionManager.getAiConfig(),
                notificationsEnabled = sessionManager.isNotificationsEnabled(),
                appLockEnabled = sessionManager.isAppLockEnabled(),
                displayName = sessionManager.getDisplayName(),
                coreGoal = sessionManager.getCoreGoal(),
                appearance = sessionManager.getAppearance()
            )
        }
    }


    fun refresh(forceRemote: Boolean = false) {
        _uiState.value = _uiState.value.copy(loading = true)
        viewModelScope.launch {
            if (forceRemote) {
                taskRepository.fetchTasks()
                habitRepository.fetchHabits()
            }
            updateNextAction()
            updateOracleInsight()
            
            // Ensure shimmer is visible for at least 600ms for premium feel
            kotlinx.coroutines.delay(600)
            _uiState.value = _uiState.value.copy(loading = false)
        }
    }

    private suspend fun updateNextAction() {
        val state = _uiState.value
        val res = aiRepository.generateNextBestAction(
            tasks = state.tasks,
            habits = state.habits,
            level = state.stats.level,
            xp = state.stats.totalXp,
            config = state.aiConfig
        )
        _uiState.value = _uiState.value.copy(nextBestAction = res.getOrDefault("Next best action: complete your top priority task."))
    }

    private suspend fun updateOracleInsight() {
        val state = _uiState.value
        
        // Check for conflicts
        val conflictDetector = ScheduleConflictDetector()
        val conflicts = conflictDetector.detectConflicts(
            tasks = state.tasks.map { it.toEntity() },
            habits = state.habits.map { it.toEntity() }
        )
        
        if (conflicts.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(oracleInsight = "CRITICAL: ${conflicts[0].message}")
            return
        }

        val contextText = """
            Current State:
            - Sync: ${state.stats.progressPercent}%
            - Tasks Pending: ${state.stats.pendingTasks}
            - Level: ${state.stats.level}
            - Recent Tasks: ${state.tasks.filter { it.completed }.takeLast(3).joinToString { it.title }}
        """.trimIndent()
        
        val insightPrompt = """
            You are the FlowOS Oracle. Analyze the user's current operational state and provide a 
            single, short, high-density proactive insight (max 15 words).
            Context:
            $contextText
            
            Return ONLY the insight text.
        """.trimIndent()
        
        val insight = aiRepository.chat(insightPrompt, activeConfig = state.aiConfig).getOrNull() ?: "Maintain current execution protocol."
        _uiState.value = _uiState.value.copy(oracleInsight = insight)
    }

    fun optimizeSchedule() {
        viewModelScope.launch {
            val activeConfig = _uiState.value.aiConfig
            _uiState.value = _uiState.value.copy(loading = true)
            
            val activeTasks = _uiState.value.tasks.filter { !it.completed }
            if (activeTasks.isEmpty()) {
                _uiState.value = _uiState.value.copy(loading = false)
                return@launch
            }

            aiScheduler.optimizeSchedule(activeTasks.map { it.toEntity() }, activeConfig)
                .onSuccess { reorderList ->
                    reorderList.forEach { (id, priority) ->
                        val task = activeTasks.find { it.id == id }
                        if (task != null) {
                            taskRepository.updateTask(task.toEntity().copy(priority = priority))
                        }
                    }
                    _uiState.value = _uiState.value.copy(loading = false)
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(loading = false, error = "Optimization failed: ${it.message}")
                }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || _uiState.value.isTyping) return
        
        val userMsg = ChatMessage("user", text)
        val currentHistory = _uiState.value.chatHistory
        _uiState.value = _uiState.value.copy(
            chatHistory = currentHistory + userMsg,
            isTyping = true
        )
        
        viewModelScope.launch {
            val userId = "local_shubham" // Hardcoded for local-only
            
            // 1. Process and Persist user message (this triggers RAG storage & summarization)
            aiContextManager.processNewMessage(userId, userMsg.role, userMsg.content)
            
            val activeConfig = _uiState.value.aiConfig
            
            // 2. Get RAG-optimized and compressed context
            val optimizedContext = aiContextManager.getOptimizedContext(userId, text)
            
            val oracleMsg = ChatMessage("assistant", "")
            _uiState.value = _uiState.value.copy(
                chatHistory = _uiState.value.chatHistory + oracleMsg
            )

            val fullResponse = StringBuilder()
            var lastUpdate = System.currentTimeMillis()
            
            // 3. Request streaming response with optimized context
            // Note: I'll need to update AiRepository to support List<Map<String, String>> context
            aiRepository.chatStreamWithContext(text, activeConfig, optimizedContext)
                .collect { chunk ->
                    val cleanChunk = chunk.replace("null", "", ignoreCase = true)
                    fullResponse.append(cleanChunk)
                    
                    val now = System.currentTimeMillis()
                    if (now - lastUpdate > 32) { // Target ~30fps for UI updates
                        val updatedHistory = _uiState.value.chatHistory.toMutableList()
                        if (updatedHistory.isNotEmpty()) {
                            updatedHistory[updatedHistory.size - 1] = oracleMsg.copy(content = fullResponse.toString())
                        }
                        _uiState.value = _uiState.value.copy(chatHistory = updatedHistory)
                        lastUpdate = now
                    }
                }

            // Final state update
            val updatedHistory = _uiState.value.chatHistory.toMutableList()
            if (updatedHistory.isNotEmpty()) {
                updatedHistory[updatedHistory.size - 1] = oracleMsg.copy(content = fullResponse.toString())
            }
            _uiState.value = _uiState.value.copy(
                chatHistory = updatedHistory,
                isTyping = false
            )

            val finalResponse = fullResponse.toString()
            
            // Parse and execute autonomous actions using the dedicated manager
            toolExecutionManager.parseAndExecute(finalResponse, activeConfig)

            // Voice feedback if enabled
            if (_uiState.value.isVoiceEnabled) {
                val speechText = finalResponse.replace(Regex("\\[.*?\\]"), "").trim()
                if (speechText.isNotEmpty()) {
                    ttsManager.speak(speechText)
                }
            }

            _uiState.value = _uiState.value.copy(isTyping = false)
            chatRepository.saveMessage(ChatMessage("assistant", finalResponse), userId)
        }
    }

    fun addTask(title: String, category: String) {
        viewModelScope.launch {
            taskRepository.addTask(title, category)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            taskRepository.softDeleteTask(taskId)
        }
    }

    fun toggleTask(task: TaskItem) {
        viewModelScope.launch {
            val wasCompleted = task.completed
            taskRepository.toggleTask(task)
            
            if (!wasCompleted) {
                // Task was just marked as completed
                val messages = listOf(
                    "Protocol Advanced",
                    "Flow Sustained",
                    "Synapse firing",
                    "Cycle Complete",
                    "Efficiency +5%"
                )
                _uiState.value = _uiState.value.copy(celebrationMessage = messages.random())
                kotlinx.coroutines.delay(2000)
                _uiState.value = _uiState.value.copy(celebrationMessage = null)
            }
        }
    }

    fun addHabit(name: String) {
        viewModelScope.launch {
            habitRepository.addHabit(name)
        }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch {
            habitRepository.deleteHabit(habitId)
        }
    }

    fun toggleHabit(habit: HabitItem) {
        viewModelScope.launch {
            habitRepository.toggleHabit(habit)
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(journalSearchQuery = query) }
    }

    fun setFilterDate(date: String?) {
        _uiState.update { it.copy(journalFilterDate = date) }
    }

    suspend fun enhanceJournalEntry(content: String): String {
        if (content.isBlank()) return ""
        _uiState.update { it.copy(isAiProcessing = true) }
        
        val prompt = """
            You are a writing assistant in FlowOS. Transform the following messy journal entry into a beautifully formatted, readable, and professional reflection. 
            Use Markdown for structure (headers, bold text, bullet points). 
            Fix grammar and flow, but preserve the original mood and meaning.
            If there are clear action items, list them at the end.
            
            ENTRY:
            $content
        """.trimIndent()

        var enhanced = ""
        try {
            aiRepository.getSmartConfig()?.let { config ->
                aiRepository.chatStreamWithContext(config, listOf(com.todo.dailyroutine.data.ai.ChatMessage("user", prompt)))
                    .collect { chunk ->
                        enhanced += chunk
                    }
            }
        } catch (e: Exception) {
            enhanced = content // Fallback
        } finally {
            _uiState.update { it.copy(isAiProcessing = false) }
        }
        return enhanced
    }

    fun addJournalEntry(content: String, rating: Int) {
        viewModelScope.launch {
            val userId = taskRepository.tasks.first().firstOrNull()?.userId ?: "local_shubham"
            journalRepository.saveEntry(userId, content, rating, "Reflection captured.")
        }
    }

    fun resetSystem() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
            // Need clear functions in repositories
            taskRepository.clearAllTasks()
            habitRepository.clearAllHabits()
            // journalRepository clear as well if possible
            _uiState.value = _uiState.value.copy(
                tasks = emptyList(),
                habits = emptyList(),
                journalEntries = emptyList(),
                loading = false
            )
        }
    }

    fun toggleSection(section: String) {
        val current = _uiState.value.collapsedSections
        _uiState.value = _uiState.value.copy(
            collapsedSections = current + (section to !(current[section] ?: false))
        )
    }

    fun updateDisplayName(name: String) {
        sessionManager.setDisplayName(name)
        _uiState.value = _uiState.value.copy(displayName = name)
    }

    fun updateCoreGoal(goal: String) {
        sessionManager.setCoreGoal(goal)
        _uiState.value = _uiState.value.copy(coreGoal = goal)
    }

    fun updateAppearance(value: String) {
        sessionManager.setAppearance(value)
        _uiState.value = _uiState.value.copy(appearance = value)
    }

    fun updateAiConfig(config: AiProviderConfig) {
        sessionManager.setAiConfig(config)
        _uiState.value = _uiState.value.copy(aiConfig = config)
    }

    suspend fun enhanceJournalEntry(content: String): String = withContext(Dispatchers.IO) {
        if (content.isBlank()) return@withContext content
        
        val prompt = """
            Polish the following journal entry to be more readable, structured, and premium. 
            Maintain the original voice but fix grammar and clarity.
            Use Markdown formatting (bullet points, bold text) if it helps organization.
            
            ENTRY:
            $content
            
            Return ONLY the polished Markdown text.
        """.trimIndent()
        
        val config = aiRepository.getSmartConfig(_uiState.value.aiConfig)
        aiRepository.chat(prompt, activeConfig = config).getOrElse { content }
    }

    fun detectAiProvider(apiKey: String) {
        if (apiKey.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTyping = true, error = null)
            val result = aiRepository.detectProviderAndModels(apiKey)
            if (result != null) {
                val (config, models) = result
                val finalConfig = if (models.isNotEmpty()) {
                    config.copy(
                        selectedModelId = models[0].id,
                        selectedModelName = models[0].displayName
                    )
                } else config
                
                _uiState.value = _uiState.value.copy(
                    aiConfig = finalConfig,
                    availableModels = models,
                    isTyping = false,
                    celebrationMessage = "Detected: ${config.providerName}"
                )
                delay(1500)
                _uiState.value = _uiState.value.copy(celebrationMessage = null)
            } else {
                _uiState.value = _uiState.value.copy(
                    isTyping = false,
                    error = "Provider detection failed. Check API key."
                )
            }
        }
    }

    fun toggleVoice(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isVoiceEnabled = enabled)
    }

    fun fetchModelsFromProvider() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTyping = true)
            // Manual refresh should force network call
            val models = aiRepository.fetchModels(_uiState.value.aiConfig, forceRefresh = true)
            _uiState.value = _uiState.value.copy(
                availableModels = models,
                isTyping = false
            )
        }
    }

    fun testAiConnection() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTyping = true)
            val result = aiRepository.testConnection(_uiState.value.aiConfig)
            _uiState.value = _uiState.value.copy(
                isTyping = false,
                celebrationMessage = if (result.getOrDefault(false)) "Protocol Online" else "Sync Failed"
            )
            delay(2000)
            _uiState.value = _uiState.value.copy(celebrationMessage = null)
        }
    }

    fun toggleNotifications() {
        val next = !_uiState.value.notificationsEnabled
        sessionManager.setNotificationsEnabled(next)
        _uiState.value = _uiState.value.copy(notificationsEnabled = next)
    }

    fun toggleAppLock() {
        val next = !_uiState.value.appLockEnabled
        sessionManager.setAppLockEnabled(next)
        _uiState.value = _uiState.value.copy(appLockEnabled = next)
    }

    fun moveTask(from: ItemPosition, to: ItemPosition) {
        val tasks = _uiState.value.tasks.toMutableList()
        val fromIdx = tasks.indexOfFirst { it.id == from.key }
        val toIdx = tasks.indexOfFirst { it.id == to.key }
        
        if (fromIdx != -1 && toIdx != -1) {
            val task = tasks.removeAt(fromIdx)
            tasks.add(toIdx, task)
            
            // Re-assign sort orders
            val updatedTasks = tasks.mapIndexed { index, item ->
                item.copy(sortOrder = index)
            }
            
            _uiState.value = _uiState.value.copy(tasks = updatedTasks)
            
            viewModelScope.launch {
                updatedTasks.forEach { taskRepository.updateTask(it.toEntity()) }
            }
        }
    }
}

class HomeViewModelFactory(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val aiRepository: AiRepository,
    private val journalRepository: JournalRepository,
    private val chatRepository: ChatRepository,
    private val sessionManager: com.todo.dailyroutine.data.session.SessionManager,
    private val aiScheduler: AiScheduler,
    private val aiContextManager: com.todo.dailyroutine.domain.ai.AiContextManager,
    private val toolExecutionManager: com.todo.dailyroutine.domain.agent.ToolExecutionManager,
    private val ttsManager: com.todo.dailyroutine.util.TtsManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            taskRepository, 
            habitRepository, 
            journalRepository,
            aiRepository, 
            chatRepository, 
            sessionManager, 
            aiScheduler,
            aiContextManager,
            toolExecutionManager,
            ttsManager
        ) as T
    }
}
