package com.enaboapps.switchify.service.face.utils

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Calculates head pose (Euler angles) from MediaPipe transformation matrices
 */
class HeadPoseCalculator {
    private val yawEMA = ExponentialMovingAverage(alpha = 0.3f)
    private val pitchEMA = ExponentialMovingAverage(alpha = 0.3f)

    /**
     * Represents Euler angles for head pose
     */
    data class EulerAngles(
        val yaw: Float,
        val pitch: Float,
        val roll: Float
    )

    /**
     * Extract smoothed Euler angles from transformation matrix
     */
    fun extractEulerAngles(transformMatrix: Any): EulerAngles {
        val rawAngles = extractEulerAnglesFromMatrix(transformMatrix)

        // Apply smoothing
        val smoothedYaw = yawEMA.update(rawAngles.yaw)
        val smoothedPitch = pitchEMA.update(rawAngles.pitch)

        return EulerAngles(smoothedYaw, smoothedPitch, rawAngles.roll)
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