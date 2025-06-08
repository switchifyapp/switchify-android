package com.enaboapps.switchify.utils

import android.content.Context
import android.util.Log
import com.amplitude.android.Amplitude
import com.amplitude.android.Configuration
import com.enaboapps.switchify.BuildConfig

/**
 * Logger class for logging events and messages to Amplitude.
 */
object Logger {
    private const val TAG = "SwitchifyLogger"
    private lateinit var amplitude: Amplitude

    /**
     * Initializes the logger with the given context.
     *
     * @param context The context to initialize the logger with.
     */
    fun init(context: Context) {
        val apiKey = BuildConfig.AMPLITUDE_API_KEY
        amplitude = Amplitude(Configuration(apiKey, context.applicationContext))
    }

    /**
     * Logs a predefined event from LogEvent sealed class.
     *
     * @param event The predefined event from LogEvent sealed class.
     */
    fun log(event: LogEvent) {
        amplitude.track(event.eventName)
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Event logged: ${event.eventName}")
        }
    }

}