package com.todo.dailyroutine.notifications

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.todo.dailyroutine.DailyRoutineApp
import com.todo.dailyroutine.R
import kotlinx.coroutines.flow.firstOrNull
import java.time.LocalDate

class OracleWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DailyRoutineApp
        val container = app.container
        
        val userId = container.sessionManager.getUserId() ?: "local_user"
        val config = container.aiConfigRepository.getActiveConfig() ?: return Result.failure()

        // 1. Build Deep Context
        val tasks = container.taskRepository.getAllTasksSync()
        val habits = container.habitRepository.getAllHabitsSync()
        val journals = container.journalRepository.getAllEntriesSync()
        val score = container.flowScoreRepository.getLatestScoreSync()
        
        val context = com.todo.dailyroutine.data.model.SystemContext(
            tasks = tasks,
            habits = habits,
            journalEntries = journals.map { 
                com.todo.dailyroutine.data.model.JournalEntry(
                    id = it.id,
                    userId = it.userId,
                    content = it.content,
                    rating = it.rating,
                    aiInsight = it.aiInsight,
                    date = it.date,
                    timestamp = it.timestamp
                )
            },
            flowScore = score?.score ?: 0,
            userLevel = "Vanguard",
            currentTime = java.time.LocalDateTime.now().toString(),
            recentMoods = journals.take(5).map { it.rating.toString() }
        )

        // 2. Generate Oracle Insight with Deep Context
        val oraclePrompt = """
            You are the Jarvis AI core of FlowOS. Perform a "System Audit" on the user's current operational status.
            
            CONTEXTUAL TELEMETRY:
            - Current State: ${container.aiRepository.formatSystemContext(context)}
            
            MISSION OBJECTIVE:
            Generate a HIGH-FIDELITY, PROACTIVE NUDGE. 
            Target the most critical divergence in their current flow:
            - High-energy task backlog? Inject momentum.
            - Low focus/vibe? Restore baseline via Stoic/Agentic perspective.
            - Ritual at risk? Issue a "Protocol Guard" warning.
            
            TONE: Elite, precise, agentic. No fluff.
            FORMAT: Single sentence. Max 18 words.
        """.trimIndent()

        val insightResult = container.aiRepository.chat(oraclePrompt, config)
        val insight = insightResult.getOrNull() ?: "Flow intelligence active. System optimized."

        // 3. Post Notification
        postNotification(insight)

        return Result.success()
    }

    private fun postNotification(content: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val notification = NotificationCompat.Builder(applicationContext, FlowNotificationEngine.CHANNEL_BRIEFING)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Fallback icon
            .setContentTitle("Oracle Neural Briefing")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        manager.notify(999, notification)
    }

    companion object {
        const val WORK_NAME = "oracle_dreaming_work"
    }
}
