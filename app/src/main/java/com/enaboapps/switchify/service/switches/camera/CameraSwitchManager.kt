package com.enaboapps.switchify.service.switches.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.pauseresume.PauseManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CameraSwitchManager(
    private val context: Context,
    private val scanningManager: ScanningManager,
    private val switchEventProvider: SwitchEventProvider
) {
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private var isInitialized = false

    private data class CameraSwitchState(
        var isActive: Boolean,
        var startTime: Long = 0
    )

    private val gestureStates = mutableMapOf(
        CameraSwitchFacialGesture.SMILE to CameraSwitchState(false),
        CameraSwitchFacialGesture.LEFT_WINK to CameraSwitchState(true),
        CameraSwitchFacialGesture.RIGHT_WINK to CameraSwitchState(true),
        CameraSwitchFacialGesture.BLINK to CameraSwitchState(true),
        CameraSwitchFacialGesture.HEAD_TURN_LEFT to CameraSwitchState(false),
        CameraSwitchFacialGesture.HEAD_TURN_RIGHT to CameraSwitchState(false)
    )

    // Track currently active gesture
    private var activeGesture: String? = null

    private data class FaceState(
        var leftEyeOpen: Boolean = true,
        var rightEyeOpen: Boolean = true,
        var isSmiling: Boolean = false,
        var headRotationY: Float = 0f
    )

    private val currentFaceState = FaceState()
    private var lastProcessedState = FaceState()

    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    private var faceDetector: FaceDetector? = null
    
    private val pauseReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PauseManager.ACTION_PAUSE_STARTED -> onPauseStarted()
                PauseManager.ACTION_PAUSE_ENDED -> onPauseEnded()
            }
        }
    }

    private fun checkInitialization(): Boolean {
        if (!isInitialized) {
            Log.e(
                TAG,
                "CameraSwitchManager must be initialized before use. Call initialize() first."
            )
            return false
        }
        return true
    }

    private fun isCameraAccessGranted(): Boolean =
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    /**
     * Initializes the camera switch manager.
     * This must be called before using any camera functionality.
     */
    fun initialize() {
        reset()
        gestureStates.forEach { (_, state) ->
            state.isActive = false
            state.startTime = 0
        }
        activeGesture = null
        lastProcessedState = FaceState()
        faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(MIN_FACE_SIZE)
                .enableTracking()  // Enable face tracking for better performance
                .build()
        )
        isInitialized = true
        
        // Register for pause broadcasts
        val filter = IntentFilter().apply {
            addAction(PauseManager.ACTION_PAUSE_STARTED)
            addAction(PauseManager.ACTION_PAUSE_ENDED)
        }
        LocalBroadcastManager.getInstance(context).registerReceiver(pauseReceiver, filter)
        
        Log.d(TAG, "CameraSwitchManager initialized")
    }

    private fun showCameraError() {
        coroutineScope.launch(Dispatchers.Main) {
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_camera_access_error,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
        }
    }

    /**
     * Starts the camera.
     * This must be called after the manager has been initialized.
     */
    fun startCamera(lifecycleOwner: LifecycleOwner) {
        if (!checkInitialization()) {
            Log.e(TAG, "Cannot start camera - manager not initialized")
            return
        }

        if (!isCameraAccessGranted()) {
            showCameraError()
            return
        }

        cameraProviderFuture = ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                try {
                    cameraProvider = future.get()
                    bindPreview(lifecycleOwner)
                    Log.d(TAG, "Camera started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera", e)
                    showCameraError()
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    /**
     * Binds the preview to the camera.
     * This must be called after the camera has been started.
     */
    @OptIn(ExperimentalGetImage::class)
    private fun bindPreview(lifecycleOwner: LifecycleOwner) {
        if (!checkInitialization()) {
            return
        }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .apply {
                setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    processImageSafely(imageProxy)
                }
            }

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                Preview.Builder().build().also { preview = it },
                imageAnalyzer
            )
            Log.d(TAG, "Camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    /**
     * Processes the image safely.
     * This must be called after the camera has been bound.
     */
    @OptIn(ExperimentalGetImage::class)
    private fun processImageSafely(imageProxy: ImageProxy) {
        coroutineScope.launch(Dispatchers.Default) {

            try {
                imageProxy.image?.let { mediaImage ->
                    val image = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    withContext(Dispatchers.Main) {
                        faceDetector?.let { detector ->
                            detector.process(image)
                                .addOnSuccessListener { faces ->
                                    if (faces.isNotEmpty()) {
                                        processFace(faces[0])
                                    } else {
                                        reset()
                                    }
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Face detection failed", e)
                                    reset()
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        }
                    }
                } ?: imageProxy.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
                imageProxy.close()
            }
        }
    }

    /**
     * Processes the face.
     * This must be called after the image has been processed.
     */
    private fun processFace(face: Face) {
        if (!checkInitialization()) {
            return
        }
        
        // Don't process gestures during pause
        val pauseManager = ServiceCore.getPauseManager()
        if (pauseManager.isPaused) {
            Log.d(TAG, "Ignoring face processing - currently paused")
            return
        }
        // Update current face state
        currentFaceState.apply {
            leftEyeOpen = (face.leftEyeOpenProbability ?: 1f) > EYE_OPEN_THRESHOLD
            rightEyeOpen = (face.rightEyeOpenProbability ?: 1f) > EYE_OPEN_THRESHOLD
            isSmiling = (face.smilingProbability ?: 0f) > SMILE_THRESHOLD
            headRotationY = face.headEulerAngleY
        }

        // Debug logging for state tracking
        Log.v(
            TAG,
            "Face state - L:${currentFaceState.leftEyeOpen} R:${currentFaceState.rightEyeOpen} " +
                    "Active:${activeGesture} LastL:${lastProcessedState.leftEyeOpen} LastR:${lastProcessedState.rightEyeOpen}"
        )

        // Only process if the state has changed
        if (currentFaceState != lastProcessedState) {
            // Handle Smile
            if (currentFaceState.isSmiling != lastProcessedState.isSmiling && switchEventProvider.isFacialGestureAssigned(
                    CameraSwitchFacialGesture.SMILE
                )
            ) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.SMILE),
                    currentFaceState.isSmiling
                )
            }

            // Handle Left Wink (only when right eye is open)
            if (currentFaceState.leftEyeOpen != lastProcessedState.leftEyeOpen && currentFaceState.rightEyeOpen && switchEventProvider.isFacialGestureAssigned(
                    CameraSwitchFacialGesture.LEFT_WINK
                )
            ) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.LEFT_WINK),
                    !currentFaceState.leftEyeOpen
                )
            }

            // Handle Right Wink (only when left eye is open)
            if (currentFaceState.rightEyeOpen != lastProcessedState.rightEyeOpen && currentFaceState.leftEyeOpen && switchEventProvider.isFacialGestureAssigned(
                    CameraSwitchFacialGesture.RIGHT_WINK
                )
            ) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.RIGHT_WINK),
                    !currentFaceState.rightEyeOpen
                )
            }

            // Handle Blink
            val eyesClosed = !currentFaceState.leftEyeOpen && !currentFaceState.rightEyeOpen
            if (eyesClosed && switchEventProvider.isFacialGestureAssigned(
                    CameraSwitchFacialGesture.BLINK
                )
            ) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK),
                    true
                )
            } else if (!eyesClosed && switchEventProvider.isFacialGestureAssigned(
                    CameraSwitchFacialGesture.BLINK
                )
            ) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.BLINK),
                    false
                )
            }

            // Handle Head Turn Left (positive Y rotation)
            val isHeadTurnedLeft = currentFaceState.headRotationY > HEAD_TURN_THRESHOLD
            val wasHeadTurnedLeft = lastProcessedState.headRotationY > HEAD_TURN_THRESHOLD
            if (isHeadTurnedLeft != wasHeadTurnedLeft && switchEventProvider.isFacialGestureAssigned(
                    CameraSwitchFacialGesture.HEAD_TURN_LEFT
                )
            ) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_LEFT),
                    isHeadTurnedLeft
                )
            }

            // Handle Head Turn Right (negative Y rotation)
            val isHeadTurnedRight = currentFaceState.headRotationY < -HEAD_TURN_THRESHOLD
            val wasHeadTurnedRight = lastProcessedState.headRotationY < -HEAD_TURN_THRESHOLD
            if (isHeadTurnedRight != wasHeadTurnedRight && switchEventProvider.isFacialGestureAssigned(
                    CameraSwitchFacialGesture.HEAD_TURN_RIGHT
                )
            ) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture(CameraSwitchFacialGesture.HEAD_TURN_RIGHT),
                    isHeadTurnedRight
                )
            }

            // Update last processed state
            lastProcessedState = currentFaceState.copy()
        }
    }

    /**
     * Handles the gesture state change.
     * This must be called after the face has been processed.
     */
    private fun handleGestureStateChange(gesture: CameraSwitchFacialGesture, isStarting: Boolean) {
        if (!checkInitialization()) {
            return

        }
        if (isStarting) {
            gestureStarted(gesture)
        } else {
            // Only complete gestures that were actually started
            if (activeGesture == gesture.id) {
                gestureCompleted(gesture)
            }
        }
    }

    /**
     * Resets the gesture states.
     * This must be called after the camera has been stopped.
     */
    private fun reset() {
        gestureStates.forEach { (_, state) ->
            state.isActive = false
            state.startTime = 0
        }
        activeGesture = null
        lastProcessedState = FaceState()  // Reset the last processed state
    }

    /**
     * Stops the camera.
     * This must be called after the manager has been initialized.
     */
    fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageAnalyzer = null
            preview = null
            cameraProviderFuture = null
            // Don't set isInitialized to false here so we can restart the camera
            Log.d(TAG, "Camera stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop camera", e)
            showCameraError()
        }
    }

    /**
     * Starts a new gesture.
     * This must be called after the manager has been initialized.
     */
    private fun gestureStarted(gesture: CameraSwitchFacialGesture) {
        if (!checkInitialization()) {
            return
        }

        // Only start a new gesture if no other gesture is active
        if (activeGesture == null) {
            findSwitchEventForGesture(gesture)?.let { switchEvent ->
                gestureStates[switchEvent.code]?.apply {
                    isActive = true
                    startTime = System.currentTimeMillis()
                }
                activeGesture = switchEvent.code
                if (switchEvent.pressAction.id == SwitchAction.ACTION_SELECT) {
                    scanningManager.pauseScanning()
                }
                Log.d(TAG, "Activated gesture: ${switchEvent.code}")
            }
        } else {
            Log.v(TAG, "Ignored gesture ${gesture.id} - $activeGesture is already active")
        }
    }

    /**
     * Completes a gesture.
     * This must be called after the manager has been initialized.
     */
    private fun gestureCompleted(gesture: CameraSwitchFacialGesture) {
        if (!checkInitialization()) {
            return
        }
        Log.d(TAG, "Gesture completed: ${gesture.id}")

        findSwitchEventForGesture(gesture)?.let { switchEvent ->
            // Only process completion if this is the active gesture
            if (activeGesture == switchEvent.code) {
                if (switchEvent.pressAction.id == SwitchAction.ACTION_SELECT) {
                    scanningManager.resumeScanning()
                }
                gestureStates[switchEvent.code]?.let { state ->
                    if (state.isActive && state.startTime > 0) {
                        val timeElapsed = System.currentTimeMillis() - state.startTime
                        if (timeElapsed >= switchEvent.facialGestureTime) {
                            scanningManager.performAction(switchEvent.pressAction)
                            Log.d(
                                TAG,
                                "${gesture.id} completed successfully after ${timeElapsed}ms"
                            )
                        } else {
                            Log.d(
                                TAG,
                                "${gesture.id} interrupted after ${timeElapsed}ms (needed ${switchEvent.facialGestureTime}ms)"
                            )
                        }
                    }
                    state.isActive = false
                }
                activeGesture = null
                Log.d(TAG, "Cleared active gesture: ${switchEvent.code}")
            } else {
                Log.d(TAG, "Ignored completion of ${gesture.id} - not the active gesture")
            }
        }
    }

    private fun findSwitchEventForGesture(gesture: CameraSwitchFacialGesture): SwitchEvent? =
        switchEventProvider.findCamera(gesture.id)

    private fun onPauseStarted() {
        Log.d(TAG, "Pause started - stopping camera")
        stopCamera()
    }
    
    private fun onPauseEnded() {
        Log.d(TAG, "Pause ended - restarting camera")
        if (context is LifecycleOwner) {
            startCamera(context)
        }
    }
    
    /**
     * Cleans up the camera manager resources.
     * Should be called when the camera manager is no longer needed.
     */
    fun cleanup() {
        LocalBroadcastManager.getInstance(context).unregisterReceiver(pauseReceiver)
        stopCamera()
        faceDetector?.close()
        faceDetector = null
        isInitialized = false
    }

    companion object {
        private const val TAG = "CameraSwitchManager"
        private const val SMILE_THRESHOLD = 0.5f
        private const val EYE_OPEN_THRESHOLD = 0.2f
        private const val MIN_FACE_SIZE = 0.2f
        private const val HEAD_TURN_THRESHOLD = 20f // degrees
    }
}