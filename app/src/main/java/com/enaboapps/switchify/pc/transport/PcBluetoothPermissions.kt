package com.enaboapps.switchify.pc.transport

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PcBluetoothPermissions {
    @SuppressLint("InlinedApi")
    fun required(sdkInt: Int = Build.VERSION.SDK_INT): List<String> =
        if (sdkInt >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun missing(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): List<String> = required(sdkInt).filter {
        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
    }

    fun hasAll(context: Context): Boolean = missing(context).isEmpty()

    fun allGranted(results: Map<String, Boolean>, sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        required(sdkInt).all { results[it] == true }
}
