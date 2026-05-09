package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeepFlowState(
    val isActive: Boolean = false,
    val timeLeftSeconds: Int = 25 * 60, // 25 minutes default
    val totalSeconds: Int = 25 * 60,
    val phase: String = "Focus", // "Focus", "Break"
    val completedCycles: Int = 0
)

class DeepFlowViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DeepFlowState())
    val uiState: StateFlow<DeepFlowState> = _uiState.asStateFlow()
    
    private var timerJob: Job? = null

    fun startFlow(minutes: Int = 25) {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(
            isActive = true,
            timeLeftSeconds = minutes * 60,
            totalSeconds = minutes * 60,
            phase = "Focus"
        )
        runTimer()
    }

    fun stopFlow() {
        timerJob?.cancel()
        _uiState.value = _uiState.value.copy(isActive = false)
    }

    private fun runTimer() {
        timerJob = viewModelScope.launch {
            while (_uiState.value.timeLeftSeconds > 0) {
                delay(1000)
                _uiState.value = _uiState.value.copy(
                    timeLeftSeconds = _uiState.value.timeLeftSeconds - 1
                )
            }
            onTimerFinished()
        }
    }

    private fun onTimerFinished() {
        if (_uiState.value.phase == "Focus") {
            _uiState.value = _uiState.value.copy(
                phase = "Break",
                timeLeftSeconds = 5 * 60,
                totalSeconds = 5 * 60,
                completedCycles = _uiState.value.completedCycles + 1
            )
            runTimer()
        } else {
            startFlow() // Cycle back to Focus
        }
    }
}
