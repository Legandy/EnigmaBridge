package io.github.legandy.enigmabridge.helpers

import android.content.Context
import android.util.Log
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.data.TimerRepository
import io.github.legandy.enigmabridge.data.TimerResult

object SchedulingHelper {
    private const val TAG = "SchedulingHelper"

    suspend fun scheduleTimer(
        context: Context,
        prefManager: PreferenceManager,
        repository: TimerRepository,
        title: String,
        sRef: String,
        startTimeMillis: Long,
        endTimeMillis: Long,
        description: String,
        justPlay: Int,
        repeated: Int,
        afterEvent: Int
    ): TimerResult<Pair<Boolean, String>> {
        if (!prefManager.isReceiverConfigured()) {
            return TimerResult.Error(context.getString(R.string.error_ip_address_empty))
        }

        val (finalStart, finalEnd) = applyBuffer(prefManager, startTimeMillis, endTimeMillis)

        Log.d(TAG, "Scheduling timer via Repository... Buffered: $finalStart -> $finalEnd")

        return repository.addTimer(
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
