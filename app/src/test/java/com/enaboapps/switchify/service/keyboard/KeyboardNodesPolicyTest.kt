package com.enaboapps.switchify.service.keyboard

import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.KeyboardNodesState
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

    @Test
    fun dropsBatchWhenKeyboardHidden() {
        // Hidden keyboard with stale (or any) captured nodes — drop regardless.
        val state = KeyboardState(isVisible = false)
        val nodes = KeyboardNodesState() // uses default null bounds

        assertTrue(policy.shouldDropAsStale(nodes, state))
    }

    @Test
    fun dropsUninitializedBatchWithNoCapturedBounds() {
        // Visible keyboard but NodeExaminer has not yet produced a batch —
        // the default KeyboardNodesState (bounds == null) must not leak
        // through as an empty node update.
        val state = KeyboardState(isVisible = true)
        val nodes = KeyboardNodesState() // default: no nodes, no bounds

        assertTrue(policy.shouldDropAsStale(nodes, state))
    }

    // Note: the bounds-mismatch and bounds-match cases of shouldDropAsStale
    // depend on android.graphics.Rect, which throws "Stub!" in pure-JVM unit
    // tests. Those paths are exercised on device by swapping IMEs while
    // item-scanning (see the PR test plan).
}
