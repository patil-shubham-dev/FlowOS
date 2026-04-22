package com.todo.dailyroutine.data.local

import com.todo.dailyroutine.BuildConfig
import com.todo.dailyroutine.data.local.dao.TaskDao
import com.todo.dailyroutine.data.local.dao.HabitDao
import com.todo.dailyroutine.data.remote.SupabaseRestApi
import com.todo.dailyroutine.data.session.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncManager(
    private val api: SupabaseRestApi,
    private val taskDao: TaskDao,
    private val habitDao: HabitDao,
    private val sessionManager: SessionManager
) {
    private fun bearer() = "Bearer ${sessionManager.token()}"

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        syncTasks()
        // syncHabits() // Add similarly
    }

    private suspend fun syncTasks() {
        val unsynced = taskDao.getUnsyncedTasks()
        unsynced.forEach { task ->
            try {
                when (task.syncStatus) {
                    1 -> { // PENDING_CREATE
                        api.createTask(
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            bearer = bearer(),
                            body = com.todo.dailyroutine.data.remote.dto.TaskDto(
                                userId = task.userId, 
                                title = task.title, 
                                category = task.category, 
                                completed = task.completed
                            )
                        )
                    }
                    2 -> { // PENDING_UPDATE
                         api.updateTask(
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            bearer = bearer(),
                            idFilter = "eq.${task.id}",
                            body = mapOf("completed" to task.completed)
                        )
                    }
                    3 -> { // PENDING_DELETE
                        api.deleteTask(
                            apiKey = BuildConfig.SUPABASE_ANON_KEY,
                            bearer = bearer(),
                            idFilter = "eq.${task.id}"
                        )
                    }
                }
                taskDao.insertTask(task.copy(syncStatus = 0)) // Mark as synced
            } catch (e: Exception) {
                // Network error, will retry later
            }
        }
    }
}
