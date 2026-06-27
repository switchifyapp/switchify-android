package com.enaboapps.switchify.service.core

import android.content.Context
import com.enaboapps.switchify.pc.PcMouseRepeatManager
import com.enaboapps.switchify.service.gestures.GestureLockManager
import com.enaboapps.switchify.service.gestures.GestureRepeatManager
import com.enaboapps.switchify.service.scanning.ScanningManager
import com.enaboapps.switchify.service.switches.external.ExternalSwitchListener
import com.enaboapps.switchify.service.utils.ScreenWatcher

class ScreenWatcherManager(
    private val context: Context
) {
    private var screenWatcher: ScreenWatcher? = null

    fun register(scanningManager: ScanningManager, externalSwitchListener: ExternalSwitchListener) {
        screenWatcher = ScreenWatcher(
            onScreenSleep = {
                val pauseManager = ServiceCore.getPauseManager()
                if (pauseManager.isPaused) pauseManager.resume()
                PcMouseRepeatManager.instance.clearServiceState()
                GestureRepeatManager.instance.clearServiceState()
                GestureLockManager.instance.clearServiceState()
                Tasks.getInstance().checkOngoingTasks()
                externalSwitchListener.reset()
                scanningManager.reset()
            },
            onOrientationChanged = {
                PcMouseRepeatManager.instance.clearServiceState()
                scanningManager.reset()
            }
        )
        screenWatcher?.register(context)
    }

    fun unregister() {
        screenWatcher?.unregister(context)
        screenWatcher = null
    }
}
