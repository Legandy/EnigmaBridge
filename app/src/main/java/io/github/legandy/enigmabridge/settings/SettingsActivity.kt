package io.github.legandy.enigmabridge.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.databinding.ActivitySettingsBinding // Corrected import
import androidx.appcompat.app.AppCompatDelegate
import android.content.Context
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import io.github.legandy.enigmabridge.main.MainActivity // Import MainActivity for restart
import io.github.legandy.enigmabridge.core.AppThemeManager // Import AppThemeManager

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding // Corrected binding type

    // Data class for accent color items
    data class AccentColorItem(val name: String, @param:ColorRes val colorResId: Int) // Fixed annotation

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyThemeAndAccentColor(this) // Apply theme and accent color here
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences(AppThemeManager.PREFS_NAME, MODE_PRIVATE)

        // Load notification settings, defaulting to true (enabled).
        binding.switchNotifyScheduled.isChecked = prefs.getBoolean("NOTIFY_SCHEDULED_ENABLED", true)
        binding.switchNotifyRecordingStarted.isChecked = prefs.getBoolean("NOTIFY_RECORDING_STARTED_ENABLED", true)
        binding.switchNotifySyncSuccess.isChecked = prefs.getBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", true)

        // Theme Picker Dropdown
        val themeOptions = resources.getStringArray(R.array.theme_options_array)
        val themeAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, themeOptions)
        (binding.textInputLayoutThemeMode.editText as? AutoCompleteTextView)?.setAdapter(themeAdapter)

        val savedThemeMode = prefs.getInt(AppThemeManager.KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        val initialThemeSelection = when (savedThemeMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> themeOptions[0]
            AppCompatDelegate.MODE_NIGHT_NO -> themeOptions[1]
            AppCompatDelegate.MODE_NIGHT_YES -> themeOptions[2]
            else -> themeOptions[0]
        }
        (binding.textInputLayoutThemeMode.editText as? AutoCompleteTextView)?.post {
            val autoCompleteTextView = (binding.textInputLayoutThemeMode.editText as? AutoCompleteTextView)
            autoCompleteTextView?.setText(initialThemeSelection, false)
            autoCompleteTextView?.clearFocus()
            autoCompleteTextView?.requestFocus()
            autoCompleteTextView?.showDropDown() // Added this for robustness
        }


        (binding.textInputLayoutThemeMode.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            val newThemeMode = when (position) {
                0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (newThemeMode != savedThemeMode) {
                prefs.edit().putInt(AppThemeManager.KEY_THEME_MODE, newThemeMode).commit()
                restartApp() // Call restartApp
            }
        }

        // Accent Color Picker Dropdown
        val accentColors = listOf(
            AccentColorItem("Light Blue", R.color.material_blue_200),
            AccentColorItem("Blue", R.color.material_blue_500),
            AccentColorItem("Dark Blue", R.color.material_blue_700),
            AccentColorItem("Purple", R.color.purple_500),
            AccentColorItem("Teal", R.color.teal_500),
            AccentColorItem("Red", R.color.material_red_500),
            AccentColorItem("Green", R.color.material_green_500),
            AccentColorItem("Orange", R.color.material_orange_500)
        )

        val accentColorAdapter = AccentColorAdapter(this, accentColors)
        (binding.textInputLayoutAccentColor.editText as? AutoCompleteTextView)?.setAdapter(accentColorAdapter)

        val savedAccentColorResId = prefs.getInt(AppThemeManager.KEY_ACCENT_COLOR, R.color.material_blue_500)
        val initialAccentColorSelection = accentColors.find { it.colorResId == savedAccentColorResId }?.name ?: accentColors[0].name
        (binding.textInputLayoutAccentColor.editText as? AutoCompleteTextView)?.post {
            val autoCompleteTextView = (binding.textInputLayoutAccentColor.editText as? AutoCompleteTextView)
            autoCompleteTextView?.setText(initialAccentColorSelection, false)
            autoCompleteTextView?.clearFocus()
            autoCompleteTextView?.requestFocus()
            // autoCompleteTextView?.showDropDown() // Removed
        }


        (binding.textInputLayoutAccentColor.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            val selectedAccentColorItem = accentColorAdapter.getItem(position)
            if (selectedAccentColorItem != null) {
                prefs.edit().putInt(AppThemeManager.KEY_ACCENT_COLOR, selectedAccentColorItem.colorResId).commit()
                restartApp() // Call restartApp
            }
        }

        // Copyable Intent Listener
        binding.buttonCopyTimerSyncIntent.setOnClickListener {
            val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
            val intentToCopy = getString(R.string.timer_sync_intent_value)
            val clip = ClipData.newPlainText("Timer Sync Intent", intentToCopy)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(this, "Timer Sync Intent copied to clipboard", Toast.LENGTH_SHORT).show()
        }

    }

    override fun onPause() {
        super.onPause()
        val prefs = getSharedPreferences(AppThemeManager.PREFS_NAME, MODE_PRIVATE)
        prefs.edit().apply {
            // Save the state of the notification switches.
            putBoolean("NOTIFY_SCHEDULED_ENABLED", binding.switchNotifyScheduled.isChecked)
            putBoolean("NOTIFY_RECORDING_STARTED_ENABLED", binding.switchNotifyRecordingStarted.isChecked)
            putBoolean("NOTIFY_SYNC_SUCCESS_ENABLED", binding.switchNotifySyncSuccess.isChecked)

            apply()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // Custom ArrayAdapter for Accent Colors
    private inner class AccentColorAdapter(context: Context, colors: List<AccentColorItem>) :
        ArrayAdapter<AccentColorItem>(context, 0, colors) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent, R.layout.list_item_accent_color)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent, R.layout.list_item_accent_color)
        }

        private fun createView(position: Int, convertView: View?, parent: ViewGroup, layoutRes: Int): View {
            val view = convertView ?: layoutInflater.inflate(layoutRes, parent, false)
            val item = getItem(position)

            if (item != null) {
                val colorSwatch = view.findViewById<View>(R.id.color_swatch)
                val colorName = view.findViewById<TextView>(R.id.color_name)
                colorSwatch.setBackgroundColor(ContextCompat.getColor(context, item.colorResId))
                colorName.text = item.name
            }
            return view
        }
    }

    private fun restartApp() {
        AppThemeManager.applyThemeAndAccentColor(this) // Apply theme and accent color immediately
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity() // Close all activities in this task
        android.os.Process.killProcess(android.os.Process.myPid()) // Terminate the app process
    }
}