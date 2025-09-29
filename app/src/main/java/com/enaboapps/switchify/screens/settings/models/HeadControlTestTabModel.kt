package com.enaboapps.switchify.screens.settings.models

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.view.Surface
import android.view.WindowManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.viewModelScope
import com.enaboapps.switchify.service.face.FaceProcessingService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class HeadControlTestTabModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "HeadControlTestTabModel"
    }

    private val faceProcessingService = FaceProcessingService(application)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        // Configure camera orientation for coordinate normalization
        configureCameraOrientation()
    }

    // Head pose state flows
    private val _headRotationX = MutableStateFlow(0f)
    val headRotationX: StateFlow<Float> = _headRotationX.asStateFlow()

    private val _headRotationY = MutableStateFlow(0f)
    val headRotationY: StateFlow<Float> = _headRotationY.asStateFlow()

    private val _isFaceDetected = MutableStateFlow(false)
    val isFaceDetected: StateFlow<Boolean> = _isFaceDetected.asStateFlow()

    // Camera setup
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var processCameraProvider: ProcessCameraProvider? = null
    private val isProcessing = AtomicBoolean(false)

    fun setupCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val context = getApplication<Application>().applicationContext
        cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture?.addListener({
            try {
                processCameraProvider = cameraProviderFuture?.get()
                bindCameraUseCases(previewView, lifecycleOwner)
            } catch (e: Exception) {
                // Handle camera setup error
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bindCameraUseCases(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val cameraProvider = processCameraProvider ?: return

        // Unbind all use cases before rebinding
        cameraProvider.unbindAll()

        // Preview use case
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)

        // Image analysis use case for face detection
        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(getApplication())) { imageProxy ->
            processImageProxy(imageProxy)
        }

        // Camera selector (front camera)
        val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

        try {
            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessing.getAndSet(true)) {
            imageProxy.close()
            return
        }

        viewModelScope.launch {
            try {
                val bitmap = imageProxyToBitmap(imageProxy)
                if (bitmap != null) {
                    // Process with FaceProcessingService
                    faceProcessingService.processFace(
                        bitmap,
                        System.currentTimeMillis()
                    ) { result ->
                        // Update head pose data on main thread
                        mainHandler.post {
                            if (result != null) {
                                _headRotationX.value = result.faceState.headRotationX
                                _headRotationY.value = result.faceState.headRotationY
                                _isFaceDetected.value = true
                            } else {
                                _isFaceDetected.value = false
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing image", e)
            } finally {
                isProcessing.set(false)
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
                yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
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

    fun resetHeadPose() {
        _headRotationX.value = 0f
        _headRotationY.value = 0f
    }

    /**
     * Get coordinate system information
     */
    fun getCoordinateSystemInfo(): String {
        return faceProcessingService.getCoordinateSystemInfo()
    }

    /**
     * Clear coordinate system cache for testing
     */
    fun clearCoordinateSystemCache() {
        faceProcessingService.clearCoordinateSystemCache()
    }

    /**
     * Set custom coordinate system for testing
     */
    fun setCustomCoordinateSystem(pitchInverted: Boolean, yawInverted: Boolean) {
        faceProcessingService.setCustomCoordinateSystem(pitchInverted, yawInverted)
    }

    /**
     * Configure face processing service with current device rotation and camera orientation
     */
    private fun configureCameraOrientation() {
        // Get device rotation from display
        val context = getApplication<Application>().applicationContext
        val rotation = try {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0
        } catch (e: Exception) {
            // Fallback to portrait if we can't get rotation
            Surface.ROTATION_0
        }

        // Configure face processing service (front camera by default)
        faceProcessingService.setCameraOrientation(rotation, frontCamera = true)
    }

    override fun onCleared() {
        super.onCleared()
        faceProcessingService.close()
        processCameraProvider?.unbindAll()
    }
}