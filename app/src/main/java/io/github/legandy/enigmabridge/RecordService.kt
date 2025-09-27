package io.github.legandy.enigmabridge

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tvbrowser.devplugin.Channel
import org.tvbrowser.devplugin.Plugin
import org.tvbrowser.devplugin.PluginManager
import org.tvbrowser.devplugin.PluginMenu
import org.tvbrowser.devplugin.Program
import org.tvbrowser.devplugin.ReceiveTarget
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var prefs: SharedPreferences

    companion object {
        private const val TAG = "EnigmaBridgeService"
        private const val MENU_ID_SCHEDULE_RECORDING = 1
        private const val NOTIFICATION_CHANNEL_ID = "ENIGMA_BRIDGE_CHANNEL"
    }

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        createNotificationChannel()
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
            val scheduleMenu = PluginMenu(MENU_ID_SCHEDULE_RECORDING, getString(R.string.context_menu_schedule))
            return arrayOf(scheduleMenu)
        }

        override fun onProgramContextMenuSelected(program: Program?, pluginMenu: PluginMenu?): Boolean {
            if (program != null && pluginMenu?.id == MENU_ID_SCHEDULE_RECORDING) {
                Log.d(TAG, "Menu item selected for program: ${program.title}")
                scheduleRecording(program)
            }
            return false
        }

        override fun getMarkIcon(): ByteArray? = null
        override fun getMarkedPrograms(): LongArray = longArrayOf()
        override fun isMarked(programId: Long): Boolean = false
        override fun onActivation(pluginManager: PluginManager?) {}
        override fun onDeactivation() {}
        override fun handleFirstKnownProgramId(programId: Long) {}
        override fun getAvailableProgramReceiveTargets(): Array<ReceiveTarget>? = null
        override fun receivePrograms(programs: Array<Program>?, target: ReceiveTarget?) {}
    }

    private fun scheduleRecording(program: Program) {
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
            Log.e(TAG, "Could not find sRef for TV Browser channel: $tvBrowserChannelName")
            return
        }

        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) {
            showToast(getString(R.string.error_ip_not_set))
            return
        }

        val title = program.title
        val startTime = program.startTimeInUTC / 1000
        val endTime = program.endTimeInUTC / 1000

        showToast(getString(R.string.scheduling_toast, title))
        Log.d(TAG, "Scheduling timer: Title='$title', sRef='$sRef', Start='$startTime', End='$endTime'")

        serviceScope.launch {
            val client = EnigmaClient(ip, user, pass)
            val success = client.addTimer(title, sRef, startTime, endTime)
            val message = if (success) getString(R.string.schedule_success) else getString(R.string.schedule_failed)
            showToast(message)

            if(success) {
                sendSuccessNotification(program)
            }
        }
    }

    private fun findSrefForChannel(tvBrowserChannelName: String, syncedChannels: Map<String, String>): String? {
        Log.d(TAG, "Attempting to match TV Browser channel: '$tvBrowserChannelName'")
        for ((syncedName, sRef) in syncedChannels) {
            if (syncedName.equals(tvBrowserChannelName, ignoreCase = true)) {
                Log.d(TAG, "Found perfect match: '$syncedName' -> $sRef")
                return sRef
            }
        }
        for ((syncedName, sRef) in syncedChannels) {
            if (syncedName.contains(tvBrowserChannelName, ignoreCase = true)) {
                Log.d(TAG, "Found 'contains' match: '$syncedName' contains '$tvBrowserChannelName'")
                return sRef
            }
        }
        for ((syncedName, sRef) in syncedChannels) {
            if (tvBrowserChannelName.contains(syncedName, ignoreCase = true)) {
                Log.d(TAG, "Found fallback 'contains' match: '$tvBrowserChannelName' contains '$syncedName'")
                return sRef
            }
        }
        return null
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.notification_channel_name)
            val descriptionText = getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun sendSuccessNotification(program: Program) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startTime = timeFormat.format(Date(program.startTimeInUTC))
        val channelName = program.channel.channelName
        val title = program.title

        val notificationContent = getString(R.string.notification_content, title, channelName, startTime)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check_circle)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(notificationContent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS permission not granted. Cannot show notification.")
            }
        } else {
            NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

