package com.enaboapps.switchify.pc.bluetooth

import com.enaboapps.switchify.pc.PcAuthenticatedSession
import com.enaboapps.switchify.pc.PcCommandResponseMode
import com.enaboapps.switchify.pc.PcCommandResult
import com.enaboapps.switchify.pc.PcControlCloseReason
import com.enaboapps.switchify.pc.PcControlCommand
import com.enaboapps.switchify.pc.PcControlConnection
import com.enaboapps.switchify.pc.PcControlConnectionEvent
import com.enaboapps.switchify.pc.PcDeviceIdentity
import com.enaboapps.switchify.pc.PcPointerMovementProfile
import com.enaboapps.switchify.pc.PcProfileCatalogResult
import com.enaboapps.switchify.pc.PcProtocol
import com.enaboapps.switchify.pc.PcProtocolResponse
import com.enaboapps.switchify.pc.resolveExpectedResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

internal class PcBleProtocolMessenger(
    private val identityRepository: PcDeviceIdentity,
    private val requestIdProvider: () -> String = { "android-${UUID.randomUUID()}" },
    private val now: () -> Long = System::currentTimeMillis
) {
    suspend fun authenticatedPing(
        connection: PcBleTransportConnection,
        token: String
    ): PcProtocolResponse {
        val requestId = nextRequestId()
        return sendExpected(
            connection,
            PcProtocol.authenticatedPing(
                id = requestId,
                deviceId = identityRepository.getDeviceId(),
                token = token,
                timestamp = now()
            ),
            requestId,
            PING_TIMEOUT_MS
        )
    }

    suspend fun sendExpected(
        connection: PcBleTransportConnection,
        message: String,
        requestId: String,
        timeoutMs: Long
    ): PcProtocolResponse {
        val raw = connection.sendAndReceive(message, requestId, timeoutMs)
        return resolveExpectedResponse(PcProtocol.parseResponse(raw), requestId)
            ?: PcProtocolResponse.Invalid
    }

    suspend fun requestPointerProfile(
        connection: PcBleTransportConnection,
        session: PcAuthenticatedSession,
        token: String
    ): PcPointerMovementProfile? {
        return try {
            val requestId = nextRequestId()
            when (val response = sendExpected(
                connection,
                PcProtocol.pointerProfile(requestId, session.deviceId, token, now()),
                requestId,
                COMMAND_TIMEOUT_MS
            )) {
                is PcProtocolResponse.PointerProfile -> response.profile
                is PcProtocolResponse.Error ->
                    if (response.code == "invalid_auth") throw InvalidPcAuthException() else null
                else -> null
            }
        } catch (error: Throwable) {
            if (error is CancellationException || error is InvalidPcAuthException) throw error
            null
        }
    }

    suspend fun sendCommand(
        connection: PcBleTransportConnection,
        session: PcAuthenticatedSession,
        token: String,
        command: PcControlCommand
    ): PcCommandResult {
        val requestId = nextRequestId()
        return when (val response = sendExpected(
            connection,
            command.toProtocolMessage(requestId, session.deviceId, token, now()),
            requestId,
            COMMAND_TIMEOUT_MS
        )) {
            is PcProtocolResponse.Ack -> PcCommandResult.Ack
            is PcProtocolResponse.Error -> {
                if (response.code == "invalid_auth") PcCommandResult.AuthFailed()
                else PcCommandResult.Failed(
                    response.message.ifBlank { "Could not send command to PC." },
                    response.code
                )
            }
            else -> PcCommandResult.Failed()
        }
    }

    suspend fun requestSwitchProfileCatalog(
        connection: PcBleTransportConnection,
        session: PcAuthenticatedSession,
        token: String
    ): PcProfileCatalogResult {
        val requestId = nextRequestId()
        return when (val response = sendExpected(
            connection,
            PcProtocol.switchProfileList(requestId, session.deviceId, token, now()),
            requestId,
            COMMAND_TIMEOUT_MS
        )) {
            is PcProtocolResponse.SwitchProfileCatalog -> PcProfileCatalogResult.Loaded(response.catalog)
            is PcProtocolResponse.Error -> if (response.code == "invalid_auth") {
                PcProfileCatalogResult.AuthFailed()
            } else {
                PcProfileCatalogResult.Failed(response.message)
            }
            else -> PcProfileCatalogResult.Failed()
        }
    }

    suspend fun sendRealtimeCommand(
        connection: PcBleTransportConnection,
        session: PcAuthenticatedSession,
        token: String,
        command: PcControlCommand
    ) {
        connection.send(
            command.toProtocolMessage(
                id = nextRequestId(),
                deviceId = session.deviceId,
                token = token,
                timestamp = now(),
                responseMode = PcCommandResponseMode.None
            ),
            PcBleWriteMode.WithoutResponse
        )
    }

    fun nextRequestId(): String = requestIdProvider()

    companion object {
        const val PING_TIMEOUT_MS = 10_000L
        const val COMMAND_TIMEOUT_MS = 5_000L
    }
}

internal class LiveBleControlConnection(
    private val connection: PcBleTransportConnection,
    private val session: PcAuthenticatedSession,
    private val token: String,
    private val messenger: PcBleProtocolMessenger,
    private val onClosed: () -> Unit,
    override val pointerProfile: PcPointerMovementProfile?
) : PcControlConnection {
    override val connectionEvents: Flow<PcControlConnectionEvent> = connection.events.map { event ->
        when (event) {
            PcBleTransportEvent.Disconnected -> PcControlConnectionEvent.Disconnected
        }
    }

    override suspend fun checkHealth(): PcCommandResult {
        return try {
            when (val response = messenger.authenticatedPing(connection, token)) {
                is PcProtocolResponse.Ack -> PcCommandResult.Ack
                is PcProtocolResponse.Error ->
                    if (response.code == "invalid_auth") PcCommandResult.AuthFailed() else PcCommandResult.Failed()
                else -> PcCommandResult.Failed()
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            PcCommandResult.Failed()
        }
    }

    override suspend fun sendCommand(command: PcControlCommand): PcCommandResult {
        return try {
            messenger.sendCommand(connection, session, token, command)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            if (error is InvalidPcAuthException) PcCommandResult.AuthFailed() else PcCommandResult.Failed()
        }
    }

    override suspend fun requestSwitchProfileCatalog(): PcProfileCatalogResult {
        return try {
            messenger.requestSwitchProfileCatalog(connection, session, token)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            PcProfileCatalogResult.Failed()
        }
    }

    override suspend fun sendRealtimeCommand(command: PcControlCommand): PcCommandResult {
        if (pointerProfile?.supportsNoAck(command) != true) {
            return sendCommand(command)
        }
        return try {
            messenger.sendRealtimeCommand(connection, session, token, command)
            PcCommandResult.Ack
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            PcCommandResult.Failed()
        }
    }

    override fun close(reason: PcControlCloseReason) {
        connection.close(reason.logName)
        onClosed()
    }
}

internal class InvalidPcAuthException : RuntimeException()
