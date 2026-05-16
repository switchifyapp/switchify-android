package com.enaboapps.switchify.service.keyboard

import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.nodes.KeyboardNodesState

/**
 * Encapsulates routing and lifetime decisions for the keyboard node scanner.
 *
 * Counterpart to [KeyboardSelectionPolicy], which owns keyboard UI decisions
 * (escape prompt, scan-keyboard menu item, auto-select bypass). This class
 * owns "should the keyboard scanner be the active scanner" / "should the
 * keyboard scanner stay alive" — decisions previously scattered between
 * `ActiveAccessTechnique.currentAccessTechnique` and `onKeyboardStateChanged`.
 */
class KeyboardNodesPolicy {
    /**
     * Whether scanning should currently be driven by the keyboard scanner.
     *
     * True when the keyboard is visible, the user has not escaped, and the
     * menu is not currently the active access technique. While the menu is
     * open we route through the menu's own scan tree instead so menu items
     * can be selected even when an IME is up.
     */
    fun shouldRouteToKeyboardScanner(state: KeyboardState, currentTechnique: String): Boolean {
        return state.isVisible &&
                !state.isEscaped &&
                currentTechnique != AccessTechnique.Technique.MENU
    }

    /**
     * Whether the keyboard scanner instance should remain alive.
     *
     * True whenever the keyboard is visible — even if the user has escaped
     * or the menu is on top, the scanner is kept around so returning to the
     * keyboard doesn't pay the cost of a fresh start.
     */
    fun shouldKeepKeyboardScannerAlive(state: KeyboardState): Boolean {
        return state.isVisible
    }

    /**
     * Whether a [KeyboardNodesState] batch should be dropped instead of
     * forwarded to the keyboard scanner.
     *
     * Drops when:
     * - The keyboard is hidden — nodes are not relevant.
     * - The batch carries no bounds — it is the default uninitialized state
     *   from before NodeExaminer has produced its first emission.
     * - The captured bounds differ from the current IME bounds — the batch
     *   was extracted from a previous keyboard layout.
     */
    fun shouldDropAsStale(nodes: KeyboardNodesState, state: KeyboardState): Boolean {
        return !state.isVisible ||
                nodes.keyboardBounds == null ||
                nodes.isStaleAgainst(state.keyboardBounds)
    }
}
