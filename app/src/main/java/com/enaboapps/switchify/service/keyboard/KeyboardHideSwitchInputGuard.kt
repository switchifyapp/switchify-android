package com.enaboapps.switchify.service.keyboard

internal class KeyboardHideSwitchInputGuard(
    private val suppressionDurationMillis: Long,
    private val uptimeMillis: () -> Long
) {
    private var suppressionEndsAt = 0L
    private var wasKeyboardVisible = false

    fun onKeyboardVisibilityChanged(isKeyboardVisible: Boolean) {
        when {
            isKeyboardVisible -> suppressionEndsAt = 0L
            wasKeyboardVisible -> suppressionEndsAt = uptimeMillis() + suppressionDurationMillis
        }
        wasKeyboardVisible = isKeyboardVisible
    }

    fun shouldSuppressSwitchInput(): Boolean {
        return uptimeMillis() < suppressionEndsAt
    }
}
