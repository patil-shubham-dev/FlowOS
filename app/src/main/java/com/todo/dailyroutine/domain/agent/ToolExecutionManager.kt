package com.todo.dailyroutine.domain.agent

import com.todo.dailyroutine.data.repository.TaskRepository
import com.todo.dailyroutine.data.repository.HabitRepository
import com.todo.dailyroutine.data.local.toEntity
import com.todo.dailyroutine.domain.scheduling.AiScheduler
import com.todo.dailyroutine.data.model.AiProviderConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.util.Log

class ToolExecutionManager(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val journalRepository: com.todo.dailyroutine.data.repository.JournalRepository,
    private val aiScheduler: AiScheduler,
    private val navigationManager: com.todo.dailyroutine.util.AppNavigationManager
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    fun parseAndExecute(response: String, config: AiProviderConfig? = null) {
        // [ADD_TASK: "Title", "Category"]
        val taskRegex = Regex("\\[ADD_TASK:\\s*['\"](.+?)['\"]\\s*,\\s*['\"](.+?)['\"]\\]")
        taskRegex.findAll(response).forEach { match ->
            val title = match.groupValues[1]
            val category = match.groupValues[2]
            scope.launch {
                taskRepository.addTask(title, category)
                Log.d("ToolExecution", "Added Task: $title")
            }
        }

        // [ADD_HABIT: "Name"]
        val habitRegex = Regex("\\[ADD_HABIT:\\s*['\"](.+?)['\"]\\]")
        habitRegex.findAll(response).forEach { match ->
            val name = match.groupValues[1]
            scope.launch {
                habitRepository.addHabit(name)
                Log.d("ToolExecution", "Added Habit: $name")
            }
        }

        // [NAVIGATE: TabName]
        val navRegex = Regex("\\[NAVIGATE:\\s*(.+?)\\]", RegexOption.IGNORE_CASE)
        navRegex.findAll(response).forEach { match ->
            val tab = match.groupValues[1].trim()
            navigationManager.navigateTo(tab.lowercase())
            Log.d("ToolExecution", "Navigating to: $tab")
        }

        // [OPTIMIZE_SCHEDULE]
        if (response.contains("[OPTIMIZE_SCHEDULE]")) {
            scope.launch {
                val tasks = taskRepository.getAllTasksSync().filter { !it.completed }
                if (tasks.isNotEmpty()) {
                    aiScheduler.optimizeSchedule(tasks.map { it.toEntity() }, config)
                        .onSuccess { reorderList ->
                            reorderList.forEach { (id, priority) ->
                                val task = tasks.find { it.id == id }
                                if (task != null) {
                                    taskRepository.updateTask(task.copy(priority = priority))
                                }
                            }
                            Log.d("ToolExecution", "Schedule Optimized")
                        }
                }
            }
        }
    }
}
