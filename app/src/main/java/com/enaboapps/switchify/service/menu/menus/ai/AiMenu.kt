package com.enaboapps.switchify.service.menu.menus.ai

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuStructureHolder

class AiMenu(
    accessibilityService: SwitchifyAccessibilityService
) : BaseMenu(
    accessibilityService,
    buildAiItems(accessibilityService),
    MenuConstants.MenuIds.AI_MENU
) {

    companion object {
        private fun buildAiItems(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            return MenuStructureHolder(accessibilityService).buildAiMenuObject().getMenuItems()
        }
    }
}
