package com.enaboapps.switchify.service.gestures.placement

import android.graphics.PointF
import android.graphics.Rect

/**
 * Data classes representing finger placement results from the FingerPlacementAlgorithm.
 *
 * These classes encapsulate the algorithm's decisions about finger count, positioning,
 * and spacing for gesture execution. The extensible design supports any number of
 * fingers without requiring modifications to consuming code.
 *
 * Design Principles:
 * - Immutable data structures for thread safety
 * - Extensible to N fingers without code changes  
 * - Rich metadata for debugging and optimization
 * - Clear separation between algorithm results and execution logic
 *
 * Architecture Integration:
 * - Created by FingerPlacementAlgorithm.calculateFingerPlacement()
 * - Consumed by GesturePathBuilder for creating multi-finger gesture paths
 * - Used by GestureVisualManager for showing finger placement previews
 * - Passed to GestureDispatcher for execution coordination
 */

/**
 * Base interface for all finger placement results.
 * Provides common properties and methods for all placement types.
 */
sealed interface FingerPlacement {
    /** Number of fingers determined by the algorithm */
    val fingerCount: Int
    
    /** Primary target point (usually the original gesture target) */
    val primaryPoint: PointF
    
    /** All finger positions for this gesture */
    val fingerPoints: List<PointF>
    
    /** Metadata about the placement decision for debugging and optimization */
    val metadata: PlacementMetadata
    
    /** Screen bounds considered during placement calculation */
    val screenBounds: Rect
    
    /**
     * Returns the spacing between fingers in pixels.
     * Used for validation and visual feedback calculations.
     */
    fun getFingerSpacing(): Float
    
    /**
     * Validates that all finger positions are within screen bounds.
     * Returns true if placement is valid for execution.
     */
    fun isValidPlacement(): Boolean {
        return fingerPoints.all { point ->
            point.x >= screenBounds.left &&
            point.x < screenBounds.right &&
            point.y >= screenBounds.top &&
            point.y < screenBounds.bottom
        }
    }
    
    /**
     * Returns a human-readable description of the placement.
     * Used for logging and debugging placement decisions.
     */
    fun getDescription(): String
}

/**
 * Single-finger gesture placement (traditional mode).
 * Represents the simplest case where only one finger is used.
 */
data class SingleFingerPlacement(
    override val primaryPoint: PointF,
    override val metadata: PlacementMetadata,
    override val screenBounds: Rect
) : FingerPlacement {
    
    override val fingerCount: Int = 1
    override val fingerPoints: List<PointF> = listOf(primaryPoint)
    
    override fun getFingerSpacing(): Float = 0f
    
    override fun getDescription(): String {
        return "Single finger at (${primaryPoint.x.toInt()}, ${primaryPoint.y.toInt()})"
    }
}

/**
 * Two-finger gesture placement with calculated spacing and positioning.
 * Provides enhanced stability and accessibility for supported gesture types.
 */
data class TwoFingerPlacement(
    override val primaryPoint: PointF,
    val secondaryPoint: PointF,
    val spacing: Float,
    val placementStrategy: TwoFingerStrategy,
    override val metadata: PlacementMetadata,
    override val screenBounds: Rect
) : FingerPlacement {
    
    override val fingerCount: Int = 2
    override val fingerPoints: List<PointF> = listOf(primaryPoint, secondaryPoint)
    
    override fun getFingerSpacing(): Float = spacing
    
    override fun getDescription(): String {
        return "Two fingers: (${primaryPoint.x.toInt()}, ${primaryPoint.y.toInt()}) and " +
               "(${secondaryPoint.x.toInt()}, ${secondaryPoint.y.toInt()}) " +
               "with ${spacing.toInt()}px spacing using ${placementStrategy.name} strategy"
    }
}

/**
 * Multi-finger gesture placement for 3+ fingers (future extension).
 * Extensible design supports any number of fingers with flexible positioning patterns.
 */
data class MultiFingerPlacement(
    override val primaryPoint: PointF,
    override val fingerPoints: List<PointF>,
    val placementPattern: PlacementPattern,
    override val metadata: PlacementMetadata,
    override val screenBounds: Rect
) : FingerPlacement {
    
    override val fingerCount: Int = fingerPoints.size
    
    override fun getFingerSpacing(): Float {
        // Calculate average spacing between adjacent fingers
        return if (fingerPoints.size < 2) {
            0f
        } else {
            var totalSpacing = 0f
            for (i in 0 until fingerPoints.size - 1) {
                val point1 = fingerPoints[i]
                val point2 = fingerPoints[i + 1]
                val distance = kotlin.math.sqrt(
                    (point2.x - point1.x) * (point2.x - point1.x) + 
                    (point2.y - point1.y) * (point2.y - point1.y)
                )
                totalSpacing += distance
            }
            totalSpacing / (fingerPoints.size - 1)
        }
    }
    
    override fun getDescription(): String {
        return "$fingerCount fingers using ${placementPattern.name} pattern " +
               "with ${getFingerSpacing().toInt()}px average spacing"
    }
}

/**
 * Strategies for two-finger placement.
 * Defines different approaches to positioning two fingers relative to the target.
 */
enum class TwoFingerStrategy {
    /** Fingers placed horizontally side-by-side */
    HORIZONTAL,
    
    /** Fingers placed vertically above/below each other */
    VERTICAL,
    
    /** Fingers placed diagonally (useful for corner targets) */
    DIAGONAL,
    
    /** Adaptive placement based on available screen space */
    ADAPTIVE
}

/**
 * Patterns for multi-finger placement (3+ fingers).
 * Extensible design for future complex gesture patterns.
 */
enum class PlacementPattern {
    /** Fingers arranged in a line */
    LINEAR,
    
    /** Fingers arranged in a triangle */
    TRIANGLE,
    
    /** Fingers arranged in a circle around the target */
    CIRCULAR,
    
    /** Custom pattern based on specific requirements */
    CUSTOM
}

/**
 * Metadata about placement decisions for debugging and optimization.
 * Provides insight into the algorithm's decision-making process.
 */
data class PlacementMetadata(
    /** The finger mode that triggered this placement decision */
    val requestedMode: FingerMode,
    
    /** The gesture type being performed */
    val gestureType: com.enaboapps.switchify.service.gestures.data.GestureType,
    
    /** Available screen space around the target (in pixels) */
    val availableSpace: Int,
    
    /** Factors that influenced the placement decision */
    val decisionFactors: List<DecisionFactor>,
    
    /** Timestamp when placement was calculated */
    val calculatedAt: Long = System.currentTimeMillis(),
    
    /** Performance metrics for algorithm optimization */
    val calculationTimeMs: Long = 0,
    
    /** Any warnings or constraints that affected placement */
    val warnings: List<String> = emptyList()
) {
    /**
     * Returns a summary of the decision process for debugging.
     */
    fun getDecisionSummary(): String {
        val factors = decisionFactors.joinToString(", ") { it.name }
        val warningText = if (warnings.isNotEmpty()) " (Warnings: ${warnings.size})" else ""
        return "Mode: ${requestedMode.name}, Factors: [$factors], " +
               "Space: ${availableSpace}px, Time: ${calculationTimeMs}ms$warningText"
    }
}

/**
 * Factors that influence finger placement decisions.
 * Used for algorithm debugging and future machine learning enhancements.
 */
enum class DecisionFactor {
    /** User explicitly requested this finger count */
    USER_PREFERENCE,
    
    /** Insufficient space for more fingers */
    SPACE_CONSTRAINT,
    
    /** Gesture type optimization (some gestures work better with certain finger counts) */
    GESTURE_OPTIMIZATION,
    
    /** Screen edge proximity requiring adjusted placement */
    EDGE_PROXIMITY,
    
    /** UI element density affecting finger spacing */
    UI_DENSITY,
    
    /** Historical user success patterns (future enhancement) */
    USER_PATTERN_LEARNING,
    
    /** System performance considerations */
    PERFORMANCE_OPTIMIZATION,
    
    /** Algorithm fallback due to calculation failure */
    FALLBACK_APPLIED
}