package com.enaboapps.switchify

import android.app.Application
import android.util.Log
import com.enaboapps.switchify.service.stats.StatsAggregationScheduler
import com.enaboapps.switchify.service.stats.StatsCollector
import com.enaboapps.switchify.utils.Resources

class SwitchifyApplication : Application() {

    companion object {
        private const val TAG = "SwitchifyApplication"
    }

    override fun onCreate() {
        super.onCreate()

        Resources.init(this)

        // Initialize stats collector
        StatsCollector.getInstance().initialize(this)

        // Schedule daily stats aggregation
        StatsAggregationScheduler.scheduleDailyAggregation(this)

        Log.i(TAG, "SwitchifyApplication initialized")
    }
}