package io.github.legandy.enigmabridge

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.tvbrowser.devplugin.Channel
import org.tvbrowser.devplugin.Plugin
import org.tvbrowser.devplugin.PluginManager
import org.tvbrowser.devplugin.PluginMenu
import org.tvbrowser.devplugin.Program
import org.tvbrowser.devplugin.ReceiveTarget
import kotlin.math.abs

class RecordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    private var mPluginManager: PluginManager? = null
    private var cachedTimers: List<Timer> = emptyList()

    private val markedProgramIds = mutableSetOf<String>()

    companion object {
        // **DIAGNOSTIC TAG**
        private const val TAG = "ENIGMA_DIAGNOSTIC" // Changed for easy filtering
        private const val MENU_ID_SIMPLE_SCHEDULE = 1
        private const val MENU_ID_ADVANCED_SCHEDULE = 2
        private const val MENU_ID_UNSCHEDULE = 3
        const val ACTION_TIMER_LIST_CHANGED = "io.github.legandy.enigmabridge.TIMER_LIST_CHANGED"
        const val ACTION_REVERT_MARKING = "io.github.legandy.enigmabridge.ACTION_REVERT_MARKING"
        private const val PREF_MARKED_IDS = "MARKED_PROGRAM_IDS"
    }

    private val revertMarkReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REVERT_MARKING) {
                @Suppress("DEPRECATION")
                val programToUnmark: Program? = intent.getParcelableExtra("PROGRAM_EXTRA")
                if (programToUnmark != null) {
                    revertMarking(programToUnmark)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        NotificationHelper.createNotificationChannel(this)
        loadMarkedProgramIds()
        updateTimerCache()
        LocalBroadcastManager.getInstance(this).registerReceiver(revertMarkReceiver, IntentFilter(ACTION_REVERT_MARKING))
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(revertMarkReceiver)
    }

    private val binder: Plugin.Stub = object : Plugin.Stub() {
        override fun getVersion(): String { return try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (e: Exception) { "1.0" } }
        override fun getName(): String = getString(R.string.app_name)
        override fun getDescription(): String = getString(R.string.plugin_description)
        override fun getAuthor(): String = getString(R.string.plugin_author)
        override fun getLicense(): String = getString(R.string.plugin_license)
        override fun hasPreferences(): Boolean = true
        override fun openPreferences(subscribedChannels: MutableList<Channel>?) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            startActivity(intent)
        }
        override fun onActivation(pluginManager: PluginManager?) { mPluginManager = pluginManager; updateTimerCache() }
        override fun getMarkIcon(): ByteArray? = null
        override fun onDeactivation() { mPluginManager = null }
        override fun handleFirstKnownProgramId(programId: Long) {}
        override fun getAvailableProgramReceiveTargets(): Array<ReceiveTarget>? = null
        override fun receivePrograms(programs: Array<Program>?, target: ReceiveTarget?) {}


        override fun getContextMenuActionsForProgram(program: Program?): Array<PluginMenu> {
            if (program != null) {
                return if (markedProgramIds.contains(program.id.toString())) {
                    arrayOf(PluginMenu(MENU_ID_UNSCHEDULE, "Un-schedule Recording"))
                } else {
                    arrayOf(
                        PluginMenu(MENU_ID_SIMPLE_SCHEDULE, getString(R.string.context_menu_schedule_simple)),
                        PluginMenu(MENU_ID_ADVANCED_SCHEDULE, getString(R.string.context_menu_schedule_advanced))
                    )
                }
            }
            return emptyArray()
        }

        override fun onProgramContextMenuSelected(program: Program?, pluginMenu: PluginMenu?): Boolean {
            if (program != null && pluginMenu != null) {
                when (pluginMenu.id) {
                    MENU_ID_SIMPLE_SCHEDULE, MENU_ID_ADVANCED_SCHEDULE -> {
                        markedProgramIds.add(program.id.toString())
                        saveMarkedProgramIds()
                        scheduleRecording(program, isAdvanced = (pluginMenu.id == MENU_ID_ADVANCED_SCHEDULE))
                        return true
                    }
                    MENU_ID_UNSCHEDULE -> {
                        unscheduleRecording(program)
                        return true
                    }
                }
            }
            return false
        }

        override fun isMarked(programId: Long): Boolean = markedProgramIds.contains(programId.toString())
        override fun getMarkedPrograms(): LongArray = markedProgramIds.mapNotNull { it.toLongOrNull() }.toLongArray()
    }

    private fun scheduleRecording(program: Program, isAdvanced: Boolean) {
        // **DIAGNOSTIC LOGGING START**
        Log.d(TAG, "------------------- SCHEDULE RECORDING START -------------------")
        Log.d(TAG, "Attempting to schedule program: '${program.title}'")
        val tvBrowserChannelName = program.channel.channelName
        Log.d(TAG, "Looking for TV-Browser channel name: '$tvBrowserChannelName'")

        val syncedChannels = getSyncedChannels()
        if (syncedChannels == null) {
            Log.e(TAG, "CRITICAL FAILURE: Synced channel list is NULL. Cannot proceed.")
            showToast(getString(R.string.error_channel_list_not_synced))
            revertMarking(program)
            return
        }

        Log.d(TAG, "Loaded ${syncedChannels.size} synced channels from storage.")
        // Log the entire map for detailed analysis
        syncedChannels.forEach { (name, sRef) ->
            Log.d(TAG, "  -> Synced Channel: NAME='$name', SREF='$sRef'")
        }
        // **DIAGNOSTIC LOGGING END**

        val sRef = findSrefForChannel(tvBrowserChannelName, syncedChannels)
        if (sRef == null) {
            Log.e(TAG, "CRITICAL FAILURE: sRef lookup failed for channel '$tvBrowserChannelName'.")
            showToast(getString(R.string.error_channel_not_found, tvBrowserChannelName))
            revertMarking(program)
            Log.d(TAG, "------------------- SCHEDULE RECORDING END ---------------------")
            return
        }

        Log.d(TAG, "SUCCESS: Found matching sRef: '$sRef'")
        Log.d(TAG, "------------------- SCHEDULE RECORDING END ---------------------")


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
                val result = SchedulingHelper.scheduleTimer(
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
                if (result.first) {
                    if (prefs.getBoolean("NOTIFY_SCHEDULED_ENABLED", true)) {
                        NotificationHelper.sendSuccessNotification(applicationContext, program)
                    }
                    sendRefreshBroadcast()
                    updateTimerCache()
                } else {
                    revertMarking(program)
                }
                showToast(result.second)
            }
        }
    }

    private fun unscheduleRecording(program: Program) {
        val timerToDelete = findTimerForProgram(program)
        if (timerToDelete == null) {
            showToast("Could not find matching timer to delete.")
            revertMarking(program)
            return
        }

        serviceScope.launch {
            val client = getEnigmaClient() ?: return@launch
            val result = client.deleteTimer(timerToDelete)
            if (result.first) {
                revertMarking(program)
                updateTimerCache()
                sendRefreshBroadcast()
            }
            showToast(result.second)
        }
    }

    private fun revertMarking(program: Program) {
        markedProgramIds.remove(program.id.toString())
        saveMarkedProgramIds()
        mPluginManager?.unmarkProgram(program)
    }

    private fun loadMarkedProgramIds() {
        val idString = prefs.getString(PREF_MARKED_IDS, null)
        if (!idString.isNullOrEmpty()) { markedProgramIds.clear(); markedProgramIds.addAll(idString.split(',')) }
    }
    private fun saveMarkedProgramIds() { prefs.edit().putString(PREF_MARKED_IDS, markedProgramIds.joinToString(",")).apply() }

    private fun findTimerForProgram(program: Program): Timer? {
        return cachedTimers.find { timer ->
            val titleMatch = timer.name.equals(program.title, ignoreCase = true)
            val timeDifference = abs(program.startTimeInUTC - (timer.beginTimestamp * 1000))
            val timeMatch = timeDifference < (5 * 60 * 1000)
            titleMatch && timeMatch
        }
    }

    private fun updateTimerCache() {
        serviceScope.launch(Dispatchers.IO) {
            val client = getEnigmaClient() ?: return@launch
            val timers = client.getTimerList()
            if (timers != null) { cachedTimers = timers }
        }
    }

    private fun getEnigmaClient(): EnigmaClient? {
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        return if (ip.isNotEmpty()) { EnigmaClient(ip, prefs.getString("USERNAME", "root") ?: "", prefs.getString("PASSWORD", "") ?: "", prefs) } else { null }
    }

    private fun getSyncedChannels(): Map<String, String>? {
        val jsonString = prefs.getString("SYNCED_CHANNELS", null)
        return if (jsonString != null) {
            try {
                Json.decodeFromString<Map<String, String>>(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse synced channels JSON.", e)
                null
            }
        } else {
            null
        }
    }

    private fun findSrefForChannel(tvBrowserChannelName: String, syncedChannels: Map<String, String>): String? {
        // **DIAGNOSTIC LOGGING**
        Log.d(TAG, "Starting sRef search for: '$tvBrowserChannelName'")

        // Exact match (case-insensitive)
        syncedChannels.forEach { (syncedName, sRef) ->
            if (syncedName.equals(tvBrowserChannelName, ignoreCase = true)) {
                Log.d(TAG, "Found exact match: '$syncedName' -> '$sRef'")
                return sRef
            }
        }

        // Synced name contains TV Browser name
        syncedChannels.forEach { (syncedName, sRef) ->
            if (syncedName.contains(tvBrowserChannelName, ignoreCase = true)) {
                Log.d(TAG, "Found partial match (synced contains tvb): '$syncedName' -> '$sRef'")
                return sRef
            }
        }

        // TV Browser name contains synced name
        syncedChannels.forEach { (syncedName, sRef) ->
            if (tvBrowserChannelName.contains(syncedName, ignoreCase = true)) {
                Log.d(TAG, "Found partial match (tvb contains synced): '$syncedName' -> '$sRef'")
                return sRef
            }
        }

        Log.w(TAG, "No match found after all checks.")
        return null
    }


    private fun showToast(message: String) { CoroutineScope(Dispatchers.Main).launch { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() } }

    private fun sendRefreshBroadcast() {
        val intent = Intent(ACTION_TIMER_LIST_CHANGED)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onBind(intent: Intent): IBinder = binder
}

