package io.github.legandy.enigmabridge.timer

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.legandy.enigmabridge.receiversettings.EnigmaClient
import io.github.legandy.enigmabridge.receiversettings.Timer
import io.github.legandy.enigmabridge.main.MainActivity
import io.github.legandy.enigmabridge.settings.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TimerCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "TimerCheckWorker"
        private const val PREVIOUS_TIMERS_KEY = "PREVIOUS_TIMERS_LIST"
        private const val NOTIFIED_RECORDING_TIMERS_KEY = "NOTIFIED_RECORDING_TIMERS"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override suspend fun doWork(): Result {
        Log.d(WORK_TAG, "--- TimerCheckWorker doWork() STARTED ---")

        NotificationHelper.createNotificationChannel(applicationContext)

        val prefs = applicationContext.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) {
            Log.w(WORK_TAG, "Aborting timer sync: IP Address not configured.")
            NotificationHelper.sendTimerSyncFailedNotification(applicationContext)
            return Result.failure()
        }

        Log.d(WORK_TAG, "Attempting to connect to receiver at IP: $ip")
        val client = EnigmaClient(ip, user, pass, prefs)
        val currentTimers = withContext(Dispatchers.IO) {
            client.getTimerList()
        }

        if (currentTimers == null) {
            Log.e(WORK_TAG, "CRITICAL FAILURE: client.getTimerList() returned NULL. Aborting.")
            NotificationHelper.sendTimerSyncFailedNotification(applicationContext)
            return Result.failure()
        }

        Log.d(WORK_TAG, "Successfully fetched ${currentTimers.size} timers from the receiver.")
        // previousTimers are no longer used for recording start notification
        // findAndNotifyNewRecordings now handles time-based and uniqueness
        findAndNotifyNewRecordings(currentTimers, prefs)
        saveCurrentTimers(currentTimers, prefs)

        Log.d(WORK_TAG, "Updating LAST_TIMER_SYNC_TIMESTAMP preference.")
        prefs.edit().putLong("LAST_TIMER_SYNC_TIMESTAMP", System.currentTimeMillis()).apply()

        val notifyEnabled = prefs.getBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", true)
        Log.d(WORK_TAG, "Notification on success enabled: $notifyEnabled")
        if (notifyEnabled) {
            Log.d(WORK_TAG, "Calling sendTimerSyncSuccessNotification...")
            NotificationHelper.sendTimerSyncSuccessNotification(applicationContext, currentTimers.size)
        }

        Log.d(WORK_TAG, "Sending ACTION_TIMER_SYNC_COMPLETED broadcast.")
        val intent = Intent(MainActivity.Companion.ACTION_TIMER_SYNC_COMPLETED)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        Log.d(WORK_TAG, "--- TimerCheckWorker doWork() FINISHED SUCCESSFULLY ---")
        return Result.success()
    }

    private fun findAndNotifyNewRecordings(currentTimers: List<Timer>, prefs: SharedPreferences) {
        if (!prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)) {
            Log.d(WORK_TAG, "Recording started notifications are disabled in settings.")
            return
        }

        val notifiedRecordingTimers = prefs.getStringSet(NOTIFIED_RECORDING_TIMERS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()
        val currentTimeSeconds = System.currentTimeMillis() / 1000L
        val notificationWindowStart = currentTimeSeconds - 300L // 5 minutes ago
        val notificationWindowEnd = currentTimeSeconds + 60L // 1 minute from now

        Log.d(WORK_TAG, "Current Time (s): $currentTimeSeconds, Window Start: $notificationWindowStart, Window End: $notificationWindowEnd")

        // Cleanup old entries (timers that have ended a while ago)
        val newNotifiedRecordingTimers = mutableSetOf<String>()
        var cleanedUpCount = 0
        for (entry in notifiedRecordingTimers) {
            val parts = entry.split("_")
            if (parts.size == 2) {
                val sRef = parts[0]
                val beginTimestamp = parts[1].toLongOrNull()
                val correspondingTimer = currentTimers.firstOrNull { it.sRef == sRef && it.beginTimestamp == beginTimestamp }
                if (correspondingTimer != null && correspondingTimer.endTimestamp > currentTimeSeconds - (60 * 60 * 2)) { // Keep if end time is within 2 hours ago
                    newNotifiedRecordingTimers.add(entry)
                } else if (correspondingTimer == null) {
                    // If the timer is no longer in the current list, assume it's done
                    cleanedUpCount++
                }
            } else {
                // Malformed entry, just carry it over for now or handle appropriately
                newNotifiedRecordingTimers.add(entry)
            }
        }
        if (cleanedUpCount > 0) {
            Log.d(WORK_TAG, "Cleaned up $cleanedUpCount old notified timer entries.")
        }
        notifiedRecordingTimers.clear()
        notifiedRecordingTimers.addAll(newNotifiedRecordingTimers)

        for (currentTimer in currentTimers) {
            Log.d(WORK_TAG, "Processing timer: ${currentTimer.name}, State: ${currentTimer.state}, beginTimestamp: ${currentTimer.beginTimestamp}, endTimestamp: ${currentTimer.endTimestamp}")

            // Check if the timer's start time is within the notification window
            val isWithinWindow = currentTimer.beginTimestamp >= notificationWindowStart && currentTimer.beginTimestamp <= notificationWindowEnd
            val notificationKey = "${currentTimer.sRef}_${currentTimer.beginTimestamp}"

            if (isWithinWindow && !notifiedRecordingTimers.contains(notificationKey)) {
                Log.i(WORK_TAG, "Recording started notification criteria met for timer: ${currentTimer.name} (beginTimestamp: ${currentTimer.beginTimestamp})")
                NotificationHelper.sendRecordingStartedNotification(applicationContext, currentTimer.name, currentTimer.sName)
                notifiedRecordingTimers.add(notificationKey)
                Log.d(WORK_TAG, "Recording started notification sent for timer: ${currentTimer.name}.")
            } else if (notifiedRecordingTimers.contains(notificationKey)) {
                Log.d(WORK_TAG, "Timer already notified about: ${currentTimer.name} (beginTimestamp: ${currentTimer.beginTimestamp}).")
            }
        }

        prefs.edit().putStringSet(NOTIFIED_RECORDING_TIMERS_KEY, notifiedRecordingTimers).apply()
    }

    private fun saveCurrentTimers(timers: List<Timer>, prefs: SharedPreferences) {
        val jsonString = json.encodeToString<List<Timer>>(timers)
        prefs.edit().putString(PREVIOUS_TIMERS_KEY, jsonString).apply()
    }
}