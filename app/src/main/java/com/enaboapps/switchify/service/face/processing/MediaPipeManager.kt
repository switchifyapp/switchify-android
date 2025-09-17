package com.enaboapps.switchify.service.face.processing

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker

/**
 * Manages MediaPipe face landmarker initialization and processing
 */
class MediaPipeManager {
    private var faceLandmarker: FaceLandmarker? = null

    companion object {
        private const val TAG = "MediaPipeManager"
    }

    fun ensureFaceLandmarker(context: Context): Boolean {
        if (faceLandmarker == null) {
            initFaceLandmarker(context)
        }
        return faceLandmarker != null
    }

    private fun initFaceLandmarker(context: Context) {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("face_landmarker.task")
                .build()

            val options = FaceLandmarker.FaceLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setOutputFaceBlendshapes(true)
                .setOutputFacialTransformationMatrixes(true)
                .setRunningMode(RunningMode.VIDEO)
                .setNumFaces(1)
                .build()

            faceLandmarker = FaceLandmarker.createFromOptions(context, options)
            Log.d(TAG, "Face landmarker initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize face landmarker", e)
            faceLandmarker = null
        }
    }

    fun detectForVideo(bitmap: Bitmap, timestampMs: Long): com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult? {
        val landmarker = faceLandmarker ?: return null
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            landmarker.detectForVideo(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Error in face detection", e)
            null
        }
    }

    fun close() {
        faceLandmarker?.close()
        faceLandmarker = null
    }
}