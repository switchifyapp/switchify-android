package com.enaboapps.switchify.service.menu.menus.media

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class MediaControlMenu(accessibilityService: SwitchifyAccessibilityService) :
    BaseMenu(
        accessibilityService,
        buildMediaControlMenuItems(accessibilityService),
        MenuConstants.MenuIds.MEDIA_CONTROL_MENU
    ) {

    companion object {
        private fun buildMediaControlMenuItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).mediaControlMenuObject.getDefaultMenuItems()
        }
    }
}