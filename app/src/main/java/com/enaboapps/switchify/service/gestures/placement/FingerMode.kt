package com.enaboapps.switchify.service.gestures.placement

import android.content.Context
import com.enaboapps.switchify.R

/**
 * Enumeration of natural finger placement modes for multi-touch gesture execution.
 *
 * This enum defines user preferences for biomechanically accurate finger positioning when
 * performing gestures. Each mode uses natural hand geometry and spacing patterns that mimic
 * real human finger placement, enabling proper multi-touch gesture recognition by Android
 * applications and improving accessibility for users with various motor abilities.
 *
 * ## Natural Finger Placement System
 *
 * All finger modes now use anatomically accurate positioning with:
 * - **Biomechanical spacing**: Based on real finger joint distances and hand anatomy
 * - **Natural hand curvature**: Fingers follow a subtle parabolic arc
 * - **Dynamic scaling**: Adapts to screen size while maintaining natural proportions
 * - **Gesture-context awareness**: Optimizes placement for different gesture types
 *
 * ## Multi-Touch Compatibility
 *
 * The natural placement system enables proper recognition by:
 * - **Google Maps**: Pinch-to-zoom, rotation gestures
 * - **Photo galleries**: Multi-finger navigation and zoom
 * - **Web browsers**: Multi-touch scrolling and pinch-zoom
 * - **Touch games**: Complex multi-finger controls
 * - **System gestures**: Proper multi-finger swipe recognition
 *
 * ## Accessibility Benefits
 *
 * Each mode provides different levels of stability and support:
 * - **Precision users**: Single finger for accurate targeting
 * - **Stability needs**: Multi-finger modes reduce tremor impact
 * - **Motor impairments**: Natural patterns reduce cognitive load
 * - **Grip assistance**: Whole-hand patterns support limited dexterity
 *
 * ## Technical Implementation
 *
 * Finger positioning uses anatomical constants:
 * - `NATURAL_INDEX_TO_MIDDLE`: 60px (ergonomic finger spacing)
 * - `NATURAL_MIDDLE_TO_RING`: 50px (natural hand geometry)
 * - `NATURAL_RING_TO_PINKY`: 45px (pinky offset)
 * - `NATURAL_THUMB_OFFSET`: 80px horizontal, 60px vertical
 * - `HAND_CURVATURE_FACTOR`: Parabolic arc simulation
 *
 * ## Integration Points
 *
 * - **FingerPlacementAlgorithm**: Calculates natural finger positions for each mode
 * - **GesturePathBuilder**: Creates multi-finger gesture paths with proper coordination
 * - **GestureVisualManager**: Shows visual feedback for all finger positions
 * - **PreferenceManager**: Persists user's selected finger mode preference
 * - **GestureManager**: Applies finger mode to all gesture types
 */
enum class FingerMode {
    /**
     * Single-finger gesture mode with precision targeting.
     *
     * Uses traditional single-point touch interaction for users who prefer maximum
     * accuracy and have good fine motor control. Compatible with all gesture types
     * and apps expecting standard single-touch input.
     *
     * **Benefits:**
     * - **Maximum precision**: Pinpoint accuracy for small targets
     * - **Universal compatibility**: Works with all apps and gesture types
     * - **Minimal screen space**: Small footprint, ideal for crowded interfaces
     * - **Familiar interaction**: Traditional touch paradigm
     * - **Low cognitive load**: Simple one-to-one mapping
     *
     * **Best for:** Users with good fine motor skills who prioritize precision over stability
     */
    ONE,

    /**
     * Two-finger natural placement mode with enhanced stability.
     *
     * Uses biomechanically accurate two-finger positioning with natural hand geometry.
     * Fingers are placed with anatomical spacing (60px between index and middle at 1.0x scaling)
     * and follow natural hand curvature patterns. Ideal for users needing stability
     * while maintaining compatibility with multi-touch applications.
     *
     * **Benefits:**
     * - **Enhanced stability**: Two contact points reduce tremor impact
     * - **Natural spacing**: 60px anatomical finger separation
     * - **Multi-touch compatibility**: Enables pinch-to-zoom and rotation gestures
     * - **Improved targeting**: Larger effective touch area
     * - **Visual feedback**: Clear two-finger gesture indicators
     * - **Tremor reduction**: Dual contact points average out movement variations
     *
     * **Multi-touch gestures enabled:**
     * - Pinch-to-zoom in Maps, Photos, Browsers
     * - Two-finger rotation and scrolling
     * - Enhanced gesture recognition by apps
     *
     * **Best for:** Users with mild tremors or motor challenges who benefit from stability
     * while still needing multi-touch app compatibility
     */
    TWO,

    /**
     * Three-finger natural tripod grip mode with superior stability.
     *
     * Uses biomechanically accurate three-finger positioning mimicking a natural tripod grip
     * with index, middle, and ring fingers. Features anatomical spacing (60px index-middle,
     * 50px middle-ring) and natural hand curvature. The middle finger is positioned slightly
     * forward (20px) to match natural finger length variation, creating an optimal tripod
     * formation for maximum stability and multi-touch gesture recognition.
     *
     * **Benefits:**
     * - **Natural tripod grip**: Index-middle-ring finger formation
     * - **Superior stability**: Three contact points eliminate most tremor impact
     * - **Anatomical accuracy**: Follows real hand geometry and finger lengths
     * - **Hand curvature**: Subtle parabolic arc for natural positioning
     * - **Large touch area**: Easier targeting for motor impairments
     * - **Multi-touch optimization**: Ideal for pinch-to-zoom gestures
     *
     * **Multi-touch gestures enabled:**
     * - Advanced pinch-to-zoom with tripod stability
     * - Three-finger rotation and complex gestures
     * - Enhanced app recognition of natural finger patterns
     * - Better gesture success rates in Maps, Photos, Games
     *
     * **Best for:** Users with moderate to severe tremors or motor impairments who need
     * maximum stability while maintaining natural multi-touch gesture compatibility
     */
    THREE,

    /**
     * Four-finger natural swipe pattern mode with extreme stability.
     *
     * Uses biomechanically accurate four-finger positioning with index, middle, ring, and pinky
     * in natural sequence. Features anatomical spacing ratios (60px index-middle, 50px middle-ring,
     * 45px ring-pinky) and natural hand curvature with finger length variations. The pinky is
     * positioned slightly back (1.2x length variation) to match natural finger proportions,
     * creating an optimal four-finger swipe formation.
     *
     * **Benefits:**
     * - **Natural four-finger sequence**: Index through pinky in anatomical order
     * - **Extreme stability**: Four contact points virtually eliminate tremors
     * - **Proportional spacing**: Decreasing gaps match natural finger spacing
     * - **Finger length accuracy**: Pinky positioned for shorter natural length
     * - **Maximum touch area**: Ideal for users with significant motor challenges
     * - **Swipe optimization**: Perfect for scrolling and navigation gestures
     *
     * **Multi-touch gestures enabled:**
     * - Four-finger swipe navigation (system and app gestures)
     * - Enhanced scrolling with natural finger patterns
     * - Complex multi-touch interactions in games and apps
     * - Better recognition by apps expecting human-like finger placement
     *
     * **Best for:** Users with significant motor challenges, limited finger control, or
     * severe tremors who benefit from whole-hand stability and natural finger positioning
     */
    FOUR,

    /**
     * Five-finger whole-hand placement mode with ultimate accessibility.
     *
     * Uses biomechanically accurate five-finger positioning with natural thumb placement plus
     * four fingers (index through pinky). Features realistic thumb offset (80px horizontal,
     * 60px vertical from index finger) and anatomical finger spacing with natural hand curvature.
     * The thumb remains uncurved while the four fingers follow a parabolic arc, creating the
     * most natural whole-hand gesture pattern possible.
     *
     * **Benefits:**
     * - **Whole-hand biomechanical accuracy**: Realistic thumb + four-finger placement
     * - **Ultimate stability**: Five contact points provide maximum tremor cancellation
     * - **Natural thumb positioning**: 80px/60px offset from index finger (anatomically correct)
     * - **Largest touch area**: Maximum coverage for severe motor impairments
     * - **Optimal for complex gestures**: Supports advanced multi-touch interactions
     * - **Natural hand curvature**: Four fingers curved, thumb in natural position
     *
     * **Multi-touch gestures enabled:**
     * - Advanced pinch-to-zoom with thumb opposition
     * - Five-finger rotation and complex manipulations
     * - Whole-hand gestures in gaming and creative apps
     * - Full-screen multi-touch interactions
     * - Maximum app compatibility with human-like finger patterns
     *
     * **Technical accuracy:**
     * - Thumb offset: 80px horizontal, 60px vertical from index
     * - Natural finger sequence with decreasing spacing
     * - Hand curvature applied to four fingers only
     * - Dynamic scaling maintains proportions across screen sizes
     *
     * **Best for:** Users with severe motor impairments, limited dexterity, or those who
     * can only perform whole-hand movements and need maximum stability with natural positioning
     */
    FIVE;

    /**
     * Returns the display name for the finger mode.
     * Used in UI components and preference screens.
     */
    fun getDisplayName(context: Context): String {
        return when (this) {
            ONE -> context.getString(R.string.menu_item_one_finger)
            TWO -> context.getString(R.string.menu_item_two_fingers)
            THREE -> context.getString(R.string.menu_item_three_fingers)
            FOUR -> context.getString(R.string.menu_item_four_fingers)
            FIVE -> context.getString(R.string.menu_item_five_fingers)
        }
    }

    /**
     * Returns a description of the finger mode for help text.
     * Used in settings screens and accessibility descriptions.
     */
    fun getDescription(context: Context): String {
        return when (this) {
            ONE -> context.getString(R.string.finger_mode_description_one)
            TWO -> context.getString(R.string.finger_mode_description_two)
            THREE -> context.getString(R.string.finger_mode_description_three)
            FOUR -> context.getString(R.string.finger_mode_description_four)
            FIVE -> context.getString(R.string.finger_mode_description_five)
        }
    }

    /**
     * Converts string preference values back to enum values.
     * Used by PreferenceManager for persistence and retrieval.
     *
     * @param value String representation of finger mode (case-insensitive)
     * @return Corresponding FingerMode enum, or ONE if invalid value provided
     */
    companion object {
        fun fromString(value: String): FingerMode {
            return try {
                valueOf(value.uppercase())
            } catch (e: IllegalArgumentException) {
                // Log the invalid value for debugging, but continue with default
                android.util.Log.w(
                    "FingerMode",
                    "Unknown finger mode value: $value, using default ONE",
                    e
                )
                // Default to ONE finger for unknown values - provides universal compatibility
                ONE
            }
        }

        /**
         * Returns the default finger mode for new users.
         *
         * ONE is chosen as the default because:
         * - Provides maximum precision for all users
         * - Universal compatibility with all apps and gesture types
         * - Familiar interaction model for most users
         * - Users can upgrade to multi-finger modes as needed for stability
         *
         * @return ONE finger mode as the default
         */
        fun getDefault(): FingerMode = ONE

        /**
         * Returns all available finger modes for UI selection.
         *
         * All modes use natural finger placement algorithms with:
         * - Biomechanically accurate spacing based on hand anatomy
         * - Natural hand curvature simulation
         * - Dynamic scaling for different screen sizes
         * - Multi-touch app compatibility
         *
         * @return Complete list of all finger modes (ONE through FIVE)
         */
        fun getAllModes(): List<FingerMode> = values().toList()
    }
}