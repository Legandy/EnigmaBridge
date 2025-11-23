package io.github.legandy.enigmabridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.legandy.enigmabridge.receiversettings.ReceiverSettingsActivity

class TimerCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Check if the phone just finished booting.
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            // If the periodic sync was enabled, reschedule it.
            val prefs = context.getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
            val intervalHours = prefs.getInt("SYNC_INTERVAL_HOURS", 0)
            if (intervalHours > 0) {
                // Call the public, static scheduling function.
                ReceiverSettingsActivity.Companion.scheduleWork(context, intervalHours)
                Log.d("TimerCheckReceiver", "Rescheduled periodic timer check after boot.")
            }
        }
    }
}