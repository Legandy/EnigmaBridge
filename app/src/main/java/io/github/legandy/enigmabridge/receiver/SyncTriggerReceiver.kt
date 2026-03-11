package io.github.legandy.enigmabridge.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log // Import Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import io.github.legandy.enigmabridge.timer.TimerCheckWorker
import java.util.concurrent.TimeUnit

class SyncTriggerReceiver : BroadcastReceiver() {

    companion object {
        private const val RECEIVER_TAG = "SyncTriggerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(RECEIVER_TAG, "onReceive() triggered for action: ${intent.action}")

        if (intent.action == "io.github.legandy.enigmabridge.intent.ACTION_TRIGGER_SYNC") {
            Log.d(RECEIVER_TAG, "ACTION_TRIGGER_SYNC received. Checking for existing worker.")

            val workInfos = WorkManager.getInstance(context).getWorkInfosForUniqueWork("TimerCheckWorker")
            var isRunningOrPending = false
            workInfos.get().forEach { workInfo ->
                if (workInfo.state == androidx.work.WorkInfo.State.RUNNING || workInfo.state == androidx.work.WorkInfo.State.ENQUEUED) {
                    isRunningOrPending = true
                }
            }

            if (!isRunningOrPending) {
                Log.d(RECEIVER_TAG, "No existing TimerCheckWorker. Enqueueing new one.")

                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val timerCheckRequest = OneTimeWorkRequest.Builder(TimerCheckWorker::class.java)
                    .setConstraints(constraints)
                    .setInitialDelay(5, TimeUnit.SECONDS)
                    .build()

                WorkManager.getInstance(context).enqueue(timerCheckRequest)
            } else {
                Log.d(RECEIVER_TAG, "TimerCheckWorker is already running or enqueued. Skipping new enqueue.")
            }
        } else {
            Log.d(RECEIVER_TAG, "Received unexpected action: ${intent.action}")
        }
    }
}