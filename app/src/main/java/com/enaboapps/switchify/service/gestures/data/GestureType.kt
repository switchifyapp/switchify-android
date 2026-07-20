package com.enaboapps.switchify.service.gestures.data

/**
 * Comprehensive enumeration of all supported gesture types in the Switchify accessibility system.
 *
 * This enum serves as the central registry for gesture types, providing type safety and
 * consistency across the entire gesture execution pipeline. Each gesture type defines
 * a specific interaction pattern with its own execution behavior, timing characteristics,
 * and visual feedback requirements.
 *
 * Design Principles:
 * - Single source of truth for all gesture type definitions
 * - Type-safe gesture classification throughout the system
 * - Clear semantic separation between gesture categories
 * - Extensible design for future gesture types
 *
 * Gesture Categories:
 *
 * 1. Point Gestures (no movement):
 *    - TAP: Single touch at a point
 *    - DOUBLE_TAP: Two rapid taps at the same location
 *    - TAP_AND_HOLD: Extended touch for context menus
 *
 * 2. Linear Movement Gestures:
 *    - SWIPE_*: Quick directional movements for navigation
 *    - SCROLL_*: Content scrolling with appropriate distances
 *    - CUSTOM_SWIPE: User-defined direction and distance
 *
 * 3. Two-Phase Interactive Gestures:
 *    - DRAG: Direct movement with visual preview
 *    - HOLD_AND_DRAG: Hold-then-drag with enhanced feedback
 *
 * 4. Multi-Touch Gestures:
 *    - PINCH_IN/PINCH_OUT: Pinch-to-zoom functionality
 *
 * System Integration:
 * - GesturePathBuilder uses these types for path creation strategy selection
 * - GestureStateManager tracks active gesture type for state coordination
 * - GestureDispatcher uses type for error reporting and event broadcasting
 * - GestureData uses type for duration and timing parameter selection
 * - Pattern recording systems use type for gesture classification and learning
 */
enum class GestureType {
    // Point gestures - no movement, position-based
    TAP,                // Single touch activation
    DOUBLE_TAP,         // Double-tap for special actions
    TAP_AND_HOLD_0_5S,  // 0.5 second hold
    TAP_AND_HOLD_1S,    // 1 second hold
    TAP_AND_HOLD_2S,    // 2 second hold
    TAP_AND_HOLD_3S,    // 3 second hold
    TAP_AND_HOLD_5S,    // 5 second hold
    TAP_AND_HOLD_10S,   // 10 second hold

    // Directional swipes - quick navigation movements
    SWIPE_UP,           // Upward navigation gesture
    SWIPE_DOWN,         // Downward navigation gesture  
    SWIPE_LEFT,         // Left navigation gesture
    SWIPE_RIGHT,        // Right navigation gesture
    CUSTOM_SWIPE,       // User-defined swipe direction and distance

    // Interactive movement gestures
    DRAG,               // Direct drag movement with immediate execution
    HOLD_AND_DRAG,

    // Content scrolling - optimized for content navigation
    SCROLL_UP,          // Scroll content upward
    SCROLL_DOWN,        // Scroll content downward
    SCROLL_LEFT,        // Scroll content leftward
    SCROLL_RIGHT,       // Scroll content rightward

    // Multi-touch gestures - complex gesture coordination
    PINCH_IN,            // Pinch-to-zoom in
    PINCH_OUT;           // Pinch-to-zoom out

    /**
     * Checks if this gesture type is a scroll gesture.
     *
     * Scroll gestures are content navigation gestures that should always use
     * single-finger input for optimal accessibility and user experience.
     *
     * @return true if this is a scroll gesture, false otherwise
     */
    fun isScrollGesture(): Boolean {
        return when (this) {
            SCROLL_UP, SCROLL_DOWN, SCROLL_LEFT, SCROLL_RIGHT -> true
            else -> false
        }
    }
}
