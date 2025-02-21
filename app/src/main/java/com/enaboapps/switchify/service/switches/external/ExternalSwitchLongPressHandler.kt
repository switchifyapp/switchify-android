package com.enaboapps.switchify.service.switches.external

import android.content.Context
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.switches.SwitchAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * This object manages the long press actions on a switch.
 */
object ExternalSwitchLongPressHandler {
    private var longPressJob: Job? = null
    private var holdActions: List<SwitchAction>? = null
    private var actionToPerform: SwitchAction? = null

    /**
     * Initiates the long press action sequence.
     * @param context The context.
     * @param actions The list of actions to perform on long press.
     */
    fun startLongPress(
        context: Context,
        actions: List<SwitchAction>
    ) {
        holdActions = actions
        val holdTime = PreferenceManager(context)
            .getLongValue(PreferenceManager.PREFERENCE_KEY_SWITCH_HOLD_TIME)

        longPressJob = CoroutineScope(Dispatchers.Main).launch {
            delay(holdTime)

            // Toggle gesture lock if enabled
            if (GestureManager.getInstance().isGestureLockEnabled()) {
                GestureManager.getInstance().toggleGestureLock()
                return@launch
            }

            holdActions?.let { actionsList ->
                for (action in actionsList) {
                    actionToPerform = action
                    val name = action.getActionName()
                    ServiceMessageHUD.instance.showMessage(
                        R.string.hud_release_to_perform,
                        arrayOf(name),
                        ServiceMessageHUD.MessageType.DISAPPEARING,
                        ServiceMessageHUD.Time.SHORT
                    )
                    delay(holdTime) // Use the switch hold time as delay between actions
                }
            }
        }
    }

    /**
     * Stops the long press action sequence.
     * Performs the action if it is not null.
     * @param scanningManager The scanning manager. Can be null (will not perform the action).
     */
    fun stopLongPress(scanningManager: ScanningManager?) {
        actionToPerform?.let {
            scanningManager?.performAction(it)
            actionToPerform = null
        }
        longPressJob?.cancel()
        longPressJob = null
    }
}