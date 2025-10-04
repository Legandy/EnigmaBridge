package io.github.legandy.enigmabridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class SyncTriggerReceiver : BroadcastReceiver() {

    companion object {
        // Define a custom action string that external apps will use to trigger the sync.
        const val ACTION_TRIGGER_SYNC = "io.github.legandy.enigmabridge.ACTION_TRIGGER_SYNC"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Check if the received broadcast matches the action.
        if (intent.action == ACTION_TRIGGER_SYNC) {
            // Show a toast to confirm the action was received.
            Toast.makeText(context, R.string.toast_sync_triggered_externally, Toast.LENGTH_SHORT).show()

            // Enqueue a one-time work request to run the TimerCheckWorker.
            val workRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>().build()
            WorkManager.getInstance(context).enqueue(workRequest)
        }
    }
}
