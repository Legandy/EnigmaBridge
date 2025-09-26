package io.github.legandy.enigmabridge

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.IBinder
import android.widget.Toast
import kotlinx.coroutines.*
import org.tvbrowser.devplugin.Plugin
import java.io.ByteArrayOutputStream

class RecordService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // A variable to hold a stable reference to our plugin's own, clean context.
    private var mPluginContext: Context? = null

    /**
     * onCreate() is guaranteed to be called before any client binds.
     * We create a dedicated, clean Package Context to ensure reliable resource access.
     */
    override fun onCreate() {
        super.onCreate()
        try {
            // This is the robust method for getting a stable context.
            mPluginContext = createPackageContext(packageName, CONTEXT_IGNORE_SECURITY)
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
    }

    private val binder = object : Plugin.Stub() {

        override fun getName(): String = mPluginContext?.getString(R.string.app_name) ?: "Enigma Bridge"

        override fun getVersionName(): String = try {
            mPluginContext?.packageManager?.getPackageInfo(mPluginContext!!.packageName, 0)?.versionName ?: "1.0"
        } catch (e: Exception) { "1.0" }

        override fun getDescription(): String = mPluginContext?.getString(R.string.plugin_description) ?: ""

        override fun getAuthor(): String = mPluginContext?.getString(R.string.plugin_author) ?: ""

        override fun getLicense(): String = "Apache License, Version 2.0"

        /**
         * THIS IS THE DEFINITIVE FIX (PART 1):
         * Per the robust plugin model, this method now returns null.
         * This signals TV-Browser to use the getIconAsByteArray() fallback.
         */
        override fun getIcon(): Bitmap? {
            return null
        }

        /**
         * THIS IS THE DEFINITIVE FIX (PART 2):
         * This is a direct copy of the robust icon handling logic from official plugins.
         * It uses the clean package context and the getIdentifier() method to reliably
         * load the icon and convert it to a safe byte array.
         */
        override fun getIconAsByteArray(): ByteArray {
            try {
                if (mPluginContext != null) {
                    // Manually look up the resource ID by its name and type.
                    val resourceId = mPluginContext!!.resources.getIdentifier("ic_plugin_icon", "drawable", mPluginContext!!.packageName)

                    if (resourceId != 0) {
                        val bitmap = BitmapFactory.decodeResource(mPluginContext!!.resources, resourceId)
                        val stream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                        return stream.toByteArray()
                    }
                }
            } catch (e: Exception) {
                // Safety net in case of any error.
            }
            // Return an empty byte array as the official plugins do.
            return ByteArray(0)
        }

        override fun getProgramContextMenuActions(programValues: Bundle?): Bundle {
            val actions = arrayOf(mPluginContext?.getString(R.string.record_menu_label) ?: "Record")
            val result = Bundle()
            result.putStringArray("ACTIONS", actions)
            return result
        }

        override fun onProgramContextMenuActionSelected(action: String?, programValues: Bundle?) {
            if (programValues != null) {
                handleRecording(programValues)
            }
        }

        // --- Added Stub Implementations to fix build error ---
        // We provide these empty implementations to satisfy the complete interface
        // and prevent any communication errors during the handshake.
        override fun getSettings(): IBinder? = null
        override fun getMarkingIcons(): IBinder? = null
        override fun onActivation(isActive: Boolean) { /* Do nothing */ }
        override fun onVersionUpdate(newVersionCode: Int): Boolean = true
    }

    private fun handleRecording(programData: Bundle) {
        val title = programData.getString("TITLE") ?: "Recording"
        val startTime = programData.getLong("START_TIME", 0) / 1000
        val endTime = programData.getLong("END_TIME", 0) / 1000
        val sRef = programData.getString("CHANNEL_ID_SERVICE_REFERENCE") ?: ""

        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty() || sRef.isEmpty()) {
            showToast("Error: IP Address or Channel Reference missing!")
            return
        }

        showToast("Scheduling '$title'...")

        serviceScope.launch {
            val client = EnigmaClient(ip, user, pass)
            val success = client.addTimer(title, sRef, startTime, endTime)
            val message = if (success) "Recording Scheduled!" else "Failed to Schedule Recording."
            showToast(message)
        }
    }

    private fun showToast(message: String) {
        CoroutineScope(Dispatchers.Main).launch {
            Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}

