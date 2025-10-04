package io.github.legandy.enigmabridge

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.JobIntentService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.runBlocking

class TimerCheckService : JobIntentService() {

    companion object {
        private const val JOB_ID = 1000
        private const val TAG = "TimerCheckService"
        private const val PREVIOUS_TIMERS_KEY = "PREVIOUS_TIMERS_LIST"

        // Helper function to enqueue work for this service.
        fun enqueueWork(context: Context, work: Intent) {
            enqueueWork(context, TimerCheckService::class.java, JOB_ID, work)
        }
    }

    // This is the main work that happens on a background thread.
    override fun onHandleWork(intent: Intent) {
        Log.d(TAG, "Background service is running a timer check.")
        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) {
            Log.w(TAG, "Aborting timer check: IP Address is not configured.")
            return
        }

        val client = EnigmaClient(ip, user, pass)
        val currentTimers: List<Timer>?

        // Run the network call synchronously within this background job.
        runBlocking {
            currentTimers = client.getTimerList()
        }

        if (currentTimers == null) {
            Log.e(TAG, "Failed to fetch current timer list from receiver.")
            return
        }

        // Compare the new list with the old one to find what's changed.
        val previousTimers = loadPreviousTimers()
        findAndNotifyNewRecordings(previousTimers, currentTimers)
        saveCurrentTimers(currentTimers)
    }

    // Loads the last-known list of timers from local storage.
    private fun loadPreviousTimers(): Map<String, Timer> {
        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val jsonString = prefs.getString(PREVIOUS_TIMERS_KEY, null)
        return if (jsonString != null) {
            val type = object : TypeToken<List<Timer>>() {}.type
            val timerList: List<Timer> = Gson().fromJson(jsonString, type)
            // Create a unique key for each timer to make lookups easy.
            timerList.associateBy { "${it.sRef}-${it.beginTimestamp}" }
        } else {
            emptyMap()
        }
    }

    // Checks for timers that have transitioned to the "recording" state.
    private fun findAndNotifyNewRecordings(previousTimers: Map<String, Timer>, currentTimers: List<Timer>) {
        for (currentTimer in currentTimers) {
            val key = "${currentTimer.sRef}-${currentTimer.beginTimestamp}"
            val previousTimer = previousTimers[key]

            // If a timer's state is now 2 (recording) and it wasn't before, send a notification.
            if (currentTimer.state == 2 && (previousTimer == null || previousTimer.state < 2)) {
                Log.d(TAG, "New recording detected: ${currentTimer.name}")
                NotificationHelper.sendRecordingStartedNotification(applicationContext, currentTimer)
            }
        }
    }

    // Saves the new list of timers to local storage for the next check.
    private fun saveCurrentTimers(timers: List<Timer>) {
        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val jsonString = Gson().toJson(timers)
        prefs.edit().putString(PREVIOUS_TIMERS_KEY, jsonString).apply()
        Log.d(TAG, "Saved ${timers.size} timers for next check.")
    }
}
