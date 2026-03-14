package io.github.legandy.enigmabridge.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.tvbrowser.devplugin.Program

// Communication bridge inside the app
object AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun emit(event: AppEvent) {
        _events.tryEmit(event)
    }
}

sealed class AppEvent {

    // Timer list updated on the broadcasts
    object TimerListChanged : AppEvent()

    // Background timer sync completed
    object TimerSyncCompleted : AppEvent()

    // Mark program
    data class MarkProgram(val program: Program) : AppEvent()

    // Unmark program
    data class RevertMarking(val program: Program) : AppEvent()
}
