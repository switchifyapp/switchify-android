package com.enaboapps.switchify.service.techniques.radar.sweep

import android.content.Context
import android.graphics.PointF
import android.graphics.Rect
import android.os.SystemClock
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningScheduler
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.techniques.blocks.ScanBlock
import com.enaboapps.switchify.service.techniques.radar.RadarSettings
import com.enaboapps.switchify.service.utils.ContinuousLineSpeedUtils
import com.enaboapps.switchify.service.utils.ScreenUtils
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Runs the windscreen-wiper radar sweep: a line rotates from a pivot, then a
 * circle travels along the chosen line. The sweep covers the whole screen unless
 * a block has been set with [setBlock], in which case everything stays inside it.
 */
class RadarSweepManager(
    private val context: Context,
    private val onPointSelected: (PointF) -> Unit
) : AccessTechniqueInterface {

    companion object {
        private const val FULL_CIRCLE = 360f
        private const val SLOW_DOWN_FACTOR = 4f
        private const val MAX_ELAPSED_MS = 250L

        private const val ANGLE_RIGHT = 0f
        private const val ANGLE_LEFT = 180f

        private const val INITIAL_DISTANCE_RATIO = 0f
        private const val MAX_DISTANCE_RATIO = 1f
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
    private val radarUI = RadarSweepUI(context)

    init {
        RadarSettings.init(context)
    }

    private var currentBlock: ScanBlock? = null
    private var currentAngle = getStartingAngle()
    private var currentDistanceRatio = INITIAL_DISTANCE_RATIO
    private var targetScreenX = INITIAL_DISTANCE_RATIO
    private var targetScreenY = INITIAL_DISTANCE_RATIO
    private var scanningScheduler: ScanningScheduler? = null
    private var lastUpdateTime = 0L

    private val geometry: RadarSweepGeometry
        get() {
            val bounds = currentBlock?.let { Rect(it.left, it.top, it.right, it.bottom) }
                ?: Rect(0, 0, ScreenUtils.getWidth(context), ScreenUtils.getHeight(context))
            return RadarSweepGeometry(bounds, RadarSettings.isStartingFromTop())
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
        return if (RadarSettings.isStartingFromTop()) ANGLE_RIGHT else ANGLE_LEFT
    }

    private fun update() {
        val elapsedMs = getElapsedMs()
        when (currentStep) {
            RadarStep.ROTATING -> rotate(elapsedMs)
            RadarStep.MOVING -> moveCircle(elapsedMs)
            RadarStep.IDLE -> {}
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

        calculateTargetScreenPoint()
        updateRadarLine()
    }

    private fun crossedHorizontalBoundary(previousAngle: Float, nextAngle: Float): Float? {
        return if (RadarSettings.isStartingFromTop()) {
            if (rotationDirection == RotationDirection.CLOCKWISE) {
                if (nextAngle >= ANGLE_LEFT) ANGLE_LEFT else null
            } else {
                if (nextAngle > previousAngle) ANGLE_RIGHT else null
            }
        } else {
            if (rotationDirection == RotationDirection.CLOCKWISE) {
                if (nextAngle < previousAngle) ANGLE_RIGHT else null
            } else {
                if (previousAngle > ANGLE_LEFT && nextAngle <= ANGLE_LEFT) ANGLE_LEFT else null
            }
        }
    }

    private fun moveCircle(elapsedMs: Long) {
        val geometry = geometry
        val maxDistance = geometry.maxDistance
        val movementStep = if (maxDistance > 0f) {
            RadarSettings.getLinearSpeedPxPerSecond(context, currentSlowDownFactor()) * elapsedMs / 1000f / maxDistance
        } else {
            0f
        }
        when (circleMovement) {
            CircleMovement.OUTWARD -> {
                currentDistanceRatio += movementStep
                val point = currentPoint(geometry)
                val pivotX = geometry.pivotX
                val pivotY = geometry.pivotY

                if ((point.x >= targetScreenX && targetScreenX > pivotX) ||
                    (point.x <= targetScreenX && targetScreenX < pivotX) ||
                    (point.y >= targetScreenY && targetScreenY > pivotY) ||
                    (point.y <= targetScreenY && targetScreenY < pivotY)
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

    private fun currentPoint(geometry: RadarSweepGeometry = this.geometry): PointF {
        val angle = Math.toRadians(currentAngle.toDouble())
        val distance = currentDistanceRatio * geometry.maxDistance
        return PointF(
            geometry.pivotX + distance * cos(angle).toFloat(),
            geometry.pivotY + distance * sin(angle).toFloat()
        )
    }

    private fun updateRadarLine() {
        radarUI.showRadarLine(currentAngle, geometry)
    }

    private fun updateRadarCircle() {
        val point = currentPoint()
        radarUI.showRadarCircle(point.x.toInt(), point.y.toInt())
    }

    private fun calculateTargetScreenPoint() {
        val geometry = geometry
        val bounds = geometry.bounds
        val pivotX = geometry.pivotX
        val pivotY = geometry.pivotY
        val left = bounds.left.toFloat()
        val top = bounds.top.toFloat()
        val right = bounds.right.toFloat()
        val bottom = bounds.bottom.toFloat()
        val angle = Math.toRadians(currentAngle.toDouble())
        val cos = cos(angle).toFloat()
        val sin = sin(angle).toFloat()

        val intersections = mutableListOf<Pair<Float, Float>>()

        if (cos < 0) {
            val t = (left - pivotX) / cos
            val y = pivotY + t * sin
            if (y >= top && y <= bottom) {
                intersections.add(Pair(left, y))
            }
        }

        if (cos > 0) {
            val t = (right - pivotX) / cos
            val y = pivotY + t * sin
            if (y >= top && y <= bottom) {
                intersections.add(Pair(right, y))
            }
        }

        if (sin < 0) {
            val t = (top - pivotY) / sin
            val x = pivotX + t * cos
            if (x >= left && x <= right) {
                intersections.add(Pair(x, top))
            }
        }

        if (sin > 0) {
            val t = (bottom - pivotY) / sin
            val x = pivotX + t * cos
            if (x >= left && x <= right) {
                intersections.add(Pair(x, bottom))
            }
        }

        val closest = intersections.minByOrNull {
            val dx = it.first - pivotX
            val dy = it.second - pivotY
            sqrt(dx * dx + dy * dy)
        }
        if (closest != null) {
            targetScreenX = closest.first
            targetScreenY = closest.second
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
            RadarStep.IDLE -> {}
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
            RadarStep.IDLE -> {}
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
        if (scanSettings.isAutoScanMode()) {
            scanningScheduler?.pauseScanning()
        }
    }

    override fun resumeAutoScanning() {
        if (scanSettings.isAutoScanMode()) {
            resetElapsedTime()
            scanningScheduler?.resumeScanning()
        }
    }

    override fun performSelectionAction() {
        setup()
        if (isSetupRequired()) return

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
                        slowDownState = SlowDownState.SLOWED_DOWN
                        resetElapsedTime()
                    }

                    SlowDownState.SLOWED_DOWN -> {
                        slowDownState = SlowDownState.NORMAL_SPEED
                        beginCircleMovement()
                    }
                }
            }

            RadarStep.MOVING -> {
                when (slowDownState) {
                    SlowDownState.NORMAL_SPEED -> {
                        slowDownState = SlowDownState.SLOWED_DOWN
                        resetElapsedTime()
                    }

                    SlowDownState.SLOWED_DOWN -> {
                        slowDownState = SlowDownState.NORMAL_SPEED
                        selectCurrentPoint()
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
                beginCircleMovement()
                startAutoScanIfEnabled()
            }

            RadarStep.MOVING -> selectCurrentPoint()

            RadarStep.IDLE -> startRadar()
        }
    }

    private fun beginCircleMovement() {
        currentStep = RadarStep.MOVING
        resetElapsedTime()
        currentDistanceRatio =
            if (circleMovement == CircleMovement.OUTWARD) INITIAL_DISTANCE_RATIO else MAX_DISTANCE_RATIO
        updateRadarCircle()
    }

    private fun selectCurrentPoint() {
        val point = currentPoint()
        scanningScheduler?.stopScanning()
        onPointSelected(point)
    }

    override fun resetUI() {
        radarUI.reset()
    }

    override fun resetForNextUse() {
        scanningScheduler?.stopScanning()
        radarUI.reset()
        currentBlock = null
        currentStep = RadarStep.IDLE
        currentAngle = getStartingAngle()
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
            RadarStep.IDLE -> {}
        }
    }

    override fun stepScanningUp() {
    }

    override fun stepScanningDown() {
    }

    override fun stepScanningLeft() {
        stepScanningBackward()
    }

    override fun stepScanningRight() {
        stepScanningForward()
    }

    fun setBlock(block: ScanBlock?) {
        currentBlock = block
        currentStep = RadarStep.IDLE
        currentAngle = getStartingAngle()
        currentDistanceRatio = INITIAL_DISTANCE_RATIO
        rotationDirection = RotationDirection.CLOCKWISE
        circleMovement = CircleMovement.OUTWARD
        slowDownState = SlowDownState.NORMAL_SPEED
        radarUI.setBlock(block)
    }

    fun getCurrentBlock(): ScanBlock? = currentBlock

    internal fun isAutoScanning(): Boolean = scanningScheduler?.isScanning() == true
}
