package io.github.legandy.enigmabridge.core

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.data.PreferenceManager
import io.github.legandy.enigmabridge.tvbrowser.AdvancedScheduleActivity // Import AdvancedScheduleActivity

// Theme Manager
object AppThemeManager {

    fun applyThemeAndAccentColor(activity: Activity) {
        val prefManager = PreferenceManager(activity)

        // Apply Dark/Light Mode
        AppCompatDelegate.setDefaultNightMode(prefManager.getThemeMode())

        // Determine Accent Color Choice
        val savedAccentColor = prefManager.getAccentColor()

        // Color Resource ID to XML Style
        val baseThemeResId = when (savedAccentColor) {
            R.color.material_green_500 -> R.style.AppTheme_Green
            R.color.material_orange_500 -> R.style.AppTheme_Orange
            R.color.material_red_500 -> R.style.AppTheme_Red
            R.color.material_blue_200, R.color.material_blue_500, R.color.material_blue_700 -> R.style.AppTheme_Blue
            else -> R.style.AppTheme_Blue
        }

        // Dialogs for specific Activities
        val finalThemeResId = if (activity is AdvancedScheduleActivity) {
            when (baseThemeResId) {
                R.style.AppTheme_Green -> R.style.AppTheme_Green_Dialog
                R.style.AppTheme_Orange -> R.style.AppTheme_Orange_Dialog
                R.style.AppTheme_Red -> R.style.AppTheme_Red_Dialog
                else -> R.style.AppTheme_Blue_Dialog
            }
        } else {
            baseThemeResId
        }

        activity.setTheme(finalThemeResId)
    }
}