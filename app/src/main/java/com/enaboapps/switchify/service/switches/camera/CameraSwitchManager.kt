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
import com.enaboapps.switchify.service.camera.CameraLifecycle
import com.enaboapps.switchify.service.camera.CameraPermissionManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.face.FaceProcessingService
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.pauseresume.PauseManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.stats.StatsCollector
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.techniques.headcontrol.HeadControlSettings
import com.enaboapps.switchify.service.utils.GestureConflictDetector
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.switches.SwitchEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages camera switch gesture processing and triggers actions.
 * Camera processing is now handled by CameraForegroundService.
 * This class focuses only on gesture state management and action triggering.
 */
class CameraSwitchManager(
    private val context: Context,
    private val scanningManager: ScanningManager,
    private val switchEventProvider: SwitchEventProvider
) : CameraLifecycle {
    private val preferenceManager = PreferenceManager(context)
    private val permissionManager = CameraPermissionManager.getInstance(context)
    private val gestureConflictDetector = GestureConflictDetector(context)

    // Lifecycle state management
    private val _lifecycleState = MutableStateFlow(CameraLifecycle.State.UNINITIALIZED)
    override val lifecycleState: StateFlow<CameraLifecycle.State> = _lifecycleState.asStateFlow()
    private val lifecycleMutex = Mutex()

    private data class CameraSwitchState(
        var isActive: Boolean,
        var startTime: Long = 0
    )

    private val gestureStates = mutableMapOf(
        CameraSwitchFacialGesture.SMILE to CameraSwitchState(false),
        CameraSwitchFacialGesture.LEFT_WINK to CameraSwitchState(false),
        CameraSwitchFacialGesture.RIGHT_WINK to CameraSwitchState(false),
        CameraSwitchFacialGesture.BLINK to CameraSwitchState(false),
        CameraSwitchFacialGesture.PUCKER to CameraSwitchState(false)
        // Head turns handled directly without state tracking
    )

    // Track currently active gesture
    private var activeGesture: String? = null

    // Head turn rate limiting
    private var lastHeadTurnTime = 0L
    private val headTurnCooldown = 500L // 500ms minimum between head turn triggers

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
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
     * Initialize the camera switch manager asynchronously.
     */
    override suspend fun initialize(): Boolean = lifecycleMutex.withLock {
        when (lifecycleState.value) {
            CameraLifecycle.State.READY -> {
                Log.d(TAG, "Already initialized")
                return true
            }

            CameraLifecycle.State.INITIALIZING -> {
                Log.d(TAG, "Initialization already in progress")
                return false
            }

            CameraLifecycle.State.DESTROYED -> {
                Log.w(TAG, "Cannot initialize destroyed manager")
                return false
            }

            else -> {
                // Proceed with initialization
            }
        }

        return try {
            _lifecycleState.value = CameraLifecycle.State.INITIALIZING
            Log.d(TAG, "Initializing camera switch manager")

            // Check camera permission first
            if (!permissionManager.hasPermission()) {
                Log.w(TAG, "Camera permission not granted")
                _lifecycleState.value = CameraLifecycle.State.ERROR
                return false
            }

            registerPauseReceiver()
            _lifecycleState.value = CameraLifecycle.State.READY
            Log.d(TAG, "Camera switch manager initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize", e)
            _lifecycleState.value = CameraLifecycle.State.ERROR
            false
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
        if (!isReady()) {
            Log.w(TAG, "Manager not ready (state: ${lifecycleState.value})")
            return false
        }
        return true
    }

    private fun onPauseStarted() {
        Log.d(TAG, "Pause started - resetting gesture states")
        gestureStates.values.forEach { it.isActive = false }
        activeGesture = null
        // Head control is now independent - gesture state managed separately
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

        // Update head control when enabled (independent of access technique)
        val headControlService = ServiceCore.getHeadControlService()
        if (headControlService?.isEnabled() == true) {
            headControlService.updateHeadPosition(
                result.faceState.headRotationX,
                result.faceState.headRotationY
            )

            // Process gestures for head control selection
            processHeadControlGestures(result, headControlService)
        }

        // Process detected gestures with priority logic
        result.detectedGestures.forEach { gestureId ->
            // Check if head control should take priority for this gesture
            val shouldSkipSwitch = gestureConflictDetector.shouldPrioritizeHeadControl(
                gestureId,
                switchEventProvider
            )

            if (shouldSkipSwitch) {
                Log.d(
                    TAG,
                    "Skipping switch processing for gesture $gestureId - head control priority"
                )
                return@forEach
            }

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

                CameraSwitchFacialGesture.PUCKER -> {
                    handleGestureStateChange(
                        CameraSwitchFacialGesture(CameraSwitchFacialGesture.PUCKER),
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

        // Handle face state changes for gesture ending with priority logic
        val faceState = result.faceState

        // Check smile ending
        if (!faceState.isSmiling) {
            val shouldSkipSwitch = gestureConflictDetector.shouldPrioritizeHeadControl(
                CameraSwitchFacialGesture.SMILE,
                switchEventProvider
            )
            if (!shouldSkipSwitch) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE),
                    false
                )
            }
        }

        // Check left wink ending
        if (faceState.leftEyeOpen) {
            val shouldSkipSwitch = gestureConflictDetector.shouldPrioritizeHeadControl(
                CameraSwitchFacialGesture.LEFT_WINK,
                switchEventProvider
            )
            if (!shouldSkipSwitch) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.LEFT_WINK),
                    false
                )
            }
        }

        // Check right wink ending
        if (faceState.rightEyeOpen) {
            val shouldSkipSwitch = gestureConflictDetector.shouldPrioritizeHeadControl(
                CameraSwitchFacialGesture.RIGHT_WINK,
                switchEventProvider
            )
            if (!shouldSkipSwitch) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.RIGHT_WINK),
                    false
                )
            }
        }

        // Check blink ending
        if (faceState.leftEyeOpen && faceState.rightEyeOpen) {
            val shouldSkipSwitch = gestureConflictDetector.shouldPrioritizeHeadControl(
                CameraSwitchFacialGesture.BLINK,
                switchEventProvider
            )
            if (!shouldSkipSwitch) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK),
                    false
                )
            }
        }

        // Check if pucker gesture is no longer detected
        if (!result.detectedGestures.contains(CameraSwitchFacialGesture.PUCKER)) {
            val shouldSkipSwitch = gestureConflictDetector.shouldPrioritizeHeadControl(
                CameraSwitchFacialGesture.PUCKER,
                switchEventProvider
            )
            if (!shouldSkipSwitch) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.PUCKER),
                    false
                )
            }
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

            CameraSwitchFacialGesture.PUCKER ->
                preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_CAMERA_PUCKER_TIME,
                    500L
                )

            CameraSwitchFacialGesture.HEAD_TURN_LEFT,
            CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
            CameraSwitchFacialGesture.HEAD_TURN_UP,
            CameraSwitchFacialGesture.HEAD_TURN_DOWN -> 0L // Instant trigger
            else -> 1000L
        }
    }

    private fun triggerSwitchAction(gesture: CameraSwitchFacialGesture) {
        // Record stats for camera gesture
        StatsCollector.getInstance().recordSwitchPress("camera", gesture.id)

        val switchEvent = findSwitchEventForGesture(gesture)
        if (switchEvent != null) {
            coroutineScope.launch(Dispatchers.Main) {
                Log.i(TAG, "Triggering switch action for gesture: ${gesture.getName()}")
                if (GestureRepeatManager.instance.stopRepeatForSwitchPress()) return@launch
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
                if (GestureRepeatManager.instance.stopRepeatForSwitchPress()) return@launch
                if (scanningManager.checkOngoingTasks()) return@launch
                scanningManager.performAction(switchEvent.pressAction)
            }
        }
    }

    /**
     * Clean up resources asynchronously.
     */
    override suspend fun cleanup(): Boolean = lifecycleMutex.withLock {
        when (lifecycleState.value) {
            CameraLifecycle.State.DESTROYED -> {
                Log.d(TAG, "Already cleaned up")
                return true
            }

            CameraLifecycle.State.CLEANING_UP -> {
                Log.d(TAG, "Cleanup already in progress")
                return false
            }

            CameraLifecycle.State.UNINITIALIZED -> {
                Log.d(TAG, "Nothing to clean up")
                _lifecycleState.value = CameraLifecycle.State.DESTROYED
                return true
            }

            else -> {
                // Proceed with cleanup
            }
        }

        return try {
            _lifecycleState.value = CameraLifecycle.State.CLEANING_UP
            Log.d(TAG, "Cleaning up camera switch manager")

            if (isReceiverRegistered) {
                context.unregisterReceiver(pauseReceiver)
                isReceiverRegistered = false
            }

            gestureStates.values.forEach { it.isActive = false }
            activeGesture = null
            // Head control is now independent - gesture state managed separately

            // Cancel coroutine scope
            coroutineScope.cancel()

            _lifecycleState.value = CameraLifecycle.State.DESTROYED
            Log.d(TAG, "Camera switch manager cleaned up successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
            _lifecycleState.value = CameraLifecycle.State.ERROR
            false
        }
    }


    /**
     * Processes gestures for head control selection
     */
    private fun processHeadControlGestures(
        result: FaceProcessingService.FaceDetectionResult,
        headControlService: com.enaboapps.switchify.service.techniques.headcontrol.HeadControlService
    ) {
        val priorityEnabled = HeadControlSettings(context).isHeadControlPriorityEnabled()
        val headControlSettings = HeadControlSettings(context)
        val selectedGesture = headControlSettings.selectGesture()
        val switchAssigned = switchEventProvider.isFacialGestureAssigned(selectedGesture)

        // Process detected gestures (gesture starting)
        result.detectedGestures.forEach { gestureId ->
            when (gestureId) {
                CameraSwitchFacialGesture.SMILE,
                CameraSwitchFacialGesture.LEFT_WINK,
                CameraSwitchFacialGesture.RIGHT_WINK,
                CameraSwitchFacialGesture.BLINK,
                CameraSwitchFacialGesture.PUCKER -> {
                    val isConflictGesture = gestureId == selectedGesture && switchAssigned
                    if (!isConflictGesture || priorityEnabled) {
                        headControlService.processGesture(gestureId, true)
                    }
                }
                // Head turns are excluded from selection gestures
            }
        }

        // Handle face state changes for gesture ending
        val faceState = result.faceState

        // Smile ending
        if (!faceState.isSmiling) {
            val isConflictGesture =
                CameraSwitchFacialGesture.SMILE == selectedGesture && switchAssigned
            if (!isConflictGesture || priorityEnabled) {
                headControlService.processGesture(CameraSwitchFacialGesture.SMILE, false)
            }
        }

        // Left wink ending
        if (faceState.leftEyeOpen) {
            val isConflictGesture =
                CameraSwitchFacialGesture.LEFT_WINK == selectedGesture && switchAssigned
            if (!isConflictGesture || priorityEnabled) {
                headControlService.processGesture(CameraSwitchFacialGesture.LEFT_WINK, false)
            }
        }

        // Right wink ending  
        if (faceState.rightEyeOpen) {
            val isConflictGesture =
                CameraSwitchFacialGesture.RIGHT_WINK == selectedGesture && switchAssigned
            if (!isConflictGesture || priorityEnabled) {
                headControlService.processGesture(CameraSwitchFacialGesture.RIGHT_WINK, false)
            }
        }

        // Blink ending
        if (faceState.leftEyeOpen && faceState.rightEyeOpen) {
            val isConflictGesture =
                CameraSwitchFacialGesture.BLINK == selectedGesture && switchAssigned
            if (!isConflictGesture || priorityEnabled) {
                headControlService.processGesture(CameraSwitchFacialGesture.BLINK, false)
            }
        }

        // Pucker ending
        if (!result.detectedGestures.contains(CameraSwitchFacialGesture.PUCKER)) {
            val isConflictGesture =
                CameraSwitchFacialGesture.PUCKER == selectedGesture && switchAssigned
            if (!isConflictGesture || priorityEnabled) {
                headControlService.processGesture(CameraSwitchFacialGesture.PUCKER, false)
            }
        }
    }
}
