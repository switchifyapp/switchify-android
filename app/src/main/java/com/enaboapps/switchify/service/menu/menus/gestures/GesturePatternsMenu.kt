package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.patterns.store.GesturePatternStore
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu

class GesturePatternsMenu(
    accessibilityService: SwitchifyAccessibilityService
) :
    BaseMenu(
        accessibilityService,
        buildGesturePatternsMenuItems(accessibilityService),
        null,  // No menuId - this is a dynamic menu
        { getGesturePatterns(accessibilityService) }
    ) {

    companion object {
        private fun buildGesturePatternsMenuItems(
            accessibilityService: SwitchifyAccessibilityService
        ): List<MenuItem> {
            return GestureMenuStructure(
                accessibilityService,
                accessibilityService.getServiceScope()
            ).createGesturePatternsMenuStructure()
                .getMenuItems()
        }

        suspend fun getGesturePatterns(accessibilityService: SwitchifyAccessibilityService): List<MenuItem> {
            val gesturePatternStore = GesturePatternStore(accessibilityService)
            return gesturePatternStore.getPatterns().map { pattern ->
                MenuItem(
                    id = pattern.id,
                    userProvidedText = pattern.name,
                    action = {
                        pattern.execute()
                    }
                )
            }
        }
    }
}