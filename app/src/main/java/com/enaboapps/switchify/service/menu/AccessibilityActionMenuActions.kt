package com.enaboapps.switchify.service.menu

import com.enaboapps.switchify.service.techniques.nodes.AccessibilityActionMenuActions
import com.enaboapps.switchify.service.techniques.nodes.NodeActionTarget
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AndroidAccessibilityActionMenuActions(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val openMenu: (NodeActionTarget) -> Unit,
    private val replaceMenu: (NodeActionTarget) -> Unit,
    private val showMain: () -> Unit,
    private val closeMenus: () -> Unit
) : AccessibilityActionMenuActions {
    override suspend fun open(target: NodeActionTarget, isCurrent: () -> Boolean) {
        withContext(dispatcher) {
            if (isCurrent()) openMenu(target)
        }
    }

    override suspend fun replace(target: NodeActionTarget, isCurrent: () -> Boolean) {
        withContext(dispatcher) {
            if (isCurrent()) replaceMenu(target)
        }
    }

    override suspend fun showMainWithoutActions(isCurrent: () -> Boolean) {
        withContext(dispatcher) {
            if (isCurrent()) showMain()
        }
    }

    override suspend fun closeAll(isCurrent: () -> Boolean) {
        withContext(dispatcher) {
            if (isCurrent()) closeMenus()
        }
    }
}
