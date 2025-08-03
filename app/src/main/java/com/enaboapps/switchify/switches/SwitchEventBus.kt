package com.enaboapps.switchify.switches

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for same-process switch event communication.
 * Replaces LocalBroadcastManager for UI layer event propagation.
 * 
 * This handles communication within the main app process between:
 * - SwitchEventStore and ViewModels
 * - Settings screens and their models
 * - UI components that need switch data updates
 */
object SwitchEventBus {
    
    private val _switchEventsUpdated = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1
    )
    
    /**
     * Flow that emits when switch events are updated.
     * Replaces the EVENTS_UPDATED LocalBroadcast.
     */
    val switchEventsUpdated: SharedFlow<Unit> = _switchEventsUpdated.asSharedFlow()
    
    /**
     * Notify that switch events have been updated.
     * This replaces LocalBroadcastManager.sendBroadcast(EVENTS_UPDATED).
     */
    fun notifySwitchEventsUpdated() {
        _switchEventsUpdated.tryEmit(Unit)
    }
}