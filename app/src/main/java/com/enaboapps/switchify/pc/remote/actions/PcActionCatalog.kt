package com.enaboapps.switchify.pc.remote.actions

import androidx.annotation.StringRes
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.protocol.PcDisplayDirection
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface

enum class PcClickButton {
    Left,
    Right,
    Double
}

enum class PcDraftOperation {
    Clear,
    Send
}

sealed class PcActionBehavior {
    data class Movement(val dx: Int, val dy: Int) : PcActionBehavior()
    data class Click(val button: PcClickButton) : PcActionBehavior()
    data object Drag : PcActionBehavior()
    data class Scroll(val dy: Int) : PcActionBehavior()
    data class Speed(val direction: Int) : PcActionBehavior()
    data class Monitor(val direction: PcDisplayDirection) : PcActionBehavior()
    data class Modifier(val key: String) : PcActionBehavior()
    data class Shortcut(val key: String) : PcActionBehavior()
    data class Window(val action: String) : PcActionBehavior()
    data class Key(val key: String) : PcActionBehavior()
    data class Draft(val operation: PcDraftOperation) : PcActionBehavior()
}

enum class PcActionCategory {
    PointerMovement,
    MouseButtons,
    Scrolling,
    PointerSpeed,
    Monitors,
    Modifiers,
    Windows,
    Shortcuts,
    PcKeys,
    DraftText
}

data class PcActionDefinition(
    val id: String,
    @StringRes val nameRes: Int,
    val category: PcActionCategory,
    val keywords: List<String>,
    val behavior: PcActionBehavior
)

object PcActionCatalog {
    private data class Direction(val dx: Int, val dy: Int, val words: String, @StringRes val nameRes: Int)

    private val directions = listOf(
        Direction(-1, -1, "up and left", R.string.pc_action_move_up_left),
        Direction(0, -1, "up", R.string.pc_action_move_up),
        Direction(1, -1, "up and right", R.string.pc_action_move_up_right),
        Direction(-1, 0, "left", R.string.pc_action_move_left),
        Direction(1, 0, "right", R.string.pc_action_move_right),
        Direction(-1, 1, "down and left", R.string.pc_action_move_down_left),
        Direction(0, 1, "down", R.string.pc_action_move_down),
        Direction(1, 1, "down and right", R.string.pc_action_move_down_right)
    )

    val windowActions = listOf(
        "switchNext" to R.string.pc_action_window_switch_next,
        "switchPrevious" to R.string.pc_action_window_switch_previous,
        "taskView" to R.string.pc_action_window_task_view,
        "showDesktop" to R.string.pc_action_window_show_desktop,
        "minimizeFocused" to R.string.pc_action_window_minimize,
        "maximizeFocused" to R.string.pc_action_window_maximize,
        "closeFocused" to R.string.pc_action_window_close
    )

    val modifierKeys = listOf("Ctrl", "Alt", "Shift", "Meta")
    val shortcutKeys = listOf("A", "C", "V", "X")
    val pcKeys = listOf("Backspace", "Enter", "Escape", "Tab", "ArrowLeft", "ArrowUp", "ArrowDown", "ArrowRight")
    val monitorDirections = listOf(
        PcDisplayDirection.Left,
        PcDisplayDirection.Up,
        PcDisplayDirection.Down,
        PcDisplayDirection.Right
    )

    val actions: List<PcActionDefinition> = buildList {
        directions.forEach { direction ->
            add(
                PcActionDefinition(
                    id = "move.${direction.dx + 1}.${direction.dy + 1}",
                    nameRes = direction.nameRes,
                    category = PcActionCategory.PointerMovement,
                    keywords = listOf("mouse", "direction", direction.words),
                    behavior = PcActionBehavior.Movement(direction.dx, direction.dy)
                )
            )
        }
        add(PcActionDefinition("move.1.1", R.string.pc_action_left_click, PcActionCategory.MouseButtons, listOf("mouse", "click"), PcActionBehavior.Click(PcClickButton.Left)))
        add(PcActionDefinition("click.double", R.string.pc_action_double_click, PcActionCategory.MouseButtons, listOf("mouse", "click"), PcActionBehavior.Click(PcClickButton.Double)))
        add(PcActionDefinition("click.right", R.string.pc_action_right_click, PcActionCategory.MouseButtons, listOf("mouse", "context menu"), PcActionBehavior.Click(PcClickButton.Right)))
        add(PcActionDefinition("drag.toggle", R.string.pc_action_drag_toggle, PcActionCategory.MouseButtons, listOf("mouse", "hold", "release"), PcActionBehavior.Drag))
        add(PcActionDefinition("scroll.up", R.string.pc_action_scroll_up, PcActionCategory.Scrolling, listOf("mouse", "wheel", "up"), PcActionBehavior.Scroll(5)))
        add(PcActionDefinition("scroll.down", R.string.pc_action_scroll_down, PcActionCategory.Scrolling, listOf("mouse", "wheel", "down"), PcActionBehavior.Scroll(-5)))
        add(PcActionDefinition("speed.slower", R.string.pc_action_speed_slower, PcActionCategory.PointerSpeed, listOf("mouse", "speed", "slower"), PcActionBehavior.Speed(-1)))
        add(PcActionDefinition("speed.faster", R.string.pc_action_speed_faster, PcActionCategory.PointerSpeed, listOf("mouse", "speed", "faster"), PcActionBehavior.Speed(1)))
        monitorDirections.forEach { direction ->
            add(
                PcActionDefinition(
                    id = "monitor.${direction.protocolValue}",
                    nameRes = monitorName(direction),
                    category = PcActionCategory.Monitors,
                    keywords = listOf("display", "screen", direction.protocolValue),
                    behavior = PcActionBehavior.Monitor(direction)
                )
            )
        }
        modifierKeys.forEach { key ->
            add(
                PcActionDefinition(
                    id = "modifier.$key",
                    nameRes = R.string.pc_action_hold_modifier,
                    category = PcActionCategory.Modifiers,
                    keywords = listOf("keyboard", key) + modifierAliases(key),
                    behavior = PcActionBehavior.Modifier(key)
                )
            )
        }
        windowActions.forEach { (action, nameRes) ->
            add(
                PcActionDefinition(
                    id = "window.$action",
                    nameRes = nameRes,
                    category = PcActionCategory.Windows,
                    keywords = listOf("window", "app", action),
                    behavior = PcActionBehavior.Window(action)
                )
            )
        }
        shortcutKeys.forEach { key ->
            add(
                PcActionDefinition(
                    id = "shortcut.$key",
                    nameRes = R.string.pc_action_shortcut,
                    category = PcActionCategory.Shortcuts,
                    keywords = listOf("keyboard", "shortcut", key),
                    behavior = PcActionBehavior.Shortcut(key)
                )
            )
        }
        pcKeys.forEach { key ->
            add(
                PcActionDefinition(
                    id = "key.$key",
                    nameRes = keyName(key),
                    category = PcActionCategory.PcKeys,
                    keywords = listOf("keyboard", key),
                    behavior = PcActionBehavior.Key(key)
                )
            )
        }
        add(PcActionDefinition("draft.clear", R.string.pc_action_draft_clear, PcActionCategory.DraftText, listOf("typing", "text", "clear"), PcActionBehavior.Draft(PcDraftOperation.Clear)))
        add(PcActionDefinition("draft.send", R.string.pc_action_draft_send, PcActionCategory.DraftText, listOf("typing", "text", "send"), PcActionBehavior.Draft(PcDraftOperation.Send)))
    }

    private val byId = actions.associateBy { it.id }

    fun get(id: String): PcActionDefinition? = byId[id]

    fun canPlace(id: String, surface: PcLayoutSurface): Boolean {
        val action = get(id) ?: return false
        return action.behavior !is PcActionBehavior.Draft || surface == PcLayoutSurface.Typing
    }

    private fun modifierAliases(key: String) = when (key) {
        "Meta" -> listOf("command", "start", "windows")
        "Alt" -> listOf("option")
        "Ctrl" -> listOf("control")
        else -> emptyList()
    }

    @StringRes
    private fun monitorName(direction: PcDisplayDirection) = when (direction) {
        PcDisplayDirection.Left -> R.string.pc_action_monitor_left
        PcDisplayDirection.Up -> R.string.pc_action_monitor_up
        PcDisplayDirection.Down -> R.string.pc_action_monitor_down
        PcDisplayDirection.Right -> R.string.pc_action_monitor_right
    }

    @StringRes
    private fun keyName(key: String) = when (key) {
        "Backspace" -> R.string.pc_action_key_backspace
        "Enter" -> R.string.pc_action_key_enter
        "Escape" -> R.string.pc_action_key_escape
        "Tab" -> R.string.pc_action_key_tab
        "ArrowLeft" -> R.string.pc_action_key_arrow_left
        "ArrowUp" -> R.string.pc_action_key_arrow_up
        "ArrowDown" -> R.string.pc_action_key_arrow_down
        else -> R.string.pc_action_key_arrow_right
    }
}
