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
                val progress = if (tasks.isNotEmpty()) (completedTasks * 100) / tasks.size else 0
                
                // Calculate XP from tasks and habits
                val totalXp = (completedTasks * 10) + habits.sumOf { it.streak * 5 }
                val level = GamificationManager.calculateLevel(totalXp)
                
                _uiState.value = _uiState.value.copy(
                    tasks = tasks,
                    habits = habits,
                    stats = DashboardStats(
                        progressPercent = progress,
                        pendingTasks = tasks.count { !it.completed },
                        totalXp = totalXp,
                        level = GamificationManager.getLevelTitle(level)
                    )
                )
            }
        }
    }

    fun refresh(forceRemote: Boolean = true) {
        viewModelScope.launch {
            if (forceRemote) {
                taskRepository.fetchTasks()
                habitRepository.fetchHabits()
            }
            updateNextAction()
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

    fun optimizeSchedule(activeConfig: UserApiConfig?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true)
             // This would normally call aiScheduler and then update priorities in LocalTasks
             // For now, let's just refresh to show we tried
             aiScheduler.optimizeSchedule(_uiState.value.tasks.map { it.toEntity() }, activeConfig)
             _uiState.value = _uiState.value.copy(loading = false)
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
