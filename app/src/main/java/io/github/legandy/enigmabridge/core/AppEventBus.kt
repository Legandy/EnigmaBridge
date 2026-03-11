package io.github.legandy.enigmabridge.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.tvbrowser.devplugin.Program

/**
 * A global event bus using Kotlin SharedFlow to replace the deprecated LocalBroadcastManager.
 * This allows for type-safe, reactive communication between different components of the app.
 */
object AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    /**
     * Emits a new event to all active collectors.
     */
    fun emit(event: AppEvent) {
        _events.tryEmit(event)
    }
}

/**
 * Sealed class representing all possible internal app events.
 */
sealed class AppEvent {
    /**
     * Fired when the timer list has been updated on the receiver.
     */
    object TimerListChanged : AppEvent()

    /**
     * Fired when a background timer sync has completed.
     */
    object TimerSyncCompleted : AppEvent()

    /**
     * Fired when a program marking needs to be reverted (e.g., if scheduling failed).
     */
    data class RevertMarking(val program: Program) : AppEvent()
}
