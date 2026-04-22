package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.BuildConfig
import com.todo.dailyroutine.data.local.dao.HabitDao
import com.todo.dailyroutine.data.local.entity.LocalHabit
import com.todo.dailyroutine.data.local.toEntity
import com.todo.dailyroutine.data.local.toModel
import com.todo.dailyroutine.data.model.HabitItem
import com.todo.dailyroutine.data.remote.SupabaseRestApi
import com.todo.dailyroutine.data.remote.dto.HabitDto
import com.todo.dailyroutine.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class HabitRepository(
    private val api: SupabaseRestApi,
    private val habitDao: HabitDao,
    private val sessionManager: SessionManager
) {
    private fun bearer() = "Bearer ${sessionManager.getToken()}"

    val habits: Flow<List<HabitItem>> = habitDao.getAllHabits().map { list -> list.map { it.toModel() } }

    suspend fun fetchHabits(): Result<Unit> = runCatching {
        val remote = api.getHabits(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            bearer = bearer(),
            userIdFilter = "eq.${sessionManager.userId()}"
        )
        habitDao.getAllHabits().first().forEach { local ->
            if (local.syncStatus == 0 && remote.none { it.id == local.id }) {
                habitDao.hardDeleteHabit(local)
            }
        }
        remote.forEach { dto ->
            habitDao.insertHabit(LocalHabit(dto.id!!, dto.userId, dto.name, dto.streak, dto.completedToday, syncStatus = 0))
        }
    }

    suspend fun addHabit(name: String): Result<Unit> = runCatching {
        val newHabit = LocalHabit(
            id = java.util.UUID.randomUUID().toString(),
            userId = sessionManager.userId(),
            name = name,
            streak = 0,
            completedToday = false,
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
}
