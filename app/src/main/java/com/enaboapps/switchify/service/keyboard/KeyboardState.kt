package com.enaboapps.switchify.service.keyboard

/**
 * Represents the current state of keyboard scanning.
 *
 * @property isVisible Whether the keyboard is currently visible
 * @property isEscaped Whether the user has escaped from keyboard scanning
 * @property isDirectSelectEnabled Whether direct selection of keyboard keys is enabled in settings
 */
data class KeyboardState(
    val isVisible: Boolean = false,
    val isEscaped: Boolean = false,
    val isDirectSelectEnabled: Boolean = false
)
