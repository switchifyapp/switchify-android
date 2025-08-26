package com.enaboapps.switchify.service.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration

/**
 * This class watches the screen for changes including screen state and orientation.
 */
class ScreenWatcher(
    private val onScreenWake: (() -> Unit)? = null,
    private val onScreenSleep: (() -> Unit)? = null,
    private val onOrientationChanged: (() -> Unit)? = null
) {

    private var isScreenOn = true
    private var currentOrientation: Int = Configuration.ORIENTATION_UNDEFINED

    /**
     * The broadcast receiver for screen and orientation changes.
     */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> onScreenWake()
                Intent.ACTION_SCREEN_OFF -> onScreenSleep()
                Intent.ACTION_CONFIGURATION_CHANGED -> {
                    context?.let { checkOrientationChange(it) }
                }
            }
        }
    }

    fun register(context: Context) {
        context.registerReceiver(screenReceiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_CONFIGURATION_CHANGED)
        })
        // Initialize the current orientation
        currentOrientation = context.resources.configuration.orientation
    }

    /**
     * This method is called when the screen is turned on.
     */
    fun onScreenWake() {
        if (!isScreenOn) {
            isScreenOn = true
            onScreenWake?.invoke()
        }
    }

    /**
     * This method is called when the screen is turned off.
     */
    fun onScreenSleep() {
        if (isScreenOn) {
            isScreenOn = false
            onScreenSleep?.invoke()
        }
    }

    /**
     * This method checks for orientation changes and invokes the callback if changed.
     */
    private fun checkOrientationChange(context: Context) {
        val newOrientation = context.resources.configuration.orientation
        if (newOrientation != currentOrientation) {
            currentOrientation = newOrientation
            onOrientationChanged?.invoke()
        }
    }

    /**
     * Unregister the broadcast receiver to prevent memory leaks.
     */
    fun unregister(context: Context) {
        try {
            context.unregisterReceiver(screenReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }
}