package io.github.legandy.enigmabridge.core

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.data.PreferenceManager
import io.github.legandy.enigmabridge.tvbrowser.AdvancedScheduleActivity

object AppThemeManager {

    fun applyThemeAndAccentColor(activity: Activity) {
        val prefManager = PreferenceManager(activity)

        AppCompatDelegate.setDefaultNightMode(prefManager.getThemeMode())

        val savedAccentColor = prefManager.getAccentColor()

        val baseThemeResId = when (savedAccentColor) {
            R.color.material_green_500 -> R.style.AppTheme_Green
            R.color.material_orange_500 -> R.style.AppTheme_Orange
            R.color.material_red_500 -> R.style.AppTheme_Red
            R.color.purple_500 -> R.style.AppTheme_Purple
            R.color.teal_500 -> R.style.AppTheme_Teal
            R.color.material_blue_200 -> R.style.AppTheme_Blue_200
            R.color.material_blue_500 -> R.style.AppTheme_Blue_500
            R.color.material_blue_700 -> R.style.AppTheme_Blue_700
            else -> R.style.AppTheme_Blue
        }

        val finalThemeResId = if (activity is AdvancedScheduleActivity) {
            when (baseThemeResId) {
                R.style.AppTheme_Green -> R.style.AppTheme_Green_Dialog
                R.style.AppTheme_Orange -> R.style.AppTheme_Orange_Dialog
                R.style.AppTheme_Red -> R.style.AppTheme_Red_Dialog
                R.style.AppTheme_Purple -> R.style.AppTheme_Purple_Dialog
                R.style.AppTheme_Teal -> R.style.AppTheme_Teal_Dialog
                R.style.AppTheme_Blue_200 -> R.style.AppTheme_Blue_200_Dialog
                R.style.AppTheme_Blue_500 -> R.style.AppTheme_Blue_500_Dialog
                R.style.AppTheme_Blue_700 -> R.style.AppTheme_Blue_700_Dialog
                else -> R.style.AppTheme_Blue_Dialog
            }
        } else {
            baseThemeResId
        }

        activity.setTheme(finalThemeResId)
    }
}