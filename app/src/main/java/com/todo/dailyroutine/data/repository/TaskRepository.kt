package com.todo.dailyroutine.data.repository

import com.todo.dailyroutine.data.local.dao.TaskDao
import com.todo.dailyroutine.data.local.entity.LocalTask
import com.todo.dailyroutine.data.local.toEntity
import com.todo.dailyroutine.data.local.toModel
import com.todo.dailyroutine.data.model.TaskItem
import com.todo.dailyroutine.data.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class TaskRepository(
    private val taskDao: TaskDao,
    private val sessionManager: SessionManager
) {
    val tasks: Flow<List<TaskItem>> = taskDao.getAllTasks().map { list -> list.map { it.toModel() } }

    suspend fun fetchTasks(): Result<Unit> = Result.success(Unit) // Local-only: no fetch needed


    suspend fun addTask(title: String, category: String, priority: Int = 2, energy: Int = 5, timeBlock: String = "Morning"): Result<Unit> = runCatching {
        val newTask = LocalTask(
            id = java.util.UUID.randomUUID().toString(),
            userId = sessionManager.userId(),
            title = title,
            category = category,
            completed = false,
            priority = priority,
            energyRequired = energy,
            timeBlock = timeBlock,
            syncStatus = 1 // PENDING_CREATE
        )
        taskDao.insertTask(newTask)
    }

    suspend fun toggleTask(taskId: String): Result<Unit> = runCatching {
        val tasks = taskDao.getAllTasks().first()
        val task = tasks.find { it.id == taskId }
        if (task != null) {
            taskDao.updateTask(task.copy(
                completed = !task.completed,
                syncStatus = 2,
                lastUpdated = System.currentTimeMillis()
            ))
        }
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
