package com.todo.dailyroutine.util

import android.content.Context
import com.todo.dailyroutine.data.repository.AiRepository
import com.todo.dailyroutine.data.repository.AiConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WhisperTranscriptionManager(
    private val context: Context,
    private val aiRepository: AiRepository,
    private val configRepository: AiConfigRepository
) {
    private val recorder = AudioRecorder(context)
    
    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _text = MutableStateFlow("")
    val text = _text.asStateFlow()

    fun startListening() {
        _isListening.value = true
        _text.value = ""
        recorder.startRecording()
    }

    suspend fun stopListening() = withContext(Dispatchers.IO) {
        val file = recorder.stopRecording()
        _isListening.value = false
        
        if (file != null && file.exists()) {
            val config = configRepository.getConfigs().getOrNull()?.find { it.isActive }
            val result = aiRepository.transcribeAudio(file, config)
            result.onSuccess { transcribed ->
                _text.value = transcribed
            }.onFailure {
                _text.value = "Transcription Error: ${it.message}"
            }
        }
    }
}
