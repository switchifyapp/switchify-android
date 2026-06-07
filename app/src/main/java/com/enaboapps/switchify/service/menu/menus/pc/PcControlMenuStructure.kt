package com.enaboapps.switchify.service.menu.menus.pc

import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcMouseCommand
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.SwitchifyAccessibilityService
import com.enaboapps.switchify.service.menu.MenuItem
import com.enaboapps.switchify.service.menu.MenuManager
import com.enaboapps.switchify.service.menu.structure.MenuConstants
import com.enaboapps.switchify.service.menu.structure.MenuItemRegistry
import com.enaboapps.switchify.service.menu.structure.MenuStructure
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PcControlMenuStructure(
    private val accessibilityService: SwitchifyAccessibilityService,
    private val coroutineScope: CoroutineScope
) {
    val pcControlMenuObject = MenuStructure(
        id = MenuConstants.MenuIds.PC_CONTROL_MENU,
        items = buildItems(),
        context = accessibilityService,
        coroutineScope = coroutineScope
    )

    private fun buildItems(): List<MenuItem> {
        val step = 80
        val scrollStep = 5
        return listOfNotNull(
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_UP_LEFT, PcMouseCommand.Move(-step, -step)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_UP, PcMouseCommand.Move(0, -step)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_UP_RIGHT, PcMouseCommand.Move(step, -step)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_LEFT, PcMouseCommand.Move(-step, 0)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_CLICK, PcMouseCommand.LeftClick),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_RIGHT, PcMouseCommand.Move(step, 0)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_DOWN_LEFT, PcMouseCommand.Move(-step, step)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_DOWN, PcMouseCommand.Move(0, step)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_DOWN_RIGHT, PcMouseCommand.Move(step, step)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_RIGHT_CLICK, PcMouseCommand.RightClick),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_DOUBLE_CLICK, PcMouseCommand.DoubleClick),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_SCROLL_UP, PcMouseCommand.Scroll(0, scrollStep)),
            commandItem(MenuConstants.ItemIds.Pc.MOUSE_SCROLL_DOWN, PcMouseCommand.Scroll(0, -scrollStep))
        )
    }

    private fun commandItem(id: String, command: PcMouseCommand): MenuItem? {
        return MenuItemRegistry.getDefinition(MenuConstants.MenuIds.PC_CONTROL_MENU, id)?.let { definition ->
            MenuItem(
                definition = definition,
                closeOnSelect = false,
                action = {
                    coroutineScope.launch {
                        val controller = ServiceCore.getPcServiceConnectionController()
                        val result = controller?.sendMouseCommand(command) ?: PcCommandResult.AuthFailed()
                        when (result) {
                            PcCommandResult.Ack -> Unit
                            is PcCommandResult.AuthFailed -> {
                                withContext(Dispatchers.Main) {
                                    showMessage(R.string.pc_control_connect_first, MessageSeverity.Warning)
                                    MenuManager.getInstance().closeMenuHierarchy()
                                }
                            }
                            is PcCommandResult.Failed -> {
                                withContext(Dispatchers.Main) {
                                    showMessage(R.string.pc_control_command_failed, MessageSeverity.Error)
                                }
                            }
                        }
                    }
                }
            )
        }
    }

    private fun showMessage(messageResId: Int, severity: MessageSeverity) {
        ServiceMessageHUD.instance.showMessage(
            messageResId,
            ServiceMessageHUD.MessageType.DISAPPEARING,
            ServiceMessageHUD.Time.MEDIUM,
            severity
        )
    }
}
