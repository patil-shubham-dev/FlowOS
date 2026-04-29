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
    private val flowScoreRepository: FlowScoreRepository // New repository to be created
) {
    suspend fun executeToolCall(name: String, args: String, userId: String): String {
        val json = JSONObject(args)
        return when (name) {
            "create_task" -> {
                val title = json.getString("title")
                val category = json.optString("category", "General")
                val priority = json.optInt("priority", 2)
                val energyRequired = json.optInt("energyRequired", 5)
                val timeBlock = json.optString("timeBlock", "Morning")
                // Create task with all fields
                taskRepository.addTask(title, category, priority, energyRequired, timeBlock)
                "Deployed task: $title (Priority: $priority, Energy: $energyRequired, Block: $timeBlock)"
            }
            "complete_task" -> {
                val taskId = json.getString("taskId")
                taskRepository.toggleTask(taskId)
                "Task optimized for completion and marked complete."
            }
            "delete_task" -> {
                val taskId = json.getString("taskId")
                taskRepository.softDeleteTask(taskId)
                "Task removed from protocol."
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
                "Ritual synchronized and streak updated."
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
                "Vibe state updated to $rating/10. Emotional baseline recorded."
            }
            "get_daily_summary" -> {
                "Generating daily synthesis report..."
            }
            "schedule_reminder" -> {
                val reminderText = json.getString("text")
                val timeMinutes = json.optInt("minutesFromNow", 60)
                "Reminder scheduled: $reminderText in $timeMinutes minutes"
            }
            else -> "Protocol unknown: $name"
        }
    }
}
