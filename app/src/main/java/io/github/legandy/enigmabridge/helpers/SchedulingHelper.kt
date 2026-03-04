package io.github.legandy.enigmabridge.helpers

import android.content.Context
import android.util.Log
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.R

object SchedulingHelper {
    private const val TAG = "SchedulingHelper"

    fun scheduleTimer(
        context: Context,
        prefManager: PreferenceManager,
        title: String,
        sRef: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        description: String,
        justPlay: Int,
        repeated: Int,
        afterEvent: Int
    ): Pair<Boolean, String> {
        // Use context.getString() for the return message
        if (!prefManager.isReceiverConfigured()) {
            return Pair(false, context.getString(R.string.error_ip_address_empty))
        }

        // Pass prefManager to the buffer logic
        val (finalStart, finalEnd) = applyBuffer(prefManager, startTimeMillis, endTimeMillis)

        Log.d(TAG, "Scheduling timer... Buffered: $finalStart -> $finalEnd")

        return prefManager.getEnigmaClient().addTimer(
            title, sRef, finalStart, finalEnd, description, justPlay, repeated, afterEvent
        )
    }

    private fun applyBuffer(prefManager: PreferenceManager, startTimeMillis: Long, endTimeMillis: Long): Pair<Long, Long> {
        val startSec = startTimeMillis / 1000
        val endSec = endTimeMillis / 1000

        val bufferBefore = prefManager.getMinutesBefore() * 60L
        val bufferAfter = prefManager.getMinutesAfter() * 60L

        return Pair(startSec - bufferBefore, endSec + bufferAfter)
    }
}