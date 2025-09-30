package io.github.legandy.enigmabridge

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.legandy.enigmabridge.databinding.ActivityRecordingSettingsBinding

class RecordingSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRecordingSettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRecordingSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_recording_settings)

        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)

        // Load existing settings, using defaults from the old app's screenshot
        val minutesBefore = prefs.getInt("MINUTES_BEFORE", 2)
        val minutesAfter = prefs.getInt("MINUTES_AFTER", 5)

        binding.editMinutesBefore.setText(minutesBefore.toString())
        binding.editMinutesAfter.setText(minutesAfter.toString())

        binding.buttonSaveRecordingSettings.setOnClickListener {
            val before = binding.editMinutesBefore.text.toString().toIntOrNull() ?: 0
            val after = binding.editMinutesAfter.text.toString().toIntOrNull() ?: 0

            prefs.edit().apply {
                putInt("MINUTES_BEFORE", before)
                putInt("MINUTES_AFTER", after)
                apply()
            }
            Toast.makeText(this, getString(R.string.toast_recording_settings_saved), Toast.LENGTH_SHORT).show()
            finish() // Close the settings screen after saving
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

