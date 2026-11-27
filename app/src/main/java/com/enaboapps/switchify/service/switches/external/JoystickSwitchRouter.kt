package com.enaboapps.switchify.service.switches.external

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.annotation.RequiresApi
import com.enaboapps.switchify.service.switches.SwitchEventProvider
import com.enaboapps.switchify.switches.JoystickDirection
import com.enaboapps.switchify.switches.JoystickDirectionDetector
import com.enaboapps.switchify.switches.JoystickSwitchCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class JoystickSwitchRouter(
    private val service: AccessibilityService,
    private val switchEventProvider: SwitchEventProvider,
    private val externalSwitchListener: ExternalSwitchListener
) {
    companion object {
        private const val TAG = "JoystickSwitchRouter"
    }

    private val detector = JoystickDirectionDetector()
    private var intercepting = false
    private var job: Job? = null
    private val inputManager = service.getSystemService(Context.INPUT_SERVICE) as? InputManager
    private val inputDeviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit

        override fun onInputDeviceChanged(deviceId: Int) = Unit

        override fun onInputDeviceRemoved(deviceId: Int) {
            detector.releaseDevice(deviceId, SystemClock.uptimeMillis())
                .forEach { dispatch(it, cancelled = true) }
        }
    }

    fun start(scope: CoroutineScope) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        inputManager?.registerInputDeviceListener(inputDeviceListener, Handler(Looper.getMainLooper()))
        job = scope.launch(Dispatchers.Main) {
            combine(
                switchEventProvider.switchesChanged,
                JoystickSwitchCapture.activeCaptures
            ) { _, captures ->
                captures == 0 && hasJoystickSwitches()
            }.collect { setIntercepting(it) }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        inputManager?.unregisterInputDeviceListener(inputDeviceListener)
        releaseHeldDirections()
        intercepting = false
    }

    fun onMotionEvent(event: MotionEvent) {
        if (!intercepting) return
        detector.process(event).forEach { dispatch(it, cancelled = false) }
    }

    private fun hasJoystickSwitches(): Boolean =
        switchEventProvider.externalSwitches().any { JoystickDirection.fromCode(it.code) != null }

    private fun setIntercepting(enabled: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || intercepting == enabled) return
        if (!enabled) releaseHeldDirections()
        if (applyMotionEventSources(if (enabled) InputDevice.SOURCE_JOYSTICK else 0)) {
            intercepting = enabled
            Log.d(TAG, "Joystick interception ${if (enabled) "enabled" else "disabled"}")
        }
    }

    private fun releaseHeldDirections() {
        detector.releaseAll(SystemClock.uptimeMillis()).forEach { dispatch(it, cancelled = true) }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun applyMotionEventSources(sources: Int): Boolean {
        val info = service.serviceInfo ?: return false
        return try {
            info.motionEventSources = sources
            service.serviceInfo = info
            true
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Unable to update motion event sources", e)
            false
        }
    }

    private fun dispatch(edge: JoystickDirectionDetector.Edge, cancelled: Boolean) {
        val code = edge.direction.code
        if (edge.pressed) {
            externalSwitchListener.onSwitchPressed(code, edge.downTimeMs, edge.eventTimeMs)
        } else {
            externalSwitchListener.onSwitchReleased(code, edge.downTimeMs, edge.eventTimeMs, cancelled)
        }
    }
}
