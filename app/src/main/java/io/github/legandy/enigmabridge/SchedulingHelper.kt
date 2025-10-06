package io.github.legandy.enigmabridge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

object SchedulingHelper {

    private const val TAG = "SchedulingHelper"

    suspend fun scheduleTimer(
        context: Context,
        title: String,
        sRef: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        description: String,
        justPlay: Int,
        repeated: Int,
        afterEvent: Int
    ): Pair<Boolean, String> {
        val prefs = context.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)

        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) {
            return Pair(false, "IP Address not configured.")
        }

        val (finalStartTimeSeconds, finalEndTimeSeconds) = applyBuffer(prefs, startTimeMillis, endTimeMillis)

        Log.d(TAG, "Scheduling timer with buffer. Original: ${startTimeMillis/1000} -> ${endTimeMillis/1000}. Buffered: $finalStartTimeSeconds -> $finalEndTimeSeconds")

        val client = EnigmaClient(ip, user, pass, prefs)
        return client.addTimer(title, sRef, finalStartTimeSeconds, finalEndTimeSeconds, description, justPlay, repeated, afterEvent)
    }

    private fun applyBuffer(prefs: SharedPreferences, startTimeMillis: Long, endTimeMillis: Long): Pair<Long, Long> {
        // TODO: If buffer still feels incorrect, check the values saved in SettingsActivity. The logic here is correct.
        val minutesBefore = prefs.getInt("MINUTES_BEFORE", 0)
        val minutesAfter = prefs.getInt("MINUTES_AFTER", 0)

        val originalStartTimeSeconds = startTimeMillis / 1000
        val originalEndTimeSeconds = endTimeMillis / 1000

        val bufferSecondsBefore = minutesBefore * 60
        val bufferSecondsAfter = minutesAfter * 60

        val finalStartTimeSeconds = originalStartTimeSeconds - bufferSecondsBefore
        val finalEndTimeSeconds = originalEndTimeSeconds + bufferSecondsAfter

        return Pair(finalStartTimeSeconds, finalEndTimeSeconds)
    }
}
