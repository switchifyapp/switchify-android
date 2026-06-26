package com.enaboapps.switchify.service.switches.external

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.pc.PcMouseRepeatManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.Tasks
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.stats.StatsCollector
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.isScanMovementAction

/**
 * Manages switch input events and their associated actions in the accessibility service.
 * Handles switch presses, releases, long presses, and notification interactions.
 *
 * @property context Application context used for accessing system services and resources
 * @property scanningManager Manages the scanning interface for switch-based navigation
 * @property switchEventProvider Provides access to switch events
 */
class ExternalSwitchListener(
    private val context: Context,
    private val scanningManager: ScanningManager,
    private val switchEventProvider: SwitchEventProvider
) {
    companion object {
        const val TAG = "ExternalSwitchListener"
    }

    private val preferenceManager = PreferenceManager(context)

    private var pressSession: ExternalSwitchPressSession = ExternalSwitchPressSession.None
    private var gestureLockHoldFired = false
    private val pauseSwitchHoldTracker = PauseSwitchHoldTracker()

    /** Timestamp of the last switch press for handling repeat events */
    private var lastSwitchPressedTime: Long = 0

    /** Key code of the last pressed switch for handling repeat events */
    private var lastSwitchPressedCode: Int = 0

    /**
     * Handles switch press events. This is the main entry point for processing
     * switch interactions.
     *
     * @param keyCode The system key code of the pressed switch
     * @return true if the event was handled and should be consumed, false otherwise
     */
    fun onSwitchPressed(keyCode: Int): Boolean {
        val switchEvent = findSwitchEvent(keyCode) ?: return false
        switchEvent.log()

        // Record stats for switch press
        StatsCollector.getInstance().recordSwitchPress("external", keyCode.toString())

        val pauseManager = ServiceCore.getPauseManager()
        if (pauseManager.isPaused) {
            pauseSwitchHoldTracker.onPressed(keyCode, System.currentTimeMillis())
            pauseManager.handleSwitchDuringPause()
            return false
        }

        if (PcMouseRepeatManager.instance.stopForSwitchPress()) {
            pressSession = ExternalSwitchPressSession.ReleaseSwallowed
            ExternalSwitchLongPressHandler.cancel()
            return true
        }

        if (!switchEvent.pressAction.isScanMovementAction() &&
            Tasks.getInstance().stopActiveStoppableTask()
        ) {
            pressSession = ExternalSwitchPressSession.ReleaseSwallowed
            ExternalSwitchLongPressHandler.cancel()
            return true
        }

        if (GestureLockManager.instance.isGestureLockEngaged()) {
            val pressTime = System.currentTimeMillis()
            pressSession = ExternalSwitchPressSession.GestureLockReplayCandidate(switchEvent, pressTime)
            gestureLockHoldFired = false
            val holdTime =
                preferenceManager.getLongValue(PreferenceManager.PREFERENCE_KEY_SWITCH_HOLD_TIME)
            ExternalSwitchLongPressHandler.cancel()
            ExternalSwitchLongPressHandler.startOneShotHold(context, holdTime) {
                gestureLockHoldFired = true
                GestureLockManager.instance.toggleGestureLock()
                pressSession = ExternalSwitchPressSession.ReleaseSwallowed
            }
            return true
        }

        processSwitchPressedActions(switchEvent)
        return true
    }

    /**
     * Handles switch release events.
     *
     * @param keyCode The system key code of the released switch
     * @return true if the event was handled and should be consumed, false otherwise
     */
    fun onSwitchReleased(keyCode: Int): Boolean {
        val switchEvent = findSwitchEvent(keyCode) ?: return false

        val pauseManager = ServiceCore.getPauseManager()
        if (pauseManager.isPaused) {
            val holdDuration = preferenceManager.getLongValue(
                PreferenceManager.PREFERENCE_KEY_HOLD_TO_UNPAUSE_DURATION,
                2000L
            )
            pauseSwitchHoldTracker.consumeReleasePressTime(keyCode)?.let { pressTime ->
                pauseManager.checkHoldToUnpause(pressTime, holdDuration)
            }
            return false
        }

        val session = pressSession.takeIf { it.matches(switchEvent) } ?: return true

        if (scanningManager.stopMoveRepeat()) {
            clearPressSession()
            return true
        }

        if (handleSwitchPressedRepeat(keyCode)) {
            ExternalSwitchLongPressHandler.cancel()
            clearPressSession()
            return true
        }

        if (SelectionHandler.isAutoSelectInProgress()) {
            cancelCurrentPressInteraction()
            SelectionHandler.performSelectionAction()
            return true
        }

        when (session) {
            ExternalSwitchPressSession.None -> return true
            ExternalSwitchPressSession.ReleaseSwallowed -> {
                ExternalSwitchLongPressHandler.cancel()
                clearPressSession()
                return true
            }

            is ExternalSwitchPressSession.GestureLockReplayCandidate -> {
                ExternalSwitchLongPressHandler.cancel()
                if (gestureLockHoldFired) {
                    clearPressSession()
                    resumeScanningIfNeeded()
                    return true
                }
                scanningManager.performAction(session.switchEvent.pressAction)
                clearPressSession()
                return true
            }

            is ExternalSwitchPressSession.HoldPicker -> {
                val performedLongPressAction =
                    ExternalSwitchLongPressHandler.stopAndPerformPending(scanningManager)
                if (performedLongPressAction) {
                    clearPressSession()
                    if (!Tasks.getInstance().hasActiveStoppableTask()) {
                        resumeScanningIfNeeded()
                    }
                    return true
                }
                processSwitchReleasedActions(session.switchEvent, session.pressTime)
            }

            is ExternalSwitchPressSession.ShortPressCandidate -> {
                processSwitchReleasedActions(session.switchEvent, session.pressTime)
            }
        }
        return true
    }

    /**
     * Looks up the switch event configuration for a given key code.
     *
     * @param keyCode The system key code to look up
     * @return The corresponding SwitchEvent or null if not found
     */
    private fun findSwitchEvent(keyCode: Int): SwitchEvent? {
        Log.d("ExternalSwitchListener", "Finding switch event for keyCode: $keyCode")
        val switchEvent = switchEventProvider.findExternal(keyCode.toString())
        Log.d("ExternalSwitchListener", "Found switch event: $switchEvent")
        return switchEvent
    }

    /**
     * Handles repeated switch press actions based on timing and settings.
     *
     * @param keyCode The system key code of the switch
     * @return true if the repeat should be ignored
     */
    private fun handleSwitchPressedRepeat(keyCode: Int): Boolean {
        return if (shouldIgnoreSwitchRepeat(keyCode)) {
            Log.d("ExternalSwitchListener", "Ignoring switch repeat: $keyCode")
            true
        } else {
            updateSwitchPressTime(keyCode)
            false
        }
    }

    /**
     * Processes actions associated with a switch press event.
     *
     * @param switchEvent The switch event to process
     * @return true if the event was processed successfully
     */
    private fun processSwitchPressedActions(switchEvent: SwitchEvent) {
        if (scanningManager.startMoveRepeat(switchEvent.pressAction)) {
            pressSession = ExternalSwitchPressSession.ShortPressCandidate(
                switchEvent,
                System.currentTimeMillis()
            )
            return
        }
        handleLongPressAction(switchEvent)
    }

    /**
     * Handles long press actions for a switch event.
     *
     * @param switchEvent The switch event to handle
     */
    private fun handleLongPressAction(switchEvent: SwitchEvent) {
        val pressTime = System.currentTimeMillis()
        if (switchEvent.holdActions.isEmpty()) {
            pressSession = ExternalSwitchPressSession.ShortPressCandidate(switchEvent, pressTime)
            ExternalSwitchLongPressHandler.cancel()
        } else {
            pressSession = ExternalSwitchPressSession.HoldPicker(switchEvent, pressTime)
            ExternalSwitchLongPressHandler.startHoldPicker(
                context,
                switchEvent.name,
                switchEvent.holdActions
            )
        }
        scanningManager.pauseScanning()
    }

    /**
     * Processes actions associated with a switch release event.
     *
     * @param switchEvent The switch event being released
     * @param absorbedAction The absorbed action from the initial press
     */
    private fun processSwitchReleasedActions(
        switchEvent: SwitchEvent,
        pressTime: Long
    ) {
        if (!switchEvent.pressAction.isScanMovementAction() &&
            Tasks.getInstance().stopActiveStoppableTask()
        ) {
            clearPressSession()
            return
        }

        val timeElapsed = System.currentTimeMillis() - pressTime

        val switchHoldTime =
            preferenceManager.getLongValue(PreferenceManager.PREFERENCE_KEY_SWITCH_HOLD_TIME)

        var performedPressAction = false

        when {
            SelectionHandler.isAutoSelectInProgress() &&
                    switchEvent.holdActions.isNotEmpty() ->
                SelectionHandler.performSelectionAction()

            switchEvent.holdActions.isEmpty() -> {
                performReleasePressAction(switchEvent.pressAction)
                performedPressAction = true
            }

            timeElapsed < switchHoldTime -> {
                performReleasePressAction(switchEvent.pressAction)
                performedPressAction = true
            }
        }

        if (performedPressAction && Tasks.getInstance().hasActiveStoppableTask()) {
            clearPressSession()
            return
        }

        if (performedPressAction && switchEvent.pressAction.id == SwitchAction.ACTION_SELECT) {
            clearPressSession()
            return
        }

        clearPressSession()
        resumeScanningIfNeeded()
    }

    private fun performReleasePressAction(action: SwitchAction) {
        if (action.id == SwitchAction.ACTION_SELECT && !SelectionHandler.isAutoSelectInProgress()) {
            scanningManager.resumeScanning()
        }
        scanningManager.performAction(action)
    }

    /**
     * Determines if a switch repeat should be ignored based on timing and settings.
     *
     * @param keyCode The system key code of the switch
     * @return true if the repeat should be ignored
     */
    private fun shouldIgnoreSwitchRepeat(keyCode: Int): Boolean {
        val currentTime = System.currentTimeMillis()
        val ignoreRepeat =
            preferenceManager.getBooleanValue(PreferenceManager.PREFERENCE_KEY_SWITCH_IGNORE_REPEAT)
        val ignoreRepeatDelay =
            preferenceManager.getLongValue(PreferenceManager.PREFERENCE_KEY_SWITCH_IGNORE_REPEAT_DELAY)

        return ignoreRepeat &&
                keyCode == lastSwitchPressedCode &&
                currentTime - lastSwitchPressedTime < ignoreRepeatDelay
    }

    /**
     * Updates the timing information for switch press repeat handling.
     *
     * @param keyCode The system key code of the pressed switch
     */
    private fun updateSwitchPressTime(keyCode: Int) {
        lastSwitchPressedTime = System.currentTimeMillis()
        lastSwitchPressedCode = keyCode
    }

    /**
     * Resets the last switch press time and code.
     * Resets the long press handler.
     */
    fun reset() {
        lastSwitchPressedTime = 0
        lastSwitchPressedCode = 0
        pauseSwitchHoldTracker.reset()
        clearPressSession()
        ExternalSwitchLongPressHandler.cancel()
    }

    private fun ExternalSwitchPressSession.matches(switchEvent: SwitchEvent): Boolean {
        return when (this) {
            ExternalSwitchPressSession.None -> false
            ExternalSwitchPressSession.ReleaseSwallowed -> true
            is ExternalSwitchPressSession.ShortPressCandidate -> this.switchEvent == switchEvent
            is ExternalSwitchPressSession.HoldPicker -> this.switchEvent == switchEvent
            is ExternalSwitchPressSession.GestureLockReplayCandidate -> this.switchEvent == switchEvent
        }
    }

    private fun clearPressSession() {
        pressSession = ExternalSwitchPressSession.None
        gestureLockHoldFired = false
    }

    private fun cancelCurrentPressInteraction() {
        ExternalSwitchLongPressHandler.cancel()
        clearPressSession()
    }

    private fun resumeScanningIfNeeded() {
        if (!SelectionHandler.isAutoSelectInProgress()) {
            scanningManager.resumeScanning()
        }
    }
}
