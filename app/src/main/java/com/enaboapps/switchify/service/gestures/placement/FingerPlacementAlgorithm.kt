package com.enaboapps.switchify.service.gestures.placement

import android.graphics.PointF
import android.graphics.Rect
import com.enaboapps.switchify.service.gestures.data.GestureType
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Core algorithm for dynamic finger placement calculation at method execution time.
 *
 * This class implements the extensible method-level finger placement approach where
 * each gesture method calls the algorithm to determine optimal finger count and
 * positioning in real-time. The algorithm considers user preference, screen context,
 * gesture type, and available space to make intelligent placement decisions.
 *
 * Key Design Principles:
 * - Method-level execution: Called by each gesture method (performTap, performSwipe, etc.)
 * - Extensible finger counts: Supports 1, 2, N fingers without code changes
 * - Context-aware: Adapts to screen space, gesture type, and UI constraints
 * - Performance-optimized: <5ms execution time for real-time gesture processing
 * - Future-ready: Algorithm structure supports machine learning enhancements
 *
 * Architecture Integration:
 * - Called by GestureManager methods for each gesture execution
 * - Results consumed by GesturePathBuilder for multi-finger path creation
 * - Coordinates with GestureVisualManager for placement preview feedback
 * - Integrates with PreferenceManager for user mode persistence
 *
 * Algorithm Strategy:
 * 1. User Preference Analysis - Respects explicit finger mode selection (1, 2, AUTO)
 * 2. Screen Space Assessment - Calculates available space around target point
 * 3. Gesture Type Optimization - Applies gesture-specific finger count preferences
 * 4. Placement Calculation - Determines optimal finger positions and spacing
 * 5. Validation & Fallback - Ensures placement validity with fallback strategies
 */
class FingerPlacementAlgorithm {

    companion object {
        private const val TAG = "FingerPlacementAlgorithm"
        
        // Spacing constants for finger placement (in pixels)
        const val MIN_FINGER_SPACING = 48f  // Minimum distance between finger centers
        const val IDEAL_FINGER_SPACING = 64f // Ideal distance for comfortable use
        const val MAX_FINGER_SPACING = 96f   // Maximum spacing before gesture feels disconnected
        
        // Screen margin constants (pixels from screen edges)
        const val EDGE_MARGIN = 32f         // Minimum margin from screen edges
        const val SAFE_MARGIN = 48f         // Preferred margin for comfortable placement
        
        // Space requirements for different finger counts
        const val MIN_TWO_FINGER_SPACE = 120  // Minimum space needed for 2 fingers
        const val IDEAL_TWO_FINGER_SPACE = 160 // Ideal space for comfortable 2-finger gestures
        const val MIN_THREE_FINGER_SPACE = 200 // Future: minimum space for 3 fingers
        
        // Performance timing thresholds
        const val MAX_CALCULATION_TIME_MS = 5L // Maximum allowed calculation time
    }

    /**
     * Calculates optimal finger placement for a gesture at method execution time.
     *
     * This is the core method-level algorithm that determines finger count and positioning
     * dynamically for each gesture execution. The algorithm runs quickly (<5ms) to
     * support real-time gesture processing.
     *
     * @param gestureType The type of gesture being performed
     * @param targetPoint The primary target point for the gesture
     * @param userFingerMode User's finger mode preference (1, 2, AUTO)
     * @param screenBounds Current screen bounds for boundary calculations
     * @param gestureContext Additional context information (future extension)
     * @return FingerPlacement result with finger positions and metadata
     */
    fun calculateFingerPlacement(
        gestureType: GestureType,
        targetPoint: PointF,
        userFingerMode: FingerMode,
        screenBounds: Rect,
        gestureContext: GestureContext? = null
    ): FingerPlacement {
        val startTime = System.currentTimeMillis()
        
        try {
            // Debug logging (replace with proper logging in production)
            // println("Calculating placement: mode=$userFingerMode, type=$gestureType, point=(${targetPoint.x.toInt()}, ${targetPoint.y.toInt()})")
            
            // Step 1: Determine optimal finger count based on user preference and context
            val optimalFingerCount = determineOptimalFingerCount(
                gestureType, userFingerMode, targetPoint, screenBounds
            )
            
            // Step 2: Calculate available screen space around target
            val availableSpace = calculateAvailableSpace(targetPoint, screenBounds)
            
            // Step 3: Generate finger placement based on determined count
            val placement = when (optimalFingerCount) {
                1 -> createSingleFingerPlacement(
                    targetPoint, userFingerMode, gestureType, availableSpace, screenBounds
                )
                2 -> createTwoFingerPlacement(
                    targetPoint, userFingerMode, gestureType, availableSpace, screenBounds
                )
                else -> createMultiFingerPlacement(
                    targetPoint, optimalFingerCount, userFingerMode, gestureType, 
                    availableSpace, screenBounds
                )
            }
            
            val calculationTime = System.currentTimeMillis() - startTime
            
            // Update placement metadata with actual calculation time
            val updatedPlacement = when (placement) {
                is SingleFingerPlacement -> placement.copy(
                    metadata = placement.metadata.copy(calculationTimeMs = calculationTime)
                )
                is TwoFingerPlacement -> placement.copy(
                    metadata = placement.metadata.copy(calculationTimeMs = calculationTime)
                )
                is MultiFingerPlacement -> placement.copy(
                    metadata = placement.metadata.copy(calculationTimeMs = calculationTime)
                )
            }
            
            // Debug logging (replace with proper logging in production)
            // println("Placement calculated in ${calculationTime}ms: ${updatedPlacement.getDescription()}")
            
            return updatedPlacement
            
        } catch (e: Exception) {
            val calculationTime = System.currentTimeMillis() - startTime
            // Error logging (replace with proper logging in production)
            // println("Error calculating finger placement: ${e.message}")
            
            // Fallback to single finger placement on error
            return createFallbackPlacement(
                targetPoint, userFingerMode, gestureType, screenBounds, calculationTime, e
            )
        }
    }

    /**
     * Determines the optimal finger count based on user preference, gesture type, and context.
     */
    private fun determineOptimalFingerCount(
        gestureType: GestureType,
        userMode: FingerMode,
        targetPoint: PointF,
        screenBounds: Rect
    ): Int {
        return when (userMode) {
            FingerMode.ONE -> 1
            FingerMode.TWO -> if (canFitTwoFingers(targetPoint, screenBounds)) 2 else 1
            FingerMode.AUTO -> algorithmicFingerCountDecision(gestureType, targetPoint, screenBounds)
        }
    }

    /**
     * Algorithmic decision for optimal finger count in AUTO mode.
     * Considers gesture type, available space, and optimization factors.
     */
    private fun algorithmicFingerCountDecision(
        gestureType: GestureType,
        targetPoint: PointF,
        screenBounds: Rect
    ): Int {
        val availableSpace = calculateAvailableSpace(targetPoint, screenBounds)
        
        // Gesture-specific optimizations
        val gesturePreference = getGestureTypeFingerPreference(gestureType)
        
        return when {
            // Insufficient space for multiple fingers
            availableSpace < MIN_TWO_FINGER_SPACE -> 1
            
            // Gesture type specifically benefits from multiple fingers
            gesturePreference > 1 && availableSpace >= IDEAL_TWO_FINGER_SPACE -> 2
            
            // Precision gestures prefer single finger
            isPrecisionGesture(gestureType) -> 1
            
            // Stability gestures benefit from two fingers if space allows
            isStabilityGesture(gestureType) && availableSpace >= MIN_TWO_FINGER_SPACE -> 2
            
            // Default to single finger for unknown cases
            else -> 1
        }
    }

    /**
     * Checks if two fingers can physically fit around the target point.
     */
    private fun canFitTwoFingers(targetPoint: PointF, screenBounds: Rect): Boolean {
        val availableSpace = calculateAvailableSpace(targetPoint, screenBounds)
        return availableSpace >= MIN_TWO_FINGER_SPACE
    }

    /**
     * Calculates available space around the target point in all directions.
     * Returns the limiting dimension (smallest available space).
     */
    private fun calculateAvailableSpace(targetPoint: PointF, screenBounds: Rect): Int {
        val leftSpace = (targetPoint.x - screenBounds.left - SAFE_MARGIN).toInt()
        val rightSpace = (screenBounds.right - targetPoint.x - SAFE_MARGIN).toInt()
        val topSpace = (targetPoint.y - screenBounds.top - SAFE_MARGIN).toInt()
        val bottomSpace = (screenBounds.bottom - targetPoint.y - SAFE_MARGIN).toInt()
        
        val horizontalSpace = leftSpace + rightSpace
        val verticalSpace = topSpace + bottomSpace
        
        return min(horizontalSpace, verticalSpace)
    }

    /**
     * Creates single-finger placement (traditional mode).
     */
    private fun createSingleFingerPlacement(
        targetPoint: PointF,
        userMode: FingerMode,
        gestureType: GestureType,
        availableSpace: Int,
        screenBounds: Rect
    ): SingleFingerPlacement {
        val decisionFactors = mutableListOf<DecisionFactor>()
        
        if (userMode == FingerMode.ONE) {
            decisionFactors.add(DecisionFactor.USER_PREFERENCE)
        } else if (availableSpace < MIN_TWO_FINGER_SPACE) {
            decisionFactors.add(DecisionFactor.SPACE_CONSTRAINT)
        }
        
        if (isPrecisionGesture(gestureType)) {
            decisionFactors.add(DecisionFactor.GESTURE_OPTIMIZATION)
        }
        
        val metadata = PlacementMetadata(
            requestedMode = userMode,
            gestureType = gestureType,
            availableSpace = availableSpace,
            decisionFactors = decisionFactors
        )
        
        return SingleFingerPlacement(targetPoint, metadata, screenBounds)
    }

    /**
     * Creates two-finger placement with optimal spacing and strategy.
     */
    private fun createTwoFingerPlacement(
        targetPoint: PointF,
        userMode: FingerMode,
        gestureType: GestureType,
        availableSpace: Int,
        screenBounds: Rect
    ): TwoFingerPlacement {
        val strategy = determineTwoFingerStrategy(targetPoint, availableSpace, screenBounds)
        val spacing = calculateOptimalSpacing(availableSpace, strategy)
        val secondaryPoint = calculateSecondaryFingerPosition(
            targetPoint, spacing, strategy, screenBounds
        )
        
        val decisionFactors = mutableListOf<DecisionFactor>()
        if (userMode == FingerMode.TWO) {
            decisionFactors.add(DecisionFactor.USER_PREFERENCE)
        }
        if (isStabilityGesture(gestureType)) {
            decisionFactors.add(DecisionFactor.GESTURE_OPTIMIZATION)
        }
        if (isNearScreenEdge(targetPoint, screenBounds)) {
            decisionFactors.add(DecisionFactor.EDGE_PROXIMITY)
        }
        
        val metadata = PlacementMetadata(
            requestedMode = userMode,
            gestureType = gestureType,
            availableSpace = availableSpace,
            decisionFactors = decisionFactors
        )
        
        return TwoFingerPlacement(
            primaryPoint = targetPoint,
            secondaryPoint = secondaryPoint,
            spacing = spacing,
            placementStrategy = strategy,
            metadata = metadata,
            screenBounds = screenBounds
        )
    }

    /**
     * Creates multi-finger placement for 3+ fingers (future extension).
     */
    private fun createMultiFingerPlacement(
        targetPoint: PointF,
        fingerCount: Int,
        userMode: FingerMode,
        gestureType: GestureType,
        availableSpace: Int,
        screenBounds: Rect
    ): MultiFingerPlacement {
        // Future implementation for 3+ finger gestures
        val pattern = PlacementPattern.LINEAR
        val fingerPoints = generateMultiFingerPositions(
            targetPoint, fingerCount, pattern, availableSpace, screenBounds
        )
        
        val metadata = PlacementMetadata(
            requestedMode = userMode,
            gestureType = gestureType,
            availableSpace = availableSpace,
            decisionFactors = listOf(DecisionFactor.USER_PREFERENCE)
        )
        
        return MultiFingerPlacement(
            primaryPoint = targetPoint,
            fingerPoints = fingerPoints,
            placementPattern = pattern,
            metadata = metadata,
            screenBounds = screenBounds
        )
    }

    /**
     * Determines the best two-finger placement strategy based on available space.
     */
    private fun determineTwoFingerStrategy(
        targetPoint: PointF,
        availableSpace: Int,
        screenBounds: Rect
    ): TwoFingerStrategy {
        val leftSpace = targetPoint.x - screenBounds.left
        val rightSpace = screenBounds.right - targetPoint.x
        val topSpace = targetPoint.y - screenBounds.top
        val bottomSpace = screenBounds.bottom - targetPoint.y
        
        val horizontalSpace = leftSpace + rightSpace
        val verticalSpace = topSpace + bottomSpace
        
        return when {
            horizontalSpace >= verticalSpace * 1.2f -> TwoFingerStrategy.HORIZONTAL
            verticalSpace >= horizontalSpace * 1.2f -> TwoFingerStrategy.VERTICAL
            else -> TwoFingerStrategy.ADAPTIVE
        }
    }

    /**
     * Calculates optimal spacing between fingers based on available space and strategy.
     */
    private fun calculateOptimalSpacing(
        availableSpace: Int,
        strategy: TwoFingerStrategy
    ): Float {
        val maxAllowedSpacing = min(availableSpace / 2f, MAX_FINGER_SPACING)
        val minRequiredSpacing = MIN_FINGER_SPACING
        
        return when (strategy) {
            TwoFingerStrategy.HORIZONTAL, TwoFingerStrategy.VERTICAL -> {
                max(minRequiredSpacing, min(IDEAL_FINGER_SPACING, maxAllowedSpacing))
            }
            TwoFingerStrategy.DIAGONAL -> {
                // Diagonal spacing can be slightly tighter
                max(minRequiredSpacing, min(IDEAL_FINGER_SPACING * 0.8f, maxAllowedSpacing))
            }
            TwoFingerStrategy.ADAPTIVE -> {
                max(minRequiredSpacing, min(IDEAL_FINGER_SPACING, maxAllowedSpacing))
            }
        }
    }

    /**
     * Calculates the secondary finger position based on strategy and spacing.
     */
    private fun calculateSecondaryFingerPosition(
        primaryPoint: PointF,
        spacing: Float,
        strategy: TwoFingerStrategy,
        screenBounds: Rect
    ): PointF {
        val halfSpacing = spacing / 2f
        
        return when (strategy) {
            TwoFingerStrategy.HORIZONTAL -> {
                // Place fingers horizontally side-by-side
                val leftPoint = PointF(primaryPoint.x - halfSpacing, primaryPoint.y)
                val rightPoint = PointF(primaryPoint.x + halfSpacing, primaryPoint.y)
                
                // Choose the secondary position that stays within bounds
                if (leftPoint.x >= screenBounds.left + EDGE_MARGIN &&
                    rightPoint.x <= screenBounds.right - EDGE_MARGIN) {
                    rightPoint // Both fit, use right as secondary
                } else if (leftPoint.x >= screenBounds.left + EDGE_MARGIN) {
                    leftPoint
                } else {
                    rightPoint
                }
            }
            
            TwoFingerStrategy.VERTICAL -> {
                // Place fingers vertically above/below each other
                val topPoint = PointF(primaryPoint.x, primaryPoint.y - halfSpacing)
                val bottomPoint = PointF(primaryPoint.x, primaryPoint.y + halfSpacing)
                
                if (topPoint.y >= screenBounds.top + EDGE_MARGIN &&
                    bottomPoint.y <= screenBounds.bottom - EDGE_MARGIN) {
                    bottomPoint // Both fit, use bottom as secondary
                } else if (topPoint.y >= screenBounds.top + EDGE_MARGIN) {
                    topPoint
                } else {
                    bottomPoint
                }
            }
            
            TwoFingerStrategy.DIAGONAL -> {
                // Place fingers diagonally
                val diagonalOffset = halfSpacing / sqrt(2f)
                PointF(
                    primaryPoint.x + diagonalOffset,
                    primaryPoint.y + diagonalOffset
                )
            }
            
            TwoFingerStrategy.ADAPTIVE -> {
                // Use the strategy that fits best in available space
                val horizontalFits = (primaryPoint.x - halfSpacing) >= screenBounds.left + EDGE_MARGIN &&
                                   (primaryPoint.x + halfSpacing) <= screenBounds.right - EDGE_MARGIN
                
                if (horizontalFits) {
                    PointF(primaryPoint.x + halfSpacing, primaryPoint.y)
                } else {
                    PointF(primaryPoint.x, primaryPoint.y + halfSpacing)
                }
            }
        }
    }

    /**
     * Generates finger positions for multi-finger placement (3+ fingers).
     */
    private fun generateMultiFingerPositions(
        centerPoint: PointF,
        fingerCount: Int,
        pattern: PlacementPattern,
        availableSpace: Int,
        screenBounds: Rect
    ): List<PointF> {
        // Future implementation for 3+ finger patterns
        // For now, return linear arrangement
        val positions = mutableListOf<PointF>()
        val spacing = min(availableSpace / fingerCount.toFloat(), IDEAL_FINGER_SPACING)
        
        for (i in 0 until fingerCount) {
            val offset = (i - fingerCount / 2f) * spacing
            positions.add(PointF(centerPoint.x + offset, centerPoint.y))
        }
        
        return positions
    }

    /**
     * Creates fallback placement when algorithm encounters errors.
     */
    private fun createFallbackPlacement(
        targetPoint: PointF,
        userMode: FingerMode,
        gestureType: GestureType,
        screenBounds: Rect,
        calculationTime: Long,
        error: Exception
    ): SingleFingerPlacement {
        val metadata = PlacementMetadata(
            requestedMode = userMode,
            gestureType = gestureType,
            availableSpace = 0,
            decisionFactors = listOf(DecisionFactor.FALLBACK_APPLIED),
            calculationTimeMs = calculationTime,
            warnings = listOf("Algorithm fallback due to error: ${error.message}")
        )
        
        return SingleFingerPlacement(targetPoint, metadata, screenBounds)
    }

    // Utility methods for gesture type classification

    private fun getGestureTypeFingerPreference(gestureType: GestureType): Int {
        return when (gestureType) {
            GestureType.TAP, GestureType.DOUBLE_TAP -> 1  // Precision gestures prefer 1 finger
            GestureType.TAP_AND_HOLD -> 2                  // Hold gestures benefit from stability
            GestureType.DRAG, GestureType.HOLD_AND_DRAG -> 2  // Drag gestures benefit from stability
            else -> 1  // Default to single finger
        }
    }

    private fun isPrecisionGesture(gestureType: GestureType): Boolean {
        return when (gestureType) {
            GestureType.TAP, GestureType.DOUBLE_TAP -> true
            else -> false
        }
    }

    private fun isStabilityGesture(gestureType: GestureType): Boolean {
        return when (gestureType) {
            GestureType.TAP_AND_HOLD, 
            GestureType.DRAG, 
            GestureType.HOLD_AND_DRAG -> true
            else -> false
        }
    }

    private fun isNearScreenEdge(point: PointF, screenBounds: Rect): Boolean {
        return point.x <= screenBounds.left + SAFE_MARGIN ||
               point.x >= screenBounds.right - SAFE_MARGIN ||
               point.y <= screenBounds.top + SAFE_MARGIN ||
               point.y >= screenBounds.bottom - SAFE_MARGIN
    }
}

/**
 * Context information for gesture placement calculations (future extension).
 * Provides additional context that may influence finger placement decisions.
 */
data class GestureContext(
    val appContext: String? = null,
    val uiElementType: String? = null,
    val userPerformanceHistory: Map<String, Any>? = null,
    val environmentalFactors: Map<String, Any>? = null
)