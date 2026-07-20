package com.enaboapps.switchify.service.menu

import com.enaboapps.switchify.service.menu.structure.MenuConstants
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuItemVisualRoleTest {
    @Test
    fun closeIdAlwaysUsesCloseRole() {
        assertEquals(
            MenuItemVisualRole.CLOSE,
            MenuItemVisualRole.resolve(
                id = MenuConstants.ItemIds.Navigation.CLOSE_MENU,
                isBackButton = false,
                isMenuHierarchyManipulator = true
            )
        )
    }

    @Test
    fun backFlagUsesBackRole() {
        assertEquals(
            MenuItemVisualRole.BACK,
            MenuItemVisualRole.resolve(
                id = "back",
                isBackButton = true,
                isMenuHierarchyManipulator = false
            )
        )
    }

    @Test
    fun otherManipulatorsUseNavigationRole() {
        assertEquals(
            MenuItemVisualRole.NAVIGATION,
            MenuItemVisualRole.resolve(
                id = MenuConstants.ItemIds.Navigation.NEXT_PAGE,
                isBackButton = false,
                isMenuHierarchyManipulator = true
            )
        )
    }

    @Test
    fun contentItemsUseRegularRole() {
        assertEquals(
            MenuItemVisualRole.REGULAR,
            MenuItemVisualRole.resolve(
                id = MenuConstants.ItemIds.Main.GESTURES,
                isBackButton = false,
                isMenuHierarchyManipulator = false
            )
        )
    }
}
