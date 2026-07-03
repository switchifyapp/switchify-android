package com.enaboapps.switchify.pc

import android.content.Context
import androidx.core.content.edit

interface PcPairingTokenStore {
    fun getToken(desktopId: String): String?
    fun saveToken(desktopId: String, token: String, lastEndpointId: String, serviceName: String? = null)
    fun clearToken(desktopId: String)
    fun listPairings(): List<PcStoredPairing>
    fun getLastEndpointId(desktopId: String): String?
    fun getServiceName(desktopId: String): String?
    fun getDefaultPcPreference(): PcDefaultPcPreference {
        return getDefaultDesktopId()?.let { PcDefaultPcPreference.SpecificPc(it) }
            ?: PcDefaultPcPreference.LastConnection
    }
    fun setDefaultPcPreference(preference: PcDefaultPcPreference) {
        when (preference) {
            PcDefaultPcPreference.LastConnection -> clearDefaultDesktopId()
            is PcDefaultPcPreference.SpecificPc -> setDefaultDesktopId(preference.desktopId)
        }
    }
    fun getLastConnectedDesktopId(): String? = null
    fun recordSuccessfulConnection(desktopId: String) = Unit
    fun getDefaultDesktopId(): String?
    fun setDefaultDesktopId(desktopId: String)
    fun clearDefaultDesktopId()
}

sealed class PcDefaultPcPreference {
    data object LastConnection : PcDefaultPcPreference()
    data class SpecificPc(val desktopId: String) : PcDefaultPcPreference()
}

data class PcStoredPairing(
    val desktopId: String,
    val serviceName: String?,
    val lastEndpointId: String?
)

/**
 * Stores pairing tokens in device-protected SharedPreferences so saved PCs
 * reconnect before the user unlocks the device. Tokens are stored in plain
 * text: they only grant control of a PC that the user explicitly approved,
 * and extracting them requires local/root access to this device. See the
 * threat model on [PcProtocol].
 */
class PcTokenStore(context: Context) : PcPairingTokenStore {
    private val preferences = context
        .applicationContext
        .createDeviceProtectedStorageContext()
        .getSharedPreferences("switchify_pc_pairings", Context.MODE_PRIVATE)

    override fun getToken(desktopId: String): String? {
        return preferences.getString(tokenKey(desktopId), null)?.takeIf { it.isNotBlank() }
    }

    override fun saveToken(desktopId: String, token: String, lastEndpointId: String, serviceName: String?) {
        preferences.edit {
            putStringSet(pairingIdsKey, pairingIds() + desktopId)
            putString(tokenKey(desktopId), token)
            putString(lastEndpointIdKey(desktopId), lastEndpointId)
            if (!serviceName.isNullOrBlank()) putString(serviceNameKey(desktopId), serviceName)
        }
    }

    override fun clearToken(desktopId: String) {
        preferences.edit {
            putStringSet(pairingIdsKey, indexedPairingIds() - desktopId)
            remove(tokenKey(desktopId))
            remove(lastEndpointIdKey(desktopId))
            remove(serviceNameKey(desktopId))
            if (preferences.getString(defaultDesktopIdKey, null) == desktopId) {
                remove(defaultDesktopIdKey)
                putString(defaultPreferenceModeKey, defaultPreferenceModeLastConnection)
            }
            if (preferences.getString(lastConnectedDesktopIdKey, null) == desktopId) {
                remove(lastConnectedDesktopIdKey)
            }
        }
    }

    override fun listPairings(): List<PcStoredPairing> {
        return pairingIds()
            .filter { getToken(it) != null }
            .map { desktopId ->
                PcStoredPairing(
                    desktopId = desktopId,
                    serviceName = getServiceName(desktopId),
                    lastEndpointId = getLastEndpointId(desktopId)
                )
            }
            .sortedBy { it.serviceName ?: it.desktopId }
    }

    override fun getLastEndpointId(desktopId: String): String? {
        return preferences.getString(lastEndpointIdKey(desktopId), null)?.takeIf { it.isNotBlank() }
    }

    override fun getServiceName(desktopId: String): String? {
        return preferences.getString(serviceNameKey(desktopId), null)?.takeIf { it.isNotBlank() }
    }

    override fun getDefaultPcPreference(): PcDefaultPcPreference {
        val mode = preferences.getString(defaultPreferenceModeKey, null)
        val desktopId = preferences.getString(defaultDesktopIdKey, null)?.takeIf { it.isNotBlank() }
        if (mode == null) {
            if (desktopId != null && getToken(desktopId) != null) {
                return PcDefaultPcPreference.SpecificPc(desktopId)
            }
            setDefaultPcPreference(PcDefaultPcPreference.LastConnection)
            return PcDefaultPcPreference.LastConnection
        }
        if (mode == defaultPreferenceModeSpecificPc) {
            if (desktopId != null && getToken(desktopId) != null) {
                return PcDefaultPcPreference.SpecificPc(desktopId)
            }
            setDefaultPcPreference(PcDefaultPcPreference.LastConnection)
            return PcDefaultPcPreference.LastConnection
        }
        return PcDefaultPcPreference.LastConnection
    }

    override fun setDefaultPcPreference(preference: PcDefaultPcPreference) {
        when (preference) {
            PcDefaultPcPreference.LastConnection -> preferences.edit {
                putString(defaultPreferenceModeKey, defaultPreferenceModeLastConnection)
                remove(defaultDesktopIdKey)
            }
            is PcDefaultPcPreference.SpecificPc -> {
                if (getToken(preference.desktopId) == null) return
                preferences.edit {
                    putString(defaultPreferenceModeKey, defaultPreferenceModeSpecificPc)
                    putString(defaultDesktopIdKey, preference.desktopId)
                }
            }
        }
    }

    override fun getLastConnectedDesktopId(): String? {
        val desktopId = preferences.getString(lastConnectedDesktopIdKey, null)?.takeIf { it.isNotBlank() } ?: return null
        if (getToken(desktopId) != null) return desktopId
        preferences.edit {
            remove(lastConnectedDesktopIdKey)
        }
        return null
    }

    override fun recordSuccessfulConnection(desktopId: String) {
        if (getToken(desktopId) == null) return
        preferences.edit {
            putString(lastConnectedDesktopIdKey, desktopId)
        }
    }

    override fun getDefaultDesktopId(): String? {
        return (getDefaultPcPreference() as? PcDefaultPcPreference.SpecificPc)?.desktopId
    }

    override fun setDefaultDesktopId(desktopId: String) {
        setDefaultPcPreference(PcDefaultPcPreference.SpecificPc(desktopId))
    }

    override fun clearDefaultDesktopId() {
        setDefaultPcPreference(PcDefaultPcPreference.LastConnection)
    }

    private fun tokenKey(desktopId: String) = "token:$desktopId"
    private fun lastEndpointIdKey(desktopId: String) = "last_endpoint_id:$desktopId"
    private fun serviceNameKey(desktopId: String) = "service_name:$desktopId"

    private fun pairingIds(): Set<String> {
        val tokenIds = preferences.all.keys.mapNotNull { key ->
            key.removePrefix(tokenPrefix).takeIf { key.startsWith(tokenPrefix) && it.isNotBlank() }
        }
        return indexedPairingIds() + tokenIds
    }

    private fun indexedPairingIds(): Set<String> {
        return preferences.getStringSet(pairingIdsKey, emptySet()).orEmpty()
    }

    private companion object {
        const val pairingIdsKey = "paired_desktop_ids"
        const val defaultPreferenceModeKey = "default_preference_mode"
        const val defaultDesktopIdKey = "default_desktop_id"
        const val lastConnectedDesktopIdKey = "last_connected_desktop_id"
        const val defaultPreferenceModeLastConnection = "last_connection"
        const val defaultPreferenceModeSpecificPc = "specific_pc"
        const val tokenPrefix = "token:"
    }
}
