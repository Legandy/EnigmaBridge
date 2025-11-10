package io.github.legandy.enigmabridge.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.about.AboutActivity // Added import for AboutActivity
import io.github.legandy.enigmabridge.databinding.ActivityMainBinding
import io.github.legandy.enigmabridge.timer.TimerListActivity
import io.github.legandy.enigmabridge.receiversettings.ReceiverSettingsActivity
import io.github.legandy.enigmabridge.settings.SettingsActivity
import io.github.legandy.enigmabridge.receiversettings.EnigmaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences

    companion object {
        const val ACTION_TIMER_SYNC_COMPLETED = "io.github.legandy.enigmabridge.TIMER_SYNC_COMPLETED"
        private const val TAG = "MainActivity"
    }

    private val timerSyncCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "--- BroadcastReceiver onReceive() TRIGGERED ---")
            if (intent?.action == ACTION_TIMER_SYNC_COMPLETED) {
                Log.d(TAG, "Broadcast action matches. Calling UI update.")
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
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("EnigmaSettings", MODE_PRIVATE)

        setupUI()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        runChecks()
        updateConnectionStatusIndicator(false) // Check connection on resume, no toast
        updateLastSyncStatus()
        Log.d(TAG, "Registering timerSyncCompletedReceiver in onResume.")
        LocalBroadcastManager.getInstance(this).registerReceiver(timerSyncCompletedReceiver,
            IntentFilter(ACTION_TIMER_SYNC_COMPLETED)
        )
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "Unregistering timerSyncCompletedReceiver in onPause.")
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

        // --- Test Connection Button ---
        binding.buttonTestConnection.setOnClickListener {
            updateConnectionStatusIndicator(true) // Check connection and show toast
        }

        // --- Info Icon Listener ---
        binding.infoIcon.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
    }

    private suspend fun testConnection(): Boolean {
        val receiverIp = prefs.getString("IP_ADDRESS", "") ?: ""
        val receiverUsername = prefs.getString("USERNAME", "") ?: ""
        val receiverPassword = prefs.getString("PASSWORD", "") ?: ""

        if (receiverIp.isBlank()) {
            Log.d(TAG, "Connection test skipped: IP address missing.")
            return false
        }

        return try {
            val client = EnigmaClient(receiverIp, receiverUsername, receiverPassword, prefs)
            // Attempt a simple operation to test connection, e.g., get timer list
            client.getTimerList()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            false
        }
    }

    private fun updateConnectionStatusIndicator(showToast: Boolean) {
        Log.d(TAG, "updateConnectionStatusIndicator() called, showToast: $showToast")
        CoroutineScope(Dispatchers.IO).launch {
            val isConnected = testConnection()
            withContext(Dispatchers.Main) {
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
        } catch (e: PackageManager.NameNotFoundException) {
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
        val intervalHours = prefs.getInt("SYNC_INTERVAL_HOURS", 0)
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

    private fun updateLastSyncStatus() {
        Log.d(TAG, "updateLastSyncStatus() called.")
        val lastSyncTimestamp = prefs.getLong("LAST_TIMER_SYNC_TIMESTAMP", 0)
        if (lastSyncTimestamp > 0) {
            val sdf = SimpleDateFormat("dd.MM.yyyy 'at' HH:mm", Locale.getDefault())
            val dateString = sdf.format(Date(lastSyncTimestamp))
            runOnUiThread {
                binding.statusLastSyncText.text = getString(R.string.status_last_sync_value, dateString)
            }
        } else {
            runOnUiThread {
                binding.statusLastSyncText.text = getString(R.string.status_last_sync_never)
            }
        }
    }
}