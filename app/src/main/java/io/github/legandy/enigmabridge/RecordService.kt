package io.github.legandy.enigmabridge

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.tvbrowser.devplugin.Channel
import org.tvbrowser.devplugin.Plugin
import org.tvbrowser.devplugin.PluginManager
import org.tvbrowser.devplugin.PluginMenu
import org.tvbrowser.devplugin.Program
import org.tvbrowser.devplugin.ReceiveTarget

class RecordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val TAG = "EnigmaBridgeService"
        private const val MENU_ID_SIMPLE_SCHEDULE = 1
        private const val MENU_ID_ADVANCED_SCHEDULE = 2
        // Action for the broadcast to notify UI of changes.
        const val ACTION_TIMER_LIST_CHANGED = "io.github.legandy.enigmabridge.TIMER_LIST_CHANGED"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        NotificationHelper.createNotificationChannel(this)
    }

    // AIDL binder implementation for TV Browser plugin communication.
    private val binder: Plugin.Stub = object : Plugin.Stub() {
        // --- Plugin Metadata ---
        override fun getVersion(): String {
            return try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
            } catch (e: Exception) { "1.0" }
        }
        override fun getName(): String = getString(R.string.app_name)
        override fun getDescription(): String = getString(R.string.plugin_description)
        override fun getAuthor(): String = getString(R.string.plugin_author)
        override fun getLicense(): String = getString(R.string.plugin_license)
        override fun hasPreferences(): Boolean = true

        // Opens the MainActivity when preferences are selected in TV Browser.
        override fun openPreferences(subscribedChannels: MutableList<Channel>?) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        // Defines the context menu items to show in TV Browser.
        override fun getContextMenuActionsForProgram(program: Program?): Array<PluginMenu> {
            val simpleMenu = PluginMenu(MENU_ID_SIMPLE_SCHEDULE, getString(R.string.context_menu_schedule_simple))
            val advancedMenu = PluginMenu(MENU_ID_ADVANCED_SCHEDULE, getString(R.string.context_menu_schedule_advanced))
            return arrayOf(simpleMenu, advancedMenu)
        }

        // Handles the user's selection from the context menu.
        override fun onProgramContextMenuSelected(program: Program?, pluginMenu: PluginMenu?): Boolean {
            if (program != null && pluginMenu != null) {
                when (pluginMenu.id) {
                    MENU_ID_SIMPLE_SCHEDULE -> scheduleRecording(program, isAdvanced = false)
                    MENU_ID_ADVANCED_SCHEDULE -> scheduleRecording(program, isAdvanced = true)
                }
            }
            return false
        }

        // --- Unused Stubs ---
        override fun getMarkIcon(): ByteArray? = null
        override fun getMarkedPrograms(): LongArray = longArrayOf()
        override fun isMarked(programId: Long): Boolean = false
        override fun onActivation(pluginManager: PluginManager?) {}
        override fun onDeactivation() {}
        override fun handleFirstKnownProgramId(programId: Long) {}
        override fun getAvailableProgramReceiveTargets(): Array<ReceiveTarget>? = null
        override fun receivePrograms(programs: Array<Program>?, target: ReceiveTarget?) {}
    }

    // Core logic for scheduling a recording.
    private fun scheduleRecording(program: Program, isAdvanced: Boolean) {
        val syncedChannelsJson = prefs.getString("SYNCED_CHANNELS", null)
        if (syncedChannelsJson == null) {
            showToast(getString(R.string.error_channel_list_not_synced))
            return
        }

        val type = object : TypeToken<Map<String, String>>() {}.type
        val syncedChannels: Map<String, String> = Gson().fromJson(syncedChannelsJson, type)

        val tvBrowserChannelName = program.channel.channelName
        val sRef = findSrefForChannel(tvBrowserChannelName, syncedChannels)

        if (sRef.isNullOrEmpty()) {
            showToast(getString(R.string.error_channel_not_found, tvBrowserChannelName))
            return
        }

        if (isAdvanced) {
            // Launch the advanced scheduling screen for user edits.
            val intent = Intent(applicationContext, AdvancedScheduleActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("PROGRAM_EXTRA", program)
                putExtra("SREF_EXTRA", sRef)
            }
            startActivity(intent)
        } else {
            // Schedule immediately with default settings.
            showToast(getString(R.string.scheduling_toast, program.title))
            serviceScope.launch {
                val success = SchedulingHelper.scheduleTimer(
                    context = applicationContext,
                    title = program.title,
                    sRef = sRef,
                    startTimeMillis = program.startTimeInUTC,
                    endTimeMillis = program.endTimeInUTC,
                    description = program.shortDescription ?: "",
                    justPlay = 0,
                    repeated = 0,
                    afterEvent = 0
                )

                val message = if (success) getString(R.string.schedule_success) else getString(R.string.schedule_failed)
                showToast(message)

                if(success) {
                    // Check user preference before sending a notification.
                    if (prefs.getBoolean("NOTIFY_SCHEDULED_ENABLED", true)) {
                        NotificationHelper.sendSuccessNotification(applicationContext, program)
                    }
                    // Notify the UI to refresh.
                    sendRefreshBroadcast()
                }
            }
        }
    }

    // Sends a local broadcast to signal that the timer list has changed.
    private fun sendRefreshBroadcast() {
        val intent = Intent(ACTION_TIMER_LIST_CHANGED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
        Log.d(TAG, "Sent timer list changed broadcast.")
    }

    // Matches the TV Browser channel name to a channel in the synced bouquet.
    private fun findSrefForChannel(tvBrowserChannelName: String, syncedChannels: Map<String, String>): String? {
        // Exact match (case-insensitive)
        for ((syncedName, sRef) in syncedChannels) {
            if (syncedName.equals(tvBrowserChannelName, ignoreCase = true)) return sRef
        }
        // Partial match: synced name contains TV Browser name
        for ((syncedName, sRef) in syncedChannels) {
            if (syncedName.contains(tvBrowserChannelName, ignoreCase = true)) return sRef
        }
        // Partial match: TV Browser name contains synced name
        for ((syncedName, sRef) in syncedChannels) {
            if (tvBrowserChannelName.contains(syncedName, ignoreCase = true)) return sRef
        }
        return null
    }

    // Helper to show toasts on the main thread.
    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    // Binds the service to TV Browser.
    override fun onBind(intent: Intent): IBinder = binder

    // Cleans up coroutine scope when the service is destroyed.
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

