package com.enaboapps.switchify.pc.control

class PcAutoConnectPolicy {
    private var started = false
    private var backgrounded = false

    fun onStart(setupComplete: Boolean): Boolean {
        val opening = !started || backgrounded
        started = true
        backgrounded = false
        return setupComplete && opening
    }

    fun onStop(changingConfigurations: Boolean) {
        if (started && !changingConfigurations) backgrounded = true
    }
}
