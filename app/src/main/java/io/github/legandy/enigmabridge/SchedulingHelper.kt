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
        repeated: Int,
        afterEvent: Int
    ): Boolean {
        val prefs = context.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)

        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) {
            Log.e(TAG, "IP address is empty. Cannot schedule timer.")
            return false
        }

        // Apply the buffer to the provided times using direct time calculation
        val (finalStartTimeSeconds, finalEndTimeSeconds) = applyBuffer(prefs, startTimeMillis, endTimeMillis)

        // Create a client and send the command
        val client = EnigmaClient(ip, user, pass)
        return client.addTimer(title, sRef, finalStartTimeSeconds, finalEndTimeSeconds, repeated, afterEvent)
    }

    /**
     * Private helper to calculate the buffered times using direct second conversion.
     * This version includes enhanced logging to debug the calculation step-by-step.
     */
    private fun applyBuffer(prefs: SharedPreferences, startTimeMillis: Long, endTimeMillis: Long): Pair<Long, Long> {
        // --- ENHANCED LOGGING ---
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        val minutesBefore = prefs.getInt("MINUTES_BEFORE", 0)
        val minutesAfter = prefs.getInt("MINUTES_AFTER", 0)

        Log.d(TAG, "--- Buffer Calculation ---")
        Log.d(TAG, "Original Start Time: ${dateFormat.format(Date(startTimeMillis))} ($startTimeMillis ms)")
        Log.d(TAG, "Original End Time:   ${dateFormat.format(Date(endTimeMillis))} ($endTimeMillis ms)")
        Log.d(TAG, "Buffer Before: $minutesBefore minutes")
        Log.d(TAG, "Buffer After:  $minutesAfter minutes")

        // Convert original milliseconds to seconds
        val originalStartTimeSeconds = startTimeMillis / 1000
        val originalEndTimeSeconds = endTimeMillis / 1000

        // Convert buffer minutes to seconds
        val bufferSecondsBefore = minutesBefore * 60
        val bufferSecondsAfter = minutesAfter * 60

        // Apply the buffer using simple addition/subtraction
        val finalStartTimeSeconds = originalStartTimeSeconds - bufferSecondsBefore
        val finalEndTimeSeconds = originalEndTimeSeconds + bufferSecondsAfter

        Log.d(TAG, "Final Start Time (Epoch Seconds): $finalStartTimeSeconds")
        Log.d(TAG, "Final End Time (Epoch Seconds):   $finalEndTimeSeconds")
        Log.d(TAG, "--------------------------")


        return Pair(finalStartTimeSeconds, finalEndTimeSeconds)
    }
}

