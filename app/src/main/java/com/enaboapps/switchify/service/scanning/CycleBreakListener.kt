package com.enaboapps.switchify.service.scanning

/**
 * Listener interface for scan tree cycle break events.
 *
 * This interface decouples scanners from keyboard management logic by providing
 * a callback mechanism for cycle break events. Components that need to respond
 * to cycle breaks (like KeyboardManager) can implement this interface instead
 * of being directly called by the scanner.
 *
 * Benefits:
 * - Single Responsibility: Scanner only handles scanning, not keyboard logic
 * - Testability: Can test scanner without keyboard dependencies
 * - Flexibility: Multiple components can respond to cycle break events
 * - Decoupling: Scanner doesn't depend on KeyboardManager
 * - Clarity: Event flow is explicit through the interface
 */
interface CycleBreakListener {
    /**
     * Called when a cycle break is selected by the user.
     *
     * This callback is invoked when the user selects the cycle break option
     * during scanning. The implementing component can then decide how to
     * handle this event (e.g., escape from keyboard, show menu, etc.).
     */
    fun onCycleBreak()
}
