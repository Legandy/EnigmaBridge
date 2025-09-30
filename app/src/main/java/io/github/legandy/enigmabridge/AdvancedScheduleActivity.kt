package io.github.legandy.enigmabridge

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import io.github.legandy.enigmabridge.databinding.ActivityAdvancedScheduleBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tvbrowser.devplugin.Program
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdvancedScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedScheduleBinding
    private var program: Program? = null
    private var sRef: String? = null

    private val startCalendar = Calendar.getInstance()
    private val endCalendar = Calendar.getInstance()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdvancedScheduleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        @Suppress("DEPRECATION")
        program = intent.getParcelableExtra("PROGRAM_EXTRA")
        sRef = intent.getStringExtra("SREF_EXTRA")

        if (program == null || sRef == null) {
            Toast.makeText(this, getString(R.string.error_program_data_missing), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        startCalendar.timeInMillis = program!!.startTimeInUTC
        endCalendar.timeInMillis = program!!.endTimeInUTC

        setupUI()
    }

    private fun setupUI() {
        binding.editProgramTitle.setText(program!!.title)
        binding.textChannelName.text = program!!.channel.channelName
        updateTimeTextViews()

        val repeatOptions = resources.getStringArray(R.array.repeat_options)
        val repeatAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, repeatOptions)
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRepeat.adapter = repeatAdapter

        binding.textStartTime.setOnClickListener { showTimePickerDialog(isStartTime = true) }
        binding.textEndTime.setOnClickListener { showTimePickerDialog(isStartTime = false) }
        binding.buttonCancel.setOnClickListener { finish() }
        binding.buttonSave.setOnClickListener { scheduleAdvancedTimer() }
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

    private fun scheduleAdvancedTimer() {
        val editedTitle = binding.editProgramTitle.text.toString()

        Toast.makeText(this, getString(R.string.scheduling_toast, editedTitle), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            // DEFINITIVE FIX: Use the time from the calendars, which includes any
            // user edits, as the basis for the buffer calculation.
            val success = SchedulingHelper.scheduleTimer(
                context = applicationContext,
                title = editedTitle,
                sRef = sRef!!,
                startTimeMillis = startCalendar.timeInMillis,
                endTimeMillis = endCalendar.timeInMillis
            )

            val message = if (success) getString(R.string.schedule_success) else getString(R.string.schedule_failed)

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                if (success) {
                    NotificationHelper.sendSuccessNotification(applicationContext, program!!)
                    finish()
                }
            }
        }
    }
}

