package com.todo.dailyroutine.notifications

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import com.todo.dailyroutine.DailyRoutineApp
import com.todo.dailyroutine.R
import com.todo.dailyroutine.data.model.UserApiConfig
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.data.repository.TaskRepository
import com.todo.dailyroutine.data.repository.HabitRepository

class OracleNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as com.todo.dailyroutine.DailyRoutineApp
        val container = app.container
        
        val aiRepository = container.aiRepository
        val taskRepo = container.taskRepository
        val habitRepo = container.habitRepository
        
        // Fetch current state for AI context
        val pendingTasks = taskRepo.tasks.first().filter { !it.completed }
        val pendingHabits = habitRepo.habits.first().filter { !it.completedToday }
        
        val prompt = """
            You are the Oracle for FlowOS. The user has ${pendingTasks.size} tasks and ${pendingHabits.size} habits pending.
            Generate a high-performance, short nudge (max 10 words) to get them into flow.
            Target channel: ${inputData.getString("channel") ?: "General"}
        """.trimIndent()
        
        val nudge = aiRepository.chat(prompt).getOrNull() ?: "Protocol check: synchronization required."
        
        showNotification(
            inputData.getString("channel") ?: FlowNotificationEngine.CHANNEL_GENERAL,
            "Oracle",
            nudge
        )
        
        return Result.success()
    }

    private fun showNotification(channelId: String, title: String, body: String) {
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use app icon
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
            
        NotificationManagerCompat.from(applicationContext).notify(System.currentTimeMillis().toInt(), notification)
    }
}
