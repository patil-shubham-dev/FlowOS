package com.todo.dailyroutine.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class LocalTask(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val category: String,
    val completed: Boolean,
    val priority: Int = 0, // For AI scheduling
    val energyRequired: Int = 5, // 1-10
    val timeBlock: String = "Morning", // "Morning", "Deep Work", "Evening", "Night"
    val scheduledTime: String? = null,
    val sortOrder: Int = 0, // Added for Section 1.2
    val lastUpdated: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)

@Entity(tableName = "habits")
data class LocalHabit(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val streak: Int,
    val completedToday: Boolean,
    val timeBlock: String = "Morning",
    val scheduledTime: String? = null,
    val sortOrder: Int = 0, // Added for Section 1.2
    val lastUpdated: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)

@Entity(tableName = "journal_entries")
data class LocalJournalEntry(
    @PrimaryKey val id: String,
    val userId: String,
    val content: String,
    val rating: Int = 5, // 1-10
    val aiInsight: String? = null,
    val date: String, // ISO date string
    val timestamp: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)

@Entity(tableName = "ai_configs")
data class LocalAiConfig(
    @PrimaryKey val id: String,
    val userId: String,
    val providerName: String,
    val baseUrl: String = "",
    val apiKeyEncrypted: String,
    val model: String?,
    val isActive: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis(),
    val syncStatus: Int = 0
)

@Entity(tableName = "ai_messages")
data class LocalMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val role: String, // "user" or "assistant" or "system"
    val content: String,
    val toolCalls: String? = null, // Added for Section 4 (JSON string)
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_memories")
data class LocalMemory(
    @PrimaryKey val id: String,
    val userId: String,
    val text: String,
    val embedding: String, // Stored as JSON array of floats
    val type: String = "fact", // "preference", "fact", "goal", "task", "context"
    val importance: Float = 1.0f,
    val lastUsed: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "conversation_state")
data class ConversationSummary(
    @PrimaryKey val userId: String,
    val currentSummary: String,
    val lastSummarizedMessageId: Int = 0,
    val messageCountSinceSummary: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)

@Entity(tableName = "flow_scores")
data class LocalFlowScore(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val date: String, // ISO date
    val score: Int,
    val habitsCompleted: Int,
    val totalHabits: Int,
    val tasksCompleted: Int,
    val totalTasks: Int,
    val hasJournalEntry: Boolean,
    val vibeRating: Int,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "journal_streaks")
data class LocalJournalStreak(
    @PrimaryKey val userId: String,
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastEntryDate: String? = null // ISO Date
)
@Entity(tableName = "bio_data")
data class LocalBioData(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: String,
    val date: String, // ISO date
    val steps: Int = 0,
    val sleepMinutes: Int = 0,
    val avgHeartRate: Int = 0,
    val hrvScore: Int = 0, // Heart Rate Variability
    val timestamp: Long = System.currentTimeMillis()
)
