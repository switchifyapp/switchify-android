package com.enaboapps.switchify.service.menu

/**
 * Interface for observing menu state changes
 * Allows components to listen to menu lifecycle events instead of managing their own state
 */
interface MenuStateObserver {

    /**
     * Called when a menu is opened
     * @param menuView The menu that was opened
     */
    fun onMenuOpened(menuView: MenuView)

    /**
     * Called when a menu is closed
     * @param menuView The menu that was closed
     */
    fun onMenuClosed(menuView: MenuView)

    /**
     * Called when menu nodes change (e.g., page navigation)
     * @param menuView The current menu view
     */
    fun onMenuNodesChanged(menuView: MenuView)

    /**
     * Called when all menus are removed/closed
     */
    fun onAllMenusClosed()
}