package com.enaboapps.switchify.service.techniques.radar

import android.content.Context
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningScheduler
import com.enaboapps.switchify.service.selection.SelectionHandler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class RadarManager(private val context: Context) : AccessTechniqueInterface {

    companion object {
        private const val TAG = "RadarManager"
        private const val FULL_CIRCLE = 360f
        private const val ROTATION_STEP = 1f  // 1 degree per step
        private const val MOVEMENT_STEP = 0.01f  // 1% of the max distance per step
        private const val SLOW_DOWN_FACTOR = 4f  // Factor to slow down by (4x slower)
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

    private var currentAngle = getStartingAngle()
    private var currentDistanceRatio = 0f
    private var targetScreenX = 0f  // Target screen X coordinate for current angle
    private var targetScreenY = 0f  // Target screen Y coordinate for current angle
    private var scanningScheduler: ScanningScheduler? = null

    // Windscreen wiper pivot point - configurable top or bottom
    private val wiperPivotX: Float
        get() = ScreenUtils.getWidth(context) / 2f
    private val wiperPivotY: Float
        get() = if (scanSettings.getRadarStartingPosition() == ScanSettings.RADAR_START_TOP) {
            0f  // Top edge
        } else {
            ScreenUtils.getHeight(context).toFloat()  // Bottom edge
        }
    private val maxDistance: Float
        get() {
            val width = ScreenUtils.getWidth(context).toFloat()
            val height = ScreenUtils.getHeight(context).toFloat()
            // For windscreen wiper, use full diagonal distance from pivot point
            return sqrt(width * width + height * height)
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
        return if (scanSettings.getRadarStartingPosition() == ScanSettings.RADAR_START_TOP) {
            0f  // 0 degrees = pointing right when starting from top (horizontal)
        } else {
            180f  // 180 degrees = pointing left when starting from bottom (horizontal)
        }
    }

    private fun update() {
        when (currentStep) {
            RadarStep.ROTATING -> rotate()
            RadarStep.MOVING -> moveCircle()
            RadarStep.IDLE -> {} // Do nothing
        }
    }

    private fun rotate() {
        currentAngle = when (rotationDirection) {
            RotationDirection.CLOCKWISE -> (currentAngle + ROTATION_STEP) % FULL_CIRCLE
            RotationDirection.ANTI_CLOCKWISE -> (currentAngle - ROTATION_STEP + FULL_CIRCLE) % FULL_CIRCLE
        }
        
        // Auto-change direction when line reaches horizontal (0 degrees or 180 degrees)
        if (shouldChangeDirection()) {
            toggleRotationDirection()
        }
        
        // Calculate target screen coordinates for this angle
        calculateTargetScreenPoint()
        updateRadarLine()
    }
    
    private fun shouldChangeDirection(): Boolean {
        // Change direction when reaching vertical positions (90° and 270°)
        val normalizedAngle = (currentAngle + 360) % 360
        return (normalizedAngle >= 89 && normalizedAngle <= 91) || (normalizedAngle >= 269 && normalizedAngle <= 271)
    }

    private fun moveCircle() {
        when (circleMovement) {
            CircleMovement.OUTWARD -> {
                currentDistanceRatio += MOVEMENT_STEP
                val currentX = wiperPivotX + currentDistanceRatio * maxDistance * cos(Math.toRadians(currentAngle.toDouble())).toFloat()
                val currentY = wiperPivotY + currentDistanceRatio * maxDistance * sin(Math.toRadians(currentAngle.toDouble())).toFloat()
                
                // Check if we've reached the target screen edge exactly
                if ((currentX >= targetScreenX && targetScreenX > wiperPivotX) || 
                    (currentX <= targetScreenX && targetScreenX < wiperPivotX) ||
                    (currentY >= targetScreenY && targetScreenY > wiperPivotY) ||
                    (currentY <= targetScreenY && targetScreenY < wiperPivotY)) {
                    circleMovement = CircleMovement.INWARD
                }
            }

            CircleMovement.INWARD -> {
                currentDistanceRatio -= MOVEMENT_STEP
                if (currentDistanceRatio <= 0f) {
                    currentDistanceRatio = 0f
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
            if (y >= 0 && y <= screenHeight) {
                intersections.add(Pair(0f, y))
            }
        }
        
        // Right edge (x = screenWidth)
        if (cos > 0) {
            val t = (screenWidth - wiperPivotX) / cos
            val y = wiperPivotY + t * sin
            if (y >= 0 && y <= screenHeight) {
                intersections.add(Pair(screenWidth, y))
            }
        }
        
        // Top edge (y = 0)
        if (sin < 0) {
            val t = -wiperPivotY / sin
            val x = wiperPivotX + t * cos
            if (x >= 0 && x <= screenWidth) {
                intersections.add(Pair(x, 0f))
            }
        }
        
        // Bottom edge (y = screenHeight)
        if (sin > 0) {
            val t = (screenHeight - wiperPivotY) / sin
            val x = wiperPivotX + t * cos
            if (x >= 0 && x <= screenWidth) {
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
            val rate = scanSettings.getRadarScanRate()
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
            RadarStep.ROTATING -> rotate()
            RadarStep.MOVING -> moveCircle()
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
            RadarStep.ROTATING -> rotate()
            RadarStep.MOVING -> moveCircle()
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
                        currentDistanceRatio = if (circleMovement == CircleMovement.OUTWARD) 0f else 1f
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
                currentDistanceRatio = if (circleMovement == CircleMovement.OUTWARD) 0f else 1f
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
        val normalRate = scanSettings.getRadarScanRate()
        val slowedRate = (normalRate * SLOW_DOWN_FACTOR).toLong()
        scanningScheduler?.stopScanning()
        scanningScheduler?.startScanning(slowedRate, slowedRate)
    }

    private fun resumeNormalSpeed() {
        val normalRate = scanSettings.getRadarScanRate()
        scanningScheduler?.stopScanning()
        scanningScheduler?.startScanning(normalRate, normalRate)
    }

    override fun resetUI() {
        radarUI.reset()
    }

    private fun performTapAction() {
        GestureManager.instance.performTap()
    }

    override fun resetForNextUse() {
        currentStep = RadarStep.IDLE
        currentAngle = getStartingAngle()  // Reset to correct starting angle based on position
        currentDistanceRatio = 0f
        targetScreenX = 0f
        targetScreenY = 0f
        rotationDirection = RotationDirection.CLOCKWISE
        circleMovement = CircleMovement.OUTWARD
        slowDownState = SlowDownState.NORMAL_SPEED
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
}