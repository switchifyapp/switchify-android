package com.enaboapps.switchify.pc

import android.content.Context
import androidx.core.content.edit

interface PcPairingTokenStore {
    fun getToken(desktopId: String): String?
    fun saveToken(desktopId: String, token: String, lastUrl: String, serviceName: String? = null)
    fun clearToken(desktopId: String)
    fun getLastUrl(desktopId: String): String?
    fun getServiceName(desktopId: String): String?
}

class PcTokenStore(context: Context) : PcPairingTokenStore {
    private val preferences = context
        .applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences("switchify_pc_pairings", Context.MODE_PRIVATE)

    override fun getToken(desktopId: String): String? {
        return preferences.getString(tokenKey(desktopId), null)?.takeIf { it.isNotBlank() }
    }

    override fun saveToken(desktopId: String, token: String, lastUrl: String, serviceName: String?) {
        preferences.edit {
            putString(tokenKey(desktopId), token)
            putString(lastUrlKey(desktopId), lastUrl)
            if (!serviceName.isNullOrBlank()) putString(serviceNameKey(desktopId), serviceName)
        }
    }

    override fun clearToken(desktopId: String) {
        preferences.edit {
            remove(tokenKey(desktopId))
            remove(lastUrlKey(desktopId))
            remove(serviceNameKey(desktopId))
        }
    }

    override fun getLastUrl(desktopId: String): String? {
        return preferences.getString(lastUrlKey(desktopId), null)?.takeIf { it.isNotBlank() }
    }

    override fun getServiceName(desktopId: String): String? {
        return preferences.getString(serviceNameKey(desktopId), null)?.takeIf { it.isNotBlank() }
    }

    private fun tokenKey(desktopId: String) = "token:$desktopId"
    private fun lastUrlKey(desktopId: String) = "last_url:$desktopId"
    private fun serviceNameKey(desktopId: String) = "service_name:$desktopId"
}
