package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.todo.dailyroutine.data.repository.JournalRepository
import com.todo.dailyroutine.data.local.entity.LocalJournalEntry
import com.todo.dailyroutine.util.VoiceToTextManager
import com.todo.dailyroutine.data.repository.AiRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

class JournalViewModel(
    private val repository: JournalRepository,
    private val voiceManager: VoiceToTextManager,
    private val aiRepository: AiRepository
) : ViewModel() {
    val entries: StateFlow<List<LocalJournalEntry>> = repository.getAllEntries()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isVoiceListening = voiceManager.isListening
    val voiceText = voiceManager.text

    private val _streak = MutableStateFlow(0)
    val streak = _streak.asStateFlow()

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
        }
    }

    private val _isEnhancing = MutableStateFlow(false)
    val isEnhancing = _isEnhancing.asStateFlow()

    fun enhanceEntry(content: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isEnhancing.value = true
            val prompt = """
                You are a premium performance coach and editor for FlowOS. 
                Enhance the following journal entry to be more professional, reflective, and insight-oriented.
                Maintain the core sentiment but improve vocabulary and structure.
                
                Content: $content
            """.trimIndent()
            
            aiRepository.chat(prompt).onSuccess { enhanced ->
                onResult(enhanced)
            }.onFailure {
                onResult("Enhancement failed, but you're doing great: $content")
            }
            _isEnhancing.value = false
        }
    }

    fun startVoiceRecording() {
        voiceManager.startListening()
    }

    fun stopVoiceRecording() {
        voiceManager.stopListening()
    }

    override fun onCleared() {
        super.onCleared()
        // Maybe don't destroy if shared, but usually fine
    }
}

class JournalViewModelFactory(
    private val repository: JournalRepository,
    private val voiceManager: VoiceToTextManager,
    private val aiRepository: AiRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return JournalViewModel(repository, voiceManager, aiRepository) as T
    }
}
