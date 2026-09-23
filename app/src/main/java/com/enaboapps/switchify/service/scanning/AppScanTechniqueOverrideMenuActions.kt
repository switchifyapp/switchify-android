package com.enaboapps.switchify.service.scanning

import com.enaboapps.switchify.service.menu.MenuManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AppScanTechniqueOverrideMenuActions(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val isMenuOpen: () -> Boolean = {
        MenuManager.getInstance().getCurrentMenuView() != null
    },
    private val closeMenuHierarchy: () -> Unit = {
        MenuManager.getInstance().closeMenuHierarchy()
    }
) {
    suspend fun closeIfOpen(shouldClose: () -> Boolean): Boolean =
        withContext(dispatcher) {
            if (!shouldClose() || !isMenuOpen()) {
                false
            } else {
                closeMenuHierarchy()
                true
            }
        }
}
