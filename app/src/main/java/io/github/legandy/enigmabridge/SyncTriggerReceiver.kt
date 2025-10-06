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
        if (intent.action == ACTION_TRIGGER_SYNC) {
            // ** THE FIX 3: Add a toast for immediate feedback **
            Toast.makeText(context, R.string.toast_sync_triggered_externally, Toast.LENGTH_SHORT).show()

            val syncWorkRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>()
                .build()
            WorkManager.getInstance(context).enqueue(syncWorkRequest)
        }
    }
}

