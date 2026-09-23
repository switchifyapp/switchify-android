package com.enaboapps.switchify.service.pauseresume

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.scanning.ScanInterval
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceHudMessage
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.utils.Resources
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/**
 * Manages the pause/resume functionality for all switch types in Switchify.
 * This centralized manager ensures that all switches (external, camera, etc.)
 * are properly paused and resumed together.
 */
class PauseManager private constructor() {

    companion object {
        private const val DEFAULT_PAUSE_TIMEOUT_MS = 30000L // 30 seconds

        /** HUD status key so the pause banner replaces itself and only pause can dismiss it. */
        private const val PAUSE_HUD_KEY = "pause"

        // Broadcast actions
        const val ACTION_PAUSE_STARTED = "com.enaboapps.switchify.PAUSE_STARTED"
        const val ACTION_PAUSE_ENDED = "com.enaboapps.switchify.PAUSE_ENDED"

        @Volatile
        private var INSTANCE: PauseManager? = null

        fun getInstance(): PauseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PauseManager().also { INSTANCE = it }
            }
        }
    }

    private var contextRef: WeakReference<Context>? = null

    /** Indicates whether Switchify is currently paused */
    var isPaused: Boolean = false
        private set

    /** Timestamp of when the pause was initiated or last switch event during pause */
    private var pauseTimestamp: Long = 0

    /** Configured timeout for the current pause, read when the pause starts */
    private var pauseTimeoutMs: Long = DEFAULT_PAUSE_TIMEOUT_MS

    /** Coroutine job that manages the pause timeout */
    private var pauseJob: Job? = null

    /** Coroutine scope for managing pause-related coroutines */
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /**
     * Initializes the PauseManager with the given context.
     * Must be called before using any other methods.
     */
    fun init(context: Context) {
        this.contextRef = WeakReference(context)
    }

    /**
     * Starts the pause mode if not already paused.
     * Shows pause message, clears scanning UI and any open menu, and notifies all listeners.
     */
    fun startPause() {
        if (pauseJob != null) return

        // Get the configured pause timeout
        pauseTimeoutMs = contextRef?.get()?.let { context ->
            PreferenceManager(context).getLongValue(
                PreferenceManager.Keys.PREFERENCE_KEY_PAUSE_TIMEOUT,
                DEFAULT_PAUSE_TIMEOUT_MS
            )
        } ?: DEFAULT_PAUSE_TIMEOUT_MS

        isPaused = true
        pauseTimestamp = System.currentTimeMillis()
        showPauseStatus(speak = true)

        // Send broadcast that pause has started
        contextRef?.get()?.let { context ->
            context.sendBroadcast(
                Intent(ACTION_PAUSE_STARTED).setPackage(context.packageName)
            )
        }

        ServiceCore.getScanningManager()?.reset()

        pauseJob = coroutineScope.launch {
            while (isPaused) {
                delay(pauseTimeoutMs)
                if (System.currentTimeMillis() - pauseTimestamp > pauseTimeoutMs) {
                    resume()
                }
            }
        }
    }

    /**
     * Handles a switch event during pause.
     * Resets the pause timer but doesn't process the switch action.
     *
     * @return true if currently paused (event should be ignored), false otherwise
     */
    fun handleSwitchDuringPause(): Boolean {
        if (isPaused) {
            pauseTimestamp = System.currentTimeMillis()
            // Restart the on-screen countdown without re-reading the message aloud.
            showPauseStatus(speak = false)
            return true
        }
        return false
    }

    /**
     * Shows or refreshes the pause banner with a countdown to automatic resume.
     */
    private fun showPauseStatus(speak: Boolean) {
        ServiceMessageHUD.instance.show(
            ServiceHudMessage(
                text = Resources.getString(R.string.hud_pause, timeoutDisplay(pauseTimeoutMs)),
                severity = MessageSeverity.Warning,
                durationMillis = null,
                key = PAUSE_HUD_KEY,
                countdown = ScanInterval(SystemClock.uptimeMillis(), pauseTimeoutMs),
                speak = speak
            )
        )
    }

    private fun timeoutDisplay(timeoutMs: Long): String = when (timeoutMs) {
        30000L -> "30 seconds"
        60000L -> "1 minute"
        120000L -> "2 minutes"
        180000L -> "3 minutes"
        240000L -> "4 minutes"
        300000L -> "5 minutes"
        else -> "${timeoutMs / 1000} seconds"
    }

    /**
     * Checks if the switch has been held long enough to trigger unpause.
     * Should be called when a switch is released during pause.
     *
     * @param pressTimestamp The timestamp when the switch was pressed
     * @param holdDuration The configured hold duration in milliseconds
     * @return true if unpause was triggered, false otherwise
     */
    fun checkHoldToUnpause(pressTimestamp: Long, holdDuration: Long): Boolean {
        if (!isPaused) return false

        val holdTime = System.currentTimeMillis() - pressTimestamp
        if (holdTime >= holdDuration) {
            resume()
            return true
        }
        return false
    }

    /**
     * Resumes from pause mode.
     * Displays resume message and notifies all listeners.
     */
    fun resume() {
        isPaused = false
        pauseJob?.cancel()
        pauseJob = null

        ServiceMessageHUD.instance.dismissStatus(PAUSE_HUD_KEY)
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_pause_resume,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            severity = MessageSeverity.Success
        )

        // Send broadcast that pause has ended
        contextRef?.get()?.let { context ->
            context.sendBroadcast(
                Intent(ACTION_PAUSE_ENDED).setPackage(context.packageName)
            )
        }
    }

}
