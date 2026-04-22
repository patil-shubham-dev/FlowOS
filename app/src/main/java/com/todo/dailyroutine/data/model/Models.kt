package com.todo.dailyroutine.data.model

data class AppUser(
    val id: String,
    val email: String
)

data class TaskItem(
    val id: String,
    val userId: String,
    val title: String,
    val category: String,
    val completed: Boolean,
    val priority: Int = 0,
    val sortOrder: Int = 0
)

data class HabitItem(
    val id: String,
    val userId: String,
    val name: String,
    val streak: Int,
    val completedToday: Boolean
)

data class DashboardStats(
    val progressPercent: Int,
    val pendingTasks: Int,
    val totalXp: Int,
    val level: String
)

data class UserApiConfig(
    val id: String = "",
    val userId: String = "",
    val providerName: String,
    val baseUrl: String,
    val apiKey: String,
    val headersJson: String? = null,
    val model: String? = null,
    val isActive: Boolean = false
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
