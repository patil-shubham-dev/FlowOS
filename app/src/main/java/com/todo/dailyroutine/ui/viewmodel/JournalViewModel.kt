package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.todo.dailyroutine.data.repository.JournalRepository
import com.todo.dailyroutine.data.local.entity.LocalJournalEntry
import com.todo.dailyroutine.util.WhisperTranscriptionManager
import com.todo.dailyroutine.data.repository.AiRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class JournalViewModel(
    private val repository: JournalRepository,
    private val taskRepository: com.todo.dailyroutine.data.repository.TaskRepository,
    private val whisperManager: WhisperTranscriptionManager,
    private val aiRepository: AiRepository
) : ViewModel() {
    val entries: StateFlow<List<LocalJournalEntry>> = repository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isVoiceListening = whisperManager.isListening
    val voiceText = whisperManager.text

    private val _streak = MutableStateFlow(0)
    val streak = _streak.asStateFlow()

    private val _saveEvent = MutableSharedFlow<Unit>()
    val saveEvent = _saveEvent.asSharedFlow()

    init {
        loadStreak()
    }

    private fun loadStreak() {
        viewModelScope.launch {
            _streak.value = repository.getCurrentStreak("user")
        }
    }

    fun saveEntry(userId: String, content: String, rating: Int, aiInsight: String? = null) {
        viewModelScope.launch {
            repository.saveEntry(userId, content, rating, aiInsight)
            loadStreak()
            _saveEvent.emit(Unit)
        }
    }

    private val _isEnhancing = MutableStateFlow(false)
    val isEnhancing = _isEnhancing.asStateFlow()

    enum class RefineStyle { PROFESSIONAL, CREATIVE, CONCISE, ACADEMIC }

    fun refineEntry(content: String, style: RefineStyle, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isEnhancing.value = true
            val styleDescription = when(style) {
                RefineStyle.PROFESSIONAL -> "polished, clear, and growth-oriented"
                RefineStyle.CREATIVE -> "evocative, metaphorical, and deeply expressive"
                RefineStyle.CONCISE -> "brief, punchy, and high-impact"
                RefineStyle.ACADEMIC -> "analytical, objective, and structured"
            }
            
            val prompt = """
                You are a premium performance coach and editor for FlowOS. 
                Refine the following journal entry to be $styleDescription.
                Maintain the core sentiment but adapt the voice to the requested style.
                Return ONLY the refined content in HTML format (using <p>, <strong>, <em> etc if needed).
                
                Content: $content
            """.trimIndent()
            
            aiRepository.chat(prompt).onSuccess { enhanced ->
                onResult(enhanced)
            }.onFailure {
                onResult(content)
            }
            _isEnhancing.value = false
        }
    }

    fun enhanceEntry(content: String, onResult: (String) -> Unit) {
        refineEntry(content, RefineStyle.PROFESSIONAL, onResult)
    }

    fun summarizeEntry(content: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isEnhancing.value = true
            val prompt = "Summarize this journal entry into a single powerful sentence: $content"
            aiRepository.chat(prompt).onSuccess { onResult(it) }
            _isEnhancing.value = false
        }
    }

    fun extractActionItems(content: String, onResult: (List<String>) -> Unit) {
        viewModelScope.launch {
            _isEnhancing.value = true
            val prompt = """
                Extract clear, actionable tasks from this journal entry. 
                Return them as a JSON array of strings.
                Example: ["Buy milk", "Call mom"]
                If no tasks, return [].
                
                Content: $content
            """.trimIndent()
            
            aiRepository.chat(prompt, jsonMode = true).onSuccess { json ->
                try {
                    val array = org.json.JSONArray(json)
                    val tasks = mutableListOf<String>()
                    for (i in 0 until array.length()) {
                        val taskTitle = array.getString(i)
                        tasks.add(taskTitle)
                        taskRepository.addTask(taskTitle, "Uncategorized")
                    }
                    onResult(tasks)
                } catch (e: Exception) { onResult(emptyList()) }
            }
            _isEnhancing.value = false
        }
    }

    fun startVoiceRecording() {
        whisperManager.startListening()
    }

    fun stopVoiceRecording(onResult: (String) -> Unit) {
        viewModelScope.launch {
            whisperManager.stopListening()
            val text = whisperManager.text.value
            if (text.isNotEmpty()) {
                _isEnhancing.value = true
                // Multi-step sync: Refine text + Extract tasks
                val prompt = """
                    You are the FlowOS Thought Stream Processor.
                    Analyze this voice recording transcription: "$text"
                    1. Clean and refine the transcription for clarity and grammar.
                    2. Extract any actionable tasks mention in the speech.
                    Return ONLY a JSON object:
                    {
                      "refined": "The polished reflection",
                      "tasks": ["Task 1", "Task 2"]
                    }
                """.trimIndent()
                
                aiRepository.chat(prompt, jsonMode = true).onSuccess { json ->
                    try {
                        val obj = org.json.JSONObject(json)
                        val refined = obj.getString("refined")
                        val tasksArray = obj.getJSONArray("tasks")
                        for (i in 0 until tasksArray.length()) {
                            taskRepository.addTask(tasksArray.getString(i), "Uncategorized")
                        }
                        onResult(refined)
                    } catch (e: Exception) { onResult(text) }
                }.onFailure { onResult(text) }
                _isEnhancing.value = false
            }
        }
    }
}

class JournalViewModelFactory(
    private val repository: JournalRepository,
    private val taskRepository: com.todo.dailyroutine.data.repository.TaskRepository,
    private val whisperManager: WhisperTranscriptionManager,
    private val aiRepository: AiRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return JournalViewModel(repository, taskRepository, whisperManager, aiRepository) as T
    }
}
