package com.enaboapps.switchify.service.menu.menus.gestures

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePatternRecorder
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure

class GestureMenuStructure(private val context: Context) {
    private val preferenceManager = PreferenceManager(context)
    private val gestureLock =
        preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_GESTURE_LOCK)

    val tapMenuItem = MenuItem(
        id = "tap",
        textResource = R.string.menu_item_tap,
        action = {
            GestureManager.instance.performTap()
        }
    )

    val toggleGestureLockMenuItem = if (gestureLock) {
        MenuItem(
            id = "toggle_gesture_lock",
            textResource = R.string.system_gesture_lock,
            closeOnSelect = false,
            action = { GestureManager.instance.toggleGestureLock() }
        )
    } else null

    val tapGesturesMenuObject = MenuStructure(
        id = "tap_gestures_menu",
        items = listOfNotNull(
            tapMenuItem,
            MenuItem(
                id = "double_tap",
                textResource = R.string.menu_item_double_tap,
                action = { GestureManager.instance.performDoubleTap() }
            ),
            MenuItem(
                id = "tap_and_hold",
                textResource = R.string.menu_item_tap_and_hold,
                action = { GestureManager.instance.performTapAndHold() }
            ),
            toggleGestureLockMenuItem
        )
    )

    val gesturesMenuObject = MenuStructure(
        id = "gestures_menu",
        items = listOfNotNull(
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
                action = { GestureManager.instance.startDragGesture() }
            ),
            MenuItem(
                id = "hold_and_drag",
                textResource = R.string.menu_item_hold_and_drag,
                action = { GestureManager.instance.startHoldAndDragGesture() }
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

    fun createGesturePatternsMenuStructure(): MenuStructure {
        return MenuStructure(
            id = "gesture_patterns_menu",
            items =
                listOfNotNull(
                    if (GesturePatternRecorder.isRecording()) {
                        MenuItem(
                            id = "stop_recording",
                            textResource = R.string.stop_recording,
                            action = { GesturePatternRecorder.stopRecording(context) }
                        )
                    } else {
                        MenuItem(
                            id = "start_recording",
                            textResource = R.string.start_recording,
                            action = { GesturePatternRecorder.startRecording() }
                        )
                    },
                ))
    }

    val swipeGesturesMenuObject = MenuStructure(
        id = "swipe_gestures_menu",
        items = listOfNotNull(
            MenuItem(
                id = "swipe_up",
                textResource = R.string.menu_item_swipe_up,
                action = { GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_UP) }
            ),
            MenuItem(
                id = "swipe_down",
                textResource = R.string.menu_item_swipe_down,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_DOWN)
                }
            ),
            MenuItem(
                id = "swipe_left",
                textResource = R.string.menu_item_swipe_left,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_LEFT)
                }
            ),
            MenuItem(
                id = "swipe_right",
                textResource = R.string.menu_item_swipe_right,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_RIGHT)
                }
            ),
            MenuItem(
                id = "custom_swipe",
                textResource = R.string.menu_item_custom_swipe,
                action = { GestureManager.instance.startCustomSwipe() }
            ),
            toggleGestureLockMenuItem
        )
    )

    val zoomGesturesMenuObject = MenuStructure(
        id = "zoom_gestures_menu",
        items = listOfNotNull(
            MenuItem(
                id = "zoom_in",
                textResource = R.string.menu_item_zoom_in,
                action = {
                    GestureManager.instance
                        .performZoom(GestureType.ZOOM_IN)
                }
            ),
            MenuItem(
                id = "zoom_out",
                textResource = R.string.menu_item_zoom_out,
                action = {
                    GestureManager.instance
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
                action = { GestureManager.instance.endLinearGesture() }
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
                action = { GestureManager.instance.cancelLinearGesture() }
            )
        )
    )
} 