package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.BuildConfig
import com.todo.dailyroutine.data.local.dao.TaskDao
import com.todo.dailyroutine.data.local.entity.LocalTask
import com.todo.dailyroutine.data.local.toEntity
import com.todo.dailyroutine.data.local.toModel
import com.todo.dailyroutine.data.model.TaskItem
import com.todo.dailyroutine.data.remote.SupabaseRestApi
import com.todo.dailyroutine.data.remote.dto.TaskDto
import com.todo.dailyroutine.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val api: SupabaseRestApi,
    private val taskDao: TaskDao,
    private val sessionManager: SessionManager
) {
    private fun bearer() = "Bearer ${sessionManager.getToken()}"

    val tasks: Flow<List<TaskItem>> = taskDao.getAllTasks().map { list -> list.map { it.toModel() } }

    suspend fun fetchTasks(): Result<Unit> = runCatching {
        val remote = api.getTasks(
            apiKey = BuildConfig.SUPABASE_ANON_KEY,
            bearer = bearer(),
            userIdFilter = "eq.${sessionManager.getUserId()}"
        )
        taskDao.getAllTasks().first().forEach { local ->
             if (local.syncStatus == 0 && remote.none { it.id == local.id }) {
                 taskDao.hardDeleteTask(local)
             }
        }
        remote.forEach { dto ->
            taskDao.insertTask(LocalTask(dto.id!!, dto.userId, dto.title, dto.category, dto.completed, priority = dto.priority, syncStatus = 0))
        }
    }

    suspend fun addTask(title: String, category: String, energy: Int = 5): Result<Unit> = runCatching {
        val newTask = LocalTask(
            id = java.util.UUID.randomUUID().toString(),
            userId = sessionManager.userId(),
            title = title,
            category = category,
            completed = false,
            energyRequired = energy,
            syncStatus = 1 // PENDING_CREATE
        )
        taskDao.insertTask(newTask)
    }

    suspend fun toggleTask(task: TaskItem): Result<Unit> = runCatching {
        val local = task.toEntity().copy(
            completed = !task.completed,
            syncStatus = 2, // PENDING_UPDATE
            lastUpdated = System.currentTimeMillis()
        )
        taskDao.updateTask(local)
    }

    suspend fun updateTask(task: LocalTask): Result<Unit> = runCatching {
        taskDao.updateTask(task.copy(syncStatus = 2, lastUpdated = System.currentTimeMillis()))
    }

    suspend fun softDeleteTask(id: String): Result<Unit> = runCatching {
        taskDao.softDeleteTask(id)
    }
}
