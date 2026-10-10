package com.enaboapps.switchify.pc.transport

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PcBluetoothPermissionsTest {
    @Test
    fun requestsNearbyDevicePermissionsOnAndroid12AndLater() {
        val expected = listOf("android.permission.BLUETOOTH_SCAN", "android.permission.BLUETOOTH_CONNECT")
        assertEquals(expected, PcBluetoothPermissions.required(31))
        assertEquals(expected, PcBluetoothPermissions.required(36))
    }

    @Test
    fun requestsLocationForLegacyBluetoothScanning() {
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), PcBluetoothPermissions.required(29))
        assertEquals(listOf("android.permission.ACCESS_FINE_LOCATION"), PcBluetoothPermissions.required(30))
    }

    @Test
    fun requiresEveryPermissionResultToBeGranted() {
        assertTrue(
            PcBluetoothPermissions.allGranted(
                mapOf("android.permission.BLUETOOTH_SCAN" to true, "android.permission.BLUETOOTH_CONNECT" to true),
                31
            )
        )
        assertFalse(PcBluetoothPermissions.allGranted(mapOf("android.permission.BLUETOOTH_SCAN" to true), 31))
    }
}
