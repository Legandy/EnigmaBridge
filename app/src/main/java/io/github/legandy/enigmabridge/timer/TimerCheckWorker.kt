package io.github.legandy.enigmabridge.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.legandy.enigmabridge.helpers.NotificationHelper
import io.github.legandy.enigmabridge.main.MainActivity
import io.github.legandy.enigmabridge.notifications.RecordingNotificationReceiver
import io.github.legandy.enigmabridge.receiversettings.EnigmaClient
import io.github.legandy.enigmabridge.receiversettings.Timer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import io.github.legandy.enigmabridge.core.PreferenceManager

class TimerCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "TimerCheckWorker"
        const val INPUT_DATA_KEY_SILENT_SYNC = "silent_sync"
        const val ACTION_WORKER_COMPLETED = "io.github.legandy.enigmabridge.WORKER_COMPLETED"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override suspend fun doWork(): Result {
        Log.d(WORK_TAG, "--- TimerCheckWorker doWork() STARTED ---")

        val isSilentSync = inputData.getBoolean(INPUT_DATA_KEY_SILENT_SYNC, false)
        Log.d(WORK_TAG, "isSilentSync: $isSilentSync")

        NotificationHelper.createNotificationChannel(applicationContext)

        val prefManager = PreferenceManager(applicationContext)
        val ip = prefManager.getIpAddress()
        val user = prefManager.getUsername()
        val pass = prefManager.getPassword()
        val useHttps = prefManager.getUseHttps()

        if (ip.isEmpty()) {
            Log.w(WORK_TAG, "Aborting timer sync: IP Address not configured.")
            NotificationHelper.sendTimerSyncFailedNotification(applicationContext)
            // Send completion broadcast even on failure to dismiss loading indicator
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                Intent(
                    ACTION_WORKER_COMPLETED
                )
            )
            return Result.failure()
        }

        Log.d(WORK_TAG, "Attempting to connect to receiver at IP: $ip")
        Log.d(WORK_TAG, "EnigmaClient initialized with IP: '$ip', User: '$user'") // Added for debugging
        val client = EnigmaClient(ip, user, pass, useHttps)
        val currentTimers = withContext(Dispatchers.IO) {
            client.getTimerList()
        }

        if (currentTimers == null) {
            Log.e(WORK_TAG, "CRITICAL FAILURE: client.getTimerList() returned NULL. Aborting.")
            NotificationHelper.sendTimerSyncFailedNotification(applicationContext)
            // Send completion broadcast even on failure to dismiss loading indicator
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
                Intent(
                    ACTION_WORKER_COMPLETED
                )
            )
            return Result.failure()
        }

        Log.d(WORK_TAG, "Successfully fetched ${currentTimers.size} timers from the receiver.")

        // 1. Get the previous timers from the manager
        val previousTimersJson = prefManager.getPreviousTimersJson()
        val previousTimers = if (previousTimersJson != null) {
            json.decodeFromString<List<Timer>>(previousTimersJson)
        } else {
            emptyList()
        }

        // 2. Run the notification logic (we will fix this signature in Step 2)
        scheduleRecordingNotifications(currentTimers, previousTimers, prefManager)

        // 3. Save the new list to the manager
        prefManager.setPreviousTimersJson(json.encodeToString(currentTimers))

        // 4. Update the sync timestamp
        prefManager.setLastSyncTimestamp(System.currentTimeMillis())

        if (!isSilentSync) {
            val notifyEnabled = prefManager.isNotifySyncSuccessEnabled()
            Log.d(WORK_TAG, "Notification on success enabled: $notifyEnabled")
            if (notifyEnabled) {
                Log.d(WORK_TAG, "Calling sendTimerSyncSuccessNotification...")
                NotificationHelper.sendTimerSyncSuccessNotification(applicationContext, currentTimers.size)
            }

            Log.d(WORK_TAG, "Sending ACTION_TIMER_SYNC_COMPLETED broadcast.")
            val intent = Intent(MainActivity.ACTION_TIMER_SYNC_COMPLETED)
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        } else {
            Log.d(WORK_TAG, "Silent sync: Skipping ACTION_TIMER_SYNC_COMPLETED broadcast.")
        }

        // Always send ACTION_WORKER_COMPLETED broadcast at the end, regardless of silent sync or success/failure (handled above)
        Log.d(WORK_TAG, "Sending ACTION_WORKER_COMPLETED broadcast.")
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(
            Intent(
                ACTION_WORKER_COMPLETED
            )
        )

        Log.d(WORK_TAG, "--- TimerCheckWorker doWork() FINISHED SUCCESSFULLY ---")
        return Result.success()
    }

    private fun scheduleRecordingNotifications(
        currentTimers: List<Timer>,
        previousTimers: List<Timer>,
        prefManager: PreferenceManager
        ) {
        if (!prefManager.isNotifyRecordingStartedEnabled()) {
            Log.d(WORK_TAG, "Recording started notifications are disabled. Cancelling alarms.")
            cancelAllScheduledRecordingNotifications(prefManager)
            return
        }

        val alarmManager =
            applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        // Get a mutable copy of the IDs
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
                    cancelScheduledRecordingNotification(
                        alarmManager,
                        applicationContext,
                        notificationId
                    )
                    scheduledNotificationIds.remove(key)
                }
            }
        }

        for (timer in currentTimers) {
            val now = System.currentTimeMillis()
            val timerStartTimeMillis = TimeUnit.SECONDS.toMillis(timer.beginTimestamp)
            val notificationKey = "${timer.sRef}_${timer.beginTimestamp}"

            if (timerStartTimeMillis > now && !scheduledNotificationIds.contains(notificationKey)) {
                val notificationId =
                    createNotificationId(timer.sRef ?: "UNKNOWN_SREF", timer.beginTimestamp)
                val intent =
                    Intent(applicationContext, RecordingNotificationReceiver::class.java).apply {
                        putExtra("title", timer.name)
                        putExtra("channel", timer.sName)
                        action =
                            "io.github.legandy.enigmabridge.RECORDING_STARTED_${notificationId}"
                    }

                val pendingIntent = PendingIntent.getBroadcast(
                    applicationContext, notificationId, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    timerStartTimeMillis,
                    pendingIntent
                )
                newScheduledNotificationIds.add(notificationKey)
            } else if (scheduledNotificationIds.contains(notificationKey)) {
                newScheduledNotificationIds.add(notificationKey)
            }
        }

        // Save back to PreferenceManager
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
                    Log.d(WORK_TAG, "Cancelled all notification for : $key")
                }
            }
        }
        prefManager.setScheduledNotificationIds(emptySet())

    }

    private fun cancelScheduledRecordingNotification(alarmManager: AlarmManager, context: Context, notificationId: Int) {
        val intent = Intent(context, RecordingNotificationReceiver::class.java)
        // Ensure the intent matches the one used for scheduling (action and extras are important for equality)
        intent.action = "io.github.legandy.enigmabridge.RECORDING_STARTED_${notificationId}" // Use the same action
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE // Use FLAG_NO_CREATE to only get existing PendingIntent
        )
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel() // Explicitly cancel the PendingIntent itself
            Log.d(WORK_TAG, "Alarm with ID $notificationId cancelled.")
        } ?: run {
            Log.d(WORK_TAG, "No alarm found with ID $notificationId to cancel.")
        }
    }


    private fun createNotificationId(sRef: String, beginTimestamp: Long): Int {
        // A simple way to create a unique ID, combine sRef hash and timestamp
        return (sRef.hashCode() + beginTimestamp).toInt()
    }
}