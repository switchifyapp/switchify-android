package com.enaboapps.switchify.pc.remote.actions

import androidx.annotation.StringRes
import com.enaboapps.switchify.R
import com.enaboapps.switchify.pc.protocol.PcCommandTypes
import com.enaboapps.switchify.pc.protocol.PcCommands
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.remote.PcDraftStore
import com.enaboapps.switchify.pc.remote.PcLiveTypingController
import com.enaboapps.switchify.pc.remote.PcRemoteSession
import com.enaboapps.switchify.pc.remote.PcText
import com.enaboapps.switchify.pc.remote.PcTypingMode
import com.enaboapps.switchify.pc.remote.layouts.PcLayoutSurface
import kotlin.math.max
import kotlin.math.min

data class PcTypingContext(
    val mode: PcTypingMode,
    val draft: String,
    val submitting: Boolean,
    val submitLive: suspend () -> Unit
)

data class PcActionContext(
    val surface: PcLayoutSurface,
    val session: PcRemoteSession,
    val platform: PcPlatform?,
    val drafts: PcDraftStore,
    val typing: PcTypingContext? = null
)

enum class PcActionUnavailable(@StringRes val messageRes: Int) {
    Unsupported(R.string.pc_action_unavailable_unsupported),
    TypingOnly(R.string.pc_action_unavailable_typing_only),
    SpeedAtMinimum(R.string.pc_action_unavailable_speed_minimum),
    SpeedAtMaximum(R.string.pc_action_unavailable_speed_maximum),
    NeedsMultipleMonitors(R.string.pc_action_unavailable_multiple_monitors),
    WaitForEnter(R.string.pc_action_unavailable_wait_enter),
    DraftModeRequired(R.string.pc_action_unavailable_draft_mode),
    DraftEmpty(R.string.pc_action_unavailable_draft_empty)
}


enum class PcActionIcon {
    DoubleClick,
    Mouse,
    Drag,
    ScrollUp,
    ScrollDown,
    Slower,
    Faster,
    Warning
}

data class PcActionPresentation(
    val label: PcText,
    val accessibilityLabel: PcText? = null,
    val icon: PcActionIcon? = null,
    val keySize: Boolean = false,
    val emphasized: Boolean = false,
    val selected: Boolean? = null,
    val danger: Boolean = false
)

data class PcResolvedAction(
    val definition: PcActionDefinition,
    val name: PcText,
    val presentation: PcActionPresentation,
    val unavailable: PcActionUnavailable?
) {
    val id: String get() = definition.id
    val enabled: Boolean get() = unavailable == null
}

object PcActionRuntime {
    fun modifierLabel(key: String, platform: PcPlatform?): String =
        if (platform == PcPlatform.MacOs) {
            when (key) {
                "Ctrl" -> "Control"
                "Alt" -> "Option"
                "Shift" -> "Shift"
                "Meta" -> "Command"
                else -> key
            }
        } else if (key == "Meta") {
            "Start"
        } else {
            key
        }

    fun movementStep(session: PcRemoteSession): Double {
        val profile = session.profile
        val preferred = profile?.capabilities?.pointerSpeed?.baseMoveDelta
            ?: profile?.recommendedDeltas?.medium
            ?: DEFAULT_STEP
        return max(1.0, min(profile?.maxDelta ?: DEFAULT_STEP, preferred))
    }

    fun availability(action: PcActionDefinition, context: PcActionContext): PcActionUnavailable? {
        val session = context.session
        val typing = context.typing
        val state = session.snapshot()
        fun supported(type: String) = if (session.supports(type)) null else PcActionUnavailable.Unsupported
        if (!PcActionCatalog.canPlace(action.id, context.surface)) return PcActionUnavailable.TypingOnly
        return when (val behavior = action.behavior) {
            is PcActionBehavior.Movement -> supported(PcCommandTypes.MOUSE_MOVE)
            is PcActionBehavior.Click -> supported(clickType(behavior.button))
            PcActionBehavior.Drag -> supported(
                if (state.dragging) PcCommandTypes.MOUSE_DRAG_END else PcCommandTypes.MOUSE_DRAG_START
            )
            is PcActionBehavior.Scroll -> supported(PcCommandTypes.MOUSE_SCROLL)
            is PcActionBehavior.Speed -> {
                val speed = session.profile?.capabilities?.pointerSpeed
                when {
                    speed == null || !speed.supported || !speed.setSupported ||
                        !session.supports(PcCommandTypes.POINTER_SPEED_SET) -> PcActionUnavailable.Unsupported
                    behavior.direction < 0 && speed.scalePercent <= speed.minScalePercent -> PcActionUnavailable.SpeedAtMinimum
                    behavior.direction > 0 && speed.scalePercent >= speed.maxScalePercent -> PcActionUnavailable.SpeedAtMaximum
                    else -> null
                }
            }
            is PcActionBehavior.Monitor -> {
                val display = session.profile?.capabilities?.displayNavigation
                if (display != null && display.supported && display.displayCount > 1) {
                    supported(PcCommandTypes.POINTER_DISPLAY_MOVE)
                } else {
                    PcActionUnavailable.NeedsMultipleMonitors
                }
            }
            is PcActionBehavior.Modifier -> supported(
                if (behavior.key in state.modifiers) PcCommandTypes.KEYBOARD_MODIFIER_UP else PcCommandTypes.KEYBOARD_MODIFIER_DOWN
            )
            is PcActionBehavior.Shortcut -> supported(PcCommandTypes.KEYBOARD_SHORTCUT)
            is PcActionBehavior.Window -> supported(PcCommandTypes.WINDOW_CONTROL)
            is PcActionBehavior.Key -> if (context.surface == PcLayoutSurface.Typing && typing?.mode == PcTypingMode.Live) {
                when {
                    !session.supportsAll(*LIVE_TYPING_COMMANDS) -> PcActionUnavailable.Unsupported
                    typing.submitting && behavior.key == PcLiveTypingController.ENTER -> PcActionUnavailable.WaitForEnter
                    else -> null
                }
            } else {
                supported(PcCommandTypes.KEYBOARD_KEY)
            }
            is PcActionBehavior.Draft -> when {
                typing == null || typing.mode != PcTypingMode.Draft -> PcActionUnavailable.DraftModeRequired
                typing.draft.isEmpty() -> PcActionUnavailable.DraftEmpty
                behavior.operation == PcDraftOperation.Send -> supported(PcCommandTypes.KEYBOARD_TYPE_TEXT)
                else -> null
            }
        }
    }

    fun presentation(action: PcActionDefinition, context: PcActionContext): PcActionPresentation {
        val state = context.session.snapshot()
        return when (val behavior = action.behavior) {
            is PcActionBehavior.Movement -> PcActionPresentation(
                label = PcText.Literal(ARROWS[behavior.dy + 1][behavior.dx + 1]),
                accessibilityLabel = PcText.Res(movementAccessibilityName(behavior)),
                keySize = true
            )
            is PcActionBehavior.Click -> when (behavior.button) {
                PcClickButton.Left -> PcActionPresentation(
                    label = PcText.Res(R.string.pc_action_click_label),
                    accessibilityLabel = PcText.Res(R.string.pc_action_left_click),
                    keySize = true,
                    emphasized = true
                )
                PcClickButton.Double -> PcActionPresentation(PcText.Res(action.nameRes), icon = PcActionIcon.DoubleClick)
                PcClickButton.Right -> PcActionPresentation(PcText.Res(action.nameRes), icon = PcActionIcon.Mouse)
            }
            PcActionBehavior.Drag -> PcActionPresentation(
                label = PcText.Res(if (state.dragging) R.string.pc_action_end_drag else R.string.pc_action_start_drag),
                icon = PcActionIcon.Drag,
                selected = state.dragging
            )
            is PcActionBehavior.Scroll -> PcActionPresentation(
                label = PcText.Res(action.nameRes),
                icon = if (behavior.dy > 0) PcActionIcon.ScrollUp else PcActionIcon.ScrollDown
            )
            is PcActionBehavior.Speed -> PcActionPresentation(
                label = PcText.Res(if (behavior.direction < 0) R.string.pc_action_slower else R.string.pc_action_faster),
                icon = if (behavior.direction < 0) PcActionIcon.Slower else PcActionIcon.Faster
            )
            is PcActionBehavior.Monitor -> PcActionPresentation(
                label = PcText.Res(monitorLabel(behavior)),
                accessibilityLabel = PcText.Res(action.nameRes)
            )
            is PcActionBehavior.Modifier -> PcActionPresentation(
                label = PcText.Literal(modifierLabel(behavior.key, context.platform)),
                accessibilityLabel = name(action, context),
                selected = behavior.key in state.modifiers
            )
            is PcActionBehavior.Shortcut -> PcActionPresentation(
                label = PcText.Literal(
                    (state.modifiers.map { modifierLabel(it, context.platform) } + behavior.key).joinToString("+")
                )
            )
            is PcActionBehavior.Window -> if (behavior.action == CLOSE_FOCUSED) {
                PcActionPresentation(
                    label = PcText.Res(R.string.pc_action_close_label),
                    accessibilityLabel = PcText.Res(action.nameRes),
                    icon = PcActionIcon.Warning,
                    danger = true
                )
            } else {
                PcActionPresentation(PcText.Res(action.nameRes))
            }
            is PcActionBehavior.Key -> PcActionPresentation(
                label = PcText.Literal(behavior.key.removePrefix(ARROW_PREFIX)),
                accessibilityLabel = if (behavior.key.startsWith(ARROW_PREFIX)) PcText.Res(action.nameRes) else null,
                keySize = true
            )
            is PcActionBehavior.Draft -> PcActionPresentation(
                PcText.Res(
                    if (behavior.operation == PcDraftOperation.Clear) R.string.pc_action_clear_label else R.string.pc_action_send_label
                )
            )
        }
    }

    fun name(action: PcActionDefinition, context: PcActionContext): PcText = when (val behavior = action.behavior) {
        is PcActionBehavior.Modifier -> PcText.Res(action.nameRes, listOf(modifierLabel(behavior.key, context.platform)))
        is PcActionBehavior.Shortcut -> PcText.Res(action.nameRes, listOf(behavior.key))
        else -> PcText.Res(action.nameRes)
    }

    fun resolve(context: PcActionContext): List<PcResolvedAction> =
        PcActionCatalog.actions
            .filter { PcActionCatalog.canPlace(it.id, context.surface) }
            .map { action ->
                PcResolvedAction(
                    definition = action,
                    name = name(action, context),
                    presentation = presentation(action, context),
                    unavailable = availability(action, context)
                )
            }

    suspend fun execute(action: PcActionDefinition, context: PcActionContext) {
        if (availability(action, context) != null) return
        val session = context.session
        val typing = context.typing
        when (val behavior = action.behavior) {
            is PcActionBehavior.Movement -> {
                val step = movementStep(session)
                session.mouse(PcCommands.move(behavior.dx * step, behavior.dy * step), repeatable = true)
            }
            is PcActionBehavior.Click -> session.command(
                when (behavior.button) {
                    PcClickButton.Left -> PcCommands.click()
                    PcClickButton.Right -> PcCommands.rightClick()
                    PcClickButton.Double -> PcCommands.doubleClick()
                }
            )
            PcActionBehavior.Drag -> session.toggleDrag()
            is PcActionBehavior.Scroll -> session.mouse(PcCommands.scroll(0.0, behavior.dy.toDouble()), repeatable = true)
            is PcActionBehavior.Speed -> {
                val speed = session.profile?.capabilities?.pointerSpeed ?: return
                val target = max(
                    speed.minScalePercent,
                    min(speed.maxScalePercent, speed.scalePercent + behavior.direction * speed.stepPercent)
                )
                session.command(PcCommands.pointerSpeed(target))
            }
            is PcActionBehavior.Monitor -> session.command(PcCommands.displayMove(behavior.direction))
            is PcActionBehavior.Modifier -> session.toggleModifier(behavior.key)
            is PcActionBehavior.Shortcut -> session.shortcut(behavior.key)
            is PcActionBehavior.Window -> session.command(PcCommands.windowControl(behavior.action))
            is PcActionBehavior.Key -> if (context.surface == PcLayoutSurface.Typing && typing?.mode == PcTypingMode.Live) {
                if (behavior.key == PcLiveTypingController.ENTER) typing.submitLive() else session.streamKey(behavior.key)
            } else {
                session.key(behavior.key)
            }
            is PcActionBehavior.Draft -> if (behavior.operation == PcDraftOperation.Clear) {
                context.drafts.setDraft("")
            } else if (typing != null) {
                val text = typing.draft
                if (session.command(PcCommands.typeText(text)) && context.drafts.draft.value == text) {
                    context.drafts.setDraft("")
                }
            }
        }
    }

    private fun clickType(button: PcClickButton) = when (button) {
        PcClickButton.Left -> PcCommandTypes.MOUSE_CLICK
        PcClickButton.Right -> PcCommandTypes.MOUSE_RIGHT_CLICK
        PcClickButton.Double -> PcCommandTypes.MOUSE_DOUBLE_CLICK
    }

    @StringRes
    private fun movementAccessibilityName(behavior: PcActionBehavior.Movement) = when (behavior.dy to behavior.dx) {
        -1 to -1 -> R.string.pc_action_move_short_up_left
        -1 to 0 -> R.string.pc_action_move_short_up
        -1 to 1 -> R.string.pc_action_move_short_up_right
        0 to -1 -> R.string.pc_action_move_short_left
        0 to 1 -> R.string.pc_action_move_short_right
        1 to -1 -> R.string.pc_action_move_short_down_left
        1 to 0 -> R.string.pc_action_move_short_down
        else -> R.string.pc_action_move_short_down_right
    }

    @StringRes
    private fun monitorLabel(behavior: PcActionBehavior.Monitor) = when (behavior.direction.protocolValue) {
        "left" -> R.string.pc_action_monitor_label_left
        "up" -> R.string.pc_action_monitor_label_up
        "down" -> R.string.pc_action_monitor_label_down
        else -> R.string.pc_action_monitor_label_right
    }

    val LIVE_TYPING_COMMANDS = arrayOf(
        PcCommandTypes.KEYBOARD_STREAM_OPEN,
        PcCommandTypes.KEYBOARD_STREAM_CHUNK,
        PcCommandTypes.KEYBOARD_STREAM_KEY,
        PcCommandTypes.KEYBOARD_STREAM_CLOSE
    )

    private const val DEFAULT_STEP = 128.0
    private const val CLOSE_FOCUSED = "closeFocused"
    private const val ARROW_PREFIX = "Arrow"
    private val ARROWS = listOf(
        listOf("↖", "↑", "↗"),
        listOf("←", "", "→"),
        listOf("↙", "↓", "↘")
    )
}
