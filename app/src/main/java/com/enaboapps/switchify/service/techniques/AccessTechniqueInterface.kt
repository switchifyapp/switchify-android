package com.enaboapps.switchify.service.techniques

interface AccessTechniqueInterface {
    fun startAutoScanning()

    fun pauseAutoScanning()

    fun resumeAutoScanning()

    /**
     * Stops scanning and resets the UI.
     * N.B. It's important to call the super class method at the beginning of this method.
     */
    fun stopScanningAndReset() {
        resetUI()
        resetForNextUse()
    }

    fun swapScanDirection()

    fun stepScanningForward()

    fun stepScanningBackward()

    fun stepScanningUp()

    fun stepScanningDown()

    fun stepScanningLeft()

    fun stepScanningRight()

    fun performSelectionAction()

    fun resetUI()

    fun resetForNextUse()

    /**
     * Cleans up the access technique.
     * N.B. It's important to call the super class method at the beginning of this method.
     */
    fun cleanup() {
        stopScanningAndReset()
    }
}