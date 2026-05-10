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
import com.todo.dailyroutine.domain.vector.MemoryPipeline
import com.todo.dailyroutine.util.WhisperTranscriptionManager
import com.todo.dailyroutine.util.VoiceToTextManager

class AppContainer(private val context: Context) {
    init {
        FlowNotificationEngine.createChannels(context)
    }

    val sessionManager by lazy { SessionManager(context) }
    val db by lazy { AppDatabase.getDatabase(context) }

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

    val chatRepository by lazy {
        ChatRepository(db.messageDao())
    }

    val whisperTranscriptionManager by lazy {
        WhisperTranscriptionManager(context, aiRepository, sessionManager)
    }

    val vectorEngine by lazy { VectorEngine(context) }
    val vectorMemoryManager by lazy { VectorMemoryManager(db.memoryDao(), vectorEngine) }
    val memoryPipeline by lazy { MemoryPipeline(aiRepository, sessionManager, vectorMemoryManager) }

    val journalRepository by lazy {
        JournalRepository(db.journalDao(), db.journalStreakDao(), memoryPipeline)
    }

    val journalViewModelFactory by lazy {
        JournalViewModelFactory(journalRepository, taskRepository, whisperTranscriptionManager, aiRepository)
    }

    val oracleToolExecutor by lazy {
        OracleToolExecutor(taskRepository, habitRepository, journalRepository, flowScoreRepository, navigationManager)
    }

    val aiToolController by lazy {
        AiToolController(oracleToolExecutor, aiRepository, sessionManager)
    }

    val searchViewModelFactory by lazy {
        com.todo.dailyroutine.ui.viewmodel.SearchViewModelFactory(vectorMemoryManager)
    }

    val voiceToTextManager by lazy {
        VoiceToTextManager(context)
    }

    val navigationManager by lazy {
        com.todo.dailyroutine.util.AppNavigationManager()
    }

    val omniViewModelFactory by lazy {
        com.todo.dailyroutine.ui.viewmodel.OmniViewModelFactory(
            aiRepository,
            taskRepository,
            habitRepository,
            sessionManager,
            journalRepository,
            flowScoreRepository,
            voiceToTextManager
        )
    }

    val aiScheduler by lazy { 
        com.todo.dailyroutine.domain.scheduling.AiScheduler(aiRepository) 
    }

    val aiContextManager by lazy {
        com.todo.dailyroutine.domain.ai.AiContextManager(
            aiRepository,
            db.messageDao(),
            db.summaryDao(),
            vectorMemoryManager,
            memoryPipeline,
            taskRepository,
            habitRepository,
            journalRepository,
            flowScoreRepository
        )
    }

    val toolExecutionManager by lazy {
        com.todo.dailyroutine.domain.agent.ToolExecutionManager(
            taskRepository,
            habitRepository,
            journalRepository,
            aiScheduler,
            navigationManager
        )
    }

    val ttsManager by lazy {
        com.todo.dailyroutine.util.TtsManager(context)
    }

    val notificationScheduler by lazy { 
        com.todo.dailyroutine.notifications.NotificationScheduler(context) 
    }

    val bioAnalyticsRepository by lazy {
        BioAnalyticsRepository(db.bioDataDao())
    }
}
