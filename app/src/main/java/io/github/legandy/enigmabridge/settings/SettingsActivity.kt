package io.github.legandy.enigmabridge.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // title is now set in the toolbar XML

        val prefs = getSharedPreferences("EnigmaSettings", MODE_PRIVATE)

        // Load notification settings, defaulting to true (enabled).
        binding.switchNotifyScheduled.isChecked = prefs.getBoolean("NOTIFY_SCHEDULED_ENABLED", true)
        binding.switchNotifyRecordingStarted.isChecked = prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)
        binding.switchNotifySyncSuccess.isChecked = prefs.getBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", true)

        // Copyable Intent Listener
        binding.intentTimerSync.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Timer Sync Intent", binding.intentTimerSync.text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, getString(R.string.toast_intent_copied), Toast.LENGTH_SHORT).show()
        }


        // Save Button Listener.
        binding.buttonSaveSettings.setOnClickListener {
            prefs.edit().apply {
                // Save the state of the notification switches.
                putBoolean("NOTIFY_SCHEDULED_ENABLED", binding.switchNotifyScheduled.isChecked)
                putBoolean("NOTIFY_RECORDING_STARTED_ENABLED", binding.switchNotifyRecordingStarted.isChecked)
                putBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", binding.switchNotifySyncSuccess.isChecked)

                apply()
            }

            Toast.makeText(this, getString(R.string.toast_settings_saved), Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}