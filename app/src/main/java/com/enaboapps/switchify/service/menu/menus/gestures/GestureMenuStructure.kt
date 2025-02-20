package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class GestureMenuStructure {
    val tapMenuItem = MenuItem(
        id = "tap",
        textResource = R.string.menu_item_tap,
        action = {
            GestureManager.getInstance().performTap()
        }
    )

    val toggleGestureLockMenuItem = MenuItem(
        id = "toggle_gesture_lock",
        textResource = R.string.menu_item_toggle_gesture_lock,
        closeOnSelect = false,
        action = { GestureManager.getInstance().toggleGestureLock() }
    )

    val tapGesturesMenuObject = MenuStructure(
        id = "tap_gestures_menu",
        items = listOf(
            tapMenuItem,
            MenuItem(
                id = "double_tap",
                textResource = R.string.menu_item_double_tap,
                action = { GestureManager.getInstance().performDoubleTap() }
            ),
            MenuItem(
                id = "tap_and_hold",
                textResource = R.string.menu_item_tap_and_hold,
                action = { GestureManager.getInstance().performTapAndHold() }
            ),
            toggleGestureLockMenuItem
        )
    )

    val gesturesMenuObject = MenuStructure(
        id = "gestures_menu",
        items = listOf(
            MenuItem(
                id = "tap_gestures",
                textResource = R.string.menu_item_tap_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openTapMenu() }
            ),
            MenuItem(
                id = "swipe_gestures",
                textResource = R.string.menu_item_swipe_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openSwipeMenu() }
            ),
            MenuItem(
                id = "drag",
                textResource = R.string.menu_item_drag,
                action = { GestureManager.getInstance().startDragGesture() }
            ),
            MenuItem(
                id = "hold_and_drag",
                textResource = R.string.menu_item_hold_and_drag,
                action = { GestureManager.getInstance().startHoldAndDragGesture() }
            ),
            MenuItem(
                id = "zoom_gestures",
                textResource = R.string.menu_item_zoom_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openZoomGesturesMenu() }
            ),
            toggleGestureLockMenuItem
        )
    )

    val swipeGesturesMenuObject = MenuStructure(
        id = "swipe_gestures_menu",
        items = listOf(
            MenuItem(
                id = "swipe_up",
                textResource = R.string.menu_item_swipe_up,
                action = { GestureManager.getInstance().performSwipeOrScroll(GestureType.SWIPE_UP) }
            ),
            MenuItem(
                id = "swipe_down",
                textResource = R.string.menu_item_swipe_down,
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SWIPE_DOWN)
                }
            ),
            MenuItem(
                id = "swipe_left",
                textResource = R.string.menu_item_swipe_left,
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SWIPE_LEFT)
                }
            ),
            MenuItem(
                id = "swipe_right",
                textResource = R.string.menu_item_swipe_right,
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SWIPE_RIGHT)
                }
            ),
            MenuItem(
                id = "custom_swipe",
                textResource = R.string.menu_item_custom_swipe,
                action = { GestureManager.getInstance().startCustomSwipe() }
            ),
            toggleGestureLockMenuItem
        )
    )

    val zoomGesturesMenuObject = MenuStructure(
        id = "zoom_gestures_menu",
        items = listOf(
            MenuItem(
                id = "zoom_in",
                textResource = R.string.menu_item_zoom_in,
                action = {
                    GestureManager.getInstance()
                        .performZoom(GestureType.ZOOM_IN)
                }
            ),
            MenuItem(
                id = "zoom_out",
                textResource = R.string.menu_item_zoom_out,
                action = {
                    GestureManager.getInstance()
                        .performZoom(GestureType.ZOOM_OUT)
                }
            ),
            toggleGestureLockMenuItem
        )
    )

    val customGestureConfirmationMenuObject = MenuStructure(
        id = "custom_gesture_confirmation_menu",
        items = listOf(
            MenuItem(
                id = "confirm",
                textResource = R.string.menu_item_confirm,
                action = { GestureManager.getInstance().endLinearGesture() }
            ),
            MenuItem(
                id = "reselect",
                textResource = R.string.menu_item_reselect,
                action = {
                    // Do nothing for reselect action
                }
            ),
            MenuItem(
                id = "cancel",
                textResource = R.string.menu_item_cancel,
                action = { GestureManager.getInstance().cancelLinearGesture() }
            )
        )
    )
} 