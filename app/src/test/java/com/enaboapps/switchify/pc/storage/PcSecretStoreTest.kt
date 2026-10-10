package com.enaboapps.switchify.pc.storage

import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcPlatform
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.InvalidKeyException
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class PcSecretStoreTest {
    private class PermanentlyInvalidated : InvalidKeyException("fixture permanent invalidation")

    private class FakeKeySource : PcSecretKeySource {
        var key: SecretKey? = null
        var failure: Exception? = null
        var transientFailuresRemaining = 0
        var rejectedKeyAttempts = 0
        var created = 0
        var deleted = 0

        override fun existingKey(): SecretKey? {
            failure?.let { throw it }
            if (transientFailuresRemaining > 0) {
                transientFailuresRemaining -= 1
                throw KeyStoreException("busy")
            }
            if (rejectedKeyAttempts > 0 && key != null) {
                rejectedKeyAttempts -= 1
                return SecretKeySpec(ByteArray(7), "AES")
            }
            return key
        }

        override fun createKey(): SecretKey {
            failure?.let { throw it }
            created += 1
            return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        }

        override fun deleteKey() {
            deleted += 1
            failure = null
            key = null
        }
    }

    private val keys = FakeKeySource()
    private val backing = InMemoryPcKeyValueStore()
    private val classifier = PcKeyFailureClassifier { error ->
        PcSecretCipher.causes(error).any { it is PermanentlyInvalidated }
    }
    private val secrets = EncryptedPcKeyValueStore(backing, PcSecretCipher(keys, classifier), retryDelayMs = 10)
    private val publicStore = InMemoryPcKeyValueStore()
    private var nextDevice = 0
    private val store = PcPairingStore(publicStore, secrets, PcIdGenerator { "device-${++nextDevice}" })
    private val office = PcSavedPc("pc-1", "Office", PcPlatform.Windows, "ble-1", 1)

    private fun transientDaemonError() =
        UnrecoverableKeyException("Failed to load information about key").apply { initCause(KeyStoreException("system error")) }

    private suspend fun pairedStore(): Map<String, String> {
        assertEquals("device-1", store.deviceId())
        store.save(office, "fixture-secret")
        store.setDefaultDesktopId("pc-1")
        return backing.values.toMap()
    }

    @Test
    fun encryptsValuesAndBindsCiphertextToTheKeyName() = runTest {
        secrets.put("token.a", "fixture-secret")
        assertEquals("fixture-secret", secrets.get("token.a"))
        val stored = backing.values.getValue("token.a")
        assertFalse(stored.contains("fixture-secret"))
        assertTrue(Base64.getDecoder().decode(stored).size >= PcSecretCipher.MIN_BLOB_BYTES)
        backing.values["token.b"] = stored
        assertNull(secrets.get("token.b"))
        assertEquals(1, keys.created)
    }

    @Test
    fun treatsCorruptBlobsAsPermanentlyMissing() = runTest {
        secrets.put("token.a", "fixture-secret")
        val bytes = Base64.getDecoder().decode(backing.values.getValue("token.a"))
        bytes[bytes.size - 1] = (bytes.last().toInt() xor 1).toByte()
        backing.values["token.a"] = Base64.getEncoder().encodeToString(bytes)
        assertNull(secrets.get("token.a"))
        backing.values["token.a"] = "not base64 !"
        assertNull(secrets.get("token.a"))
        backing.values["token.a"] = Base64.getEncoder().encodeToString(ByteArray(PcSecretCipher.MIN_BLOB_BYTES - 1))
        assertNull(secrets.get("token.a"))
    }

    @Test
    fun aMissingAliasIsUnavailableOnReadAndAWriteOnlyCreatesANewKey() = runTest {
        secrets.put("token.a", "fixture-secret")
        secrets.put("device", "device-1")
        val stored = backing.values.toMap()
        val alias = keys.key
        keys.key = null
        expectUnavailable { secrets.get("token.a") }
        expectUnavailable { secrets.get("device") }
        assertEquals(stored, backing.values)

        keys.key = alias
        assertEquals("fixture-secret", secrets.get("token.a"))

        keys.key = null
        secrets.put("token.a", "new-secret")
        assertEquals(setOf("token.a", "device"), backing.values.keys)
        assertEquals(stored.getValue("device"), backing.values.getValue("device"))
        assertEquals("new-secret", secrets.get("token.a"))
        assertNull(secrets.get("device"))
        assertEquals(2, keys.created)
        assertEquals(0, keys.deleted)
    }

    @Test
    fun aMissingAliasOnReadKeepsSavedPcsAndTheDeviceId() = runTest {
        val stored = pairedStore()
        val index = publicStore.values.toMap()
        val alias = keys.key
        keys.key = null
        expectUnavailable { store.deviceId() }
        expectUnavailable { store.token("pc-1") }
        assertEquals(emptyList<PcSavedPc>(), store.list())
        assertEquals(index, publicStore.values)
        assertEquals(stored, backing.values)
        keys.key = alias
        assertEquals("device-1", store.deviceId())
        assertEquals(listOf(office), store.list())
    }

    @Test
    fun pairingRetriesThreeTimesASecondApartBeforeResetting() = runTest {
        val patientSecrets = EncryptedPcKeyValueStore(backing, PcSecretCipher(keys, classifier))
        val patientStore = PcPairingStore(publicStore, patientSecrets, PcIdGenerator { "device-${++nextDevice}" })
        assertEquals("device-1", patientStore.deviceId())
        patientStore.save(office, "fixture-secret")
        keys.transientFailuresRemaining = 3
        val start = currentTime
        assertEquals("device-1", patientStore.deviceIdForPairing())
        assertEquals(2 * EncryptedPcKeyValueStore.PAIRING_RETRY_DELAY_MS, currentTime - start)
        assertEquals(1_000L, EncryptedPcKeyValueStore.PAIRING_RETRY_DELAY_MS)
        assertEquals(3, EncryptedPcKeyValueStore.PAIRING_VERIFY_ATTEMPTS)
        assertEquals(0, keys.deleted)
        assertEquals("fixture-secret", patientStore.token("pc-1"))
    }

    @Test
    fun pairingResetsAnAliasThatStaysMissing() = runTest {
        pairedStore()
        keys.key = null
        val pairingDeviceId = store.deviceIdForPairing()
        assertNotEquals("device-1", pairingDeviceId)
        assertNull(store.token("pc-1"))
        assertEquals(emptyList<PcSavedPc>(), store.list())
    }

    @Test
    fun transientDaemonErrorsLoseNoPairingDefaultOrDeviceId() = runTest {
        val stored = pairedStore()
        val index = publicStore.values.toMap()
        listOf(transientDaemonError(), KeyStoreException("busy"), ProviderException("busy"), InvalidKeyException("Keystore operation failed"))
            .forEach { failure ->
                keys.failure = failure
                expectUnavailable { secrets.get(PcPairingStore.tokenKey("pc-1")) }
                expectUnavailable { store.token("pc-1") }
                expectUnavailable { store.deviceId() }
                expectUnavailable { store.save(office.copy(desktopId = "pc-2"), "other") }
                assertEquals(emptyList<PcSavedPc>(), store.list())
                store.setDefaultDesktopId("pc-1")
                assertEquals(index, publicStore.values)
                assertEquals(stored, backing.values)
            }
        assertEquals(0, keys.deleted)
        keys.failure = null
        assertEquals("device-1", store.deviceId())
        assertEquals(listOf(office), store.list())
        assertEquals("pc-1", store.defaultDesktopId())
        assertEquals("fixture-secret", store.token("pc-1"))
    }

    @Test
    fun aSingleRejectedCipherInitIsTransient() = runTest {
        val stored = pairedStore()
        keys.rejectedKeyAttempts = 1
        expectUnavailable { store.token("pc-1") }
        assertEquals("fixture-secret", store.token("pc-1"))
        assertEquals(stored, backing.values)
        assertEquals(0, keys.deleted)
    }

    @Test
    fun pairingRetriesATransientFailureWithoutResettingWhenItRecovers() = runTest {
        pairedStore()
        keys.rejectedKeyAttempts = 1
        assertEquals("device-1", store.deviceIdForPairing())
        assertEquals(0, keys.deleted)
        assertEquals("fixture-secret", store.token("pc-1"))
    }

    @Test
    fun onlyPairingResetsAPermanentlyInvalidatedKey() = runTest {
        pairedStore()
        keys.failure = InvalidKeyException("wrapped").apply { initCause(PermanentlyInvalidated()) }
        expectUnavailable { store.deviceId() }
        expectUnavailable { store.token("pc-1") }
        expectUnavailable { store.save(office.copy(desktopId = "pc-2"), "other") }
        store.setDefaultDesktopId("pc-1")
        assertEquals(0, keys.deleted)

        val pairingDeviceId = store.deviceIdForPairing()
        assertEquals(1, keys.deleted)
        assertNotEquals("device-1", pairingDeviceId)
        assertEquals(pairingDeviceId, store.deviceId())
        assertNull(store.token("pc-1"))
        assertEquals(emptyList<PcSavedPc>(), store.list())
    }

    @Test
    fun pairingKeepsAKeyThatStaysTransientlyUnavailableAfterRetrying() = runTest {
        val stored = pairedStore()
        keys.failure = transientDaemonError()
        expectUnavailable { store.deviceIdForPairing() }
        assertEquals(0, keys.deleted)
        assertEquals(stored, backing.values)
        keys.failure = null
        assertEquals("device-1", store.deviceIdForPairing())
        assertEquals("fixture-secret", store.token("pc-1"))
    }

    private suspend fun expectUnavailable(action: suspend () -> Unit) {
        try {
            action()
        } catch (_: PcSecureStorageUnavailableException) {
            return
        }
        fail("Expected secure storage to be unavailable.")
    }
}
