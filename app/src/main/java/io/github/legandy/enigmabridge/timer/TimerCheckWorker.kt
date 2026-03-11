package io.github.legandy.enigmabridge.timer

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.legandy.enigmabridge.core.EnigmaBridgeApplication
import io.github.legandy.enigmabridge.data.TimerResult
import io.github.legandy.enigmabridge.helpers.NotificationHelper

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

        // 1. Refresh timers using the Repository (Updates StateFlow, Prefs & Syncs Notifications)
        return when (val result = repository.refreshTimers()) {
            is TimerResult.Success -> {
                val currentTimers = result.data
                Log.d(WORK_TAG, "Successfully fetched ${currentTimers.size} timers.")

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
}
