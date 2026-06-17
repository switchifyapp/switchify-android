package com.enaboapps.switchify.pc.bluetooth

import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Test

class PcBleDiscoveryServiceTest {
    @Test
    fun constructingDiscoveryDoesNotResolveBluetoothManager() {
        var bluetoothLookups = 0

        PcBleDiscoveryService(
            context = ContextWrapper(null),
            bluetoothManagerProvider = {
                bluetoothLookups += 1
                error("Bluetooth should not be resolved during construction.")
            }
        )

        assertEquals(0, bluetoothLookups)
    }
}
