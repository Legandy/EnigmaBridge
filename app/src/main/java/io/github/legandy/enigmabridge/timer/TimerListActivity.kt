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
import io.github.legandy.enigmabridge.receiversettings.EnigmaClient
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.service.RecordService
import io.github.legandy.enigmabridge.receiversettings.Timer
import io.github.legandy.enigmabridge.databinding.ActivityTimerListBinding
import io.github.legandy.enigmabridge.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerListActivity : AppCompatActivity(), TimerAdapter.OnTimerActionsListener {

    private lateinit var binding: ActivityTimerListBinding
    private lateinit var timerAdapter: TimerAdapter
    private lateinit var enigmaClient: EnigmaClient

    companion object {
        const val EXTRA_TIMER = "TIMER_EXTRA"
        private const val TAG = "TimerListActivity"
    }

    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                RecordService.Companion.ACTION_TIMER_LIST_CHANGED, MainActivity.Companion.ACTION_TIMER_SYNC_COMPLETED -> {
                    Log.d(TAG, "Received broadcast (${intent.action}), fetching timer list.")
                    fetchTimerList()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("EnigmaSettings", MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""
        enigmaClient = EnigmaClient(ip, user, pass, prefs)

        setupRecyclerView()
        setupPullToRefresh()

        val intentFilter = IntentFilter().apply {
            addAction(RecordService.Companion.ACTION_TIMER_LIST_CHANGED)
            addAction(MainActivity.Companion.ACTION_TIMER_SYNC_COMPLETED)
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(refreshReceiver, intentFilter)
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
        timerAdapter = TimerAdapter(mutableListOf(), this)
        binding.timersRecyclerView.apply {
            adapter = timerAdapter
            layoutManager = LinearLayoutManager(this@TimerListActivity)
        }
    }

    // ** THE FIX 2: Upgrade pull-to-refresh to trigger a full background sync **
    private fun setupPullToRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            Log.d(TAG, "Pull-to-refresh triggered. Enqueuing TimerCheckWorker.")
            binding.swipeRefreshLayout.isRefreshing = true
            val syncWorkRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>().build()
            WorkManager.getInstance(this).enqueue(syncWorkRequest)
        }
    }

    private fun fetchTimerList() {
        // This is now just for display. The loading indicator is handled by the pull-to-refresh listener
        // and the broadcast receiver.
        binding.emptyView.visibility = View.GONE

        val prefs = getSharedPreferences("EnigmaSettings", MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        if (ip.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_ip_not_configured), Toast.LENGTH_LONG).show()
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val timers = enigmaClient.getTimerList()
            withContext(Dispatchers.Main) {
                binding.swipeRefreshLayout.isRefreshing = false // Always hide indicator after fetch
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
                    Toast.makeText(
                        this@TimerListActivity,
                        getString(R.string.error_fetch_timers),
                        Toast.LENGTH_LONG
                    ).show()
                    binding.emptyView.visibility = View.VISIBLE
                }
            }
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
        lifecycleScope.launch(Dispatchers.IO) {
            val result = enigmaClient.deleteTimer(timer)
            withContext(Dispatchers.Main) {
                if (result.first) {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.delete_timer_success),
                        Toast.LENGTH_SHORT
                    ).show()
                    fetchTimerList()
                } else {
                    Toast.makeText(applicationContext, result.second, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}