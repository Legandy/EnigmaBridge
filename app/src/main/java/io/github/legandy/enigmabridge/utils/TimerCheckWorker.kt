package io.github.legandy.enigmabridge.utils

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.legandy.enigmabridge.receiver.EnigmaClient
import io.github.legandy.enigmabridge.receiver.Timer
import io.github.legandy.enigmabridge.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class TimerCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "TimerCheckWorker"
        private const val PREVIOUS_TIMERS_KEY = "PREVIOUS_TIMERS_LIST"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override suspend fun doWork(): Result {
        Log.d(WORK_TAG, "--- TimerCheckWorker doWork() STARTED ---")

        // ** THE FIX 2: Ensure the notification channel exists before sending a notification **
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
        val previousTimers = loadPreviousTimers(prefs)
        findAndNotifyNewRecordings(previousTimers, currentTimers, prefs)
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
        val intent = Intent(MainActivity.ACTION_TIMER_SYNC_COMPLETED)
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        Log.d(WORK_TAG, "--- TimerCheckWorker doWork() FINISHED SUCCESSFULLY ---")
        return Result.success()
    }

    private fun loadPreviousTimers(prefs: SharedPreferences): Map<String, Timer> {
        val jsonString = prefs.getString(PREVIOUS_TIMERS_KEY, null)
        return if (jsonString != null) {
            try {
                val timerList: List<Timer> = json.decodeFromString<List<Timer>>(jsonString)
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
            return
        }

        for (currentTimer in currentTimers) {
            val key = "${currentTimer.sRef}-${currentTimer.beginTimestamp}"
            val previousTimer = previousTimers[key]

            if (currentTimer.state == 2 && (previousTimer == null || previousTimer.state < 2)) {
                Log.i(WORK_TAG, "New recording detected: ${currentTimer.name}")
                NotificationHelper.sendRecordingStartedNotification(applicationContext, currentTimer)
            }
        }
    }

    private fun saveCurrentTimers(timers: List<Timer>, prefs: SharedPreferences) {
        val jsonString = json.encodeToString<List<Timer>>(timers)
        prefs.edit().putString(PREVIOUS_TIMERS_KEY, jsonString).apply()
    }
}

