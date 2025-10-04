package io.github.legandy.enigmabridge

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimerCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        // Unique tag for the WorkManager job.
        const val WORK_TAG = "TimerCheckWorker"
        // Key for storing the last known timer list in SharedPreferences.
        private const val PREVIOUS_TIMERS_KEY = "PREVIOUS_TIMERS_LIST"
    }

    override suspend fun doWork(): Result {
        Log.d(WORK_TAG, "Periodic check running.")
        val prefs = applicationContext.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        // Abort if IP is not configured.
        if (ip.isEmpty()) {
            Log.w(WORK_TAG, "Aborting: IP Address not configured.")
            return Result.failure()
        }

        // Fetch the current list of timers from the receiver.
        val client = EnigmaClient(ip, user, pass)
        val currentTimers = withContext(Dispatchers.IO) {
            client.getTimerList()
        }

        // If the fetch fails, retry the job later.
        if (currentTimers == null) {
            Log.e(WORK_TAG, "Failed to fetch current timer list.")
            return Result.retry()
        }

        // Compare the new list with the old one to find changes.
        val previousTimers = loadPreviousTimers()
        findAndNotifyNewRecordings(previousTimers, currentTimers)
        saveCurrentTimers(currentTimers)

        // Indicate that the work finished successfully.
        return Result.success()
    }

    // Loads the previously stored timer list from SharedPreferences.
    private fun loadPreviousTimers(): Map<String, Timer> {
        val prefs = applicationContext.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val jsonString = prefs.getString(PREVIOUS_TIMERS_KEY, null)
        return if (jsonString != null) {
            val type = object : TypeToken<List<Timer>>() {}.type
            val timerList: List<Timer> = Gson().fromJson(jsonString, type)
            // Use a map for efficient lookups based on a unique key.
            timerList.associateBy { "${it.sRef}-${it.beginTimestamp}" }
        } else {
            emptyMap()
        }
    }

    // Compares timer states and sends a notification if a recording has started.
    private fun findAndNotifyNewRecordings(previousTimers: Map<String, Timer>, currentTimers: List<Timer>) {
        // Check user preference before proceeding.
        val prefs = applicationContext.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)) {
            Log.d(WORK_TAG, "Recording start notifications disabled. Skipping check.")
            return
        }

        for (currentTimer in currentTimers) {
            val key = "${currentTimer.sRef}-${currentTimer.beginTimestamp}"
            val previousTimer = previousTimers[key]

            // A recording has started if the state is 2 (recording) and the previous state was not 2.
            if (currentTimer.state == 2 && (previousTimer == null || previousTimer.state < 2)) {
                Log.i(WORK_TAG, "New recording detected: ${currentTimer.name}")
                NotificationHelper.sendRecordingStartedNotification(applicationContext, currentTimer)
            }
        }
    }

    // Saves the current timer list to SharedPreferences for the next check.
    private fun saveCurrentTimers(timers: List<Timer>) {
        val prefs = applicationContext.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val jsonString = Gson().toJson(timers)
        prefs.edit().putString(PREVIOUS_TIMERS_KEY, jsonString).apply()
    }
}

