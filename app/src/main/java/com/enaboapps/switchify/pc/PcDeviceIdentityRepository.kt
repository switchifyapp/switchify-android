package com.enaboapps.switchify.pc

import android.content.Context
import android.os.Build
import com.enaboapps.switchify.backend.preferences.PreferenceManager

interface PcDeviceIdentity {
    fun getDeviceId(): String
    fun getDeviceName(): String
}

class PcDeviceIdentityRepository(context: Context) : PcDeviceIdentity {
    private val preferenceManager = PreferenceManager(context.applicationContext)

    override fun getDeviceId(): String = preferenceManager.getOrCreateDeviceId()

    override fun getDeviceName(): String {
        val name = listOf(Build.MANUFACTURER, Build.MODEL)
            .joinToString(" ")
            .replace("\\s+".toRegex(), " ")
            .trim()
        return name.ifBlank { "Android phone" }
    }
}
