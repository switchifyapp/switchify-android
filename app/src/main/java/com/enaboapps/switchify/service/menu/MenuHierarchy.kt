package com.enaboapps.switchify.service.menu

import android.os.Handler
import android.os.Looper
import androidx.core.os.postDelayed
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.stats.StatsCollector
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger

class MenuHierarchy(
    private val scanningManager: ScanningManager
) : MenuViewListener {
    private val TAG = "SwitchifyMenuHierarchy"

    private var tree: List<MenuView> = mutableListOf()

    private fun addMenu(menu: MenuView) {
        tree += menu
    }

    private fun logStackChange(action: String, depthBefore: Int, depthAfter: Int, menuId: String? = null) {
        Logger.log(
            LogEvent.MenuStackChanged,
            data = mapOf(
                "result" to "success",
                "action" to action,
                "depth_before" to depthBefore,
                "depth_after" to depthAfter,
                "menu_id" to menuId
            )
        )
    }

    private fun canPopMenu(): Boolean {
        return tree.size > 1
    }

    fun popMenu() {
        if (canPopMenu()) {
            val depthBefore = tree.size
            val closedMenu = tree.lastOrNull()
            closedMenu?.close()
            tree = tree.dropLast(1)
            logStackChange("pop", depthBefore, tree.size, closedMenu?.menuId)

            // Notify observers of menu closure
            closedMenu?.let { MenuManager.getInstance().notifyMenuClosed(it) }

            Handler(Looper.getMainLooper()).postDelayed(100) {
                tree.lastOrNull()?.let {
                    it.menuViewListener = this
                    it.open(scanningManager)
                    // MenuView will handle nodes change notification after inflating
                }
            }
        }
    }

    fun openMenu(menu: MenuView) {
        val depthBefore = tree.size
        getTopMenu()?.close()

        addMenu(menu)
        logStackChange("open", depthBefore, tree.size, menu.menuId)

        // Record stats for menu open
        menu.menuId?.let { menuId ->
            StatsCollector.getInstance().recordMenuOpen(menuId)
        }

        menu.menuViewListener = this
        Handler(Looper.getMainLooper()).postDelayed(100) {
            menu.open(scanningManager)
            // Notify observers that menu was opened
            MenuManager.getInstance().notifyMenuOpened(menu)
            // MenuView will handle nodes change notification after inflating
        }
    }

    fun removeAllMenus() {
        val depthBefore = tree.size
        // close the top menu
        getTopMenu()?.close()
        tree = mutableListOf()
        logStackChange("clear", depthBefore, tree.size)

        // remove the menu view
        MenuViewHandler.instance.kill()

        // Notify observers that all menus were closed
        MenuManager.getInstance().notifyAllMenusClosed()

        AccessTechnique.loadCurrentTechnique() // reload the current technique
    }

    fun getTopMenu(): MenuView? {
        return tree.lastOrNull()
    }

    fun isAtFirstMenu(): Boolean {
        return tree.size == 1
    }

    override fun onMenuViewClosed() {}
}
