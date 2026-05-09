package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.local.dao.FlowScoreDao
import com.todo.dailyroutine.data.local.entity.LocalFlowScore
import com.todo.dailyroutine.domain.FlowScoreCalculator
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import kotlinx.coroutines.flow.first

class FlowScoreRepository(private val flowScoreDao: FlowScoreDao) {
    fun getWeeklyTrend(userId: String): Flow<List<LocalFlowScore>> =
        flowScoreDao.getWeeklyTrend(userId)

    suspend fun getLatestScoreSync(): LocalFlowScore? = flowScoreDao.getLatestScore()

    suspend fun getScoreForDate(userId: String, date: String): LocalFlowScore? =
        flowScoreDao.getScoreByDate(userId, date)

    suspend fun saveDailyScore(
        userId: String,
        habitsCompleted: Int,
        totalHabits: Int,
        tasksCompleted: Int,
        totalTasks: Int,
        hasJournalEntry: Boolean,
        vibeRating: Int,
        aiInteractions: Int
    ) {
        val date = LocalDate.now().toString()
        val score = FlowScoreCalculator.calculateFlowScore(
            habitsCompleted, totalHabits, tasksCompleted, totalTasks,
            hasJournalEntry, vibeRating, aiInteractions
        )
        
        val localScore = LocalFlowScore(
            userId = userId,
            date = date,
            score = score,
            habitsCompleted = habitsCompleted,
            totalHabits = totalHabits,
            tasksCompleted = tasksCompleted,
            totalTasks = totalTasks,
            hasJournalEntry = hasJournalEntry,
            vibeRating = vibeRating
        )
        flowScoreDao.insertScore(localScore)
    }

    suspend fun getAverageScore(userId: String): Int {
        val scores = flowScoreDao.getWeeklyTrend(userId).first()
        if (scores.isEmpty()) return 0
        return scores.map { it.score }.average().toInt()
    }

    fun getRankTitle(score: Int): String {
        return when {
            score >= 90 -> "Arch-Oracle"
            score >= 80 -> "Oracle Link"
            score >= 60 -> "Neural Weaver"
            score >= 40 -> "Flow Adept"
            score >= 20 -> "Novice Sync"
            else -> "Protocol Initializing"
        }
    }
}
