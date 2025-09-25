package io.github.legandy.enigmabridge


import androidx.appcompat.app.AppCompatActivity
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The base class is now AppCompatActivity
class RecordActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val receivedIntent = intent

        if (receivedIntent?.action == "de.heavencall.tvbrowser.ACTION_SEND_PROGRAM") {
            // Get program data from TV-Browser
            val title = receivedIntent.getStringExtra("TITLE") ?: "Recording"
            val startTime = receivedIntent.getLongExtra("START_TIME", 0) / 1000
            val endTime = receivedIntent.getLongExtra("END_TIME", 0) / 1000
            // This is the key for the channel reference in TV-Browser's data
            val sRef = receivedIntent.getStringExtra("CHANNEL_ID_SERVICE_REFERENCE") ?: ""

            // Load saved settings
            val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
            val ip = prefs.getString("IP_ADDRESS", "") ?: ""
            val user = prefs.getString("USERNAME", "root") ?: ""
            val pass = prefs.getString("PASSWORD", "") ?: ""

            if (ip.isEmpty()) {
                Toast.makeText(this, "Error: IP Address not configured!", Toast.LENGTH_LONG).show()
                finish()
                return
            }

            if (sRef.isEmpty()) {
                // Let the user know if the vital channel data is missing
                Toast.makeText(this, "Error: Channel Reference (sRef) not provided by TV-Browser for this channel.", Toast.LENGTH_LONG).show()
                finish()
                return
            }


            Toast.makeText(this, "Scheduling '$title'...", Toast.LENGTH_SHORT).show()

            // Perform network operation on a background thread
            lifecycleScope.launch(Dispatchers.IO) {
                val client = EnigmaClient(ip, user, pass)
                val success = client.addTimer(title, sRef, startTime, endTime)

                // Show result on the main UI thread
                withContext(Dispatchers.Main) {
                    val message = if (success) "Recording Scheduled!" else "Failed to Schedule Recording."
                    Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        } else {
            // If the activity is started with the wrong action, just close it.
            finish()
        }
    }
}