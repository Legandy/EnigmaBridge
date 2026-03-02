package io.github.legandy.enigmabridge.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
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
import java.util.Date
import java.util.concurrent.TimeUnit

class TimerCheckWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_TAG = "TimerCheckWorker"
        private const val PREVIOUS_TIMERS_KEY = "PREVIOUS_TIMERS_LIST"
        private const val SCHEDULED_NOTIFICATION_IDS_KEY = "SCHEDULED_NOTIFICATION_IDS"
        const val INPUT_DATA_KEY_SILENT_SYNC = "silent_sync" // New constant for silent sync flag
        const val ACTION_WORKER_COMPLETED = "io.github.legandy.enigmabridge.WORKER_COMPLETED" // New constant for worker completion broadcast
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

        val prefs = applicationContext.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

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
        val client = EnigmaClient(ip, user, pass, prefs)
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

        val previousTimersJson = prefs.getString(PREVIOUS_TIMERS_KEY, null)
        val previousTimers = if (previousTimersJson != null) {
            json.decodeFromString<List<Timer>>(previousTimersJson)
        } else {
            emptyList()
        }

        // Schedule and manage recording notifications
        scheduleRecordingNotifications(currentTimers, previousTimers, prefs)
        saveCurrentTimers(currentTimers, prefs)

        // Always update LAST_TIMER_SYNC_TIMESTAMP
        Log.d(WORK_TAG, "Updating LAST_TIMER_SYNC_TIMESTAMP preference.")
        prefs.edit {
            putLong("LAST_TIMER_SYNC_TIMESTAMP", System.currentTimeMillis())
        }

        if (!isSilentSync) {
            val notifyEnabled = prefs.getBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", true)
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

    private fun scheduleRecordingNotifications(currentTimers: List<Timer>, previousTimers: List<Timer>, prefs: SharedPreferences) {
        if (!prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)) {
            Log.d(WORK_TAG, "Recording started notifications are disabled in settings. Cancelling all alarms.")
            cancelAllScheduledRecordingNotifications(prefs)
            return
        }

        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduledNotificationIds = prefs.getStringSet(SCHEDULED_NOTIFICATION_IDS_KEY, emptySet())?.toMutableSet() ?: mutableSetOf()

        val newScheduledNotificationIds = mutableSetOf<String>()

        // Cancel alarms for timers that are no longer present or have changed
        val currentTimerKeys = currentTimers.map { "${it.sRef}_${it.beginTimestamp}" }.toSet()
        val previousTimerKeys = previousTimers.map { "${it.sRef}_${it.beginTimestamp}" }.toSet()

        // Timers that were previously scheduled but are no longer in the current list
        val removedTimerKeys = previousTimerKeys - currentTimerKeys
        for (key in removedTimerKeys) {
            val parts = key.split("_")
            if (parts.size == 2) {
                val sRef: String = parts[0]
                val beginTimestamp = parts[1].toLongOrNull()
                if (beginTimestamp != null) {
                    val notificationId = createNotificationId(sRef, beginTimestamp)
                    cancelScheduledRecordingNotification(alarmManager, applicationContext, notificationId)
                    Log.d(WORK_TAG, "Cancelled notification for removed/changed timer: $key")
                    scheduledNotificationIds.remove(key)
                }
            }
        }


        for (timer in currentTimers) {
            // Only schedule if the timer is in the future and hasn't been scheduled already
            val now = System.currentTimeMillis()
            val timerStartTimeMillis = TimeUnit.SECONDS.toMillis(timer.beginTimestamp)

            val notificationKey = "${timer.sRef}_${timer.beginTimestamp}"

            if (timerStartTimeMillis > now && !scheduledNotificationIds.contains(notificationKey)) {
                val notificationId = createNotificationId(timer.sRef ?: "UNKNOWN_SREF", timer.beginTimestamp)
                val intent = Intent(applicationContext, RecordingNotificationReceiver::class.java).apply {
                    putExtra("title", timer.name)
                    putExtra("channel", timer.sName)
                    // Ensure unique intent data if needed, or rely on unique request code
                    action = "io.github.legandy.enigmabridge.RECORDING_STARTED_${notificationId}"
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    applicationContext,
                    notificationId,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                Log.d(WORK_TAG, "Scheduling recording start notification for '${timer.name}' at ${
                    Date(
                        timerStartTimeMillis
                    )
                } with ID: $notificationId")
                // Schedule the alarm
                alarmManager.setAndAllowWhileIdle( // Changed from setExactAndAllowWhileIdle
                    AlarmManager.RTC_WAKEUP,
                    timerStartTimeMillis,
                    pendingIntent
                )

                Log.i(WORK_TAG, "Scheduled recording start notification for '${timer.name}' at ${
                    Date(
                        timerStartTimeMillis
                    )
                }")
                newScheduledNotificationIds.add(notificationKey)
            } else if (scheduledNotificationIds.contains(notificationKey)) {
                Log.d(WORK_TAG, "Notification for '${timer.name}' (ID: $notificationKey) already scheduled.")
                newScheduledNotificationIds.add(notificationKey) // Keep in the new set
            } else if (timerStartTimeMillis <= now) {
                Log.d(WORK_TAG, "Timer '${timer.name}' is in the past, not scheduling notification.")
            }
        }

        // Update the stored set of scheduled notification IDs
        prefs.edit {
            putStringSet(SCHEDULED_NOTIFICATION_IDS_KEY, newScheduledNotificationIds)
        }
    }


    private fun cancelAllScheduledRecordingNotifications(prefs: SharedPreferences) {
        val alarmManager = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val scheduledNotificationIds = prefs.getStringSet(SCHEDULED_NOTIFICATION_IDS_KEY, emptySet()) ?: emptySet()

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
        prefs.edit {
            remove(SCHEDULED_NOTIFICATION_IDS_KEY)
        }
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

    private fun saveCurrentTimers(timers: List<Timer>, prefs: SharedPreferences) {
        val jsonString = json.encodeToString<List<Timer>>(timers)
        prefs.edit {
            putString(PREVIOUS_TIMERS_KEY, jsonString)
        }
    }
}