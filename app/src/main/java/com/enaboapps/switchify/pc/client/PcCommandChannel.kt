package com.enaboapps.switchify.pc.client

import com.enaboapps.switchify.pc.protocol.PcClock
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcMessages
import com.enaboapps.switchify.pc.protocol.PcResponse
import com.enaboapps.switchify.pc.protocol.PcResponseMode

class PcCredentials(val deviceId: String, val token: String) {
    override fun toString(): String = "PcCredentials(deviceId=$deviceId)"
}

interface PcCommandChannel {
    suspend fun request(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.Ack): PcResponse

    suspend fun send(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.Ack): Boolean =
        request(command, responseMode) is PcResponse.Ack
}

class PcAuthenticatedCommandChannel(
    private val client: PcProtocolClient,
    private val credentials: PcCredentials,
    private val clock: PcClock = PcClock.System,
    private val requestIds: PcIdGenerator = PcIdGenerator.Request,
    private val requestTimeoutMs: Long = DEFAULT_COMMAND_TIMEOUT_MS
) : PcCommandChannel {
    override suspend fun request(command: PcCommand, responseMode: PcResponseMode): PcResponse {
        val id = requestIds.nextId()
        val message = PcMessages.authenticatedCommand(
            id = id,
            deviceId = credentials.deviceId,
            token = credentials.token,
            timestamp = clock.nowMillis(),
            command = command,
            responseMode = responseMode
        )
        if (responseMode == PcResponseMode.None) {
            client.send(message)
            return PcResponse.Ack(id)
        }
        return client.request(message, id, requestTimeoutMs)
    }

    companion object {
        const val DEFAULT_COMMAND_TIMEOUT_MS = 5_000L
    }
}
