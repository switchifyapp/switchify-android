package com.enaboapps.switchify.screens.settings.models

import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import com.enaboapps.switchify.switches.CameraSwitchFacialGesture
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class CameraSettingsScreenModel(private val context: Context) : ViewModel() {
    
    private val _detectedExpressions = MutableStateFlow<Set<String>>(emptySet())
    val detectedExpressions: StateFlow<Set<String>> = _detectedExpressions.asStateFlow()
    
    private val _isFaceDetected = MutableStateFlow(false)
    val isFaceDetected: StateFlow<Boolean> = _isFaceDetected.asStateFlow()
    
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var preview: Preview? = null
    private var previewView: PreviewView? = null
    private var faceDetector: FaceDetector? = null
    private val isProcessing = AtomicBoolean(false)
    
    // Face detection thresholds
    private companion object {
        const val TAG = "CameraSettingsScreenModel"
        const val SMILE_THRESHOLD = 0.5f
        const val EYE_OPEN_THRESHOLD = 0.2f
        const val HEAD_TURN_THRESHOLD = 20f
        const val MIN_FACE_SIZE = 0.2f
    }
    
    init {
        initializeFaceDetector()
    }
    
    private fun initializeFaceDetector() {
        faceDetector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .setMinFaceSize(MIN_FACE_SIZE)
                .enableTracking()
                .build()
        )
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
                previewView?.let { setSurfaceProvider(it.surfaceProvider) }
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
        // Skip processing if already processing another frame
        if (isProcessing.getAndSet(true)) {
            imageProxy.close()
            return
        }
        
        try {
            imageProxy.image?.let { mediaImage ->
                val image = InputImage.fromMediaImage(
                    mediaImage,
                    imageProxy.imageInfo.rotationDegrees
                )
                
                faceDetector?.process(image)
                    ?.addOnSuccessListener { faces ->
                        if (faces.isNotEmpty()) {
                            _isFaceDetected.value = true
                            processFace(faces[0])
                        } else {
                            _isFaceDetected.value = false
                            _detectedExpressions.value = emptySet()
                        }
                    }
                    ?.addOnFailureListener { e ->
                        Log.e(TAG, "Face detection failed", e)
                        _isFaceDetected.value = false
                        _detectedExpressions.value = emptySet()
                    }
                    ?.addOnCompleteListener {
                        isProcessing.set(false)
                        imageProxy.close()
                    }
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
    
    private fun processFace(face: Face) {
        val detectedGestures = mutableSetOf<String>()
        
        // Check for smile
        val smilingProbability = face.smilingProbability ?: 0f
        if (smilingProbability > SMILE_THRESHOLD) {
            detectedGestures.add(CameraSwitchFacialGesture.SMILE)
        }
        
        // Check for eye states
        val leftEyeOpen = (face.leftEyeOpenProbability ?: 1f) > EYE_OPEN_THRESHOLD
        val rightEyeOpen = (face.rightEyeOpenProbability ?: 1f) > EYE_OPEN_THRESHOLD
        
        // Left wink (left eye closed, right eye open)
        if (!leftEyeOpen && rightEyeOpen) {
            detectedGestures.add(CameraSwitchFacialGesture.LEFT_WINK)
        }
        
        // Right wink (right eye closed, left eye open)
        if (leftEyeOpen && !rightEyeOpen) {
            detectedGestures.add(CameraSwitchFacialGesture.RIGHT_WINK)
        }
        
        // Blink (both eyes closed)
        if (!leftEyeOpen && !rightEyeOpen) {
            detectedGestures.add(CameraSwitchFacialGesture.BLINK)
        }
        
        // Check for head turns
        val headRotationY = face.headEulerAngleY
        val headRotationX = face.headEulerAngleX
        
        // Head turn left (positive Y rotation)
        if (headRotationY > HEAD_TURN_THRESHOLD) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_LEFT)
        }
        
        // Head turn right (negative Y rotation)
        if (headRotationY < -HEAD_TURN_THRESHOLD) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_RIGHT)
        }
        
        // Head turn up (positive X rotation)
        if (headRotationX > HEAD_TURN_THRESHOLD) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_UP)
        }
        
        // Head turn down (negative X rotation)
        if (headRotationX < -HEAD_TURN_THRESHOLD) {
            detectedGestures.add(CameraSwitchFacialGesture.HEAD_TURN_DOWN)
        }
        
        _detectedExpressions.value = detectedGestures
    }
    
    fun cleanup() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageAnalyzer = null
            preview = null
            previewView = null
            faceDetector?.close()
            faceDetector = null
            Log.d(TAG, "Camera cleaned up successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup camera", e)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        cleanup()
    }
}