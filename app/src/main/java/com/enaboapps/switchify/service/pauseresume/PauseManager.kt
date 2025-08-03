package com.enaboapps.switchify.service.pauseresume

import android.content.Context
import android.content.Intent
import java.lang.ref.WeakReference
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.service.window.SwitchifyAccessibilityWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Manages the pause/resume functionality for all switch types in Switchify.
 * This centralized manager ensures that all switches (external, camera, etc.)
 * are properly paused and resumed together.
 */
class PauseManager private constructor() {
    
    companion object {
        private const val PAUSE_DURATION_MS = 30000L // 30 seconds
        private const val UI_DELAY_MS = 1000L // 1 second delay for UI transitions
        
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
     * Shows pause message, hides service window, and notifies all listeners.
     */
    fun startPause() {
        if (pauseJob != null) return
        
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_pause,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
        
        isPaused = true
        pauseTimestamp = System.currentTimeMillis()
        
        // Send broadcast that pause has started
        contextRef?.get()?.let { context ->
            context.sendBroadcast(
                Intent(ACTION_PAUSE_STARTED).setPackage(context.packageName)
            )
        }
        
        pauseJob = coroutineScope.launch {
            // Give the pause message time to show before hiding the window
            delay(UI_DELAY_MS)
            
            // Hide the service window when pausing
            SwitchifyAccessibilityWindow.instance.hide()
            
            // Monitor for pause timeout
            while (isPaused) {
                delay(PAUSE_DURATION_MS)
                if (System.currentTimeMillis() - pauseTimestamp > PAUSE_DURATION_MS) {
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
            return true
        }
        return false
    }
    
    /**
     * Resumes from pause mode.
     * Shows service window, displays resume message, and notifies all listeners.
     */
    fun resume() {
        isPaused = false
        pauseJob?.cancel()
        pauseJob = null
        
        coroutineScope.launch {
            // Show the service window when resuming
            SwitchifyAccessibilityWindow.instance.show()
            
            // Give the window time to show before displaying the message
            delay(UI_DELAY_MS)
            
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_pause_resume,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            
            // Send broadcast that pause has ended
            contextRef?.get()?.let { context ->
                context.sendBroadcast(
                    Intent(ACTION_PAUSE_ENDED).setPackage(context.packageName)
                )
            }
        }
    }
    
}