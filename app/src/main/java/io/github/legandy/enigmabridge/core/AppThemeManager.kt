package io.github.legandy.enigmabridge.core

import android.app.Activity
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.tvbrowser.AdvancedScheduleActivity // Import AdvancedScheduleActivity

object AppThemeManager {

    const val PREFS_NAME = "EnigmaSettings"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_ACCENT_COLOR = "accent_color"

    fun applyThemeAndAccentColor(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Apply Theme Mode
        val savedThemeMode = prefs.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedThemeMode)

        // Determine if the activity is AdvancedScheduleActivity
        val isAdvancedScheduleActivity = activity is AdvancedScheduleActivity

        // Apply Accent Color
        val savedAccentColorResId = prefs.getInt(KEY_ACCENT_COLOR, R.color.material_blue_500)
        val baseThemeResId = when (savedAccentColorResId) {
            R.color.material_green_500 -> R.style.AppTheme_Green
            R.color.material_orange_500 -> R.style.AppTheme_Orange
            R.color.material_red_500 -> R.style.AppTheme_Red
            else -> R.style.AppTheme_Blue // Default to blue theme if not found or default blue color
        }

        // Select the appropriate dialog theme if it's AdvancedScheduleActivity
        val finalThemeResId = if (isAdvancedScheduleActivity) {
            when (savedAccentColorResId) {
                R.color.material_green_500 -> R.style.AppTheme_Green_Dialog
                R.color.material_orange_500 -> R.style.AppTheme_Orange_Dialog
                R.color.material_red_500 -> R.style.AppTheme_Red_Dialog
                else -> R.style.AppTheme_Blue_Dialog // Default to blue dialog theme
            }
        } else {
            baseThemeResId
        }

        activity.setTheme(finalThemeResId)
    }
}