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
            finish(); return
        }

        startCalendar.timeInMillis = program!!.startTimeInUTC
        endCalendar.timeInMillis = program!!.endTimeInUTC
        setupUI()
    }

    private fun setupUI() {
        binding.editProgramTitle.setText(program!!.title)
        binding.textChannelName.text = program!!.channel.channelName
        updateTimeTextViews()

        binding.spinnerRepeat.adapter = ArrayAdapter.createFromResource(this, R.array.repeat_options, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerAfterEvent.adapter = ArrayAdapter.createFromResource(this, R.array.after_event_options, android.R.layout.simple_spinner_item).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        binding.textStartTime.setOnClickListener { showTimePickerDialog(isStart = true) }
        binding.textEndTime.setOnClickListener { showTimePickerDialog(isStart = false) }
        binding.buttonCancel.setOnClickListener { finish() }
        binding.buttonSave.setOnClickListener { scheduleAdvancedTimer() }
    }

    private fun showTimePickerDialog(isStart: Boolean) {
        val cal = if (isStart) startCalendar else endCalendar
        TimePickerDialog(this, { _, hour, minute ->
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            updateTimeTextViews()
        }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
    }

    private fun updateTimeTextViews() {
        binding.textStartTime.text = timeFormat.format(startCalendar.time)
        binding.textEndTime.text = timeFormat.format(endCalendar.time)
    }

    private fun scheduleAdvancedTimer() {
        val editedTitle = binding.editProgramTitle.text.toString()
        Toast.makeText(this, getString(R.string.scheduling_toast, editedTitle), Toast.LENGTH_SHORT).show()

        val repeatedValue = when (binding.spinnerRepeat.selectedItemPosition) {
            1 -> 127 // Daily
            2 -> getWeeklyRepeatedValue()
            else -> 0 // Once
        }
        val afterEventValue = binding.spinnerAfterEvent.selectedItemPosition

        lifecycleScope.launch(Dispatchers.IO) {
            // DEFINITIVE FIX: Pass all required parameters.
            val success = SchedulingHelper.scheduleTimer(
                context = applicationContext,
                title = editedTitle,
                sRef = sRef!!,
                startTimeMillis = startCalendar.timeInMillis,
                endTimeMillis = endCalendar.timeInMillis,
                description = program?.shortDescription ?: editedTitle,
                justPlay = 0,
                repeated = repeatedValue,
                afterEvent = afterEventValue
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

    private fun getWeeklyRepeatedValue(): Int = when (startCalendar.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 4; Calendar.THURSDAY -> 8
        Calendar.FRIDAY -> 16; Calendar.SATURDAY -> 32; Calendar.SUNDAY -> 64; else -> 0
    }
}

