package io.github.legandy.enigmabridge.timer

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.core.EnigmaBridgeApplication
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.data.TimerRepository
import io.github.legandy.enigmabridge.data.TimerResult
import io.github.legandy.enigmabridge.databinding.ActivityEditTimerBinding
import io.github.legandy.enigmabridge.receiversettings.Timer
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditTimerActivity : AppCompatActivity() {

    private lateinit var prefManager: PreferenceManager
    private lateinit var repository: TimerRepository

    private lateinit var binding: ActivityEditTimerBinding
    private var originalTimer: Timer? = null

    private val startCalendar = Calendar.getInstance()
    private val endCalendar = Calendar.getInstance()
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as EnigmaBridgeApplication
        prefManager = app.prefManager
        repository = app.timerRepository

        binding = ActivityEditTimerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Modern, type-safe way to get Parcelable extras
        originalTimer = IntentCompat.getParcelableExtra(
            intent, 
            TimerListActivity.EXTRA_TIMER, 
            Timer::class.java
        )

        if (originalTimer == null) {
            Toast.makeText(this, getString(R.string.error_program_data_missing), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupInitialState()
        setupClickListeners()
    }

    private fun setupInitialState() {
        startCalendar.timeInMillis = originalTimer!!.beginTimestamp * 1000
        endCalendar.timeInMillis = originalTimer!!.endTimestamp * 1000

        binding.editProgramTitle.setText(originalTimer!!.name)
        binding.editDescription.setText(originalTimer!!.description)
        updateDateTimeTextViews()

        val items = listOf(originalTimer!!.sName)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerService.adapter = adapter
        binding.spinnerService.setSelection(0)
        binding.spinnerService.isEnabled = false

        binding.toggleEnabled.isChecked = originalTimer!!.disabled == 0

        binding.timerTypeGroup.check(
            when (originalTimer!!.justPlay) {
                1 -> binding.toggleJustPlay.id
                2 -> binding.toggleSwitch.id
                else -> binding.toggleRecord.id
            }
        )

        val dayButtonMap = mapOf(
            1 to binding.toggleMonday, 2 to binding.toggleTuesday, 4 to binding.toggleWednesday,
            8 to binding.toggleThursday, 16 to binding.toggleFriday, 32 to binding.toggleSaturday,
            64 to binding.toggleSunday
        )

        val repeats = originalTimer!!.repeated
        if (repeats > 0) {
            dayButtonMap.forEach { (dayValue, button) ->
                if ((repeats and dayValue) != 0) {
                    button.isChecked = true
                }
            }
        }
    }

    private fun setupClickListeners() {
        binding.textBeginDate.setOnClickListener { showDatePickerDialog(isStart = true) }
        binding.textBeginTime.setOnClickListener { showTimePickerDialog(isStart = true) }
        binding.textEndDate.setOnClickListener { showDatePickerDialog(isStart = false) }
        binding.textEndTime.setOnClickListener { showTimePickerDialog(isStart = false) }
        binding.buttonSave.setOnClickListener { saveTimer() }
    }

    private fun saveTimer() {
        val newTitle = binding.editProgramTitle.text.toString()
        val newDescription = binding.editDescription.text.toString()

        val justPlay = when (binding.timerTypeGroup.checkedButtonId) {
            binding.toggleJustPlay.id -> 1
            binding.toggleSwitch.id -> 2
            else -> 0
        }

        val dayValueMap = mapOf(
            binding.toggleMonday.id to 1, binding.toggleTuesday.id to 2, binding.toggleWednesday.id to 4,
            binding.toggleThursday.id to 8, binding.toggleFriday.id to 16, binding.toggleSaturday.id to 32,
            binding.toggleSunday.id to 64
        )
        var repeated = 0
        for (i in 0 until binding.repeatsOnGroup.childCount) {
            val button = binding.repeatsOnGroup.getChildAt(i) as? com.google.android.material.button.MaterialButton
            if (button?.isChecked == true) {
                repeated = repeated or (dayValueMap[button.id] ?: 0)
            }
        }

        val afterEvent = originalTimer!!.afterEvent
        val disabled = if (binding.toggleEnabled.isChecked) 0 else 1

        if (!prefManager.isReceiverConfigured()) {
            Toast.makeText(this, getString(R.string.error_ip_address_empty), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            val result = repository.editTimer(
                originalTimer = originalTimer!!,
                newTitle = newTitle,
                newDescription = newDescription,
                newStartTime = startCalendar.timeInMillis / 1000,
                newEndTime = endCalendar.timeInMillis / 1000,
                justPlay = justPlay,
                repeated = repeated,
                afterEvent = afterEvent,
                disabled = disabled
            )

            when (result) {
                is TimerResult.Success -> {
                    Toast.makeText(this@EditTimerActivity, getString(R.string.toast_timer_saved), Toast.LENGTH_SHORT).show()
                    finish() // TimerListActivity will refresh automatically via Flow
                }
                is TimerResult.Error -> {
                    Toast.makeText(this@EditTimerActivity, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showDatePickerDialog(isStart: Boolean) {
        val calendar = if (isStart) startCalendar else endCalendar
        DatePickerDialog(
            this, { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateTimeTextViews()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePickerDialog(isStart: Boolean) {
        val calendar = if (isStart) startCalendar else endCalendar
        TimePickerDialog(
            this, { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                updateDateTimeTextViews()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateTimeTextViews() {
        binding.textBeginDate.text = dateFormat.format(startCalendar.time)
        binding.textBeginTime.text = timeFormat.format(startCalendar.time)
        binding.textEndDate.text = dateFormat.format(endCalendar.time)
        binding.textEndTime.text = timeFormat.format(endCalendar.time)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
