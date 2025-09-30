package io.github.legandy.enigmabridge

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
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

    private lateinit var binding: ActivityEditTimerBinding
    private var originalTimer: Timer? = null
    private val startCalendar = Calendar.getInstance()
    private val endCalendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        const val EXTRA_TIMER = "TIMER_EXTRA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()

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
        populateFields()
    }

    private fun setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar)) // Assuming you add a toolbar to your layout
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = getString(R.string.title_edit_schedule)
    }

    private fun setupUI() {
        binding.editStartDate.setOnClickListener { showDatePicker(isStart = true) }
        binding.editStartTime.setOnClickListener { showTimePicker(isStart = true) }
        binding.editEndDate.setOnClickListener { showDatePicker(isStart = false) }
        binding.editEndTime.setOnClickListener { showTimePicker(isStart = false) }

        val afterEventOptions = resources.getStringArray(R.array.after_event_options)
        val afterEventAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, afterEventOptions)
        binding.spinnerAfterEvent.setAdapter(afterEventAdapter)
    }

    private fun populateFields() {
        binding.editTitle.setText(originalTimer!!.name)
        binding.editDescription.setText(originalTimer!!.description)
        updateDateAndTimeFields()

        // Safely set spinner value
        if (originalTimer!!.afterevent >= 0 && originalTimer!!.afterevent < binding.spinnerAfterEvent.adapter.count) {
            binding.spinnerAfterEvent.setText(binding.spinnerAfterEvent.adapter.getItem(originalTimer!!.afterevent).toString(), false)
        }

        binding.switchJustPlay.isChecked = originalTimer!!.justplay == 1

        val repeated = originalTimer!!.repeated
        binding.toggleMonday.isChecked = (repeated and 1) != 0
        binding.toggleTuesday.isChecked = (repeated and 2) != 0
        binding.toggleWednesday.isChecked = (repeated and 4) != 0
        binding.toggleThursday.isChecked = (repeated and 8) != 0
        binding.toggleFriday.isChecked = (repeated and 16) != 0
        binding.toggleSaturday.isChecked = (repeated and 32) != 0
        binding.toggleSunday.isChecked = (repeated and 64) != 0
    }

    private fun showDatePicker(isStart: Boolean) {
        val calendar = if (isStart) startCalendar else endCalendar
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateAndTimeFields()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker(isStart: Boolean) {
        val calendar = if (isStart) startCalendar else endCalendar
        TimePickerDialog(
            this,
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                updateDateAndTimeFields()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateAndTimeFields() {
        binding.editStartDate.setText(dateFormat.format(startCalendar.time))
        binding.editStartTime.setText(timeFormat.format(startCalendar.time))
        binding.editEndDate.setText(dateFormat.format(endCalendar.time))
        binding.editEndTime.setText(timeFormat.format(endCalendar.time))
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val saveItem = menu?.add(Menu.NONE, 1, Menu.NONE, getString(R.string.button_save))
        saveItem?.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            1 -> { // Our Save button
                saveTimer()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveTimer() {
        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        val newTitle = binding.editTitle.text.toString()
        val newDescription = binding.editDescription.text.toString()
        val newStartTime = startCalendar.timeInMillis / 1000
        val newEndTime = endCalendar.timeInMillis / 1000

        val afterEventOptions = resources.getStringArray(R.array.after_event_options)
        val selectedAfterEventString = binding.spinnerAfterEvent.text.toString()
        val afterEvent = afterEventOptions.indexOf(selectedAfterEventString).coerceAtLeast(0)


        val justPlay = if (binding.switchJustPlay.isChecked) 1 else 0

        var repeated = 0
        if (binding.toggleMonday.isChecked) repeated = repeated or 1
        if (binding.toggleTuesday.isChecked) repeated = repeated or 2
        if (binding.toggleWednesday.isChecked) repeated = repeated or 4
        if (binding.toggleThursday.isChecked) repeated = repeated or 8
        if (binding.toggleFriday.isChecked) repeated = repeated or 16
        if (binding.toggleSaturday.isChecked) repeated = repeated or 32
        if (binding.toggleSunday.isChecked) repeated = repeated or 64

        Toast.makeText(this, getString(R.string.saving_toast, newTitle), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass)
            val success = client.editTimer(
                originalTimer!!,
                newTitle,
                newDescription,
                originalTimer!!.sRef ?: "",
                newStartTime,
                newEndTime,
                repeated,
                afterEvent,
                justPlay
            )
            val message = if (success) getString(R.string.save_success) else getString(R.string.save_failed)
            withContext(Dispatchers.Main) {
                Toast.makeText(this@EditTimerActivity, message, Toast.LENGTH_LONG).show()
                if (success) {
                    // This will effectively refresh the list in the previous activity
                    setResult(RESULT_OK)
                    finish()
                }
            }
        }
    }
}

