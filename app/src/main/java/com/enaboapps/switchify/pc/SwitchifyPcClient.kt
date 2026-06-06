package com.enaboapps.switchify.pc

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.withTimeout
import java.util.UUID

sealed class PcPairingResult {
    data class Paired(val desktopId: String, val token: String, val websocketUrl: String) : PcPairingResult()
    data class Failed(val message: String) : PcPairingResult()
}

sealed class PcPingResult {
    data class Connected(val websocketUrl: String) : PcPingResult()
    data class AuthFailed(val message: String = "Connection expired. Request access again.") : PcPingResult()
    data class Failed(val message: String) : PcPingResult()
}

sealed class PcMouseCommand {
    data class Move(val dx: Int, val dy: Int) : PcMouseCommand()
    data class Scroll(val dx: Int, val dy: Int) : PcMouseCommand()
    data object LeftClick : PcMouseCommand()
    data object DoubleClick : PcMouseCommand()
    data object RightClick : PcMouseCommand()
}

sealed class PcCommandResult {
    data object Ack : PcCommandResult()
    data class AuthFailed(val message: String = "Connection expired. Connect to PC from Switchify first.") : PcCommandResult()
    data class Failed(val message: String = "Could not send command to PC.") : PcCommandResult()
}

interface PcConnector {
    suspend fun requestApproval(pc: DiscoveredPc): PcPairingResult
    suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult
    suspend fun sendMouseCommand(session: PcAuthenticatedSession, command: PcMouseCommand): PcCommandResult
    fun close()
}

class SwitchifyPcClient(
    private val identityRepository: PcDeviceIdentity,
    private val tokenStore: PcPairingTokenStore
) : PcConnector {
    private val client = HttpClient(CIO) {
        install(WebSockets)
        engine {
            requestTimeout = 5_000
        }
    }

    override suspend fun requestApproval(pc: DiscoveredPc): PcPairingResult {
        val deviceId = identityRepository.getDeviceId()
        val deviceName = identityRepository.getDeviceName()
        for (urlString in candidateUrls(pc)) {
            val result = runCatching {
                val session = client.webSocketSession { url { takeFrom(urlString) } }
                val requestId = nextRequestId()
                session.send(
                    Frame.Text(
                        PcProtocol.pairingRequest(
                            id = requestId,
                            deviceId = deviceId,
                            deviceName = deviceName,
                            desktopId = pc.desktopId,
                            requestNonce = UUID.randomUUID().toString()
                        )
                    )
                )
                val response = withTimeout(125_000) { readExpectedResponse(session, requestId) }
                session.close()
                when (response) {
                    is PcProtocolResponse.PairingComplete -> {
                        if (PcProtocol.validatePairingComplete(response, pc.desktopId, deviceId)) {
                            tokenStore.saveToken(pc.desktopId, response.token, urlString, pc.displayName)
                            PcPairingResult.Paired(pc.desktopId, response.token, urlString)
                        } else {
                            PcPairingResult.Failed("Could not connect to this PC.")
                        }
                    }
                    is PcProtocolResponse.Error -> PcPairingResult.Failed(PcProtocol.userMessageForError(response.message))
                    else -> PcPairingResult.Failed("Could not connect to this PC.")
                }
            }.getOrElse {
                PcPairingResult.Failed("Found PC, but could not connect.")
            }
            if (result is PcPairingResult.Paired) return result
            if (result is PcPairingResult.Failed && result.message != "Found PC, but could not connect.") return result
        }
        return PcPairingResult.Failed("Found PC, but could not connect.")
    }

    override suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult {
        val deviceId = identityRepository.getDeviceId()
        for (urlString in candidateUrls(pc)) {
            val result = runCatching {
                val session = client.webSocketSession { url { takeFrom(urlString) } }
                val requestId = nextRequestId()
                session.send(
                    Frame.Text(
                        PcProtocol.authenticatedPing(
                            id = requestId,
                            deviceId = deviceId,
                            token = token,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                )
                val response = withTimeout(10_000) { readExpectedResponse(session, requestId) }
                session.close()
                when (response) {
                    is PcProtocolResponse.Ack -> PcPingResult.Connected(urlString)
                    is PcProtocolResponse.Error -> {
                        if (response.code == "invalid_auth") PcPingResult.AuthFailed()
                        else PcPingResult.Failed(PcProtocol.userMessageForError(response.message))
                    }
                    else -> PcPingResult.Failed("Could not connect to this PC.")
                }
            }.getOrElse {
                PcPingResult.Failed("Found PC, but could not connect.")
            }
            if (result is PcPingResult.Connected || result is PcPingResult.AuthFailed) return result
        }
        return PcPingResult.Failed("Found PC, but could not connect.")
    }

    override suspend fun sendMouseCommand(session: PcAuthenticatedSession, command: PcMouseCommand): PcCommandResult {
        val token = tokenStore.getToken(session.desktopId) ?: return PcCommandResult.AuthFailed()
        return runCatching {
            val webSocketSession = client.webSocketSession { url { takeFrom(session.websocketUrl) } }
            val requestId = nextRequestId()
            webSocketSession.send(
                Frame.Text(
                    command.toMessage(
                        id = requestId,
                        deviceId = session.deviceId,
                        token = token,
                        timestamp = System.currentTimeMillis()
                    )
                )
            )
            val response = withTimeout(5_000) { readExpectedResponse(webSocketSession, requestId) }
            webSocketSession.close()
            when (response) {
                is PcProtocolResponse.Ack -> PcCommandResult.Ack
                is PcProtocolResponse.Error -> {
                    if (response.code == "invalid_auth") PcCommandResult.AuthFailed()
                    else PcCommandResult.Failed()
                }
                else -> PcCommandResult.Failed()
            }
        }.getOrDefault(PcCommandResult.Failed())
    }

    override fun close() {
        client.close()
    }

    private fun candidateUrls(pc: DiscoveredPc): List<String> {
        val urls = pc.websocketUrls.toMutableList()
        tokenStore.getLastUrl(pc.desktopId)?.takeIf { it !in urls }?.let(urls::add)
        return urls.distinct()
    }

    private suspend fun readExpectedResponse(session: io.ktor.websocket.WebSocketSession, requestId: String): PcProtocolResponse {
        while (true) {
            val frame = session.incoming.receive()
            if (frame !is Frame.Text) continue
            val response = PcProtocol.parseResponse(frame.readText())
            when (response) {
                is PcProtocolResponse.Ack -> if (response.id == requestId) return response
                is PcProtocolResponse.PairingComplete -> if (response.id == requestId) return response
                is PcProtocolResponse.Error -> if (response.id == requestId || response.id == null) return response
                PcProtocolResponse.Invalid -> return response
            }
        }
    }

    private fun nextRequestId(): String = "android-${UUID.randomUUID()}"

    private fun PcMouseCommand.toMessage(id: String, deviceId: String, token: String, timestamp: Long): String {
        return when (this) {
            is PcMouseCommand.Move -> PcProtocol.mouseMove(id, deviceId, token, timestamp, dx, dy)
            is PcMouseCommand.Scroll -> PcProtocol.mouseScroll(id, deviceId, token, timestamp, dx, dy)
            PcMouseCommand.LeftClick -> PcProtocol.mouseClick(id, deviceId, token, timestamp)
            PcMouseCommand.DoubleClick -> PcProtocol.mouseDoubleClick(id, deviceId, token, timestamp)
            PcMouseCommand.RightClick -> PcProtocol.mouseRightClick(id, deviceId, token, timestamp)
        }
    }
}
