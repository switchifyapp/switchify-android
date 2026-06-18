package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.patterns.store.GesturePatternStore
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.BaseMenu
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.utils.DeviceLockObserver

class GesturePatternsMenu(
    accessibilityService: SwitchifyAccessibilityService
) :
    BaseMenu(
        accessibilityService,
        buildGesturePatternsMenuItems(accessibilityService),
        MenuConstants.MenuIds.GESTURE_PATTERNS_MENU,
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
            if (!DeviceLockObserver.isUserUnlocked(accessibilityService)) return emptyList()
            val gesturePatternStore = GesturePatternStore(accessibilityService)
            return gesturePatternStore.getPatterns().map { pattern ->
                MenuItem(
                    id = pattern.id,
                    userProvidedText = pattern.name,
                    descriptionResource = R.string.menu_item_run_gesture_pattern_description,
                    action = {
                        pattern.execute()
                    }
                )
            }
        }
    }
}
