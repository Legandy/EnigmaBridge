package io.github.legandy.enigmabridge.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.utils.TimerCheckWorker
import io.github.legandy.enigmabridge.databinding.ActivitySettingsBinding
import java.util.concurrent.TimeUnit

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    companion object {
        // This function is public and static, accessible from anywhere.
        fun scheduleWork(context: Context, hours: Int) {
            val workManager = WorkManager.getInstance(context)

            if (hours <= 0) {
                // Cancel the work if the interval is 0.
                workManager.cancelUniqueWork(TimerCheckWorker.Companion.WORK_TAG)
            } else {
                // Schedule the periodic work.
                val periodicRequest = PeriodicWorkRequestBuilder<TimerCheckWorker>(
                    hours.toLong(),
                    TimeUnit.HOURS
                )
                    .build()
                workManager.enqueueUniquePeriodicWork(
                    TimerCheckWorker.Companion.WORK_TAG, ExistingPeriodicWorkPolicy.REPLACE, periodicRequest
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_settings)

        val prefs = getSharedPreferences("EnigmaSettings", MODE_PRIVATE)

        // Load existing settings.
        binding.editMinutesBefore.setText(prefs.getInt("MINUTES_BEFORE", 2).toString())
        binding.editMinutesAfter.setText(prefs.getInt("MINUTES_AFTER", 5).toString())
        binding.editSyncInterval.setText(prefs.getInt("SYNC_INTERVAL_HOURS", 0).toString())

        // Load notification settings, defaulting to true (enabled).
        binding.switchNotifyScheduled.isChecked = prefs.getBoolean("NOTIFY_SCHEDULED_ENABLED", true)
        binding.switchNotifyRecordingStarted.isChecked = prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)
        binding.switchNotifySyncSuccess.isChecked = prefs.getBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", true)


        // Save Button Listener.
        binding.buttonSaveSettings.setOnClickListener {
            val before = binding.editMinutesBefore.text.toString().toIntOrNull() ?: 0
            val after = binding.editMinutesAfter.text.toString().toIntOrNull() ?: 0
            var intervalHours = binding.editSyncInterval.text.toString().toIntOrNull() ?: 0
            if (intervalHours < 0 || intervalHours > 24) {
                intervalHours = 0 // Default to disabled if invalid.
                Toast.makeText(this, "Invalid interval. Sync disabled.", Toast.LENGTH_SHORT).show()
            }

            prefs.edit().apply {
                putInt("MINUTES_BEFORE", before)
                putInt("MINUTES_AFTER", after)
                putInt("SYNC_INTERVAL_HOURS", intervalHours)

                // Save the state of the notification switches.
                putBoolean("NOTIFY_SCHEDULED_ENABLED", binding.switchNotifyScheduled.isChecked)
                putBoolean("NOTIFY_RECORDING_STARTED_ENABLED", binding.switchNotifyRecordingStarted.isChecked)
                putBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", binding.switchNotifySyncSuccess.isChecked)

                apply()
            }

            // Call the static scheduleWork function.
            scheduleWork(applicationContext, intervalHours)

            Toast.makeText(this, getString(R.string.toast_recording_settings_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}