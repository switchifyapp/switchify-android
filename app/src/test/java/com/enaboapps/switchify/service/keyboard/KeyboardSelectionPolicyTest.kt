package com.enaboapps.switchify.service.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardSelectionPolicyTest {
    private val policy = KeyboardSelectionPolicy()

    @Test
    fun visibleKeyboardNotEscapedShowsEscapePromptAndCycleBreak() {
        val state = KeyboardState(isVisible = true, isEscaped = false)

        assertTrue(policy.shouldShowEscapePrompt(state))
        assertTrue(policy.shouldEnableCycleBreak(state))
        assertFalse(policy.shouldShowScanKeyboardMenuItem(state))
    }

    @Test
    fun visibleKeyboardEscapedShowsScanKeyboardMenuItem() {
        val state = KeyboardState(isVisible = true, isEscaped = true)

        assertFalse(policy.shouldShowEscapePrompt(state))
        assertFalse(policy.shouldEnableCycleBreak(state))
        assertTrue(policy.shouldShowScanKeyboardMenuItem(state))
    }

    @Test
    fun hiddenKeyboardDisablesKeyboardSpecificUi() {
        val state = KeyboardState(isVisible = false, isEscaped = false)

        assertFalse(policy.shouldShowEscapePrompt(state))
        assertFalse(policy.shouldEnableCycleBreak(state))
        assertFalse(policy.shouldShowScanKeyboardMenuItem(state))
    }

    @Test
    fun directSelectionBypassesOnlyWhenVisibleEnabledAndNotEscaped() {
        assertTrue(
            policy.shouldBypassAutoSelect(
                KeyboardState(
                    isVisible = true,
                    isEscaped = false,
                    isDirectSelectEnabled = true
                )
            )
        )
        assertFalse(
            policy.shouldBypassAutoSelect(
                KeyboardState(
                    isVisible = false,
                    isEscaped = false,
                    isDirectSelectEnabled = true
                )
            )
        )
        assertFalse(
            policy.shouldBypassAutoSelect(
                KeyboardState(
                    isVisible = true,
                    isEscaped = true,
                    isDirectSelectEnabled = true
                )
            )
        )
        assertFalse(
            policy.shouldBypassAutoSelect(
                KeyboardState(
                    isVisible = true,
                    isEscaped = false,
                    isDirectSelectEnabled = false
                )
            )
        )
    }
}
