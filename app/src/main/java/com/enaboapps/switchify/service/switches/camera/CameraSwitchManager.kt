package com.enaboapps.switchify.service.switches.camera

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.face.FaceProcessingService
import com.enaboapps.switchify.service.pauseresume.PauseManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Manages camera switch gesture processing and triggers actions.
 * Camera processing is now handled by CameraForegroundService.
 * This class focuses only on gesture state management and action triggering.
 */
class CameraSwitchManager(
    private val context: Context,
    private val scanningManager: ScanningManager,
    private val switchEventProvider: SwitchEventProvider
) {
    private val preferenceManager = PreferenceManager(context)
    private var isInitialized = false

    private data class CameraSwitchState(
        var isActive: Boolean,
        var startTime: Long = 0
    )

    private val gestureStates = mutableMapOf(
        CameraSwitchFacialGesture.SMILE to CameraSwitchState(false),
        CameraSwitchFacialGesture.LEFT_WINK to CameraSwitchState(true),
        CameraSwitchFacialGesture.RIGHT_WINK to CameraSwitchState(true),
        CameraSwitchFacialGesture.BLINK to CameraSwitchState(true)
        // Head turns handled directly without state tracking
    )

    // Track currently active gesture
    private var activeGesture: String? = null

    // Head turn rate limiting
    private var lastHeadTurnTime = 0L
    private val headTurnCooldown = 500L // 500ms minimum between head turn triggers

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var isReceiverRegistered = false

    private val pauseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PauseManager.ACTION_PAUSE_STARTED -> onPauseStarted()
                PauseManager.ACTION_PAUSE_ENDED -> onPauseEnded()
            }
        }
    }

    companion object {
        private const val TAG = "CameraSwitchManager"
    }

    /**
     * Initialize the camera switch manager
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }

        try {
            registerPauseReceiver()
            isInitialized = true
            Log.d(TAG, "Camera switch manager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
        }
    }

    private fun registerPauseReceiver() {
        if (!isReceiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(PauseManager.ACTION_PAUSE_STARTED)
                addAction(PauseManager.ACTION_PAUSE_ENDED)
            }

            ContextCompat.registerReceiver(
                context,
                pauseReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
            Log.d(TAG, "Pause receiver registered")
        }
    }

    private fun checkInitialization(): Boolean {
        if (!isInitialized) {
            Log.w(TAG, "Manager not initialized")
            return false
        }
        return true
    }

    private fun onPauseStarted() {
        Log.d(TAG, "Pause started - resetting gesture states")
        gestureStates.values.forEach { it.isActive = false }
        activeGesture = null
    }

    private fun onPauseEnded() {
        Log.d(TAG, "Pause ended - ready for gestures")
    }

    /**
     * Processes face detection results from CameraForegroundService.
     * This is the main entry point for gesture processing.
     */
    fun processFaceResult(result: FaceProcessingService.FaceDetectionResult) {
        if (!checkInitialization()) {
            return
        }

        val pauseManager = ServiceCore.getPauseManager()
        if (pauseManager.isPaused) {
            return
        }

        // Process detected gestures
        result.detectedGestures.forEach { gestureId ->
            when (gestureId) {
                CameraSwitchFacialGesture.SMILE -> {
                    handleGestureStateChange(
                        CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE),
                        true
                    )
                }

                CameraSwitchFacialGesture.LEFT_WINK -> {
                    handleGestureStateChange(
                        CameraSwitchFacialGesture(CameraSwitchFacialGesture.LEFT_WINK),
                        true
                    )
                }

                CameraSwitchFacialGesture.RIGHT_WINK -> {
                    handleGestureStateChange(
                        CameraSwitchFacialGesture(CameraSwitchFacialGesture.RIGHT_WINK),
                        true
                    )
                }

                CameraSwitchFacialGesture.BLINK -> {
                    handleGestureStateChange(
                        CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK),
                        true
                    )
                }

                CameraSwitchFacialGesture.HEAD_TURN_LEFT,
                CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
                CameraSwitchFacialGesture.HEAD_TURN_UP,
                CameraSwitchFacialGesture.HEAD_TURN_DOWN -> {
                    triggerHeadTurnGesture(CameraSwitchFacialGesture(gestureId))
                }
            }
        }

        // Handle face state changes for gesture ending
        val faceState = result.faceState
        if (faceState.isSmiling) {
            handleGestureStateChange(
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE),
                false
            )
        }
        if (faceState.leftEyeOpen) {
            handleGestureStateChange(
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.LEFT_WINK),
                false
            )
        }
        if (faceState.rightEyeOpen) {
            handleGestureStateChange(
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.RIGHT_WINK),
                false
            )
        }
        if (faceState.leftEyeOpen && faceState.rightEyeOpen) {
            handleGestureStateChange(
                CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK),
                false
            )
        }
    }

    private fun handleGestureStateChange(gesture: CameraSwitchFacialGesture, isStarting: Boolean) {
        val state = gestureStates[gesture.id] ?: return

        if (isStarting && !state.isActive) {
            gestureStarted(gesture)
        } else if (!isStarting && state.isActive) {
            gestureCompleted(gesture)
        }
    }

    private fun gestureStarted(gesture: CameraSwitchFacialGesture) {
        val state = gestureStates[gesture.id] ?: return
        state.isActive = true
        state.startTime = System.currentTimeMillis()
        activeGesture = gesture.id

        Log.d(TAG, "Gesture started: ${gesture.getName()}")
    }

    private fun gestureCompleted(gesture: CameraSwitchFacialGesture) {
        val state = gestureStates[gesture.id] ?: return
        if (!state.isActive) return

        state.isActive = false
        val duration = System.currentTimeMillis() - state.startTime

        Log.d(TAG, "Gesture completed: ${gesture.getName()}, duration: ${duration}ms")

        // Check if this gesture meets the minimum hold time requirement
        val requiredHoldTime = getRequiredHoldTime(gesture)
        if (duration >= requiredHoldTime) {
            triggerSwitchAction(gesture)
        }

        if (activeGesture == gesture.id) {
            activeGesture = null
        }
    }

    private fun getRequiredHoldTime(gesture: CameraSwitchFacialGesture): Long {
        return when (gesture.id) {
            CameraSwitchFacialGesture.SMILE ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_SMILE_TIME,
                    500L
                )

            CameraSwitchFacialGesture.LEFT_WINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_LEFT_WINK_TIME,
                    300L
                )

            CameraSwitchFacialGesture.RIGHT_WINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_RIGHT_WINK_TIME,
                    300L
                )

            CameraSwitchFacialGesture.BLINK ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_BLINK_TIME,
                    400L
                )

            CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            CameraSwitchFacialGesture.HEAD_TURN_UP,
            CameraSwitchFacialGesture.HEAD_TURN_DOWN -> 0L // Instant trigger
            else -> 1000L
        }
    }

    private fun triggerSwitchAction(gesture: CameraSwitchFacialGesture) {
        val switchEvent = findSwitchEventForGesture(gesture)
        if (switchEvent != null) {
            coroutineScope.launch(Dispatchers.Main) {
                Log.i(TAG, "Triggering switch action for gesture: ${gesture.getName()}")
                if (scanningManager.checkOngoingTasks()) return@launch
                scanningManager.performAction(switchEvent.pressAction)
            }
        }
    }

    private fun findSwitchEventForGesture(gesture: CameraSwitchFacialGesture): SwitchEvent? =
        switchEventProvider.findCamera(gesture.id)

    private fun triggerHeadTurnGesture(gesture: CameraSwitchFacialGesture) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastHeadTurnTime < headTurnCooldown) {
            return // Rate limiting
        }

        lastHeadTurnTime = currentTime

        val switchEvent = findSwitchEventForGesture(gesture)
        if (switchEvent != null) {
            coroutineScope.launch(Dispatchers.Main) {
                Log.i(TAG, "Triggering head turn gesture: ${gesture.getName()}")
                if (scanningManager.checkOngoingTasks()) return@launch
                scanningManager.performAction(switchEvent.pressAction)
            }
        }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            if (isReceiverRegistered) {
                context.unregisterReceiver(pauseReceiver)
                isReceiverRegistered = false
            }

            gestureStates.values.forEach { it.isActive = false }
            activeGesture = null
            isInitialized = false

            Log.d(TAG, "Camera switch manager cleaned up")

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}
