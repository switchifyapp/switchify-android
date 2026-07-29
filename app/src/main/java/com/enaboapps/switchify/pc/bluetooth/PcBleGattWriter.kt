package com.enaboapps.switchify.pc.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

internal class PcBleGattWriter(
    private val gatt: BluetoothGatt,
    private val characteristic: BluetoothGattCharacteristic
) {
    private val writeCompletion = PcBleWriteCompletion()
    private val messageWriter = PcBleMessageWriter(::writeFrame)

    suspend fun send(message: String, requestedMode: PcBleWriteMode) {
        val writeMode = resolveBleWriteMode(
            requested = requestedMode,
            supportsWithoutResponse =
                characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
        )
        messageWriter.send(message, writeMode)
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeFrame(value: ByteArray, writeMode: PcBleWriteMode) {
        val completion = CompletableDeferred<Boolean>()
        if (!writeCompletion.begin(completion)) {
            throw PcBleTransportException(PcBleFailureReason.ConnectionClosed, "Bluetooth connection closed.")
        }
        val writeType = when (writeMode) {
            PcBleWriteMode.WithResponse -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            PcBleWriteMode.WithoutResponse -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }
        val started = if (Build.VERSION.SDK_INT >= 33) {
            gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothGatt.GATT_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            characteristic.writeType = writeType
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            writeCompletion.complete(success = false)
        }
        val completed = try {
            withTimeout(GATT_WRITE_TIMEOUT_MS) { completion.await() }
        } catch (error: TimeoutCancellationException) {
            fail()
            throw PcBleTransportException(PcBleFailureReason.WriteTimedOut, "Bluetooth write timed out.", error)
        }
        if (!completed) {
            throw PcBleTransportException(PcBleFailureReason.WriteFailed, "Bluetooth write failed.")
        }
    }

    fun complete(success: Boolean) {
        writeCompletion.complete(success)
    }

    fun fail() {
        writeCompletion.fail()
    }
}

internal class PcBleMessageWriter(
    private val writeFrame: suspend (ByteArray, PcBleWriteMode) -> Unit
) {
    private val messageMutex = Mutex()

    suspend fun send(message: String, writeMode: PcBleWriteMode) {
        messageMutex.withLock {
            BluetoothFrameCodec.createFrames(message).forEach { frame ->
                writeFrame(BluetoothFrameCodec.encode(frame), writeMode)
            }
        }
    }
}

internal class PcBleWriteCompletion {
    private val lock = Any()
    private var pending: CompletableDeferred<Boolean>? = null
    private var failed = false

    fun begin(completion: CompletableDeferred<Boolean>): Boolean {
        val accepted = synchronized(lock) {
            if (failed) return@synchronized false
            check(pending == null)
            pending = completion
            true
        }
        if (!accepted) completion.complete(false)
        return accepted
    }

    fun complete(success: Boolean): Boolean {
        val completion = synchronized(lock) {
            pending.also { pending = null }
        } ?: return false
        return completion.complete(success)
    }

    fun fail() {
        val completion = synchronized(lock) {
            failed = true
            pending.also { pending = null }
        }
        completion?.complete(false)
    }
}

internal fun resolveBleWriteMode(
    requested: PcBleWriteMode,
    supportsWithoutResponse: Boolean
): PcBleWriteMode {
    return if (requested == PcBleWriteMode.WithoutResponse && supportsWithoutResponse) {
        PcBleWriteMode.WithoutResponse
    } else {
        PcBleWriteMode.WithResponse
    }
}

private const val GATT_WRITE_TIMEOUT_MS = 2_000L
