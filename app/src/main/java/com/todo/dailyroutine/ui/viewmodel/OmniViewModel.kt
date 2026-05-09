package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.todo.dailyroutine.data.model.ParsedIntent
import com.todo.dailyroutine.data.repository.*
import com.todo.dailyroutine.data.session.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.todo.dailyroutine.util.VoiceToTextManager
import com.todo.dailyroutine.data.local.entity.LocalJournalEntry
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class OmniState(
    val query: String = "",
    val isProcessing: Boolean = false,
    val lastResult: String? = null,
    val isOpen: Boolean = false,
    val isListening: Boolean = false
)

class OmniViewModel(
    private val aiRepository: AiRepository,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val sessionManager: SessionManager,
    private val journalRepository: JournalRepository,
    private val flowScoreRepository: FlowScoreRepository,
    private val voiceManager: VoiceToTextManager
) : ViewModel() {
    private val _uiState = MutableStateFlow(OmniState())
    val uiState: StateFlow<OmniState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            voiceManager.text.collectLatest { voicedText ->
                if (voicedText.isNotEmpty()) {
                    updateQuery(voicedText)
                }
            }
        }
        viewModelScope.launch {
            voiceManager.isListening.collectLatest { listening ->
                _uiState.value = _uiState.value.copy(isListening = listening)
            }
        }
    }

    fun open() { _uiState.value = _uiState.value.copy(isOpen = true, query = "", lastResult = null) }
    fun close() { 
        voiceManager.stopListening()
        _uiState.value = _uiState.value.copy(isOpen = false) 
    }
    fun updateQuery(q: String) { _uiState.value = _uiState.value.copy(query = q) }

    fun toggleVoice() {
        if (_uiState.value.isListening) {
            voiceManager.stopListening()
        } else {
            voiceManager.startListening()
        }
    }

    fun executeIntent() {
        val q = _uiState.value.query
        if (q.isBlank()) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isProcessing = true)
            val config = sessionManager.getAiConfig()
            
            // Build Context
            val context = buildSystemContext()
            
            val result = aiRepository.parseIntentWithContext(q, context, config)
            if (result.isSuccess) {
                val intent = result.getOrThrow()
                handleParsedIntent(intent)
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    lastResult = "Jarvis: ${intent.type} verified.",
                    query = ""
                )
                delay(1000)
                close()
            } else {
                _uiState.value = _uiState.value.copy(isProcessing = false, lastResult = "Signal interference. Try again.")
            }
        }
    }

    private suspend fun buildSystemContext(): com.todo.dailyroutine.data.model.SystemContext {
        val tasks = taskRepository.getAllTasksSync()
        val habits = habitRepository.getAllHabitsSync()
        val journals = journalRepository.getAllEntriesSync()
        val score = flowScoreRepository.getLatestScoreSync()
        
        return com.todo.dailyroutine.data.model.SystemContext(
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
            currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm, EEE dd MMM")),
            recentMoods = journals.take(5).map { it.rating.toString() }
        )
    }

    private suspend fun handleParsedIntent(intent: ParsedIntent) {
        when (intent.type) {
            "task" -> {
                taskRepository.addTask(
                    title = intent.title ?: "Untitled Task",
                    category = intent.category,
                    timeBlock = intent.timeBlock ?: "Morning"
                )
            }
            "habit" -> {
                habitRepository.addHabit(
                    name = intent.title ?: "Untitled Habit",
                    timeBlock = intent.timeBlock ?: "Morning"
                )
            }
            "search" -> {
                // Search handled by search UI
            }
        }
    }
}

class OmniViewModelFactory(
    private val aiRepository: AiRepository,
    private val taskRepository: TaskRepository,
    private val habitRepository: HabitRepository,
    private val sessionManager: SessionManager,
    private val journalRepository: JournalRepository,
    private val flowScoreRepository: FlowScoreRepository,
    private val voiceManager: VoiceToTextManager
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return OmniViewModel(
            aiRepository, 
            taskRepository, 
            habitRepository, 
            sessionManager,
            journalRepository,
            flowScoreRepository,
            voiceManager
        ) as T
    }
}
