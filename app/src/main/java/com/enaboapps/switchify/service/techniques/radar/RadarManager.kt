package com.enaboapps.switchify.service.techniques.radar

import android.content.Context
import android.os.SystemClock
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningScheduler
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.utils.ContinuousLineSpeedUtils
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadarManager(private val context: Context) : AccessTechniqueInterface {

    companion object {
        private const val TAG = "RadarManager"
        private const val FULL_CIRCLE = 360f
        private const val SLOW_DOWN_FACTOR = 4f
        private const val MAX_ELAPSED_MS = 250L

        // Angle constants for radar positioning
        private const val ANGLE_RIGHT = 0f  // Horizontal right
        private const val ANGLE_LEFT = 180f  // Horizontal left

        // Distance and position constants
        private const val INITIAL_DISTANCE_RATIO = 0f  // Starting distance from pivot
        private const val MAX_DISTANCE_RATIO = 1f  // Maximum distance from pivot
        private const val SCREEN_EDGE_POSITION = 0f  // Screen edge coordinate
        private const val PIVOT_DIVISOR = 2f  // Divisor for calculating pivot position
    }

    enum class RadarStep {
        IDLE,
        ROTATING,
        MOVING
    }

    enum class RotationDirection {
        CLOCKWISE,
        ANTI_CLOCKWISE
    }

    enum class CircleMovement {
        OUTWARD,
        INWARD
    }

    enum class SlowDownState {
        NORMAL_SPEED,
        SLOWED_DOWN
    }

    private val scanSettings = ScanSettings(context)
    private val radarUI = RadarUI(context)

    init {
        RadarSettings.init(context)
    }

    private var currentAngle = getStartingAngle()
    private var currentDistanceRatio = INITIAL_DISTANCE_RATIO
    private var targetScreenX =
        INITIAL_DISTANCE_RATIO  // Target screen X coordinate for current angle
    private var targetScreenY =
        INITIAL_DISTANCE_RATIO  // Target screen Y coordinate for current angle
    private var scanningScheduler: ScanningScheduler? = null
    private var lastUpdateTime = 0L

    // Windscreen wiper pivot point - configurable top or bottom
    private val wiperPivotX: Float
        get() = ScreenUtils.getWidth(context) / PIVOT_DIVISOR
    private val wiperPivotY: Float
        get() = if (RadarSettings.getStartingPosition() == RadarSettings.StartingPosition.TOP) {
            SCREEN_EDGE_POSITION  // Top edge
        } else {
            ScreenUtils.getHeight(context).toFloat()  // Bottom edge
        }
    private val maxDistance: Float
        get() {
            val width = ScreenUtils.getWidth(context).toFloat()
            val height = ScreenUtils.getHeight(context).toFloat()
            // For windscreen wiper, calculate max distance to screen edge from pivot
            return if (RadarSettings.getStartingPosition() == RadarSettings.StartingPosition.TOP) {
                // From top edge, max distance is to bottom edge
                height
            } else {
                // From bottom edge, max distance is to top edge plus width for diagonal reach
                sqrt(width * width + height * height) / 2f
            }
        }

    private var currentStep = RadarStep.IDLE
    private var rotationDirection = RotationDirection.CLOCKWISE
    private var circleMovement = CircleMovement.OUTWARD
    private var slowDownState = SlowDownState.NORMAL_SPEED

    init {
        setup()
    }

    private fun setup() {
        if (isSetupRequired()) {
            scanningScheduler = ScanningScheduler(context) { update() }
        }
    }

    private fun isSetupRequired(): Boolean = scanningScheduler == null

    private fun getStartingAngle(): Float {
        return if (RadarSettings.getStartingPosition() == RadarSettings.StartingPosition.TOP) {
            ANGLE_RIGHT  // Pointing right when starting from top (horizontal)
        } else {
            ANGLE_LEFT  // Pointing left when starting from bottom (horizontal)
        }
    }

    private fun update() {
        val elapsedMs = getElapsedMs()
        when (currentStep) {
            RadarStep.ROTATING -> rotate(elapsedMs)
            RadarStep.MOVING -> moveCircle(elapsedMs)
            RadarStep.IDLE -> {} // Do nothing
        }
    }

    private fun getElapsedMs(): Long {
        val now = SystemClock.elapsedRealtime()
        val elapsed = if (lastUpdateTime == 0L) {
            ContinuousLineSpeedUtils.UPDATE_PERIOD_MS
        } else {
            now - lastUpdateTime
        }
        lastUpdateTime = now
        return elapsed.coerceIn(1L, MAX_ELAPSED_MS)
    }

    private fun resetElapsedTime() {
        lastUpdateTime = SystemClock.elapsedRealtime()
    }

    private fun currentSlowDownFactor(): Float {
        return if (slowDownState == SlowDownState.SLOWED_DOWN) SLOW_DOWN_FACTOR else 1f
    }

    private fun rotate(elapsedMs: Long) {
        val rotationStep =
            RadarSettings.getAngularSpeedDegreesPerSecond(currentSlowDownFactor()) * elapsedMs / 1000f
        val previousAngle = currentAngle
        currentAngle = when (rotationDirection) {
            RotationDirection.CLOCKWISE -> (currentAngle + rotationStep) % FULL_CIRCLE
            RotationDirection.ANTI_CLOCKWISE -> (currentAngle - rotationStep + FULL_CIRCLE) % FULL_CIRCLE
        }

        val boundaryAngle = crossedHorizontalBoundary(previousAngle, currentAngle)
        if (boundaryAngle != null) {
            currentAngle = boundaryAngle
            toggleRotationDirection()
        }

        // Calculate target screen coordinates for this angle
        calculateTargetScreenPoint()
        updateRadarLine()
    }

    private fun crossedHorizontalBoundary(previousAngle: Float, nextAngle: Float): Float? {
        // Change direction when completing 180° sweep from horizontal starting position
        val normalizedAngle = (currentAngle + 360) % 360

        return if (RadarSettings.getStartingPosition() == RadarSettings.StartingPosition.TOP) {
            // Starting from right (0°), change direction when reaching horizontal limits
            if (rotationDirection == RotationDirection.CLOCKWISE) {
                // Clockwise from 0° -> stop at 180°
                if (nextAngle >= ANGLE_LEFT) ANGLE_LEFT else null
            } else {
                // Anti-clockwise from 0° -> stop at 0° (don't go past horizontal)
                if (nextAngle > previousAngle) ANGLE_RIGHT else null
            }
        } else {
            // Starting from left (180°), change direction when reaching horizontal limits
            if (rotationDirection == RotationDirection.CLOCKWISE) {
                // Clockwise from 180° -> stop at 0°
                if (nextAngle < previousAngle) ANGLE_RIGHT else null
            } else {
                // Anti-clockwise from 180° -> stop at 180° (don't go past horizontal)
                if (previousAngle > ANGLE_LEFT && nextAngle <= ANGLE_LEFT) ANGLE_LEFT else null
            }
        }
    }

    private fun moveCircle(elapsedMs: Long) {
        val movementStep = if (maxDistance > 0f) {
            RadarSettings.getLinearSpeedPxPerSecond(context, currentSlowDownFactor()) * elapsedMs / 1000f / maxDistance
        } else {
            0f
        }
        when (circleMovement) {
            CircleMovement.OUTWARD -> {
                currentDistanceRatio += movementStep
                val currentX = wiperPivotX + currentDistanceRatio * maxDistance * cos(
                    Math.toRadians(currentAngle.toDouble())
                ).toFloat()
                val currentY = wiperPivotY + currentDistanceRatio * maxDistance * sin(
                    Math.toRadians(currentAngle.toDouble())
                ).toFloat()

                // Check if we've reached the target screen edge exactly
                if ((currentX >= targetScreenX && targetScreenX > wiperPivotX) ||
                    (currentX <= targetScreenX && targetScreenX < wiperPivotX) ||
                    (currentY >= targetScreenY && targetScreenY > wiperPivotY) ||
                    (currentY <= targetScreenY && targetScreenY < wiperPivotY)
                ) {
                    circleMovement = CircleMovement.INWARD
                }
            }

            CircleMovement.INWARD -> {
                currentDistanceRatio -= movementStep
                if (currentDistanceRatio <= INITIAL_DISTANCE_RATIO) {
                    currentDistanceRatio = INITIAL_DISTANCE_RATIO
                    circleMovement = CircleMovement.OUTWARD
                }
            }
        }
        updateRadarCircle()
    }

    private fun updateRadarLine() {
        radarUI.showRadarLine(currentAngle)
    }

    private fun updateRadarCircle() {
        val angle = Math.toRadians(currentAngle.toDouble())
        val distance = currentDistanceRatio * maxDistance
        val x = wiperPivotX + distance * cos(angle).toFloat()
        val y = wiperPivotY + distance * sin(angle).toFloat()
        radarUI.showRadarCircle(x.toInt(), y.toInt())
    }

    private fun calculateTargetScreenPoint() {
        val angle = Math.toRadians(currentAngle.toDouble())
        val screenWidth = ScreenUtils.getWidth(context).toFloat()
        val screenHeight = ScreenUtils.getHeight(context).toFloat()

        // Calculate where the line intersects the screen edges
        val cos = cos(angle).toFloat()
        val sin = sin(angle).toFloat()

        // Calculate intersection with each screen edge
        val intersections = mutableListOf<Pair<Float, Float>>()

        // Left edge (x = 0)
        if (cos < 0) {
            val t = -wiperPivotX / cos
            val y = wiperPivotY + t * sin
            if (y >= SCREEN_EDGE_POSITION && y <= screenHeight) {
                intersections.add(Pair(SCREEN_EDGE_POSITION, y))
            }
        }

        // Right edge (x = screenWidth)
        if (cos > 0) {
            val t = (screenWidth - wiperPivotX) / cos
            val y = wiperPivotY + t * sin
            if (y >= SCREEN_EDGE_POSITION && y <= screenHeight) {
                intersections.add(Pair(screenWidth, y))
            }
        }

        // Top edge (y = 0)
        if (sin < 0) {
            val t = -wiperPivotY / sin
            val x = wiperPivotX + t * cos
            if (x >= SCREEN_EDGE_POSITION && x <= screenWidth) {
                intersections.add(Pair(x, SCREEN_EDGE_POSITION))
            }
        }

        // Bottom edge (y = screenHeight)
        if (sin > 0) {
            val t = (screenHeight - wiperPivotY) / sin
            val x = wiperPivotX + t * cos
            if (x >= SCREEN_EDGE_POSITION && x <= screenWidth) {
                intersections.add(Pair(x, screenHeight))
            }
        }

        // Use the closest intersection point
        if (intersections.isNotEmpty()) {
            val closest = intersections.minByOrNull {
                val dx = it.first - wiperPivotX
                val dy = it.second - wiperPivotY
                sqrt(dx * dx + dy * dy)
            }
            if (closest != null) {
                targetScreenX = closest.first
                targetScreenY = closest.second
            }
        }
    }


    private fun startRadar() {
        if (currentStep == RadarStep.IDLE) {
            currentStep = RadarStep.ROTATING
            updateRadarLine()
            startAutoScanIfEnabled()
        }
    }

    private fun startAutoScanIfEnabled() {
        if (scanSettings.isAutoScanMode()) {
            val rate = ContinuousLineSpeedUtils.UPDATE_PERIOD_MS
            resetElapsedTime()
            scanningScheduler?.startScanning(rate, rate)
        }
    }

    override fun stepScanningForward() {
        rotationDirection = RotationDirection.CLOCKWISE
        circleMovement = CircleMovement.OUTWARD
        if (currentStep == RadarStep.IDLE) {
            currentStep = RadarStep.ROTATING
        }

        when (currentStep) {
            RadarStep.ROTATING -> rotate(ContinuousLineSpeedUtils.UPDATE_PERIOD_MS)
            RadarStep.MOVING -> moveCircle(ContinuousLineSpeedUtils.UPDATE_PERIOD_MS)
            RadarStep.IDLE -> {} // Do nothing
        }
    }

    override fun stepScanningBackward() {
        rotationDirection = RotationDirection.ANTI_CLOCKWISE
        circleMovement = CircleMovement.INWARD
        if (currentStep == RadarStep.IDLE) {
            currentStep = RadarStep.ROTATING
        }

        when (currentStep) {
            RadarStep.ROTATING -> rotate(ContinuousLineSpeedUtils.UPDATE_PERIOD_MS)
            RadarStep.MOVING -> moveCircle(ContinuousLineSpeedUtils.UPDATE_PERIOD_MS)
            RadarStep.IDLE -> {} // Do nothing
        }
    }

    override fun startAutoScanning() {
        setup()
        startRadar()
    }

    override fun stopScanningAndReset() {
        super.stopScanningAndReset()
        scanningScheduler?.stopScanning()
    }

    override fun pauseAutoScanning() {
        scanningScheduler?.pauseScanning()
    }

    override fun resumeAutoScanning() {
        scanningScheduler?.resumeScanning()
    }

    override fun performSelectionAction() {
        setup()
        if (isSetupRequired()) return // Failsafe in case setup was not successful

        // Check if slow down then select mode is enabled
        if (scanSettings.isRadarSlowDownThenSelectEnabled() && scanSettings.isAutoScanMode()) {
            handleSlowDownThenSelectMode()
        } else {
            handleNormalSelectionMode()
        }
    }

    private fun handleSlowDownThenSelectMode() {
        when (currentStep) {
            RadarStep.ROTATING -> {
                when (slowDownState) {
                    SlowDownState.NORMAL_SPEED -> {
                        // First press: slow down the rotation
                        slowDownState = SlowDownState.SLOWED_DOWN
                        slowDownScanning()
                    }

                    SlowDownState.SLOWED_DOWN -> {
                        // Second press: select line position and move to circle movement
                        slowDownState = SlowDownState.NORMAL_SPEED
                        currentStep = RadarStep.MOVING
                        resetElapsedTime()
                        currentDistanceRatio =
                            if (circleMovement == CircleMovement.OUTWARD) INITIAL_DISTANCE_RATIO else MAX_DISTANCE_RATIO
                        updateRadarCircle()
                        resumeNormalSpeed()
                    }
                }
            }

            RadarStep.MOVING -> {
                when (slowDownState) {
                    SlowDownState.NORMAL_SPEED -> {
                        // First press: slow down the circle movement
                        slowDownState = SlowDownState.SLOWED_DOWN
                        slowDownScanning()
                    }

                    SlowDownState.SLOWED_DOWN -> {
                        // Second press: select final position and perform tap
                        slowDownState = SlowDownState.NORMAL_SPEED
                        val angle = Math.toRadians(currentAngle.toDouble())
                        val distance = currentDistanceRatio * maxDistance
                        val x = wiperPivotX + distance * cos(angle).toFloat()
                        val y = wiperPivotY + distance * sin(angle).toFloat()
                        GesturePoint.x = x.toInt()
                        GesturePoint.y = y.toInt()
                        SelectionHandler.setSelectAction { performTapAction() }
                        SelectionHandler.setStartScanningAction { startRadar() }
                        SelectionHandler.performSelectionAction()
                        stopScanningAndReset()
                    }
                }
            }

            RadarStep.IDLE -> {
                slowDownState = SlowDownState.NORMAL_SPEED
                startRadar()
            }
        }
    }

    private fun handleNormalSelectionMode() {
        when (currentStep) {
            RadarStep.ROTATING -> {
                currentStep = RadarStep.MOVING
                resetElapsedTime()
                currentDistanceRatio =
                    if (circleMovement == CircleMovement.OUTWARD) INITIAL_DISTANCE_RATIO else MAX_DISTANCE_RATIO
                updateRadarCircle()
                startAutoScanIfEnabled()
            }

            RadarStep.MOVING -> {
                val angle = Math.toRadians(currentAngle.toDouble())
                val distance = currentDistanceRatio * maxDistance
                val x = wiperPivotX + distance * cos(angle).toFloat()
                val y = wiperPivotY + distance * sin(angle).toFloat()
                GesturePoint.x = x.toInt()
                GesturePoint.y = y.toInt()
                SelectionHandler.setSelectAction { performTapAction() }
                SelectionHandler.setStartScanningAction { startRadar() }
                SelectionHandler.performSelectionAction()
                stopScanningAndReset()
            }

            RadarStep.IDLE -> {
                startRadar()
            }
        }
    }

    private fun slowDownScanning() {
        resetElapsedTime()
    }

    private fun resumeNormalSpeed() {
        resetElapsedTime()
    }

    override fun resetUI() {
        radarUI.reset()
    }

    private fun performTapAction() {
        GestureManager.instance.performTap(overrideFingerMode = com.enaboapps.switchify.service.gestures.placement.FingerMode.ONE)
    }

    override fun resetForNextUse() {
        currentStep = RadarStep.IDLE
        currentAngle = getStartingAngle()  // Reset to correct starting angle based on position
        currentDistanceRatio = INITIAL_DISTANCE_RATIO
        targetScreenX = INITIAL_DISTANCE_RATIO
        targetScreenY = INITIAL_DISTANCE_RATIO
        rotationDirection = RotationDirection.CLOCKWISE
        circleMovement = CircleMovement.OUTWARD
        slowDownState = SlowDownState.NORMAL_SPEED
        lastUpdateTime = 0L
    }

    override fun cleanup() {
        super.cleanup()
        scanningScheduler?.shutdown()
        scanningScheduler = null
    }

    private fun toggleRotationDirection() {
        rotationDirection = when (rotationDirection) {
            RotationDirection.CLOCKWISE -> RotationDirection.ANTI_CLOCKWISE
            RotationDirection.ANTI_CLOCKWISE -> RotationDirection.CLOCKWISE
        }
    }

    private fun toggleCircleMovement() {
        circleMovement = when (circleMovement) {
            CircleMovement.OUTWARD -> CircleMovement.INWARD
            CircleMovement.INWARD -> CircleMovement.OUTWARD
        }
    }

    override fun swapScanDirection() {
        when (currentStep) {
            RadarStep.ROTATING -> toggleRotationDirection()
            RadarStep.MOVING -> toggleCircleMovement()
            RadarStep.IDLE -> {}  // Do nothing
        }
    }

    override fun stepScanningUp() {
        // Not applicable for radar mode
    }

    override fun stepScanningDown() {
        // Not applicable for radar mode
    }

    override fun stepScanningLeft() {
        stepScanningBackward()
    }

    override fun stepScanningRight() {
        stepScanningForward()
    }
}
