package com.enaboapps.switchify.service.menu.menus.gestures

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePatternRecorder
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.placement.FingerMode
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.window.ServiceMessageHUD

class GestureMenuStructure(private val context: Context) {
    private val preferenceManager = PreferenceManager(context)
    private val gestureLock =
        preferenceManager.getBooleanValue(PreferenceManager.Keys.PREFERENCE_KEY_GESTURE_LOCK)

    val tapMenuItem = MenuItem(
        id = "tap",
        labelResource = R.string.menu_item_tap,
        drawableId = R.drawable.ic_gesture_tap,
        action = {
            GestureManager.instance.performTap()
        }
    )

    val toggleGestureLockMenuItem = if (gestureLock) {
        MenuItem(
            id = "toggle_gesture_lock",
            labelResource = R.string.system_gesture_lock,
            drawableId = R.drawable.ic_toggle_gesture_lock,
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
                labelResource = R.string.menu_item_double_tap,
                drawableId = R.drawable.ic_gesture_double_tap,
                action = { GestureManager.instance.performDoubleTap() }
            ),
            MenuItem(
                id = "tap_and_hold",
                labelResource = R.string.menu_item_tap_and_hold,
                drawableId = R.drawable.ic_gesture_tap_hold,
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
                labelResource = R.string.menu_item_tap_gestures,
                drawableId = R.drawable.ic_tap_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openTapMenu() }
            ),
            MenuItem(
                id = "swipe_gestures",
                labelResource = R.string.menu_item_swipe_gestures,
                drawableId = R.drawable.ic_swipe_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openSwipeMenu() }
            ),
            MenuItem(
                id = "drag",
                labelResource = R.string.menu_item_drag,
                drawableId = R.drawable.ic_gesture_drag,
                action = { GestureManager.instance.startDragGesture() }
            ),
            MenuItem(
                id = "hold_and_drag",
                labelResource = R.string.menu_item_hold_and_drag,
                drawableId = R.drawable.ic_gesture_hold_drag,
                action = { GestureManager.instance.startHoldAndDragGesture() }
            ),
            MenuItem(
                id = "zoom_gestures",
                labelResource = R.string.menu_item_zoom_gestures,
                drawableId = R.drawable.ic_zoom_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openZoomGesturesMenu() }
            ),
            MenuItem(
                id = "finger_mode",
                labelResource = R.string.menu_item_finger_mode,
                drawableId = R.drawable.ic_finger_mode,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openFingerModeMenu() }
            ),
            toggleGestureLockMenuItem
        )
    )

    fun createGesturePatternsMenuStructure(): MenuStructure {
        return MenuStructure(
            id = "gesture_patterns_menu",
            items =
                listOfNotNull(
                    if (GesturePatternRecorder.isRecording() && GesturePatternRecorder.getRecordedGestureCount() > 0) {
                        MenuItem(
                            id = "save_recording",
                            labelResource = R.string.save_recording,
                            drawableId = R.drawable.ic_save_recording,
                            action = { GesturePatternRecorder.saveRecording(context) }
                        )
                    } else if (!GesturePatternRecorder.isRecording()) {
                        MenuItem(
                            id = "start_recording",
                            labelResource = R.string.start_recording,
                            drawableId = R.drawable.ic_start_recording,
                            action = { GesturePatternRecorder.startRecording(context) }
                        )
                    } else null,
                    if (GesturePatternRecorder.isRecording()) {
                        MenuItem(
                            id = "cancel_recording",
                            labelResource = R.string.cancel_recording,
                            drawableId = R.drawable.ic_cancel_recording,
                            action = { GesturePatternRecorder.cancelRecording() }
                        )
                    } else null
                ))
    }

    val swipeGesturesMenuObject = MenuStructure(
        id = "swipe_gestures_menu",
        items = listOfNotNull(
            MenuItem(
                id = "swipe_up",
                labelResource = R.string.menu_item_swipe_up,
                drawableId = R.drawable.ic_gesture_swipe_up,
                action = { GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_UP) }
            ),
            MenuItem(
                id = "swipe_down",
                labelResource = R.string.menu_item_swipe_down,
                drawableId = R.drawable.ic_gesture_swipe_down,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_DOWN)
                }
            ),
            MenuItem(
                id = "swipe_left",
                labelResource = R.string.menu_item_swipe_left,
                drawableId = R.drawable.ic_gesture_swipe_left,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_LEFT)
                }
            ),
            MenuItem(
                id = "swipe_right",
                labelResource = R.string.menu_item_swipe_right,
                drawableId = R.drawable.ic_gesture_swipe_right,
                action = {
                    GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_RIGHT)
                }
            ),
            MenuItem(
                id = "custom_swipe",
                labelResource = R.string.menu_item_custom_swipe,
                drawableId = R.drawable.ic_gesture_custom_swipe,
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
                labelResource = R.string.menu_item_zoom_in,
                drawableId = R.drawable.ic_gesture_zoom_in,
                action = {
                    GestureManager.instance
                        .performZoom(GestureType.ZOOM_IN)
                }
            ),
            MenuItem(
                id = "zoom_out",
                labelResource = R.string.menu_item_zoom_out,
                drawableId = R.drawable.ic_gesture_zoom_out,
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
                labelResource = R.string.menu_item_confirm,
                drawableId = R.drawable.ic_confirm,
                action = { GestureManager.instance.endLinearGesture() }
            ),
            MenuItem(
                id = "reselect",
                labelResource = R.string.menu_item_reselect,
                drawableId = R.drawable.ic_reselect,
                action = {
                    // Do nothing for reselect action
                }
            ),
            MenuItem(
                id = "cancel",
                labelResource = R.string.menu_item_cancel,
                drawableId = R.drawable.ic_cancel,
                action = { GestureManager.instance.cancelLinearGesture() }
            )
        )
    )

    /**
     * Finger mode selection menu structure.
     * 
     * This menu allows users to select between different finger modes for the
     * multi-finger gesture system. The selected mode affects how many fingers
     * are used for gesture execution across all gesture types (except zoom).
     */
    val fingerModeMenuObject = MenuStructure(
        id = "finger_mode_menu",
        items = listOf(
            MenuItem(
                id = "one_finger_mode",
                labelResource = R.string.menu_item_one_finger,
                drawableId = R.drawable.ic_one_finger, // One finger mode icon
                closeOnSelect = false, // Keep menu open to show feedback
                action = {
                    GestureManager.instance.setFingerMode(FingerMode.ONE)
                    // Show feedback using string resource with format arguments
                    ServiceMessageHUD.instance.showMessage(
                        R.string.finger_mode_changed,
                        arrayOf(FingerMode.ONE.getDisplayName()),
                        ServiceMessageHUD.MessageType.DISAPPEARING
                    )
                }
            ),
            MenuItem(
                id = "two_finger_mode",
                labelResource = R.string.menu_item_two_fingers,
                drawableId = R.drawable.ic_two_finger, // Two finger mode icon
                closeOnSelect = false, // Keep menu open to show feedback
                action = {
                    GestureManager.instance.setFingerMode(FingerMode.TWO)
                    ServiceMessageHUD.instance.showMessage(
                        R.string.finger_mode_changed,
                        arrayOf(FingerMode.TWO.getDisplayName()),
                        ServiceMessageHUD.MessageType.DISAPPEARING
                    )
                }
            )
        )
    )
} 