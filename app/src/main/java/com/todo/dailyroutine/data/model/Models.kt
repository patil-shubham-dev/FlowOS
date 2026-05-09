package com.todo.dailyroutine.data.model

data class AppUser(
    val id: String,
    val email: String
)

data class JournalEntry(
    val id: String,
    val userId: String,
    val content: String,
    val rating: Int,
    val aiInsight: String? = null,
    val date: String,
    val timestamp: Long
)

data class TaskItem(
    val id: String,
    val userId: String,
    val title: String,
    val category: String,
    val completed: Boolean,
    val priority: Int = 0,
    val energyRequired: Int = 5,
    val timeBlock: String = "Morning",
    val scheduledTime: String? = null,
    val sortOrder: Int = 0
)

data class HabitItem(
    val id: String,
    val userId: String,
    val name: String,
    val streak: Int,
    val completedToday: Boolean,
    val timeBlock: String = "Morning",
    val scheduledTime: String? = null,
    val sortOrder: Int = 0
)

data class DashboardStats(
    val progressPercent: Int,
    val pendingTasks: Int,
    val totalXp: Int,
    val level: String
)


data class ChatMessage(
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AiReminderPlan(
    val enabled: Boolean,
    val hour24: Int,
    val minute: Int,
    val title: String,
    val body: String
)

data class AiRoutineResponse(
    val advice: String,
    val reminderPlan: AiReminderPlan?
)

data class ParsedIntent(
    val type: String, // "task", "habit", "search"
    val title: String? = null,
    val category: String = "Other",
    val timeBlock: String? = null,
    val isRecurring: Boolean = false,
    val searchQuery: String? = null
)

data class SystemContext(
    val tasks: List<TaskItem>,
    val habits: List<HabitItem>,
    val journalEntries: List<JournalEntry>,
    val flowScore: Int,
    val userLevel: String,
    val currentTime: String,
    val recentMoods: List<String>
)

data class AiToolCall(
    val id: String,
    val type: String,
    val function: AiFunctionCall
)

data class AiFunctionCall(
    val name: String,
    val arguments: String
)
