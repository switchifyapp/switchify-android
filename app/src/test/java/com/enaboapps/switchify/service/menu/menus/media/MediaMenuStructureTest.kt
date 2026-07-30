package com.enaboapps.switchify.service.menu.menus.media

import com.enaboapps.switchify.service.menu.structure.MenuConstants
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.coroutines.EmptyCoroutineContext

class MediaMenuStructureTest {
    private val menuItems = MediaMenuStructure(
        accessibilityService = null,
        coroutineScope = CoroutineScope(EmptyCoroutineContext)
    ).mediaControlMenuObject.getDefaultMenuItems().associateBy { it.id }

    @Test
    fun trackNavigationItemsKeepMenuOpen() {
        assertFalse(menuItems.getValue(MenuConstants.ItemIds.Media.PREVIOUS_TRACK).closeOnSelect)
        assertFalse(menuItems.getValue(MenuConstants.ItemIds.Media.NEXT_TRACK).closeOnSelect)
    }

    @Test
    fun otherMediaItemsRetainExistingSelectionBehavior() {
        assertTrue(menuItems.getValue(MenuConstants.ItemIds.Media.PLAY_PAUSE).closeOnSelect)
        assertTrue(menuItems.getValue(MenuConstants.ItemIds.Media.VOLUME_CONTROL).isLinkToMenu)
    }
}
