package io.github.legandy.enigmabridge.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.about.AboutActivity
import io.github.legandy.enigmabridge.databinding.ActivityMainBinding
import io.github.legandy.enigmabridge.timer.TimerListActivity
import io.github.legandy.enigmabridge.receiversettings.ReceiverSettingsActivity
import io.github.legandy.enigmabridge.settings.SettingsActivity
import kotlinx.coroutines.launch
import java.util.Date
import io.github.legandy.enigmabridge.core.AppThemeManager
import io.github.legandy.enigmabridge.core.EnigmaBridgeApplication
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.data.TimerRepository
import io.github.legandy.enigmabridge.data.TimerResult

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefManager: PreferenceManager
    private lateinit var timerRepository: TimerRepository

    companion object {
        const val ACTION_TIMER_SYNC_COMPLETED = "io.github.legandy.enigmabridge.TIMER_SYNC_COMPLETED"
    }

    private val timerSyncCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_TIMER_SYNC_COMPLETED) {
                updateLastSyncStatus()
            }
        }
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }
            updateNotificationStatusIndicator()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyThemeAndAccentColor(this)
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize from Application Singleton
        val app = application as EnigmaBridgeApplication
        prefManager = app.prefManager
        timerRepository = app.timerRepository

        setupUI()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        runChecks()
        updateConnectionStatusIndicator(false)
        updateLastSyncStatus()
        LocalBroadcastManager.getInstance(this).registerReceiver(timerSyncCompletedReceiver,
            IntentFilter(ACTION_TIMER_SYNC_COMPLETED)
        )
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(timerSyncCompletedReceiver)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupUI() {
        binding.buttonViewTimers.setOnClickListener {
            startActivity(Intent(this, TimerListActivity::class.java))
        }

        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        binding.buttonReceiverSettings.setOnClickListener {
            startActivity(Intent(this, ReceiverSettingsActivity::class.java))
        }

        binding.buttonTestConnection.setOnClickListener {
            updateConnectionStatusIndicator(true)
        }

        binding.infoIcon.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private suspend fun testConnection(): Boolean {
        if (!prefManager.isReceiverConfigured()) return false
        // Use repository instead of direct client call
        val result = timerRepository.getTimers()
        return result is TimerResult.Success
    }

    private fun updateConnectionStatusIndicator(showToast: Boolean) {
        lifecycleScope.launch {
            val isConnected = testConnection() // testConnection already handles Dispatchers.IO inside repository
            
            if (isConnected) {
                binding.statusEnigmaIcon.setImageResource(R.drawable.ic_outline_check_circle_24)
                binding.statusEnigmaIcon.setColorFilter(ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark))
                binding.statusEnigmaText.text = getString(R.string.status_enigma_connected)
                if (showToast) Toast.makeText(this@MainActivity, getString(R.string.toast_connection_test_successful), Toast.LENGTH_SHORT).show()
            } else {
                binding.statusEnigmaIcon.setImageResource(R.drawable.ic_outline_error_24)
                binding.statusEnigmaIcon.setColorFilter(Color.RED)
                binding.statusEnigmaText.text = getString(R.string.status_enigma_disconnected)
                if (showToast) Toast.makeText(this@MainActivity, getString(R.string.toast_connection_test_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isTvBrowserInstalled(): Boolean {
        return try {
            val pm = packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo("org.tvbrowser.tvbrowser", PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo("org.tvbrowser.tvbrowser", 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun runChecks() {
        if (isTvBrowserInstalled()) {
            binding.statusTvBrowserIcon.setImageResource(R.drawable.ic_outline_check_circle_24)
            binding.statusTvBrowserIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.statusTvBrowserText.text = getString(R.string.status_tvbrowser_found)
        } else {
            binding.statusTvBrowserIcon.setImageResource(R.drawable.ic_outline_error_24)
            binding.statusTvBrowserIcon.setColorFilter(Color.RED)
            binding.statusTvBrowserText.text = getString(R.string.status_tvbrowser_not_found)
        }

        updatePeriodicSyncStatusIndicator()
        updateNotificationStatusIndicator()
    }

    private fun updatePeriodicSyncStatusIndicator() {
        val intervalHours = prefManager.getSyncIntervalHours()
        if (intervalHours > 0) {
            binding.statusTimerSyncIcon.setImageResource(R.drawable.ic_outline_check_circle_24)
            binding.statusTimerSyncIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.statusTimerSyncText.text = getString(R.string.status_periodic_sync_enabled)
        } else {
            binding.statusTimerSyncIcon.setImageResource(R.drawable.ic_outline_error_24)
            binding.statusTimerSyncIcon.setColorFilter(Color.RED)
            binding.statusTimerSyncText.text = getString(R.string.status_periodic_sync_disabled)
        }
    }

    private fun updateNotificationStatusIndicator() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                binding.statusNotificationIcon.setImageResource(R.drawable.ic_outline_check_circle_24)
                binding.statusNotificationIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                binding.statusNotificationText.text = getString(R.string.status_notifications_enabled)
            } else {
                binding.statusNotificationIcon.setImageResource(R.drawable.ic_outline_error_24)
                binding.statusNotificationIcon.setColorFilter(Color.RED)
                binding.statusNotificationText.text = getString(R.string.status_notifications_disabled)
            }
        } else {
            binding.statusNotificationIcon.setImageResource(R.drawable.ic_outline_check_circle_24)
            binding.statusNotificationIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.statusNotificationText.text = getString(R.string.status_notifications_enabled)
        }
    }

    // In MainActivity.kt -> updateLastSyncStatus()
    // In MainActivity.kt -> updateLastSyncStatus()
    private fun updateLastSyncStatus() {
        val lastSyncTimestamp = prefManager.getLastSyncTimestamp()
        if (lastSyncTimestamp > 0) {
            val date = Date(lastSyncTimestamp)

            // The "Pro" Way: Fetches the user's preferred date/time format from Android Settings
            val dateFormat = android.text.format.DateFormat.getDateFormat(this)
            val timeFormat = android.text.format.DateFormat.getTimeFormat(this)

            val formattedDate = dateFormat.format(date)
            val formattedTime = timeFormat.format(date)

            val dateString = "$formattedDate $formattedTime"
            binding.statusLastSyncText.text = getString(R.string.status_last_sync_value, dateString)
        }
    }
}
