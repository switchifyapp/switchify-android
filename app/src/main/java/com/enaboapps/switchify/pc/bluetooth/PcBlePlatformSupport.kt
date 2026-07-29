package com.enaboapps.switchify.pc.bluetooth

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

internal fun Context.hasBluetoothScanPermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
    } else {
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }
}

internal fun Context.hasBluetoothConnectPermission(): Boolean {
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
}

internal fun Context.isBluetoothEnabled(): Boolean {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return manager.adapter?.isEnabled == true
}
