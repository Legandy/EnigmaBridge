package io.github.legandy.enigmabridge

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.legandy.enigmabridge.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A dedicated settings activity for the plugin.
 * This can be named MainActivity and act as the launcher.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)

        // Load saved settings
        binding.editIpAddress.setText(prefs.getString("IP_ADDRESS", ""))
        binding.editUsername.setText(prefs.getString("USERNAME", "root"))
        binding.editPassword.setText(prefs.getString("PASSWORD", ""))

        // Save button logic
        binding.buttonSave.setOnClickListener {
            prefs.edit().apply {
                putString("IP_ADDRESS", binding.editIpAddress.text.toString().trim())
                putString("USERNAME", binding.editUsername.text.toString().trim())
                putString("PASSWORD", binding.editPassword.text.toString())
                apply()
            }
            Toast.makeText(this, "Settings Saved!", Toast.LENGTH_SHORT).show()
            runConnectionCheck()
        }

        runChecks()
    }

    private fun isTvBrowserInstalled(): Boolean {
        return try {
            packageManager.getPackageInfo("org.tvbrowser.tvbrowser", 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun runChecks() {
        // TV-Browser Check
        if (isTvBrowserInstalled()) {
            binding.statusTvBrowserIcon.setImageResource(R.drawable.ic_check_circle)
            binding.statusTvBrowserIcon.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            binding.statusTvBrowserText.text = getString(R.string.status_tvbrowser_found)
        } else {
            binding.statusTvBrowserIcon.setImageResource(R.drawable.ic_error)
            binding.statusTvBrowserIcon.setColorFilter(Color.RED)
            binding.statusTvBrowserText.text = getString(R.string.status_tvbrowser_not_found)
        }

        runConnectionCheck()
    }

    private fun runConnectionCheck() {
        val ip = binding.editIpAddress.text.toString().trim()
        val user = binding.editUsername.text.toString().trim()
        val pass = binding.editPassword.text.toString()

        if (ip.isEmpty()) {
            binding.statusEnigmaIcon.setImageResource(R.drawable.ic_error)
            binding.statusEnigmaIcon.setColorFilter(Color.RED)
            binding.statusEnigmaText.text = getString(R.string.status_enigma_failed)
            return
        }

        Toast.makeText(this, "Testing connection...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass)
            val isConnected = client.checkConnection()

            withContext(Dispatchers.Main) {
                if (isConnected) {
                    binding.statusEnigmaIcon.setImageResource(R.drawable.ic_check_circle)
                    binding.statusEnigmaIcon.setColorFilter(ContextCompat.getColor(applicationContext, android.R.color.holo_green_dark))
                    binding.statusEnigmaText.text = getString(R.string.status_enigma_success)
                    Toast.makeText(applicationContext, "Connection successful!", Toast.LENGTH_SHORT).show()
                } else {
                    binding.statusEnigmaIcon.setImageResource(R.drawable.ic_error)
                    binding.statusEnigmaIcon.setColorFilter(Color.RED)
                    binding.statusEnigmaText.text = getString(R.string.status_enigma_failed)
                    Toast.makeText(applicationContext, "Connection failed. Check settings.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
