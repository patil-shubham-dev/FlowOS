package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.todo.dailyroutine.data.model.DashboardStats
import com.todo.dailyroutine.data.model.HabitItem
import com.todo.dailyroutine.data.model.TaskItem
import com.todo.dailyroutine.data.model.UserApiConfig
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.data.repository.HabitRepository
import com.todo.dailyroutine.data.repository.TaskRepository
import com.todo.dailyroutine.data.local.toEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.todo.dailyroutine.domain.gamification.GamificationManager
import com.todo.dailyroutine.domain.scheduling.AiScheduler
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
    val error: String? = null
)

class HomeViewModel(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val aiRepository: AiRepository,
    private val aiScheduler: AiScheduler
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            combine(taskRepository.tasks, habitRepository.habits) { tasks, habits ->
                tasks to habits
            }.collect { (tasks, habits) ->
                val completedTasks = tasks.count { it.completed }
                
                // Refined Sync %: Completion + Time Block Adherence
                val currentHour = java.time.LocalTime.now().hour
                val currentBlock = when (currentHour) {
                    in 5..11 -> "Morning"
                    in 12..16 -> "Deep Work"
                    in 17..21 -> "Evening"
                    else -> "Night"
                }
                
                val adherenceBonus = tasks.count { it.completed && it.timeBlock == currentBlock } * 5
                val baseProgress = if (tasks.isNotEmpty()) (completedTasks * 100) / tasks.size else 0
                val syncPercent = (baseProgress + adherenceBonus).coerceAtMost(100)
                
                // Calculate XP from tasks and habits
                val totalXp = (completedTasks * 10) + habits.sumOf { it.streak * 5 }
                val level = GamificationManager.calculateLevel(totalXp)
                
                _uiState.value = _uiState.value.copy(
                    tasks = tasks,
                    habits = habits,
                    stats = DashboardStats(
                        progressPercent = syncPercent,
                        pendingTasks = tasks.count { !it.completed },
                        totalXp = totalXp,
                        level = GamificationManager.getLevelTitle(level)
                    )
                )
            }
        }
    }

    fun refresh(forceRemote: Boolean = false) {
        viewModelScope.launch {
            if (forceRemote) {
                taskRepository.fetchTasks()
                habitRepository.fetchHabits()
            }
            updateNextAction()
            updateOracleInsight()
        }
    }

    private suspend fun updateNextAction() {
        val state = _uiState.value
        val res = aiRepository.generateNextBestAction(
            tasks = state.tasks,
            habits = state.habits,
            level = state.stats.level,
            xp = state.stats.totalXp
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
            _uiState.value = _uiState.value.copy(oracleInsight = "CRITICAL: ${conflicts.first().message}")
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
        
        val insight = aiRepository.chat(insightPrompt).getOrNull() ?: "Maintain current execution protocol."
        _uiState.value = _uiState.value.copy(oracleInsight = insight)
    }

    fun optimizeSchedule(activeConfig: UserApiConfig?) {
        viewModelScope.launch {
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

    fun addTask(title: String, category: String) {
        viewModelScope.launch {
            taskRepository.addTask(title, category)
        }
    }

    fun toggleTask(task: TaskItem) {
        viewModelScope.launch {
            taskRepository.toggleTask(task)
        }
    }

    fun addHabit(name: String) {
        viewModelScope.launch {
            habitRepository.addHabit(name)
        }
    }

    fun toggleHabit(habit: HabitItem) {
        viewModelScope.launch {
            habitRepository.toggleHabit(habit)
        }
    }

    fun toggleSection(section: String) {
        val current = _uiState.value.collapsedSections
        _uiState.value = _uiState.value.copy(
            collapsedSections = current + (section to !(current[section] ?: false))
        )
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
    private val aiScheduler: AiScheduler
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(taskRepository, habitRepository, aiRepository, aiScheduler) as T
    }
}
