package com.todo.dailyroutine.domain

import kotlin.math.min

object FlowScoreCalculator {
    fun calculateFlowScore(
        habitsCompleted: Int,
        totalHabits: Int,
        tasksCompleted: Int,
        totalTasks: Int,
        hasJournalEntry: Boolean,
        vibeRating: Int, // 1-10
        aiInteractions: Int
    ): Int {
        val habitScore = if (totalHabits > 0) 
            (habitsCompleted.toFloat() / totalHabits * 30).toInt() else 0
            
        val taskScore = if (totalTasks > 0)
            (tasksCompleted.toFloat() / totalTasks * 25).toInt() else 0
            
        val journalScore = if (hasJournalEntry) 20 else 0
        val vibeScore = (vibeRating * 2) // max 20
        val oracleBonus = min(aiInteractions, 5) // max 5
        
        return habitScore + taskScore + journalScore + vibeScore + oracleBonus
    }

    fun getScoreCategory(score: Int): String {
        return when (score) {
            in 0..39 -> "Scattered"
            in 40..59 -> "Building"
            in 60..79 -> "Flowing"
            else -> "Peak Flow"
        }
    }
}
