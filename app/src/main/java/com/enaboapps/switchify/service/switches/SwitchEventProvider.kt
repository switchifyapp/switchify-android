package com.enaboapps.switchify.service.switches

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.enaboapps.switchify.service.window.ServiceMessageHUD
import com.enaboapps.switchify.switches.*
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
        LocalBroadcastManager.getInstance(context.applicationContext).registerReceiver(
            receiver,
            IntentFilter(SwitchEventStore.EVENTS_UPDATED)
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
                    "No Switches Found",
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.LONG
                )
            } else {
                ServiceMessageHUD.instance.showMessage(
                    "Switches Loaded",
                    ServiceMessageHUD.MessageType.DISAPPEARING,
                    ServiceMessageHUD.Time.LONG
                )
            }
        }
    }

    fun findExternal(code: String): SwitchEvent? = synchronized(switchEvents) {
        switchEvents.find {
            it.code == code &&
                    it.type == SWITCH_EVENT_TYPE_EXTERNAL &&
                    it.isOnDevice
        }
    }

    fun findCamera(code: String): SwitchEvent? = synchronized(switchEvents) {
        switchEvents.find {
            it.code == code &&
                    it.type == SWITCH_EVENT_TYPE_CAMERA &&
                    it.isOnDevice
        }
    }

    fun addCameraSwitchListener(listener: CameraSwitchListener) {
        cameraSwitchListeners.add(listener)
    }

    fun removeCameraSwitchListener(listener: CameraSwitchListener) {
        cameraSwitchListeners.remove(listener)
    }

    fun isFacialGestureAssigned(gestureId: String): Boolean {
        return synchronized(switchEvents) {
            switchEvents.any {
                it.code == gestureId &&
                        it.type == SWITCH_EVENT_TYPE_CAMERA &&
                        it.isOnDevice
            }
        }
    }

    private fun checkCameraSwitchAvailability() {
        val hasCamera = synchronized(switchEvents) {
            switchEvents.any {
                it.type == SWITCH_EVENT_TYPE_CAMERA &&
                        it.isOnDevice
            }
        }

        if (hasCamera != hasCameraSwitch) {
            hasCameraSwitch = hasCamera
            notifyCameraSwitchListeners()
        }
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