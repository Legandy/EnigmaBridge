package io.github.legandy.enigmabridge

import android.app.Application
import androidx.work.Configuration

/**
 * A custom Application class to ensure WorkManager is initialized correctly.
 * This is the standard, recommended approach for robust background work.
 */
class EnigmaBridgeApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .build()
}

