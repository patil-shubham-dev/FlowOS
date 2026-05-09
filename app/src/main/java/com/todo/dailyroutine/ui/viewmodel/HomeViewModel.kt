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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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
    val error: String? = null
)

class HomeViewModel(
    val taskRepository: TaskRepository,
    val habitRepository: HabitRepository,
    private val aiRepository: AiRepository,
    private val journalRepository: JournalRepository,
    private val chatRepository: ChatRepository,
    private val sessionManager: com.todo.dailyroutine.data.session.SessionManager,
    private val aiScheduler: AiScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
        loadChatHistory()
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
            combine(
                taskRepository.tasks, 
                habitRepository.habits,
                journalRepository.getAllEntries()
            ) { tasks, habits, journals ->
                Triple(tasks, habits, journals)
            }.collect { (tasks, habits, journals) ->
                val completedTasks = tasks.count { t -> t.completed }
                
                // Refined Sync %: Completion + Time Block Adherence
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
                
                // Calculate XP from tasks and habits
                val totalXp = (completedTasks * 10) + habits.sumOf { h -> h.streak * 5 }
                val level = GamificationManager.calculateLevel(totalXp)
                
                _uiState.value = _uiState.value.copy(
                    tasks = tasks,
                    habits = habits,
                    stats = DashboardStats(
                        progressPercent = syncPercent,
                        pendingTasks = tasks.count { t -> !t.completed },
                        totalXp = totalXp,
                        level = GamificationManager.getLevelTitle(level)
                    ),
                    journalEntries = journals,
                    appearance = sessionManager.getAppearance(),
                    aiConfig = sessionManager.getAiConfig(),
                    notificationsEnabled = sessionManager.isNotificationsEnabled(),
                    appLockEnabled = sessionManager.isAppLockEnabled()
                )
            }
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
            // Persist user message
            val userId = "local_shubham" // Hardcoded for local-only
            chatRepository.saveMessage(userMsg, userId)

            val activeConfig = _uiState.value.aiConfig
            val systemPrompt = """
                You are the FlowOS Oracle, an autonomous high-performance AI core.
                Your mission is to help the user achieve peak "Flow" by analyzing their data and providing proactive, direct guidance.
                
                ${formatDataForPrompt()}

                Guidelines:
                1. You have READ ACCESS to the user's system state (provided above). Always use this data to provide context-aware answers.
                2. If the user asks about their progress, tasks, or habits, reference the SYSTEM MEMORY accurately.
                3. Proactively suggest ways to improve their routine or complete pending tasks to reach their core goal.
                4. NEVER use the word "null". 
                5. Keep responses concise, direct, and actionable. No conversational filler.
                6. If a task or habit is mentioned, you can offer to "Optimize" it conceptually.
                7. EXECUTIVE CONTROL: You can autonomously perform actions by outputting these exact tags at the END of your response:
                   - [ADD_TASK: "Title", "Category"] : To add a new task to the user's list.
                   - [ADD_HABIT: "Name"] : To add a new habit to the user's routine.
                   (Example: "I've added a deep work block for you. [ADD_TASK: 'Deep Work Session', 'Work']")
                
                Context:
                - User: ${_uiState.value.displayName}
                - Primary Goal: ${_uiState.value.coreGoal}
            """.trimIndent()

            val oracleMsg = ChatMessage("assistant", "")
            _uiState.value = _uiState.value.copy(
                chatHistory = _uiState.value.chatHistory + oracleMsg
            )

            val fullResponse = StringBuilder()
            aiRepository.chatStream(text, activeConfig, systemPrompt)
                .collect { chunk ->
                    val cleanChunk = chunk.replace("null", "", ignoreCase = true)
                    fullResponse.append(cleanChunk)
                    
                    val updatedHistory = _uiState.value.chatHistory.toMutableList()
                    if (updatedHistory.isNotEmpty()) {
                        updatedHistory[updatedHistory.size - 1] = oracleMsg.copy(content = fullResponse.toString())
                    }
                    _uiState.value = _uiState.value.copy(chatHistory = updatedHistory)
                }

            val finalResponse = fullResponse.toString()
            // Parse and execute autonomous actions
            if (finalResponse.contains("[ADD_TASK:")) {
                val match = Regex("\\[ADD_TASK:\\s*['\"](.+?)['\"]\\s*,\\s*['\"](.+?)['\"]\\]").find(finalResponse)
                match?.let {
                    val title = it.groupValues[1]
                    val category = it.groupValues[2]
                    addTask(title, category)
                }
            }
            if (finalResponse.contains("[ADD_HABIT:")) {
                val match = Regex("\\[ADD_HABIT:\\s*['\"](.+?)['\"]\\]").find(finalResponse)
                match?.let {
                    val name = it.groupValues[1]
                    addHabit(name)
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

    fun fetchModelsFromProvider() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isTyping = true)
            val models = aiRepository.fetchModels(_uiState.value.aiConfig)
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
    private val aiScheduler: AiScheduler
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(taskRepository, habitRepository, aiRepository, journalRepository, chatRepository, sessionManager, aiScheduler) as T
    }
}
