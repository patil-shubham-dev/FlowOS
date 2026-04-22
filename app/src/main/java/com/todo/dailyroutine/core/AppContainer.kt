package com.todo.dailyroutine.core

import android.content.Context
import com.todo.dailyroutine.data.local.db.AppDatabase
import com.todo.dailyroutine.data.remote.ApiClient
import com.todo.dailyroutine.data.repository.*
import com.todo.dailyroutine.data.session.SessionManager
import com.todo.dailyroutine.notifications.FlowNotificationEngine
import com.todo.dailyroutine.domain.ai.*
import com.todo.dailyroutine.util.VoiceToTextManager
import com.todo.dailyroutine.ui.viewmodel.JournalViewModelFactory

class AppContainer(private val context: Context) {
    init {
        FlowNotificationEngine.createChannels(context)
    }

    val sessionManager by lazy { SessionManager(context) }
    val db by lazy { AppDatabase.getDatabase(context) }

    val authRepository by lazy { 
        AuthRepository(ApiClient.supabaseAuthApi, ApiClient.customAuthApi, sessionManager) 
    }

    val taskRepository by lazy { 
        TaskRepository(ApiClient.supabaseRestApi, db.taskDao(), sessionManager) 
    }

    val habitRepository by lazy { 
        HabitRepository(ApiClient.supabaseRestApi, db.habitDao(), sessionManager) 
    }

    val flowScoreRepository by lazy {
        FlowScoreRepository(db.flowScoreDao())
    }

    val journalRepository by lazy {
        JournalRepository(db.journalDao(), db.journalStreakDao())
    }

    val aiRepository by lazy { 
        AiRepository(ApiClient.aiStudioApi, ApiClient.universalAiApi) 
    }

    val journalViewModelFactory by lazy {
        JournalViewModelFactory(journalRepository, voiceToTextManager, aiRepository)
    }

    val aiConfigRepository by lazy { 
        AiConfigRepository(ApiClient.supabaseRestApi, db.aiConfigDao(), sessionManager) 
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
            db.memoryDao(),
            db.summaryDao()
        )
    }

    val aiScheduler by lazy { 
        com.todo.dailyroutine.domain.scheduling.AiScheduler(aiRepository) 
    }

    val notificationScheduler by lazy { 
        com.todo.dailyroutine.notifications.NotificationScheduler(context) 
    }

    val voiceToTextManager by lazy {
        VoiceToTextManager(context)
    }
}
