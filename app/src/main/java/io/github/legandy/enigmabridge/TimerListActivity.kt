package io.github.legandy.enigmabridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.legandy.enigmabridge.databinding.ActivityTimerListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTimerListBinding
    private lateinit var timerAdapter: TimerAdapter
    companion object {
        // Key for passing timer data via Intent extras.
        const val EXTRA_TIMER = "TIMER_EXTRA"
    }

    // Receives broadcasts from RecordService to trigger a list refresh.
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RecordService.ACTION_TIMER_LIST_CHANGED) {
                // On receive, refresh the timer list.
                fetchTimerList()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup toolbar with back arrow.
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupRecyclerView()
        setupPullToRefresh()

        // Register receiver to listen for timer changes.
        LocalBroadcastManager.getInstance(this).registerReceiver(
            refreshReceiver,
            IntentFilter(RecordService.ACTION_TIMER_LIST_CHANGED)
        )
    }

    override fun onResume() {
        super.onResume()
        // Refresh list every time the activity resumes to ensure data is fresh.
        fetchTimerList()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister receiver on destroy to prevent memory leaks.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver)
    }

    // Initializes the RecyclerView and its adapter.
    private fun setupRecyclerView() {
        timerAdapter = TimerAdapter(mutableListOf())
        binding.timersRecyclerView.apply {
            adapter = timerAdapter
            layoutManager = LinearLayoutManager(this@TimerListActivity)
        }
    }

    // Configures the pull-to-refresh listener.
    private fun setupPullToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchTimerList()
        }
    }

    // Fetches the timer list from the Enigma2 receiver.
    private fun fetchTimerList() {
        // Show loading indicator.
        binding.swipeRefreshLayout.isRefreshing = true
        binding.emptyView.visibility = View.GONE

        // Get saved connection settings.
        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        // Abort if IP address is not configured.
        if (ip.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_ip_not_configured), Toast.LENGTH_LONG).show()
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        // Perform network call on IO dispatcher.
        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass, prefs)
            val timers = client.getTimerList()

            // Switch to Main dispatcher to update UI.
            withContext(Dispatchers.Main) {
                binding.swipeRefreshLayout.isRefreshing = false
                if (timers != null) {
                    // Handle empty list state.
                    if (timers.isEmpty()) {
                        binding.emptyView.visibility = View.VISIBLE
                        binding.timersRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyView.visibility = View.GONE
                        binding.timersRecyclerView.visibility = View.VISIBLE
                        timerAdapter.updateTimers(timers)
                    }
                } else {
                    // Handle fetch failure.
                    Toast.makeText(this@TimerListActivity, getString(R.string.error_fetch_timers), Toast.LENGTH_LONG).show()
                    binding.emptyView.visibility = View.VISIBLE
                }
            }
        }
    }

    // Handles the toolbar back arrow press.
    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

