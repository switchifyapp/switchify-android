package com.enaboapps.switchify.service.menu.menus.gestures

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePatternRecorder
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.placement.FingerMode
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.window.ServiceMessageHUD

class GestureMenuStructure(private val context: Context) {
    val tapMenuItem = MenuItem(
        id = "tap",
        labelResource = R.string.menu_item_tap,
        drawableId = R.drawable.ic_gesture_tap,
        action = {
            GestureManager.instance.performTap()
        }
    )

    val toggleGestureLockMenuItem = MenuItem(
        id = "toggle_gesture_lock",
        labelResource = R.string.system_gesture_lock,
        drawableId = R.drawable.ic_toggle_gesture_lock,
        closeOnSelect = false,
        action = { GestureManager.instance.toggleGestureLock() }
    )

    val tapGesturesMenuObject = MenuStructure(
        id = "tap_gestures_menu",
        items = listOf(
            tapMenuItem,
            MenuItem(
                id = "double_tap",
                labelResource = R.string.menu_item_double_tap,
                drawableId = R.drawable.ic_gesture_double_tap,
                action = { GestureManager.instance.performDoubleTap() }
            ),
            MenuItem(
                id = "tap_and_hold_0_5s",
                labelResource = R.string.menu_item_tap_and_hold_0_5s,
                drawableId = R.drawable.ic_gesture_tap_hold_0_5s,
                action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_0_5S_DURATION, gestureType = GestureType.TAP_AND_HOLD_0_5S) }
            ),
            MenuItem(
                id = "tap_and_hold_1s",
                labelResource = R.string.menu_item_tap_and_hold_1s,
                drawableId = R.drawable.ic_gesture_tap_hold_1s,
                action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_1S_DURATION, gestureType = GestureType.TAP_AND_HOLD_1S) }
            ),
            MenuItem(
                id = "tap_and_hold_2s",
                labelResource = R.string.menu_item_tap_and_hold_2s,
                drawableId = R.drawable.ic_gesture_tap_hold_2s,
                action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_2S_DURATION, gestureType = GestureType.TAP_AND_HOLD_2S) }
            ),
            MenuItem(
                id = "tap_and_hold_3s",
                labelResource = R.string.menu_item_tap_and_hold_3s,
                drawableId = R.drawable.ic_gesture_tap_hold_3s,
                action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_3S_DURATION, gestureType = GestureType.TAP_AND_HOLD_3S) }
            ),
            MenuItem(
                id = "tap_and_hold_5s",
                labelResource = R.string.menu_item_tap_and_hold_5s,
                drawableId = R.drawable.ic_gesture_tap_hold_5s,
                action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_5S_DURATION, gestureType = GestureType.TAP_AND_HOLD_5S) }
            ),
            MenuItem(
                id = "tap_and_hold_10s",
                labelResource = R.string.menu_item_tap_and_hold_10s,
                drawableId = R.drawable.ic_gesture_tap_hold_10s,
                action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_10S_DURATION, gestureType = GestureType.TAP_AND_HOLD_10S) }
            ),
            toggleGestureLockMenuItem
        ),
        context = context
    )

    val gesturesMenuObject = MenuStructure(
        id = "gestures_menu",
        items = listOf(
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
                id = "pinch_gestures",
                labelResource = R.string.menu_item_pinch_gestures,
                drawableId = R.drawable.ic_pinch_gestures,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openPinchGesturesMenu() }
            ),
            MenuItem(
                id = "finger_mode",
                labelResource = R.string.menu_item_finger_mode,
                drawableId = R.drawable.ic_finger_mode,
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openFingerModeMenu() }
            ),
            toggleGestureLockMenuItem
        ),
        context = context
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
                ),
            context = context
        )
    }

    val swipeGesturesMenuObject = MenuStructure(
        id = "swipe_gestures_menu",
        items = listOf(
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
        ),
        context = context
    )

    val pinchGesturesMenuObject = MenuStructure(
        id = "pinch_gestures_menu",
        items = listOf(
            MenuItem(
                id = "pinch_in",
                labelResource = R.string.menu_item_pinch_in,
                drawableId = R.drawable.ic_gesture_pinch_in,
                action = {
                    GestureManager.instance
                        .performPinch(GestureType.PINCH_IN)
                }
            ),
            MenuItem(
                id = "pinch_out",
                labelResource = R.string.menu_item_pinch_out,
                drawableId = R.drawable.ic_gesture_pinch_out,
                action = {
                    GestureManager.instance
                        .performPinch(GestureType.PINCH_OUT)
                }
            ),
            toggleGestureLockMenuItem
        ),
        context = context
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
        ),
        context = context
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
                drawableId = R.drawable.ic_one_finger,
                closeOnSelect = false, // Keep menu open to show feedback
                action = {
                    GestureManager.instance.setFingerMode(FingerMode.ONE)
                    ServiceMessageHUD.instance.showMessage(
                        R.string.finger_mode_changed,
                        arrayOf(FingerMode.ONE.getDisplayName(context)),
                        ServiceMessageHUD.MessageType.DISAPPEARING
                    )
                }
            ),
            MenuItem(
                id = "two_finger_mode",
                labelResource = R.string.menu_item_two_fingers,
                drawableId = R.drawable.ic_two_finger,
                closeOnSelect = false, // Keep menu open to show feedback
                action = {
                    GestureManager.instance.setFingerMode(FingerMode.TWO)
                    ServiceMessageHUD.instance.showMessage(
                        R.string.finger_mode_changed,
                        arrayOf(FingerMode.TWO.getDisplayName(context)),
                        ServiceMessageHUD.MessageType.DISAPPEARING
                    )
                }
            ),
            MenuItem(
                id = "three_finger_mode",
                labelResource = R.string.menu_item_three_fingers,
                drawableId = R.drawable.ic_three_finger,
                closeOnSelect = false, // Keep menu open to show feedback
                action = {
                    GestureManager.instance.setFingerMode(FingerMode.THREE)
                    ServiceMessageHUD.instance.showMessage(
                        R.string.finger_mode_changed,
                        arrayOf(FingerMode.THREE.getDisplayName(context)),
                        ServiceMessageHUD.MessageType.DISAPPEARING
                    )
                }
            ),
            MenuItem(
                id = "four_finger_mode",
                labelResource = R.string.menu_item_four_fingers,
                drawableId = R.drawable.ic_four_finger,
                closeOnSelect = false, // Keep menu open to show feedback
                action = {
                    GestureManager.instance.setFingerMode(FingerMode.FOUR)
                    ServiceMessageHUD.instance.showMessage(
                        R.string.finger_mode_changed,
                        arrayOf(FingerMode.FOUR.getDisplayName(context)),
                        ServiceMessageHUD.MessageType.DISAPPEARING
                    )
                }
            ),
            MenuItem(
                id = "five_finger_mode",
                labelResource = R.string.menu_item_five_fingers,
                drawableId = R.drawable.ic_five_finger,
                closeOnSelect = false, // Keep menu open to show feedback
                action = {
                    GestureManager.instance.setFingerMode(FingerMode.FIVE)
                    ServiceMessageHUD.instance.showMessage(
                        R.string.finger_mode_changed,
                        arrayOf(FingerMode.FIVE.getDisplayName(context)),
                        ServiceMessageHUD.MessageType.DISAPPEARING
                    )
                }
            )
        ),
        context = context
    )
} 