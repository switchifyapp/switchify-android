package com.enaboapps.switchify.service.menu.menus.scroll

import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class ScrollMenuStructure {
    private val gestureMenuStructure = GestureMenuStructure()

    val scrollMenuObject = MenuStructure(
        id = "scroll_menu",
        items = listOf(
            MenuItem(
                id = "scroll_up",
                text = "Scroll Up",
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SCROLL_UP)
                }
            ),
            MenuItem(
                id = "scroll_down",
                text = "Scroll Down",
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SCROLL_DOWN)
                }
            ),
            MenuItem(
                id = "scroll_left",
                text = "Scroll Left",
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SCROLL_LEFT)
                }
            ),
            MenuItem(
                id = "scroll_right",
                text = "Scroll Right",
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SCROLL_RIGHT)
                }
            ),
            gestureMenuStructure.toggleGestureLockMenuItem
        )
    )
} 