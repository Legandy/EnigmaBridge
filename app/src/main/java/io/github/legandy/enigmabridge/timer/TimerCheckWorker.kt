package io.github.legandy.enigmabridge.timer

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import io.github.legandy.enigmabridge.core.EnigmaBridgeApplication
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.data.TimerResult
import io.github.legandy.enigmabridge.helpers.NotificationHelper
import io.github.legandy.enigmabridge.notifications.RecordingNotificationWorker
import io.github.legandy.enigmabridge.receiversettings.Timer
import java.util.concurrent.TimeUnit

class TimerCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "TimerCheckWorker"
        const val INPUT_DATA_KEY_SILENT_SYNC = "silent_sync"
    }

    override suspend fun doWork(): Result {
        Log.d(WORK_TAG, "--- TimerCheckWorker doWork() STARTED ---")

        val app = applicationContext as EnigmaBridgeApplication
        val repository = app.timerRepository
        val prefManager = app.prefManager

        val isSilentSync = inputData.getBoolean(INPUT_DATA_KEY_SILENT_SYNC, false)
        NotificationHelper.createNotificationChannel(applicationContext)

        if (!prefManager.isReceiverConfigured()) {
            Log.w(WORK_TAG, "Aborting timer sync: IP Address not configured.")
            return Result.failure()
        }

        // 1. Get previous timers for notification comparison
        val previousTimers = repository.timers.value

        // 2. Refresh timers using the Repository (Updates StateFlow & Prefs)
        return when (val result = repository.refreshTimers()) {
            is TimerResult.Success -> {
                val currentTimers = result.data
                Log.d(WORK_TAG, "Successfully fetched ${currentTimers.size} timers.")

                // 3. Run notification logic
                scheduleRecordingNotifications(currentTimers, previousTimers, prefManager)

                if (!isSilentSync && prefManager.isNotifySyncSuccessEnabled()) {
                    NotificationHelper.sendTimerSyncSuccessNotification(applicationContext, currentTimers.size)
                }

                Log.d(WORK_TAG, "--- TimerCheckWorker FINISHED SUCCESSFULLY ---")
                Result.success()
            }
            is TimerResult.Error -> {
                Log.e(WORK_TAG, "Sync failed: ${result.message}")
                NotificationHelper.sendTimerSyncFailedNotification(applicationContext)
                Result.failure()
            }
        }
    }

    private fun scheduleRecordingNotifications(
        currentTimers: List<Timer>,
        previousTimers: List<Timer>,
        prefManager: PreferenceManager
    ) {
        val workManager = WorkManager.getInstance(applicationContext)

        if (!prefManager.isNotifyRecordingStartedEnabled()) {
            cancelAllScheduledRecordingNotifications(prefManager)
            return
        }

        val scheduledNotificationKeys = prefManager.getScheduledNotificationIds().toMutableSet()
        val newScheduledNotificationKeys = mutableSetOf<String>()

        val currentTimerKeys = currentTimers.map { "${it.sRef}_${it.beginTimestamp}" }.toSet()
        val previousTimerKeys = previousTimers.map { "${it.sRef}_${it.beginTimestamp}" }.toSet()

        // Cancel notifications for timers that were removed
        val removedTimerKeys = previousTimerKeys - currentTimerKeys
        for (key in removedTimerKeys) {
            workManager.cancelUniqueWork(key)
            scheduledNotificationKeys.remove(key)
        }

        for (timer in currentTimers) {
            val now = System.currentTimeMillis()
            val timerStartTimeMillis = TimeUnit.SECONDS.toMillis(timer.beginTimestamp)
            val notificationKey = "${timer.sRef}_${timer.beginTimestamp}"

            if (timerStartTimeMillis > now) {
                // Schedule or update the notification work
                val delay = timerStartTimeMillis - now
                val notificationWork = OneTimeWorkRequestBuilder<RecordingNotificationWorker>()
                    .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                    .setInputData(workDataOf(
                        "title" to timer.name,
                        "channel" to timer.sName
                    ))
                    .addTag(notificationKey)
                    .build()

                workManager.enqueueUniqueWork(
                    notificationKey,
                    ExistingWorkPolicy.REPLACE,
                    notificationWork
                )
                newScheduledNotificationKeys.add(notificationKey)
            }
        }
        prefManager.setScheduledNotificationIds(newScheduledNotificationKeys)
    }

    private fun cancelAllScheduledRecordingNotifications(prefManager: PreferenceManager) {
        val workManager = WorkManager.getInstance(applicationContext)
        val scheduledNotificationKeys = prefManager.getScheduledNotificationIds()

        for (key in scheduledNotificationKeys) {
            workManager.cancelUniqueWork(key)
        }
        prefManager.setScheduledNotificationIds(emptySet())
    }
}
