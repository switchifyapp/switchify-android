package com.enaboapps.switchify.screens.pc

import com.enaboapps.switchify.pc.connection.LoopbackTransport
import com.enaboapps.switchify.pc.connection.PcConnectionManager
import com.enaboapps.switchify.pc.connection.PcConnectionState
import com.enaboapps.switchify.pc.connection.PcDiagnosticLog
import com.enaboapps.switchify.pc.connection.PcList.toDesktop
import com.enaboapps.switchify.pc.connection.PcListItem
import com.enaboapps.switchify.pc.connection.PcPermissionRequester
import com.enaboapps.switchify.pc.connection.savedOffice
import com.enaboapps.switchify.pc.protocol.PcClock
import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.storage.InMemoryPcKeyValueStore
import com.enaboapps.switchify.pc.storage.PcPairingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcConnectionViewModelTest {
    private class Fixture(test: TestScope) {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(test.testScheduler))
        val transport = LoopbackTransport(scope)
        val storage = PcPairingStore(InMemoryPcKeyValueStore(), InMemoryPcKeyValueStore(), PcIdGenerator { "device-1" })
        var granted = false
        val permissions = PcPermissionRequester { granted }
        val manager = PcConnectionManager(
            transport = transport,
            storage = storage,
            diagnostics = PcDiagnosticLog(PcClock { 0 }),
            requestPermission = permissions::request,
            reconnectDelay = {},
            scope = scope
        )
        val viewModel = PcConnectionViewModel(manager, permissions)
        val savedItem = PcListItem(savedOffice().toDesktop(), savedOffice(), nearby = false)
    }

    private fun viewModelTest(block: suspend TestScope.(Fixture) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val fixture = Fixture(this)
        try {
            block(fixture)
        } finally {
            fixture.scope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun retriesTheSavedPcInsteadOfScanningAfterPermissionIsGrantedInSettings() = viewModelTest { f ->
        f.storage.save(savedOffice(), "saved-secret")
        f.viewModel.connect(f.savedItem)
        runCurrent()
        assertTrue(f.permissions.requested.value)
        f.viewModel.onPermissionResult(false)
        runCurrent()
        assertTrue(f.manager.state.value is PcConnectionState.PermissionDenied)

        f.granted = true
        f.viewModel.onResume()
        runCurrent()
        assertTrue(f.manager.state.value is PcConnectionState.Connected)
        assertNull(f.transport.onScanDesktop)
    }

    @Test
    fun aLostPermissionResultDoesNotBlockTheNextPrompt() = viewModelTest { f ->
        f.viewModel.scan()
        runCurrent()
        assertTrue(f.permissions.requested.value)

        f.viewModel.onResume()
        runCurrent()
        assertFalse(f.permissions.requested.value)
        assertTrue(f.manager.state.value is PcConnectionState.PermissionDenied)

        f.viewModel.scan()
        runCurrent()
        assertTrue(f.permissions.requested.value)
        f.granted = true
        f.viewModel.onPermissionResult(true)
        runCurrent()
        assertTrue(f.manager.state.value is PcConnectionState.Scanning)
    }
}
