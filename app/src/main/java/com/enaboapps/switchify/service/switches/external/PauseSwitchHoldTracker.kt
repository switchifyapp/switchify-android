package com.enaboapps.switchify.service.switches.external

internal class PauseSwitchHoldTracker {
    private var pressedKeyCode: Int = 0
    private var pressedTime: Long = 0

    fun onPressed(keyCode: Int, now: Long) {
        if (pressedTime == 0L || pressedKeyCode != keyCode) {
            pressedKeyCode = keyCode
            pressedTime = now
        }
    }

    fun consumeRelease(keyCode: Int, now: Long, holdDuration: Long): Boolean {
        val didHold = pressedTime > 0 &&
                pressedKeyCode == keyCode &&
                now - pressedTime >= holdDuration
        reset()
        return didHold
    }

    fun consumeReleasePressTime(keyCode: Int): Long? {
        val releasePressTime = pressedTime.takeIf {
            it > 0 && pressedKeyCode == keyCode
        }
        reset()
        return releasePressTime
    }

    fun reset() {
        pressedKeyCode = 0
        pressedTime = 0
    }
}
