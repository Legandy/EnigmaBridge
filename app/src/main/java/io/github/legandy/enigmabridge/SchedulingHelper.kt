package io.github.legandy.enigmabridge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object SchedulingHelper {

    private const val TAG = "SchedulingHelper"

    // DEFINITIVE FIX: All parameters are now included in the function signature.
    suspend fun scheduleTimer(
        context: Context, title: String, sRef: String, startTimeMillis: Long, endTimeMillis: Long,
        description: String, justPlay: Int, repeated: Int, afterEvent: Int
    ): Boolean {
        val prefs = context.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) return false

        val (finalStartTimeSeconds, finalEndTimeSeconds) = applyBuffer(prefs, startTimeMillis, endTimeMillis)
        val client = EnigmaClient(ip, user, pass)

        // DEFINITIVE FIX: All parameters are now passed correctly to the client.
        return client.addTimer(title, sRef, finalStartTimeSeconds, finalEndTimeSeconds, description, justPlay, repeated, afterEvent)
    }

    private fun applyBuffer(prefs: SharedPreferences, startTimeMillis: Long, endTimeMillis: Long): Pair<Long, Long> {
        val minutesBefore = prefs.getInt("MINUTES_BEFORE", 0)
        val minutesAfter = prefs.getInt("MINUTES_AFTER", 0)

        val bufferSecondsBefore = minutesBefore * 60
        val bufferSecondsAfter = minutesAfter * 60

        val finalStartTimeSeconds = (startTimeMillis / 1000) - bufferSecondsBefore
        val finalEndTimeSeconds = (endTimeMillis / 1000) + bufferSecondsAfter

        Log.d(TAG, "Final Times (Epoch Seconds): Start=$finalStartTimeSeconds, End=$finalEndTimeSeconds")
        return Pair(finalStartTimeSeconds, finalEndTimeSeconds)
    }
}

