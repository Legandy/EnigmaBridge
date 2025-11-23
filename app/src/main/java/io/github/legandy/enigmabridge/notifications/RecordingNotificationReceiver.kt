package io.github.legandy.enigmabridge.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.legandy.enigmabridge.helpers.NotificationHelper

class RecordingNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Unknown Title"
        val channel = intent.getStringExtra("channel") ?: "Unknown Channel"

        NotificationHelper.sendRecordingStartedNotification(context, title, channel)
    }
}
