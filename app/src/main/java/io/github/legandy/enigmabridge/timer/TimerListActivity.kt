package io.github.legandy.enigmabridge.timer

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.legandy.enigmabridge.R
import io.github.legandy.enigmabridge.core.AppEvent
import io.github.legandy.enigmabridge.core.AppEventBus
import io.github.legandy.enigmabridge.core.AppThemeManager
import io.github.legandy.enigmabridge.core.EnigmaBridgeApplication
import io.github.legandy.enigmabridge.core.PreferenceManager
import io.github.legandy.enigmabridge.databinding.ActivityTimerListBinding
import io.github.legandy.enigmabridge.receiversettings.Timer
import kotlinx.coroutines.launch

class TimerListActivity : AppCompatActivity(), TimerAdapter.OnTimerActionsListener {

    private lateinit var binding: ActivityTimerListBinding
    private lateinit var prefManager: PreferenceManager
    private lateinit var timerAdapter: TimerAdapter
    
    private val viewModel: TimerListViewModel by viewModels {
        TimerListViewModel.Factory((application as EnigmaBridgeApplication).timerRepository)
    }

    companion object {
        const val EXTRA_TIMER = "TIMER_EXTRA"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val app = application as EnigmaBridgeApplication
        prefManager = app.prefManager
        AppThemeManager.applyThemeAndAccentColor(this)
        
        super.onCreate(savedInstanceState)
        binding = ActivityTimerListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        setupMenu()
        setupRecyclerView()
        setupPullToRefresh()
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        triggerBackgroundSync(silent = true)
    }

    private fun setupMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.timer_list_menu, menu)
            }

            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                return when (menuItem.itemId) {
                    R.id.action_refresh -> {
                        performRefresh()
                        true
                    }
                    android.R.id.home -> {
                        onBackPressedDispatcher.onBackPressed()
                        true
                    }
                    else -> false
                }
            }
        }, this, Lifecycle.State.RESUMED)
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
            performRefresh()
        }
    }

    private fun performRefresh() {
        binding.swipeRefreshLayout.isRefreshing = true
        viewModel.refresh()
        triggerBackgroundSync(silent = false)
    }

    private fun triggerBackgroundSync(silent: Boolean) {
        val syncWorkRequest = OneTimeWorkRequestBuilder<TimerCheckWorker>()
            .setInputData(workDataOf(TimerCheckWorker.INPUT_DATA_KEY_SILENT_SYNC to silent))
            .build()
        WorkManager.getInstance(this).enqueue(syncWorkRequest)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is TimerListUiState.Loading -> { }
                            is TimerListUiState.Success -> {
                                updateUI(state.timers)
                            }
                            is TimerListUiState.Error -> {
                                Toast.makeText(this@TimerListActivity, state.message, Toast.LENGTH_LONG).show()
                                binding.swipeRefreshLayout.isRefreshing = false
                            }
                        }
                    }
                }

                launch {
                    AppEventBus.events.collect { event ->
                        if (event is AppEvent.TimerSyncCompleted) {
                            binding.swipeRefreshLayout.isRefreshing = false
                        }
                    }
                }
            }
        }
    }

    private fun updateUI(timers: List<Timer>) {
        if (timers.isEmpty()) {
            binding.emptyView.visibility = View.VISIBLE
            binding.timersRecyclerView.visibility = View.GONE
        } else {
            binding.emptyView.visibility = View.GONE
            binding.timersRecyclerView.visibility = View.VISIBLE
            timerAdapter.updateTimers(timers)
        }
        binding.swipeRefreshLayout.isRefreshing = false
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
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
                binding.swipeRefreshLayout.isRefreshing = true
                viewModel.deleteTimer(timer)
            }
            .setNegativeButton(getString(R.string.action_cancel), null)
            .show()
    }
}
