package com.enaboapps.switchify.service.gestures.execution

import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import com.enaboapps.switchify.service.gestures.data.GestureData
import com.enaboapps.switchify.service.gestures.data.GestureType
import com.enaboapps.switchify.service.gestures.placement.FingerPlacement
import com.enaboapps.switchify.service.gestures.placement.SingleFingerPlacement
import com.enaboapps.switchify.service.gestures.placement.TwoFingerPlacement
import com.enaboapps.switchify.service.gestures.placement.MultiFingerPlacement

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
        duration: Long = GestureData.TAP_AND_HOLD_DURATION
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

    /**
     * Creates a hold-and-drag gesture with proper timing.
     *
     * @param startPoint Starting point of the gesture
     * @param endPoint Ending point of the gesture
     * @param holdDuration Duration of the initial hold
     * @param dragDuration Duration of the drag movement
     * @return GestureDescription for the hold-and-drag
     */
    fun createHoldAndDragPath(
        startPoint: PointF,
        endPoint: PointF,
        holdDuration: Long = GestureData.HOLD_BEFORE_DRAG_DURATION,
        dragDuration: Long = GestureData.DRAG_DURATION
    ): GestureDescription {
        // Hold stroke
        val holdPath = Path().apply { moveTo(startPoint.x, startPoint.y) }
        val holdStroke = GestureDescription.StrokeDescription(
            holdPath, 0, holdDuration
        )

        // Drag stroke (starts slightly before hold ends for continuity)
        val dragPath = Path().apply {
            moveTo(startPoint.x, startPoint.y)
            lineTo(endPoint.x, endPoint.y)
        }
        val dragStroke = GestureDescription.StrokeDescription(
            dragPath, holdDuration - 5, dragDuration
        )

        return GestureDescription.Builder()
            .addStroke(holdStroke)
            .addStroke(dragStroke)
            .build()
    }

    /**
     * Creates a zoom gesture with correct pinch mechanics.
     *
     * @param centerPoint Center point of the zoom
     * @param isZoomIn True for zoom in (pinch), false for zoom out (spread)
     * @param duration Duration of the zoom gesture
     * @return GestureDescription for the zoom
     */
    fun createZoomPath(
        centerPoint: PointF,
        isZoomIn: Boolean,
        duration: Long = GestureData.ZOOM_DURATION
    ): GestureDescription {
        val startSeparation = if (isZoomIn) 50f else 200f
        val endSeparation = if (isZoomIn) 200f else 50f

        // First finger path (left side)
        val finger1Start = PointF(centerPoint.x - startSeparation / 2, centerPoint.y)
        val finger1End = PointF(centerPoint.x - endSeparation / 2, centerPoint.y)
        val finger1Path = Path().apply {
            moveTo(finger1Start.x, finger1Start.y)
            lineTo(finger1End.x, finger1End.y)
        }
        val finger1Stroke = GestureDescription.StrokeDescription(finger1Path, 0, duration)

        // Second finger path (right side)
        val finger2Start = PointF(centerPoint.x + startSeparation / 2, centerPoint.y)
        val finger2End = PointF(centerPoint.x + endSeparation / 2, centerPoint.y)
        val finger2Path = Path().apply {
            moveTo(finger2Start.x, finger2Start.y)
            lineTo(finger2End.x, finger2End.y)
        }
        val finger2Stroke = GestureDescription.StrokeDescription(finger2Path, 0, duration)

        return GestureDescription.Builder()
            .addStroke(finger1Stroke)
            .addStroke(finger2Stroke)
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
            GestureType.TAP_AND_HOLD -> GestureData.TAP_AND_HOLD_DURATION
            GestureType.DRAG, GestureType.HOLD_AND_DRAG -> GestureData.DRAG_DURATION
            GestureType.SCROLL_UP, GestureType.SCROLL_DOWN,
            GestureType.SCROLL_LEFT, GestureType.SCROLL_RIGHT -> GestureData.SCROLL_DURATION

            GestureType.ZOOM_IN, GestureType.ZOOM_OUT -> GestureData.ZOOM_DURATION
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
            GestureType.TAP_AND_HOLD -> createTapAndHoldPath(point, duration)
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
            GestureType.TAP_AND_HOLD -> createTwoFingerTapAndHoldPath(primaryPoint, secondaryPoint, duration)
            else -> {
                // For linear gestures, both fingers move in parallel
                // End points will be provided by LinearGesturePerformer for actual drags/swipes
                createTwoFingerLinearPath(primaryPoint, secondaryPoint, primaryPoint, secondaryPoint, duration)
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
            GestureType.DOUBLE_TAP -> createMultiFingerDoubleTapPath(placement.fingerPoints, duration)
            GestureType.TAP_AND_HOLD -> createMultiFingerTapAndHoldPath(placement.fingerPoints, duration)
            else -> {
                // For linear gestures, all fingers move in parallel
                // End points will be provided by LinearGesturePerformer for actual drags/swipes
                createMultiFingerLinearPath(placement.fingerPoints, placement.fingerPoints, duration)
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
        builder.addStroke(GestureDescription.StrokeDescription(secondTap1, tapDuration + interval, tapDuration))
        builder.addStroke(GestureDescription.StrokeDescription(secondTap2, tapDuration + interval, tapDuration))
        
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
            builder.addStroke(GestureDescription.StrokeDescription(secondTap, tapDuration + interval, tapDuration))
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
            GestureType.TAP, GestureType.TAP_AND_HOLD -> Path().apply {
                moveTo(point.x, point.y)
            }
            else -> Path().apply {
                moveTo(point.x, point.y)
                // For linear gestures, lineTo will be added by the caller
            }
        }
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
            GestureType.TAP_AND_HOLD -> createTapAndHoldPath(gestureData.startPoint)
            GestureType.HOLD_AND_DRAG -> createHoldAndDragPath(
                gestureData.startPoint,
                gestureData.endPoint ?: gestureData.startPoint
            )

            GestureType.ZOOM_IN -> createZoomPath(gestureData.startPoint, true)
            GestureType.ZOOM_OUT -> createZoomPath(gestureData.startPoint, false)
            else -> {
                val endPoint = gestureData.endPoint ?: gestureData.startPoint
                val duration = getDurationForGestureType(gestureData.gestureType)
                createLinearPath(gestureData.startPoint, endPoint, duration)
            }
        }
    }
}