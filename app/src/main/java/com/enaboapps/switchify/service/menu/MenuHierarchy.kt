package com.enaboapps.switchify.service.menu

import android.os.Handler
import android.os.Looper
import androidx.core.os.postDelayed
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.techniques.AccessTechnique

class MenuHierarchy(
    private val scanningManager: ScanningManager
) : MenuViewListener {
    private val TAG = "SwitchifyMenuHierarchy"

    private var tree: List<MenuView> = mutableListOf()

    private fun addMenu(menu: MenuView) {
        tree += menu
    }

    private fun canPopMenu(): Boolean {
        return tree.size > 1
    }

    fun popMenu() {
        if (canPopMenu()) {
            tree.lastOrNull()?.close()
            tree = tree.dropLast(1)
            Handler(Looper.getMainLooper()).postDelayed(100) {
                tree.lastOrNull()?.let {
                    it.menuViewListener = this
                    it.open(scanningManager)
                }
            }
        }
    }

    fun openMenu(menu: MenuView) {
        getTopMenu()?.close()

        addMenu(menu)
        menu.menuViewListener = this
        Handler(Looper.getMainLooper()).postDelayed(100) {
            menu.open(scanningManager)
        }
    }

    fun removeAllMenus() {
        // close the top menu
        getTopMenu()?.close()
        tree = mutableListOf()

        // remove the menu view
        MenuViewHandler.instance.kill()

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