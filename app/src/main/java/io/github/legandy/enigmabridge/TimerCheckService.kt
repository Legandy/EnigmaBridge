package io.github.legandy.enigmabridge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TimerCheckService(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "TimerCheckWorker"
        private const val PREVIOUS_TIMERS_KEY = "PREVIOUS_TIMERS_LIST"
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

        // Pass the prefs object to the client.
        val client = EnigmaClient(ip, user, pass, prefs)
        val currentTimers = withContext(Dispatchers.IO) {
            client.getTimerList()
        }

        if (currentTimers == null) {
            Log.e(WORK_TAG, "Failed to fetch current timer list.")
            return Result.retry()
        }

        // The critical fix: Pass the 'prefs' object to the helper functions.
        val previousTimers = loadPreviousTimers(prefs)
        findAndNotifyNewRecordings(previousTimers, currentTimers, prefs)
        saveCurrentTimers(currentTimers, prefs)

        return Result.success()
    }

    private fun loadPreviousTimers(prefs: SharedPreferences): Map<String, Timer> {
        val jsonString = prefs.getString(PREVIOUS_TIMERS_KEY, null)
        return if (jsonString != null) {
            val type = object : TypeToken<List<Timer>>() {}.type
            val timerList: List<Timer> = Gson().fromJson(jsonString, type)
            timerList.associateBy { "${it.sRef}-${it.beginTimestamp}" }
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
            val key = "${currentTimer.sRef}-${currentTimer.beginTimestamp}"
            val previousTimer = previousTimers[key]

            if (currentTimer.state == 2 && (previousTimer == null || previousTimer.state < 2)) {
                Log.i(WORK_TAG, "New recording detected: ${currentTimer.name}")
                NotificationHelper.sendRecordingStartedNotification(applicationContext, currentTimer)
            }
        }
    }

    private fun saveCurrentTimers(timers: List<Timer>, prefs: SharedPreferences) {
        val jsonString = Gson().toJson(timers)
        prefs.edit().putString(PREVIOUS_TIMERS_KEY, jsonString).apply()
    }
}

