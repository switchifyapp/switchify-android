package com.enaboapps.switchify.service.switches

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.window.MessageSeverity
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventLocalStorage
import com.enaboapps.switchify.switches.SwitchEventStore
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

class SwitchEventProvider(private val context: Context) {
    private val switchEvents = Collections.synchronizedSet(mutableSetOf<SwitchEvent>())
    private val localStorage = SwitchEventLocalStorage()
    private val cameraSwitchListeners = mutableSetOf<CameraSwitchListener>()
    private val mutex = Mutex()
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    var hasCameraSwitch = false
        private set

    companion object {
        private const val TAG = "SwitchEventProvider"
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SwitchEventStore.EVENTS_UPDATED) {
                reload("broadcast_events_updated")
                Log.d(TAG, "Switch events updated")
            }
        }
    }

    init {
        androidx.core.content.ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(SwitchEventStore.EVENTS_UPDATED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        coroutineScope.launch {
            loadInitialEvents()
        }
        Log.d(TAG, "Initialized SwitchEventProvider")
    }

    private suspend fun loadInitialEvents() {
        mutex.withLock {
            val loadedEvents = localStorage.loadFromFile(context)
            synchronized(switchEvents) {
                switchEvents.clear()
                switchEvents.addAll(loadedEvents)
            }
            checkCameraSwitchAvailability()
            val cameraSwitchCount = switchEvents.count { it.type == SWITCH_EVENT_TYPE_CAMERA }
            Log.d(TAG, "Loaded ${switchEvents.size} switches")
            Logger.log(
                if (switchEvents.isEmpty()) LogEvent.SwitchConfigEmpty else LogEvent.SwitchConfigLoaded,
                data = mapOf(
                    "result" to if (switchEvents.isEmpty()) "empty" else "success",
                    "total_switches" to switchEvents.size,
                    "camera_switches" to cameraSwitchCount,
                    "external_switches" to (switchEvents.size - cameraSwitchCount)
                )
            )
            switchEvents.forEach { event ->
                event.log()
            }
            if (switchEvents.isEmpty()) {
                ServiceMessageHUD.instance.showMessage(
                    R.string.hud_no_switches_found,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.LONG,
                    severity = MessageSeverity.Warning
                )
            } else {
                ServiceMessageHUD.instance.showMessage(
                    R.string.hud_switches_loaded,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.LONG,
                    severity = MessageSeverity.Success
                )
            }
            // Notify service/app that switches are ready so it can validate configuration
            ServiceBridge.emitEvent(ServiceBridge.ServiceEvent.SwitchEventsUpdated)
        }
    }

    fun findExternal(code: String): SwitchEvent? = synchronized(switchEvents) {
        switchEvents.find {
            it.code == code &&
                    it.type == SWITCH_EVENT_TYPE_EXTERNAL
        }
    }

    fun findCamera(code: String): SwitchEvent? = synchronized(switchEvents) {
        switchEvents.find {
            it.code == code &&
                    it.type == SWITCH_EVENT_TYPE_CAMERA
        }
    }

    fun addCameraSwitchListener(listener: CameraSwitchListener) {
        cameraSwitchListeners.add(listener)
    }

    fun removeCameraSwitchListener(listener: CameraSwitchListener) {
        cameraSwitchListeners.remove(listener)
    }

    fun isFacialGestureAssigned(gestureId: String): Boolean = switchEvents.any {
        it.code == gestureId &&
                it.type == SWITCH_EVENT_TYPE_CAMERA
    }

    private fun checkCameraSwitchAvailability() {
        val previous = hasCameraSwitch
        hasCameraSwitch = switchEvents.any {
            it.type == SWITCH_EVENT_TYPE_CAMERA
        }
        Log.d(TAG, "Camera switch availability changed: $hasCameraSwitch")
        if (previous != hasCameraSwitch) {
            Logger.log(
                LogEvent.CameraSwitchAvailabilityChanged,
                data = mapOf(
                    "result" to "success",
                    "available" to hasCameraSwitch,
                    "previous_available" to previous
                )
            )
        }
        notifyCameraSwitchListeners()
    }

    private fun notifyCameraSwitchListeners() {
        cameraSwitchListeners.forEach { listener ->
            listener.onCameraSwitchAvailabilityChanged(hasCameraSwitch)
        }
    }

    fun reload(source: String = "unknown") {
        Logger.log(
            LogEvent.SwitchReloadTriggered,
            data = mapOf(
                "result" to "started",
                "source" to source
            )
        )
        coroutineScope.launch {
            loadInitialEvents()
        }
    }

    interface CameraSwitchListener {
        fun onCameraSwitchAvailabilityChanged(available: Boolean)
    }
}
