package com.enaboapps.switchify.service.techniques

interface AccessTechniqueInterface {
    fun startAutoScanning()

    fun pauseAutoScanning()

    fun resumeAutoScanning()

    fun stopScanningAndReset() {
        resetUI()
        resetForNextUse()
    }

    fun swapScanDirection()

    fun stepScanningForward()

    fun stepScanningBackward()

    fun performSelectionAction()

    fun resetUI()

    fun resetForNextUse()

    fun cleanup()
}