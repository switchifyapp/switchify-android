package com.enaboapps.switchify.service.menu.structure

import com.enaboapps.switchify.R

/**
 * Single source of truth for all menu item definitions.
 * This object maintains the metadata for all menu items across the entire application.
 * Menu structures and UI screens should use this registry to get item definitions.
 */
object MenuItemRegistry {

    /**
     * Provide the set of menu item definitions for the app's main menu.
     *
     * @return A list of MenuItemDefinition for each main menu entry containing its `id`, `labelResource`, and `drawableId`.
     */
    fun getMainMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Main.SYS_BACK, labelResource = R.string.system_back, descriptionResource = R.string.menu_item_sys_back_description, drawableId = R.drawable.ic_sys_back),
            MenuItemDefinition(MenuConstants.ItemIds.Main.SYS_HOME, labelResource = R.string.system_home, descriptionResource = R.string.menu_item_sys_home_description, drawableId = R.drawable.ic_sys_home),
            MenuItemDefinition(MenuConstants.ItemIds.Main.SCAN_KEYBOARD, labelResource = R.string.menu_item_scan_keyboard, descriptionResource = R.string.menu_item_scan_keyboard_description, drawableId = R.drawable.ic_scan_keyboard),
            MenuItemDefinition(MenuConstants.ItemIds.Main.TAP, labelResource = R.string.menu_item_tap, descriptionResource = R.string.menu_item_tap_description, drawableId = R.drawable.ic_gesture_tap),
            MenuItemDefinition(MenuConstants.ItemIds.Main.GESTURES, labelResource = R.string.menu_title_gestures, descriptionResource = R.string.menu_item_gestures_description, drawableId = R.drawable.ic_gestures),
            MenuItemDefinition(MenuConstants.ItemIds.Main.SCROLL, labelResource = R.string.menu_title_scroll, descriptionResource = R.string.menu_item_scroll_description, drawableId = R.drawable.ic_scroll),
            MenuItemDefinition(MenuConstants.ItemIds.Main.FAVOURITE_APPS, labelResource = R.string.menu_title_favourite_apps, descriptionResource = R.string.menu_item_favourite_apps_description, drawableId = R.drawable.ic_favourite_apps),
            MenuItemDefinition(MenuConstants.ItemIds.Main.GESTURE_PATTERNS, labelResource = R.string.gesture_patterns_title, descriptionResource = R.string.menu_item_gesture_patterns_description, drawableId = R.drawable.ic_gesture_patterns),
            MenuItemDefinition(MenuConstants.ItemIds.Main.DEVICE, labelResource = R.string.menu_title_device, descriptionResource = R.string.menu_item_device_description, drawableId = R.drawable.ic_device),
            MenuItemDefinition(MenuConstants.ItemIds.Main.SETTINGS, labelResource = R.string.menu_item_settings, descriptionResource = R.string.menu_item_settings_description, drawableId = R.drawable.ic_settings),
            MenuItemDefinition(MenuConstants.ItemIds.Main.MEDIA_CONTROL, labelResource = R.string.menu_title_media_control, descriptionResource = R.string.menu_item_media_control_description, drawableId = R.drawable.ic_media_control),
            MenuItemDefinition(MenuConstants.ItemIds.Main.CONTROL_PC, labelResource = R.string.menu_item_control_pc, descriptionResource = R.string.menu_item_control_pc_description, drawableId = R.drawable.ic_control_pc),
            MenuItemDefinition(MenuConstants.ItemIds.Main.EDIT, labelResource = R.string.menu_title_edit, descriptionResource = R.string.menu_item_edit_description, drawableId = R.drawable.ic_edit),
            MenuItemDefinition(MenuConstants.ItemIds.Main.AI, labelResource = R.string.menu_title_ai, descriptionResource = R.string.menu_item_ai_description, drawableId = R.drawable.ic_ai),
            MenuItemDefinition(MenuConstants.ItemIds.Main.PAUSE, labelResource = R.string.menu_item_pause, descriptionResource = R.string.menu_item_pause_description, drawableId = R.drawable.ic_pause)
        )
    }

    /**
     * Provides menu item definitions for the Settings submenu.
     *
     * @return A list of MenuItemDefinition for the technique switches and head-control toggle.
     */
    fun getSettingsMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Settings.SWITCH_TO_ITEM_SCAN, labelResource = R.string.access_technique_item_scan, descriptionResource = R.string.menu_item_switch_to_item_scan_description, drawableId = R.drawable.ic_item_scan),
            MenuItemDefinition(MenuConstants.ItemIds.Settings.SWITCH_TO_RADAR, labelResource = R.string.access_technique_radar, descriptionResource = R.string.menu_item_switch_to_radar_description, drawableId = R.drawable.ic_radar),
            MenuItemDefinition(MenuConstants.ItemIds.Settings.SWITCH_TO_POINT_SCAN, labelResource = R.string.access_technique_point_scan, descriptionResource = R.string.menu_item_switch_to_point_scan_description, drawableId = R.drawable.ic_point_scan),
            MenuItemDefinition(MenuConstants.ItemIds.Settings.TOGGLE_HEAD_CONTROL, labelResource = R.string.menu_item_enable_head_control, descriptionResource = R.string.menu_item_toggle_head_control_description, drawableId = R.drawable.ic_head_control_pointer),
            MenuItemDefinition(MenuConstants.ItemIds.Settings.TOGGLE_GROUP_SCAN, labelResource = R.string.menu_item_group_scan, descriptionResource = R.string.menu_item_group_scan_description, drawableId = R.drawable.ic_toggle_group_scan),
            MenuItemDefinition(MenuConstants.ItemIds.Settings.TOGGLE_GESTURE_LOCK_REARM, labelResource = R.string.preference_title_gesture_lock_auto_reenable, descriptionResource = R.string.menu_item_gesture_lock_rearm_description, drawableId = R.drawable.ic_toggle_gesture_lock),
            MenuItemDefinition(MenuConstants.ItemIds.Settings.TOGGLE_GESTURE_REPEAT, labelResource = R.string.preference_title_gesture_repeat, descriptionResource = R.string.menu_item_gesture_repeat_description, drawableId = R.drawable.ic_toggle_gesture_lock)
        )
    }

    /**
     * Provides menu item definitions for device-related actions.
     *
     * @return A list of MenuItemDefinition representing device menu entries: recent apps, notifications, assistant, quick settings, lock screen, power dialog, screenshot, and volume control.
     */
    fun getDeviceMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Device.RECENT_APPS, labelResource = R.string.system_recents, descriptionResource = R.string.menu_item_recent_apps_description, drawableId = R.drawable.ic_recent_apps),
            MenuItemDefinition(MenuConstants.ItemIds.Device.NOTIFICATIONS, labelResource = R.string.system_notifications, descriptionResource = R.string.menu_item_notifications_description, drawableId = R.drawable.ic_notifications),
            MenuItemDefinition(MenuConstants.ItemIds.Device.OPEN_ASSISTANT, labelResource = R.string.system_assistant, descriptionResource = R.string.menu_item_open_assistant_description, drawableId = R.drawable.ic_assistant),
            MenuItemDefinition(MenuConstants.ItemIds.Device.QUICK_SETTINGS, labelResource = R.string.system_quick_settings, descriptionResource = R.string.menu_item_quick_settings_description, drawableId = R.drawable.ic_quick_settings),
            MenuItemDefinition(MenuConstants.ItemIds.Device.LOCK_SCREEN, labelResource = R.string.system_lock_screen, descriptionResource = R.string.menu_item_lock_screen_description, drawableId = R.drawable.ic_lock_screen),
            MenuItemDefinition(MenuConstants.ItemIds.Device.POWER_DIALOG, labelResource = R.string.system_power_dialog, descriptionResource = R.string.menu_item_power_dialog_description, drawableId = R.drawable.ic_power_dialog),
            MenuItemDefinition(MenuConstants.ItemIds.Device.TAKE_SCREENSHOT, labelResource = R.string.system_screenshot, descriptionResource = R.string.menu_item_take_screenshot_description, drawableId = R.drawable.ic_screenshot),
            MenuItemDefinition(MenuConstants.ItemIds.Device.VOLUME_CONTROL, labelResource = R.string.action_volume_control, descriptionResource = R.string.menu_item_volume_control_description, drawableId = R.drawable.ic_volume_control)
        )
    }

    /**
     * Provides menu item definitions for volume control options.
     *
     * @return A list of MenuItemDefinition for the volume actions `volume_up`, `volume_down`,
     * `full_volume`, `mute`, and `half_volume`.
     */
    fun getVolumeControlMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Volume.VOLUME_UP, labelResource = R.string.menu_item_volume_up, descriptionResource = R.string.menu_item_volume_up_description, drawableId = R.drawable.ic_volume_up),
            MenuItemDefinition(MenuConstants.ItemIds.Volume.VOLUME_DOWN, labelResource = R.string.menu_item_volume_down, descriptionResource = R.string.menu_item_volume_down_description, drawableId = R.drawable.ic_volume_down),
            MenuItemDefinition(MenuConstants.ItemIds.Volume.FULL_VOLUME, labelResource = R.string.menu_item_full_volume, descriptionResource = R.string.menu_item_full_volume_description, drawableId = R.drawable.ic_full_volume),
            MenuItemDefinition(MenuConstants.ItemIds.Volume.MUTE, labelResource = R.string.menu_item_mute, descriptionResource = R.string.menu_item_mute_description, drawableId = R.drawable.ic_mute),
            MenuItemDefinition(MenuConstants.ItemIds.Volume.HALF_VOLUME, labelResource = R.string.menu_item_half_volume, descriptionResource = R.string.menu_item_half_volume_description, drawableId = R.drawable.ic_half_volume)
        )
    }

    /**
     * Provides menu item definitions for gesture-related actions.
     *
     * @return A list of MenuItemDefinition objects for gesture menus including tap gestures, swipe gestures, drag, pinch gestures, finger mode, and a gesture-lock toggle.
     */
    fun getGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Gestures.TAP_GESTURES, labelResource = R.string.menu_item_tap_gestures, descriptionResource = R.string.menu_item_tap_gestures_description, drawableId = R.drawable.ic_tap_gestures),
            MenuItemDefinition(MenuConstants.ItemIds.Gestures.SWIPE_GESTURES, labelResource = R.string.menu_item_swipe_gestures, descriptionResource = R.string.menu_item_swipe_gestures_description, drawableId = R.drawable.ic_swipe_gestures),
            MenuItemDefinition(MenuConstants.ItemIds.Gestures.DRAG, labelResource = R.string.menu_item_drag, descriptionResource = R.string.menu_item_drag_description, drawableId = R.drawable.ic_gesture_drag),
            MenuItemDefinition(MenuConstants.ItemIds.Gestures.PINCH_GESTURES, labelResource = R.string.menu_item_pinch_gestures, descriptionResource = R.string.menu_item_pinch_gestures_description, drawableId = R.drawable.ic_pinch_gestures),
            MenuItemDefinition(MenuConstants.ItemIds.Gestures.FINGER_MODE, labelResource = R.string.menu_item_finger_mode, descriptionResource = R.string.menu_item_finger_mode_description, drawableId = R.drawable.ic_finger_mode),
            MenuItemDefinition(MenuConstants.ItemIds.Gestures.TOGGLE_GESTURE_LOCK, labelResource = R.string.system_gesture_lock, descriptionResource = R.string.menu_item_toggle_gesture_lock_description, drawableId = R.drawable.ic_toggle_gesture_lock)
        )
    }

    /**
     * Tap-related gesture menu item definitions for the gestures submenu.
     *
     * @return A list of MenuItemDefinition for tap gestures, including tap, double tap, a link to the tap-and-hold submenu, and a gesture lock toggle.
     */
    fun getTapGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.TapGestures.TAP, labelResource = R.string.menu_item_tap, descriptionResource = R.string.menu_item_tap_description, drawableId = R.drawable.ic_gesture_tap),
            MenuItemDefinition(MenuConstants.ItemIds.TapGestures.DOUBLE_TAP, labelResource = R.string.menu_item_double_tap, descriptionResource = R.string.menu_item_double_tap_description, drawableId = R.drawable.ic_gesture_double_tap),
            MenuItemDefinition(MenuConstants.ItemIds.TapGestures.TAP_AND_HOLD, labelResource = R.string.menu_item_tap_and_hold, descriptionResource = R.string.menu_item_tap_and_hold_description, drawableId = R.drawable.ic_gesture_tap_hold_1s),
            MenuItemDefinition(MenuConstants.ItemIds.TapGestures.TOGGLE_GESTURE_LOCK, labelResource = R.string.system_gesture_lock, descriptionResource = R.string.menu_item_toggle_gesture_lock_description, drawableId = R.drawable.ic_toggle_gesture_lock)
        )
    }

    /**
     * Tap-and-hold gesture menu item definitions for the tap-and-hold submenu.
     *
     * The duration items render as text inside the menu circle ("0.5s", "1s",
     * "2s"…) instead of icons; the duration value reads more clearly as text
     * than as an arbitrary glyph.
     *
     * @return A list of MenuItemDefinition for the six tap-and-hold durations (0.5s, 1s, 2s, 3s, 5s, 10s).
     */
    fun getTapAndHoldGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.TapAndHold.TAP_AND_HOLD_0_5S, labelResource = R.string.menu_item_tap_and_hold_0_5s, descriptionResource = R.string.menu_item_tap_and_hold_0_5s_description, circleText = "0.5s"),
            MenuItemDefinition(MenuConstants.ItemIds.TapAndHold.TAP_AND_HOLD_1S, labelResource = R.string.menu_item_tap_and_hold_1s, descriptionResource = R.string.menu_item_tap_and_hold_1s_description, circleText = "1s"),
            MenuItemDefinition(MenuConstants.ItemIds.TapAndHold.TAP_AND_HOLD_2S, labelResource = R.string.menu_item_tap_and_hold_2s, descriptionResource = R.string.menu_item_tap_and_hold_2s_description, circleText = "2s"),
            MenuItemDefinition(MenuConstants.ItemIds.TapAndHold.TAP_AND_HOLD_3S, labelResource = R.string.menu_item_tap_and_hold_3s, descriptionResource = R.string.menu_item_tap_and_hold_3s_description, circleText = "3s"),
            MenuItemDefinition(MenuConstants.ItemIds.TapAndHold.TAP_AND_HOLD_5S, labelResource = R.string.menu_item_tap_and_hold_5s, descriptionResource = R.string.menu_item_tap_and_hold_5s_description, circleText = "5s"),
            MenuItemDefinition(MenuConstants.ItemIds.TapAndHold.TAP_AND_HOLD_10S, labelResource = R.string.menu_item_tap_and_hold_10s, descriptionResource = R.string.menu_item_tap_and_hold_10s_description, circleText = "10s")
        )
    }

    /**
     * Provide definitions for the swipe gestures submenu.
     *
     * @return A list of MenuItemDefinition entries for swipe gestures (up, down, left, right), a custom swipe action, and a gesture lock toggle; each entry contains an id, a label resource, and a drawable id.
     */
    fun getSwipeGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.SwipeGestures.SWIPE_UP, labelResource = R.string.menu_item_swipe_up, descriptionResource = R.string.menu_item_swipe_up_description, drawableId = R.drawable.ic_gesture_swipe_up),
            MenuItemDefinition(MenuConstants.ItemIds.SwipeGestures.SWIPE_DOWN, labelResource = R.string.menu_item_swipe_down, descriptionResource = R.string.menu_item_swipe_down_description, drawableId = R.drawable.ic_gesture_swipe_down),
            MenuItemDefinition(MenuConstants.ItemIds.SwipeGestures.SWIPE_LEFT, labelResource = R.string.menu_item_swipe_left, descriptionResource = R.string.menu_item_swipe_left_description, drawableId = R.drawable.ic_gesture_swipe_left),
            MenuItemDefinition(MenuConstants.ItemIds.SwipeGestures.SWIPE_RIGHT, labelResource = R.string.menu_item_swipe_right, descriptionResource = R.string.menu_item_swipe_right_description, drawableId = R.drawable.ic_gesture_swipe_right),
            MenuItemDefinition(MenuConstants.ItemIds.SwipeGestures.CUSTOM_SWIPE, labelResource = R.string.menu_item_custom_swipe, descriptionResource = R.string.menu_item_custom_swipe_description, drawableId = R.drawable.ic_gesture_custom_swipe),
            MenuItemDefinition(MenuConstants.ItemIds.SwipeGestures.TOGGLE_GESTURE_LOCK, labelResource = R.string.system_gesture_lock, descriptionResource = R.string.menu_item_toggle_gesture_lock_description, drawableId = R.drawable.ic_toggle_gesture_lock)
        )
    }

    /**
     * Provide menu item definitions for pinch gesture actions.
     *
     * @return A list of MenuItemDefinition for pinch-in, pinch-out, and the gesture lock toggle.
     */
    fun getPinchGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.PinchGestures.PINCH_IN, labelResource = R.string.menu_item_pinch_in, descriptionResource = R.string.menu_item_pinch_in_description, drawableId = R.drawable.ic_gesture_pinch_in),
            MenuItemDefinition(MenuConstants.ItemIds.PinchGestures.PINCH_OUT, labelResource = R.string.menu_item_pinch_out, descriptionResource = R.string.menu_item_pinch_out_description, drawableId = R.drawable.ic_gesture_pinch_out),
            MenuItemDefinition(MenuConstants.ItemIds.PinchGestures.TOGGLE_GESTURE_LOCK, labelResource = R.string.system_gesture_lock, descriptionResource = R.string.menu_item_toggle_gesture_lock_description, drawableId = R.drawable.ic_toggle_gesture_lock)
        )
    }

    /**
     * Provides menu item definitions for scroll actions.
     *
     * @return A list of MenuItemDefinition for scroll up, scroll down, scroll left, and scroll right,
     * each populated with the corresponding label and drawable resource IDs.
     */
    fun getScrollMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Scroll.SCROLL_UP, labelResource = R.string.menu_item_scroll_up, descriptionResource = R.string.menu_item_scroll_up_description, drawableId = R.drawable.ic_scroll_up),
            MenuItemDefinition(MenuConstants.ItemIds.Scroll.SCROLL_DOWN, labelResource = R.string.menu_item_scroll_down, descriptionResource = R.string.menu_item_scroll_down_description, drawableId = R.drawable.ic_scroll_down),
            MenuItemDefinition(MenuConstants.ItemIds.Scroll.SCROLL_LEFT, labelResource = R.string.menu_item_scroll_left, descriptionResource = R.string.menu_item_scroll_left_description, drawableId = R.drawable.ic_scroll_left),
            MenuItemDefinition(MenuConstants.ItemIds.Scroll.SCROLL_RIGHT, labelResource = R.string.menu_item_scroll_right, descriptionResource = R.string.menu_item_scroll_right_description, drawableId = R.drawable.ic_scroll_right)
        )
    }

    /**
     * Provide the menu item definitions for media control actions.
     *
     * @return A list containing menu item definitions for play/pause, previous/next track, and volume control.
     */
    fun getMediaControlMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Media.PLAY_PAUSE, labelResource = R.string.menu_item_play_pause, descriptionResource = R.string.menu_item_play_pause_description, drawableId = R.drawable.ic_play_pause),
            MenuItemDefinition(MenuConstants.ItemIds.Media.PREVIOUS_TRACK, labelResource = R.string.menu_item_previous_track, descriptionResource = R.string.menu_item_previous_track_description, drawableId = R.drawable.ic_previous_track),
            MenuItemDefinition(MenuConstants.ItemIds.Media.NEXT_TRACK, labelResource = R.string.menu_item_next_track, descriptionResource = R.string.menu_item_next_track_description, drawableId = R.drawable.ic_next_track),
            MenuItemDefinition(MenuConstants.ItemIds.Media.VOLUME_CONTROL, labelResource = R.string.action_volume_control, descriptionResource = R.string.menu_item_volume_control_description, drawableId = R.drawable.ic_volume_control)
        )
    }

    /**
     * Provide menu item definitions for editing actions.
     *
     * @return A list of MenuItemDefinition for the "cut", "copy", and "paste" menu items.
     */
    fun getEditMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Edit.CUT, labelResource = R.string.menu_item_cut, descriptionResource = R.string.menu_item_cut_description, drawableId = R.drawable.ic_cut),
            MenuItemDefinition(MenuConstants.ItemIds.Edit.COPY, labelResource = R.string.menu_item_copy, descriptionResource = R.string.menu_item_copy_description, drawableId = R.drawable.ic_copy),
            MenuItemDefinition(MenuConstants.ItemIds.Edit.PASTE, labelResource = R.string.menu_item_paste, descriptionResource = R.string.menu_item_paste_description, drawableId = R.drawable.ic_paste)
        )
    }

    /**
     * Provides menu item definitions for the AI submenu.
     *
     * @return A list of MenuItemDefinition for Reply Drafter and Screen Highlights.
     */
    fun getAiMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition(MenuConstants.ItemIds.Ai.REPLY_DRAFTER, labelResource = R.string.menu_item_reply_drafter, descriptionResource = R.string.menu_item_reply_drafter_description, drawableId = R.drawable.ic_reply_drafter),
            MenuItemDefinition(MenuConstants.ItemIds.Ai.SCREEN_HIGHLIGHTS, labelResource = R.string.menu_item_screen_highlights, descriptionResource = R.string.menu_item_screen_highlights_description, drawableId = R.drawable.ic_screen_highlights)
        )
    }

    /**
     * Retrieve the menu item definitions for a given menu ID.
     *
     * @param menuId Identifier of the menu. Recognized values: "main_menu", "ai_menu", "device_menu", "volume_control_menu",
     * "gestures_menu", "tap_gestures_menu", "tap_and_hold_menu", "swipe_gestures_menu", "pinch_gestures_menu",
     * "scroll_menu", "media_control_menu", "edit_menu", "settings_menu".
     * @return A list of MenuItemDefinition for the specified menu, or an empty list if the menuId is not recognized.
     */
    fun getDefinitionsForMenu(menuId: String): List<MenuItemDefinition> {
        return when (menuId) {
            MenuConstants.MenuIds.MAIN_MENU -> getMainMenuDefinitions()
            MenuConstants.MenuIds.AI_MENU -> getAiMenuDefinitions()
            MenuConstants.MenuIds.DEVICE_MENU -> getDeviceMenuDefinitions()
            MenuConstants.MenuIds.VOLUME_CONTROL_MENU -> getVolumeControlMenuDefinitions()
            MenuConstants.MenuIds.GESTURES_MENU -> getGesturesMenuDefinitions()
            MenuConstants.MenuIds.TAP_GESTURES_MENU -> getTapGesturesMenuDefinitions()
            MenuConstants.MenuIds.TAP_AND_HOLD_MENU -> getTapAndHoldGesturesMenuDefinitions()
            MenuConstants.MenuIds.SWIPE_GESTURES_MENU -> getSwipeGesturesMenuDefinitions()
            MenuConstants.MenuIds.PINCH_GESTURES_MENU -> getPinchGesturesMenuDefinitions()
            MenuConstants.MenuIds.SCROLL_MENU -> getScrollMenuDefinitions()
            MenuConstants.MenuIds.MEDIA_CONTROL_MENU -> getMediaControlMenuDefinitions()
            MenuConstants.MenuIds.EDIT_MENU -> getEditMenuDefinitions()
            MenuConstants.MenuIds.SETTINGS_MENU -> getSettingsMenuDefinitions()
            else -> emptyList()
        }
    }

    /**
     * Retrieves the definition for a main-menu item by its identifier.
     *
     * @param id The menu item identifier to look up.
     * @return The matching MenuItemDefinition if found, `null` otherwise.
     */
    fun getMainMenuDefinition(id: String): MenuItemDefinition? {
        return getMainMenuDefinitions().find { it.id == id }
    }

    /**
     * Retrieve a menu item definition by menu and item identifiers.
     *
     * @param menuId Identifier of the menu to search.
     * @param itemId Identifier of the menu item to find within the specified menu.
     * @return The matching MenuItemDefinition, or `null` if no matching item exists.
     */
    fun getDefinition(menuId: String, itemId: String): MenuItemDefinition? {
        return getDefinitionsForMenu(menuId).find { it.id == itemId }
    }
}
