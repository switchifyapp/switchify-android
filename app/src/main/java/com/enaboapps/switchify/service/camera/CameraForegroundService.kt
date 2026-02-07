package com.enaboapps.switchify.service.camera

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.camera2.CameraManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.face.FaceProcessingService
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service dedicated to camera capture and MediaPipe processing.
 * Runs independently of AccessibilityService for better reliability and Android compliance.
 */
class CameraForegroundService : Service(), CameraLifecycle {

    private val binder = CameraServiceBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Wake lock to keep CPU active during camera processing
    private var wakeLock: PowerManager.WakeLock? = null

    // Camera components
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService? = null
    private var boundCamera: Camera? = null

    // Processing components
    private var faceProcessingService: FaceProcessingService? = null
    private var yuvToRgbConverter: YuvToRgbConverter? = null

    // State management
    private val _lifecycleState = MutableStateFlow(CameraLifecycle.State.UNINITIALIZED)
    override val lifecycleState: StateFlow<CameraLifecycle.State> = _lifecycleState.asStateFlow()
    private val lifecycleMutex = Mutex()
    private var isInitialized = false
    private var isProcessing = false
    private val processingMutex = Mutex()
    private var currentLifecycleOwner: LifecycleOwner? = null
    private var isPausedForConflict = false
    private var retryAttempt = 0

    // Callbacks
    private var frameProcessingCallback: ((Bitmap) -> Unit)? = null
    private var gestureDetectedCallback: ((String) -> Unit)? = null
    private var faceResultCallback: ((FaceProcessingService.FaceDetectionResult?) -> Unit)? = null

    // Backpressure monitoring
    private var lastFrameTime = 0L
    private var droppedFrameCount = 0L
    private var processedFrameCount = 0L
    private var lastBackpressureTelemetryTime = 0L
    private var frameProcessingErrorCount = 0L
    private var lastFrameErrorTelemetryTime = 0L

    // Performance optimization
    private var lastProcessingStartTime = 0L
    private var lastFrameProcessedTime = 0L
    private var isMediaPipeProcessing = false
    private val mediaPipeMutex = Mutex()
    private var averageProcessingTime = 0L
    private var processedFramesForAverage = 0
    private var watchdogJob: kotlinx.coroutines.Job? = null
    private var retryJob: kotlinx.coroutines.Job? = null
    private var cameraStateObserver: Observer<CameraState>? = null
    private var cameraManager: CameraManager? = null
    private var availabilityCallback: CameraManager.AvailabilityCallback? = null

    companion object {
        private const val TAG = "CameraForegroundService"
        private const val NOTIFICATION_ID = 2001
        private const val CHANNEL_ID = "camera_processing_channel"
        private const val CHANNEL_NAME = "Camera Processing"

        // Processing configuration
        private const val IMAGE_ANALYSIS_TARGET_RESOLUTION_WIDTH = 640
        private const val IMAGE_ANALYSIS_TARGET_RESOLUTION_HEIGHT = 480

        // Performance tuning
        private const val TARGET_FPS = 15 // Reduced from 30fps to reduce processing load
        private const val MIN_FRAME_INTERVAL_MS = 1000L / TARGET_FPS // ~67ms between frames
        private const val MAX_PROCESSING_TIME_MS =
            150L // Skip if processing takes longer than 150ms
    }

    /**
     * Binder class for clients to access the service
     */
    inner class CameraServiceBinder : Binder() {
        fun getService(): CameraForegroundService = this@CameraForegroundService
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "Service bound")
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")

        // Check for CAMERA permission before starting foreground service
        // Required for Android API 34+ when using foregroundServiceType="camera"
        if (!hasCameraPermission()) {
            Log.e(TAG, "CAMERA permission not granted - cannot start camera foreground service")
            Logger.log(
                LogEvent.CameraPermissionMissing,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "camera_permission_not_granted"
                )
            )
            stopSelf()
            return
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Acquire wake lock to keep CPU active during camera processing
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Switchify::CameraProcessing").apply {
                acquire(30 * 60 * 1000L) // 30 minutes timeout for safety
                Log.d(TAG, "Wake lock acquired - CPU will stay active for camera processing")
            }
        }

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        availabilityCallback = object : CameraManager.AvailabilityCallback() {
            override fun onCameraAvailable(cameraId: String) {
                if (isPausedForConflict) scheduleRetry()
            }
        }
        cameraManager?.registerAvailabilityCallback(availabilityCallback!!, null)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")

        // Release wake lock
        wakeLock?.let { wl ->
            if (wl.isHeld) {
                wl.release()
                Log.d(TAG, "Wake lock released")
            }
            wakeLock = null
        }

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                cleanup()
            } finally {
                serviceScope.cancel()
            }
        }
        super.onDestroy()
    }

    /**
     * Initialize the camera service components asynchronously.
     */
    override suspend fun initialize(): Boolean = lifecycleMutex.withLock {
        when (lifecycleState.value) {
            CameraLifecycle.State.READY -> {
                Log.d(TAG, "Service already initialized")
                return true
            }

            CameraLifecycle.State.INITIALIZING -> {
                Log.d(TAG, "Initialization already in progress")
                return false
            }

            CameraLifecycle.State.DESTROYED -> {
                Log.w(TAG, "Cannot initialize destroyed service")
                return false
            }

            else -> {
                // Proceed with initialization
            }
        }

        return try {
            _lifecycleState.value = CameraLifecycle.State.INITIALIZING
            Log.d(TAG, "Initializing camera service")
            Logger.log(
                LogEvent.CameraStartAttempt,
                data = mapOf(
                    "result" to "started",
                    "reason" to "initialize"
                )
            )

            if (isInitialized) {
                _lifecycleState.value = CameraLifecycle.State.READY
                return true
            }

            // Initialize YUV to RGB converter
            yuvToRgbConverter = YuvToRgbConverter()

            // Initialize FaceProcessingService
            faceProcessingService = FaceProcessingService(this)

            // Configure camera orientation for coordinate normalization
            configureCameraOrientation()

            // Initialize camera executor
            cameraExecutor = Executors.newSingleThreadExecutor()

            isInitialized = true
            _lifecycleState.value = CameraLifecycle.State.READY
            Log.i(TAG, "Camera service initialized successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera service", e)
            Logger.log(
                LogEvent.CameraStartFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "initialize_exception"
                ),
                throwable = e
            )
            _lifecycleState.value = CameraLifecycle.State.ERROR
            false
        }
    }

    /**
     * Start camera capture and processing
     */
    fun startCamera(lifecycleOwner: LifecycleOwner): Boolean {
        Logger.log(
            LogEvent.CameraStartAttempt,
            data = mapOf(
                "result" to "started",
                "reason" to "start_camera"
            )
        )

        if (!isInitialized) {
            Log.e(TAG, "Service not initialized")
            Logger.log(
                LogEvent.CameraStartFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "service_not_initialized"
                )
            )
            return false
        }

        return try {
            currentLifecycleOwner = lifecycleOwner

            val cameraProviderFuture =
                ProcessCameraProvider.getInstance(this@CameraForegroundService)
            cameraProviderFuture.addListener({
                serviceScope.launch(Dispatchers.Main) {
                    try {
                        cameraProvider = cameraProviderFuture.get()
                        setupImageAnalysis()
                        bindCameraUseCases(lifecycleOwner)
                        isProcessing = true
                        Log.i(TAG, "Camera started successfully")
                        startWatchdog()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start camera in listener", e)
                        Logger.log(
                            LogEvent.CameraStartFailed,
                            data = mapOf(
                                "result" to "failure",
                                "reason" to "start_listener_exception"
                            ),
                            throwable = e
                        )
                    }
                }
            }, { serviceScope.launch(Dispatchers.Main) { it.run() } })

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera", e)
            Logger.log(
                LogEvent.CameraStartFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "start_exception"
                ),
                throwable = e
            )
            false
        }
    }

    /**
     * Stop camera capture and processing
     */
    fun stopCamera() {
        try {
            serviceScope.launch(Dispatchers.Main) {
                processingMutex.withLock {
                    isProcessing = false
                    isPausedForConflict = false
                    cameraProvider?.unbindAll()
                    cameraProvider = null
                    imageAnalysis = null
                    currentLifecycleOwner = null
                    boundCamera?.cameraInfo?.cameraState?.removeObserver(
                        cameraStateObserver ?: return@launch
                    )
                    boundCamera = null
                    cameraStateObserver = null
                    retryJob?.cancel()
                    retryJob = null
                    watchdogJob?.cancel()
                    watchdogJob = null

                    Log.i(TAG, "Camera stopped")
                    Logger.log(
                        LogEvent.CameraStop,
                        data = mapOf(
                            "result" to "success",
                            "reason" to "explicit_stop"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping camera", e)
        }
    }

    /**
     * Set callback for processed frame bitmaps
     */
    fun setFrameProcessingCallback(callback: (Bitmap) -> Unit) {
        frameProcessingCallback = callback
    }

    /**
     * Set callback for detected gestures
     */
    fun setGestureDetectedCallback(callback: (String) -> Unit) {
        gestureDetectedCallback = callback
    }

    /**
     * Set callback for face detection results
     */
    fun setFaceResultCallback(callback: (FaceProcessingService.FaceDetectionResult?) -> Unit) {
        faceResultCallback = callback
    }

    /**
     * Check if camera is currently processing
     */
    fun isProcessing(): Boolean = isProcessing

    /**
     * Set up image analysis pipeline
     */
    private fun setupImageAnalysis() {
        val resolutionSelector = androidx.camera.core.resolutionselector.ResolutionSelector.Builder()
            .setResolutionFilter { supportedSizes, _ ->
                supportedSizes.filter { size ->
                    size.width <= IMAGE_ANALYSIS_TARGET_RESOLUTION_WIDTH &&
                    size.height <= IMAGE_ANALYSIS_TARGET_RESOLUTION_HEIGHT
                }
            }
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setImageQueueDepth(1)
            .build()

        imageAnalysis?.setAnalyzer(cameraExecutor!!) { imageProxy ->
            processFrame(imageProxy)
        }
    }

    /**
     * Bind camera use cases to lifecycle
     */
    private fun bindCameraUseCases(lifecycleOwner: LifecycleOwner) {
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            cameraProvider?.unbindAll()

            boundCamera = cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )

            Log.d(TAG, "Camera use cases bound successfully")
            observeCameraState(lifecycleOwner)
            isPausedForConflict = false
            retryAttempt = 0
            retryJob?.cancel()
            retryJob = null
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_camera_recovered,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
            Logger.log(
                LogEvent.CameraBindFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "bind_use_cases_exception"
                ),
                throwable = e
            )
            handleRecoverableIssue()
        }
    }

    private fun observeCameraState(lifecycleOwner: LifecycleOwner) {
        cameraStateObserver?.let { boundCamera?.cameraInfo?.cameraState?.removeObserver(it) }
        val observer = Observer<CameraState> { state ->
            val error = state.error
            if (error != null) {
                when (error.code) {
                    CameraState.ERROR_CAMERA_IN_USE,
                    CameraState.ERROR_MAX_CAMERAS_IN_USE,
                    CameraState.ERROR_OTHER_RECOVERABLE_ERROR -> onCameraInUse()

                    CameraState.ERROR_CAMERA_DISABLED,
                    CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED,
                    CameraState.ERROR_STREAM_CONFIG -> onCameraFatal()

                    else -> onCameraInUse()
                }
            }
        }
        cameraStateObserver = observer
        boundCamera?.cameraInfo?.cameraState?.observe(lifecycleOwner, observer)
    }

    private fun onCameraInUse() {
        if (!isPausedForConflict) {
            ServiceMessageHUD.instance.showMessage(
                R.string.hud_camera_unavailable_external_app,
                ServiceMessageHUD.MessageType.DISAPPEARING
            )
            pauseProcessing()
            scheduleRetry()
        }
    }

    private fun onCameraFatal() {
        ServiceMessageHUD.instance.showMessage(
            R.string.hud_camera_access_error,
            ServiceMessageHUD.MessageType.DISAPPEARING
        )
        pauseProcessing()
    }

    private fun pauseProcessing() {
        serviceScope.launch(Dispatchers.Main) {
            isProcessing = false
            isPausedForConflict = true
            cameraProvider?.unbindAll()
        }
    }

    private fun scheduleRetry() {
        if (retryJob != null) return
        val delayMs = kotlin.math.min(30000L, 1000L shl retryAttempt.coerceAtMost(5))
        retryJob = serviceScope.launch(Dispatchers.Main) {
            delay(delayMs)
            retryJob = null
            val owner = currentLifecycleOwner ?: return@launch
            try {
                if (cameraProvider == null) {
                    val future = ProcessCameraProvider.getInstance(this@CameraForegroundService)
                    cameraProvider = future.get()
                }
                setupImageAnalysis()
                bindCameraUseCases(owner)
                isProcessing = true
            } catch (e: Exception) {
                retryAttempt++
                handleRecoverableIssue()
            }
        }
    }

    private fun handleRecoverableIssue() {
        isPausedForConflict = true
        scheduleRetry()
    }

    private fun startWatchdog() {
        if (watchdogJob != null) return
        watchdogJob = serviceScope.launch(Dispatchers.Default) {
            while (true) {
                delay(5000)
                if (isProcessing && System.currentTimeMillis() - lastFrameProcessedTime > 5000) {
                    onCameraInUse()
                }
            }
        }
    }

    /**
     * Process individual camera frame with backpressure protection
     */
    @OptIn(ExperimentalGetImage::class)
    private fun processFrame(imageProxy: ImageProxy) {
        if (!isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image ?: run {
            imageProxy.close()
            return
        }

        val currentTime = System.currentTimeMillis()

        // Frame rate throttling - skip frames if processing too frequently
        if (currentTime - lastFrameProcessedTime < MIN_FRAME_INTERVAL_MS) {
            droppedFrameCount++
            imageProxy.close()
            return
        }

        // Try to acquire mutex immediately - if busy, drop frame to prevent backlog
        val acquired = processingMutex.tryLock()
        if (!acquired) {
            droppedFrameCount++
            if (currentTime - lastFrameTime > 5000L) { // Log every 5 seconds
                Log.w(
                    TAG,
                    "Backpressure detected - dropped $droppedFrameCount frames, processed $processedFrameCount frames"
                )
                if (currentTime - lastBackpressureTelemetryTime > 5000L) {
                    Logger.log(
                        LogEvent.CameraBackpressureDetected,
                        data = mapOf(
                            "result" to "degraded",
                            "reason" to "processing_mutex_busy",
                            "dropped_frames" to droppedFrameCount,
                            "processed_frames" to processedFrameCount,
                            "interval_ms" to 5000
                        )
                    )
                    lastBackpressureTelemetryTime = currentTime
                }
                lastFrameTime = currentTime
                droppedFrameCount = 0
                processedFrameCount = 0
            }
            imageProxy.close()
            return
        }

        lastFrameProcessedTime = currentTime

        serviceScope.launch {
            try {
                if (!isProcessing) {
                    imageProxy.close()
                    return@launch
                }

                try {
                    processedFrameCount++

                    // Skip processing if MediaPipe is still busy (prevent queue buildup)
                    val canProcessMediaPipe = mediaPipeMutex.tryLock()
                    if (!canProcessMediaPipe) {
                        Log.d(TAG, "Skipping MediaPipe processing - still busy with previous frame")
                        return@launch
                    }

                    try {
                        lastProcessingStartTime = System.currentTimeMillis()

                        // Only convert YUV to RGB when MediaPipe is actually ready to process
                        // This avoids expensive conversion operations when they would be wasted
                        faceProcessingService?.let { faceService ->
                            // Convert YUV to RGB bitmap only when MediaPipe can process it
                            val bitmap = yuvToRgbConverter?.convertYuvToBitmap(mediaImage)

                            if (bitmap != null) {
                                // Notify frame callback (optional - can disable for performance)
                                // frameProcessingCallback?.invoke(bitmap)

                                // Process with MediaPipe asynchronously
                                faceService.processFace(
                                    bitmap = bitmap,
                                    timestampMs = imageProxy.imageInfo.timestamp / 1_000_000L
                                ) { result ->
                                    // Process result on background thread to avoid blocking
                                    serviceScope.launch(Dispatchers.Default) {
                                        try {
                                            // Track processing performance
                                            val endTime = System.currentTimeMillis()
                                            val processingTime = endTime - lastProcessingStartTime
                                            updateProcessingTimeStats(processingTime)

                                            // Pass result to AccessibilityService
                                            faceResultCallback?.invoke(result)
                                        } catch (e: Exception) {
                                            Log.e(TAG, "Error in gesture callback processing", e)
                                        }
                                    }
                                }
                            } else {
                                Log.w(TAG, "YUV to RGB conversion failed, skipping frame")
                            }
                        } ?: run {
                            Log.w(TAG, "FaceProcessingService not available, skipping frame")
                        }
                    } finally {
                        mediaPipeMutex.unlock()
                    }

                } catch (e: Exception) {
                    Log.e(TAG, "Error processing frame", e)
                    frameProcessingErrorCount++
                    if (currentTime - lastFrameErrorTelemetryTime > 5000L) {
                        Logger.log(
                            LogEvent.CameraFrameProcessingFailed,
                            data = mapOf(
                                "result" to "failure",
                                "reason" to "frame_processing_exception",
                                "error_count" to frameProcessingErrorCount,
                                "interval_ms" to 5000
                            ),
                            throwable = e
                        )
                        lastFrameErrorTelemetryTime = currentTime
                        frameProcessingErrorCount = 0
                    }
                } finally {
                    imageProxy.close()
                }
            } finally {
                processingMutex.unlock()
            }
        }
    }

    /**
     * Create notification channel for foreground service
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Camera processing for accessibility features"
                setShowBadge(false)
            }

            val notificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Create persistent notification for foreground service
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Switchify Camera")
            .setContentText("Processing camera input for accessibility gestures")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }

    /**
     * Update processing time statistics for performance monitoring
     */
    private fun updateProcessingTimeStats(processingTime: Long) {
        processedFramesForAverage++

        // Calculate rolling average of processing time
        if (processedFramesForAverage == 1) {
            averageProcessingTime = processingTime
        } else {
            // Use exponential moving average for smoother results
            averageProcessingTime = (averageProcessingTime * 0.9 + processingTime * 0.1).toLong()
        }

        // Log performance stats periodically
        if (processedFramesForAverage % 100 == 0) {
            Log.i(
                TAG,
                "Performance stats: Avg processing time: ${averageProcessingTime}ms, Processed frames: $processedFramesForAverage"
            )

            // Warn if processing is consistently slow
            if (averageProcessingTime > MAX_PROCESSING_TIME_MS) {
                Log.w(
                    TAG,
                    "Processing time averaging ${averageProcessingTime}ms exceeds target ${MAX_PROCESSING_TIME_MS}ms"
                )
                Logger.log(
                    LogEvent.CameraFrameProcessingSlow,
                    data = mapOf(
                        "result" to "degraded",
                        "reason" to "average_processing_time_exceeded",
                        "average_processing_ms" to averageProcessingTime,
                        "target_processing_ms" to MAX_PROCESSING_TIME_MS,
                        "processed_frames" to processedFramesForAverage
                    )
                )
            }
        }
    }

    /**
     * Configure face processing service with current device rotation and camera orientation
     */
    private fun configureCameraOrientation() {
        // Get device rotation from display
        val rotation = try {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0
        } catch (e: Exception) {
            // Fallback to portrait if we can't get rotation
            Surface.ROTATION_0
        }

        // Configure face processing service (front camera by default)
        faceProcessingService?.setCameraOrientation(rotation, frontCamera = true)
        Log.d(TAG, "Camera orientation configured for coordinate normalization")
    }

    /**
     * Check if the CAMERA permission is granted.
     * Required for Android API 34+ when using foregroundServiceType="camera".
     */
    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Clean up all resources asynchronously.
     */
    override suspend fun cleanup(): Boolean = lifecycleMutex.withLock {
        when (lifecycleState.value) {
            CameraLifecycle.State.DESTROYED -> {
                Log.d(TAG, "Service already cleaned up")
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
            Log.d(TAG, "Cleaning up camera service")

            isProcessing = false
            isInitialized = false

            // Stop camera
            serviceScope.launch(Dispatchers.Main) {
                cameraProvider?.unbindAll()
            }.join()

            // Clean up processing components  
            faceProcessingService =
                null  // FaceProcessingService cleans up automatically in background thread
            yuvToRgbConverter?.cleanup()

            // Shut down executor
            cameraExecutor?.shutdown()

            // Coroutine scope will be cancelled in onDestroy

            // Clear references
            cameraProvider = null
            imageAnalysis = null
            cameraExecutor = null
            faceProcessingService = null
            yuvToRgbConverter = null
            currentLifecycleOwner = null
            frameProcessingCallback = null
            gestureDetectedCallback = null
            faceResultCallback = null
            retryJob?.cancel()
            watchdogJob?.cancel()
            cameraStateObserver = null
            availabilityCallback?.let { cameraManager?.unregisterAvailabilityCallback(it) }
            availabilityCallback = null
            cameraManager = null

            // Ensure wake lock is released during cleanup
            wakeLock?.let { wl ->
                if (wl.isHeld) {
                    wl.release()
                    Log.d(TAG, "Wake lock released during cleanup")
                }
                wakeLock = null
            }

            _lifecycleState.value = CameraLifecycle.State.DESTROYED
            Log.i(TAG, "Camera service cleaned up successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
            _lifecycleState.value = CameraLifecycle.State.ERROR
            false
        }
    }
}
