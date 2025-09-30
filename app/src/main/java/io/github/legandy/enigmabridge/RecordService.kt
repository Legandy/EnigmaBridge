package io.github.legandy.enigmabridge

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import android.widget.Toast
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
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        NotificationHelper.createNotificationChannel(this)
    }

    private val binder: Plugin.Stub = object : Plugin.Stub() {
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
        override fun openPreferences(subscribedChannels: MutableList<Channel>?) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }
        override fun getContextMenuActionsForProgram(program: Program?): Array<PluginMenu> {
            val simpleMenu = PluginMenu(MENU_ID_SIMPLE_SCHEDULE, getString(R.string.context_menu_schedule_simple))
            val advancedMenu = PluginMenu(MENU_ID_ADVANCED_SCHEDULE, getString(R.string.context_menu_schedule_advanced))
            return arrayOf(simpleMenu, advancedMenu)
        }

        override fun onProgramContextMenuSelected(program: Program?, pluginMenu: PluginMenu?): Boolean {
            if (program != null && pluginMenu != null) {
                when (pluginMenu.id) {
                    MENU_ID_SIMPLE_SCHEDULE -> scheduleRecording(program, isAdvanced = false)
                    MENU_ID_ADVANCED_SCHEDULE -> scheduleRecording(program, isAdvanced = true)
                }
            }
            return false
        }

        // Unused stubs
        override fun getMarkIcon(): ByteArray? = null
        override fun getMarkedPrograms(): LongArray = longArrayOf()
        override fun isMarked(programId: Long): Boolean = false
        override fun onActivation(pluginManager: PluginManager?) {}
        override fun onDeactivation() {}
        override fun handleFirstKnownProgramId(programId: Long) {}
        override fun getAvailableProgramReceiveTargets(): Array<ReceiveTarget>? = null
        override fun receivePrograms(programs: Array<Program>?, target: ReceiveTarget?) {}
    }

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
            val intent = Intent(applicationContext, AdvancedScheduleActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("PROGRAM_EXTRA", program)
                putExtra("SREF_EXTRA", sRef)
            }
            startActivity(intent)
        } else {
            showToast(getString(R.string.scheduling_toast, program.title))
            serviceScope.launch {
                // FIX: Pass the new 'repeated' and 'afterEvent' parameters with default values.
                val success = SchedulingHelper.scheduleTimer(
                    context = applicationContext,
                    title = program.title,
                    sRef = sRef,
                    startTimeMillis = program.startTimeInUTC,
                    endTimeMillis = program.endTimeInUTC,
                    repeated = 0, // 0 = Once
                    afterEvent = 0  // 0 = Do Nothing
                )

                val message = if (success) getString(R.string.schedule_success) else getString(R.string.schedule_failed)
                showToast(message)

                if(success) {
                    NotificationHelper.sendSuccessNotification(applicationContext, program)
                }
            }
        }
    }

    private fun findSrefForChannel(tvBrowserChannelName: String, syncedChannels: Map<String, String>): String? {
        for ((syncedName, sRef) in syncedChannels) {
            if (syncedName.equals(tvBrowserChannelName, ignoreCase = true)) return sRef
        }
        for ((syncedName, sRef) in syncedChannels) {
            if (syncedName.contains(tvBrowserChannelName, ignoreCase = true)) return sRef
        }
        for ((syncedName, sRef) in syncedChannels) {
            if (tvBrowserChannelName.contains(syncedName, ignoreCase = true)) return sRef
        }
        return null
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
