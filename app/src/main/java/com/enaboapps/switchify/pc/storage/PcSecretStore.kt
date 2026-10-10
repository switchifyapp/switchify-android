package com.enaboapps.switchify.pc.storage

import kotlinx.coroutines.delay
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class PcSecureStorageUnavailableException(cause: Throwable? = null) :
    Exception("Secure PC storage is unavailable.", cause)

class PcSecretKeyInvalidatedException(cause: Throwable? = null) :
    Exception("The PC secret key was permanently invalidated.", cause)

interface PcSecretKeySource {
    fun existingKey(): SecretKey?
    fun createKey(): SecretKey
    fun deleteKey()
}

fun interface PcKeyFailureClassifier {
    fun isPermanent(error: Throwable): Boolean
}

enum class PcSecretKeyStatus {
    Present,
    Missing
}

class PcSecretCipher(
    private val keys: PcSecretKeySource,
    private val classifier: PcKeyFailureClassifier
) {
    fun keyStatus(): PcSecretKeyStatus =
        secure { if (keys.existingKey() != null) PcSecretKeyStatus.Present else PcSecretKeyStatus.Missing }

    fun deleteKey() = secure { keys.deleteKey() }

    fun verifyKey() {
        if (keyStatus() == PcSecretKeyStatus.Missing) return
        decrypt(PROBE_NAME, encrypt(PROBE_NAME, PROBE_NAME))
    }

    fun encrypt(name: String, value: String): String = secure {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.existingKey() ?: keys.createKey())
        cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
        val sealed = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        Base64.getEncoder().encodeToString(cipher.iv + sealed)
    }

    fun decrypt(name: String, stored: String): String? {
        val bytes = try {
            Base64.getDecoder().decode(stored)
        } catch (_: IllegalArgumentException) {
            return null
        }
        if (bytes.size < MIN_BLOB_BYTES) return null
        return secure {
            val key = keys.existingKey() ?: return@secure null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
            cipher.updateAAD(name.toByteArray(Charsets.UTF_8))
            try {
                String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
            } catch (_: AEADBadTagException) {
                null
            }
        }
    }

    private inline fun <T> secure(action: () -> T): T = try {
        action()
    } catch (error: GeneralSecurityException) {
        throw classify(error)
    } catch (error: ProviderException) {
        throw classify(error)
    } catch (error: IOException) {
        throw classify(error)
    }

    private fun classify(error: Throwable): Exception =
        if (classifier.isPermanent(error)) PcSecretKeyInvalidatedException(error) else PcSecureStorageUnavailableException(error)

    companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val MIN_BLOB_BYTES = IV_BYTES + TAG_BITS / 8
        private const val PROBE_NAME = "switchify.pc.key-probe"

        fun causes(error: Throwable): Sequence<Throwable> =
            generateSequence(error) { current -> current.cause?.takeIf { it !== current } }.take(MAX_CAUSE_DEPTH)

        private const val MAX_CAUSE_DEPTH = 8
    }
}

class EncryptedPcKeyValueStore(
    private val backing: PcKeyValueStore,
    private val cipher: PcSecretCipher,
    private val retryDelayMs: Long = PAIRING_RETRY_DELAY_MS
) : PcKeyValueStore {
    override suspend fun get(key: String): String? {
        val stored = backing.get(key) ?: return null
        return guarded {
            val value = cipher.decrypt(key, stored)
            if (value == null && cipher.keyStatus() == PcSecretKeyStatus.Missing) throw PcSecureStorageUnavailableException()
            value
        }
    }

    override suspend fun put(key: String, value: String) {
        val sealed = guarded { cipher.encrypt(key, value) }
        backing.put(key, sealed)
    }

    override suspend fun remove(key: String) = backing.remove(key)

    override suspend fun clear() = backing.clear()

    override suspend fun resetForPairingIfUnusable(): Boolean {
        repeat(PAIRING_VERIFY_ATTEMPTS) { attempt ->
            try {
                if (cipher.keyStatus() == PcSecretKeyStatus.Present) {
                    cipher.verifyKey()
                    return false
                }
            } catch (_: PcSecretKeyInvalidatedException) {
                return reset()
            } catch (_: PcSecureStorageUnavailableException) {
            }
            if (attempt < PAIRING_VERIFY_ATTEMPTS - 1) delay(retryDelayMs)
        }
        return reset()
    }

    private suspend fun reset(): Boolean {
        guarded { cipher.deleteKey() }
        backing.clear()
        return true
    }

    private inline fun <T> guarded(action: () -> T): T = try {
        action()
    } catch (error: PcSecretKeyInvalidatedException) {
        throw PcSecureStorageUnavailableException(error)
    }

    companion object {
        const val PAIRING_RETRY_DELAY_MS = 1_000L
        const val PAIRING_VERIFY_ATTEMPTS = 3
    }
}
