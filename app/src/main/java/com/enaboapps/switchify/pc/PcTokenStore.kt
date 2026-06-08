package com.enaboapps.switchify.pc

import android.content.Context
import androidx.core.content.edit

interface PcPairingTokenStore {
    fun getToken(desktopId: String): String?
    fun saveToken(desktopId: String, token: String, lastUrl: String, serviceName: String? = null)
    fun clearToken(desktopId: String)
    fun listPairings(): List<PcStoredPairing>
    fun getLastUrl(desktopId: String): String?
    fun getServiceName(desktopId: String): String?
}

data class PcStoredPairing(
    val desktopId: String,
    val serviceName: String?,
    val lastUrl: String?
)

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
            putStringSet(pairingIdsKey, pairingIds() + desktopId)
            putString(tokenKey(desktopId), token)
            putString(lastUrlKey(desktopId), lastUrl)
            if (!serviceName.isNullOrBlank()) putString(serviceNameKey(desktopId), serviceName)
        }
    }

    override fun clearToken(desktopId: String) {
        preferences.edit {
            putStringSet(pairingIdsKey, indexedPairingIds() - desktopId)
            remove(tokenKey(desktopId))
            remove(lastUrlKey(desktopId))
            remove(serviceNameKey(desktopId))
        }
    }

    override fun listPairings(): List<PcStoredPairing> {
        return pairingIds()
            .filter { getToken(it) != null }
            .map { desktopId ->
                PcStoredPairing(
                    desktopId = desktopId,
                    serviceName = getServiceName(desktopId),
                    lastUrl = getLastUrl(desktopId)
                )
            }
            .sortedBy { it.serviceName ?: it.desktopId }
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

    private fun pairingIds(): Set<String> {
        val legacyIds = preferences.all.keys.mapNotNull { key ->
            key.removePrefix(tokenPrefix).takeIf { key.startsWith(tokenPrefix) && it.isNotBlank() }
        }
        return indexedPairingIds() + legacyIds
    }

    private fun indexedPairingIds(): Set<String> {
        return preferences.getStringSet(pairingIdsKey, emptySet()).orEmpty()
    }

    private companion object {
        const val pairingIdsKey = "paired_desktop_ids"
        const val tokenPrefix = "token:"
    }
}
