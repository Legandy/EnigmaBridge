package io.github.legandy.enigmabridge.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.receiversettings.Timer
import io.github.legandy.enigmabridge.databinding.ActivityTimerListBinding
import io.github.legandy.enigmabridge.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import io.github.legandy.enigmabridge.core.AppThemeManager // Import AppThemeManager
import io.github.legandy.enigmabridge.notifications.TimerCheckWorker

class TimerListActivity : AppCompatActivity(), TimerAdapter.OnTimerActionsListener {

    private lateinit var binding: ActivityTimerListBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var timerAdapter: TimerAdapter // Declare timerAdapter
    // enigmaClient is no longer directly used in TimerListActivity for fetching

    companion object {
        const val EXTRA_TIMER = "TIMER_EXTRA"
        private const val TAG = "TimerListActivity"
        private const val PREVIOUS_TIMERS_KEY = "PREVIOUS_TIMERS_LIST" // from TimerCheckWorker
    }

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                MainActivity.ACTION_TIMER_SYNC_COMPLETED -> {
                    Log.d(TAG, "Received broadcast (${intent.action}), loading timers from preferences.")
                    loadTimersFromPreferences()
                    binding.swipeRefreshLayout.isRefreshing = false // Hide indicator
                }
                TimerCheckWorker.ACTION_WORKER_COMPLETED -> {
                    Log.d(TAG, "Received broadcast (${intent.action}), hiding refresh indicator.")
                    binding.swipeRefreshLayout.isRefreshing = false // Hide indicator for any worker completion
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppThemeManager.applyThemeAndAccentColor(this) // Apply theme here
        super.onCreate(savedInstanceState)
        binding = ActivityTimerListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefs = getSharedPreferences(AppThemeManager.PREFS_NAME, MODE_PRIVATE) // Use AppThemeManager.PREFS_NAME
        // enigmaClient initialization removed as it's not directly used here for fetching

        setupRecyclerView()
        setupPullToRefresh()
        loadTimersFromPreferences() // Initial load from preferences (last synced timers)

        val intentFilter = IntentFilter().apply {
            addAction(MainActivity.ACTION_TIMER_SYNC_COMPLETED)
            addAction(TimerCheckWorker.ACTION_WORKER_COMPLETED) // Listen for general worker completion
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(refreshReceiver, intentFilter)
    }

    override fun onResume() {
        super.onResume()
        // Always trigger a sync when returning to this activity
        Log.d(TAG, "onResume() triggered. Enqueuing TimerCheckWorker for SILENT sync.")
        binding.swipeRefreshLayout.isRefreshing = true
        val syncWorkRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>()
            .setInputData(workDataOf(TimerCheckWorker.INPUT_DATA_KEY_SILENT_SYNC to true))
            .build()
        WorkManager.getInstance(this).enqueue(syncWorkRequest)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(refreshReceiver)
    }

    private fun setupRecyclerView() {
        timerAdapter = TimerAdapter(mutableListOf(), this)
        binding.timersRecyclerView.apply {
            adapter = timerAdapter
            layoutManager = LinearLayoutManager(this@TimerListActivity)
        }
    }

    private fun setupPullToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Pull-to-refresh triggered. Enqueuing TimerCheckWorker for FULL sync.")
            binding.swipeRefreshLayout.isRefreshing = true
            val syncWorkRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>().build()
            WorkManager.getInstance(this).enqueue(syncWorkRequest)
        }
    }

    private fun loadTimersFromPreferences() {
        binding.emptyView.visibility = View.GONE

        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        if (ip.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_ip_not_configured), Toast.LENGTH_LONG).show()
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        val previousTimersJson = prefs.getString(PREVIOUS_TIMERS_KEY, null)
        val timers: List<Timer> = if (previousTimersJson != null) {
            try {
                json.decodeFromString<List<Timer>>(previousTimersJson)
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding previous timers from preferences", e)
                emptyList()
            }
        } else {
            emptyList()
        }

        if (timers.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.timersRecyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.timersRecyclerView.visibility = View.VISIBLE
            timerAdapter.updateTimers(timers)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onEditClicked(timer: Timer) {
        val intent = Intent(this, EditTimerActivity::class.java)
        intent.putExtra(EXTRA_TIMER, timer)
        startActivity(intent)
    }

    override fun onDeleteClicked(timer: Timer) {
        showDeleteConfirmationDialog(timer)
    }

    private fun showDeleteConfirmationDialog(timer: Timer) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_timer_title))
            .setMessage(getString(R.string.delete_timer_message, timer.name))
            .setPositiveButton(getString(R.string.action_delete)) { _, _ ->
                deleteTimer(timer)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }

    private fun deleteTimer(timer: Timer) {
        // Since enigmaClient is removed, we'll enqueue the worker to handle deletion and resync.
        // The worker needs a way to know which timer to delete.
        // For simplicity and to avoid adding complex data passing to the worker for a single delete,
        // we'll assume deleteTimer handles the network call and then enqueues the Worker for a full refresh.
        // Re-introducing a minimal EnigmaClient here for the delete operation as passing timer object
        // to worker for deletion is more complex and out of immediate scope.
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""
        val enigmaClientForDelete = io.github.legandy.enigmabridge.receiversettings.EnigmaClient(ip, user, pass, prefs)

        lifecycleScope.launch(Dispatchers.IO) {
            val result = enigmaClientForDelete.deleteTimer(timer) // Use a local client for delete
            withContext(Dispatchers.Main) {
                if (result.first) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.delete_timer_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    // After delete, trigger a full sync to update the list and MainActivity status
                    val syncWorkRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>().build()
                    WorkManager.getInstance(this@TimerListActivity).enqueue(syncWorkRequest)
                    binding.swipeRefreshLayout.isRefreshing = true // Show indicator
                } else {
                    Toast.makeText(applicationContext, result.second, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}