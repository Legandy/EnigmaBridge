package io.github.legandy.enigmabridge

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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

        val repeatAdapter = ArrayAdapter.createFromResource(
            this, R.array.repeat_options, android.R.layout.simple_spinner_item
        )
        repeatAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerRepeat.adapter = repeatAdapter

        val afterEventAdapter = ArrayAdapter.createFromResource(
            this, R.array.after_event_options, android.R.layout.simple_spinner_item
        )
        afterEventAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerAfterEvent.adapter = afterEventAdapter


        binding.textStartTime.setOnClickListener { showTimePickerDialog(isStart = true) }
        binding.textEndTime.setOnClickListener { showTimePickerDialog(isStart = false) }

        // When the user cancels, revert the optimistic marking.
        binding.buttonCancel.setOnClickListener { revertMarkAndFinish() }
        binding.buttonSave.setOnClickListener { scheduleAdvancedTimer() }
    }

    // Also revert the marking if the user presses the physical back button.
    override fun onBackPressed() {
        super.onBackPressed()
        revertMarkAndFinish()
    }

    private fun showTimePickerDialog(isStart: Boolean) {
        val calendar = if (isStart) startCalendar else endCalendar
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
        val afterEvent = binding.spinnerAfterEvent.selectedItemPosition

        val repeated = 0

        Toast.makeText(this, getString(R.string.scheduling_toast, editedTitle), Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = SchedulingHelper.scheduleTimer(
                context = applicationContext,
                title = editedTitle,
                sRef = sRef!!,
                startTimeMillis = startCalendar.timeInMillis,
                endTimeMillis = endCalendar.timeInMillis,
                description = program?.shortDescription ?: "",
                justPlay = 0,
                repeated = repeated,
                afterEvent = afterEvent
            )

            val success = result.first
            val message = result.second

            withContext(Dispatchers.Main) {
                Toast.makeText(applicationContext, if (success) getString(R.string.schedule_success) else message, Toast.LENGTH_LONG).show()
                if (success) {
                    val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
                    if (prefs.getBoolean("NOTIFY_SCHEDULED_ENABLED", true)) {
                        NotificationHelper.sendSuccessNotification(applicationContext, program!!)
                    }
                    // The marking was already done, so just finish.
                    finish()
                } else {
                    // If scheduling failed, revert the optimistic marking.
                    revertMarkAndFinish()
                }
            }
        }
    }

    // Sends a broadcast to the RecordService to tell it to un-mark the program.
    private fun revertMarkAndFinish() {
        program?.let {
            val intent = Intent(RecordService.ACTION_REVERT_MARKING)
            intent.putExtra("PROGRAM_EXTRA", it)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
        finish()
    }
}

