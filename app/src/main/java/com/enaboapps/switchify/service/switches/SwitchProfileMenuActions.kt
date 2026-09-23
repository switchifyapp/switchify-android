package com.enaboapps.switchify.service.switches

import com.enaboapps.switchify.service.menu.MenuManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class SwitchProfileMenuActions(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val openConfirmationMenu: (String) -> Unit = {
        MenuManager.getInstance().openSwitchProfileConfirmationMenu(it)
    },
    private val dismissConfirmationMenu: () -> Unit = {
        MenuManager.getInstance().dismissSwitchProfileConfirmationMenu()
    },
    private val closeMenuHierarchy: () -> Unit = {
        MenuManager.getInstance().closeMenuHierarchy()
    }
) {
    suspend fun open(profileName: String, shouldOpen: () -> Boolean): Boolean =
        withContext(dispatcher) {
            if (!shouldOpen()) {
                false
            } else {
                openConfirmationMenu(profileName)
                true
            }
        }

    suspend fun dismiss() = withContext(dispatcher) {
        dismissConfirmationMenu()
    }

    suspend fun closeAll() = withContext(dispatcher) {
        closeMenuHierarchy()
    }
}
