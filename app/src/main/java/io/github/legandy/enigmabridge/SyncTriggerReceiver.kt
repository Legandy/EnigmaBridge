package io.github.legandy.enigmabridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class SyncTriggerReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TRIGGER_SYNC = "io.github.legandy.enigmabridge.ACTION_TRIGGER_SYNC"
    }

    override fun onReceive(context: Context, intent: Intent) {
        // Only act if the intent action matches what we expect.
        if (intent.action == ACTION_TRIGGER_SYNC) {
            // Create a one-time work request for our TimerCheckWorker.
            val syncWorkRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>()
                .build()
            // Enqueue the work.
            WorkManager.getInstance(context).enqueue(syncWorkRequest)

            // Show a toast to confirm the action was triggered.
            Toast.makeText(context, R.string.toast_sync_triggered_externally, Toast.LENGTH_SHORT).show()
        }
    }
}

