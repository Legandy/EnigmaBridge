package io.github.legandy.enigmabridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.receiversettings.ReceiverSettingsActivity

class TimerCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefManager = PreferenceManager(context)
            val intervalHours = prefManager.getSyncIntervalHours()

            if (intervalHours > 0) {
                ReceiverSettingsActivity.scheduleWork(context, intervalHours)
                Log.d("TimerCheckReceiver", "Rescheduled periodic timer check after boot.")
            }
        }
    }
}