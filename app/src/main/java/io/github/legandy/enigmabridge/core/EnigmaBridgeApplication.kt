package io.github.legandy.enigmabridge.core

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import io.github.legandy.enigmabridge.data.PreferenceManager
import io.github.legandy.enigmabridge.data.TimerRepository

//  Initializes global components
class EnigmaBridgeApplication : Application(), Configuration.Provider {

    lateinit var prefManager: PreferenceManager
        private set

    lateinit var timerRepository: TimerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        prefManager = PreferenceManager(this)
        timerRepository = TimerRepository(this, prefManager)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setMinimumLoggingLevel(Log.DEBUG).build()
}
