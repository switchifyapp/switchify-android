package com.enaboapps.switchify.service.menu.menus.gestures

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePatternRecorder
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.placement.FingerMode
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope

class GestureMenuStructure(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    val tapMenuItem: MenuItem? = MenuItemRegistry.getDefinition("tap_gestures_menu", "tap")?.let { def ->
        MenuItem(
            definition = def,
            action = { GestureManager.instance.performTap() }
        )
    }

    val toggleGestureLockMenuItem: MenuItem? = MenuItemRegistry.getDefinition("tap_gestures_menu", "toggle_gesture_lock")?.let { def ->
        MenuItem(
            definition = def,
            closeOnSelect = false,
            action = { GestureManager.instance.toggleGestureLock() }
        )
    }

    val tapGesturesMenuObject = MenuStructure(
        id = "tap_gestures_menu",
        items = listOfNotNull(
            tapMenuItem,
            MenuItemRegistry.getDefinition("tap_gestures_menu", "double_tap")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performDoubleTap() }
                )
            },
            MenuItemRegistry.getDefinition("tap_gestures_menu", "tap_and_hold")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openTapAndHoldMenu() }
                )
            },
            toggleGestureLockMenuItem
        ),
        context = context,
        coroutineScope = coroutineScope
    )

    val tapAndHoldGesturesMenuObject = MenuStructure(
        id = "tap_and_hold_menu",
        items = listOfNotNull(
            MenuItemRegistry.getDefinition("tap_and_hold_menu", "tap_and_hold_0_5s")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_0_5S_DURATION, gestureType = GestureType.TAP_AND_HOLD_0_5S) }
                )
            },
            MenuItemRegistry.getDefinition("tap_and_hold_menu", "tap_and_hold_1s")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_1S_DURATION, gestureType = GestureType.TAP_AND_HOLD_1S) }
                )
            },
            MenuItemRegistry.getDefinition("tap_and_hold_menu", "tap_and_hold_2s")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_2S_DURATION, gestureType = GestureType.TAP_AND_HOLD_2S) }
                )
            },
            MenuItemRegistry.getDefinition("tap_and_hold_menu", "tap_and_hold_3s")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_3S_DURATION, gestureType = GestureType.TAP_AND_HOLD_3S) }
                )
            },
            MenuItemRegistry.getDefinition("tap_and_hold_menu", "tap_and_hold_5s")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_5S_DURATION, gestureType = GestureType.TAP_AND_HOLD_5S) }
                )
            },
            MenuItemRegistry.getDefinition("tap_and_hold_menu", "tap_and_hold_10s")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performTapAndHold(duration = GestureData.TAP_AND_HOLD_10S_DURATION, gestureType = GestureType.TAP_AND_HOLD_10S) }
                )
            }
        ),
        context = context,
        coroutineScope = coroutineScope
    )

    val gesturesMenuObject = MenuStructure(
        id = "gestures_menu",
        items = listOfNotNull(
            MenuItemRegistry.getDefinition("gestures_menu", "tap_gestures")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openTapMenu() }
                )
            },
            MenuItemRegistry.getDefinition("gestures_menu", "swipe_gestures")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openSwipeMenu() }
                )
            },
            MenuItemRegistry.getDefinition("gestures_menu", "drag")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.startDragGesture() }
                )
            },
            MenuItemRegistry.getDefinition("gestures_menu", "pinch_gestures")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openPinchGesturesMenu() }
                )
            },
            MenuItemRegistry.getDefinition("gestures_menu", "finger_mode")?.let { def ->
                MenuItem(
                    definition = def,
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openFingerModeMenu() }
                )
            },
            toggleGestureLockMenuItem
        ),
        context = context,
        coroutineScope = coroutineScope
    )

    /**
     * Creates the gesture patterns menu structure used to start, save, or cancel gesture recording.
     *
     * The menu's items vary with `GesturePatternRecorder` state:
     * - When recording and at least one gesture has been recorded, includes a `save_recording` item.
     * - When not recording, includes a `start_recording` item.
     * - When recording, includes a `cancel_recording` item.
     *
     * @return A `MenuStructure` with id "gesture_patterns_menu" containing the appropriate start/save/cancel recording items based on the recorder's current state.
     */
    fun createGesturePatternsMenuStructure(): MenuStructure {
        return MenuStructure(
            id = "gesture_patterns_menu",
            items =
                listOfNotNull(
                    if (GesturePatternRecorder.isRecording() && GesturePatternRecorder.getRecordedGestureCount() > 0) {
                        MenuItem(
                            id = "save_recording",
                            labelResource = R.string.save_recording,
                            descriptionResource = R.string.menu_item_save_recording_description,
                            drawableId = R.drawable.ic_save_recording,
                            action = { GesturePatternRecorder.saveRecording(context) }
                        )
                    } else if (!GesturePatternRecorder.isRecording()) {
                        MenuItem(
                            id = "start_recording",
                            labelResource = R.string.start_recording,
                            descriptionResource = R.string.menu_item_start_recording_description,
                            drawableId = R.drawable.ic_start_recording,
                            action = { GesturePatternRecorder.startRecording(context) }
                        )
                    } else null,
                    if (GesturePatternRecorder.isRecording()) {
                        MenuItem(
                            id = "cancel_recording",
                            labelResource = R.string.cancel_recording,
                            descriptionResource = R.string.menu_item_cancel_recording_description,
                            drawableId = R.drawable.ic_cancel_recording,
                            action = { GesturePatternRecorder.cancelRecording() }
                        )
                    } else null
                ),
            context = context,
            coroutineScope = coroutineScope
        )
    }

    val swipeGesturesMenuObject = MenuStructure(
        id = "swipe_gestures_menu",
        items = listOfNotNull(
            MenuItemRegistry.getDefinition("swipe_gestures_menu", "swipe_up")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_UP) }
                )
            },
            MenuItemRegistry.getDefinition("swipe_gestures_menu", "swipe_down")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_DOWN) }
                )
            },
            MenuItemRegistry.getDefinition("swipe_gestures_menu", "swipe_left")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_LEFT) }
                )
            },
            MenuItemRegistry.getDefinition("swipe_gestures_menu", "swipe_right")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performSwipeOrScroll(GestureType.SWIPE_RIGHT) }
                )
            },
            MenuItemRegistry.getDefinition("swipe_gestures_menu", "custom_swipe")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.startCustomSwipe() }
                )
            },
            toggleGestureLockMenuItem
        ),
        context = context,
        coroutineScope = coroutineScope
    )

    val pinchGesturesMenuObject = MenuStructure(
        id = "pinch_gestures_menu",
        items = listOfNotNull(
            MenuItemRegistry.getDefinition("pinch_gestures_menu", "pinch_in")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performPinch(GestureType.PINCH_IN) }
                )
            },
            MenuItemRegistry.getDefinition("pinch_gestures_menu", "pinch_out")?.let { def ->
                MenuItem(
                    definition = def,
                    action = { GestureManager.instance.performPinch(GestureType.PINCH_OUT) }
                )
            },
            toggleGestureLockMenuItem
        ),
        context = context,
        coroutineScope = coroutineScope
    )

    val customGestureConfirmationMenuObject = MenuStructure(
        id = "custom_gesture_confirmation_menu",
        items = listOf(
            MenuItem(
                id = "confirm",
                labelResource = R.string.menu_item_confirm,
                descriptionResource = R.string.menu_item_confirm_description,
                drawableId = R.drawable.ic_confirm,
                action = { GestureManager.instance.endLinearGesture() }
            ),
            MenuItem(
                id = "reselect",
                labelResource = R.string.menu_item_reselect,
                descriptionResource = R.string.menu_item_reselect_description,
                drawableId = R.drawable.ic_reselect,
                action = {
                    // Do nothing for reselect action
                }
            ),
            MenuItem(
                id = "cancel",
                labelResource = R.string.menu_item_cancel,
                descriptionResource = R.string.menu_item_cancel_description,
                drawableId = R.drawable.ic_cancel,
                action = { GestureManager.instance.cancelLinearGesture() }
            )
        ),
        context = context,
        coroutineScope = coroutineScope
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
                descriptionResource = R.string.finger_mode_description_one,
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
                descriptionResource = R.string.finger_mode_description_two,
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
                descriptionResource = R.string.finger_mode_description_three,
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
                descriptionResource = R.string.finger_mode_description_four,
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
                descriptionResource = R.string.finger_mode_description_five,
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
        context = context,
        coroutineScope = coroutineScope
    )
} 