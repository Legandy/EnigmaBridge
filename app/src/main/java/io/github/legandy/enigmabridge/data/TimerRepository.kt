package io.github.legandy.enigmabridge.data

import android.content.Context
import io.github.legandy.enigmabridge.core.AppEvent
import io.github.legandy.enigmabridge.core.AppEventBus
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.notifications.TimerNotificationManager
import io.github.legandy.enigmabridge.receiversettings.EnigmaClient
import io.github.legandy.enigmabridge.receiversettings.Timer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed class TimerResult<out T> {
    data class Success<out T>(val data: T) : TimerResult<T>()
    data class Error(val message: String, val exception: Throwable? = null) : TimerResult<Nothing>()
}

/**
 * Repository for managing Timer data.
 * Acts as a single source of truth for timer operations, bridging [EnigmaClient] and [PreferenceManager].
 */
class TimerRepository(context: Context, private val prefs: PreferenceManager) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val notificationManager = TimerNotificationManager(context, prefs)

    private val _timers = MutableStateFlow(loadTimersFromPrefs())
    val timers: StateFlow<List<Timer>> = _timers.asStateFlow()

    private fun getClient(): EnigmaClient = prefs.getEnigmaClient()

    private fun loadTimersFromPrefs(): List<Timer> {
        val jsonString = prefs.getPreviousTimersJson() ?: return emptyList()
        return try {
            json.decodeFromString<List<Timer>>(jsonString)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun refreshTimers(): TimerResult<List<Timer>> = withContext(Dispatchers.IO) {
        runCatching { getClient().getTimerList() }
            .fold(
                onSuccess = { fetchedTimers ->
                    prefs.setPreviousTimersJson(json.encodeToString(fetchedTimers))
                    prefs.setLastSyncTimestamp(System.currentTimeMillis())
                    _timers.value = fetchedTimers
                    
                    // Sync notifications based on the new list
                    notificationManager.syncNotifications(fetchedTimers)

                    // Notify the app that a sync has completed
                    AppEventBus.emit(AppEvent.TimerSyncCompleted)

                    TimerResult.Success(fetchedTimers)
                },
                onFailure = { TimerResult.Error("Network error: ${it.message}", it) }
            )
    }

    suspend fun getTimers(): TimerResult<List<Timer>> = withContext(Dispatchers.IO) {
        if (_timers.value.isEmpty()) {
            refreshTimers()
        } else {
            TimerResult.Success(_timers.value)
        }
    }

    suspend fun addTimer(
        title: String,
        sRef: String,
        startTime: Long,
        endTime: Long,
        description: String,
        justPlay: Int,
        repeated: Int,
        afterEvent: Int
    ): TimerResult<Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        runCatching {
            getClient().addTimer(title, sRef, startTime, endTime, description, justPlay, repeated, afterEvent)
        }.fold(
            onSuccess = { message ->
                refreshTimers()
                TimerResult.Success(true to message)
            },
            onFailure = {
                TimerResult.Error(it.message ?: "Unknown error", it)
            }
        )
    }

    suspend fun deleteTimer(timer: Timer): TimerResult<Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        runCatching { getClient().deleteTimer(timer) }
            .fold(
                onSuccess = { message ->
                    // Immediately cancel the local notification work before refreshing
                    notificationManager.cancelNotificationForTimer(timer)
                    refreshTimers()
                    TimerResult.Success(true to message)
                },
                onFailure = { TimerResult.Error("Network error: ${it.message}", it) }
            )
    }

    suspend fun editTimer(
        originalTimer: Timer,
        newTitle: String,
        newDescription: String,
        newStartTime: Long,
        newEndTime: Long,
        justPlay: Int,
        repeated: Int,
        afterEvent: Int,
        disabled: Int
    ): TimerResult<Pair<Boolean, String>> = withContext(Dispatchers.IO) {
        runCatching {
            getClient().editTimer(
                originalTimer, newTitle, newDescription, newStartTime, newEndTime,
                justPlay, repeated, afterEvent, disabled
            )
        }.fold(
            onSuccess = { message ->
                    // Cancel the old notification key and refresh will schedule the new one
                    notificationManager.cancelNotificationForTimer(originalTimer)
                    refreshTimers()
                    TimerResult.Success(true to message)
            },
            onFailure = { TimerResult.Error("Network error: ${it.message}", it) }
        )
    }
}
