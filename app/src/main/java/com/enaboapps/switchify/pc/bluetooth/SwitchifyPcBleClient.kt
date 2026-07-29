package com.enaboapps.switchify.pc.bluetooth

import android.content.Context
import com.enaboapps.switchify.pc.DiscoveredPc
import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcBluetoothEndpoint
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcConnector
import com.enaboapps.switchify.pc.PcControlCloseReason
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcDeviceIdentity
import com.enaboapps.switchify.pc.PcErrorReason
import com.enaboapps.switchify.pc.PcLiveControlFailureReason
import com.enaboapps.switchify.pc.PcLiveControlResult
import com.enaboapps.switchify.pc.PcPairingResult
import com.enaboapps.switchify.pc.PcPairingTokenStore
import com.enaboapps.switchify.pc.PcPingResult
import com.enaboapps.switchify.pc.PcProtocol
import com.enaboapps.switchify.pc.PcProtocolResponse
import com.enaboapps.switchify.utils.LogEvent
import com.enaboapps.switchify.utils.Logger
import kotlinx.coroutines.CancellationException

class SwitchifyPcBleClient(
    private val identityRepository: PcDeviceIdentity,
    private val tokenStore: PcPairingTokenStore,
    transportFactory: PcBleTransportFactory
) : PcConnector {
    constructor(
        context: Context,
        identityRepository: PcDeviceIdentity,
        tokenStore: PcPairingTokenStore
    ) : this(identityRepository, tokenStore, PcBleGattTransportFactory(context.applicationContext))

    private val openConnections = mutableSetOf<PcBleTransportConnection>()
    private val transportFactory = HighPriorityPcBleTransportFactory(transportFactory)
    private val protocolMessenger = PcBleProtocolMessenger(identityRepository)

    override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
        val endpoint = pc.bluetoothEndpoint
            ?: return PcPairingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect.")
        val deviceId = identityRepository.getDeviceId()
        val deviceName = identityRepository.getDeviceName()
        return withConnection(endpoint) { connection ->
            val requestId = protocolMessenger.nextRequestId()
            val response = protocolMessenger.sendExpected(
                connection,
                PcProtocol.pairingRequest(
                    id = requestId,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    desktopId = pc.desktopId,
                    requestNonce = requestNonce
                ),
                requestId,
                PAIRING_TIMEOUT_MS
            )
            when (response) {
                is PcProtocolResponse.PairingComplete -> {
                    if (PcProtocol.validatePairingComplete(response, pc.desktopId, deviceId)) {
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
            logTransportError("pairing", pc.desktopId, it)
            PcPairingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect.")
        }
    }

    override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
        val endpoint = pc.bluetoothEndpoint
            ?: return PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect.")
        return withConnection(endpoint) { connection ->
            when (val response = protocolMessenger.authenticatedPing(connection, token)) {
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
            logTransportError("ping", pc.desktopId, it)
            PcPingResult.Failed(PcErrorReason.Unreachable, "Found PC, but could not connect.")
        }
    }

    override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
        val endpoint = pcEndpointFromSession(session) ?: return PcLiveControlResult.Failed()
        val token = tokenStore.getToken(session.desktopId) ?: return PcLiveControlResult.AuthFailed()
        val connection = runCatching { transportFactory.connect(endpoint) }.getOrElse {
            if (it is CancellationException) throw it
            logTransportError("open_session", session.desktopId, it)
            return liveControlFailure(it)
        }
        openConnections += connection
        return try {
            when (val response = protocolMessenger.authenticatedPing(connection, token)) {
                is PcProtocolResponse.Ack -> PcLiveControlResult.Connected(
                    LiveBleControlConnection(
                        connection = connection,
                        session = session,
                        token = token,
                        messenger = protocolMessenger,
                        onClosed = { openConnections -= connection },
                        pointerProfile = protocolMessenger.requestPointerProfile(connection, session, token)
                    )
                )
                is PcProtocolResponse.Error -> {
                    connection.close(PcControlCloseReason.AuthFailure.logName)
                    openConnections -= connection
                    if (response.code == "invalid_auth") PcLiveControlResult.AuthFailed()
                    else PcLiveControlResult.Failed()
                }
                else -> {
                    connection.close(PcControlCloseReason.CommandFailureRecovery.logName)
                    openConnections -= connection
                    PcLiveControlResult.Failed()
                }
            }
        } catch (error: InvalidPcAuthException) {
            connection.close(PcControlCloseReason.AuthFailure.logName)
            openConnections -= connection
            PcLiveControlResult.AuthFailed()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            logTransportError("open_session", session.desktopId, error)
            connection.close(PcControlCloseReason.CommandFailureRecovery.logName)
            openConnections -= connection
            liveControlFailure(error)
        }
    }

    override suspend fun sendCommand(
        session: PcAuthenticatedSession,
        command: PcControlCommand
    ): PcCommandResult {
        val endpoint = pcEndpointFromSession(session) ?: return PcCommandResult.Failed()
        val token = tokenStore.getToken(session.desktopId) ?: return PcCommandResult.AuthFailed()
        return withConnection(endpoint) { connection ->
            protocolMessenger.sendCommand(connection, session, token, command)
        }.getOrDefault(PcCommandResult.Failed())
    }

    override fun close() {
        openConnections.toList().forEach { it.close(PcControlCloseReason.ConnectorShutdown.logName) }
        openConnections.clear()
    }

    private suspend fun <T> withConnection(
        endpoint: PcBluetoothEndpoint,
        block: suspend (PcBleTransportConnection) -> T
    ): Result<T> {
        return runCatching {
            val connection = transportFactory.connect(endpoint)
            try {
                block(connection)
            } finally {
                connection.close(PcControlCloseReason.ExplicitStop.logName)
            }
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

    private fun logTransportError(operation: String, desktopId: String, error: Throwable) {
        Logger.log(
            LogEvent.PcBleTransportError,
            data = mapOf("operation" to operation, "desktopId" to desktopId),
            throwable = error
        )
    }

    private fun liveControlFailure(error: Throwable): PcLiveControlResult.Failed {
        val reason = (error as? PcBleTransportException)?.reason
        return when {
            reason == PcBleFailureReason.PermissionMissing ||
                error.message == "Bluetooth permission missing." -> PcLiveControlResult.Failed(
                message = "Bluetooth permission denied.",
                reason = PcLiveControlFailureReason.PermissionDenied
            )
            reason == PcBleFailureReason.BluetoothDisabled ||
                error.message == "Bluetooth is off." -> PcLiveControlResult.Failed(
                message = "Bluetooth is off.",
                reason = PcLiveControlFailureReason.BluetoothDisabled
            )
            else -> PcLiveControlResult.Failed()
        }
    }

    private companion object {
        const val PAIRING_TIMEOUT_MS = 125_000L
    }
}
