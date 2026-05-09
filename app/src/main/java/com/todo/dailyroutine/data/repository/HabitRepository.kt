package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.local.dao.HabitDao
import com.todo.dailyroutine.data.local.entity.LocalHabit
import com.todo.dailyroutine.data.local.toEntity
import com.todo.dailyroutine.data.local.toModel
import com.todo.dailyroutine.data.model.HabitItem
import com.todo.dailyroutine.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class HabitRepository(
    private val habitDao: HabitDao,
    private val sessionManager: SessionManager
) {
    val habits: Flow<List<HabitItem>> = habitDao.getAllHabits().map { list -> list.map { it.toModel() } }

    suspend fun getAllHabitsSync(): List<HabitItem> = habitDao.getAllHabits().first().map { it.toModel() }

    suspend fun fetchHabits(): Result<Unit> = Result.success(Unit) // Local-only: no fetch needed


    suspend fun addHabit(name: String, timeBlock: String = "Morning"): Result<Unit> = runCatching {
        val newHabit = LocalHabit(
            id = java.util.UUID.randomUUID().toString(),
            userId = sessionManager.userId(),
            name = name,
            streak = 0,
            completedToday = false,
            timeBlock = timeBlock,
            syncStatus = 1
        )
        habitDao.insertHabit(newHabit)
    }

    suspend fun toggleHabit(habit: HabitItem): Result<Unit> = runCatching {
        val nextCompleted = !habit.completedToday
        val nextStreak = if (nextCompleted) habit.streak + 1 else (habit.streak - 1).coerceAtLeast(0)
        val local = habit.toEntity().copy(
            completedToday = nextCompleted,
            streak = nextStreak,
            syncStatus = 2,
            lastUpdated = System.currentTimeMillis()
        )
        habitDao.insertHabit(local)
    }

    suspend fun toggleHabit(habitId: String): Result<Unit> = runCatching {
        val habits = habitDao.getAllHabits().first()
        val habit = habits.find { it.id == habitId }
        if (habit != null) {
            val nextCompleted = !habit.completedToday
            val nextStreak = if (nextCompleted) habit.streak + 1 else (habit.streak - 1).coerceAtLeast(0)
            habitDao.insertHabit(habit.copy(
                completedToday = nextCompleted,
                streak = nextStreak,
                syncStatus = 2,
                lastUpdated = System.currentTimeMillis()
            ))
        }
    }

    suspend fun deleteHabit(habitId: String): Result<Unit> = runCatching {
        // Mark as deleted by updating syncStatus to 3
        val habits = habitDao.getAllHabits().first()
        val habit = habits.find { it.id == habitId }
        if (habit != null) {
            habitDao.insertHabit(habit.copy(syncStatus = 3))
        }
    }

    suspend fun clearAllHabits() {
        habitDao.getAllHabits().first().forEach {
            habitDao.insertHabit(it.copy(syncStatus = 3))
        }
    }
}
