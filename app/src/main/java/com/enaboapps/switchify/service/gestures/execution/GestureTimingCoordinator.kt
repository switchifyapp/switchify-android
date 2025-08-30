package com.enaboapps.switchify.service.gestures.execution

import android.os.Handler
import android.os.Looper
import com.enaboapps.switchify.service.gestures.GestureStateManager
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.visuals.GestureVisualManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Coordinates timing and sequencing for complex multi-stroke gestures.
 * Handles visual feedback timing synchronization and gesture sequence management.
 * Integrates with GestureStateManager for coordinated timing events.
 */
class GestureTimingCoordinator {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pendingActions = ConcurrentHashMap<String, Runnable>()
    private val sequenceCounter = AtomicLong(0)

    /**
     * Interface for handling timed gesture events.
     */
    interface TimedGestureHandler {
        fun onGestureReady(gestureType: GestureType, sequenceId: String)
        fun onVisualUpdate(gestureType: GestureType, sequenceId: String, stage: String)
        fun onSequenceComplete(sequenceId: String)
        fun onSequenceCancelled(sequenceId: String)
    }

    /**
     * Coordinates a double tap gesture with proper timing and visual feedback.
     *
     * @param handler Handler for timed events
     * @param visualManager Visual feedback manager
     * @param tapInterval Interval between taps in milliseconds
     * @param tapDuration Duration of each tap
     * @return Sequence ID for tracking this gesture
     */
    fun coordinateDoubleTap(
        handler: TimedGestureHandler,
        visualManager: GestureVisualManager?,
        tapInterval: Long = 250L,
        tapDuration: Long = 100L
    ): String {
        val sequenceId = generateSequenceId("double_tap")

        scope.launch {
            try {
                // First tap ready
                handler.onGestureReady(GestureType.DOUBLE_TAP, sequenceId)
                handler.onVisualUpdate(GestureType.DOUBLE_TAP, sequenceId, "first_tap")

                // Wait for interval
                delay(tapInterval + tapDuration)

                // Check if sequence is still valid
                if (isSequenceActive(sequenceId)) {
                    handler.onVisualUpdate(GestureType.DOUBLE_TAP, sequenceId, "second_tap")

                    // Wait for second tap completion
                    delay(tapDuration)

                    if (isSequenceActive(sequenceId)) {
                        handler.onSequenceComplete(sequenceId)
                    }
                }
            } catch (e: Exception) {
                handler.onSequenceCancelled(sequenceId)
            } finally {
                cleanupSequence(sequenceId)
            }
        }

        return sequenceId
    }

    /**
     * Coordinates a hold-and-drag gesture with proper timing.
     *
     * @param handler Handler for timed events
     * @param visualManager Visual feedback manager
     * @param holdDuration Duration of the hold phase
     * @param dragDuration Duration of the drag phase
     * @return Sequence ID for tracking this gesture
     */
    fun coordinateHoldAndDrag(
        handler: TimedGestureHandler,
        visualManager: GestureVisualManager?,
        holdDuration: Long = 400L,
        dragDuration: Long = 1500L
    ): String {
        val sequenceId = generateSequenceId("hold_and_drag")

        scope.launch {
            try {
                // Hold phase starts
                handler.onGestureReady(GestureType.HOLD_AND_DRAG, sequenceId)
                handler.onVisualUpdate(GestureType.HOLD_AND_DRAG, sequenceId, "hold_start")

                // Wait for hold duration
                delay(holdDuration - 5) // Start drag slightly before hold ends

                if (isSequenceActive(sequenceId)) {
                    handler.onVisualUpdate(GestureType.HOLD_AND_DRAG, sequenceId, "drag_start")

                    // Wait for drag completion
                    delay(dragDuration)

                    if (isSequenceActive(sequenceId)) {
                        handler.onVisualUpdate(GestureType.HOLD_AND_DRAG, sequenceId, "drag_end")
                        handler.onSequenceComplete(sequenceId)
                    }
                }
            } catch (e: Exception) {
                handler.onSequenceCancelled(sequenceId)
            } finally {
                cleanupSequence(sequenceId)
            }
        }

        return sequenceId
    }

    /**
     * Coordinates visual feedback timing for simple gestures.
     *
     * @param handler Handler for timed events
     * @param gestureType Type of gesture
     * @param duration Duration of the gesture
     * @param visualStages List of visual stages with their timing
     * @return Sequence ID for tracking this coordination
     */
    fun coordinateVisualSequence(
        handler: TimedGestureHandler,
        gestureType: GestureType,
        duration: Long,
        visualStages: List<Pair<String, Long>> = emptyList()
    ): String {
        val sequenceId = generateSequenceId("visual_${gestureType.name.lowercase()}")

        scope.launch {
            try {
                handler.onGestureReady(gestureType, sequenceId)

                // Process visual stages
                var currentTime = 0L
                for ((stage, stageDelay) in visualStages) {
                    if (stageDelay > currentTime) {
                        delay(stageDelay - currentTime)
                        currentTime = stageDelay
                    }

                    if (isSequenceActive(sequenceId)) {
                        handler.onVisualUpdate(gestureType, sequenceId, stage)
                    } else {
                        break
                    }
                }

                // Wait for gesture completion if needed
                if (duration > currentTime && isSequenceActive(sequenceId)) {
                    delay(duration - currentTime)
                }

                if (isSequenceActive(sequenceId)) {
                    handler.onSequenceComplete(sequenceId)
                }
            } catch (e: Exception) {
                handler.onSequenceCancelled(sequenceId)
            } finally {
                cleanupSequence(sequenceId)
            }
        }

        return sequenceId
    }

    /**
     * Schedules a delayed action with proper cleanup.
     *
     * @param actionId Unique identifier for the action
     * @param delay Delay in milliseconds
     * @param action Action to execute
     */
    fun scheduleAction(actionId: String, delay: Long, action: () -> Unit) {
        val runnable = Runnable {
            try {
                action()
            } catch (e: Exception) {
                android.util.Log.e(
                    "GestureTimingCoordinator",
                    "Error executing scheduled action: $actionId",
                    e
                )
            } finally {
                pendingActions.remove(actionId)
            }
        }

        pendingActions[actionId] = runnable
        mainHandler.postDelayed(runnable, delay)
    }

    /**
     * Cancels a scheduled action.
     *
     * @param actionId Identifier of the action to cancel
     */
    fun cancelAction(actionId: String) {
        pendingActions.remove(actionId)?.let { runnable ->
            mainHandler.removeCallbacks(runnable)
        }
    }

    /**
     * Cancels a gesture sequence.
     *
     * @param sequenceId Identifier of the sequence to cancel
     */
    fun cancelSequence(sequenceId: String) {
        cleanupSequence(sequenceId)
    }

    /**
     * Cancels all pending actions and sequences.
     */
    fun cancelAll() {
        // Cancel all scheduled actions
        pendingActions.values.forEach { runnable ->
            mainHandler.removeCallbacks(runnable)
        }
        pendingActions.clear()

        // Cancel all coroutines
        scope.cancel()
    }

    /**
     * Generates a unique sequence ID.
     */
    private fun generateSequenceId(prefix: String): String {
        return "${prefix}_${sequenceCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    }

    /**
     * Checks if a sequence is still active (not cancelled).
     */
    private fun isSequenceActive(sequenceId: String): Boolean {
        // For now, we assume sequences are active unless explicitly cancelled
        // This could be enhanced with a proper tracking mechanism
        return true // Simplified - assume sequences are active
    }

    /**
     * Cleans up resources for a sequence.
     */
    private fun cleanupSequence(sequenceId: String) {
        // Remove any pending actions related to this sequence
        pendingActions.keys.removeAll { key ->
            key.startsWith(sequenceId)
        }
    }

    /**
     * Creates a default timed gesture handler that integrates with GestureStateManager.
     *
     * @param onReady Optional callback when gesture is ready
     * @param onComplete Optional callback when sequence completes
     * @return Default TimedGestureHandler implementation
     */
    fun createDefaultHandler(
        onReady: ((GestureType, String) -> Unit)? = null,
        onComplete: ((String) -> Unit)? = null
    ): TimedGestureHandler {
        return object : TimedGestureHandler {
            override fun onGestureReady(gestureType: GestureType, sequenceId: String) {
                GestureStateManager.notifyGestureDispatchStarted(gestureType)
                onReady?.invoke(gestureType, sequenceId)
            }

            override fun onVisualUpdate(
                gestureType: GestureType,
                sequenceId: String,
                stage: String
            ) {
                // Notify state manager of visual stage changes
                GestureStateManager.setActiveVisualFeedback(true)
            }

            override fun onSequenceComplete(sequenceId: String) {
                GestureStateManager.setActiveVisualFeedback(false)
                onComplete?.invoke(sequenceId)
            }

            override fun onSequenceCancelled(sequenceId: String) {
                GestureStateManager.setActiveVisualFeedback(false)
            }
        }
    }

    /**
     * Releases all resources and cancels pending operations.
     */
    fun cleanup() {
        cancelAll()
    }
}