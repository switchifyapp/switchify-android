package com.enaboapps.switchify.pc.storage

import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcPlatform
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.security.KeyStoreException
import java.security.ProviderException
import java.security.UnrecoverableKeyException
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

class PcSecretStoreTest {
    private class FakeKeySource : PcSecretKeySource {
        var key: SecretKey? = null
        var failure: Exception? = null
        var created = 0
        var deleted = 0

        override fun existingKey(): SecretKey? {
            failure?.let { throw it }
            return key
        }

        override fun createKey(): SecretKey {
            failure?.let { throw it }
            created += 1
            return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it }
        }

        override fun deleteKey() {
            failure?.takeUnless { it is UnrecoverableKeyException }?.let { throw it }
            deleted += 1
            failure = null
            key = null
        }
    }

    private val keys = FakeKeySource()
    private val backing = InMemoryPcKeyValueStore()
    private val secrets = EncryptedPcKeyValueStore(backing, PcSecretCipher(keys))

    @Test
    fun treatsAnUnrecoverableKeyAsPermanentAndRegeneratesItOnTheNextPairing() = runTest {
        secrets.put("token.a", "fixture-secret")
        keys.failure = UnrecoverableKeyException("corrupted after update")
        assertNull(secrets.get("token.a"))
        assertEquals(0, keys.deleted)
        secrets.put("token.b", "new-secret")
        assertEquals(1, keys.deleted)
        assertEquals(setOf("token.b"), backing.values.keys)
        assertEquals("new-secret", secrets.get("token.b"))
        assertEquals(2, keys.created)
    }

    @Test
    fun treatsAKeyTheCipherRejectsAsPermanentAndRegeneratesItOnTheNextPairing() = runTest {
        secrets.put("token.a", "fixture-secret")
        keys.key = SecretKeySpec(ByteArray(7), "AES")
        assertNull(secrets.get("token.a"))
        secrets.put("token.b", "new-secret")
        assertEquals(1, keys.deleted)
        assertEquals(setOf("token.b"), backing.values.keys)
        assertEquals("new-secret", secrets.get("token.b"))
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
    fun reportsTransientKeystoreFailuresWithoutTouchingStoredSecrets() = runTest {
        secrets.put("token.a", "fixture-secret")
        val stored = backing.values.toMap()
        listOf(KeyStoreException("busy"), ProviderException("busy")).forEach { failure ->
            keys.failure = failure
            expectUnavailable { secrets.get("token.a") }
            expectUnavailable { secrets.put("token.b", "other") }
        }
        assertEquals(stored, backing.values)
        keys.failure = null
        assertEquals("fixture-secret", secrets.get("token.a"))
    }

    @Test
    fun clearsSecretsWhenTheKeyIsGoneSoTheUserPairsAgain() = runTest {
        secrets.put("token.a", "fixture-secret")
        secrets.put("device", "device-1")
        keys.key = null
        assertNull(secrets.get("token.a"))
        assertTrue(backing.values.isEmpty())
        secrets.put("token.a", "new-secret")
        assertEquals("new-secret", secrets.get("token.a"))
    }

    @Test
    fun pairingStoreKeepsPairingsAndDeviceIdWhileSecureStorageIsUnavailable() = runTest {
        val publicStore = InMemoryPcKeyValueStore()
        var created = 0
        val store = PcPairingStore(publicStore, secrets, PcIdGenerator { "device-${++created}" })
        val pc = PcSavedPc("pc-1", "Office", PcPlatform.Windows, "ble-1", 1)
        assertEquals("device-1", store.deviceId())
        store.save(pc, "fixture-secret")
        val index = publicStore.values.toMap()
        val stored = backing.values.toMap()

        keys.failure = ProviderException("busy")
        expectUnavailable { store.deviceId() }
        expectUnavailable { store.token("pc-1") }
        assertEquals(emptyList<PcSavedPc>(), store.list())
        assertEquals(index, publicStore.values)
        assertEquals(stored, backing.values)

        keys.failure = null
        assertEquals("device-1", store.deviceId())
        assertEquals(listOf(pc), store.list())
        assertEquals("fixture-secret", store.token("pc-1"))
        assertEquals(1, created)
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
