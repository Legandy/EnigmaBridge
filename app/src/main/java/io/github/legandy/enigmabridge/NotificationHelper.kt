package io.github.legandy.enigmabridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.tvbrowser.devplugin.Program
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object NotificationHelper {

    private const val NOTIFICATION_CHANNEL_ID = "ENIGMA_BRIDGE_CHANNEL"
    private const val TAG = "NotificationHelper"

    // Creates the notification channel required on Android 8.0+.
    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    // Sends a notification for a successfully scheduled timer.
    fun sendSuccessNotification(context: Context, program: Program) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startTime = timeFormat.format(Date(program.startTimeInUTC))
        val channelName = program.channel.channelName
        val title = program.title

        val notificationContent = context.getString(R.string.notification_content, title, channelName, startTime)

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(notificationContent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        sendNotification(context, builder)
    }

    // Sends a notification when a recording has started.
    fun sendRecordingStartedNotification(context: Context, timer: Timer) {
        val notificationContent = context.getString(R.string.notification_content_recording_started, timer.name, timer.sName)

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_recording)
            .setContentTitle(context.getString(R.string.notification_title_recording_started))
            .setContentText(notificationContent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        sendNotification(context, builder)
    }

    // Sends a notification for a successful channel sync.
    fun sendSyncSuccessNotification(context: Context, channelCount: Int) {
        val notificationContent = context.getString(R.string.notification_content_sync_success, channelCount)
        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(context.getString(R.string.notification_title_sync_success))
            .setContentText(notificationContent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        sendNotification(context, builder)
    }

    // Generic function to display a notification.
    private fun sendNotification(context: Context, builder: NotificationCompat.Builder) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(context).notify(System.currentTimeMillis().toInt(), builder.build())
        } else {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
        }
    }
}

