package com.enaboapps.switchify.pc.storage

import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcPlatform
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InMemoryPcKeyValueStore : PcKeyValueStore {
    val values = linkedMapOf<String, String>()
    var failGet: (String) -> Boolean = { false }
    var failPut: (String, String) -> Boolean = { _, _ -> false }
    var failRemove: (String) -> Boolean = { false }
    val log = mutableListOf<String>()

    override suspend fun get(key: String): String? {
        yield()
        if (failGet(key)) throw IllegalStateException("fixture read failed")
        return values[key]
    }

    override suspend fun put(key: String, value: String) {
        yield()
        log += "put:$key"
        if (failPut(key, value)) throw IllegalStateException("fixture write failed")
        values[key] = value
    }

    override suspend fun remove(key: String) {
        yield()
        log += "remove:$key"
        if (failRemove(key)) throw IllegalStateException("fixture remove failed")
        values.remove(key)
    }

    override suspend fun clear() {
        yield()
        log += "clear"
        values.clear()
    }
}

class PcPairingStoreTest {
    private val publicStore = InMemoryPcKeyValueStore()
    private val secretStore = InMemoryPcKeyValueStore()
    private var nextDevice = 0
    private val store = PcPairingStore(publicStore, secretStore, PcIdGenerator { "android-device-${++nextDevice}" })

    private fun pc(id: String, lastConnectedAt: Long = 1, platform: PcPlatform? = PcPlatform.Windows) =
        PcSavedPc(id, "PC $id", platform, "ble-$id", lastConnectedAt)

    private val tokenOne = PcPairingStore.tokenKey("pc-1")

    @Test
    fun createsOnePerInstallDeviceIdInSecretStorage() = runTest {
        val ids = (1..3).map { async { store.deviceId() } }.awaitAll()
        assertEquals(listOf("android-device-1", "android-device-1", "android-device-1"), ids)
        assertEquals("android-device-1", secretStore.values[PcPairingStore.DEVICE_ID_KEY])
        assertFalse(publicStore.values.containsKey(PcPairingStore.DEVICE_ID_KEY))
    }

    @Test
    fun keepsTokensOutOfThePublicIndex() = runTest {
        store.save(pc("pc-1"), "fixture-secret")
        assertEquals("fixture-secret", store.token("pc-1"))
        assertFalse(publicStore.values.values.any { it.contains("fixture-secret") })
        assertEquals(listOf(pc("pc-1")), store.list())
    }

    @Test
    fun ordersSavedPcsByMostRecentConnectionAndReplacesExistingEntries() = runTest {
        store.save(pc("pc-1", lastConnectedAt = 10), "a")
        store.save(pc("pc-2", lastConnectedAt = 30), "b")
        store.save(pc("pc-1", lastConnectedAt = 20).copy(displayName = "Renamed"), "c")
        assertEquals(listOf("pc-2", "pc-1"), store.list().map { it.desktopId })
        assertEquals("Renamed", store.list().last().displayName)
        assertEquals("c", store.token("pc-1"))
    }

    @Test
    fun persistsMacsWithoutPlatformAsExplicitNull() = runTest {
        store.save(pc("pc-1", platform = null), "a")
        val stored = JSONArray(publicStore.values[PcPairingStore.INDEX_KEY]).getJSONObject(0)
        assertTrue(stored.has("platform"))
        assertTrue(stored.isNull("platform"))
        assertEquals(listOf(pc("pc-1", platform = null)), store.list())
    }

    @Test
    fun rollsBackATokenUpdateWhenSavingThePublicIndexFails() = runTest {
        store.save(pc("pc-1"), "old")
        publicStore.failPut = { key, _ -> key == PcPairingStore.INDEX_KEY }
        expectFailure { store.save(pc("pc-1", lastConnectedAt = 2), "new") }
        assertEquals("old", store.token("pc-1"))
    }

    @Test
    fun removesANewTokenWhenSavingThePublicIndexFails() = runTest {
        publicStore.failPut = { key, _ -> key == PcPairingStore.INDEX_KEY }
        expectFailure { store.save(pc("pc-1"), "new") }
        assertNull(store.token("pc-1"))
    }

    @Test
    fun deletesTheSecretBeforePublishingAnUnpairedIndex() = runTest {
        store.save(pc("pc-1"), "secret")
        secretStore.log.clear()
        publicStore.log.clear()
        val order = mutableListOf<String>()
        secretStore.failRemove = { order += "secret"; false }
        publicStore.failPut = { _, _ -> order += "public"; false }
        store.remove("pc-1")
        assertEquals(listOf("secret", "public"), order)
        assertEquals(emptyList<PcSavedPc>(), store.list())
    }

    @Test
    fun leavesPublicStateIntactWhenSecretDeletionFails() = runTest {
        store.save(pc("pc-1"), "secret")
        secretStore.failRemove = { true }
        expectFailure { store.remove("pc-1") }
        assertEquals(listOf(pc("pc-1")), store.list())
        assertEquals("secret", store.token("pc-1"))
    }

    @Test
    fun restoresTheTokenAndIndexWhenPublicIndexPersistenceFails() = runTest {
        store.save(pc("pc-1"), "secret")
        var failed = false
        publicStore.failPut = { key, _ -> (key == PcPairingStore.INDEX_KEY && !failed).also { if (it) failed = true } }
        expectFailure { store.remove("pc-1") }
        assertEquals("secret", store.token("pc-1"))
        assertEquals(listOf(pc("pc-1")), store.list())
    }

    @Test
    fun rollsBackTheRemovalWhenClearingTheDefaultFails() = runTest {
        store.save(pc("pc-1"), "secret")
        store.setDefaultDesktopId("pc-1")
        publicStore.failRemove = { it == PcPairingStore.DEFAULT_KEY }
        expectFailure { store.remove("pc-1") }
        assertEquals("pc-1", store.defaultDesktopId())
        assertEquals("secret", store.token("pc-1"))
        assertEquals(listOf(pc("pc-1")), store.list())
    }

    @Test
    fun doesNotMutateAPairingWhenThePublicIndexCannotBeRead() = runTest {
        store.save(pc("pc-1"), "secret")
        publicStore.failGet = { it == PcPairingStore.INDEX_KEY }
        expectFailure { store.save(pc("pc-2"), "other") }
        expectFailure { store.remove("pc-1") }
        assertEquals("secret", store.token("pc-1"))
        assertNull(store.token("pc-2"))
    }

    @Test
    fun doesNotOverwriteAMalformedPairingIndexDuringAMutation() = runTest {
        publicStore.values[PcPairingStore.INDEX_KEY] = "{not json"
        expectFailure { store.save(pc("pc-1"), "secret") }
        assertEquals("{not json", publicStore.values[PcPairingStore.INDEX_KEY])
        assertEquals(emptyList<PcSavedPc>(), store.list())
    }

    @Test
    fun ignoresMalformedEntriesAndUnknownPlatforms() = runTest {
        secretStore.values[PcPairingStore.tokenKey("pc-1")] = "a"
        publicStore.values[PcPairingStore.INDEX_KEY] = """[
            {"desktopId":"pc-1","displayName":"One","platform":"windows","peripheralId":"ble-1","lastConnectedAt":1},
            {"desktopId":"pc-2","displayName":"Two","platform":"linux","peripheralId":"ble-2","lastConnectedAt":1},
            {"desktopId":"pc-3","displayName":"Three","peripheralId":"ble-3","lastConnectedAt":1},
            "broken"
        ]"""
        assertEquals(listOf("pc-1"), store.list().map { it.desktopId })
    }

    @Test
    fun removesConfirmedOrphanedMetadataAndItsDefaultWithoutDeletingOtherPairings() = runTest {
        store.save(pc("pc-1", lastConnectedAt = 1), "a")
        store.save(pc("pc-2", lastConnectedAt = 2), "b")
        store.setDefaultDesktopId("pc-1")
        secretStore.values.remove(tokenOne)
        assertEquals(listOf("pc-2"), store.list().map { it.desktopId })
        assertNull(store.defaultDesktopId())
        assertEquals("b", store.token("pc-2"))
        assertEquals(1, JSONArray(publicStore.values[PcPairingStore.INDEX_KEY]).length())
    }

    @Test
    fun doesNotReconcileOrDeleteMetadataWhenSecureTokenReadsFail() = runTest {
        store.save(pc("pc-1"), "a")
        secretStore.failGet = { true }
        assertEquals(emptyList<PcSavedPc>(), store.list())
        secretStore.failGet = { false }
        assertEquals(listOf(pc("pc-1")), store.list())
    }

    @Test
    fun rollsBackOrphanReconciliationWhenTheDefaultCannotBeCleared() = runTest {
        store.save(pc("pc-1", lastConnectedAt = 1), "a")
        store.save(pc("pc-2", lastConnectedAt = 2), "b")
        store.setDefaultDesktopId("pc-1")
        secretStore.values.remove(tokenOne)
        publicStore.failRemove = { it == PcPairingStore.DEFAULT_KEY }
        assertEquals(emptyList<PcSavedPc>(), store.list())
        assertEquals("pc-1", store.defaultDesktopId())
        assertEquals(2, JSONArray(publicStore.values[PcPairingStore.INDEX_KEY]).length())
    }

    @Test
    fun keepsBothPcsWhenSavesRunConcurrently() = runTest {
        listOf(async { store.save(pc("pc-1", 1), "a") }, async { store.save(pc("pc-2", 2), "b") }).awaitAll()
        assertEquals(setOf("pc-1", "pc-2"), store.list().map { it.desktopId }.toSet())
    }

    @Test
    fun setsAndClearsTheDefaultPc() = runTest {
        store.setDefaultDesktopId("pc-1")
        assertEquals("pc-1", store.defaultDesktopId())
        store.setDefaultDesktopId(null)
        assertNull(store.defaultDesktopId())
    }

    private suspend fun expectFailure(action: suspend () -> Unit) {
        try {
            action()
        } catch (_: IllegalStateException) {
            return
        } catch (_: org.json.JSONException) {
            return
        }
        fail("Expected the storage operation to fail.")
    }
}
