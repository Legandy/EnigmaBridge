package io.github.legandy.enigmabridge.core

import android.content.Context
import androidx.core.content.edit
import io.github.legandy.enigmabridge.receiversettings.EnigmaClient

class PreferenceManager (context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isReceiverConfigured(): Boolean = getIpAddress().isNotBlank()

    fun getEnigmaClient(): EnigmaClient {
        return EnigmaClient(
            getIpAddress(),
            getUsername(),
            getPassword(),
            getUseHttps()
        )
    }
    fun getIpAddress(): String = prefs.getString(KEY_IP_ADDRESS, "") ?: ""
    fun setIpAddress(ip: String) = prefs.edit { putString(KEY_IP_ADDRESS, ip) }

    fun getUsername(): String = prefs.getString(KEY_USERNAME, "") ?: ""
    fun setUsername(username: String) = prefs.edit { putString(KEY_USERNAME, username) }

    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""
    fun setPassword(password: String) = prefs.edit { putString(KEY_PASSWORD, password) }

    fun getUseHttps(): Boolean = prefs.getBoolean(KEY_USE_HTTPS, false)
    fun setUseHttps(useHttps: Boolean) = prefs.edit { putBoolean(KEY_USE_HTTPS, useHttps) }


    fun getSyncIntervalHours(): Int = prefs.getInt(KEY_SYNC_INTERVAL_HOURS, 0)
    fun setSyncIntervalHours(hours: Int) = prefs.edit { putInt(KEY_SYNC_INTERVAL_HOURS, hours) }

    fun getLastSyncTimestamp(): Long = prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    fun setLastSyncTimestamp(timestamp: Long) = prefs.edit { putLong(KEY_LAST_SYNC_TIMESTAMP, timestamp) }

    fun getPreviousTimersJson(): String? = prefs.getString(KEY_PREVIOUS_TIMERS, null)
    fun setPreviousTimersJson(json: String) = prefs.edit { putString(KEY_PREVIOUS_TIMERS, json) }

    fun getScheduledNotificationIds(): Set<String> = prefs.getStringSet(KEY_SCHEDULED_NOTIFICATIONS, emptySet()) ?: emptySet()
    fun setScheduledNotificationIds(ids: Set<String>) = prefs.edit { putStringSet(KEY_SCHEDULED_NOTIFICATIONS, ids) }

    fun isNotifyScheduledEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_SCHEDULED, true)
    fun setNotifyScheduledEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_NOTIFY_SCHEDULED, enabled) }

    fun isNotifyRecordingStartedEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_RECORDING_STARTED, true)
    fun setNotifyRecordingStartedEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_NOTIFY_RECORDING_STARTED, enabled) }

    fun isNotifySyncSuccessEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFY_SYNC_SUCCESS, true)
    fun setNotifySyncSuccessEnabled(enabled: Boolean) = prefs.edit { putBoolean(KEY_NOTIFY_SYNC_SUCCESS, enabled) }

    fun getMinutesBefore(): Int = prefs.getInt(KEY_MINUTES_BEFORE, 2)
    fun setMinutesBefore(minutes: Int) = prefs.edit { putInt(KEY_MINUTES_BEFORE, minutes) }

    fun getMinutesAfter(): Int = prefs.getInt(KEY_MINUTES_AFTER, 5)
    fun setMinutesAfter(minutes: Int) = prefs.edit { putInt(KEY_MINUTES_AFTER, minutes) }

    fun getSelectedBouquetName(): String? = prefs.getString(KEY_SELECTED_BOUQUET_NAME, null)
    fun setSelectedBouquetName(name: String?) = prefs.edit { putString(KEY_SELECTED_BOUQUET_NAME, name) }

    fun getBouquetsJson(): String? = prefs.getString(KEY_BOUQUETS_JSON, null)
    fun setBouquetsJson(json: String?) = prefs.edit { putString(KEY_BOUQUETS_JSON, json) }

    fun getSyncedChannelsJson(): String? = prefs.getString(KEY_SYNCED_CHANNELS_JSON, null)
    fun setSyncedChannelsJson(json: String?) = prefs.edit { putString(KEY_SYNCED_CHANNELS_JSON, json) }

    fun getThemeMode(): Int = prefs.getInt(KEY_THEME_MODE, -1)
    fun setThemeMode(themeMode: Int) = prefs.edit { putInt(KEY_THEME_MODE, themeMode) }
    fun getAccentColor(): Int = prefs.getInt(KEY_ACCENT_COLOR, android.R.color.holo_blue_light)
    fun setAccentColor(colorResId: Int) = prefs.edit { putInt(KEY_ACCENT_COLOR, colorResId) }

    fun getMarkedProgramIds(): Set<String> {
        val idString = prefs.getString(KEY_MARKED_IDS, null)
        return idString?.split(',')?.toSet() ?: emptySet()
    }

    fun setMarkedProgramIds(ids: Set<String>) {
        prefs.edit { putString(KEY_MARKED_IDS, ids.joinToString(",")) }
    }

    companion object {
        private const val PREFS_NAME = "EnigmaSettings"
        private const val KEY_IP_ADDRESS = "IP_ADDRESS"
        private const val KEY_USERNAME = "USERNAME"
        private const val KEY_PASSWORD = "PASSWORD"
        private const val KEY_USE_HTTPS = "USE_HTTPS"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_ACCENT_COLOR = "accent_color"
        private const val KEY_SYNC_INTERVAL_HOURS = "SYNC_INTERVAL_HOURS"
        private const val KEY_LAST_SYNC_TIMESTAMP = "LAST_TIMER_SYNC_TIMESTAMP"
        private const val KEY_PREVIOUS_TIMERS = "PREVIOUS_TIMERS_LIST"
        private const val KEY_SCHEDULED_NOTIFICATIONS = "SCHEDULED_NOTIFICATION_IDS"
        private const val KEY_NOTIFY_SCHEDULED = "NOTIFY_SCHEDULED_ENABLED"
        private const val KEY_NOTIFY_RECORDING_STARTED = "NOTIFY_RECORDING_STARTED_ENABLED"
        private const val KEY_NOTIFY_SYNC_SUCCESS = "NOTIFY_SYNC_SUCCESS_ENABLED"
        private const val KEY_MINUTES_BEFORE = "MINUTES_BEFORE"
        private const val KEY_MINUTES_AFTER = "MINUTES_AFTER"
        private const val KEY_SELECTED_BOUQUET_NAME = "SELECTED_BOUQUET_NAME"
        private const val KEY_BOUQUETS_JSON = "PREVIOUS_BOUQUETS_JSON"
        private const val KEY_SYNCED_CHANNELS_JSON = "SYNCED_CHANNELS"
        private const val KEY_MARKED_IDS = "MARKED_PROGRAM_IDS"
    }
}