package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.local.dao.JournalDao
import com.todo.dailyroutine.data.local.dao.JournalStreakDao
import com.todo.dailyroutine.data.local.entity.LocalJournalEntry
import com.todo.dailyroutine.data.local.entity.LocalJournalStreak
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class JournalRepository(
    private val journalDao: JournalDao,
    private val streakDao: JournalStreakDao,
    private val memoryPipeline: com.todo.dailyroutine.domain.vector.MemoryPipeline? = null
) {
    fun getAllEntries(): Flow<List<LocalJournalEntry>> = journalDao.getAllEntries()

    suspend fun saveEntry(userId: String, content: String, rating: Int, aiInsight: String? = null) {
        val entry = LocalJournalEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            content = content,
            rating = rating,
            aiInsight = aiInsight,
            date = java.time.LocalDate.now().toString()
        )
        journalDao.insertEntry(entry)
        
        // Process entry through Memory Pipeline for long-term intelligence
        memoryPipeline?.processAndStore(userId, "Journal Entry: $content (Vibe: $rating/10)")
        
        // Update persistent streak
        val currentStreak = getCurrentStreak(userId)
        val existingStreak = streakDao.getStreak(userId)
        val longestStreak = maxOf(currentStreak, existingStreak?.longestStreak ?: 0)
        
        streakDao.updateStreak(
            LocalJournalStreak(
                userId = userId,
                currentStreak = currentStreak,
                lastEntryDate = entry.date,
                longestStreak = longestStreak
            )
        )
    }

    suspend fun getEntryForToday(): LocalJournalEntry? {
        return journalDao.getEntryByDate(java.time.LocalDate.now().toString())
    }

    suspend fun getCurrentStreak(userId: String): Int {
        val entries = journalDao.getEntriesByUserId(userId).firstOrNull() ?: return 0
        if (entries.isEmpty()) return 0
        
        val dates = entries.map { java.time.LocalDate.parse(it.date) }.sortedDescending()
        var streak = 0
        var currentDate = java.time.LocalDate.now()
        
        // If no entry today, check if yesterday was part of streak
        if (dates.first() < currentDate.minusDays(1)) return 0
        
        for (date in dates) {
            if (date == currentDate || date == currentDate.minusDays(1)) {
                streak++
                currentDate = date
            } else {
                break
            }
        }
        return streak
    }
    
    suspend fun getStreak(userId: String): LocalJournalStreak? {
        return streakDao.getStreak(userId)
    }
}
