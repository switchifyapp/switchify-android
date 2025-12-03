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
            MenuItemDefinition("sys_back", labelResource = R.string.system_back, drawableId = R.drawable.ic_sys_back),
            MenuItemDefinition("sys_home", labelResource = R.string.system_home, drawableId = R.drawable.ic_sys_home),
            MenuItemDefinition("scan_keyboard", labelResource = R.string.menu_item_scan_keyboard, drawableId = R.drawable.ic_scan_keyboard),
            MenuItemDefinition("tap", labelResource = R.string.menu_item_tap, drawableId = R.drawable.ic_gesture_tap),
            MenuItemDefinition("gestures", labelResource = R.string.menu_title_gestures, drawableId = R.drawable.ic_gestures),
            MenuItemDefinition("scroll", labelResource = R.string.menu_title_scroll, drawableId = R.drawable.ic_scroll),
            MenuItemDefinition("quick_apps", labelResource = R.string.menu_title_quick_apps, drawableId = R.drawable.ic_quick_apps),
            MenuItemDefinition("gesture_patterns", labelResource = R.string.gesture_patterns_title, drawableId = R.drawable.ic_gesture_patterns),
            MenuItemDefinition("device", labelResource = R.string.menu_title_device, drawableId = R.drawable.ic_device),
            MenuItemDefinition("media_control", labelResource = R.string.menu_title_media_control, drawableId = R.drawable.ic_media_control),
            MenuItemDefinition("edit", labelResource = R.string.menu_title_edit, drawableId = R.drawable.ic_edit),
            MenuItemDefinition("switch_to_item_scan", labelResource = R.string.access_technique_item_scan, drawableId = R.drawable.ic_item_scan),
            MenuItemDefinition("switch_to_radar", labelResource = R.string.access_technique_radar, drawableId = R.drawable.ic_radar),
            MenuItemDefinition("switch_to_point_scan", labelResource = R.string.access_technique_point_scan, drawableId = R.drawable.ic_point_scan),
            MenuItemDefinition("toggle_head_control", labelResource = R.string.menu_item_enable_head_control, drawableId = R.drawable.ic_head_control_pointer),
            MenuItemDefinition("pause", labelResource = R.string.menu_item_pause, drawableId = R.drawable.ic_pause)
        )
    }

    /**
     * Provides menu item definitions for device-related actions.
     *
     * @return A list of MenuItemDefinition representing device menu entries: recent apps, notifications, assistant, quick settings, lock screen, power dialog, screenshot, and volume control.
     */
    fun getDeviceMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("recent_apps", labelResource = R.string.system_recents, drawableId = R.drawable.ic_recent_apps),
            MenuItemDefinition("notifications", labelResource = R.string.system_notifications, drawableId = R.drawable.ic_notifications),
            MenuItemDefinition("open_assistant", labelResource = R.string.system_assistant, drawableId = R.drawable.ic_assistant),
            MenuItemDefinition("quick_settings", labelResource = R.string.system_quick_settings, drawableId = R.drawable.ic_quick_settings),
            MenuItemDefinition("lock_screen", labelResource = R.string.system_lock_screen, drawableId = R.drawable.ic_lock_screen),
            MenuItemDefinition("power_dialog", labelResource = R.string.system_power_dialog, drawableId = R.drawable.ic_power_dialog),
            MenuItemDefinition("take_screenshot", labelResource = R.string.system_screenshot, drawableId = R.drawable.ic_screenshot),
            MenuItemDefinition("volume_control", labelResource = R.string.action_volume_control, drawableId = R.drawable.ic_volume_control)
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
            MenuItemDefinition("volume_up", labelResource = R.string.menu_item_volume_up, drawableId = R.drawable.ic_volume_up),
            MenuItemDefinition("volume_down", labelResource = R.string.menu_item_volume_down, drawableId = R.drawable.ic_volume_down),
            MenuItemDefinition("full_volume", labelResource = R.string.menu_item_full_volume, drawableId = R.drawable.ic_full_volume),
            MenuItemDefinition("mute", labelResource = R.string.menu_item_mute, drawableId = R.drawable.ic_mute),
            MenuItemDefinition("half_volume", labelResource = R.string.menu_item_half_volume, drawableId = R.drawable.ic_half_volume)
        )
    }

    /**
     * Provides menu item definitions for gesture-related actions.
     *
     * @return A list of MenuItemDefinition objects for gesture menus including tap gestures, swipe gestures, drag, pinch gestures, finger mode, and a gesture-lock toggle.
     */
    fun getGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("tap_gestures", labelResource = R.string.menu_item_tap_gestures, drawableId = R.drawable.ic_tap_gestures),
            MenuItemDefinition("swipe_gestures", labelResource = R.string.menu_item_swipe_gestures, drawableId = R.drawable.ic_swipe_gestures),
            MenuItemDefinition("drag", labelResource = R.string.menu_item_drag, drawableId = R.drawable.ic_gesture_drag),
            MenuItemDefinition("pinch_gestures", labelResource = R.string.menu_item_pinch_gestures, drawableId = R.drawable.ic_pinch_gestures),
            MenuItemDefinition("finger_mode", labelResource = R.string.menu_item_finger_mode, drawableId = R.drawable.ic_finger_mode),
            MenuItemDefinition("toggle_gesture_lock", labelResource = R.string.system_gesture_lock, drawableId = R.drawable.ic_toggle_gesture_lock)
        )
    }

    /**
     * Tap-related gesture menu item definitions for the gestures submenu.
     *
     * @return A list of MenuItemDefinition for tap gestures, including tap, double tap, several tap-and-hold durations, and a gesture lock toggle.
     */
    fun getTapGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("tap", labelResource = R.string.menu_item_tap, drawableId = R.drawable.ic_gesture_tap),
            MenuItemDefinition("double_tap", labelResource = R.string.menu_item_double_tap, drawableId = R.drawable.ic_gesture_double_tap),
            MenuItemDefinition("tap_and_hold_0_5s", labelResource = R.string.menu_item_tap_and_hold_0_5s, drawableId = R.drawable.ic_gesture_tap_hold_0_5s),
            MenuItemDefinition("tap_and_hold_1s", labelResource = R.string.menu_item_tap_and_hold_1s, drawableId = R.drawable.ic_gesture_tap_hold_1s),
            MenuItemDefinition("tap_and_hold_2s", labelResource = R.string.menu_item_tap_and_hold_2s, drawableId = R.drawable.ic_gesture_tap_hold_2s),
            MenuItemDefinition("tap_and_hold_3s", labelResource = R.string.menu_item_tap_and_hold_3s, drawableId = R.drawable.ic_gesture_tap_hold_3s),
            MenuItemDefinition("tap_and_hold_5s", labelResource = R.string.menu_item_tap_and_hold_5s, drawableId = R.drawable.ic_gesture_tap_hold_5s),
            MenuItemDefinition("tap_and_hold_10s", labelResource = R.string.menu_item_tap_and_hold_10s, drawableId = R.drawable.ic_gesture_tap_hold_10s),
            MenuItemDefinition("toggle_gesture_lock", labelResource = R.string.system_gesture_lock, drawableId = R.drawable.ic_toggle_gesture_lock)
        )
    }

    /**
     * Provide definitions for the swipe gestures submenu.
     *
     * @return A list of MenuItemDefinition entries for swipe gestures (up, down, left, right), a custom swipe action, and a gesture lock toggle; each entry contains an id, a label resource, and a drawable id.
     */
    fun getSwipeGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("swipe_up", labelResource = R.string.menu_item_swipe_up, drawableId = R.drawable.ic_gesture_swipe_up),
            MenuItemDefinition("swipe_down", labelResource = R.string.menu_item_swipe_down, drawableId = R.drawable.ic_gesture_swipe_down),
            MenuItemDefinition("swipe_left", labelResource = R.string.menu_item_swipe_left, drawableId = R.drawable.ic_gesture_swipe_left),
            MenuItemDefinition("swipe_right", labelResource = R.string.menu_item_swipe_right, drawableId = R.drawable.ic_gesture_swipe_right),
            MenuItemDefinition("custom_swipe", labelResource = R.string.menu_item_custom_swipe, drawableId = R.drawable.ic_gesture_custom_swipe),
            MenuItemDefinition("toggle_gesture_lock", labelResource = R.string.system_gesture_lock, drawableId = R.drawable.ic_toggle_gesture_lock)
        )
    }

    /**
     * Provide menu item definitions for pinch gesture actions.
     *
     * @return A list of MenuItemDefinition for pinch-in, pinch-out, and the gesture lock toggle.
     */
    fun getPinchGesturesMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("pinch_in", labelResource = R.string.menu_item_pinch_in, drawableId = R.drawable.ic_gesture_pinch_in),
            MenuItemDefinition("pinch_out", labelResource = R.string.menu_item_pinch_out, drawableId = R.drawable.ic_gesture_pinch_out),
            MenuItemDefinition("toggle_gesture_lock", labelResource = R.string.system_gesture_lock, drawableId = R.drawable.ic_toggle_gesture_lock)
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
            MenuItemDefinition("scroll_up", labelResource = R.string.menu_item_scroll_up, drawableId = R.drawable.ic_scroll_up),
            MenuItemDefinition("scroll_down", labelResource = R.string.menu_item_scroll_down, drawableId = R.drawable.ic_scroll_down),
            MenuItemDefinition("scroll_left", labelResource = R.string.menu_item_scroll_left, drawableId = R.drawable.ic_scroll_left),
            MenuItemDefinition("scroll_right", labelResource = R.string.menu_item_scroll_right, drawableId = R.drawable.ic_scroll_right)
        )
    }

    /**
     * Provide the menu item definitions for media control actions.
     *
     * @return A list containing menu item definitions for "play_pause" (play/pause) and "volume_control" (volume control).
     */
    fun getMediaControlMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("play_pause", labelResource = R.string.menu_item_play_pause, drawableId = R.drawable.ic_play_pause),
            MenuItemDefinition("volume_control", labelResource = R.string.action_volume_control, drawableId = R.drawable.ic_volume_control)
        )
    }

    /**
     * Provide menu item definitions for editing actions.
     *
     * @return A list of MenuItemDefinition for the "cut", "copy", and "paste" menu items.
     */
    fun getEditMenuDefinitions(): List<MenuItemDefinition> {
        return listOf(
            MenuItemDefinition("cut", labelResource = R.string.menu_item_cut, drawableId = R.drawable.ic_cut),
            MenuItemDefinition("copy", labelResource = R.string.menu_item_copy, drawableId = R.drawable.ic_copy),
            MenuItemDefinition("paste", labelResource = R.string.menu_item_paste, drawableId = R.drawable.ic_paste)
        )
    }

    /**
     * Retrieve the menu item definitions for a given menu ID.
     *
     * @param menuId Identifier of the menu. Recognized values: "main_menu", "device_menu", "volume_control_menu",
     * "gestures_menu", "tap_gestures_menu", "swipe_gestures_menu", "pinch_gestures_menu", "scroll_menu",
     * "media_control_menu", "edit_menu".
     * @return A list of MenuItemDefinition for the specified menu, or an empty list if the menuId is not recognized.
     */
    fun getDefinitionsForMenu(menuId: String): List<MenuItemDefinition> {
        return when (menuId) {
            "main_menu" -> getMainMenuDefinitions()
            "device_menu" -> getDeviceMenuDefinitions()
            "volume_control_menu" -> getVolumeControlMenuDefinitions()
            "gestures_menu" -> getGesturesMenuDefinitions()
            "tap_gestures_menu" -> getTapGesturesMenuDefinitions()
            "swipe_gestures_menu" -> getSwipeGesturesMenuDefinitions()
            "pinch_gestures_menu" -> getPinchGesturesMenuDefinitions()
            "scroll_menu" -> getScrollMenuDefinitions()
            "media_control_menu" -> getMediaControlMenuDefinitions()
            "edit_menu" -> getEditMenuDefinitions()
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