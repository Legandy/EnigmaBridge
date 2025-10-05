package io.github.legandy.enigmabridge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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

    // **DEFINITIVE FIX: Use the same lenient parser as EnigmaClient**
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
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
        findAndNotifyNewRecordings(previousTimers, currentTimers, prefs)
        saveCurrentTimers(currentTimers, prefs)

        return Result.success()
    }

    private fun loadPreviousTimers(prefs: SharedPreferences): Map<String, Timer> {
        val jsonString = prefs.getString(PREVIOUS_TIMERS_KEY, null)
        return if (jsonString != null) {
            try {
                // **DEFINITIVE FIX: Use Kotlinx Serialization to read the timer list**
                val timerList: List<Timer> = json.decodeFromString(jsonString)
                // Use a null-safe call for sRef
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
            Log.d(WORK_TAG, "Recording start notifications disabled. Skipping check.")
            return
        }

        for (currentTimer in currentTimers) {
            // Use a null-safe call for sRef
            val key = "${currentTimer.sRef}-${currentTimer.beginTimestamp}"
            val previousTimer = previousTimers[key]

            if (currentTimer.state == 2 && (previousTimer == null || previousTimer.state < 2)) {
                Log.i(WORK_TAG, "New recording detected: ${currentTimer.name}")
                NotificationHelper.sendRecordingStartedNotification(applicationContext, currentTimer)
            }
        }
    }

    private fun saveCurrentTimers(timers: List<Timer>, prefs: SharedPreferences) {
        // **DEFINITIVE FIX: Use Kotlinx Serialization to save the timer list**
        val jsonString = json.encodeToString(timers)
        prefs.edit().putString(PREVIOUS_TIMERS_KEY, jsonString).apply()
    }
}

