package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

/**
 * Menu for selecting finger mode for multi-finger gesture system.
 *
 * This menu allows users to choose between different finger modes:
 * - 1 Finger: Force single-finger gestures for maximum precision
 * - 2 Fingers: Force two-finger gestures for enhanced stability
 *
 * The menu is integrated at the end of the gestures menu and provides
 * immediate feedback when finger modes are changed.
 */
class FingerModeMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(accessibilityService, buildFingerModeMenuItems(accessibilityService)) {

    companion object {
        private fun buildFingerModeMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).fingerModeMenuObject.getMenuItems()
        }
    }
}