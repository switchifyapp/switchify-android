package com.enaboapps.switchify.service.face.utils

import android.util.Log
import android.view.Surface
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Calculates head pose (Euler angles) from MediaPipe transformation matrices
 * with coordinate system normalization for consistent results across devices
 */
class HeadPoseCalculator {
    private val yawEMA = ExponentialMovingAverage(alpha = 0.3f)
    private val pitchEMA = ExponentialMovingAverage(alpha = 0.3f)

    // Camera and device orientation state
    private var deviceRotation: Int = Surface.ROTATION_0
    private var isFrontCamera: Boolean = true

    companion object {
        private const val TAG = "HeadPoseCalculator"
    }

    /**
     * Represents Euler angles for head pose
     */
    data class EulerAngles(
        val yaw: Float,
        val pitch: Float,
        val roll: Float
    )

    /**
     * Configure camera and device orientation for coordinate normalization
     * @param rotation Device rotation (Surface.ROTATION_0, ROTATION_90, etc.)
     * @param frontCamera Whether using front-facing camera
     */
    fun setCameraOrientation(rotation: Int, frontCamera: Boolean = true) {
        val rotationName = when (rotation) {
            Surface.ROTATION_0 -> "PORTRAIT"
            Surface.ROTATION_90 -> "LANDSCAPE_LEFT"
            Surface.ROTATION_180 -> "PORTRAIT_UPSIDE_DOWN"
            Surface.ROTATION_270 -> "LANDSCAPE_RIGHT"
            else -> "UNKNOWN"
        }
        Log.d(TAG, "Camera orientation configured: rotation=$rotationName, frontCamera=$frontCamera")

        deviceRotation = rotation
        isFrontCamera = frontCamera
    }

    /**
     * Extract smoothed and normalized Euler angles from transformation matrix
     */
    fun extractEulerAngles(transformMatrix: Any): EulerAngles {
        val rawAngles = extractEulerAnglesFromMatrix(transformMatrix)

        // Apply coordinate system normalization
        val (normalizedYaw, normalizedPitch) = normalizeCoordinates(rawAngles.yaw, rawAngles.pitch)

        // Apply smoothing to normalized values
        val smoothedYaw = yawEMA.update(normalizedYaw)
        val smoothedPitch = pitchEMA.update(normalizedPitch)

        return EulerAngles(smoothedYaw, smoothedPitch, rawAngles.roll)
    }

    /**
     * Normalize yaw and pitch values based on device rotation and camera orientation
     * @param yaw Raw yaw angle in degrees
     * @param pitch Raw pitch angle in degrees
     * @return Pair of normalized (yaw, pitch) angles
     */
    private fun normalizeCoordinates(yaw: Float, pitch: Float): Pair<Float, Float> {
        var normalizedYaw = yaw
        var normalizedPitch = pitch

        // Apply device rotation compensation
        when (deviceRotation) {
            Surface.ROTATION_90 -> {
                // Device rotated 90° clockwise (landscape left)
                normalizedYaw = -pitch
                normalizedPitch = yaw
            }
            Surface.ROTATION_180 -> {
                // Device upside down
                normalizedYaw = -yaw
                normalizedPitch = -pitch
            }
            Surface.ROTATION_270 -> {
                // Device rotated 90° counter-clockwise (landscape right)
                normalizedYaw = pitch
                normalizedPitch = -yaw
            }
            Surface.ROTATION_0 -> {
                // Portrait - no rotation needed
                // normalizedYaw and normalizedPitch already set
            }
        }

        // Apply front camera mirroring (mirror yaw for natural left/right movement)
        if (isFrontCamera) {
            normalizedYaw = -normalizedYaw
        }

        // Invert pitch for natural up/down movement (head up = cursor up)
        normalizedPitch = -normalizedPitch

        return Pair(normalizedYaw, normalizedPitch)
    }

    private fun extractEulerAnglesFromMatrix(transformMatrix: Any): EulerAngles {
        return try {
            when (transformMatrix) {
                is FloatArray -> extractEulerFromFloatArray(transformMatrix)
                else -> {
                    // Handle MediaPipe matrix object using reflection
                    val clazz = transformMatrix::class.java
                    val dataMethod = clazz.methods.find { it.name == "data" }
                    if (dataMethod != null) {
                        val matrixData = dataMethod.invoke(transformMatrix) as? FloatArray
                        if (matrixData != null) {
                            extractEulerFromFloatArray(matrixData)
                        } else {
                            EulerAngles(0f, 0f, 0f)
                        }
                    } else {
                        EulerAngles(0f, 0f, 0f)
                    }
                }
            }
        } catch (e: Exception) {
            EulerAngles(0f, 0f, 0f)
        }
    }

    private fun extractEulerFromFloatArray(matrixData: FloatArray): EulerAngles {
        if (matrixData.size < 16) {
            return EulerAngles(0f, 0f, 0f)
        }

        // Extract rotation matrix components (assuming 4x4 matrix)
        val r00 = matrixData[0]
        val r01 = matrixData[1]
        val r02 = matrixData[2]
        val r10 = matrixData[4]
        val r11 = matrixData[5]
        val r12 = matrixData[6]
        val r20 = matrixData[8]
        val r21 = matrixData[9]
        val r22 = matrixData[10]

        // Calculate Euler angles from rotation matrix
        val sy = sqrt(r00 * r00 + r10 * r10)

        val singular = sy < 1e-6

        val yaw: Float
        val pitch: Float
        val roll: Float

        if (!singular) {
            yaw = atan2(r21, r22) * 180f / PI.toFloat()
            pitch = atan2(-r20, sy) * 180f / PI.toFloat()
            roll = atan2(r10, r00) * 180f / PI.toFloat()
        } else {
            yaw = atan2(-r12, r11) * 180f / PI.toFloat()
            pitch = atan2(-r20, sy) * 180f / PI.toFloat()
            roll = 0f
        }

        return EulerAngles(yaw, pitch, roll)
    }

    fun reset() {
        yawEMA.reset()
        pitchEMA.reset()
    }
}