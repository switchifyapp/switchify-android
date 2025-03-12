package com.enaboapps.switchify.service.menu.menus.system

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class DeviceMenu(
    accessibilityService: SwitchifyAccessibilityService
) : BaseMenu(accessibilityService, buildDeviceItems(accessibilityService)) {

    companion object {
        private fun buildDeviceItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).buildDeviceMenuObject().getMenuItems()
        }
    }
}