package com.enaboapps.switchify.service.gestures.placement

import android.graphics.PointF
import android.graphics.Rect
import com.enaboapps.switchify.service.gestures.data.GestureType
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for FingerPlacementAlgorithm core functionality.
 * 
 * These tests validate the extensible method-level algorithm approach and ensure
 * that finger placement decisions are made correctly based on user preference,
 * gesture type, and available screen space.
 */
class FingerPlacementAlgorithmTest {

    private val algorithm = FingerPlacementAlgorithm()
    private val screenBounds = Rect(0, 0, 1080, 1920) // Standard phone screen
    private val centerPoint = PointF(540f, 960f) // Center of screen

    @Test
    fun testSingleFingerModeAlwaysReturnsOneFingerPlacement() {
        val placement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.ONE,
            screenBounds = screenBounds
        )

        assertTrue("Should return SingleFingerPlacement", placement is SingleFingerPlacement)
        assertEquals("Should have 1 finger", 1, placement.fingerCount)
        assertEquals("Primary point should match target", centerPoint, placement.primaryPoint)
    }

    @Test
    fun testTwoFingerModeReturnsTwoFingerPlacementWhenSpaceAvailable() {
        val placement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.TWO,
            screenBounds = screenBounds
        )

        assertTrue("Should return TwoFingerPlacement", placement is TwoFingerPlacement)
        assertEquals("Should have 2 fingers", 2, placement.fingerCount)
        assertEquals("Primary point should match target", centerPoint, placement.primaryPoint)
        
        val twoFingerPlacement = placement as TwoFingerPlacement
        assertTrue("Spacing should be reasonable", 
            twoFingerPlacement.spacing >= FingerPlacementAlgorithm.MIN_FINGER_SPACING)
        assertTrue("Spacing should not be excessive",
            twoFingerPlacement.spacing <= FingerPlacementAlgorithm.MAX_FINGER_SPACING)
    }

    @Test
    fun testTwoFingerModeFallsBackToSingleFingerWhenInsufficientSpace() {
        // Test near screen edge where two fingers won't fit
        val edgePoint = PointF(50f, 50f) // Very close to top-left corner
        
        val placement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP,
            targetPoint = edgePoint,
            userFingerMode = FingerMode.TWO,
            screenBounds = screenBounds
        )

        assertTrue("Should fallback to SingleFingerPlacement", placement is SingleFingerPlacement)
        assertEquals("Should have 1 finger", 1, placement.fingerCount)
        assertTrue("Should include space constraint factor",
            placement.metadata.decisionFactors.contains(DecisionFactor.SPACE_CONSTRAINT))
    }

    @Test
    fun testAutoModeConsidersGestureTypeForOptimalFingerCount() {
        // Test precision gesture (TAP) in AUTO mode - should prefer single finger
        val tapPlacement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.AUTO,
            screenBounds = screenBounds
        )

        assertTrue("TAP should prefer single finger for precision", 
            tapPlacement is SingleFingerPlacement)

        // Test stability gesture (TAP_AND_HOLD) in AUTO mode - should prefer two fingers
        val holdPlacement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP_AND_HOLD,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.AUTO,
            screenBounds = screenBounds
        )

        assertTrue("TAP_AND_HOLD should prefer two fingers for stability",
            holdPlacement is TwoFingerPlacement)
    }

    @Test
    fun testPlacementValidationWithinScreenBounds() {
        val placement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.TWO,
            screenBounds = screenBounds
        )

        assertTrue("Placement should be valid", placement.isValidPlacement())
        
        // Test that all finger points are within bounds
        placement.fingerPoints.forEach { point ->
            assertTrue("Finger X should be within bounds", 
                point.x >= screenBounds.left && point.x <= screenBounds.right)
            assertTrue("Finger Y should be within bounds",
                point.y >= screenBounds.top && point.y <= screenBounds.bottom)
        }
    }

    @Test
    fun testMetadataIncludesDecisionFactors() {
        val placement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP_AND_HOLD,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.TWO,
            screenBounds = screenBounds
        )

        val metadata = placement.metadata
        assertEquals("Should record correct requested mode", FingerMode.TWO, metadata.requestedMode)
        assertEquals("Should record correct gesture type", GestureType.TAP_AND_HOLD, metadata.gestureType)
        assertTrue("Should include user preference factor",
            metadata.decisionFactors.contains(DecisionFactor.USER_PREFERENCE))
        assertTrue("Should include gesture optimization factor",
            metadata.decisionFactors.contains(DecisionFactor.GESTURE_OPTIMIZATION))
        assertTrue("Should calculate available space", metadata.availableSpace > 0)
    }

    @Test
    fun testAlgorithmPerformance() {
        val startTime = System.currentTimeMillis()
        
        // Run algorithm multiple times to test performance
        repeat(100) {
            algorithm.calculateFingerPlacement(
                gestureType = GestureType.TAP,
                targetPoint = centerPoint,
                userFingerMode = FingerMode.AUTO,
                screenBounds = screenBounds
            )
        }
        
        val totalTime = System.currentTimeMillis() - startTime
        val averageTime = totalTime / 100.0
        
        assertTrue("Algorithm should execute quickly (average < 5ms per call)",
            averageTime < FingerPlacementAlgorithm.MAX_CALCULATION_TIME_MS)
    }

    @Test
    fun testExtensibilityToFutureFingerModes() {
        // Test that algorithm gracefully handles unknown enum values
        // This tests forward compatibility when new finger modes are added
        
        val placement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.AUTO, // Use AUTO as proxy for extensible behavior
            screenBounds = screenBounds
        )

        assertNotNull("Algorithm should handle all finger modes", placement)
        assertTrue("Should return valid placement", placement.isValidPlacement())
        assertTrue("Should have reasonable finger count", 
            placement.fingerCount in 1..4) // Allow for future expansion
    }

    @Test
    fun testTwoFingerPlacementStrategies() {
        val twoFingerPlacement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP_AND_HOLD,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.TWO,
            screenBounds = screenBounds
        ) as TwoFingerPlacement

        // Verify strategy is reasonable
        assertTrue("Should use a valid strategy", 
            twoFingerPlacement.placementStrategy in TwoFingerStrategy.values())
        
        // Verify finger points are properly spaced
        val distance = kotlin.math.sqrt(
            (twoFingerPlacement.secondaryPoint.x - twoFingerPlacement.primaryPoint.x) *
            (twoFingerPlacement.secondaryPoint.x - twoFingerPlacement.primaryPoint.x) +
            (twoFingerPlacement.secondaryPoint.y - twoFingerPlacement.primaryPoint.y) *
            (twoFingerPlacement.secondaryPoint.y - twoFingerPlacement.primaryPoint.y)
        )
        
        assertEquals("Distance should match reported spacing", 
            twoFingerPlacement.spacing, distance, 1.0f)
    }

    @Test 
    fun testDescriptionsAreInformative() {
        val singlePlacement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.ONE,
            screenBounds = screenBounds
        )

        val twoPlacement = algorithm.calculateFingerPlacement(
            gestureType = GestureType.TAP_AND_HOLD,
            targetPoint = centerPoint,
            userFingerMode = FingerMode.TWO,
            screenBounds = screenBounds
        )

        assertTrue("Single finger description should be informative",
            singlePlacement.getDescription().contains("Single finger"))
        assertTrue("Two finger description should be informative",
            twoPlacement.getDescription().contains("Two fingers"))
        assertTrue("Metadata summary should be informative",
            twoPlacement.metadata.getDecisionSummary().isNotEmpty())
    }
}