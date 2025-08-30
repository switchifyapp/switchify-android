package com.enaboapps.switchify.service.core

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Unified communication bridge between the main app and accessibility service.
 * Replaces SwitchEventBus and provides bidirectional communication.
 * 
 * This handles:
 * - App UI requesting service actions (commands)
 * - Service notifying app of state changes (events) 
 * - Switch event updates (replaces SwitchEventBus)
 * - Settings change notifications
 */
object ServiceBridge {
    
    private val _serviceCommands = MutableSharedFlow<ServiceCommand>(
        replay = 0,
        extraBufferCapacity = 10
    )
    
    private val _serviceEvents = MutableSharedFlow<ServiceEvent>(
        replay = 0, 
        extraBufferCapacity = 10
    )
    
    /**
     * Flow that emits service commands for the accessibility service to handle.
     */
    val serviceCommands: SharedFlow<ServiceCommand> = _serviceCommands.asSharedFlow()
    
    /**
     * Flow that emits service events for the app UI to handle.
     * Replaces SwitchEventBus.switchEventsUpdated and adds more event types.
     */
    val serviceEvents: SharedFlow<ServiceEvent> = _serviceEvents.asSharedFlow()
    
    /**
     * Send a command to the accessibility service.
     * Used by UI components to trigger service actions.
     */
    fun sendCommand(command: ServiceCommand) {
        _serviceCommands.tryEmit(command)
    }
    
    /**
     * Emit a service event.
     * Used by the accessibility service to notify the app of changes.
     */
    fun emitEvent(event: ServiceEvent) {
        _serviceEvents.tryEmit(event)
    }
    
    /**
     * Commands that can be sent from app to service.
     */
    sealed class ServiceCommand {
        /**
         * Request service to enforce technique compatibility based on current scan mode.
         */
        object EnforceTechniqueCompatibility : ServiceCommand()
        
        /**
         * Request service to reload all settings from preferences.
         */
        object ReloadSettings : ServiceCommand()
        
        /**
         * Request service to clear internal caches.
         */
        object ClearCache : ServiceCommand()
        
        /**
         * Request service to update switch configuration.
         */
        object UpdateSwitches : ServiceCommand()
        
        /**
         * Request service to validate and update configuration.
         * @param key The preference key that changed
         * @param value The new value (for validation purposes)
         */
        data class UpdateConfiguration(val key: String, val value: Any?) : ServiceCommand()
    }
    
    /**
     * Events that can be emitted from service to app.
     */
    sealed class ServiceEvent {
        /**
         * Service has updated technique compatibility.
         * @param newTechnique The technique that was set after enforcement
         */
        data class TechniqueEnforced(val newTechnique: String) : ServiceEvent()
        
        /**
         * Service configuration has been updated.
         */
        object ConfigurationUpdated : ServiceEvent()
        
        /**
         * Switch events have been updated.
         * Replaces SwitchEventBus.switchEventsUpdated.
         */
        object SwitchEventsUpdated : ServiceEvent()
        
        /**
         * Service is ready and fully initialized.
         */
        object ServiceReady : ServiceEvent()
        
        /**
         * Service encountered an error.
         * @param error Brief error description for logging/debugging
         */
        data class ServiceError(val error: String) : ServiceEvent()
    }
}