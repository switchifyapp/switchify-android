package com.enaboapps.switchify.service.face.utils

/**
 * Exponential Moving Average filter for smoothing noisy signals
 */
class ExponentialMovingAverage(private val alpha: Float) {
    private var value: Float = 0f
    private var isInitialized = false

    /**
     * Update the EMA with a new value
     * @param newValue The new input value
     * @return The smoothed output value
     */
    fun update(newValue: Float): Float {
        value = if (isInitialized) {
            alpha * newValue + (1 - alpha) * value
        } else {
            isInitialized = true
            newValue
        }
        return value
    }

    /**
     * Get the current smoothed value
     */
    fun getValue(): Float = value

    /**
     * Reset the filter
     */
    fun reset() {
        value = 0f
        isInitialized = false
    }
}