package com.enaboapps.switchify.service.menu.menus.gestures

import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class GestureMenuStructure {
    val tapMenuItem = MenuItem(
        id = "tap",
        text = "Tap",
        action = {
            GestureManager.getInstance().performTap()
        }
    )

    val toggleGestureLockMenuItem = MenuItem(
        id = "toggle_gesture_lock",
        text = "Toggle Gesture Lock",
        closeOnSelect = false,
        action = { GestureManager.getInstance().toggleGestureLock() }
    )

    val tapGesturesMenuObject = MenuStructure(
        id = "tap_gestures_menu",
        items = listOf(
            tapMenuItem,
            MenuItem(
                id = "double_tap",
                text = "Double Tap",
                action = { GestureManager.getInstance().performDoubleTap() }
            ),
            MenuItem(
                id = "tap_and_hold",
                text = "Tap and Hold",
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
                text = "Tap Gestures",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openTapMenu() }
            ),
            MenuItem(
                id = "swipe_gestures",
                text = "Swipe Gestures",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openSwipeMenu() }
            ),
            MenuItem(
                id = "drag",
                text = "Drag",
                action = { GestureManager.getInstance().startDragGesture() }
            ),
            MenuItem(
                id = "hold_and_drag",
                text = "Hold and Drag",
                action = { GestureManager.getInstance().startHoldAndDragGesture() }
            ),
            MenuItem(
                id = "zoom_gestures",
                text = "Zoom Gestures",
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
                text = "Swipe Up",
                action = { GestureManager.getInstance().performSwipeOrScroll(GestureType.SWIPE_UP) }
            ),
            MenuItem(
                id = "swipe_down",
                text = "Swipe Down",
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SWIPE_DOWN)
                }
            ),
            MenuItem(
                id = "swipe_left",
                text = "Swipe Left",
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SWIPE_LEFT)
                }
            ),
            MenuItem(
                id = "swipe_right",
                text = "Swipe Right",
                action = {
                    GestureManager.getInstance().performSwipeOrScroll(GestureType.SWIPE_RIGHT)
                }
            ),
            MenuItem(
                id = "custom_swipe",
                text = "Custom Swipe",
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
                text = "Zoom In",
                action = {
                    GestureManager.getInstance()
                        .performZoom(GestureType.ZOOM_IN)
                }
            ),
            MenuItem(
                id = "zoom_out",
                text = "Zoom Out",
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
                text = "Confirm",
                action = { GestureManager.getInstance().endLinearGesture() }
            ),
            MenuItem(
                id = "reselect",
                text = "Reselect",
                action = {
                    // Do nothing for reselect action
                }
            ),
            MenuItem(
                id = "cancel",
                text = "Cancel",
                action = { GestureManager.getInstance().cancelLinearGesture() }
            )
        )
    )
} 