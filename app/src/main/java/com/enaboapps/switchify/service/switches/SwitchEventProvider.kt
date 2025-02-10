package com.enaboapps.switchify.service.switches

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_CAMERA
import com.enaboapps.switchify.switches.SWITCH_EVENT_TYPE_EXTERNAL
import com.enaboapps.switchify.switches.SwitchEvent
import com.enaboapps.switchify.switches.SwitchEventStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SwitchEventProvider(context: Context) {
    private val store = SwitchEventStore.getInstance().apply {
        initialize(context.applicationContext, true)
    }
    private val cameraSwitchListeners = mutableSetOf<CameraSwitchListener>()
    var hasCameraSwitch = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SwitchEventStore.EVENTS_UPDATED) {
                context?.let {
                    store.reload(it)
                    checkCameraSwitchAvailability()
                    notifyCameraSwitchListeners()
                }
            }
        }
    }

    init {
        LocalBroadcastManager.getInstance(context.applicationContext).registerReceiver(
            receiver,
            IntentFilter(SwitchEventStore.EVENTS_UPDATED)
        )
        checkCameraSwitchAvailability()
    }

    fun findExternal(code: String): SwitchEvent? = store.find(code).takeIf {
        it?.type == SWITCH_EVENT_TYPE_EXTERNAL && it.isOnDevice
    }

    fun findCamera(code: String): SwitchEvent? = store.find(code).takeIf {
        it?.type == SWITCH_EVENT_TYPE_CAMERA && it.isOnDevice
    }

    fun addCameraSwitchListener(listener: CameraSwitchListener) {
        cameraSwitchListeners.add(listener)
    }

    fun removeCameraSwitchListener(listener: CameraSwitchListener) {
        cameraSwitchListeners.remove(listener)
    }

    fun isFacialGestureAssigned(gestureId: String): Boolean {
        return store.find(gestureId) != null
    }

    private fun checkCameraSwitchAvailability() {
        coroutineScope.launch {
            val hasCamera = store.getSwitchEvents()
                .any { it.type == SWITCH_EVENT_TYPE_CAMERA && it.isOnDevice }

            if (hasCamera != hasCameraSwitch) {
                hasCameraSwitch = hasCamera
            }
        }
    }

    private fun notifyCameraSwitchListeners() {
        cameraSwitchListeners.forEach { listener ->
            listener.onCameraSwitchAvailabilityChanged(hasCameraSwitch)
        }
    }

    interface CameraSwitchListener {
        fun onCameraSwitchAvailabilityChanged(available: Boolean)
    }
}