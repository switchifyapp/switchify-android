package com.enaboapps.switchify.service.scanning

import android.content.Context
import android.util.Log
import com.enaboapps.switchify.service.techniques.AccessTechnique
import com.enaboapps.switchify.service.techniques.directcontrol.DirectControlSettings

/**
 * This class manages the move repeat functionality in the scanning process.
 * It provides methods to set the previous and next actions, and starts the scanning process.
 *
 * @property context The application context.
 */
class MoveRepeatManager(private val context: Context) {
    companion object {
        private const val TAG = "MoveRepeatManager"
    }

    private var scanningScheduler: ScanningScheduler? = null

    private var previousAction: (() -> Unit)? = null
    private var nextAction: (() -> Unit)? = null

    private val scanSettings = ScanSettings(context)

    fun setup() {
        if (scanningScheduler == null) {
            scanningScheduler = ScanningScheduler(context, onScan = {
                move()
            })
        }
    }

    fun setPreviousAction(action: () -> Unit) {
        previousAction = action
        nextAction = null
    }

    fun setNextAction(action: () -> Unit) {
        nextAction = action
        previousAction = null
    }

    fun start(): Boolean {
        Log.d(TAG, "start")
        setup()
        if (!isRunning()) {
            val delay = if (AccessTechnique.getCurrentTechnique() == AccessTechnique.Technique.DIRECT_CONTROL) {
                DirectControlSettings(context).repeatDelay()
            } else {
                scanSettings.getMoveRepeatDelay()
            }
            scanningScheduler?.startScanning(0, delay)
            return true
        }
        return false
    }

    fun stop(): Boolean {
        if (isRunning()) {
            scanningScheduler?.stopScanning()
            scanningScheduler = null
            return true
        }
        return false
    }

    fun isRunning(): Boolean {
        Log.d(TAG, "isRunning: ${scanningScheduler?.isScanning()}")
        return scanningScheduler?.isScanning() == true
    }

    private fun move() {
        if (previousAction != null && nextAction != null) {
            println("Move Repeat: Both actions are set")
            return
        }

        if (previousAction != null) {
            previousAction!!.invoke()
        }

        if (nextAction != null) {
            nextAction!!.invoke()
        }
    }
}
