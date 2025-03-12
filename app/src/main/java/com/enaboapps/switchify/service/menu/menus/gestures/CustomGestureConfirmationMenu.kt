package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class CustomGestureConfirmationMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildCustomGestureConfirmationMenuItems(accessibilityService),
        showSystemNavItems = false,
        showNavMenuItems = false
    ) {

    companion object {
        private fun buildCustomGestureConfirmationMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).customGestureConfirmationMenuObject.getMenuItems()
        }
    }
}