package io.github.legandy.enigmabridge.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.core.AppThemeManager
import io.github.legandy.enigmabridge.data.PreferenceManager
import io.github.legandy.enigmabridge.databinding.ActivitySettingsBinding
import io.github.legandy.enigmabridge.databinding.ListItemAccentColorBinding
import io.github.legandy.enigmabridge.ui.main.MainActivity

// ToDo: Refactor to Jetpack Compose

// Screen for app settings
class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefManager: PreferenceManager

    data class AccentColorItem(val name: String, @param:ColorRes val colorResId: Int)

    override fun onCreate(savedInstanceState: Bundle?) {

        prefManager = PreferenceManager(this)

        AppThemeManager.applyThemeAndAccentColor(this)
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.switchNotifyScheduled.isChecked = prefManager.isNotifyScheduledEnabled()
        binding.switchNotifyRecordingStarted.isChecked =
            prefManager.isNotifyRecordingStartedEnabled()
        binding.switchNotifySyncSuccess.isChecked = prefManager.isNotifySyncSuccessEnabled()

        val themeOptions = resources.getStringArray(R.array.theme_options_array)
        val themeAdapter =
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, themeOptions)
        (binding.textInputLayoutThemeMode.editText as? AutoCompleteTextView)?.setAdapter(
            themeAdapter
        )

        val savedThemeMode = prefManager.getThemeMode()
        val initialThemeSelection = when (savedThemeMode) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> themeOptions[0]
            AppCompatDelegate.MODE_NIGHT_NO -> themeOptions[1]
            AppCompatDelegate.MODE_NIGHT_YES -> themeOptions[2]
            else -> themeOptions[0]
        }
        (binding.textInputLayoutThemeMode.editText as? AutoCompleteTextView)?.post {
            val autoCompleteTextView =
                (binding.textInputLayoutThemeMode.editText as? AutoCompleteTextView)
            autoCompleteTextView?.setText(initialThemeSelection, false)
            autoCompleteTextView?.clearFocus()
            autoCompleteTextView?.requestFocus()
            autoCompleteTextView?.showDropDown()
        }


        (binding.textInputLayoutThemeMode.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            val newThemeMode = when (position) {
                0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            if (newThemeMode != prefManager.getThemeMode()) {
                prefManager.setThemeMode(newThemeMode)
                restartApp()
            }
        }

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
        (binding.textInputLayoutAccentColor.editText as? AutoCompleteTextView)?.setAdapter(
            accentColorAdapter
        )

        val savedAccentColorResId = prefManager.getAccentColor()
        val initialAccentColorSelection =
            accentColors.find { it.colorResId == savedAccentColorResId }?.name
                ?: accentColors[0].name
        (binding.textInputLayoutAccentColor.editText as? AutoCompleteTextView)?.post {
            val autoCompleteTextView =
                (binding.textInputLayoutAccentColor.editText as? AutoCompleteTextView)
            autoCompleteTextView?.setText(initialAccentColorSelection, false)
            autoCompleteTextView?.clearFocus()
            autoCompleteTextView?.requestFocus()
        }


        (binding.textInputLayoutAccentColor.editText as? AutoCompleteTextView)?.setOnItemClickListener { _, _, position, _ ->
            val selectedAccentColorItem = accentColorAdapter.getItem(position)
            if (selectedAccentColorItem != null) {
                prefManager.setAccentColor(selectedAccentColorItem.colorResId)
                restartApp()
            }
        }

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
        prefManager.setNotifyScheduledEnabled(binding.switchNotifyScheduled.isChecked)
        prefManager.setNotifyRecordingStartedEnabled(binding.switchNotifyRecordingStarted.isChecked)
        prefManager.setNotifySyncSuccessEnabled(binding.switchNotifySyncSuccess.isChecked)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    private inner class AccentColorAdapter(context: Context, colors: List<AccentColorItem>) :
        ArrayAdapter<AccentColorItem>(context, 0, colors) {

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent)
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return createView(position, convertView, parent)
        }

        private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
            val itemBinding = if (convertView == null) {
                ListItemAccentColorBinding.inflate(layoutInflater, parent, false)
            } else {
                ListItemAccentColorBinding.bind(convertView)
            }
            val item = getItem(position)

            if (item != null) {
                itemBinding.colorSwatch.setBackgroundColor(
                    ContextCompat.getColor(
                        context,
                        item.colorResId
                    )
                )
                itemBinding.colorName.text = item.name
            }
            return itemBinding.root
        }
    }

    private fun restartApp() {
        AppThemeManager.applyThemeAndAccentColor(this)
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}
