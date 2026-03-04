package io.github.legandy.enigmabridge.receiversettings

import android.content.Context
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
import io.github.legandy.enigmabridge.timer.TimerCheckWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit
import io.github.legandy.enigmabridge.core.AppThemeManager
import io.github.legandy.enigmabridge.core.PreferenceManager

class ReceiverSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReceiverSettingsBinding
    private lateinit var prefManager: PreferenceManager
    private var bouquetsMap: Map<String, String> = emptyMap()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefManager = PreferenceManager(this)
        AppThemeManager.applyThemeAndAccentColor(this)
        super.onCreate(savedInstanceState)
        binding = ActivityReceiverSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefManager.getThemeMode()

        initializeBouquetDisplay()
        loadReceiverSettings()
        setupListeners()

    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        saveReceiverSettings() // Auto-save on pause
    }

    private fun initializeBouquetDisplay() {
        val savedBouquetsJson = prefManager.getBouquetsJson()
        if (!savedBouquetsJson.isNullOrEmpty()) {
            try {
                bouquetsMap = json.decodeFromString<Map<String, String>>(savedBouquetsJson)
                updateBouquetsSpinner(bouquetsMap)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding saved bouquets: ${e.message}")
                bouquetsMap = emptyMap()
            }
        }
    }

    private fun updateBouquetsSpinner(bouquets: Map<String, String>) {
        val bouquetNames = bouquets.keys.toList()
        val adapter = ArrayAdapter(
            this@ReceiverSettingsActivity,
            android.R.layout.simple_spinner_item,
            bouquetNames
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.bouquetsSpinner.adapter = adapter

        // Restore the last selected bouquet
        val savedBouquetName = prefManager.getSelectedBouquetName()
        if (savedBouquetName != null) {
            val position = bouquetNames.indexOf(savedBouquetName)
            if (position >= 0) {
                binding.bouquetsSpinner.setSelection(position)
            }
        }
    }

    private fun loadReceiverSettings() {
        binding.editIpAddress.setText(prefManager.getIpAddress())
        binding.switchUseHttps.isChecked = prefManager.getUseHttps()
        binding.editUsername.setText(prefManager.getUsername().ifEmpty { "root" })
        binding.editPassword.setText(prefManager.getPassword())
        binding.editMinutesBefore.setText(prefManager.getMinutesBefore().toString())
        binding.editMinutesAfter.setText(prefManager.getMinutesAfter().toString())
        binding.editSyncInterval.setText(prefManager.getSyncIntervalHours().toString())
    }

    private fun saveReceiverSettings() {
        val before = binding.editMinutesBefore.text.toString().toIntOrNull() ?: 2
        val after = binding.editMinutesAfter.text.toString().toIntOrNull() ?: 5
        val syncInterval = binding.editSyncInterval.text.toString().toIntOrNull() ?: 0

        prefManager.setIpAddress(binding.editIpAddress.text.toString().trim())
        prefManager.setUseHttps(binding.switchUseHttps.isChecked)
        prefManager.setUsername(binding.editUsername.text.toString())
        prefManager.setPassword(binding.editPassword.text.toString())
        prefManager.setMinutesBefore(before)
        prefManager.setMinutesAfter(after)
        prefManager.setSyncIntervalHours(syncInterval)

        scheduleWork(this, syncInterval)
    }

    private fun setupListeners() {
        binding.buttonTestConnection.setOnClickListener {
            checkReceiverConnectionAndFetchBouquets()
        }

        binding.buttonSyncChannels.setOnClickListener {
            syncSelectedBouquet()
        }
    }

    private fun checkReceiverConnectionAndFetchBouquets() {
        if (!prefManager.isReceiverConfigured()) {
            Toast.makeText(this, getString(R.string.error_ip_address_empty), Toast.LENGTH_LONG).show()
            return
        }

        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val client = prefManager.getEnigmaClient()
            val isConnected = client.checkConnection()

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (isConnected) {
                    Toast.makeText(
                        this@ReceiverSettingsActivity, // Use activity context here
                        getString(R.string.status_enigma_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    fetchBouquets()
                } else {
                    Toast.makeText(
                        this@ReceiverSettingsActivity, // Use activity context here
                        getString(R.string.status_enigma_failed),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun fetchBouquets() {
        showLoading(true)
        if (!prefManager.isReceiverConfigured()) {
            Toast.makeText(this, getString(R.string.error_ip_address_empty), Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val client = prefManager.getEnigmaClient()
            val fetchedBouquets = client.getBouquets()

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (fetchedBouquets != null) {
                    bouquetsMap = fetchedBouquets
                    updateBouquetsSpinner(bouquetsMap)

                    // Save fetched bouquets to preferences
                    prefManager.setBouquetsJson(json.encodeToString(bouquetsMap))

                } else {
                    Toast.makeText(
                        this@ReceiverSettingsActivity, // Use activity context here
                        getString(R.string.error_fetch_bouquets),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun syncSelectedBouquet() {
        if (!prefManager.isReceiverConfigured()) {
            Toast.makeText(this, getString(R.string.error_ip_address_empty), Toast.LENGTH_LONG).show()
            return
        }
        val selectedBouquetName = binding.bouquetsSpinner.selectedItem as? String
        if (selectedBouquetName == null) {
            Toast.makeText(this@ReceiverSettingsActivity, getString(R.string.error_no_bouquet_selected), Toast.LENGTH_SHORT).show()
            return
        }

        val bouquetSref = bouquetsMap[selectedBouquetName]
        if (bouquetSref == null) {
            Toast.makeText(this@ReceiverSettingsActivity, getString(R.string.error_sref_not_found), Toast.LENGTH_LONG).show()
            return
        }

        showLoading(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val client = prefManager.getEnigmaClient()
            val channels = client.getChannelsInBouquet(bouquetSref)

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (channels != null) {
                    val jsonChannels = json.encodeToString(channels)
                    prefManager.setSyncedChannelsJson(jsonChannels)
                    prefManager.setSelectedBouquetName(selectedBouquetName)
                    Toast.makeText(
                        this@ReceiverSettingsActivity, // Use activity context here
                        getString(R.string.sync_success_toast, channels.size),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this@ReceiverSettingsActivity, // Use activity context here
                        getString(R.string.error_sync_channels),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.buttonTestConnection.isEnabled = !isLoading // Control the new button
        binding.buttonSyncChannels.isEnabled = !isLoading
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val TAG = "ReceiverSettingsActivity"

        fun scheduleWork(context: Context, intervalHours: Int) {
            val workManager = WorkManager.getInstance(context.applicationContext)

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