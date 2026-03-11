package io.github.legandy.enigmabridge.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.legandy.enigmabridge.data.TimerRepository
import io.github.legandy.enigmabridge.data.TimerResult
import io.github.legandy.enigmabridge.receiversettings.Timer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class TimerListUiState {
    object Loading : TimerListUiState()
    data class Success(val timers: List<Timer>) : TimerListUiState()
    data class Error(val message: String) : TimerListUiState()
}

/**
 * ViewModel for the Timer List.
 * Observes the [TimerRepository] and provides a [TimerListUiState] for the Activity.
 */
class TimerListViewModel(private val repository: TimerRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<TimerListUiState>(TimerListUiState.Loading)
    val uiState: StateFlow<TimerListUiState> = _uiState.asStateFlow()

    init {
        // Observe the repository's timer flow and update the UI state accordingly.
        viewModelScope.launch {
            repository.timers.collect { timers ->
                _uiState.value = TimerListUiState.Success(timers)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val result = repository.refreshTimers()
            if (result is TimerResult.Error) {
                _uiState.value = TimerListUiState.Error(result.message)
            }
        }
    }

    fun deleteTimer(timer: Timer) {
        viewModelScope.launch {
            val result = repository.deleteTimer(timer)
            if (result is TimerResult.Error) {
                _uiState.value = TimerListUiState.Error(result.message)
            }
        }
    }

    class Factory(private val repository: TimerRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(TimerListViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return TimerListViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
