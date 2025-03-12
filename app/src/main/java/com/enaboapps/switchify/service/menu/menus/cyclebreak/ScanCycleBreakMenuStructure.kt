package com.enaboapps.switchify.service.menu.menus.cyclebreak

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.menus.main.MainMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class ScanCycleBreakMenuStructure(accessibilityService: SwitchifyAccessibilityService) {
    val breakMenuObject = MenuStructure(
        id = "break_menu",
        items = listOfNotNull(
            MainMenuStructure(accessibilityService).deviceItem,
            GestureMenuStructure(accessibilityService).toggleGestureLockMenuItem
        )
    )
} 