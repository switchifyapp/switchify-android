package com.enaboapps.switchify.service.switches.external

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.service.core.ServiceCore
import com.enaboapps.switchify.service.core.Tasks
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.stats.StatsCollector
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.switches.SwitchAction
import com.enaboapps.switchify.switches.SwitchEvent

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

    /** Tracks the most recent switch action for handling release events */
    private var latestAction: AbsorbedSwitchAction? = null

    /** Timestamp of the last switch press for handling repeat events */
    private var lastSwitchPressedTime: Long = 0

    /** Key code of the last pressed switch for handling repeat events */
    private var lastSwitchPressedCode: Int = 0

    /** Timestamp of when a switch was pressed during pause (for hold-to-unpause) */
    private var pauseSwitchPressedTime: Long = 0


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

        if (Tasks.getInstance().stopOngoingTaskForSwitchPress()) {
            latestAction = null
            return true
        }

        val pauseManager = ServiceCore.getPauseManager()
        if (pauseManager.isPaused) {
            // Store the timestamp for hold-to-unpause check
            pauseSwitchPressedTime = System.currentTimeMillis()
            pauseManager.handleSwitchDuringPause()
            return false
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
        val absorbedAction = latestAction?.takeIf { it.switchEvent == switchEvent } ?: return true

        val pauseManager = ServiceCore.getPauseManager()
        if (pauseManager.isPaused) {
            // Check if switch was held long enough to unpause
            if (pauseSwitchPressedTime > 0) {
                val holdDuration = preferenceManager.getLongValue(
                    PreferenceManager.PREFERENCE_KEY_HOLD_TO_UNPAUSE_DURATION,
                    2000L // Default: 2 seconds
                )
                pauseManager.checkHoldToUnpause(pauseSwitchPressedTime, holdDuration)
                pauseSwitchPressedTime = 0
            }
            return false
        }

        if (Tasks.getInstance().shouldAbsorbSwitchRelease()) {
            latestAction = null
            return true
        }

        if (scanningManager.stopMoveRepeat()) return true
        val performedLongPressAction = ExternalSwitchLongPressHandler.stopLongPress(scanningManager)
        if (performedLongPressAction && Tasks.getInstance().shouldAbsorbSwitchReleaseAfterAction()) {
            latestAction = null
            return true
        }

        if (handleSwitchPressedRepeat(keyCode)) {
            return true
        }

        if (SelectionHandler.isAutoSelectInProgress()) {
            SelectionHandler.performSelectionAction()
            return true
        }

        processSwitchReleasedActions(switchEvent, absorbedAction)
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
        latestAction = AbsorbedSwitchAction(switchEvent, System.currentTimeMillis())
        if (scanningManager.startMoveRepeat(switchEvent.pressAction)) {
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
        ExternalSwitchLongPressHandler.startLongPress(context, switchEvent.name, switchEvent.holdActions)
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
        absorbedAction: AbsorbedSwitchAction
    ) {
        val timeElapsed = System.currentTimeMillis() - absorbedAction.time

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

        if (Tasks.getInstance().shouldAbsorbSwitchReleaseAfterAction()) {
            latestAction = null
            return
        }

        if (performedPressAction && switchEvent.pressAction.id == SwitchAction.ACTION_SELECT) {
            return
        }

        if (!SelectionHandler.isAutoSelectInProgress()) {
            scanningManager.resumeScanning()
        }
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
        pauseSwitchPressedTime = 0
        ExternalSwitchLongPressHandler.stopLongPress(null)
    }

    /**
     * Data class representing an absorbed switch action and its timing information.
     *
     * @property switchEvent The switch event that was absorbed
     * @property time The timestamp when the action was absorbed
     */
    private data class AbsorbedSwitchAction(val switchEvent: SwitchEvent, val time: Long)
}
