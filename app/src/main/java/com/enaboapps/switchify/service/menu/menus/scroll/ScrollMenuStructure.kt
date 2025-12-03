package com.enaboapps.switchify.service.menu.menus.scroll

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class ScrollMenuStructure(accessibilityService: SwitchifyAccessibilityService) {
    private val gestureMenuStructure = GestureMenuStructure(accessibilityService)

    val scrollMenuObject = MenuStructure(
        id = "scroll_menu",
        items = listOfNotNull(
            MenuItemRegistry.getScrollMenuDefinitions().find { it.id == "scroll_up" }?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_UP) }
                )
            },
            MenuItemRegistry.getScrollMenuDefinitions().find { it.id == "scroll_down" }?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_DOWN) }
                )
            },
            MenuItemRegistry.getScrollMenuDefinitions().find { it.id == "scroll_left" }?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_LEFT) }
                )
            },
            MenuItemRegistry.getScrollMenuDefinitions().find { it.id == "scroll_right" }?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SCROLL_RIGHT) }
                )
            },
            gestureMenuStructure.toggleGestureLockMenuItem
        ),
        context = accessibilityService
    )
} 