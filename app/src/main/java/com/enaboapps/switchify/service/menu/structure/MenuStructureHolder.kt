package com.enaboapps.switchify.service.menu.structure

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.accessibility.AccessibilityNodeInfo
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.iap.IAPHandler
import com.enaboapps.switchify.service.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.custom.actions.ActionPerformer
import com.enaboapps.switchify.service.custom.actions.store.ActionStore
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.methods.nodes.Node
import com.enaboapps.switchify.service.methods.nodes.NodeExaminer
import com.enaboapps.switchify.service.scanning.ScanMethod
import com.enaboapps.switchify.service.window.ServiceMessageHUD

class MenuStructureHolder(private val accessibilityService: SwitchifyAccessibilityService? = null) {
    private val tapMenuItem = MenuItem(
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

    private val openVolumeControlMenu = MenuItem(
        id = "volume_control",
        text = "Volume Control",
        isLinkToMenu = true,
        action = { MenuManager.getInstance().openVolumeControlMenu() }
    )

    /**
     * The system navigation items
     */
    val systemNavItems = listOf(
        MenuItem(
            id = "sys_back",
            drawableId = R.drawable.ic_sys_back,
            drawableDescription = "Back",
            action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK) }
        ),
        MenuItem(
            id = "sys_home",
            drawableId = R.drawable.ic_sys_home,
            drawableDescription = "Home",
            action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME) }
        )
    )

    /**
     * The navigation menu items
     */
    val menuManipulatorItems = listOfNotNull(
        if (MenuManager.getInstance().menuHierarchy?.getTopMenu() != null) {
            MenuItem(
                id = "previous_menu",
                drawableId = R.drawable.ic_previous_menu,
                drawableDescription = "Previous menu",
                showDrawableDescription = false,
                isSmall = true,
                isMenuHierarchyManipulator = true,
                action = { MenuManager.getInstance().menuHierarchy?.popMenu() }
            )
        } else null,
        MenuItem(
            id = "close_menu",
            drawableId = R.drawable.ic_close_menu,
            drawableDescription = "Close menu",
            showDrawableDescription = false,
            isSmall = true,
            isMenuHierarchyManipulator = true,
            action = { MenuManager.getInstance().closeMenuHierarchy() }
        )
    )

    /**
     * The main menu item store object
     */
    val mainMenuObject = MenuStructure(
        id = "main_menu",
        items = listOfNotNull(
            tapMenuItem,
            MenuItem(
                id = "gestures",
                text = "Gestures",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openGesturesMenu() }
            ),
            MenuItem(
                id = "scroll",
                text = "Scroll",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openScrollMenu() }
            ),
            if (ScanMethod.getType() != ScanMethod.MethodType.ITEM_SCAN && ScanMethod.getType() != ScanMethod.MethodType.RADAR) {
                MenuItem(
                    id = "refine_selection",
                    text = "Refine Selection",
                    action = { GesturePoint.setReselect(true) }
                )
            } else null,
            MenuItem(
                id = "device",
                text = "Device",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openDeviceMenu() }
            ),
            MenuItem(
                id = "media_control",
                text = "Media Control",
                isLinkToMenu = true,
                action = { MenuManager.getInstance().openMediaControlMenu() }
            ),
            if (NodeExaminer.canPerformEditActions(GesturePoint.getPoint())) {
                MenuItem(
                    id = "edit",
                    text = "Edit",
                    isLinkToMenu = true,
                    action = { MenuManager.getInstance().openEditMenu() }
                )
            } else null,
            if (ScanMethod.getType() != ScanMethod.MethodType.ITEM_SCAN) {
                MenuItem(
                    id = "switch_to_item_scan",
                    text = ScanMethod.getName(ScanMethod.MethodType.ITEM_SCAN),
                    action = {
                        MenuManager.getInstance().switchToItemScan()
                    }
                )
            } else null,
            if (ScanMethod.getType() != ScanMethod.MethodType.RADAR) {
                MenuItem(
                    id = "switch_to_radar",
                    text = ScanMethod.getName(ScanMethod.MethodType.RADAR),
                    action = {
                        MenuManager.getInstance().switchToRadar()
                    }
                )
            } else null,
            if (ScanMethod.getType() != ScanMethod.MethodType.CURSOR) {
                MenuItem(
                    id = "switch_to_cursor",
                    text = ScanMethod.getName(ScanMethod.MethodType.CURSOR),
                    action = {
                        MenuManager.getInstance().switchToCursor()
                    }
                )
            } else null,
            if (accessibilityService != null && !ActionStore(accessibilityService).isEmpty()) {
                MenuItem(
                    id = "my_actions",
                    text = "My Actions",
                    isLinkToMenu = true,
                    action = {
                        MenuManager.getInstance().openMyActionsMenu()
                    }
                )
            } else null,
        )
    )

    /**
     * The gestures menu item store object
     */
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

    /**
     * The swipe gestures menu item store object
     */
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

    /**
     * The tap gestures menu item store object
     */
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

    /**
     * The zoom gestures menu item store object
     */
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

    /**
     * The device menu item store object
     */
    fun buildDeviceMenuObject(): MenuStructure {
        return MenuStructure(
            id = "device_menu",
            items = listOfNotNull(
                MenuItem(
                    id = "recent_apps",
                    text = "Recent Apps",
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Recent Apps is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "notifications",
                    text = "Notifications",
                    action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
                ),
                MenuItem(
                    id = "open_assistant",
                    text = "Open Assistant",
                    action = {
                        val intent = Intent(Intent.ACTION_VOICE_COMMAND)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        accessibilityService?.startActivity(intent)
                    }
                ),
                MenuItem(
                    id = "quick_settings",
                    text = "Quick Settings",
                    action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS) }
                ),
                MenuItem(
                    id = "lock_screen",
                    text = "Lock Screen",
                    action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
                ),
                MenuItem(
                    id = "power_dialog",
                    text = "Power Dialog",
                    action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG) }
                ),
                openVolumeControlMenu
            )
        )
    }

    /**
     * The volume control menu item store object
     */
    fun buildVolumeControlMenuObject(): MenuStructure {
        return MenuStructure(
            id = "volume_control_menu",
            items = listOf(
                MenuItem(
                    id = "volume_up",
                    text = "Volume Up",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.adjustStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    AudioManager.ADJUST_RAISE,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "volume_down",
                    text = "Volume Down",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.adjustStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    AudioManager.ADJUST_LOWER,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "full_volume",
                    text = "Full Volume",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY),
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "mute",
                    text = "Mute",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    0,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                ),
                MenuItem(
                    id = "half_volume",
                    text = "Half Volume",
                    closeOnSelect = false,
                    action = {
                        if (IAPHandler.hasPurchasedPro()) {
                            accessibilityService?.let { service ->
                                val audioManager =
                                    service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                                val halfVolume =
                                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ACCESSIBILITY) / 2
                                audioManager.setStreamVolume(
                                    AudioManager.STREAM_ACCESSIBILITY,
                                    halfVolume,
                                    AudioManager.FLAG_SHOW_UI
                                )
                            }
                        } else {
                            ServiceMessageHUD.instance.showMessage(
                                "Volume control is a pro feature. Please purchase Switchify Pro to use it.",
                                ServiceMessageHUD.MessageType.DISAPPEARING
                            )
                        }
                    }
                )
            )
        )
    }

    /**
     * The media control menu item store object
     */
    val mediaControlMenuObject = MenuStructure(
        id = "media_control_menu",
        items = listOf(
            MenuItem(
                id = "play_pause",
                text = "Play/Pause",
                action = { accessibilityService?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_KEYCODE_HEADSETHOOK) }
            ),
            openVolumeControlMenu
        )
    )

    /**
     * The scroll menu item store object
     */
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
            toggleGestureLockMenuItem
        )
    )

    /**
     * The edit menu item store object
     */
    fun buildEditMenuObject(): MenuStructure {
        val currentPoint = GesturePoint.getPoint()
        val cutNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.CUT)
        val copyNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.COPY)
        val pasteNode = NodeExaminer.findNodeForAction(currentPoint, Node.ActionType.PASTE)
        return MenuStructure(
            id = "edit_menu",
            items = listOfNotNull(
                if (cutNode != null) {
                    MenuItem(
                        id = "cut",
                        text = "Cut",
                        action = {
                            cutNode.performAction(AccessibilityNodeInfo.ACTION_CUT)
                        }
                    )
                } else null,
                if (copyNode != null) {
                    MenuItem(
                        id = "copy",
                        text = "Copy",
                        action = {
                            copyNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
                        }
                    )
                } else null,
                if (pasteNode != null) {
                    MenuItem(
                        id = "paste",
                        text = "Paste",
                        action = {
                            pasteNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                        }
                    )
                } else null
            )
        )
    }

    /**
     * The my actions menu item store object
     */
    fun buildMyActionsMenuObject(): MenuStructure {
        if (accessibilityService == null) {
            return MenuStructure(id = "my_actions_menu", items = emptyList())
        }
        val actionStore = ActionStore(accessibilityService)
        val actions = actionStore.getActions()
        return MenuStructure(
            id = "my_actions_menu",
            items = actions.map { action ->
                MenuItem(
                    id = action.id,
                    text = action.text,
                    action = {
                        val actionPerformer = ActionPerformer(accessibilityService)
                        actionPerformer.performActionFromStore(action.id)
                    }
                )
            }
        )
    }

    /**
     * The menu for confirming, reselecting, and cancelling custom gestures
     */
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
