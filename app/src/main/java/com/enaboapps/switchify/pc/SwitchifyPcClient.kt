package com.enaboapps.switchify.pc

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.http.takeFrom
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
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

sealed class PcControlCommand {
    data class Move(val dx: Int, val dy: Int) : PcControlCommand()
    data class Scroll(val dx: Int, val dy: Int) : PcControlCommand()
    data class TypeText(val text: String) : PcControlCommand()
    data class PressKey(val key: PcKeyboardKey) : PcControlCommand()
    data class WindowControl(val action: PcWindowControlAction) : PcControlCommand()
    data object LeftClick : PcControlCommand()
    data object DoubleClick : PcControlCommand()
    data object RightClick : PcControlCommand()
}

sealed class PcCommandResult {
    data object Ack : PcCommandResult()
    data class AuthFailed(val message: String = "Connection expired. Connect to PC from Switchify first.") : PcCommandResult()
    data class Failed(val message: String = "Could not send command to PC.") : PcCommandResult()
}

sealed class PcLiveControlResult {
    data class Connected(val connection: PcControlConnection) : PcLiveControlResult()
    data class AuthFailed(val message: String = "Connection expired. Connect to PC from Switchify first.") : PcLiveControlResult()
    data class Failed(val message: String = "Could not connect to PC.") : PcLiveControlResult()
}

interface PcControlConnection {
    val pointerProfile: PcPointerMovementProfile?
    suspend fun sendCommand(command: PcControlCommand): PcCommandResult
    fun close()
}

internal fun resolveExpectedResponse(response: PcProtocolResponse, requestId: String): PcProtocolResponse? {
    return when (response) {
        is PcProtocolResponse.Ack -> response.takeIf { it.id == requestId }
        is PcProtocolResponse.PairingComplete -> response.takeIf { it.id == requestId }
        is PcProtocolResponse.PointerProfile -> response.takeIf { it.id == requestId }
        is PcProtocolResponse.Error -> response.takeIf { it.id == requestId || it.id == null }
        PcProtocolResponse.Invalid -> null
    }
}

interface PcConnector {
    suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult
    suspend fun authenticatedPing(pc: DiscoveredPc, token: String): PcPingResult
    suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult
    suspend fun sendCommand(session: PcAuthenticatedSession, command: PcControlCommand): PcCommandResult
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

    override suspend fun requestApproval(pc: DiscoveredPc, requestNonce: String): PcPairingResult {
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
                            requestNonce = requestNonce
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

    override suspend fun sendCommand(session: PcAuthenticatedSession, command: PcControlCommand): PcCommandResult {
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

    override suspend fun openControlSession(session: PcAuthenticatedSession): PcLiveControlResult {
        val token = tokenStore.getToken(session.desktopId) ?: return PcLiveControlResult.AuthFailed()
        var webSocketSession: WebSocketSession? = null
        return try {
            webSocketSession = client.webSocketSession { url { takeFrom(session.websocketUrl) } }
            val requestId = nextRequestId()
            webSocketSession.send(
                Frame.Text(
                    PcProtocol.authenticatedPing(
                        id = requestId,
                        deviceId = session.deviceId,
                        token = token,
                        timestamp = System.currentTimeMillis()
                    )
                )
            )
            when (val response = withTimeout(10_000) { readExpectedResponse(webSocketSession, requestId) }) {
                is PcProtocolResponse.Ack -> PcLiveControlResult.Connected(
                    LiveControlConnection(
                        webSocketSession = webSocketSession,
                        authenticatedSession = session,
                        token = token,
                        pointerProfile = requestPointerProfile(webSocketSession, session, token)
                    )
                )
                is PcProtocolResponse.Error -> {
                    webSocketSession.close()
                    if (response.code == "invalid_auth") PcLiveControlResult.AuthFailed()
                    else PcLiveControlResult.Failed()
                }
                else -> {
                    webSocketSession.close()
                    PcLiveControlResult.Failed()
                }
            }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            webSocketSession?.close()
            if (error is InvalidPcAuthException) PcLiveControlResult.AuthFailed()
            else PcLiveControlResult.Failed()
        }
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
            val response = resolveExpectedResponse(PcProtocol.parseResponse(frame.readText()), requestId)
            if (response != null) return response
        }
    }

    private suspend fun requestPointerProfile(
        webSocketSession: WebSocketSession,
        session: PcAuthenticatedSession,
        token: String
    ): PcPointerMovementProfile? {
        return try {
            val requestId = nextRequestId()
            webSocketSession.send(
                Frame.Text(
                    PcProtocol.pointerProfile(
                        id = requestId,
                        deviceId = session.deviceId,
                        token = token,
                        timestamp = System.currentTimeMillis()
                    )
                )
            )
            when (val response = withTimeout(5_000) { readExpectedResponse(webSocketSession, requestId) }) {
                is PcProtocolResponse.PointerProfile -> response.profile
                is PcProtocolResponse.Error -> {
                    if (response.code == "invalid_auth") throw InvalidPcAuthException()
                    null
                }
                else -> null
            }
        } catch (error: Throwable) {
            if (error is CancellationException || error is InvalidPcAuthException) throw error
            null
        }
    }

    private fun nextRequestId(): String = "android-${UUID.randomUUID()}"

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

    private inner class LiveControlConnection(
        private val webSocketSession: WebSocketSession,
        private val authenticatedSession: PcAuthenticatedSession,
        private val token: String,
        override val pointerProfile: PcPointerMovementProfile?
    ) : PcControlConnection {
        override suspend fun sendCommand(command: PcControlCommand): PcCommandResult {
            return runCatching {
                val requestId = nextRequestId()
                webSocketSession.send(
                    Frame.Text(
                        command.toMessage(
                            id = requestId,
                            deviceId = authenticatedSession.deviceId,
                            token = token,
                            timestamp = System.currentTimeMillis()
                        )
                    )
                )
                when (val response = withTimeout(5_000) { readExpectedResponse(webSocketSession, requestId) }) {
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
            webSocketSession.cancel()
        }
    }

    private class InvalidPcAuthException : RuntimeException()
}
