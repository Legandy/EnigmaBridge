package io.github.legandy.enigmabridge.timer

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.legandy.enigmabridge.receiversettings.EnigmaClient
import io.github.legandy.enigmabridge.receiversettings.Timer
import io.github.legandy.enigmabridge.helpers.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TimerCheckService(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "TimerCheckWorker"
        private const val PREVIOUS_TIMERS_KEY = "PREVIOUS_TIMERS_LIST"
        private const val NOTIFIED_RECORDING_TIMERS_KEY = "NOTIFIED_RECORDING_TIMERS"
    }

    override suspend fun doWork(): Result {
        Log.d(WORK_TAG, "Periodic check running.")
        val prefs = applicationContext.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) {
            Log.w(WORK_TAG, "Aborting: IP Address not configured.")
            return Result.failure()
        }

        val client = EnigmaClient(ip, user, pass, prefs)
        val currentTimers = withContext(Dispatchers.IO) {
            client.getTimerList()
        }

        if (currentTimers == null) {
            Log.e(WORK_TAG, "Failed to fetch current timer list.")
            return Result.retry()
        }

        val previousTimers = loadPreviousTimers(prefs)
        findAndNotifyNewRecordings(previousTimers, currentTimers, prefs) // Keep this for state-based recording start detection if needed
        saveCurrentTimers(currentTimers, prefs)

        triggerTimeBasedRecordingNotifications(currentTimers, prefs)

        return Result.success()
    }

    private fun loadPreviousTimers(prefs: SharedPreferences): Map<String, Timer> {
        val jsonString = prefs.getString(PREVIOUS_TIMERS_KEY, null)
        return if (jsonString != null) {
            try {
                val timerList: List<Timer> = Json.Default.decodeFromString(jsonString)
                timerList.associateBy { "${it.sRef}-${it.beginTimestamp}" }
            } catch (e: Exception) {
                Log.e(WORK_TAG, "Failed to parse previous timers JSON.", e)
                emptyMap()
            }
        } else {
            emptyMap()
        }
    }

    private fun findAndNotifyNewRecordings(previousTimers: Map<String, Timer>, currentTimers: List<Timer>, prefs: SharedPreferences) {
        if (!prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)) {
            Log.d(WORK_TAG, "Recording start notifications disabled. Skipping check for state-based detection.")
            return
        }

        for (currentTimer in currentTimers) {
            val key = "${currentTimer.sRef}-${currentTimer.beginTimestamp}"
            val previousTimer = previousTimers[key]

            // This logic is for state-based detection, which was previously deemed incorrect for "recording started"
            // Keeping it for now but the time-based trigger is the primary for "recording started" notifications
            if (currentTimer.state == 2 && (previousTimer == null || previousTimer.state < 2)) {
                Log.i(WORK_TAG, "New recording detected via state change: ${currentTimer.name}")
                // This call might need adjustment based on final NotificationHelper signature
                // NotificationHelper.sendRecordingStartedNotification(applicationContext, currentTimer.name, currentTimer.sName)
            }
        }
    }

    private fun triggerTimeBasedRecordingNotifications(currentTimers: List<Timer>, prefs: SharedPreferences) {
        if (!prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)) {
            Log.d(WORK_TAG, "Recording start notifications disabled. Skipping time-based check.")
            return
        }

        val currentTimeSeconds = System.currentTimeMillis() / 1000L
        val notificationWindowStart = currentTimeSeconds - 300L // 5 minutes ago
        val notificationWindowEnd = currentTimeSeconds + 60L    // 1 minute from now

        val notifiedRecordingTimers = prefs.getStringSet(NOTIFIED_RECORDING_TIMERS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

        val editor = prefs.edit()
        val currentNotifiedTimersSnapshot = notifiedRecordingTimers.toSet() // Snapshot for iteration

        // Cleanup old entries
        for (notifiedKey in currentNotifiedTimersSnapshot) {
            val parts = notifiedKey.split("_")
            if (parts.size == 2) {
                val sRef = parts[0]
                val beginTimestamp = parts[1].toLongOrNull()

                val correspondingCurrentTimer = currentTimers.find { it.sRef == sRef && it.beginTimestamp == beginTimestamp }

                // If the timer is no longer in current timers or its end time has passed, remove it from notified
                if (correspondingCurrentTimer == null) {
                    notifiedRecordingTimers.remove(notifiedKey)
                    Log.d(WORK_TAG, "Cleaning up old notified timer entry: $notifiedKey")
                } else if (correspondingCurrentTimer.endTimestamp < currentTimeSeconds) {
                    notifiedRecordingTimers.remove(notifiedKey)
                    Log.d(WORK_TAG, "Cleaning up old notified timer entry (end time passed): $notifiedKey")
                }
            } else {
                // Handle malformed keys by removing them
                notifiedRecordingTimers.remove(notifiedKey)
            }
        }


        for (currentTimer in currentTimers) {
            val uniqueKey = "${currentTimer.sRef}_${currentTimer.beginTimestamp}"

            Log.d(WORK_TAG, "Processing timer: ${currentTimer.name}, State: ${currentTimer.state}, beginTimestamp: ${currentTimer.beginTimestamp}, endTimestamp: ${currentTimer.endTimestamp}")


            if (currentTimer.beginTimestamp in notificationWindowStart..notificationWindowEnd) {
                if (!notifiedRecordingTimers.contains(uniqueKey)) {
                    Log.i(WORK_TAG, "Recording started notification criteria met for timer: ${currentTimer.name} (beginTimestamp: ${currentTimer.beginTimestamp})")
                    NotificationHelper.sendRecordingStartedNotification(applicationContext, currentTimer.name, currentTimer.sName)
                    notifiedRecordingTimers.add(uniqueKey)
                    Log.d(WORK_TAG, "Recording started notification sent for timer: ${currentTimer.name}.")
                } else {
                    Log.d(WORK_TAG, "Timer already notified about: ${currentTimer.name}.")
                }
            }
        }

        editor.putStringSet(NOTIFIED_RECORDING_TIMERS_KEY, notifiedRecordingTimers).apply()
    }

    private fun saveCurrentTimers(timers: List<Timer>, prefs: SharedPreferences) {
        val jsonString = Json.encodeToString(timers)
        prefs.edit().putString(PREVIOUS_TIMERS_KEY, jsonString).apply()
    }
}