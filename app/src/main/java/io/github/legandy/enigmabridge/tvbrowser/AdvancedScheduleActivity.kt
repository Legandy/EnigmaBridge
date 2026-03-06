package io.github.legandy.enigmabridge.tvbrowser

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.core.AppThemeManager
import io.github.legandy.enigmabridge.core.EnigmaBridgeApplication
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.data.TimerRepository
import io.github.legandy.enigmabridge.data.TimerResult
import io.github.legandy.enigmabridge.databinding.ActivityAdvancedScheduleBinding
import io.github.legandy.enigmabridge.helpers.NotificationHelper
import io.github.legandy.enigmabridge.helpers.SchedulingHelper
import kotlinx.coroutines.launch
import org.tvbrowser.devplugin.Program
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AdvancedScheduleActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdvancedScheduleBinding

    private lateinit var prefManager: PreferenceManager
    private lateinit var repository: TimerRepository
    private var program: Program? = null
    private var sRef: String? = null

    private val startCalendar = Calendar.getInstance()
    private val endCalendar = Calendar.getInstance()
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as EnigmaBridgeApplication
        prefManager = app.prefManager
        repository = app.timerRepository
        
        AppThemeManager.applyThemeAndAccentColor(this)
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

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                revertMarkAndFinish()
            }
        })
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

        binding.buttonCancel.setOnClickListener { revertMarkAndFinish() }
        binding.buttonSave.setOnClickListener { scheduleAdvancedTimer() }
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

        lifecycleScope.launch {
            val result = SchedulingHelper.scheduleTimer(
                context = this@AdvancedScheduleActivity,
                prefManager = prefManager,
                repository = repository,
                title = editedTitle,
                sRef = sRef!!,
                startTimeMillis = startCalendar.timeInMillis,
                endTimeMillis = endCalendar.timeInMillis,
                description = program?.shortDescription ?: "",
                justPlay = 0,
                repeated = repeated,
                afterEvent = afterEvent
            )

            when (result) {
                is TimerResult.Success -> {
                    Toast.makeText(applicationContext, result.data.second, Toast.LENGTH_LONG).show()
                    if (prefManager.isNotifyScheduledEnabled()) {
                        NotificationHelper.sendSuccessNotification(applicationContext, program!!)
                    }
                    finish()
                }
                is TimerResult.Error -> {
                    Toast.makeText(applicationContext, result.message, Toast.LENGTH_LONG).show()
                    revertMarkAndFinish()
                }
            }
        }
    }

    private fun revertMarkAndFinish() {
        program?.let {
            val intent = Intent(RecordService.ACTION_REVERT_MARKING)
            intent.putExtra("PROGRAM_EXTRA", it)
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        }
        finish()
    }
}
