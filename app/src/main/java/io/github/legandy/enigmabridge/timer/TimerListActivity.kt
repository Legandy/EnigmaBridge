package io.github.legandy.enigmabridge.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import io.github.legandy.enigmabridge.core.AppThemeManager
import io.github.legandy.enigmabridge.core.PreferenceManager

class TimerListActivity : AppCompatActivity(), TimerAdapter.OnTimerActionsListener {

    private lateinit var binding: ActivityTimerListBinding
    private lateinit var prefManager: PreferenceManager
    private lateinit var timerAdapter: TimerAdapter // Declare timerAdapter
    // enigmaClient is no longer directly used in TimerListActivity for fetching

    companion object {
        const val EXTRA_TIMER = "TIMER_EXTRA"
        private const val TAG = "TimerListActivity"
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
                    Log.d(
                        TAG,
                        "Received broadcast (${intent.action}), loading timers from preferences."
                    )
                    loadTimersFromPreferences()
                    binding.swipeRefreshLayout.isRefreshing = false // Hide indicator
                }

                TimerCheckWorker.ACTION_WORKER_COMPLETED -> {
                    Log.d(TAG, "Received broadcast (${intent.action}), hiding refresh indicator.")
                    binding.swipeRefreshLayout.isRefreshing =
                        false // Hide indicator for any worker completion
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefManager = PreferenceManager(this)
        AppThemeManager.applyThemeAndAccentColor(this) // Apply theme here
        super.onCreate(savedInstanceState)
        binding = ActivityTimerListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

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

        if (!prefManager.isReceiverConfigured()) {
            Toast.makeText(this, getString(R.string.error_ip_not_configured), Toast.LENGTH_LONG)
                .show()
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        val previousTimersJson = prefManager.getPreviousTimersJson()
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
        val client = prefManager.getEnigmaClient()

        lifecycleScope.launch(Dispatchers.IO) {
            val result = client.deleteTimer(timer)
            withContext(Dispatchers.Main) {
                if (result.first) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.delete_timer_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    // Trigger worker for refresh
                    val syncWorkRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>().build()
                    WorkManager.getInstance(this@TimerListActivity).enqueue(syncWorkRequest)
                    binding.swipeRefreshLayout.isRefreshing = true
                } else {
                    Toast.makeText(applicationContext, result.second, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}