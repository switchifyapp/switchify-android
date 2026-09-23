package com.enaboapps.switchify.service.menu.structure

import org.junit.Assert.assertTrue
import org.junit.Test

class MenuActionResolverCoverageTest {

    @Test
    fun everyCustomizableMenuIsAResolvableSource() {
        val unresolvable = MenuConstants.customizableMenus
            .map { it.first }
            .filterNot { it in RESOLVED_SOURCE_MENU_IDS }

        assertTrue(
            "Customizable menus with no MenuActionResolver branch resolve to a no-op: $unresolvable",
            unresolvable.isEmpty()
        )
    }

    companion object {
        /**
         * Mirrors the source menus handled by MenuActionResolver.resolveAction. Any menu
         * offered in MenuConstants.customizableMenus but missing here renders its items in
         * other menus and silently does nothing when selected.
         */
        private val RESOLVED_SOURCE_MENU_IDS = setOf(
            MenuConstants.MenuIds.MAIN_MENU,
            MenuConstants.MenuIds.DEVICE_MENU,
            MenuConstants.MenuIds.VOLUME_CONTROL_MENU,
            MenuConstants.MenuIds.GESTURES_MENU,
            MenuConstants.MenuIds.TAP_GESTURES_MENU,
            MenuConstants.MenuIds.TAP_AND_HOLD_MENU,
            MenuConstants.MenuIds.SWIPE_GESTURES_MENU,
            MenuConstants.MenuIds.PINCH_GESTURES_MENU,
            MenuConstants.MenuIds.SCROLL_MENU,
            MenuConstants.MenuIds.MEDIA_CONTROL_MENU,
            MenuConstants.MenuIds.EDIT_MENU,
            MenuConstants.MenuIds.SETTINGS_MENU
        )
    }
}
