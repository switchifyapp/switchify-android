package com.enaboapps.switchify.pc.bluetooth

import android.content.Context
import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcControlConnection
import com.enaboapps.switchify.pc.PcControlConnectionEvent
import com.enaboapps.switchify.pc.PcDeviceIdentity
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcKeyboardKey
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcPairingResult
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcPingResult
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcProtocol
import com.enaboapps.switchify.pc.PcProtocolResponse
import com.enaboapps.switchify.pc.PcWindowControlAction
import com.enaboapps.switchify.pc.resolveExpectedResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.UUID

class SwitchifyPcBleClient(
    private val identityRepository: PcDeviceIdentity,
    private val tokenStore: PcPairingTokenStore,
    private val transportFactory: PcBleTransportFactory
) : PcConnector {
    constructor(
        context: Context,
        identityRepository: PcDeviceIdentity,
        tokenStore: PcPairingTokenStore
    ) : this(identityRepository, tokenStore, PcBleGattTransportFactory(context.applicationContext))

    private val openConnections = mutableSetOf<PcBleTransportConnection>()

    override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
        val endpoint = pc.bluetoothEndpoint ?: return PcPairingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect.")
        val deviceId = identityRepository.getDeviceId()
        val deviceName = identityRepository.getDeviceName()
        return withConnection(endpoint) { connection ->
            val requestId = nextRequestId()
            val response = sendExpected(
                connection = connection,
                message = PcProtocol.pairingRequest(
                    id = requestId,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    desktopId = pc.desktopId,
                    requestNonce = requestNonce
                ),
                requestId = requestId,
                timeoutMs = PAIRING_TIMEOUT_MS
            )
            when (response) {
                is PcProtocolResponse.PairingComplete -> {
                    if (PcProtocol.validatePairingComplete(response, pc.desktopId, deviceId)) {
                        tokenStore.saveToken(pc.desktopId, response.token, endpoint.deviceAddress, pc.displayName)
                        PcPairingResult.Paired(pc.desktopId, response.token, endpoint.deviceAddress)
                    } else {
                        PcPairingResult.Failed(PcErrorReason.Failed, "Could not connect to this PC.")
                    }
                }
                is PcProtocolResponse.Error -> PcPairingResult.Failed(
                    PcProtocol.errorReason(response.message),
                    PcProtocol.userMessageForError(response.message)
                )
                else -> PcPairingResult.Failed(PcErrorReason.Failed, "Could not connect to this PC.")
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            PcPairingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect.")
        }
    }

    override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
        val endpoint = pc.bluetoothEndpoint ?: return PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect.")
        return withConnection(endpoint) { connection ->
            when (val response = sendAuthenticatedPing(connection, token)) {
                is PcProtocolResponse.Ack -> PcPingResult.Connected(endpoint.deviceAddress)
                is PcProtocolResponse.Error -> {
                    if (response.code == "invalid_auth") PcPingResult.AuthFailed()
                    else PcPingResult.Failed(
                        PcProtocol.errorReason(response.message),
                        PcProtocol.userMessageForError(response.message)
                    )
                }
                else -> PcPingResult.Failed(PcErrorReason.Failed, "Could not connect to this PC.")
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect.")
        }
    }

    override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
        val endpoint = pcEndpointFromSession(session) ?: return PcLiveControlResult.Failed()
        val token = tokenStore.getToken(session.desktopId) ?: return PcLiveControlResult.AuthFailed()
        return runCatching {
            val connection = transportFactory.connect(endpoint)
            openConnections += connection
            when (val response = sendAuthenticatedPing(connection, token)) {
                is PcProtocolResponse.Ack -> PcLiveControlResult.Connected(
                    LiveBleControlConnection(
                        connection = connection,
                        authenticatedSession = session,
                        token = token,
                        pointerProfile = requestPointerProfile(connection, session, token)
                    )
                )
                is PcProtocolResponse.Error -> {
                    connection.close()
                    openConnections -= connection
                    if (response.code == "invalid_auth") PcLiveControlResult.AuthFailed()
                    else PcLiveControlResult.Failed()
                }
                else -> {
                    connection.close()
                    openConnections -= connection
                    PcLiveControlResult.Failed()
                }
            }
        }.getOrElse {
            if (it is CancellationException) throw it
            PcLiveControlResult.Failed(safeConnectionFailureMessage(it))
        }
    }

    override suspend fun sendCommand(session: PcAuthenticatedSession, command: PcControlCommand): PcCommandResult {
        val endpoint = pcEndpointFromSession(session) ?: return PcCommandResult.Failed()
        val token = tokenStore.getToken(session.desktopId) ?: return PcCommandResult.AuthFailed()
        return withConnection(endpoint) { connection ->
            sendCommandMessage(connection, session, token, command)
        }.getOrDefault(PcCommandResult.Failed())
    }

    override fun close() {
        openConnections.toList().forEach { it.close() }
        openConnections.clear()
    }

    private suspend fun <T> withConnection(endpoint: PcBluetoothEndpoint, block: suspend (PcBleTransportConnection) -> T): Result<T> {
        return runCatching {
            val connection = transportFactory.connect(endpoint)
            try {
                block(connection)
            } finally {
                connection.close()
            }
        }
    }

    private suspend fun sendAuthenticatedPing(connection: PcBleTransportConnection, token: String): PcProtocolResponse {
        val requestId = nextRequestId()
        return sendExpected(
            connection = connection,
            message = PcProtocol.authenticatedPing(
                id = requestId,
                deviceId = identityRepository.getDeviceId(),
                token = token,
                timestamp = System.currentTimeMillis()
            ),
            requestId = requestId,
            timeoutMs = PING_TIMEOUT_MS
        )
    }

    private suspend fun sendExpected(
        connection: PcBleTransportConnection,
        message: String,
        requestId: String,
        timeoutMs: Long
    ): PcProtocolResponse {
        return withTimeout(timeoutMs) {
            while (true) {
                val raw = connection.sendAndReceive(message, timeoutMs)
                val response = resolveExpectedResponse(PcProtocol.parseResponse(raw), requestId)
                if (response != null) return@withTimeout response
            }
            PcProtocolResponse.Invalid
        }
    }

    private suspend fun requestPointerProfile(
        connection: PcBleTransportConnection,
        session: PcAuthenticatedSession,
        token: String
    ): PcPointerMovementProfile? {
        return try {
            val requestId = nextRequestId()
            when (val response = sendExpected(
                connection = connection,
                message = PcProtocol.pointerProfile(
                    id = requestId,
                    deviceId = session.deviceId,
                    token = token,
                    timestamp = System.currentTimeMillis()
                ),
                requestId = requestId,
                timeoutMs = COMMAND_TIMEOUT_MS
            )) {
                is PcProtocolResponse.PointerProfile -> response.profile
                is PcProtocolResponse.Error -> if (response.code == "invalid_auth") throw InvalidPcAuthException() else null
                else -> null
            }
        } catch (error: Throwable) {
            if (error is CancellationException || error is InvalidPcAuthException) throw error
            null
        }
    }

    private suspend fun sendCommandMessage(
        connection: PcBleTransportConnection,
        session: PcAuthenticatedSession,
        token: String,
        command: PcControlCommand
    ): PcCommandResult {
        val requestId = nextRequestId()
        return when (val response = sendExpected(
            connection = connection,
            message = command.toMessage(requestId, session.deviceId, token, System.currentTimeMillis()),
            requestId = requestId,
            timeoutMs = COMMAND_TIMEOUT_MS
        )) {
            is PcProtocolResponse.Ack -> PcCommandResult.Ack
            is PcProtocolResponse.Error -> {
                if (response.code == "invalid_auth") PcCommandResult.AuthFailed()
                else PcCommandResult.Failed()
            }
            else -> PcCommandResult.Failed()
        }
    }

    private fun pcEndpointFromSession(session: PcAuthenticatedSession): PcBluetoothEndpoint? {
        val serviceName = tokenStore.getServiceName(session.desktopId) ?: "Switchify PC"
        return PcBluetoothEndpoint(
            deviceAddress = session.endpointId,
            deviceName = null,
            desktopId = session.desktopId,
            displayName = serviceName
        )
    }

    private fun nextRequestId(): String = "android-${UUID.randomUUID()}"

    private fun safeConnectionFailureMessage(error: Throwable): String {
        return when (error.message) {
            "Bluetooth permission missing." -> "Bluetooth permission denied."
            "Bluetooth is off." -> "Bluetooth is off."
            else -> "Could not connect to PC."
        }
    }

    private fun PcControlCommand.toMessage(id: String, deviceId: String, token: String, timestamp: Long): String {
        return when (this) {
            is PcControlCommand.Move -> PcProtocol.mouseMove(id, deviceId, token, timestamp, dx, dy)
            is PcControlCommand.Scroll -> PcProtocol.mouseScroll(id, deviceId, token, timestamp, dx, dy)
            PcControlCommand.LeftClick -> PcProtocol.mouseClick(id, deviceId, token, timestamp)
            PcControlCommand.DoubleClick -> PcProtocol.mouseDoubleClick(id, deviceId, token, timestamp)
            PcControlCommand.RightClick -> PcProtocol.mouseRightClick(id, deviceId, token, timestamp)
            is PcControlCommand.TypeText -> PcProtocol.keyboardTypeText(id, deviceId, token, timestamp, text)
            is PcControlCommand.PressKey -> PcProtocol.keyboardKey(id, deviceId, token, timestamp, key)
            is PcControlCommand.WindowControl -> PcProtocol.windowControl(id, deviceId, token, timestamp, action)
        }
    }

    private inner class LiveBleControlConnection(
        private val connection: PcBleTransportConnection,
        private val authenticatedSession: PcAuthenticatedSession,
        private val token: String,
        override val pointerProfile: PcPointerMovementProfile?
    ) : PcControlConnection {
        override val connectionEvents: Flow<PcControlConnectionEvent> = connection.events.map { event ->
            when (event) {
                PcBleTransportEvent.Disconnected -> PcControlConnectionEvent.Disconnected
            }
        }

        override suspend fun sendCommand(command: PcControlCommand): PcCommandResult {
            return try {
                sendCommandMessage(connection, authenticatedSession, token, command)
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                if (error is InvalidPcAuthException) PcCommandResult.AuthFailed()
                else PcCommandResult.Failed()
            }
        }

        override fun close() {
            connection.close()
            openConnections -= connection
        }
    }

    private class InvalidPcAuthException : RuntimeException()

    private companion object {
        const val PAIRING_TIMEOUT_MS = 125_000L
        const val PING_TIMEOUT_MS = 10_000L
        const val COMMAND_TIMEOUT_MS = 5_000L
    }
}
