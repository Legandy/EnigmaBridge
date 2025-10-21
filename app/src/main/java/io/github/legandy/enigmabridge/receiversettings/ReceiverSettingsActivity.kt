package io.github.legandy.enigmabridge.receiversettings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.ActivityReceiverSettingsBinding
import io.github.legandy.enigmabridge.receiver.TimerCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

class ReceiverSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiverSettingsBinding
    private lateinit var prefs: SharedPreferences
    private var bouquetsMap: Map<String, String> = emptyMap()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        // title is now set in the toolbar XML

        prefs = getSharedPreferences("EnigmaSettings", MODE_PRIVATE)

        loadReceiverSettings()
        setupListeners()
        checkReceiverConnectionAndFetchBouquets()
    }

    override fun onPause() {
        super.onPause()
        saveReceiverSettings() // Auto-save on pause
        Log.d(TAG, "Unregistering timerSyncCompletedReceiver in onPause.")
        // The original onPause method in MainActivity handles broadcast receiver unregistration. This is not needed here.
        // Assuming unregistering timerSyncCompletedReceiver was a copy-paste error from MainActivity and not relevant here.
    }

    private fun loadReceiverSettings() {
        binding.editIpAddress.setText(prefs.getString("IP_ADDRESS", ""))
        binding.switchUseHttps.isChecked = prefs.getBoolean("USE_HTTPS", false)
        binding.editUsername.setText(prefs.getString("USERNAME", "root"))
        binding.editPassword.setText(prefs.getString("PASSWORD", ""))
        binding.editMinutesBefore.setText(prefs.getInt("MINUTES_BEFORE", 2).toString())
        binding.editMinutesAfter.setText(prefs.getInt("MINUTES_AFTER", 5).toString())
        binding.editSyncInterval.setText(prefs.getInt("SYNC_INTERVAL_HOURS", 0).toString())
    }

    private fun saveReceiverSettings() {
        val before = binding.editMinutesBefore.text.toString().toIntOrNull() ?: 0
        val after = binding.editMinutesAfter.text.toString().toIntOrNull() ?: 0
        val syncInterval = binding.editSyncInterval.text.toString().toIntOrNull() ?: 0

        prefs.edit().apply {
            putString("IP_ADDRESS", binding.editIpAddress.text.toString().trim())
            putBoolean("USE_HTTPS", binding.switchUseHttps.isChecked)
            putString("USERNAME", binding.editUsername.text.toString().trim())
            putString("PASSWORD", binding.editPassword.text.toString())
            putInt("MINUTES_BEFORE", before)
            putInt("MINUTES_AFTER", after)
            putInt("SYNC_INTERVAL_HOURS", syncInterval)
            apply()
        }
        // Removed Toast message for silent auto-save

        scheduleWork(this, syncInterval)
    }

    private fun setupListeners() {
        // Removed binding.buttonSaveReceiverSettings.setOnClickListener as the button is removed

        binding.buttonSyncChannels.setOnClickListener {
            syncSelectedBouquet()
        }
    }

    private fun checkReceiverConnectionAndFetchBouquets() {
        val ip = binding.editIpAddress.text.toString().trim()
        val user = binding.editUsername.text.toString().trim()
        val pass = binding.editPassword.text.toString()

        if (ip.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_ip_address_empty), Toast.LENGTH_LONG).show()
            return
        }

        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass, prefs)
            val isConnected = client.checkConnection()

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (isConnected) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.status_enigma_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    fetchBouquets()
                } else {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.status_enigma_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun fetchBouquets() {
        showLoading(true)
        val ip = binding.editIpAddress.text.toString().trim()
        val user = binding.editUsername.text.toString().trim()
        val pass = binding.editPassword.text.toString()

        if (ip.isEmpty()) {
            showLoading(false)
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass, prefs)
            val fetchedBouquets = client.getBouquets()

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (fetchedBouquets != null) {
                    bouquetsMap = fetchedBouquets
                    val bouquetNames = bouquetsMap.keys.toList()
                    val adapter = ArrayAdapter(
                        this@ReceiverSettingsActivity,
                        android.R.layout.simple_spinner_item,
                        bouquetNames
                    )
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.bouquetsSpinner.adapter = adapter

                    // Restore the last selected bouquet
                    val savedBouquetName = prefs.getString("SELECTED_BOUQUET_NAME", null)
                    if (savedBouquetName != null) {
                        val position = bouquetNames.indexOf(savedBouquetName)
                        if (position >= 0) {
                            binding.bouquetsSpinner.setSelection(position)
                        }
                    }
                } else {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.error_fetch_bouquets),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun syncSelectedBouquet() {
        val selectedBouquetName = binding.bouquetsSpinner.selectedItem as? String
        if (selectedBouquetName == null) {
            Toast.makeText(this, getString(R.string.error_no_bouquet_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val bouquetSref = bouquetsMap[selectedBouquetName]
        if (bouquetSref == null) {
            Toast.makeText(this, getString(R.string.error_sref_not_found), Toast.LENGTH_LONG).show()
            return
        }

        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val ip = prefs.getString("IP_ADDRESS", "") ?: ""
            val user = prefs.getString("USERNAME", "root") ?: ""
            val pass = prefs.getString("PASSWORD", "") ?: ""
            val client = EnigmaClient(ip, user, pass, prefs)
            val channels = client.getChannelsInBouquet(bouquetSref)

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (channels != null) {
                    val jsonChannels = json.encodeToString(channels)
                    prefs.edit().apply {
                        putString("SYNCED_CHANNELS", jsonChannels)
                        putString("SELECTED_BOUQUET_NAME", selectedBouquetName) // Save the selected bouquet name
                        apply()
                    }
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.sync_success_toast, channels.size),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.error_sync_channels),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        // Disable buttons while loading to prevent multiple actions
        // Removed: binding.buttonSaveReceiverSettings.isEnabled = !isLoading
        binding.buttonSyncChannels.isEnabled = !isLoading
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val TAG = "ReceiverSettingsActivity"
        fun scheduleWork(context: Context, intervalHours: Int) {
            val workManager = WorkManager.getInstance(context)

            if (intervalHours > 0) {
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()

                val periodicWorkRequest = PeriodicWorkRequest.Builder(
                    TimerCheckWorker::class.java,
                    intervalHours.toLong(),
                    TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                workManager.enqueueUniquePeriodicWork(
                    "TimerCheckWorker",
                    ExistingPeriodicWorkPolicy.UPDATE,
                    periodicWorkRequest
                )
                Log.d("ReceiverSettingsActivity", "Periodic timer check scheduled for every $intervalHours hours.")
            } else {
                workManager.cancelUniqueWork("TimerCheckWorker")
                Log.d("ReceiverSettingsActivity", "Periodic timer check cancelled.")
            }
        }
    }
}