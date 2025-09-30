package io.github.legandy.enigmabridge

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A helper object that acts as a "postman" to handle the entire scheduling process.
 * It applies the buffer and sends the command to the Enigma2 receiver, ensuring
 * the logic is centralized and consistent.
 */
object SchedulingHelper {

    private const val TAG = "SchedulingHelper"

    /**
     * Applies the buffer and schedules a timer on the Enigma2 receiver.
     * @return `true` if the timer was scheduled successfully, `false` otherwise.
     */
    suspend fun scheduleTimer(
        context: Context,
        title: String,
        sRef: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
    ): Boolean {
        val prefs = context.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)

        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) {
            return false
        }

        // Apply the buffer to the provided times
        val (finalStartTimeSeconds, finalEndTimeSeconds) = applyBuffer(prefs, startTimeMillis, endTimeMillis)

        // Create a client and send the command with default values for a simple recording
        val client = EnigmaClient(ip, user, pass)
        return client.addTimer(
            title = title,
            description = title, // Use title as description for simple schedules
            sRef = sRef,
            startTime = finalStartTimeSeconds,
            endTime = finalEndTimeSeconds,
            repeated = 0, // Not repeated
            afterEvent = 0, // Do Nothing
            justPlay = 0 // Record, don't just zap
        )
    }

    /**
     * Private helper to calculate the buffered times.
     */
    private fun applyBuffer(prefs: SharedPreferences, startTimeMillis: Long, endTimeMillis: Long): Pair<Long, Long> {
        val minutesBefore = prefs.getInt("MINUTES_BEFORE", 0)
        val minutesAfter = prefs.getInt("MINUTES_AFTER", 0)

        val originalStartTimeSeconds = startTimeMillis / 1000
        val originalEndTimeSeconds = endTimeMillis / 1000

        val bufferSecondsBefore = minutesBefore * 60
        val bufferSecondsAfter = minutesAfter * 60

        val finalStartTimeSeconds = originalStartTimeSeconds - bufferSecondsBefore
        val finalEndTimeSeconds = originalEndTimeSeconds + bufferSecondsAfter

        // --- ENHANCED LOGGING ---
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        Log.d(TAG, "--- Buffer Calculation ---")
        Log.d(TAG, "Original Start: ${dateFormat.format(Date(startTimeMillis))} ($originalStartTimeSeconds)")
        Log.d(TAG, "Original End:   ${dateFormat.format(Date(endTimeMillis))} ($originalEndTimeSeconds)")
        Log.d(TAG, "Buffer Before:  $minutesBefore min ($bufferSecondsBefore s)")
        Log.d(TAG, "Buffer After:   $minutesAfter min ($bufferSecondsAfter s)")
        Log.d(TAG, "Final Start:    ${dateFormat.format(Date(finalStartTimeSeconds * 1000))} ($finalStartTimeSeconds)")
        Log.d(TAG, "Final End:      ${dateFormat.format(Date(finalEndTimeSeconds * 1000))} ($finalEndTimeSeconds)")
        Log.d(TAG, "--------------------------")


        return Pair(finalStartTimeSeconds, finalEndTimeSeconds)
    }
}

