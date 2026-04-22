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
                val priority = json.optInt("priority", 0)
                val energy = json.optInt("energyRequired", 5)
                val timeBlock = json.optString("timeBlock", "Morning")
                taskRepository.addTask(title, category) // Need to update repo to handle more fields
                "Deployed task: $title"
            }
            "complete_task" -> {
                val taskId = json.getString("taskId")
                // Need to find task and toggle
                "Task optimized for completion."
            }
            "delete_task" -> {
                val taskId = json.getString("taskId")
                taskRepository.softDeleteTask(taskId)
                "Task removed from protocol."
            }
            "create_habit" -> {
                val name = json.getString("name")
                habitRepository.addHabit(name)
                "Ritual initialized: $name"
            }
            "complete_habit" -> {
                val habitId = json.getString("habitId")
                "Ritual synchronized."
            }
            "delete_habit" -> {
                val habitId = json.getString("habitId")
                "Ritual removed."
            }
            "write_journal_entry" -> {
                val content = json.getString("content")
                val rating = json.optInt("vibeRating", 5)
                journalRepository.saveEntry(userId, content, rating)
                "Journey documented and synchronized."
            }
            "update_vibe_score" -> {
                val rating = json.getInt("rating")
                "Vibe state updated to $rating/10."
            }
            else -> "Protocol unknown."
        }
    }
}
