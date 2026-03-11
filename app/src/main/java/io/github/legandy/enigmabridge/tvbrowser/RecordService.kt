package io.github.legandy.enigmabridge.tvbrowser

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.core.AppEvent
import io.github.legandy.enigmabridge.core.AppEventBus
import io.github.legandy.enigmabridge.core.EnigmaBridgeApplication
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.data.TimerRepository
import io.github.legandy.enigmabridge.data.TimerResult
import io.github.legandy.enigmabridge.helpers.NotificationHelper
import io.github.legandy.enigmabridge.helpers.SchedulingHelper
import io.github.legandy.enigmabridge.main.MainActivity
import io.github.legandy.enigmabridge.receiversettings.Timer
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

    private lateinit var prefManager: PreferenceManager
    private lateinit var repository: TimerRepository

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var mPluginManager: PluginManager? = null
    private var cachedTimers: List<Timer> = emptyList()

    private val markedProgramIds = mutableSetOf<String>()

    companion object {
        private const val TAG = "RecordService"
        private const val MENU_ID_SIMPLE_SCHEDULE = 1
        private const val MENU_ID_ADVANCED_SCHEDULE = 2
        private const val MENU_ID_UNSCHEDULE = 3
        private const val MENU_ID_MANUAL_UNMARK = 4
        private const val TIMER_MATCH_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes buffer
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as EnigmaBridgeApplication
        prefManager = app.prefManager
        repository = app.timerRepository
        
        NotificationHelper.createNotificationChannel(this)

        markedProgramIds.clear()
        markedProgramIds.addAll(prefManager.getMarkedProgramIds())

        updateTimerCache()

        // Listen for internal events (e.g., from AdvancedScheduleActivity)
        serviceScope.launch {
            AppEventBus.events.collect { event ->
                if (event is AppEvent.RevertMarking) {
                    Log.d(TAG, "Received signal to revert mark for program: ${event.program.title}")
                    revertMarking(event.program)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private val binder: Plugin.Stub = object : Plugin.Stub() {
        override fun getVersion(): String { return try { packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0" } catch (_: Exception) { "1.0" } }
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
                return arrayOf(
                    PluginMenu(MENU_ID_SIMPLE_SCHEDULE, getString(R.string.context_menu_schedule_simple)),
                    PluginMenu(MENU_ID_ADVANCED_SCHEDULE, getString(R.string.context_menu_schedule_advanced)),
                    PluginMenu(MENU_ID_UNSCHEDULE, getString(R.string.context_menu_unschedule)),
                    PluginMenu(MENU_ID_MANUAL_UNMARK, getString(R.string.context_menu_manual_unmark))
                )
            }
            return emptyArray()
        }

        override fun onProgramContextMenuSelected(program: Program?, pluginMenu: PluginMenu?): Boolean {
            if (program != null && pluginMenu != null) {
                when (pluginMenu.id) {
                    MENU_ID_SIMPLE_SCHEDULE, MENU_ID_ADVANCED_SCHEDULE -> {
                        markedProgramIds.add(program.id.toString())
                        prefManager.setMarkedProgramIds(markedProgramIds)
                        scheduleRecording(program, isAdvanced = (pluginMenu.id == MENU_ID_ADVANCED_SCHEDULE))
                        return true

                    }
                    MENU_ID_UNSCHEDULE -> {
                        unscheduleRecording(program)
                        return false
                    }
                    MENU_ID_MANUAL_UNMARK -> {
                        revertMarking(program)
                        return false
                    }
                }

            }
            return false
        }

        override fun isMarked(programId: Long): Boolean = markedProgramIds.contains(programId.toString())
        override fun getMarkedPrograms(): LongArray = markedProgramIds.mapNotNull { it.toLongOrNull() }.toLongArray()
    }

    private fun scheduleRecording(program: Program, isAdvanced: Boolean) {
        val channelsJson = prefManager.getSyncedChannelsJson()
        val syncedChannels: Map<String, String>? = channelsJson?.let {
            try {
                json.decodeFromString<Map<String, String>>(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode channels", e)
                null
            }
        }

        if (syncedChannels == null) {
            showToast(getString(R.string.error_channel_list_not_synced))
            revertMarking(program)
            return
        }

        val sRef = findSrefForChannel(program.channel.channelName, syncedChannels)
        if (sRef == null) {
            showToast(getString(R.string.error_channel_not_found, program.channel.channelName))
            revertMarking(program)
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
                val result = SchedulingHelper.scheduleTimer(
                    context = applicationContext,
                    prefManager = prefManager,
                    repository = repository,
                    title = program.title,
                    sRef = sRef,
                    startTimeMillis = program.startTimeInUTC,
                    endTimeMillis = program.endTimeInUTC,
                    description = program.shortDescription ?: "",
                    justPlay = 0,
                    repeated = 0,
                    afterEvent = 0
                )
                
                when (result) {
                    is TimerResult.Success -> {
                        if (prefManager.isNotifyScheduledEnabled()) {
                            NotificationHelper.sendSuccessNotification(applicationContext, program)
                        }
                        AppEventBus.emit(AppEvent.TimerListChanged)
                        updateTimerCache()
                        showToast(result.data.second)
                    }
                    is TimerResult.Error -> {
                        revertMarking(program)
                        showToast(result.message)
                    }
                }
            }
        }
    }

    private fun unscheduleRecording(program: Program) {
        val timerToZap = findTimerForProgram(program)
        if (timerToZap == null) {
            showToast(getString(R.string.timer_not_found))
            revertMarking(program)
            return
        }

        serviceScope.launch {
            when (val result = repository.deleteTimer(timerToZap)) {
                is TimerResult.Success -> {
                    showToast(result.data.second)
                    revertMarking(program)
                    updateTimerCache()
                    AppEventBus.emit(AppEvent.TimerListChanged)
                }
                is TimerResult.Error -> {
                    showToast(result.message)
                    revertMarking(program)
                }
            }
        }
    }

    private fun revertMarking(program: Program) {
        markedProgramIds.remove(program.id.toString())
        prefManager.setMarkedProgramIds(markedProgramIds)
        mPluginManager?.unmarkProgram(program)
    }

    private fun findTimerForProgram(program: Program): Timer? {
        return cachedTimers.find { timer ->
            val titleMatch = timer.name.equals(program.title, ignoreCase = true)
            val timeDifference = abs(program.startTimeInUTC - (timer.beginTimestamp * 1000))
            val timeMatch = timeDifference < TIMER_MATCH_THRESHOLD_MS
            titleMatch && timeMatch
        }
    }

    private fun updateTimerCache() {
        serviceScope.launch {
            val result = repository.getTimers()
            if (result is TimerResult.Success) {
                cachedTimers = result.data
                Log.d(TAG, "Timer cache updated with ${cachedTimers.size} timers.")
            }
        }
    }

    private fun findSrefForChannel(tvBrowserChannelName: String, syncedChannels: Map<String, String>): String? {
        syncedChannels.forEach { (syncedName, sRef) -> if (syncedName.equals(tvBrowserChannelName, ignoreCase = true)) return sRef }
        syncedChannels.forEach { (syncedName, sRef) -> if (syncedName.contains(tvBrowserChannelName, ignoreCase = true)) return sRef }
        syncedChannels.forEach { (syncedName, sRef) -> if (tvBrowserChannelName.contains(syncedName, ignoreCase = true)) return sRef }
        return null
    }

    private fun showToast(message: String) { CoroutineScope(Dispatchers.Main).launch { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() } }

    override fun onBind(intent: Intent): IBinder = binder
}
