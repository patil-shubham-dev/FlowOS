package com.todo.dailyroutine.core

import android.content.Context
import com.todo.dailyroutine.data.local.db.AppDatabase
import com.todo.dailyroutine.data.remote.ApiClient
import com.todo.dailyroutine.data.repository.*
import com.todo.dailyroutine.data.session.SessionManager
import com.todo.dailyroutine.notifications.FlowNotificationEngine
import com.todo.dailyroutine.domain.ai.*
import com.todo.dailyroutine.ui.viewmodel.JournalViewModelFactory
import com.todo.dailyroutine.domain.vector.VectorEngine
import com.todo.dailyroutine.domain.vector.VectorMemoryManager
import com.todo.dailyroutine.util.WhisperTranscriptionManager

class AppContainer(private val context: Context) {
    init {
        FlowNotificationEngine.createChannels(context)
    }

    val sessionManager by lazy { SessionManager(context) }
    val db by lazy { AppDatabase.getDatabase(context) }

    val authRepository by lazy { 
        AuthRepository(sessionManager) 
    }

    val taskRepository by lazy { 
        TaskRepository(db.taskDao(), sessionManager) 
    }

    val habitRepository by lazy { 
        HabitRepository(db.habitDao(), sessionManager) 
    }

    val flowScoreRepository by lazy {
        FlowScoreRepository(db.flowScoreDao())
    }

    val aiRepository by lazy { 
        AiRepository(ApiClient.aiStudioApi, ApiClient.universalAiApi) 
    }

    val aiConfigRepository by lazy { 
        AiConfigRepository(db.aiConfigDao(), sessionManager) 
    }

    val whisperTranscriptionManager by lazy {
        WhisperTranscriptionManager(context, aiRepository, aiConfigRepository)
    }

    val vectorEngine by lazy { VectorEngine(context) }
    val vectorMemoryManager by lazy { VectorMemoryManager(db.memoryDao(), vectorEngine) }
    val memoryPipeline by lazy { MemoryPipeline(aiRepository, vectorMemoryManager) }

    val journalRepository by lazy {
        JournalRepository(db.journalDao(), db.journalStreakDao(), memoryPipeline)
    }

    val journalViewModelFactory by lazy {
        JournalViewModelFactory(journalRepository, whisperTranscriptionManager, aiRepository)
    }

    val oracleToolExecutor by lazy {
        OracleToolExecutor(taskRepository, habitRepository, journalRepository, flowScoreRepository)
    }

    val aiToolController by lazy {
        AiToolController(oracleToolExecutor, aiRepository, sessionManager)
    }

    val aiContextManager by lazy { 
        AiContextManager(
            aiRepository,
            db.messageDao(),
            db.summaryDao(),
            vectorMemoryManager,
            memoryPipeline
        )
    }

    val aiScheduler by lazy { 
        com.todo.dailyroutine.domain.scheduling.AiScheduler(aiRepository) 
    }

    val notificationScheduler by lazy { 
        com.todo.dailyroutine.notifications.NotificationScheduler(context) 
    }

    val bioAnalyticsRepository by lazy {
        BioAnalyticsRepository(db.bioDataDao())
    }
}
