package com.enaboapps.switchify.pc.storage

import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcPlatform
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PcSavedPc(
    val desktopId: String,
    val displayName: String,
    val platform: PcPlatform?,
    val peripheralId: String,
    val lastConnectedAt: Long
)

interface PcKeyValueStore {
    suspend fun get(key: String): String?
    suspend fun put(key: String, value: String)
    suspend fun remove(key: String)
    suspend fun clear()

    suspend fun resetForPairingIfUnusable(): Boolean = false
}

interface PcPairingStorage {
    suspend fun deviceId(): String
    suspend fun deviceIdForPairing(): String
    suspend fun list(): List<PcSavedPc>
    suspend fun token(desktopId: String): String?
    suspend fun save(pc: PcSavedPc, token: String)
    suspend fun remove(desktopId: String)
    suspend fun defaultDesktopId(): String?
    suspend fun setDefaultDesktopId(desktopId: String?)
}

class PcPairingStore(
    private val publicStore: PcKeyValueStore,
    private val secretStore: PcKeyValueStore,
    private val deviceIds: PcIdGenerator = PcIdGenerator { "android-${UUID.randomUUID()}" }
) : PcPairingStorage {
    private val mutex = Mutex()

    override suspend fun deviceId(): String = mutex.withLock { readOrCreateDeviceId() }

    override suspend fun deviceIdForPairing(): String = mutex.withLock {
        try {
            readOrCreateDeviceId()
        } catch (_: PcSecureStorageUnavailableException) {
            secretStore.resetForPairingIfUnusable()
            readOrCreateDeviceId()
        }
    }

    private suspend fun readOrCreateDeviceId(): String =
        secretStore.get(DEVICE_ID_KEY)?.takeIf { it.isNotEmpty() } ?: deviceIds.nextId().also {
            secretStore.put(DEVICE_ID_KEY, it)
        }

    override suspend fun list(): List<PcSavedPc> = try {
        mutex.withLock { reconcileIndex() }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        emptyList()
    }

    override suspend fun token(desktopId: String): String? = secretStore.get(tokenKey(desktopId))?.takeIf { it.isNotEmpty() }

    override suspend fun save(pc: PcSavedPc, token: String): Unit = mutex.withLock {
        val next = listOf(pc) + readIndex().filter { it.desktopId != pc.desktopId }
        val previousToken = token(pc.desktopId)
        secretStore.put(tokenKey(pc.desktopId), token)
        try {
            publicStore.put(INDEX_KEY, encode(next))
        } catch (error: Exception) {
            if (previousToken != null) secretStore.put(tokenKey(pc.desktopId), previousToken)
            else secretStore.remove(tokenKey(pc.desktopId))
            throw error
        }
    }

    override suspend fun remove(desktopId: String): Unit = mutex.withLock {
        val previous = readIndex()
        val previousToken = token(desktopId)
        val previousDefault = defaultDesktopId()
        val next = previous.filter { it.desktopId != desktopId }
        secretStore.remove(tokenKey(desktopId))
        try {
            publicStore.put(INDEX_KEY, encode(next))
            if (previousDefault == desktopId) publicStore.remove(DEFAULT_KEY)
        } catch (error: Exception) {
            val indexRestored = attempt { publicStore.put(INDEX_KEY, encode(previous)) }
            if (indexRestored && previousDefault != null) attempt { publicStore.put(DEFAULT_KEY, previousDefault) }
            if (indexRestored && previousToken != null) attempt { secretStore.put(tokenKey(desktopId), previousToken) }
            throw error
        }
    }

    override suspend fun defaultDesktopId(): String? = publicStore.get(DEFAULT_KEY)?.takeIf { it.isNotEmpty() }

    override suspend fun setDefaultDesktopId(desktopId: String?): Unit = mutex.withLock {
        if (desktopId != null) publicStore.put(DEFAULT_KEY, desktopId) else publicStore.remove(DEFAULT_KEY)
    }

    private suspend fun readIndex(): List<PcSavedPc> {
        val raw = publicStore.get(INDEX_KEY) ?: return emptyList()
        val parsed = JSONArray(raw)
        return (0 until parsed.length())
            .mapNotNull { decode(parsed.opt(it)) }
            .sortedByDescending { it.lastConnectedAt }
    }

    private suspend fun reconcileIndex(): List<PcSavedPc> {
        val previous = readIndex()
        val valid = previous.filter { token(it.desktopId) != null }
        if (valid.size == previous.size) return valid
        val previousDefault = defaultDesktopId()
        val defaultMissing = previousDefault != null && valid.none { it.desktopId == previousDefault }
        try {
            publicStore.put(INDEX_KEY, encode(valid))
            if (defaultMissing) publicStore.remove(DEFAULT_KEY)
        } catch (error: Exception) {
            attempt { publicStore.put(INDEX_KEY, encode(previous)) }
            if (previousDefault != null) attempt { publicStore.put(DEFAULT_KEY, previousDefault) }
            throw error
        }
        return valid
    }

    private suspend fun attempt(action: suspend () -> Unit): Boolean = try {
        action()
        true
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }

    companion object {
        const val INDEX_KEY = "switchify.pc.pairings.v1"
        const val DEFAULT_KEY = "$INDEX_KEY.default"
        const val DEVICE_ID_KEY = "switchify.pc.device-id.v1"
        private const val TOKEN_PREFIX = "switchify.pc.token."

        fun tokenKey(desktopId: String) = "$TOKEN_PREFIX$desktopId"

        fun encode(pcs: List<PcSavedPc>): String = JSONArray().apply {
            pcs.forEach { pc ->
                put(
                    JSONObject()
                        .put("desktopId", pc.desktopId)
                        .put("displayName", pc.displayName)
                        .put("platform", pc.platform?.protocolValue ?: JSONObject.NULL)
                        .put("peripheralId", pc.peripheralId)
                        .put("lastConnectedAt", pc.lastConnectedAt)
                )
            }
        }.toString()

        private fun decode(value: Any?): PcSavedPc? {
            val item = value as? JSONObject ?: return null
            val desktopId = item.opt("desktopId") as? String ?: return null
            val displayName = item.opt("displayName") as? String ?: return null
            val peripheralId = item.opt("peripheralId") as? String ?: return null
            val lastConnectedAt = (item.opt("lastConnectedAt") as? Number)?.toLong() ?: return null
            if (!item.has("platform")) return null
            val rawPlatform = item.opt("platform")
            val platform = if (rawPlatform == JSONObject.NULL) null else PcPlatform.fromProtocol(rawPlatform) ?: return null
            return PcSavedPc(desktopId, displayName, platform, peripheralId, lastConnectedAt)
        }
    }
}
