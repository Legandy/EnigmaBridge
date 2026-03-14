package io.github.legandy.enigmabridge.background

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.legandy.enigmabridge.notifications.NotificationHelper

// Worker for sending a notification when a recording starts
class RecordingNotificationWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString("title") ?: "Unknown Title"
        val channel = inputData.getString("channel") ?: "Unknown Channel"

        NotificationHelper.sendRecordingStartedNotification(applicationContext, title, channel)

        return Result.success()
    }
}