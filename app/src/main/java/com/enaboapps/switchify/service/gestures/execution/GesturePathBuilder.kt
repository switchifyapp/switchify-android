package com.enaboapps.switchify.service.gestures.execution

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.placement.FingerPlacement
import com.enaboapps.switchify.service.gestures.placement.MultiFingerPlacement
import com.enaboapps.switchify.service.gestures.placement.SingleFingerPlacement
import com.enaboapps.switchify.service.gestures.placement.TwoFingerPlacement

/**
 * Central factory for creating standardized Android gesture paths with consistent timing and behavior.
 *
 * Architecture & Design:
 * This class implements the Factory pattern to encapsulate the complexity of Android's
 * GestureDescription creation while providing a clean, type-safe interface for the
 * entire gesture system. It serves as the single source of truth for gesture path
 * creation, ensuring consistent behavior across all gesture performers.
 *
 * Key Design Benefits:
 * - Centralized path creation logic prevents inconsistencies between gesture performers
 * - Type-safe factory methods reduce runtime errors in gesture creation
 * - Standardized timing parameters ensure predictable gesture behavior
 * - Consistent visual feedback coordination through timing synchronization
 * - Extensible design supports new gesture types without modifying existing performers
 *
 * Path Creation Strategy:
 * Each gesture type has a specialized creation method that:
 * 1. Creates appropriate Android Path objects with correct coordinates
 * 2. Applies gesture-specific timing parameters from GestureData constants
 * 3. Constructs GestureDescription with proper stroke timing and coordination
 * 4. Handles multi-stroke gestures (double-tap, hold-and-drag, zoom) with precise timing
 *
 * Timing Coordination:
 * - All timing parameters are centralized in GestureData for consistency
 * - Visual feedback timing is synchronized with actual gesture execution
 * - Multi-stroke gestures use calculated intervals for proper coordination
 * - Hold-and-drag gestures use overlapping strokes for smooth transitions
 *
 * Integration Points:
 * - GestureManager: Uses factory methods for immediate gesture execution
 * - LinearGesturePerformer: Uses factory methods for two-phase gesture completion
 * - GestureDispatcher: Receives standardized GestureDescription objects for dispatch
 * - GestureData: Provides timing constants and duration calculations
 *
 * Android Integration:
 * - Creates proper Android Path objects with correct coordinate systems
 * - Constructs GestureDescription.StrokeDescription with appropriate timing
 * - Handles coordinate validation and boundary checking
 * - Manages multi-finger gesture coordination for zoom operations
 */
object GesturePathBuilder {
    data class ContinuedGesture(
        val initial: GestureDescription,
        val continuation: GestureDescription,
        val continuationPoint: PointF
    )

    /**
     * Creates a simple tap gesture path.
     *
     * @param point The tap location
     * @param duration Duration of the tap in milliseconds
     * @return GestureDescription for the tap
     */
    fun createTapPath(
        point: PointF,
        duration: Long = GestureData.TAP_DURATION
    ): GestureDescription {
        val path = Path().apply {
            moveTo(point.x, point.y)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    /**
     * Creates a double tap gesture with proper timing.
     *
     * @param point The tap location
     * @param tapDuration Duration of each tap
     * @param interval Interval between taps
     * @return GestureDescription for the double tap
     */
    fun createDoubleTapPath(
        point: PointF,
        tapDuration: Long = GestureData.TAP_DURATION,
        interval: Long = GestureData.DOUBLE_TAP_INTERVAL
    ): GestureDescription {
        // First tap
        val firstTapPath = Path().apply { moveTo(point.x, point.y) }
        val firstTapStroke = GestureDescription.StrokeDescription(
            firstTapPath, 0, tapDuration
        )

        // Second tap
        val secondTapPath = Path().apply { moveTo(point.x, point.y) }
        val secondTapStroke = GestureDescription.StrokeDescription(
            secondTapPath, tapDuration + interval, tapDuration
        )

        return GestureDescription.Builder()
            .addStroke(firstTapStroke)
            .addStroke(secondTapStroke)
            .build()
    }

    /**
     * Creates a tap and hold gesture path.
     *
     * @param point The tap location
     * @param duration Duration of the hold
     * @return GestureDescription for the tap and hold
     */
    fun createTapAndHoldPath(
        point: PointF,
        duration: Long = GestureData.TAP_AND_HOLD_1S_DURATION
    ): GestureDescription {
        val path = Path().apply {
            moveTo(point.x, point.y)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    /**
     * Creates a linear gesture path (drag, swipe, scroll).
     *
     * @param startPoint Starting point of the gesture
     * @param endPoint Ending point of the gesture
     * @param duration Duration of the gesture
     * @return GestureDescription for the linear gesture
     */
    fun createLinearPath(
        startPoint: PointF,
        endPoint: PointF,
        duration: Long
    ): GestureDescription {
        val path = Path().apply {
            moveTo(startPoint.x, startPoint.y)
            lineTo(endPoint.x, endPoint.y)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, duration)
        return GestureDescription.Builder().addStroke(stroke).build()
    }

    fun createHoldAndDragPath(
        startPoint: PointF,
        endPoint: PointF,
        holdDuration: Long,
        dragDuration: Long = GestureData.DRAG_DURATION
    ): ContinuedGesture {
        val continuationPoint = holdContinuationPoint(startPoint, endPoint)
        val holdPath = Path().apply {
            moveTo(startPoint.x, startPoint.y)
            lineTo(continuationPoint.x, continuationPoint.y)
        }
        val holdStroke = GestureDescription.StrokeDescription(
            holdPath,
            0,
            holdDuration,
            true
        )
        val dragPath = Path().apply {
            moveTo(continuationPoint.x, continuationPoint.y)
            lineTo(endPoint.x, endPoint.y)
        }
        val dragStroke = holdStroke.continueStroke(
            dragPath,
            0,
            dragDuration,
            false
        )
        return ContinuedGesture(
            initial = GestureDescription.Builder().addStroke(holdStroke).build(),
            continuation = GestureDescription.Builder().addStroke(dragStroke).build(),
            continuationPoint = continuationPoint
        )
    }

    private fun holdContinuationPoint(startPoint: PointF, endPoint: PointF): PointF {
        val deltaX = endPoint.x - startPoint.x
        val deltaY = endPoint.y - startPoint.y
        val distance = kotlin.math.hypot(deltaX, deltaY)
        if (distance == 0f) return PointF(startPoint.x + 1f, startPoint.y)
        return PointF(
            startPoint.x + deltaX / distance,
            startPoint.y + deltaY / distance
        )
    }

    /**
     * Creates a pinch gesture with correct pinch mechanics.
     *
     * @param centerPoint Center point of the pinch
     * @param expands True when the fingers spread apart (zoom in), false when they move together (zoom out)
     * @param duration Duration of the pinch gesture
     * @return GestureDescription for the pinch
     */
    fun createPinchPath(
        centerPoint: PointF,
        expands: Boolean,
        duration: Long = GestureData.PINCH_DURATION
    ): GestureDescription {
        return createPinchPath(
            PinchGestureGeometry.calculate(centerPoint.x, centerPoint.y, expands),
            duration
        )
    }

    fun createPinchPath(
        geometry: PinchGestureGeometry,
        duration: Long = GestureData.PINCH_DURATION
    ): GestureDescription {
        val finger1Path = Path().apply {
            moveTo(geometry.first.start.x, geometry.first.start.y)
            lineTo(geometry.first.end.x, geometry.first.end.y)
        }
        val finger2Path = Path().apply {
            moveTo(geometry.second.start.x, geometry.second.start.y)
            lineTo(geometry.second.end.x, geometry.second.end.y)
        }

        return GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(finger1Path, 0, duration))
            .addStroke(GestureDescription.StrokeDescription(finger2Path, 0, duration))
            .build()
    }

    /**
     * Creates a custom gesture path from multiple strokes.
     *
     * @param strokes Array of stroke descriptions
     * @return GestureDescription combining all strokes
     */
    fun createCustomPath(vararg strokes: GestureDescription.StrokeDescription): GestureDescription {
        val builder = GestureDescription.Builder()
        strokes.forEach { stroke ->
            builder.addStroke(stroke)
        }
        return builder.build()
    }

    /**
     * Gets the appropriate duration for a gesture type.
     *
     * @param gestureType The type of gesture
     * @return Duration in milliseconds
     */
    fun getDurationForGestureType(gestureType: GestureType): Long {
        return when (gestureType) {
            GestureType.TAP -> GestureData.TAP_DURATION
            GestureType.DOUBLE_TAP -> GestureData.TAP_DURATION
            GestureType.TAP_AND_HOLD_0_5S -> GestureData.TAP_AND_HOLD_0_5S_DURATION
            GestureType.TAP_AND_HOLD_1S -> GestureData.TAP_AND_HOLD_1S_DURATION
            GestureType.TAP_AND_HOLD_2S -> GestureData.TAP_AND_HOLD_2S_DURATION
            GestureType.TAP_AND_HOLD_3S -> GestureData.TAP_AND_HOLD_3S_DURATION
            GestureType.TAP_AND_HOLD_5S -> GestureData.TAP_AND_HOLD_5S_DURATION
            GestureType.TAP_AND_HOLD_10S -> GestureData.TAP_AND_HOLD_10S_DURATION
            GestureType.DRAG -> GestureData.DRAG_DURATION
            GestureType.HOLD_AND_DRAG -> HoldAndDragTiming.systemHoldDuration() + GestureData.DRAG_DURATION
            GestureType.SCROLL_UP, GestureType.SCROLL_DOWN,
            GestureType.SCROLL_LEFT, GestureType.SCROLL_RIGHT -> GestureData.SCROLL_DURATION

            GestureType.PINCH_IN, GestureType.PINCH_OUT -> GestureData.PINCH_DURATION
            else -> GestureData.SWIPE_DURATION
        }
    }

    /**
     * Creates a dynamic gesture path based on FingerPlacement results from the algorithm.
     *
     * This is the core method-level integration point where the FingerPlacementAlgorithm's
     * results are converted into executable Android gesture paths. The method automatically
     * adapts to any finger count (1, 2, N) without requiring code changes.
     *
     * @param gestureType The type of gesture being performed
     * @param fingerPlacement Algorithm result containing finger positions and metadata
     * @param duration Gesture duration in milliseconds
     * @return GestureDescription with appropriate multi-finger strokes
     */
    fun createDynamicPath(
        gestureType: GestureType,
        fingerPlacement: FingerPlacement,
        duration: Long
    ): GestureDescription {
        return when (fingerPlacement) {
            is SingleFingerPlacement -> createSingleFingerGesturePath(
                gestureType, fingerPlacement, duration
            )

            is TwoFingerPlacement -> createTwoFingerGesturePath(
                gestureType, fingerPlacement, duration
            )

            is MultiFingerPlacement -> createMultiFingerGesturePath(
                gestureType, fingerPlacement, duration
            )
        }
    }

    /**
     * Creates single-finger gesture paths (traditional behavior).
     */
    private fun createSingleFingerGesturePath(
        gestureType: GestureType,
        placement: SingleFingerPlacement,
        duration: Long
    ): GestureDescription {
        val point = placement.primaryPoint

        return when (gestureType) {
            GestureType.TAP -> createTapPath(point, duration)
            GestureType.DOUBLE_TAP -> createDoubleTapPath(point)
            GestureType.TAP_AND_HOLD_0_5S,
            GestureType.TAP_AND_HOLD_1S,
            GestureType.TAP_AND_HOLD_2S,
            GestureType.TAP_AND_HOLD_3S,
            GestureType.TAP_AND_HOLD_5S,
            GestureType.TAP_AND_HOLD_10S -> createTapAndHoldPath(point, duration)
            else -> {
                // For linear gestures, we need an end point - use primary point for now
                // In practice, LinearGesturePerformer will provide the end point
                createLinearPath(point, point, duration)
            }
        }
    }

    /**
     * Creates two-finger gesture paths with synchronized finger movements.
     */
    private fun createTwoFingerGesturePath(
        gestureType: GestureType,
        placement: TwoFingerPlacement,
        duration: Long
    ): GestureDescription {
        val primaryPoint = placement.primaryPoint
        val secondaryPoint = placement.secondaryPoint

        return when (gestureType) {
            GestureType.TAP -> createTwoFingerTapPath(primaryPoint, secondaryPoint, duration)
            GestureType.DOUBLE_TAP -> createTwoFingerDoubleTapPath(primaryPoint, secondaryPoint)
            GestureType.TAP_AND_HOLD_0_5S,
            GestureType.TAP_AND_HOLD_1S,
            GestureType.TAP_AND_HOLD_2S,
            GestureType.TAP_AND_HOLD_3S,
            GestureType.TAP_AND_HOLD_5S,
            GestureType.TAP_AND_HOLD_10S -> createTwoFingerTapAndHoldPath(
                primaryPoint,
                secondaryPoint,
                duration
            )

            else -> {
                // For linear gestures, both fingers move in parallel
                // End points will be provided by LinearGesturePerformer for actual drags/swipes
                createTwoFingerLinearPath(
                    primaryPoint,
                    secondaryPoint,
                    primaryPoint,
                    secondaryPoint,
                    duration
                )
            }
        }
    }

    /**
     * Creates multi-finger gesture paths (3+ fingers) with proper coordination.
     */
    private fun createMultiFingerGesturePath(
        gestureType: GestureType,
        placement: MultiFingerPlacement,
        duration: Long
    ): GestureDescription {
        return when (gestureType) {
            GestureType.TAP -> createMultiFingerTapPath(placement.fingerPoints, duration)
            GestureType.DOUBLE_TAP -> createMultiFingerDoubleTapPath(
                placement.fingerPoints,
                duration
            )

            GestureType.TAP_AND_HOLD_0_5S,
            GestureType.TAP_AND_HOLD_1S,
            GestureType.TAP_AND_HOLD_2S,
            GestureType.TAP_AND_HOLD_3S,
            GestureType.TAP_AND_HOLD_5S,
            GestureType.TAP_AND_HOLD_10S -> createMultiFingerTapAndHoldPath(
                placement.fingerPoints,
                duration
            )

            else -> {
                // For linear gestures, all fingers move in parallel
                // End points will be provided by LinearGesturePerformer for actual drags/swipes
                createMultiFingerLinearPath(
                    placement.fingerPoints,
                    placement.fingerPoints,
                    duration
                )
            }
        }
    }

    // Two-finger gesture path creation methods

    /**
     * Creates a two-finger tap path with synchronized timing.
     */
    private fun createTwoFingerTapPath(
        point1: PointF,
        point2: PointF,
        duration: Long
    ): GestureDescription {
        // First finger
        val path1 = Path().apply { moveTo(point1.x, point1.y) }
        val stroke1 = GestureDescription.StrokeDescription(path1, 0, duration)

        // Second finger (synchronized)
        val path2 = Path().apply { moveTo(point2.x, point2.y) }
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, duration)

        return GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
    }

    /**
     * Creates a two-finger double tap path.
     */
    private fun createTwoFingerDoubleTapPath(
        point1: PointF,
        point2: PointF,
        tapDuration: Long = GestureData.TAP_DURATION,
        interval: Long = GestureData.DOUBLE_TAP_INTERVAL
    ): GestureDescription {
        val builder = GestureDescription.Builder()

        // First tap - both fingers
        val firstTap1 = Path().apply { moveTo(point1.x, point1.y) }
        val firstTap2 = Path().apply { moveTo(point2.x, point2.y) }
        builder.addStroke(GestureDescription.StrokeDescription(firstTap1, 0, tapDuration))
        builder.addStroke(GestureDescription.StrokeDescription(firstTap2, 0, tapDuration))

        // Second tap - both fingers
        val secondTap1 = Path().apply { moveTo(point1.x, point1.y) }
        val secondTap2 = Path().apply { moveTo(point2.x, point2.y) }
        builder.addStroke(
            GestureDescription.StrokeDescription(
                secondTap1,
                tapDuration + interval,
                tapDuration
            )
        )
        builder.addStroke(
            GestureDescription.StrokeDescription(
                secondTap2,
                tapDuration + interval,
                tapDuration
            )
        )

        return builder.build()
    }

    /**
     * Creates a two-finger tap and hold path.
     */
    private fun createTwoFingerTapAndHoldPath(
        point1: PointF,
        point2: PointF,
        duration: Long
    ): GestureDescription {
        // Both fingers hold simultaneously
        val path1 = Path().apply { moveTo(point1.x, point1.y) }
        val path2 = Path().apply { moveTo(point2.x, point2.y) }

        val stroke1 = GestureDescription.StrokeDescription(path1, 0, duration)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, duration)

        return GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
    }

    /**
     * Creates a two-finger linear path (drag, swipe, scroll).
     */
    private fun createTwoFingerLinearPath(
        startPoint1: PointF,
        startPoint2: PointF,
        endPoint1: PointF,
        endPoint2: PointF,
        duration: Long
    ): GestureDescription {
        // First finger movement
        val path1 = Path().apply {
            moveTo(startPoint1.x, startPoint1.y)
            lineTo(endPoint1.x, endPoint1.y)
        }

        // Second finger movement (parallel)
        val path2 = Path().apply {
            moveTo(startPoint2.x, startPoint2.y)
            lineTo(endPoint2.x, endPoint2.y)
        }

        val stroke1 = GestureDescription.StrokeDescription(path1, 0, duration)
        val stroke2 = GestureDescription.StrokeDescription(path2, 0, duration)

        return GestureDescription.Builder()
            .addStroke(stroke1)
            .addStroke(stroke2)
            .build()
    }

    // Multi-finger gesture path creation methods (3+ fingers)

    /**
     * Creates a multi-finger tap path with synchronized timing.
     */
    private fun createMultiFingerTapPath(
        fingerPoints: List<PointF>,
        duration: Long
    ): GestureDescription {
        val builder = GestureDescription.Builder()

        fingerPoints.forEach { point ->
            val path = Path().apply { moveTo(point.x, point.y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            builder.addStroke(stroke)
        }

        return builder.build()
    }

    /**
     * Creates a multi-finger double tap path.
     */
    private fun createMultiFingerDoubleTapPath(
        fingerPoints: List<PointF>,
        tapDuration: Long = GestureData.TAP_DURATION,
        interval: Long = GestureData.DOUBLE_TAP_INTERVAL
    ): GestureDescription {
        val builder = GestureDescription.Builder()

        // First tap - all fingers
        fingerPoints.forEach { point ->
            val firstTap = Path().apply { moveTo(point.x, point.y) }
            builder.addStroke(GestureDescription.StrokeDescription(firstTap, 0, tapDuration))
        }

        // Second tap - all fingers
        fingerPoints.forEach { point ->
            val secondTap = Path().apply { moveTo(point.x, point.y) }
            builder.addStroke(
                GestureDescription.StrokeDescription(
                    secondTap,
                    tapDuration + interval,
                    tapDuration
                )
            )
        }

        return builder.build()
    }

    /**
     * Creates a multi-finger tap and hold path.
     */
    private fun createMultiFingerTapAndHoldPath(
        fingerPoints: List<PointF>,
        duration: Long
    ): GestureDescription {
        val builder = GestureDescription.Builder()

        fingerPoints.forEach { point ->
            val path = Path().apply { moveTo(point.x, point.y) }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            builder.addStroke(stroke)
        }

        return builder.build()
    }

    /**
     * Creates a multi-finger linear path (drag, swipe, scroll).
     */
    private fun createMultiFingerLinearPath(
        startPoints: List<PointF>,
        endPoints: List<PointF>,
        duration: Long
    ): GestureDescription {
        val builder = GestureDescription.Builder()

        // Ensure we have matching start and end points
        val pointPairs = startPoints.zip(endPoints)

        pointPairs.forEach { (startPoint, endPoint) ->
            val path = Path().apply {
                moveTo(startPoint.x, startPoint.y)
                lineTo(endPoint.x, endPoint.y)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0, duration)
            builder.addStroke(stroke)
        }

        return builder.build()
    }

    /**
     * Creates a single-finger path for a specific gesture type (utility method).
     */
    private fun createSingleFingerPath(
        gestureType: GestureType,
        point: PointF,
        duration: Long
    ): Path {
        return when (gestureType) {
            GestureType.TAP,
            GestureType.TAP_AND_HOLD_0_5S,
            GestureType.TAP_AND_HOLD_1S,
            GestureType.TAP_AND_HOLD_2S,
            GestureType.TAP_AND_HOLD_3S,
            GestureType.TAP_AND_HOLD_5S,
            GestureType.TAP_AND_HOLD_10S -> Path().apply {
                moveTo(point.x, point.y)
            }

            else -> Path().apply {
                moveTo(point.x, point.y)
                // For linear gestures, lineTo will be added by the caller
            }
        }
    }

    /**
     * Creates a dynamic linear gesture path supporting multi-finger coordination.
     *
     * This method creates coordinated linear gesture paths for multiple fingers, where each finger
     * moves in parallel from its start position to its corresponding end position. This enables
     * natural multi-finger linear gestures like swipes and drags while maintaining the relative
     * finger positioning established by the FingerPlacementAlgorithm.
     *
     * Features:
     * - Supports any number of fingers (1 to N)
     * - Maintains relative finger spacing during movement
     * - Uses gesture-appropriate timing and duration
     *
     * @param gestureType The type of linear gesture (DRAG, SWIPE_*, SCROLL_*, etc.)
     * @param startPoints List of start positions for all fingers
     * @param endPoints List of end positions for all fingers (must match startPoints length)
     * @return GestureDescription with coordinated multi-finger linear paths
     */
    fun createDynamicLinearPath(
        gestureType: GestureType,
        startPoints: List<PointF>,
        endPoints: List<PointF>
    ): GestureDescription {
        require(startPoints.size == endPoints.size) {
            "Start points and end points must have the same size: ${startPoints.size} vs ${endPoints.size}"
        }

        val duration = getDurationForGestureType(gestureType)

        // Standard linear path for swipes, drags, scrolls
        return createMultiFingerLinearPath(startPoints, endPoints, duration)
    }

    /**
     * Creates a gesture description based on gesture data.
     *
     * @param gestureData The gesture data containing type, points, and other parameters
     * @return Appropriate GestureDescription for the gesture data
     */
    fun createFromGestureData(gestureData: GestureData): GestureDescription {
        return when (gestureData.gestureType) {
            GestureType.TAP -> createTapPath(gestureData.startPoint)
            GestureType.DOUBLE_TAP -> createDoubleTapPath(gestureData.startPoint)
            GestureType.TAP_AND_HOLD_0_5S,
            GestureType.TAP_AND_HOLD_1S,
            GestureType.TAP_AND_HOLD_2S,
            GestureType.TAP_AND_HOLD_3S,
            GestureType.TAP_AND_HOLD_5S,
            GestureType.TAP_AND_HOLD_10S -> createTapAndHoldPath(gestureData.startPoint, gestureData.duration())
            GestureType.PINCH_IN -> createPinchPath(gestureData.startPoint, false)
            GestureType.PINCH_OUT -> createPinchPath(gestureData.startPoint, true)
            else -> {
                val endPoint = gestureData.endPoint ?: gestureData.startPoint
                val duration = getDurationForGestureType(gestureData.gestureType)
                createLinearPath(gestureData.startPoint, endPoint, duration)
            }
        }
    }
}
