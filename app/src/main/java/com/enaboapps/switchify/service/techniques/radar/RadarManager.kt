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

    private val scanSettings = ScanSettings(context)
    private val radarUI = RadarUI(context)

    private var currentAngle = -90f  // Start at 12 o'clock (-90 degrees)
    private var currentDistanceRatio = 0f
    private var scanningScheduler: ScanningScheduler? = null

    private val screenCenterX: Float
        get() = ScreenUtils.getWidth(context) / 2f
    private val screenCenterY: Float
        get() = ScreenUtils.getHeight(context) / 2f
    private val maxDistance: Float
        get() {
            val width = ScreenUtils.getWidth(context).toFloat()
            val height = ScreenUtils.getHeight(context).toFloat()
            return sqrt(width * width + height * height) / 2f
        }

    private var currentStep = RadarStep.IDLE
    private var rotationDirection = RotationDirection.CLOCKWISE
    private var circleMovement = CircleMovement.OUTWARD

    init {
        setup()
    }

    private fun setup() {
        if (isSetupRequired()) {
            scanningScheduler = ScanningScheduler(context) { update() }
        }
    }

    private fun isSetupRequired(): Boolean = scanningScheduler == null

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
        updateRadarLine()
    }

    private fun moveCircle() {
        when (circleMovement) {
            CircleMovement.OUTWARD -> {
                currentDistanceRatio += MOVEMENT_STEP
                if (currentDistanceRatio > 1f || isCircleAtEdge()) {
                    circleMovement =
                        CircleMovement.INWARD  // Reverse direction when reaching the edge
                }
            }

            CircleMovement.INWARD -> {
                currentDistanceRatio -= MOVEMENT_STEP
                if (currentDistanceRatio < 0f) {
                    currentDistanceRatio = 0f
                    circleMovement =
                        CircleMovement.OUTWARD  // Reverse direction when reaching the center
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
        val x = screenCenterX + distance * cos(angle).toFloat()
        val y = screenCenterY + distance * sin(angle).toFloat()
        radarUI.showRadarCircle(x.toInt(), y.toInt())
    }

    private fun isCircleAtEdge(): Boolean {
        val angle = Math.toRadians(currentAngle.toDouble())
        val distance = currentDistanceRatio * maxDistance
        val x = screenCenterX + distance * cos(angle).toFloat()
        val y = screenCenterY + distance * sin(angle).toFloat()

        val screenWidth = ScreenUtils.getWidth(context)
        val screenHeight = ScreenUtils.getHeight(context)

        val circleSize = RadarUI.RADAR_CIRCLE_SIZE

        return x < 0 || x > screenWidth - circleSize || y < 0 || y > screenHeight - circleSize
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

        when (currentStep) {
            RadarStep.ROTATING -> {
                currentStep = RadarStep.MOVING
                currentDistanceRatio = if (circleMovement == CircleMovement.OUTWARD) 0f else 1f
                updateRadarCircle()  // Show the circle at the start of the line
                startAutoScanIfEnabled()
            }

            RadarStep.MOVING -> {
                val angle = Math.toRadians(currentAngle.toDouble())
                val distance = currentDistanceRatio * maxDistance
                val x = screenCenterX + distance * cos(angle).toFloat()
                val y = screenCenterY + distance * sin(angle).toFloat()
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

    override fun resetUI() {
        radarUI.reset()
    }

    private fun performTapAction() {
        GestureManager.instance.performTap()
    }

    override fun resetForNextUse() {
        currentStep = RadarStep.IDLE
        currentAngle = -90f
        currentDistanceRatio = 0f
        rotationDirection = RotationDirection.CLOCKWISE
        circleMovement = CircleMovement.OUTWARD
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