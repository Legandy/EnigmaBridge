package io.github.legandy.enigmabridge

import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.legandy.enigmabridge.databinding.ActivityEditTimerBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditTimerActivity : AppCompatActivity() {

    // DEFINITIVE FIX: Use the correct binding for the layout file provided.
    private lateinit var binding: ActivityEditTimerBinding
    private var originalTimer: Timer? = null

    private val startCalendar = Calendar.getInstance()
    private val endCalendar = Calendar.getInstance()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        const val EXTRA_TIMER = "EXTRA_TIMER"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the correct layout
        binding = ActivityEditTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        originalTimer = intent.getParcelableExtra(EXTRA_TIMER)

        if (originalTimer == null) {
            Toast.makeText(this, getString(R.string.error_timer_data_missing), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startCalendar.timeInMillis = originalTimer!!.beginTimestamp * 1000
        endCalendar.timeInMillis = originalTimer!!.endTimestamp * 1000

        setupUI()
    }

    private fun setupUI() {
        binding.textDialogTitle.text = getString(R.string.title_edit_schedule)
        binding.editProgramTitle.setText(originalTimer!!.name)
        binding.textChannelName.text = originalTimer!!.sName
        updateTimeTextViews()

        // Setup Repeat Spinner
        ArrayAdapter.createFromResource(
            this, R.array.repeat_options, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerRepeat.adapter = adapter
        }

        // Setup After Event Spinner
        ArrayAdapter.createFromResource(
            this, R.array.after_event_options, android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerAfterEvent.adapter = adapter
        }

        binding.textStartTime.setOnClickListener { showTimePickerDialog(isStartTime = true) }
        binding.textEndTime.setOnClickListener { showTimePickerDialog(isStartTime = false) }
        // Note: There is no cancel button in activity_edit_timer.xml
        binding.buttonSave.setOnClickListener { saveEditedTimer() }
    }

    private fun showTimePickerDialog(isStartTime: Boolean) {
        val calendar = if (isStartTime) startCalendar else endCalendar
        TimePickerDialog(
            this, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                updateTimeTextViews()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateTimeTextViews() {
        binding.textStartTime.text = timeFormat.format(startCalendar.time)
        binding.textEndTime.text = timeFormat.format(endCalendar.time)
    }

    private fun saveEditedTimer() {
        val editedTitle = binding.editProgramTitle.text.toString()
        val sRef = originalTimer?.sRef

        if (sRef.isNullOrEmpty()) {
            Toast.makeText(this, "Cannot save timer, service reference is missing.", Toast.LENGTH_LONG).show()
            return
        }

        val repeated = when (binding.spinnerRepeat.selectedItemPosition) {
            1 -> 254 // Daily
            2 -> 127 // Weekly
            else -> 0 // Once
        }
        val afterEvent = binding.spinnerAfterEvent.selectedItemPosition

        Toast.makeText(this, getString(R.string.saving_toast, editedTitle), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
            val ip = prefs.getString("IP_ADDRESS", "") ?: ""
            val user = prefs.getString("USERNAME", "root") ?: ""
            val pass = prefs.getString("PASSWORD", "") ?: ""
            val client = EnigmaClient(ip, user, pass)

            val success = client.editTimer(
                originalTimer = originalTimer!!,
                newTitle = editedTitle,
                newSRef = sRef,
                newStartTime = startCalendar.timeInMillis / 1000,
                newEndTime = endCalendar.timeInMillis / 1000,
                repeated = repeated,
                afterEvent = afterEvent
            )

            val message = if (success) getString(R.string.save_success) else getString(R.string.save_failed)

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                if (success) {
                    finish()
                }
            }
        }
    }
}

