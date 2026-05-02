package com.todo.dailyroutine.data.local.dao

import androidx.room.*
import com.todo.dailyroutine.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {
    @Query("SELECT * FROM journal_entries ORDER BY timestamp DESC")
    fun getAllEntries(): Flow<List<LocalJournalEntry>>
    @Query("SELECT * FROM journal_entries WHERE userId = :userId ORDER BY timestamp DESC")
    fun getEntriesByUserId(userId: String): Flow<List<LocalJournalEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: LocalJournalEntry)

    @Query("SELECT * FROM journal_entries WHERE date = :date LIMIT 1")
    suspend fun getEntryByDate(date: String): LocalJournalEntry?

    @Delete
    suspend fun deleteEntry(entry: LocalJournalEntry)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE syncStatus != 3 ORDER BY timeBlock ASC, sortOrder ASC")
    fun getAllTasks(): Flow<List<LocalTask>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: LocalTask)

    @Update
    suspend fun updateTask(task: LocalTask)

    @Query("UPDATE tasks SET syncStatus = 3 WHERE id = :id")
    suspend fun softDeleteTask(id: String)

    @Query("SELECT * FROM tasks WHERE syncStatus != 0")
    suspend fun getUnsyncedTasks(): List<LocalTask>

    @Delete
    suspend fun hardDeleteTask(task: LocalTask)
}

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits WHERE syncStatus != 3 ORDER BY timeBlock ASC, sortOrder ASC")
    fun getAllHabits(): Flow<List<LocalHabit>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: LocalHabit)

    @Query("SELECT * FROM habits WHERE syncStatus != 0")
    suspend fun getUnsyncedHabits(): List<LocalHabit>

    @Delete
    suspend fun hardDeleteHabit(habit: LocalHabit)
}

@Dao
interface AiConfigDao {
    @Query("SELECT * FROM ai_configs")
    fun getAllConfigs(): Flow<List<LocalAiConfig>>

    @Query("SELECT * FROM ai_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveConfig(): LocalAiConfig?
    
    @Query("SELECT * FROM ai_configs WHERE id = :id LIMIT 1")
    suspend fun getConfigById(id: String): LocalAiConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: LocalAiConfig)
    
    @Query("DELETE FROM ai_configs WHERE id = :id")
    suspend fun deleteConfigById(id: String)
    
    @Query("UPDATE ai_configs SET isActive = 0")
    suspend fun deactivateAll()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM ai_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<LocalMessage>

    @Query("SELECT * FROM ai_messages WHERE id > :afterId ORDER BY timestamp ASC")
    suspend fun getMessagesAfter(afterId: Int): List<LocalMessage>

    @Insert
    suspend fun insertMessage(message: LocalMessage): Long

    @Query("SELECT COUNT(*) FROM ai_messages WHERE userId = :userId")
    suspend fun getMessageCount(userId: String): Int

    @Query("DELETE FROM ai_messages")
    suspend fun clearHistory()
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM ai_memories WHERE userId = :userId ORDER BY importance DESC, timestamp DESC LIMIT 20")
    suspend fun getAllMemories(userId: String): List<LocalMemory>

    @Query("SELECT * FROM ai_memories WHERE id = :id LIMIT 1")
    suspend fun getMemoryById(id: String): LocalMemory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveMemory(memory: LocalMemory)

    @Query("DELETE FROM ai_memories WHERE id = :id")
    suspend fun deleteMemory(id: String)

    @Query("UPDATE ai_memories SET importance = importance * 0.95")
    suspend fun decayMemories()
    
    @Query("UPDATE ai_memories SET importance = importance + 0.2, lastUsed = :now WHERE id = :id")
    suspend fun reinforceMemory(id: String, now: Long = System.currentTimeMillis())
}

@Dao
interface SummaryDao {
    @Query("SELECT * FROM conversation_state WHERE userId = :userId")
    suspend fun getSummary(userId: String): ConversationSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateSummary(summary: ConversationSummary)
}

@Dao
interface FlowScoreDao {
    @Query("SELECT * FROM flow_scores WHERE userId = :userId ORDER BY date DESC")
    fun getAllScores(userId: String): Flow<List<LocalFlowScore>>

    @Query("SELECT * FROM flow_scores WHERE userId = :userId AND date = :date LIMIT 1")
    suspend fun getScoreByDate(userId: String, date: String): LocalFlowScore?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScore(score: LocalFlowScore)

    @Query("SELECT * FROM flow_scores WHERE userId = :userId ORDER BY date DESC LIMIT 7")
    fun getWeeklyTrend(userId: String): Flow<List<LocalFlowScore>>
}

@Dao
interface JournalStreakDao {
    @Query("SELECT * FROM journal_streaks WHERE userId = :userId")
    suspend fun getStreak(userId: String): LocalJournalStreak?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun updateStreak(streak: LocalJournalStreak)
}
