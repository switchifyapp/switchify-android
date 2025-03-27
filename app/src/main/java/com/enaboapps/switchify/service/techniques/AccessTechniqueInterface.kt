package com.enaboapps.switchify.service.techniques

interface AccessTechniqueInterface {
    fun startAutoScanning()
    fun pauseAutoScanning()
    fun resumeAutoScanning()
    fun stopAutoScanning()
    fun swapScanDirection()
    fun stepScanningForward()
    fun stepScanningBackward()
    fun performSelectionAction()
    fun cleanup()
}