package com.enaboapps.switchify.service.core

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner

/**
 * A shared lifecycle owner for the Switchify accessibility service and its components.
 * This provides a single source of truth for lifecycle management across the service.
 */
class SwitchifyLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this).apply {
        performRestore(null)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    /**
     * Handles a lifecycle event.
     * @param event The lifecycle event to handle
     */
    fun handleLifecycleEvent(event: Lifecycle.Event) {
        lifecycleRegistry.handleLifecycleEvent(event)
    }

    companion object {
        private var instance: SwitchifyLifecycleOwner? = null

        /**
         * Gets the singleton instance of the SwitchifyLifecycleOwner.
         * @return The SwitchifyLifecycleOwner instance
         */
        fun getInstance(): SwitchifyLifecycleOwner {
            return instance ?: SwitchifyLifecycleOwner().also { instance = it }
        }

        /**
         * Cleans up the instance.
         */
        fun cleanup() {
            instance = null
        }
    }
} 