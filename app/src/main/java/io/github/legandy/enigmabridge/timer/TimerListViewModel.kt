package io.github.legandy.enigmabridge.timer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import io.github.legandy.enigmabridge.data.TimerRepository
import io.github.legandy.enigmabridge.receiversettings.Timer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    val uiState: StateFlow<TimerListUiState> = repository.timers
        .map { timers ->
            TimerListUiState.Success(timers)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TimerListUiState.Loading
        )

    fun refresh() {
        viewModelScope.launch {
            repository.refreshTimers()
        }
    }

    fun deleteTimer(timer: Timer) {
        viewModelScope.launch {
            repository.deleteTimer(timer)
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
