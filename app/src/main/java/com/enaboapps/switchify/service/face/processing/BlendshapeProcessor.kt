package com.enaboapps.switchify.service.face.processing

import com.enaboapps.switchify.service.face.utils.ExponentialMovingAverage

/**
 * Handles blendshape extraction, caching, and smoothing
 */
class BlendshapeProcessor {
    private var blendshapeIndices: BlendshapeIndices? = null

    // Smoothing filters
    private val blinkScoreEMA = ExponentialMovingAverage(alpha = 0.3f)
    private val smileScoreEMA = ExponentialMovingAverage(alpha = 0.3f)
    private val mouthCloseScoreEMA = ExponentialMovingAverage(alpha = 0.3f)
    private val puckerScoreEMA = ExponentialMovingAverage(alpha = 0.3f)

    /**
     * Cached blendshape indices for performance optimization
     */
    private data class BlendshapeIndices(
        val leftEyeCloseIndex: Int = -1,
        val rightEyeCloseIndex: Int = -1,
        val mouthSmileLeftIndex: Int = -1,
        val mouthSmileRightIndex: Int = -1,
        val mouthCloseIndex: Int = -1,
        val jawOpenIndex: Int = -1,
        val mouthPuckerIndex: Int = -1
    )

    /**
     * Container for processed blendshape scores
     */
    data class BlendshapeScores(
        val smileScore: Float = 0f,
        val leftEyeCloseScore: Float = 0f,
        val rightEyeCloseScore: Float = 0f,
        val blinkScore: Float = 0f,
        val mouthCloseScore: Float = 1f, // Default to closed (1.0)
        val puckerScore: Float = 0f
    )

    fun processBlendshapes(
        blendShapes: List<com.google.mediapipe.tasks.components.containers.Category>
    ): BlendshapeScores {
        if (blendShapes.isEmpty()) {
            return BlendshapeScores()
        }

        // Cache indices for performance if not already done
        if (blendshapeIndices == null) {
            blendshapeIndices = cacheBlendshapeIndices(blendShapes)
        }

        val indices = blendshapeIndices!!

        // Extract raw scores
        var leftEyeClose = 0f
        var rightEyeClose = 0f
        var smileLeft = 0f
        var smileRight = 0f
        var mouthClose = 0f
        var mouthPucker = 0f

        // Use cached indices for direct access
        if (indices.leftEyeCloseIndex >= 0 && indices.leftEyeCloseIndex < blendShapes.size) {
            leftEyeClose = blendShapes[indices.leftEyeCloseIndex].score()
        }

        if (indices.rightEyeCloseIndex >= 0 && indices.rightEyeCloseIndex < blendShapes.size) {
            rightEyeClose = blendShapes[indices.rightEyeCloseIndex].score()
        }

        if (indices.mouthSmileLeftIndex >= 0 && indices.mouthSmileLeftIndex < blendShapes.size) {
            smileLeft = blendShapes[indices.mouthSmileLeftIndex].score()
        }

        if (indices.mouthSmileRightIndex >= 0 && indices.mouthSmileRightIndex < blendShapes.size) {
            smileRight = blendShapes[indices.mouthSmileRightIndex].score()
        }

        if (indices.mouthCloseIndex >= 0 && indices.mouthCloseIndex < blendShapes.size) {
            mouthClose = blendShapes[indices.mouthCloseIndex].score()
        }

        if (indices.mouthPuckerIndex >= 0 && indices.mouthPuckerIndex < blendShapes.size) {
            mouthPucker = blendShapes[indices.mouthPuckerIndex].score()
        }

        // Calculate combined scores
        val blinkScore = kotlin.math.max(leftEyeClose, rightEyeClose)
        val smileScore = kotlin.math.max(smileLeft, smileRight)

        // Apply smoothing
        val smoothedBlinkScore = blinkScoreEMA.update(blinkScore)
        val smoothedSmileScore = smileScoreEMA.update(smileScore)
        val smoothedMouthCloseScore = mouthCloseScoreEMA.update(mouthClose)
        val smoothedPuckerScore = puckerScoreEMA.update(mouthPucker)

        return BlendshapeScores(
            smileScore = smoothedSmileScore,
            leftEyeCloseScore = leftEyeClose,
            rightEyeCloseScore = rightEyeClose,
            blinkScore = smoothedBlinkScore,
            mouthCloseScore = smoothedMouthCloseScore,
            puckerScore = smoothedPuckerScore
        )
    }

    private fun cacheBlendshapeIndices(
        blendShapes: List<com.google.mediapipe.tasks.components.containers.Category>
    ): BlendshapeIndices {
        var leftEyeCloseIndex = -1
        var rightEyeCloseIndex = -1
        var mouthSmileLeftIndex = -1
        var mouthSmileRightIndex = -1
        var mouthCloseIndex = -1
        var jawOpenIndex = -1
        var mouthPuckerIndex = -1

        for (i in blendShapes.indices) {
            when (blendShapes[i].categoryName()) {
                "eyeBlinkLeft" -> leftEyeCloseIndex = i
                "eyeBlinkRight" -> rightEyeCloseIndex = i
                "mouthSmileLeft" -> mouthSmileLeftIndex = i
                "mouthSmileRight" -> mouthSmileRightIndex = i
                "mouthClose" -> mouthCloseIndex = i
                "jawOpen" -> jawOpenIndex = i
                "mouthPucker" -> mouthPuckerIndex = i
            }
        }

        return BlendshapeIndices(
            leftEyeCloseIndex,
            rightEyeCloseIndex,
            mouthSmileLeftIndex,
            mouthSmileRightIndex,
            mouthCloseIndex,
            jawOpenIndex,
            mouthPuckerIndex
        )
    }

    fun reset() {
        blinkScoreEMA.reset()
        smileScoreEMA.reset()
        mouthCloseScoreEMA.reset()
        puckerScoreEMA.reset()
    }
}