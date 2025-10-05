package io.github.legandy.enigmabridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.legandy.enigmabridge.databinding.ActivityTimerListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerListActivity : AppCompatActivity(), TimerAdapter.OnTimerActionsListener {

    private lateinit var binding: ActivityTimerListBinding
    private lateinit var timerAdapter: TimerAdapter
    private lateinit var enigmaClient: EnigmaClient // Re-usable client instance

    companion object {
        const val EXTRA_TIMER = "TIMER_EXTRA"
    }

    // Receives broadcasts from RecordService to trigger a list refresh.
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == RecordService.ACTION_TIMER_LIST_CHANGED) {
                fetchTimerList()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // Initialize the client once with saved settings.
        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""
        enigmaClient = EnigmaClient(ip, user, pass, prefs)

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
        fetchTimerList()
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver)
    }

    private fun setupRecyclerView() {
        // Pass 'this' as the listener to the adapter.
        timerAdapter = TimerAdapter(mutableListOf(), this)
        binding.timersRecyclerView.apply {
            adapter = timerAdapter
            layoutManager = LinearLayoutManager(this@TimerListActivity)
        }
    }

    private fun setupPullToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            fetchTimerList()
        }
    }

    private fun fetchTimerList() {
        binding.swipeRefreshLayout.isRefreshing = true
        binding.emptyView.visibility = View.GONE

        val ip = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE).getString("IP_ADDRESS", "")
        if (ip.isNullOrEmpty()) {
            Toast.makeText(this, getString(R.string.error_ip_not_configured), Toast.LENGTH_LONG).show()
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val timers = enigmaClient.getTimerList()
            withContext(Dispatchers.Main) {
                binding.swipeRefreshLayout.isRefreshing = false
                if (timers != null) {
                    if (timers.isEmpty()) {
                        binding.emptyView.visibility = View.VISIBLE
                        binding.timersRecyclerView.visibility = View.GONE
                    } else {
                        binding.emptyView.visibility = View.GONE
                        binding.timersRecyclerView.visibility = View.VISIBLE
                        timerAdapter.updateTimers(timers)
                    }
                } else {
                    Toast.makeText(this@TimerListActivity, getString(R.string.error_fetch_timers), Toast.LENGTH_LONG).show()
                    binding.emptyView.visibility = View.VISIBLE
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    /**
     * Handles the 'Edit' click from the adapter.
     */
    override fun onEditClicked(timer: Timer) {
        val intent = Intent(this, EditTimerActivity::class.java)
        intent.putExtra(EXTRA_TIMER, timer)
        startActivity(intent)
    }

    /**
     * Handles the 'Delete' click from the adapter.
     */
    override fun onDeleteClicked(timer: Timer) {
        showDeleteConfirmationDialog(timer)
    }

    /**
     * Displays an AlertDialog to confirm the deletion.
     */
    private fun showDeleteConfirmationDialog(timer: Timer) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_timer_title))
            .setMessage(getString(R.string.delete_timer_message, timer.name))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                deleteTimer(timer) // User confirmed, proceed with deletion
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    /**
     * Calls the EnigmaClient to delete the timer and refreshes the list on success.
     */
    private fun deleteTimer(timer: Timer) {
        lifecycleScope.launch(Dispatchers.IO) {
            val result = enigmaClient.deleteTimer(timer)
            withContext(Dispatchers.Main) {
                if (result.first) {
                    Toast.makeText(applicationContext, getString(R.string.delete_timer_success), Toast.LENGTH_SHORT).show()
                    fetchTimerList() // Refresh the list after successful deletion
                } else {
                    Toast.makeText(applicationContext, result.second, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
