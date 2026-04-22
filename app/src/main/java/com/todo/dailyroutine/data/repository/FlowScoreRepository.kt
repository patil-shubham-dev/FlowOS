package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.local.dao.FlowScoreDao
import com.todo.dailyroutine.data.local.entity.LocalFlowScore
import com.todo.dailyroutine.domain.FlowScoreCalculator
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class FlowScoreRepository(private val flowScoreDao: FlowScoreDao) {
    fun getWeeklyTrend(userId: String): Flow<List<LocalFlowScore>> =
        flowScoreDao.getWeeklyTrend(userId)

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
}
