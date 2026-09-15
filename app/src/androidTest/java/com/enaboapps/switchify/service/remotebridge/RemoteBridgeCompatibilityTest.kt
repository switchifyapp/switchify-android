package com.enaboapps.switchify.service.remotebridge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.enaboapps.switchify.remotebridge.ISwitchifyRemoteBridge
import com.enaboapps.switchify.remotebridge.ISwitchifyRemoteBridgeCallback
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class RemoteBridgeCompatibilityTest {
    @Test fun bridgeStillBindsForwardsAndCancelsRepeat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val connected = CountDownLatch(1)
        lateinit var bridge: ISwitchifyRemoteBridge
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                bridge = ISwitchifyRemoteBridge.Stub.asInterface(binder)
                connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        val edges = mutableListOf<Pair<Long, Boolean>>()
        var stopped = 0L
        var available = false
        val callback = object : ISwitchifyRemoteBridgeCallback.Stub() {
            override fun onSnapshot(snapshot: Bundle) { available = snapshot.getBoolean("captureAvailable") }
            override fun onRepeatStopRequested(generation: Long) { stopped = generation }
            override fun onSwitchEdge(generation: Long, sequence: Long, keyCode: Int, down: Boolean, downTimeMs: Long, eventTimeMs: Long, cancelled: Boolean) {
                edges += sequence to down
            }
        }
        SwitchifyRemoteBridgeCoordinator.resetForTests()
        SwitchifyRemoteBridgeCoordinator.attach { listOf(62 to "Space") }
        assertTrue(context.bindService(Intent(context, SwitchifyRemoteBridgeService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue(connected.await(10, TimeUnit.SECONDS))
            assertEquals(1, bridge.version)
            bridge.registerCallback(callback)
            assertTrue(available)
            assertTrue(bridge.setForwardingActive(100, true))
            assertFalse(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(99, true, 1, 1, false))
            assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(62, true, 2, 2, false))
            assertTrue(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(62, false, 2, 3, false))
            assertEquals(listOf(1L to true, 2L to false), edges)
            assertTrue(bridge.setForwardingActive(100, false))
            assertFalse(SwitchifyRemoteBridgeCoordinator.forwardExternalEdge(62, true, 4, 4, false))
            assertTrue(bridge.setRepeatActive(101, true))
            assertTrue(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
            assertEquals(101L, stopped)
            assertFalse(SwitchifyRemoteBridgeCoordinator.stopRemoteRepeatForSwitch())
            bridge.unregisterCallback(callback)
        } finally {
            context.unbindService(connection)
            SwitchifyRemoteBridgeCoordinator.detach()
            SwitchifyRemoteBridgeCoordinator.resetForTests()
        }
    }
}
