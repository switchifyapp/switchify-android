package com.enaboapps.switchify.service.menu.structure

import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.menus.edit.EditMenuStructure
import com.enaboapps.switchify.service.menu.menus.gestures.GestureMenuStructure
import com.enaboapps.switchify.service.menu.menus.media.MediaMenuStructure
import com.enaboapps.switchify.service.menu.menus.scroll.ScrollMenuStructure
import com.enaboapps.switchify.service.menu.menus.settings.SettingsMenuStructure
import com.enaboapps.switchify.service.menu.menus.system.SystemMenuStructure
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope

/**
 * Resolves and binds actions for menu items that are moved from other menus.
 * This service temporarily builds menu structures to extract action lambdas for specific items.
 */
object MenuActionResolver {

    /**
     * Resolves the action for a menu item based on its source menu and item ID.
     *
     * @param sourceMenuId The ID of the source menu where the item originates
     * @param itemId The ID of the menu item
     * @param accessibilityService The accessibility service instance
     * @param coroutineScope The coroutine scope for async operations
     * @return The action lambda for the item, or a no-op lambda if not found
     */
    fun resolveAction(
        sourceMenuId: String,
        itemId: String,
        accessibilityService: SwitchifyAccessibilityService?,
        coroutineScope: CoroutineScope
    ): () -> Unit {
        try {
            return when (sourceMenuId) {
                MenuConstants.MenuIds.DEVICE_MENU -> {
                    if (accessibilityService == null) return {}
                    val menu = SystemMenuStructure(accessibilityService, coroutineScope)
                        .buildDeviceMenuObject()
                    val item = menu.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.VOLUME_CONTROL_MENU -> {
                    if (accessibilityService == null) return {}
                    val menu = SystemMenuStructure(accessibilityService, coroutineScope)
                        .buildVolumeControlMenuObject()
                    val item = menu.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.GESTURES_MENU -> {
                    if (accessibilityService == null) return {}
                    val gestureStructure = GestureMenuStructure(accessibilityService, coroutineScope)
                    val item = gestureStructure.gesturesMenuObject.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.TAP_GESTURES_MENU -> {
                    if (accessibilityService == null) return {}
                    val gestureStructure = GestureMenuStructure(accessibilityService, coroutineScope)
                    val item = gestureStructure.tapGesturesMenuObject.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.SWIPE_GESTURES_MENU -> {
                    if (accessibilityService == null) return {}
                    val gestureStructure = GestureMenuStructure(accessibilityService, coroutineScope)
                    val item = gestureStructure.swipeGesturesMenuObject.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.PINCH_GESTURES_MENU -> {
                    if (accessibilityService == null) return {}
                    val gestureStructure = GestureMenuStructure(accessibilityService, coroutineScope)
                    val item = gestureStructure.pinchGesturesMenuObject.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.SCROLL_MENU -> {
                    if (accessibilityService == null) return {}
                    val menu = ScrollMenuStructure(accessibilityService, coroutineScope)
                        .scrollMenuObject
                    val item = menu.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.MEDIA_CONTROL_MENU -> {
                    if (accessibilityService == null) return {}
                    val menu = MediaMenuStructure(accessibilityService, coroutineScope)
                        .mediaControlMenuObject
                    val item = menu.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.EDIT_MENU -> {
                    if (accessibilityService == null) return {}
                    val menu = EditMenuStructure(accessibilityService, coroutineScope)
                        .buildEditMenuObject()
                    val item = menu.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                MenuConstants.MenuIds.SETTINGS_MENU -> {
                    if (accessibilityService == null) return {}
                    val menu = SettingsMenuStructure(accessibilityService, coroutineScope)
                        .buildSettingsMenuObject()
                    val item = menu.getMenuItems().find { it.id == itemId }
                    item?.let { { it.select() } } ?: {}
                }

                else -> {
                    // Unknown source menu
                    {}
                }
            }
        } catch (e: Exception) {
            // If action resolution fails, return no-op
            android.util.Log.e("MenuActionResolver", "Failed to resolve action for $itemId from $sourceMenuId: ${e.message}")
            Logger.log(
                LogEvent.MenuActionResolveFailed,
                data = mapOf(
                    "result" to "failure",
                    "reason" to "exception",
                    "item_id" to itemId,
                    "source_menu_id" to sourceMenuId
                ),
                throwable = e
            )
            return {}
        }
    }
}
