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
    private var openGeneration = 0L

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

            tree.lastOrNull()?.let { openReplacement(it, notifyOpened = false) }
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

        openReplacement(menu)
    }

    fun replaceTopMenu(menu: MenuView) {
        val depthBefore = tree.size
        val closedMenu = tree.lastOrNull()
        closedMenu?.close()
        if (tree.isNotEmpty()) tree = tree.dropLast(1)
        addMenu(menu)
        logStackChange("replace_top", depthBefore, tree.size, menu.menuId)
        closedMenu?.let { MenuManager.getInstance().notifyMenuClosed(it) }
        openReplacement(menu)
    }

    fun replaceAllMenus(menu: MenuView) {
        val depthBefore = tree.size
        val closedMenu = tree.lastOrNull()
        closedMenu?.close()
        tree = listOf(menu)
        logStackChange("replace_all", depthBefore, tree.size, menu.menuId)
        closedMenu?.let { MenuManager.getInstance().notifyMenuClosed(it) }
        openReplacement(menu)
    }

    private fun openReplacement(menu: MenuView, notifyOpened: Boolean = true) {
        val generation = ++openGeneration
        menu.menuViewListener = this
        Handler(Looper.getMainLooper()).postDelayed(100) {
            if (generation != openGeneration || getTopMenu() !== menu) return@postDelayed
            menu.open(scanningManager)
            if (notifyOpened) MenuManager.getInstance().notifyMenuOpened(menu)
        }
    }

    fun removeAllMenus() {
        val depthBefore = tree.size
        dispose()
        logStackChange("clear", depthBefore, tree.size)

        // Notify observers that all menus were closed
        MenuManager.getInstance().notifyAllMenusClosed()

        AccessTechnique.loadCurrentTechnique() // reload the current technique
        scanningManager.refreshAppScanTechniqueOverride()
    }

    fun getTopMenu(): MenuView? {
        return tree.lastOrNull()
    }

    /**
     * Invalidates pending opens, closes the top menu, clears the stack, and
     * releases the menu container. Does not notify observers or reload the
     * access technique; [removeAllMenus] layers those on top for normal closes.
     */
    fun dispose() {
        openGeneration++
        getTopMenu()?.close()
        tree = emptyList()
        MenuViewHandler.instance.kill()
    }

    fun isAtFirstMenu(): Boolean {
        return tree.size == 1
    }

    override fun onMenuViewClosed() {}
}
