package com.enaboapps.switchify.service.keyboard

import com.enaboapps.switchify.service.techniques.AccessTechnique
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardNodesPolicyTest {
    private val policy = KeyboardNodesPolicy()

    @Test
    fun routesToKeyboardScannerWhenVisibleNotEscapedAndNotInMenu() {
        val state = KeyboardState(isVisible = true, isEscaped = false)

        assertTrue(
            policy.shouldRouteToKeyboardScanner(state, AccessTechnique.Technique.ITEM_SCAN)
        )
        assertTrue(
            policy.shouldRouteToKeyboardScanner(state, AccessTechnique.Technique.POINT_SCAN)
        )
        assertTrue(
            policy.shouldRouteToKeyboardScanner(state, AccessTechnique.Technique.RADAR)
        )
    }

    @Test
    fun doesNotRouteToKeyboardScannerWhileMenuActive() {
        val state = KeyboardState(isVisible = true, isEscaped = false)

        assertFalse(
            policy.shouldRouteToKeyboardScanner(state, AccessTechnique.Technique.MENU)
        )
    }

    @Test
    fun doesNotRouteToKeyboardScannerWhenEscaped() {
        val state = KeyboardState(isVisible = true, isEscaped = true)

        assertFalse(
            policy.shouldRouteToKeyboardScanner(state, AccessTechnique.Technique.ITEM_SCAN)
        )
    }

    @Test
    fun doesNotRouteToKeyboardScannerWhenHidden() {
        val state = KeyboardState(isVisible = false, isEscaped = false)

        assertFalse(
            policy.shouldRouteToKeyboardScanner(state, AccessTechnique.Technique.ITEM_SCAN)
        )
    }

    @Test
    fun keepsKeyboardScannerAliveWheneverVisible() {
        assertTrue(
            policy.shouldKeepKeyboardScannerAlive(
                KeyboardState(isVisible = true, isEscaped = false)
            )
        )
        // Escaped but still visible: scanner stays alive so returning to
        // the keyboard does not pay a fresh-start cost.
        assertTrue(
            policy.shouldKeepKeyboardScannerAlive(
                KeyboardState(isVisible = true, isEscaped = true)
            )
        )
    }

    @Test
    fun tearsDownKeyboardScannerOnceHidden() {
        assertFalse(
            policy.shouldKeepKeyboardScannerAlive(
                KeyboardState(isVisible = false, isEscaped = false)
            )
        )
    }
}
