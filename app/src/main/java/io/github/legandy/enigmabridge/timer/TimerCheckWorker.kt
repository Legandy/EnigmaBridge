package io.github.legandy.enigmabridge.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.legandy.enigmabridge.core.EnigmaBridgeApplication
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.data.TimerResult
import io.github.legandy.enigmabridge.helpers.NotificationHelper
import io.github.legandy.enigmabridge.notifications.RecordingNotificationReceiver
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
        if (!prefManager.isNotifyRecordingStartedEnabled()) {
            cancelAllScheduledRecordingNotifications(prefManager)
            return
        }

        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduledNotificationIds = prefManager.getScheduledNotificationIds().toMutableSet()
        val newScheduledNotificationIds = mutableSetOf<String>()

        val currentTimerKeys = currentTimers.map { "${it.sRef}_${it.beginTimestamp}" }.toSet()
        val previousTimerKeys = previousTimers.map { "${it.sRef}_${it.beginTimestamp}" }.toSet()

        val removedTimerKeys = previousTimerKeys - currentTimerKeys
        for (key in removedTimerKeys) {
            val parts = key.split("_")
            if (parts.size == 2) {
                val sRef = parts[0]
                val beginTimestamp = parts[1].toLongOrNull()
                if (beginTimestamp != null) {
                    val notificationId = createNotificationId(sRef, beginTimestamp)
                    cancelScheduledRecordingNotification(alarmManager, applicationContext, notificationId)
                    scheduledNotificationIds.remove(key)
                }
            }
        }

        for (timer in currentTimers) {
            val now = System.currentTimeMillis()
            val timerStartTimeMillis = TimeUnit.SECONDS.toMillis(timer.beginTimestamp)
            val notificationKey = "${timer.sRef}_${timer.beginTimestamp}"

            if (timerStartTimeMillis > now && !scheduledNotificationIds.contains(notificationKey)) {
                val notificationId = createNotificationId(timer.sRef ?: "UNKNOWN_SREF", timer.beginTimestamp)
                val intent = Intent(applicationContext, RecordingNotificationReceiver::class.java).apply {
                    putExtra("title", timer.name)
                    putExtra("channel", timer.sName)
                    action = "io.github.legandy.enigmabridge.RECORDING_STARTED_${notificationId}"
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    applicationContext, notificationId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timerStartTimeMillis, pendingIntent)
                newScheduledNotificationIds.add(notificationKey)
            } else if (scheduledNotificationIds.contains(notificationKey)) {
                newScheduledNotificationIds.add(notificationKey)
            }
        }
        prefManager.setScheduledNotificationIds(newScheduledNotificationIds)
    }

    private fun cancelAllScheduledRecordingNotifications(prefManager: PreferenceManager) {
        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduledNotificationIds = prefManager.getScheduledNotificationIds()

        for (key in scheduledNotificationIds) {
            val parts = key.split("_")
            if (parts.size == 2) {
                val sRef: String = parts[0]
                val beginTimestamp = parts[1].toLongOrNull()
                if (beginTimestamp != null) {
                    val notificationId = createNotificationId(sRef, beginTimestamp)
                    cancelScheduledRecordingNotification(alarmManager, applicationContext, notificationId)
                }
            }
        }
        prefManager.setScheduledNotificationIds(emptySet())
    }

    private fun cancelScheduledRecordingNotification(alarmManager: AlarmManager, context: Context, notificationId: Int) {
        val intent = Intent(context, RecordingNotificationReceiver::class.java)
        intent.action = "io.github.legandy.enigmabridge.RECORDING_STARTED_${notificationId}"
        val pendingIntent = PendingIntent.getBroadcast(
            context, notificationId, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    private fun createNotificationId(sRef: String, beginTimestamp: Long): Int = (sRef.hashCode() + beginTimestamp).toInt()
}
