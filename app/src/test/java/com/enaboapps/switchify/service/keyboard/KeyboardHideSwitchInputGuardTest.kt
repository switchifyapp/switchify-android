package com.enaboapps.switchify.service.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardHideSwitchInputGuardTest {
    private var currentTime = 0L
    private val guard = KeyboardHideSwitchInputGuard(1000L) { currentTime }

    @Test
    fun suppressesInputForOneSecondAfterKeyboardHides() {
        guard.onKeyboardVisibilityChanged(true)
        guard.onKeyboardVisibilityChanged(false)

        assertTrue(guard.shouldSuppressSwitchInput())

        currentTime = 999L
        assertTrue(guard.shouldSuppressSwitchInput())

        currentTime = 1000L
        assertFalse(guard.shouldSuppressSwitchInput())
    }

    @Test
    fun repeatedHiddenUpdatesDoNotExtendSuppression() {
        guard.onKeyboardVisibilityChanged(true)
        guard.onKeyboardVisibilityChanged(false)

        currentTime = 500L
        guard.onKeyboardVisibilityChanged(false)
        assertTrue(guard.shouldSuppressSwitchInput())

        currentTime = 1000L
        assertFalse(guard.shouldSuppressSwitchInput())
    }

    @Test
    fun keyboardShownEndsSuppressionImmediately() {
        guard.onKeyboardVisibilityChanged(true)
        guard.onKeyboardVisibilityChanged(false)
        currentTime = 250L

        guard.onKeyboardVisibilityChanged(true)

        assertFalse(guard.shouldSuppressSwitchInput())
    }

    @Test
    fun inputIsNotSuppressedBeforeKeyboardHides() {
        assertFalse(guard.shouldSuppressSwitchInput())
    }
}
