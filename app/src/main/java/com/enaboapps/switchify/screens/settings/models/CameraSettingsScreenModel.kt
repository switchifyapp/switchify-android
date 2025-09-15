package com.enaboapps.switchify.screens.settings.models

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
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
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.face.FaceProcessingService
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class CameraSettingsScreenModel(private val context: Context) : ViewModel() {

    private val preferenceManager = PreferenceManager(context)
    private val faceProcessingService = FaceProcessingService(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _detectedExpressions = MutableStateFlow<Set<String>>(emptySet())
    val detectedExpressions: StateFlow<Set<String>> = _detectedExpressions.asStateFlow()

    private val _isFaceDetected = MutableStateFlow(false)
    val isFaceDetected: StateFlow<Boolean> = _isFaceDetected.asStateFlow()

    // Gesture timing state for testing actual configured thresholds
    private data class GestureState(
        var isActive: Boolean = false,
        var startTime: Long = 0
    )

    private val gestureStates = mutableMapOf(
        CameraSwitchFacialGesture.SMILE to GestureState(),
        CameraSwitchFacialGesture.LEFT_WINK to GestureState(),
        CameraSwitchFacialGesture.RIGHT_WINK to GestureState(),
        CameraSwitchFacialGesture.BLINK to GestureState(),
        CameraSwitchFacialGesture.MOUTH_OPEN to GestureState()
    )

    private var lastProcessedState = FaceProcessingService.FaceState()
    private var currentFaceState = FaceProcessingService.FaceState()

    // Threshold state flows for UI
    private val _smileTime = MutableStateFlow(getSmileTime())
    val smileTime: StateFlow<Long> = _smileTime.asStateFlow()

    private val _leftWinkTime = MutableStateFlow(getLeftWinkTime())
    val leftWinkTime: StateFlow<Long> = _leftWinkTime.asStateFlow()

    private val _rightWinkTime = MutableStateFlow(getRightWinkTime())
    val rightWinkTime: StateFlow<Long> = _rightWinkTime.asStateFlow()

    private val _blinkTime = MutableStateFlow(getBlinkTime())
    val blinkTime: StateFlow<Long> = _blinkTime.asStateFlow()

    private val _mouthOpenTime = MutableStateFlow(getMouthOpenTime())
    val mouthOpenTime: StateFlow<Long> = _mouthOpenTime.asStateFlow()

    // Real-time blendshape scores for progress bars
    private val _smileScore = MutableStateFlow(0f)
    val smileScore: StateFlow<Float> = _smileScore.asStateFlow()

    private val _leftWinkScore = MutableStateFlow(0f)
    val leftWinkScore: StateFlow<Float> = _leftWinkScore.asStateFlow()

    private val _rightWinkScore = MutableStateFlow(0f)
    val rightWinkScore: StateFlow<Float> = _rightWinkScore.asStateFlow()

    private val _blinkScore = MutableStateFlow(0f)
    val blinkScore: StateFlow<Float> = _blinkScore.asStateFlow()

    private val _mouthOpenScore = MutableStateFlow(0f)
    val mouthOpenScore: StateFlow<Float> = _mouthOpenScore.asStateFlow()

    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null
    private val isProcessing = AtomicBoolean(false)

    @Volatile
    private var isCleanedUp = false

    private companion object {
        const val TAG = "CameraSettingsScreenModel"
        const val MIN_FACE_SIZE = 0.2f
    }

    init {
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

    fun startCamera(lifecycleOwner: LifecycleOwner) {
        cameraProviderFuture = ProcessCameraProvider.getInstance(context).also { future ->
            future.addListener({
                try {
                    cameraProvider = future.get()
                    bindPreviewAndAnalysis(lifecycleOwner)
                    Log.d(TAG, "Camera started successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start camera", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    fun bindPreview(previewView: PreviewView) {
        this.previewView = previewView
    }

    @OptIn(ExperimentalGetImage::class)
    private fun bindPreviewAndAnalysis(lifecycleOwner: LifecycleOwner) {
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        // Create preview
        preview = Preview.Builder()
            .build()
            .apply {
                previewView?.let { surfaceProvider = it.surfaceProvider }
            }

        // Create image analyzer
        imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .apply {
                setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
                    processImage(imageProxy)
                }
            }

        try {
            cameraProvider?.unbindAll()
            cameraProvider?.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            Log.d(TAG, "Camera bound successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        // Skip processing if cleaned up or already processing another frame
        if (isCleanedUp || isProcessing.getAndSet(true)) {
            imageProxy.close()
            return
        }

        try {
            imageProxy.image?.let { mediaImage ->
                val bitmap = imageProxyToBitmap(imageProxy)
                bitmap?.let {
                    faceProcessingService.processFace(it) { result ->
                        // Post to main thread to ensure UI updates are handled correctly
                        mainHandler.post {
                            if (result != null) {
                                _isFaceDetected.value = true
                                processFaceResult(result)
                            } else {
                                _isFaceDetected.value = false
                                _detectedExpressions.value = emptySet()
                            }
                        }
                    }
                } ?: run {
                    _isFaceDetected.value = false
                    _detectedExpressions.value = emptySet()
                }

                isProcessing.set(false)
                imageProxy.close()
            } ?: run {
                isProcessing.set(false)
                imageProxy.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing image", e)
            isProcessing.set(false)
            imageProxy.close()
        }
    }

    private fun processFaceResult(result: FaceProcessingService.FaceDetectionResult) {
        currentFaceState = result.faceState

        // Update real-time blendshape scores for UI progress bars
        _smileScore.value = result.blendshapeScores.smileScore
        _leftWinkScore.value = result.blendshapeScores.leftEyeCloseScore
        _rightWinkScore.value = result.blendshapeScores.rightEyeCloseScore
        _blinkScore.value = result.blendshapeScores.blinkScore
        _mouthOpenScore.value = 1f - result.blendshapeScores.mouthCloseScore // Inverted for mouth open

        val validatedGestures = mutableSetOf<String>()

        // Only process if the state has changed
        if (currentFaceState != lastProcessedState) {
            // Handle Smile
            if (currentFaceState.isSmiling != lastProcessedState.isSmiling) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture.SMILE,
                    currentFaceState.isSmiling,
                    validatedGestures
                )
            }

            // Handle Left Wink (only when right eye is open)
            if (currentFaceState.leftEyeOpen != lastProcessedState.leftEyeOpen && currentFaceState.rightEyeOpen) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture.LEFT_WINK,
                    !currentFaceState.leftEyeOpen,
                    validatedGestures
                )
            }

            // Handle Right Wink (only when left eye is open)
            if (currentFaceState.rightEyeOpen != lastProcessedState.rightEyeOpen && currentFaceState.leftEyeOpen) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture.RIGHT_WINK,
                    !currentFaceState.rightEyeOpen,
                    validatedGestures
                )
            }

            // Handle Blink
            val eyesClosed = !currentFaceState.leftEyeOpen && !currentFaceState.rightEyeOpen
            val wereEyesClosed = !lastProcessedState.leftEyeOpen && !lastProcessedState.rightEyeOpen
            if (eyesClosed != wereEyesClosed) {
                handleGestureStateChange(
                    CameraSwitchFacialGesture.BLINK,
                    eyesClosed,
                    validatedGestures
                )
            }

            // Handle all facial gestures with timing
            result.detectedGestures.forEach { gesture ->
                if (gestureStates.containsKey(gesture)) {
                    val wasActive = gestureStates[gesture]?.isActive == true
                    if (!wasActive) {
                        handleGestureStateChange(gesture, true, validatedGestures)
                    }
                }
            }

            // Stop gestures that are no longer detected
            gestureStates.keys.forEach { gesture ->
                if (gestureStates[gesture]?.isActive == true && gesture !in result.detectedGestures) {
                    handleGestureStateChange(gesture, false, validatedGestures)
                }
            }

            lastProcessedState = currentFaceState
        }

        // Check currently active timed gestures to see if they've been held long enough
        gestureStates.forEach { (gestureId, state) ->
            if (state.isActive && state.startTime > 0) {
                val timeElapsed = System.currentTimeMillis() - state.startTime
                val requiredTime = faceProcessingService.getGestureTime(gestureId)

                // Add gesture to display if it's been held long enough
                if (timeElapsed >= requiredTime) {
                    validatedGestures.add(gestureId)
                }
            }
        }

        _detectedExpressions.value = validatedGestures
    }

    private fun handleGestureStateChange(
        gestureId: String,
        isStarting: Boolean,
        validatedGestures: MutableSet<String>
    ) {
        val state = gestureStates[gestureId] ?: return

        if (isStarting) {
            // Start timing the gesture
            state.isActive = true
            state.startTime = System.currentTimeMillis()
        } else {
            // Stop timing the gesture
            if (state.isActive) {
                val timeElapsed = System.currentTimeMillis() - state.startTime
                val requiredTime = faceProcessingService.getGestureTime(gestureId)

                // Only add to validated gestures if held long enough
                if (timeElapsed >= requiredTime) {
                    validatedGestures.add(gestureId)
                }
            }
            state.isActive = false
            state.startTime = 0
        }
    }

    fun cleanup() {
        try {
            isCleanedUp = true
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageAnalyzer = null
            preview = null
            previewView = null
            faceProcessingService.close()

            // Reset gesture states
            gestureStates.forEach { (_, state) ->
                state.isActive = false
                state.startTime = 0
            }
            lastProcessedState = FaceProcessingService.FaceState()

            Log.d(TAG, "Camera cleaned up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup camera", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanup()
    }

    // Preference getter methods
    fun getSmileTime(): Long {
        return preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_SMILE_TIME,
            500L
        )
    }

    fun getLeftWinkTime(): Long {
        return preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_LEFT_WINK_TIME,
            300L
        )
    }

    fun getRightWinkTime(): Long {
        return preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_RIGHT_WINK_TIME,
            300L
        )
    }

    fun getBlinkTime(): Long {
        return preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_BLINK_TIME,
            400L
        )
    }

    fun getMouthOpenTime(): Long {
        return preferenceManager.getLongValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_MOUTH_OPEN_TIME,
            500L
        )
    }

    // Preference setter methods
    fun setSmileTime(value: Long) {
        preferenceManager.setLongValue(PreferenceManager.PREFERENCE_KEY_CAMERA_SMILE_TIME, value)
        _smileTime.value = value
    }

    fun setLeftWinkTime(value: Long) {
        preferenceManager.setLongValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_LEFT_WINK_TIME,
            value
        )
        _leftWinkTime.value = value
    }

    fun setRightWinkTime(value: Long) {
        preferenceManager.setLongValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_RIGHT_WINK_TIME,
            value
        )
        _rightWinkTime.value = value
    }

    fun setBlinkTime(value: Long) {
        preferenceManager.setLongValue(PreferenceManager.PREFERENCE_KEY_CAMERA_BLINK_TIME, value)
        _blinkTime.value = value
    }

    fun setMouthOpenTime(value: Long) {
        preferenceManager.setLongValue(
            PreferenceManager.PREFERENCE_KEY_CAMERA_MOUTH_OPEN_TIME,
            value
        )
        _mouthOpenTime.value = value
    }
}