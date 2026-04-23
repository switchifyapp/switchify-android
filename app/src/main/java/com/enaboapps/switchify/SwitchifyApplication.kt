package com.enaboapps.switchify

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.enaboapps.switchify.service.stats.StatsCollector
import com.enaboapps.switchify.utils.CrashReporter
import com.enaboapps.switchify.utils.Logger
import com.enaboapps.switchify.utils.Resources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SwitchifyApplication : Application() {

    companion object {
        private const val TAG = "SwitchifyApplication"
    }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()

        Resources.init(this)

        // Wire Logger to PreferenceManager before any Logger.log(..) call so the
        // telemetry opt-in gate is active (including for the pending-crash upload
        // kicked off immediately below).
        Logger.init(this)

        CrashReporter.uploadPendingCrashIfPresent(this)
        CrashReporter.install(this)

        // Initialize stats collector
        StatsCollector.getInstance().initialize(this)

        // Set up process lifecycle observer for proper stats cleanup
        setupProcessLifecycleObserver()

        Log.i(TAG, "SwitchifyApplication initialized")
    }

    /**
     * Sets up a ProcessLifecycleOwner observer to handle app-wide lifecycle events.
     * This ensures StatsCollector flushes data when the app goes to background.
     */
    private fun setupProcessLifecycleObserver() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                // App moved to background - flush any pending stats
                Log.d(TAG, "App moved to background, flushing stats")
                applicationScope.launch {
                    try {
                        StatsCollector.getInstance().forceFlush()
                        Log.d(TAG, "Stats flushed successfully on background")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error flushing stats on background", e)
                    }
                }
            }

            override fun onDestroy(owner: LifecycleOwner) {
                // Process is being destroyed - flush then close StatsCollector
                Log.d(TAG, "Process lifecycle onDestroy, flushing and closing StatsCollector")
                try {
                    kotlinx.coroutines.runBlocking {
                        StatsCollector.getInstance().forceFlush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error flushing stats before close", e)
                }
                StatsCollector.getInstance().close()
            }
        })
    }
}