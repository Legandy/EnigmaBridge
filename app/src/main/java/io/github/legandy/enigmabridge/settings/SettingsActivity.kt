package io.github.legandy.enigmabridge.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent // Added import
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager // Added import
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.ActivitySettingsBinding
import android.util.Log // Import Log

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val TAG = "SettingsActivity" // Add a TAG for logging

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("EnigmaSettings", MODE_PRIVATE)

        // Load notification settings, defaulting to true (enabled).
        binding.switchNotifyScheduled.isChecked = prefs.getBoolean("NOTIFY_SCHEDULED_ENABLED", true)
        binding.switchNotifyRecordingStarted.isChecked = prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)
        binding.switchNotifySyncSuccess.isChecked = prefs.getBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", true)

        // Copyable Intent Listener
        binding.buttonCopyTimerSyncIntent.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val intentToCopy = getString(R.string.timer_sync_intent_value)
            val clip = ClipData.newPlainText("Timer Sync Intent", intentToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Timer Sync Intent copied to clipboard", Toast.LENGTH_SHORT).show()
        }

    }

    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences("EnigmaSettings", MODE_PRIVATE)
        prefs.edit().apply {
            // Save the state of the notification switches.
            putBoolean("NOTIFY_SCHEDULED_ENABLED", binding.switchNotifyScheduled.isChecked)
            putBoolean("NOTIFY_RECORDING_STARTED_ENABLED", binding.switchNotifyRecordingStarted.isChecked)
            putBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", binding.switchNotifySyncSuccess.isChecked)

            apply()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}