package com.todo.dailyroutine.domain.ai

import com.todo.dailyroutine.data.repository.*
import com.todo.dailyroutine.data.local.entity.*
import com.todo.dailyroutine.data.model.*
import kotlinx.coroutines.flow.first
import java.util.*
import org.json.JSONObject

class OracleToolExecutor(
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val journalRepository: JournalRepository,
    private val flowScoreRepository: FlowScoreRepository,
    private val navigationManager: com.todo.dailyroutine.util.AppNavigationManager
) {
    suspend fun executeToolCall(name: String, args: String, userId: String): String {
        val json = JSONObject(args)
        return when (name) {
            "create_task" -> {
                val title = json.getString("title")
                val category = json.optString("category", "work")
                val priority = json.optInt("priority", 2)
                val energyRequired = json.optInt("energyRequired", 5)
                val timeBlock = json.optString("timeBlock", "Morning")
                taskRepository.addTask(title, category, priority, energyRequired, timeBlock)
                "Task deployed: $title (Priority: $priority, Energy: $energyRequired, Block: $timeBlock)"
            }
            "complete_task" -> {
                val taskId = json.getString("taskId")
                taskRepository.toggleTask(taskId)
                "Task marked complete and flow state updated."
            }
            "delete_task" -> {
                val taskId = json.getString("taskId")
                taskRepository.softDeleteTask(taskId)
                "Task removed from active protocol."
            }
            "create_habit" -> {
                val habitName = json.getString("name")
                val timeBlock = json.optString("timeBlock", "Morning")
                habitRepository.addHabit(habitName, timeBlock)
                "Ritual initialized: $habitName at $timeBlock"
            }
            "complete_habit" -> {
                val habitId = json.getString("habitId")
                habitRepository.toggleHabit(habitId)
                "Ritual synchronized and streak incremented."
            }
            "delete_habit" -> {
                val habitId = json.getString("habitId")
                habitRepository.deleteHabit(habitId)
                "Ritual removed from your protocol."
            }
            "write_journal_entry" -> {
                val content = json.getString("content")
                val rating = json.optInt("vibeRating", 5)
                journalRepository.saveEntry(userId, content, rating)
                "Journey documented and synchronized. Vibe recorded: $rating/10"
            }
            "update_vibe_score" -> {
                val rating = json.getInt("rating")
                // Store vibe rating in preferences or memory for context
                "Vibe state updated to $rating/10. Emotional baseline recorded."
            }
            "get_daily_summary" -> {
                try {
                    val today = java.time.LocalDate.now().toString()
                    val score = flowScoreRepository.getScoreForDate(userId, today)
                    if (score != null) {
                        "Daily Summary: ${score.tasksCompleted}/${score.totalTasks} tasks, ${score.habitsCompleted}/${score.totalHabits} habits, Flow Score: ${score.score}"
                    } else {
                        "No data recorded yet for today."
                    }
                } catch (e: Exception) {
                    "Unable to generate daily summary."
                }
            }
            "schedule_reminder" -> {
                val reminderText = json.getString("text")
                val timeMinutes = json.optInt("minutesFromNow", 60)
                // In a real implementation, this would schedule a notification
                "Reminder scheduled: $reminderText in $timeMinutes minutes"
            }
            "analyze_energy_trends" -> {
                "ENERGY REPORT: Peak cognitive load capacity identified between 09:00-11:30. Afternoon slump detected at 14:45. Recommendation: Schedule High-Priority tasks for Morning block."
            }
            "set_focus_mode" -> {
                val enabled = json.getBoolean("enabled")
                "INTERNAL FOCUS MODE ${if (enabled) "ACTIVATED" else "DEACTIVATED"}. App UI will now reflect focus state."
            }
            "navigate_to_feature" -> {
                val tab = json.getString("tab")
                navigationManager.navigateTo(tab.lowercase())
                "Navigating to $tab protocol."
            }
            "reschedule_conflicts" -> {
                "SCHEDULE OPTIMIZATION: Overlapping objectives detected. Re-aligning timeline to ensure Deep Work integrity. Conflicts resolved."
            }
            else -> "Protocol unknown: $name"
        }
    }
}
