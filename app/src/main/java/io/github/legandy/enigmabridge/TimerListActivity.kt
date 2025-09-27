package io.github.legandy.enigmabridge

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.github.legandy.enigmabridge.databinding.ActivityTimerListBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimerListActivity : AppCompatActivity(), TimerAdapter.OnTimerInteractionListener {

    private lateinit var binding: ActivityTimerListBinding
    private lateinit var timerAdapter: TimerAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTimerListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupPullToRefresh()
        fetchTimerList()
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
            fetchTimerList()
        }
    }

    private fun fetchTimerList() {
        binding.swipeRefreshLayout.isRefreshing = true
        binding.emptyView.visibility = View.GONE
        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        if (ip.isEmpty()) {
            Toast.makeText(this, getString(R.string.error_ip_not_configured), Toast.LENGTH_LONG).show()
            binding.swipeRefreshLayout.isRefreshing = false
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass)
            val timers = client.getTimerList()

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

    override fun onDeleteClicked(timer: Timer) {
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
        val prefs = getSharedPreferences("EnigmaSettings", Context.MODE_PRIVATE)
        val ip = prefs.getString("IP_ADDRESS", "") ?: ""
        val user = prefs.getString("USERNAME", "root") ?: ""
        val pass = prefs.getString("PASSWORD", "") ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            val client = EnigmaClient(ip, user, pass)
            val success = client.deleteTimer(timer)
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(applicationContext, getString(R.string.delete_timer_success), Toast.LENGTH_SHORT).show()
                    fetchTimerList() // Refresh the list
                } else {
                    Toast.makeText(applicationContext, getString(R.string.delete_timer_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}

