package com.enaboapps.switchify.service.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleOwner
import com.enaboapps.switchify.service.face.FaceProcessingService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Foreground service dedicated to camera capture and MediaPipe processing.
 * Runs independently of AccessibilityService for better reliability and Android compliance.
 */
class CameraForegroundService : Service() {

    private val binder = CameraServiceBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // Camera components
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraExecutor: ExecutorService? = null

    // Processing components
    private var faceProcessingService: FaceProcessingService? = null
    private var yuvToRgbConverter: YuvToRgbConverter? = null

    // State management
    private var isInitialized = false
    private var isProcessing = false
    private val processingMutex = Mutex()
    private var currentLifecycleOwner: LifecycleOwner? = null

    // Callbacks
    private var frameProcessingCallback: ((Bitmap) -> Unit)? = null
    private var gestureDetectedCallback: ((String) -> Unit)? = null
    private var faceResultCallback: ((FaceProcessingService.FaceDetectionResult?) -> Unit)? = null

    // Backpressure monitoring
    private var lastFrameTime = 0L
    private var droppedFrameCount = 0L
    private var processedFrameCount = 0L

    // Performance optimization
    private var lastProcessingStartTime = 0L
    private var lastFrameProcessedTime = 0L
    private var isMediaPipeProcessing = false
    private val mediaPipeMutex = Mutex()
    private var averageProcessingTime = 0L
    private var processedFramesForAverage = 0

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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "Service destroyed")
        cleanup()
        super.onDestroy()
    }

    /**
     * Initialize the camera service components
     */
    fun initialize(): Boolean {
        return try {
            if (isInitialized) {
                Log.d(TAG, "Service already initialized")
                return true
            }

            // Initialize YUV to RGB converter
            yuvToRgbConverter = YuvToRgbConverter()

            // Initialize FaceProcessingService
            faceProcessingService = FaceProcessingService(this)

            // Initialize camera executor
            cameraExecutor = Executors.newSingleThreadExecutor()

            isInitialized = true
            Log.i(TAG, "Camera service initialized successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize camera service", e)
            false
        }
    }

    /**
     * Start camera capture and processing
     */
    fun startCamera(lifecycleOwner: LifecycleOwner): Boolean {
        if (!isInitialized) {
            Log.e(TAG, "Service not initialized")
            return false
        }

        return try {
            currentLifecycleOwner = lifecycleOwner

            serviceScope.launch(Dispatchers.Main) {
                val cameraProviderFuture =
                    ProcessCameraProvider.getInstance(this@CameraForegroundService)
                cameraProvider = cameraProviderFuture.get()

                setupImageAnalysis()
                bindCameraUseCases(lifecycleOwner)

                isProcessing = true
                Log.i(TAG, "Camera started successfully")
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start camera", e)
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
                    cameraProvider?.unbindAll()
                    cameraProvider = null
                    imageAnalysis = null
                    currentLifecycleOwner = null

                    Log.i(TAG, "Camera stopped")
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
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(
                android.util.Size(
                    IMAGE_ANALYSIS_TARGET_RESOLUTION_WIDTH,
                    IMAGE_ANALYSIS_TARGET_RESOLUTION_HEIGHT
                )
            )
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

            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                imageAnalysis
            )

            Log.d(TAG, "Camera use cases bound successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
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
            }
        }
    }

    /**
     * Clean up all resources
     */
    private fun cleanup() {
        try {
            isProcessing = false
            isInitialized = false

            // Stop camera
            serviceScope.launch(Dispatchers.Main) {
                cameraProvider?.unbindAll()
            }

            // Clean up processing components  
            faceProcessingService =
                null  // FaceProcessingService cleans up automatically in background thread
            yuvToRgbConverter?.cleanup()

            // Shut down executor
            cameraExecutor?.shutdown()

            // Cancel coroutine scope
            serviceScope.cancel()

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

            Log.i(TAG, "Camera service cleaned up")

        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}