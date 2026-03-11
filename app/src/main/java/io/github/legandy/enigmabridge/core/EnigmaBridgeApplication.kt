package io.github.legandy.enigmabridge.core

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import io.github.legandy.enigmabridge.data.TimerRepository

/**
 * A custom Application class to ensure WorkManager is initialized correctly.
 * This is the standard, recommended approach for robust background work.
 * Also provides singletons for [PreferenceManager] and [TimerRepository].
 */
class EnigmaBridgeApplication : Application(), Configuration.Provider {

    lateinit var prefManager: PreferenceManager
        private set

    lateinit var timerRepository: TimerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        // Initialize Singletons
        prefManager = PreferenceManager(this)
        timerRepository = TimerRepository(this, prefManager)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .build()
}
