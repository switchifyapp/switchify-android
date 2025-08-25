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
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.face.FaceProcessingService
import com.enaboapps.switchify.service.pauseresume.PauseManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import com.google.common.util.concurrent.ListenableFuture
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong

class CameraSwitchManager(
    private val context: Context,
    private val scanningManager: ScanningManager,
    private val switchEventProvider: SwitchEventProvider
) {
    private val preferenceManager = PreferenceManager(context)
    private val faceProcessingService = FaceProcessingService(context)
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
        CameraSwitchFacialGesture.HEAD_TURN_RIGHT to CameraSwitchState(false),
        CameraSwitchFacialGesture.HEAD_TURN_UP to CameraSwitchState(false),
        CameraSwitchFacialGesture.HEAD_TURN_DOWN to CameraSwitchState(false)
    )

    // Track currently active gesture
    private var activeGesture: String? = null

    private var currentFaceState = FaceProcessingService.FaceState()
    private var lastProcessedState = FaceProcessingService.FaceState()
    private var consecutiveNoFaceFrames = 0
    private val maxNoFaceFrames = 3 // Require 3 consecutive no-face frames before resetting

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val lastProcessingTime = AtomicLong(0)
    private val processingTimeoutMs = 100L
    private val frameSkipThreshold = 100L // Skip frames if processing takes more than 100ms (more stable)

    private var isReceiverRegistered = false
    
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
        lastProcessedState = FaceProcessingService.FaceState()
        // MediaPipe initialization handled by FaceProcessingService
        isInitialized = true
        
        // Register for pause broadcasts
        val filter = IntentFilter().apply {
            addAction(PauseManager.ACTION_PAUSE_STARTED)
            addAction(PauseManager.ACTION_PAUSE_ENDED)
        }
        if (!isReceiverRegistered) {
            androidx.core.content.ContextCompat.registerReceiver(
                context, 
                pauseReceiver, 
                filter, 
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
            isReceiverRegistered = true
        }
        
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
     * Processes the image safely with frame dropping and timeout handling.
     * This must be called after the camera has been bound.
     */
    @OptIn(ExperimentalGetImage::class)
    private fun processImageSafely(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        
        // Frame dropping: skip if previous processing is still ongoing
        if (currentTime - lastProcessingTime.get() < frameSkipThreshold) {
            imageProxy.close()
            return
        }
        
        lastProcessingTime.set(currentTime)
        
        coroutineScope.launch {
            val processingResult = withTimeoutOrNull(processingTimeoutMs) {
                try {
                    imageProxy.image?.let { mediaImage ->

                        val bitmap = imageProxyToBitmap(imageProxy)
                        bitmap?.let {
                            val result = faceProcessingService.processFace(it)
                            if (result != null) {
                                consecutiveNoFaceFrames = 0
                                processFaceResult(result)
                            } else {
                                consecutiveNoFaceFrames++
                                if (consecutiveNoFaceFrames >= maxNoFaceFrames) {
                                    reset()
                                }
                            }
                        } ?: run {
                            consecutiveNoFaceFrames++
                            if (consecutiveNoFaceFrames >= maxNoFaceFrames) {
                                reset()
                            }
                        }
                        imageProxy.close()
                    } ?: imageProxy.close()
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing image", e)
                    imageProxy.close()
                }
            }
            
            if (processingResult == null) {
                Log.w(TAG, "Image processing timed out after ${processingTimeoutMs}ms")
                imageProxy.close()
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val image = imageProxy.image ?: return null
            
            // Handle YUV format (most common from camera)
            if (image.format == ImageFormat.YUV_420_888) {
                val yBuffer = image.planes[0].buffer
                val uBuffer = image.planes[1].buffer
                val vBuffer = image.planes[2].buffer
                
                val ySize = yBuffer.remaining()
                val uSize = uBuffer.remaining()
                val vSize = vBuffer.remaining()
                
                val nv21 = ByteArray(ySize + uSize + vSize)
                yBuffer.get(nv21, 0, ySize)
                vBuffer.get(nv21, ySize, vSize)
                uBuffer.get(nv21, ySize + vSize, uSize)
                
                val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
                val out = ByteArrayOutputStream()
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 85, out)
                val imageBytes = out.toByteArray()
                return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            } else {
                // Fallback for other formats
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error converting ImageProxy to Bitmap", e)
            null
        }
    }

    /**
     * Processes the face detection result.
     */
    private fun processFaceResult(result: FaceProcessingService.FaceDetectionResult) {
        if (!checkInitialization()) {
            return
        }
        
        // Don't process gestures during pause
        val pauseManager = ServiceCore.getPauseManager()
        if (pauseManager.isPaused) {
            Log.d(TAG, "Ignoring face processing - currently paused")
            return
        }
        
        currentFaceState = result.faceState

        // Debug logging for state tracking
        Log.v(
            TAG,
            "Face state - L:${currentFaceState.leftEyeOpen} R:${currentFaceState.rightEyeOpen} " +
                    "Active:${activeGesture} LastL:${lastProcessedState.leftEyeOpen} LastR:${lastProcessedState.rightEyeOpen} " +
                    "HeadX:${currentFaceState.headRotationX} HeadY:${currentFaceState.headRotationY}"
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

            // Handle Head Turn gestures using centralized detection from FaceProcessingService
            listOf(
                CameraSwitchFacialGesture.HEAD_TURN_LEFT,
                CameraSwitchFacialGesture.HEAD_TURN_RIGHT,
                CameraSwitchFacialGesture.HEAD_TURN_UP,
                CameraSwitchFacialGesture.HEAD_TURN_DOWN
            ).forEach { headTurnGesture ->
                val isCurrentlyDetected = result.detectedGestures.contains(headTurnGesture)
                val wasActivelyDetected = gestureStates[headTurnGesture]?.isActive == true
                
                if (isCurrentlyDetected != wasActivelyDetected && switchEventProvider.isFacialGestureAssigned(headTurnGesture)) {
                    handleGestureStateChange(CameraSwitchFacialGesture(headTurnGesture), isCurrentlyDetected)
                }
            }

            // Update last processed state
            lastProcessedState = currentFaceState
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
        lastProcessedState = FaceProcessingService.FaceState()  // Reset the last processed state
        consecutiveNoFaceFrames = 0 // Reset smoothing counter
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
                        // All gestures need to meet their time requirement
                        val requiredTime = faceProcessingService.getGestureTime(gesture.id)
                        val shouldTrigger = timeElapsed >= requiredTime
                        
                        if (shouldTrigger) {
                            if (!scanningManager.checkOngoingTasks()) {
                                scanningManager.performAction(switchEvent.pressAction)
                            }
                            Log.d(
                                TAG,
                                "${gesture.id} completed successfully after ${timeElapsed}ms"
                            )
                        } else {
                            val requiredTime = faceProcessingService.getGestureTime(gesture.id)
                            Log.d(
                                TAG,
                                "${gesture.id} interrupted after ${timeElapsed}ms (needed ${requiredTime}ms)"
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
        if (isReceiverRegistered) {
            try {
                context.unregisterReceiver(pauseReceiver)
                isReceiverRegistered = false
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Receiver was not registered or already unregistered", e)
            }
        }
        stopCamera()
        // MediaPipe cleanup handled by FaceProcessingService
        isInitialized = false
    }

    companion object {
        private const val TAG = "CameraSwitchManager"
        private const val MIN_FACE_SIZE = 0.2f
        
        /**
         * Calculates the head turn threshold based on sensitivity setting
         * Sensitivity 1-10 maps to 5-50 degrees (5° increments)
         */
        fun getHeadTurnThreshold(sensitivity: Int): Float {
            return (sensitivity.coerceIn(1, 10) * 5).toFloat()
        }
    }
}