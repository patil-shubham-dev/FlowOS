package com.todo.dailyroutine.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.todo.dailyroutine.data.local.entity.LocalMemory
import com.todo.dailyroutine.domain.vector.VectorMemoryManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val results: List<LocalMemory> = emptyList(),
    val isLoading: Boolean = false,
    val selectedType: String? = null
)

class SearchViewModel(
    private val vectorMemoryManager: VectorMemoryManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
        if (query.length > 2) {
            performSearch(query)
        } else {
            _uiState.value = _uiState.value.copy(results = emptyList())
        }
    }

    fun onTypeSelected(type: String?) {
        _uiState.value = _uiState.value.copy(selectedType = type)
        performSearch(_uiState.value.query)
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val userId = "user_default"
            val rawResults = vectorMemoryManager.retrieveRelevantMemories(userId, query, limit = 15)
            
            val filtered = if (_uiState.value.selectedType != null) {
                rawResults.filter { it.type == _uiState.value.selectedType }
            } else {
                rawResults
            }
            
            _uiState.value = _uiState.value.copy(
                results = filtered,
                isLoading = false
            )
        }
    }
}

class SearchViewModelFactory(
    private val vectorMemoryManager: VectorMemoryManager
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return SearchViewModel(vectorMemoryManager) as T
    }
}
