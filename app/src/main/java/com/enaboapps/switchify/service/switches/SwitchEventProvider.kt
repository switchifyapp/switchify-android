package com.enaboapps.switchify.service.switches

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.enaboapps.switchify.R
import com.enaboapps.switchify.service.core.ServiceBridge
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventLocalStorage
import com.enaboapps.switchify.switches.SwitchEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
                reload()
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
            delay(5000)
            val loadedEvents = localStorage.loadFromFile(context)
            synchronized(switchEvents) {
                switchEvents.clear()
                switchEvents.addAll(loadedEvents)
            }
            checkCameraSwitchAvailability()
            Log.d(TAG, "Loaded ${switchEvents.size} switches")
            switchEvents.forEach { event ->
                event.log()
            }
            if (switchEvents.isEmpty()) {
                ServiceMessageHUD.instance.showMessage(
                    R.string.hud_no_switches_found,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.LONG
                )
            } else {
                ServiceMessageHUD.instance.showMessage(
                    R.string.hud_switches_loaded,
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.LONG
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
        hasCameraSwitch = switchEvents.any {
            it.type == SWITCH_EVENT_TYPE_CAMERA
        }
        Log.d(TAG, "Camera switch availability changed: $hasCameraSwitch")
        notifyCameraSwitchListeners()
    }

    private fun notifyCameraSwitchListeners() {
        cameraSwitchListeners.forEach { listener ->
            listener.onCameraSwitchAvailabilityChanged(hasCameraSwitch)
        }
    }

    fun reload() {
        coroutineScope.launch {
            loadInitialEvents()
        }
    }

    interface CameraSwitchListener {
        fun onCameraSwitchAvailabilityChanged(available: Boolean)
    }
}
