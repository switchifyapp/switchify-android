package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class ZoomGesturesMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(accessibilityService, buildZoomGesturesMenuItems(accessibilityService)) {
    companion object {
        private fun buildZoomGesturesMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).zoomGesturesMenuObject.getMenuItems()
        }
    }
}