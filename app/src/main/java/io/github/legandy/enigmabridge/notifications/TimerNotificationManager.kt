package io.github.legandy.enigmabridge.notifications

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.legandy.enigmabridge.background.RecordingNotificationWorker
import io.github.legandy.enigmabridge.data.PreferenceManager
import io.github.legandy.enigmabridge.data.Timer
import java.util.concurrent.TimeUnit


// Manager for showing notifications for timers/syncs
class TimerNotificationManager(context: Context, private val prefManager: PreferenceManager) {

    private val workManager = WorkManager.getInstance(context)

    companion object {
        private const val TAG = "TimerNotificationMgr"
        fun getNotificationKey(timer: Timer): String = "${timer.sRef}_${timer.beginTimestamp}"
    }


    // Schedules or updates notifications for a list of timers
    fun syncNotifications(currentTimers: List<Timer>) {
        if (!prefManager.isNotifyRecordingStartedEnabled()) {
            cancelAllNotifications()
            return
        }

        val scheduledNotificationKeys = prefManager.getScheduledNotificationIds().toMutableSet()
        val currentTimerKeys = currentTimers.map { getNotificationKey(it) }.toSet()

        // Cancel notifications for timers that are no longer in the fetched list
        val keysToCancel = scheduledNotificationKeys - currentTimerKeys
        for (key in keysToCancel) {
            Log.d(TAG, "Cancelling scheduled notification for removed timer: $key")
            workManager.cancelUniqueWork(key)
            scheduledNotificationKeys.remove(key)
        }

        // Schedule notifications for future timers
        for (timer in currentTimers) {
            scheduleNotification(timer)
            scheduledNotificationKeys.add(getNotificationKey(timer))
        }

        // Persist the currently scheduled keys
        prefManager.setScheduledNotificationIds(scheduledNotificationKeys)
    }

    fun scheduleNotification(timer: Timer) {
        val now = System.currentTimeMillis()
        val timerStartTimeMillis = TimeUnit.SECONDS.toMillis(timer.beginTimestamp)
        val notificationKey = getNotificationKey(timer)

        if (timerStartTimeMillis > now) {
            val delay = timerStartTimeMillis - now
            val notificationWork =
                OneTimeWorkRequestBuilder<RecordingNotificationWorker>().setInitialDelay(
                    delay,
                    TimeUnit.MILLISECONDS
                ).setInputData(
                    workDataOf(
                        "title" to timer.name, "channel" to timer.sName
                    )
                ).addTag(notificationKey).build()

            workManager.enqueueUniqueWork(
                notificationKey, ExistingWorkPolicy.REPLACE, notificationWork
            )
            Log.d(TAG, "Scheduled notification for: ${timer.name} at $notificationKey")
        }
    }

    fun cancelNotificationForTimer(timer: Timer) {
        val notificationKey = getNotificationKey(timer)
        workManager.cancelUniqueWork(notificationKey)

        val scheduledIds = prefManager.getScheduledNotificationIds().toMutableSet()
        if (scheduledIds.remove(notificationKey)) {
            prefManager.setScheduledNotificationIds(scheduledIds)
        }
        Log.d(TAG, "Manually cancelled notification for: ${timer.name}")
    }

    fun cancelAllNotifications() {
        val scheduledNotificationKeys = prefManager.getScheduledNotificationIds()
        for (key in scheduledNotificationKeys) {
            workManager.cancelUniqueWork(key)
        }
        prefManager.setScheduledNotificationIds(emptySet())
        Log.d(TAG, "Cancelled all scheduled recording notifications.")
    }
}
