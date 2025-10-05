package io.github.legandy.enigmabridge

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.legandy.enigmabridge.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private var bouquetsMap: Map<String, String> = emptyMap()

    // **DIAGNOSTIC TAG**
    private val TAG = "ENIGMA_DIAGNOSTIC"

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Toast.makeText(this, getString(R.string.permission_granted), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)

        setupUI()
        requestNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        runChecks()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupUI() {
        binding.editIpAddress.setText(prefs.getString("IP_ADDRESS", ""))
        binding.switchUseHttps.isChecked = prefs.getBoolean("USE_HTTPS", false)
        binding.editUsername.setText(prefs.getString("USERNAME", "root"))
        binding.editPassword.setText(prefs.getString("PASSWORD", ""))

        binding.buttonSave.setOnClickListener {
            prefs.edit().apply {
                putString("IP_ADDRESS", binding.editIpAddress.text.toString().trim())
                putBoolean("USE_HTTPS", binding.switchUseHttps.isChecked)
                putString("USERNAME", binding.editUsername.text.toString().trim())
                putString("PASSWORD", binding.editPassword.text.toString())
                apply()
            }
            runChecks()
        }

        binding.buttonSyncChannels.setOnClickListener {
            syncSelectedBouquet()
        }

        binding.buttonViewTimers.setOnClickListener {
            startActivity(Intent(this, TimerListActivity::class.java))
        }

        binding.buttonSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
                    val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_item, bouquetNames)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    binding.bouquetsSpinner.adapter = adapter
                } else {
                    Toast.makeText(applicationContext, getString(R.string.error_fetch_bouquets), Toast.LENGTH_LONG).show()
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

        // **DIAGNOSTIC LOGGING START**
        Log.d(TAG, "------------------- CHANNEL SYNC START -------------------")
        Log.d(TAG, "Selected bouquet to sync: NAME='$selectedBouquetName', SREF='$bouquetSref'")
        // **DIAGNOSTIC LOGGING END**

        showLoading(true)
        val ip = binding.editIpAddress.text.toString().trim()
        val user = binding.editUsername.text.toString().trim()
        val pass = binding.editPassword.text.toString()

        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass, prefs)
            val channels = client.getChannelsInBouquet(bouquetSref)

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (channels != null) {
                    // **DIAGNOSTIC LOGGING START**
                    Log.d(TAG, "SUCCESS: Fetched ${channels.size} channels from receiver.")
                    channels.forEach { (name, sRef) ->
                        Log.d(TAG, "  -> Fetched Channel: NAME='$name', SREF='$sRef'")
                    }
                    // **DIAGNOSTIC LOGGING END**

                    val jsonChannels = Json.encodeToString(channels)
                    prefs.edit().putString("SYNCED_CHANNELS", jsonChannels).apply()
                    Log.d(TAG, "Saved channel list to SharedPreferences.")
                    Log.d(TAG, "------------------- CHANNEL SYNC END ---------------------")


                    Toast.makeText(applicationContext, getString(R.string.sync_success_toast, channels.size), Toast.LENGTH_LONG).show()
                    if (prefs.getBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", true)) {
                        NotificationHelper.sendSyncSuccessNotification(applicationContext, channels.size)
                    }

                } else {
                    Log.e(TAG, "CRITICAL FAILURE: getChannelsInBouquet returned NULL.")
                    Log.d(TAG, "------------------- CHANNEL SYNC END ---------------------")
                    Toast.makeText(applicationContext, getString(R.string.error_sync_channels), Toast.LENGTH_LONG).show()
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
            binding.statusTvBrowserIcon.setImageResource(R.drawable.ic_check_circle)
            binding.statusTvBrowserIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.statusTvBrowserText.text = getString(R.string.status_tvbrowser_found)
        } else {
            binding.statusTvBrowserIcon.setImageResource(R.drawable.ic_error)
            binding.statusTvBrowserIcon.setColorFilter(Color.RED)
            binding.statusTvBrowserText.text = getString(R.string.status_tvbrowser_not_found)
        }

        updatePeriodicSyncStatusIndicator()

        val ip = binding.editIpAddress.text.toString().trim()
        val user = binding.editUsername.text.toString().trim()
        val pass = binding.editPassword.text.toString()

        if (ip.isEmpty()) {
            binding.statusEnigmaIcon.setImageResource(R.drawable.ic_error)
            binding.statusEnigmaIcon.setColorFilter(Color.RED)
            binding.statusEnigmaText.text = getString(R.string.status_enigma_failed)
            return
        }

        showLoading(true)
        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass, prefs)
            val isConnected = client.checkConnection()

            withContext(Dispatchers.Main) {
                showLoading(false)
                if (isConnected) {
                    binding.statusEnigmaIcon.setImageResource(R.drawable.ic_check_circle)
                    binding.statusEnigmaIcon.setColorFilter(ContextCompat.getColor(applicationContext, android.R.color.holo_green_dark))
                    binding.statusEnigmaText.text = getString(R.string.status_enigma_success)
                    fetchBouquets()
                } else {
                    binding.statusEnigmaIcon.setImageResource(R.drawable.ic_error)
                    binding.statusEnigmaIcon.setColorFilter(Color.RED)
                    binding.statusEnigmaText.text = getString(R.string.status_enigma_failed)
                }
            }
        }
    }

    private fun updatePeriodicSyncStatusIndicator() {
        val intervalHours = prefs.getInt("SYNC_INTERVAL_HOURS", 0)
        if (intervalHours > 0) {
            binding.statusTimerSyncIcon.setImageResource(R.drawable.ic_check_circle)
            binding.statusTimerSyncIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.statusTimerSyncText.text = getString(R.string.status_periodic_sync_enabled)
        } else {
            binding.statusTimerSyncIcon.setImageResource(R.drawable.ic_error)
            binding.statusTimerSyncIcon.setColorFilter(Color.RED)
            binding.statusTimerSyncText.text = getString(R.string.status_periodic_sync_disabled)
        }
    }

    private fun showLoading(isLoading: Boolean) {
        binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }
}

