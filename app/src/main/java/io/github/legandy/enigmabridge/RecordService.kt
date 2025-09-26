package io.github.legandy.enigmabridge

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import android.widget.Toast
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

/**
 * The corrected plugin Service that implements the modern TV Browser AIDL interface.
 */
class RecordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val TAG = "EnigmaBridgePlugin"
        private const val MENU_ID_SCHEDULE_RECORDING = 1
    }

    /**
     * This is the implementation of the modern AIDL Plugin.Stub interface.
     * It correctly handles Program objects instead of raw Bundles.
     */
    private val binder: Plugin.Stub = object : Plugin.Stub() {

        override fun getVersion(): String {
            return try {
                packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0"
            } catch (e: Exception) {
                Log.e(TAG, "Could not get package info.", e)
                "1.0"
            }
        }

        override fun getName(): String = "EnigmaBridge"
        override fun getDescription(): String = "Schedules recordings on an Enigma2 receiver."
        override fun getAuthor(): String = "Legandy"
        override fun getLicense(): String = "Apache License, Version 2.0"

        override fun getContextMenuActionsForProgram(program: Program): Array<PluginMenu> {
            val scheduleMenu = PluginMenu(MENU_ID_SCHEDULE_RECORDING, "Schedule with Enigma2")
            return arrayOf(scheduleMenu)
        }

        override fun onProgramContextMenuSelected(program: Program, pluginMenu: PluginMenu): Boolean {
            if (pluginMenu.id == MENU_ID_SCHEDULE_RECORDING) {
                Log.d(TAG, "Menu item selected for program: ${program.title}")
                scheduleRecording(program)
            }
            return false // We are not "marking" the program
        }

        override fun hasPreferences(): Boolean = true

        override fun openPreferences(subscribedChannels: MutableList<Channel>?) {
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        }

        // --- STUB METHODS (not needed for this plugin's core function) ---
        override fun getMarkIcon(): ByteArray? = null
        override fun getMarkedPrograms(): LongArray = longArrayOf()
        override fun isMarked(programId: Long): Boolean = false
        override fun onActivation(pluginManager: PluginManager?) {}
        override fun onDeactivation() {}
        override fun handleFirstKnownProgramId(programId: Long) {}
        override fun getAvailableProgramReceiveTargets(): Array<ReceiveTarget>? = null
        override fun receivePrograms(programs: Array<Program>?, target: ReceiveTarget?) {}
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "TV Browser bound to RecordService.")
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun scheduleRecording(program: Program) {
        serviceScope.launch {
            val prefs: SharedPreferences = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
            val ip = prefs.getString("IP_ADDRESS", "") ?: ""
            val user = prefs.getString("USERNAME", "root") ?: ""
            val pass = prefs.getString("PASSWORD", "") ?: ""

            // TODO: CRITICAL - You must implement a way to map the TV Browser channel to an Enigma2 sRef.
            // This is a placeholder and will need to be customized for your setup.
            val sRef = getServiceReferenceForChannel(program.channel.channelName)

            if (ip.isBlank() || sRef.isBlank()) {
                showToast("Error: IP Address or Channel Reference missing!")
                return@launch
            }

            showToast("Scheduling '${program.title}'...")
            val client = EnigmaClient(ip, user, pass)
            val success = client.addTimer(program.title, sRef, program.startTimeInUTC / 1000, program.endTimeInUTC / 1000)
            val message = if (success) "Recording Scheduled!" else "Failed to Schedule Recording."
            showToast(message)
        }
    }

    // TODO: This is where you must add your logic to map channel names to sRefs.
    private fun getServiceReferenceForChannel(channelName: String): String {
        return when {
            channelName.contains("Das Erste HD", ignoreCase = true) -> "1:0:19:283D:3FB:1:C00000:0:0:0:"
            channelName.contains("ZDF HD", ignoreCase = true) -> "1:0:19:2B66:3F3:1:C00000:0:0:0:"
            // Add more of your channels here
            else -> "" // Return empty if no match, so the error can be caught
        }
    }

    private suspend fun showToast(message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }
}

