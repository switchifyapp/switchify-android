package com.enaboapps.switchify.service.techniques.cursor

import android.content.Context
import com.enaboapps.switchify.service.gestures.GestureManager
import com.enaboapps.switchify.service.gestures.GesturePoint
import com.enaboapps.switchify.service.gestures.GesturePointListener
import com.enaboapps.switchify.service.techniques.shared.ScanMethodUIConstants
import com.enaboapps.switchify.service.scanning.ScanDirection
import com.enaboapps.switchify.service.techniques.AccessTechniqueInterface
import com.enaboapps.switchify.service.scanning.ScanSettings
import com.enaboapps.switchify.service.scanning.ScanningScheduler
import com.enaboapps.switchify.service.selection.SelectionHandler

/**
 * CursorManager class manages the cursor movement, quadrants, and scanning for the Switchify accessibility service.
 *
 * @param context The application context.
 */
class CursorManager(private val context: Context) : AccessTechniqueInterface, GesturePointListener {

    companion object {
        private const val TAG = "CursorManager"
        private const val MIN_QUADRANT_INDEX = 0
    }

    private val cursorLineMovement = 10
    private val scanSettings = ScanSettings(context)
    private val cursorUI = CursorUI(context)

    private var isInQuadrant = false
    private var quadrantInfo: QuadrantInfo? = null
    private var direction: ScanDirection = ScanDirection.RIGHT
    private var scanningScheduler: ScanningScheduler? = null

    /**
     * Sets up the cursor manager if not already set up.
     */
    private fun setup() {
        if (isSetupRequired()) {
            GesturePoint.listener = this
            scanningScheduler = ScanningScheduler(context) { move() }
        }
        CursorMode.init(context)
    }

    /**
     * Checks if setup is required.
     * @return True if setup is required, false otherwise.
     */
    private fun isSetupRequired(): Boolean = scanningScheduler == null

    /**
     * Handles gesture point reselection.
     */
    override fun onGesturePointReselect() {
        GesturePoint.y = 0
        quadrantInfo = GesturePoint.lastXQuadrant
        GesturePoint.x = quadrantInfo?.start ?: 0
        isInQuadrant = true
        cursorUI.showXCursorLine(GesturePoint.x)
        startAutoScanIfEnabled()
    }

    /**
     * Sets the quadrant information.
     * @param quadrantIndex The index of the quadrant.
     * @param start The start position of the quadrant.
     * @param end The end position of the quadrant.
     */
    private fun setQuadrantInfo(quadrantIndex: Int, start: Int, end: Int) {
        quadrantInfo = QuadrantInfo(quadrantIndex, start, end)
        when (direction) {
            ScanDirection.LEFT, ScanDirection.RIGHT -> GesturePoint.lastXQuadrant = quadrantInfo!!
            ScanDirection.UP, ScanDirection.DOWN -> GesturePoint.lastYQuadrant = quadrantInfo!!
        }
    }

    /**
     * Sets up the Y quadrant.
     */
    private fun setupYQuadrant() {
        GesturePoint.y = CursorBounds.Y_MIN
        cursorUI.showYQuadrant(0)
        setQuadrantInfo(0, GesturePoint.y, GesturePoint.y + cursorUI.getQuadrantHeight())
    }

    /**
     * Updates the Y quadrant.
     * @param quadrantIndex The index of the quadrant to update.
     */
    private fun updateYQuadrant(quadrantIndex: Int) {
        GesturePoint.y = CursorBounds.Y_MIN + (quadrantIndex * cursorUI.getQuadrantHeight())
        cursorUI.showYQuadrant(quadrantIndex)
        setQuadrantInfo(
            quadrantIndex,
            GesturePoint.y,
            GesturePoint.y + cursorUI.getQuadrantHeight()
        )
    }

    /**
     * Sets up the X quadrant.
     */
    private fun setupXQuadrant() {
        GesturePoint.x = CursorBounds.X_MIN
        cursorUI.showXQuadrant(0)
        setQuadrantInfo(0, GesturePoint.x, GesturePoint.x + cursorUI.getQuadrantWidth())
    }

    /**
     * Updates the X quadrant.
     * @param quadrantIndex The index of the quadrant to update.
     */
    private fun updateXQuadrant(quadrantIndex: Int) {
        GesturePoint.x = quadrantIndex * cursorUI.getQuadrantWidth()
        cursorUI.showXQuadrant(quadrantIndex)
        setQuadrantInfo(quadrantIndex, GesturePoint.x, GesturePoint.x + cursorUI.getQuadrantWidth())
    }

    /**
     * Sets up the Y cursor line.
     */
    private fun setupYCursorLine() {
        cursorUI.showYCursorLine(GesturePoint.y)
    }

    /**
     * Updates the Y cursor line.
     */
    private fun updateYCursorLine() {
        val y = GesturePoint.y.coerceIn(
            quadrantInfo?.start ?: 0,
            (quadrantInfo?.end ?: 0) - ScanMethodUIConstants.LINE_THICKNESS
        )
        GesturePoint.y = y
        cursorUI.showYCursorLine(y)
    }

    /**
     * Sets up the X cursor line.
     */
    private fun setupXCursorLine() {
        cursorUI.showXCursorLine(GesturePoint.x)
    }

    /**
     * Updates the X cursor line.
     */
    private fun updateXCursorLine() {
        val x = GesturePoint.x.coerceIn(
            quadrantInfo?.start ?: 0,
            (quadrantInfo?.end ?: 0) - ScanMethodUIConstants.LINE_THICKNESS
        )
        GesturePoint.x = x
        cursorUI.showXCursorLine(x)
    }

    /**
     * Starts auto-scanning if enabled.
     */
    private fun startAutoScanIfEnabled() {
        if (scanSettings.isAutoScanMode()) {
            val rate = if (isInQuadrant || CursorMode.isSingleMode()) {
                scanSettings.getFineCursorScanRate()
            } else {
                scanSettings.getCursorBlockScanRate()
            }
            scanningScheduler?.startScanning(rate, rate)
        }
    }

    /**
     * Gets the maximum quadrant index.
     * @return The maximum quadrant index.
     */
    private fun getMaxQuadrantIndex(): Int = CursorUI.getNumberOfQuadrants() - 1

    /**
     * Swaps the scanning direction.
     */
    override fun swapScanDirection() {
        if (isSetupRequired()) return // Failsafe in case setup was not successful

        direction = when (direction) {
            ScanDirection.LEFT -> ScanDirection.RIGHT
            ScanDirection.RIGHT -> ScanDirection.LEFT
            ScanDirection.UP -> ScanDirection.DOWN
            ScanDirection.DOWN -> ScanDirection.UP
        }

        if (!isInQuadrant) {
            quadrantInfo?.let {
                when (direction) {
                    ScanDirection.LEFT, ScanDirection.RIGHT -> {
                        if (it.quadrantIndex == MIN_QUADRANT_INDEX) updateXQuadrant(
                            getMaxQuadrantIndex()
                        )
                        else if (it.quadrantIndex == getMaxQuadrantIndex()) updateXQuadrant(
                            MIN_QUADRANT_INDEX
                        )
                    }

                    ScanDirection.UP, ScanDirection.DOWN -> {
                        if (it.quadrantIndex == MIN_QUADRANT_INDEX) updateYQuadrant(
                            getMaxQuadrantIndex()
                        )
                        else if (it.quadrantIndex == getMaxQuadrantIndex()) updateYQuadrant(
                            MIN_QUADRANT_INDEX
                        )
                    }
                }
            }
        }

        resumeScanning()
    }

    /**
     * Starts the scanning process.
     */
    override fun startScanning() {
        setup()
        startAutoScanIfEnabled()
    }

    /**
     * Stops the scanning process.
     */
    override fun stopScanning() {
        scanningScheduler?.stopScanning()
    }

    /**
     * Pauses the scanning process.
     */
    override fun pauseScanning() {
        scanningScheduler?.pauseScanning()
    }

    /**
     * Resumes the scanning process.
     */
    override fun resumeScanning() {
        scanningScheduler?.resumeScanning()
    }

    /**
     * Moves the cursor.
     */
    private fun move() {
        if (isInQuadrant) moveCursorLine() else moveToNextQuadrant()
    }

    /**
     * Moves to the next quadrant.
     */
    private fun moveToNextQuadrant() {
        quadrantInfo?.let {
            when (direction) {
                ScanDirection.LEFT -> {
                    if (it.quadrantIndex > MIN_QUADRANT_INDEX) updateXQuadrant(it.quadrantIndex - 1)
                    else {
                        direction = ScanDirection.RIGHT
                        moveToNextQuadrant()
                    }
                }

                ScanDirection.RIGHT -> {
                    if (it.quadrantIndex < getMaxQuadrantIndex()) updateXQuadrant(it.quadrantIndex + 1)
                    else {
                        direction = ScanDirection.LEFT
                        moveToNextQuadrant()
                    }
                }

                ScanDirection.UP -> {
                    if (it.quadrantIndex > MIN_QUADRANT_INDEX) updateYQuadrant(it.quadrantIndex - 1)
                    else {
                        direction = ScanDirection.DOWN
                        moveToNextQuadrant()
                    }
                }

                ScanDirection.DOWN -> {
                    if (it.quadrantIndex < getMaxQuadrantIndex()) updateYQuadrant(it.quadrantIndex + 1)
                    else {
                        direction = ScanDirection.UP
                        moveToNextQuadrant()
                    }
                }
            }
        }
    }

    /**
     * Moves the cursor line.
     */
    private fun moveCursorLine() {
        quadrantInfo?.let {
            when (direction) {
                ScanDirection.LEFT -> {
                    if (GesturePoint.x > (it.start + ScanMethodUIConstants.LINE_THICKNESS)) {
                        GesturePoint.x -= cursorLineMovement
                        updateXCursorLine()
                    } else {
                        direction = ScanDirection.RIGHT
                        moveCursorLine()
                    }
                }

                ScanDirection.RIGHT -> {
                    if (GesturePoint.x < (it.end - ScanMethodUIConstants.LINE_THICKNESS)) {
                        GesturePoint.x += cursorLineMovement
                        updateXCursorLine()
                    } else {
                        direction = ScanDirection.LEFT
                        moveCursorLine()
                    }
                }

                ScanDirection.UP -> {
                    if (GesturePoint.y > (it.start + ScanMethodUIConstants.LINE_THICKNESS)) {
                        GesturePoint.y -= cursorLineMovement
                        updateYCursorLine()
                    } else {
                        direction = ScanDirection.DOWN
                        moveCursorLine()
                    }
                }

                ScanDirection.DOWN -> {
                    if (GesturePoint.y < (it.end - ScanMethodUIConstants.LINE_THICKNESS)) {
                        GesturePoint.y += cursorLineMovement
                        updateYCursorLine()
                    } else {
                        direction = ScanDirection.UP
                        moveCursorLine()
                    }
                }
            }
        }
    }

    /**
     * Resets the cursor.
     */
    fun reset() {
        stopScanning()
        isInQuadrant = false
        quadrantInfo = null
        direction = ScanDirection.RIGHT
        cursorUI.reset()
    }

    /**
     * Checks if the cursor is reset.
     * @return True if the cursor is reset, false otherwise.
     */
    private fun isReset(): Boolean = cursorUI.isReset()

    /**
     * Resets the quadrants.
     */
    private fun resetQuadrants() {
        cursorUI.removeXQuadrant()
        cursorUI.removeYQuadrant()
    }

    /**
     * Moves the cursor to the next item.
     */
    override fun stepForward() {
        if (isReset()) {
            determineCursorModeSetup()
            startAutoScanIfEnabled()
            return
        }

        direction = when (direction) {
            ScanDirection.LEFT, ScanDirection.RIGHT -> ScanDirection.RIGHT
            ScanDirection.UP, ScanDirection.DOWN -> ScanDirection.DOWN
        }

        move()
    }

    /**
     * Moves the cursor to the previous item.
     */
    override fun stepBackward() {
        if (isReset()) {
            determineCursorModeSetup()
            startAutoScanIfEnabled()
            return
        }

        direction = when (direction) {
            ScanDirection.LEFT, ScanDirection.RIGHT -> ScanDirection.LEFT
            ScanDirection.UP, ScanDirection.DOWN -> ScanDirection.UP
        }

        move()
    }

    /**
     * Performs the selection action.
     */
    override fun performSelectionAction() {
        if (scanSettings.isAutoScanMode()) {
            setup()
            stopScanning()

            if (isSetupRequired()) return // Failsafe in case setup was not successful
        }

        if (isReset()) {
            determineCursorModeSetup()
            startAutoScanIfEnabled()
            return
        }

        when (direction) {
            ScanDirection.LEFT, ScanDirection.RIGHT -> {
                if (!isInQuadrant && CursorMode.isBlockMode()) {
                    isInQuadrant = true
                    direction = ScanDirection.RIGHT
                    resetQuadrants()
                    if (!cursorUI.isXCursorLineVisible()) {
                        setupXCursorLine()
                    }
                } else {
                    direction = ScanDirection.DOWN
                    if (CursorMode.isBlockMode()) {
                        isInQuadrant = false
                        if (!cursorUI.isYQuadrantVisible()) {
                            setupYQuadrant()
                        }
                    } else {
                        setQuadrantInfo(0, CursorBounds.Y_MIN, CursorBounds.height(context))
                        GesturePoint.y = CursorBounds.Y_MIN
                        if (!cursorUI.isYCursorLineVisible()) {
                            setupYCursorLine()
                        }
                    }
                }
                startAutoScanIfEnabled()
            }

            ScanDirection.UP, ScanDirection.DOWN -> {
                if (!isInQuadrant && CursorMode.isBlockMode()) {
                    isInQuadrant = true
                    direction = ScanDirection.DOWN
                    resetQuadrants()
                    if (!cursorUI.isYCursorLineVisible()) {
                        setupYCursorLine()
                    }
                    startAutoScanIfEnabled()
                } else {
                    isInQuadrant = false
                    performFinalAction()
                }
            }
        }
    }

    /**
     * Determines the cursor mode setup based on the current cursor mode.
     */
    private fun determineCursorModeSetup() {
        if (CursorMode.isBlockMode()) {
            setupXQuadrant()
        } else {
            isInQuadrant = true
            setQuadrantInfo(0, CursorBounds.X_MIN, CursorBounds.width(context))
            GesturePoint.x = CursorBounds.X_MIN
            setupXCursorLine()
        }
    }

    /**
     * Performs the final action.
     */
    private fun performFinalAction() {
        reset()
        SelectionHandler.setSelectAction { performTapAction() }
        SelectionHandler.setStartScanningAction { performSelectionAction() }
        SelectionHandler.performSelectionAction()
    }

    /**
     * Performs the tap action.
     */
    private fun performTapAction() {
        GestureManager.getInstance().performTap()
    }

    /**
     * Cleans up the cursor manager.
     */
    override fun cleanup() {
        reset()
        scanningScheduler?.shutdown()
        scanningScheduler = null
    }
}