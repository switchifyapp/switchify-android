package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

/**
 * Confirmation menu for custom gestures (swipe/drag).
 * Note: This menu intentionally does not pass a menuId to BaseMenu because it's a
 * temporary confirmation dialog, not a customizable menu that users can modify.
 */
class CustomGestureConfirmationMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildCustomGestureConfirmationMenuItems(accessibilityService),
        menuId = null,  // Explicitly null - this is a confirmation dialog, not customizable
        showNavMenuItems = false
    ) {

    companion object {
        private fun buildCustomGestureConfirmationMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).customGestureConfirmationMenuObject.getDefaultMenuItems()
        }
    }
}