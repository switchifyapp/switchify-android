package com.enaboapps.switchify.pc.connection

import com.enaboapps.switchify.pc.client.PcAuthenticatedCommandChannel
import com.enaboapps.switchify.pc.client.PcCommandChannel
import com.enaboapps.switchify.pc.client.PcCredentials
import com.enaboapps.switchify.pc.client.PcProtocolClient
import com.enaboapps.switchify.pc.client.PcProtocolResponseTimeoutException
import com.enaboapps.switchify.pc.client.PcProtocolWriteException
import com.enaboapps.switchify.pc.protocol.PcClock
import com.enaboapps.switchify.pc.protocol.PcCommand
import com.enaboapps.switchify.pc.protocol.PcCommandTypes
import com.enaboapps.switchify.pc.protocol.PcCommands
import com.enaboapps.switchify.pc.protocol.PcIdGenerator
import com.enaboapps.switchify.pc.protocol.PcMessages
import com.enaboapps.switchify.pc.protocol.PcPointerProfile
import com.enaboapps.switchify.pc.protocol.PcResponse
import com.enaboapps.switchify.pc.protocol.PcResponseMode
import com.enaboapps.switchify.pc.protocol.PcVerificationCode
import com.enaboapps.switchify.pc.storage.PcPairingStorage
import com.enaboapps.switchify.pc.storage.PcSavedPc
import com.enaboapps.switchify.pc.transport.PcBluetoothAvailability
import com.enaboapps.switchify.pc.transport.PcBluetoothException
import com.enaboapps.switchify.pc.transport.PcDiscoveredDesktop
import com.enaboapps.switchify.pc.transport.PcTransport
import com.enaboapps.switchify.pc.transport.PcUnsubscribe
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArraySet

enum class PcSessionEndReason {
    Disconnected,
    ConnectionLost,
    Failed
}

fun interface PcSessionEndListener {
    fun onSessionEnded(reason: PcSessionEndReason)
}

enum class PcRemoteNameSync {
    Synced,
    Deferred,
    Failed
}

enum class PcSendOutcome {
    Accepted,
    Rejected,
    Unconfirmed
}

private class PcRequestResult(val response: PcResponse?, val unconfirmed: Boolean = false)

class PcPairingException(val failure: PcConnectionFailure) : Exception("Pairing was not completed.")

private class PcInvalidSavedAccessException : Exception("Saved access is no longer valid.")

private class SavedConnectAttempt(val operation: Int, val desktopId: String)

class PcConnectionManager(
    private val transport: PcTransport,
    private val storage: PcPairingStorage,
    val diagnostics: PcDiagnosticLog,
    private val requestPermission: suspend () -> Boolean,
    private val clock: PcClock = PcClock.System,
    private val requestIds: PcIdGenerator = PcIdGenerator.Request,
    private val nonces: PcIdGenerator = PcIdGenerator.Nonce,
    private val reconnectDelay: suspend (Long) -> Unit = { delay(it) },
    private val remoteName: suspend () -> String = { PcRemoteName.FALLBACK },
    private val locationServicesOff: () -> Boolean = { false },
    private val pairingIntents: PcPairingIntentPublisher = PcPairingIntentPublisher.None,
    private val onUnexpectedError: (Throwable) -> Unit = {},
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
) {
    private val unexpectedErrors = CoroutineExceptionHandler { _, error -> handleUnexpectedError(error) }
    private val reportOnly = CoroutineExceptionHandler { _, error -> reportUnexpectedError(error) }

    private val _state = MutableStateFlow<PcConnectionState>(PcConnectionState.Idle(emptyList()))
    val state: StateFlow<PcConnectionState> = _state.asStateFlow()

    private val cleanups = CopyOnWriteArraySet<suspend () -> Unit>()
    private val sessionEndListeners = CopyOnWriteArraySet<PcSessionEndListener>()
    private val invalidSavedDesktopIds = mutableSetOf<String>()
    private val profileRecoveryWaits = mutableSetOf<CompletableDeferred<Boolean>>()
    private var scanStop: PcUnsubscribe? = null
    private var disconnectStop: PcUnsubscribe? = null
    private var client: PcProtocolClient? = null
    private var channel: PcCommandChannel? = null
    private var token: String? = null
    private var deviceId: String? = null
    private var operation = 0
    private var switchIntent = 0
    private var preferredConnect: Deferred<Unit>? = null
    private var savedConnectInFlight: SavedConnectAttempt? = null
    private var disconnecting: Deferred<Unit>? = null
    private var profileRecoveryDeadline: Job? = null
    private var healthTimer: Job? = null
    private var healthProbe: Deferred<Boolean>? = null
    private var protocolOperations = 0

    val commands: PcCommandChannel = object : PcCommandChannel {
        override suspend fun request(command: PcCommand, responseMode: PcResponseMode): PcResponse =
            this@PcConnectionManager.request(command, responseMode)
                ?: PcResponse.Error(null, COMMAND_FAILED, PcDiagnosticEvent.CommandFailed.message)
    }

    fun registerCleanup(cleanup: suspend () -> Unit): PcUnsubscribe {
        cleanups += cleanup
        return PcUnsubscribe { cleanups -= cleanup }
    }

    fun addSessionEndListener(listener: PcSessionEndListener): PcUnsubscribe {
        sessionEndListeners += listener
        return PcUnsubscribe { sessionEndListeners -= listener }
    }

    suspend fun load() = operate { loadNow() }

    suspend fun scan() = operate { scanNow() }

    fun stopScan(): Job = scope.launch(unexpectedErrors) {
        if (_state.value !is PcConnectionState.Scanning) return@launch
        val current = ++operation
        stopScanning()
        val saved = orderedSaved()
        if (isCurrent(current)) set(PcConnectionState.Idle(saved))
    }

    suspend fun connect(desktop: PcDiscoveredDesktop) = operate { connectNow(desktop) }

    suspend fun connectSaved(pc: PcSavedPc) = operate { connectSavedNow(pc) }

    suspend fun switchSaved(pc: PcSavedPc) = operate {
        val intent = ++switchIntent
        if (_state.value.activeDesktop?.desktopId == pc.desktopId && disconnecting == null) return@operate
        beginDisconnect(false)
        if (intent != switchIntent) return@operate
        connectSavedNow(pc)
    }

    suspend fun connectPreferred() = operate { connectPreferredNow() }

    suspend fun cancelPreferredConnection() = operate {
        if (preferredConnect == null || _state.value is PcConnectionState.Connected) return@operate
        disconnectNow(false)
    }

    suspend fun unpair(desktopId: String): Boolean = operate {
        if (_state.value.activeDesktop?.desktopId == desktopId) disconnectNow(true)
        try {
            storage.remove(desktopId)
            invalidSavedDesktopIds -= desktopId
            loadNow()
            true
        } catch (_: Exception) {
            diagnostics.add(PcDiagnosticEvent.UnpairFailed, PcDiagnosticLevel.Warning)
            set(PcConnectionState.Failed(PcConnectionFailure.UnpairFailed, orderedSaved()))
            false
        }
    }

    suspend fun listSaved(): List<PcSavedPc> = operate { orderedSaved() }

    suspend fun defaultDesktopId(): String? = storage.defaultDesktopId()

    fun isLocationOff(): Boolean = locationServicesOff()

    suspend fun setDefaultDesktopId(desktopId: String?) = operate {
        storage.setDefaultDesktopId(desktopId)
        val saved = orderedSaved()
        val current = _state.value
        if (current.savedPcs != null) set(current.withSaved(saved))
    }

    suspend fun disconnect(record: Boolean = true) = operate { disconnectNow(record) }

    suspend fun send(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.Ack): Boolean =
        sendWithOutcome(command, responseMode) == PcSendOutcome.Accepted

    suspend fun sendWithOutcome(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.Ack): PcSendOutcome {
        val result = operate { requestResultNow(command, responseMode) }
        val response = result.response
        val accepted = if (responseMode == PcResponseMode.None) response != null else response is PcResponse.Ack
        return when {
            accepted -> PcSendOutcome.Accepted
            result.unconfirmed -> PcSendOutcome.Unconfirmed
            else -> PcSendOutcome.Rejected
        }
    }

    suspend fun request(command: PcCommand, responseMode: PcResponseMode = PcResponseMode.Ack): PcResponse? =
        operate { requestNow(command, responseMode) }

    suspend fun syncRemoteName(): PcRemoteNameSync = operate {
        if (client == null || token == null || deviceId == null || _state.value !is PcConnectionState.Connected) {
            return@operate PcRemoteNameSync.Deferred
        }
        val name = remoteName()
        val response = requestNow(PcCommands.ping(name), PcResponseMode.Ack)
        if (response is PcResponse.Ack) PcRemoteNameSync.Synced else PcRemoteNameSync.Failed
    }

    private suspend fun loadNow() {
        val current = operation
        val saved = orderedSaved()
        if (isCurrent(current)) set(PcConnectionState.Idle(saved))
    }

    private suspend fun scanNow() {
        disconnectNow(false)
        val current = ++operation
        val saved = orderedSaved()
        if (!isCurrent(current)) return
        if (!requestPermission()) {
            if (isCurrent(current)) set(PcConnectionState.PermissionDenied(saved))
            return
        }
        if (!isCurrent(current)) return
        val availability = transport.availability()
        if (!isCurrent(current)) return
        if (availability != PcBluetoothAvailability.Ready) {
            set(availabilityState(availability, saved))
            return
        }
        if (locationServicesOff()) {
            set(PcConnectionState.LocationOff(saved))
            return
        }
        diagnostics.add(PcDiagnosticEvent.ScanStarted)
        val discovered = LinkedHashMap<String, PcDiscoveredDesktop>()
        set(PcConnectionState.Scanning(saved, emptyList()))
        scanStop = transport.scan(
            onDesktop = { desktop ->
                launchConfined {
                    if (!isCurrent(current)) return@launchConfined
                    discovered[desktop.desktopId] = desktop
                    set(PcConnectionState.Scanning(saved, discovered.values.toList()))
                }
            },
            onError = { launchConfined { handleScanFailure(current, saved) } }
        )
    }

    private suspend fun connectNow(desktop: PcDiscoveredDesktop) {
        val current = ++operation
        stopScanning()
        teardownConnection()
        if (!isCurrent(current)) return
        set(PcConnectionState.Connecting(desktop))
        diagnostics.add(PcDiagnosticEvent.Connecting)
        try {
            val resolved = transport.resolveAndConnect(desktop.desktopId)
            if (!isCurrent(current)) return
            connectDesktop(resolved, current, announced = true, transportConnected = true)
        } catch (_: Exception) {
            if (!isCurrent(current)) return
            fail(PcConnectionFailure.CouldNotConnect, current)
        }
    }

    private suspend fun connectSavedNow(pc: PcSavedPc) {
        val inFlight = savedConnectInFlight
        if (inFlight != null && inFlight.desktopId == pc.desktopId && isCurrent(inFlight.operation)) return
        val current = ++operation
        val attempt = SavedConnectAttempt(current, pc.desktopId)
        savedConnectInFlight = attempt
        try {
            connectSavedAttempt(pc, current)
        } finally {
            if (savedConnectInFlight === attempt) savedConnectInFlight = null
        }
    }

    private suspend fun connectSavedAttempt(pc: PcSavedPc, current: Int) {
        stopScanning()
        teardownConnection()
        if (!isCurrent(current)) return

        val savedToken = try {
            storage.token(pc.desktopId)
        } catch (_: Exception) {
            if (isCurrent(current)) fail(PcConnectionFailure.SavedAccessLoadFailed, current)
            return
        }
        if (!isCurrent(current)) return
        if (savedToken == null) {
            invalidSavedDesktopIds += pc.desktopId
            try {
                storage.remove(pc.desktopId)
            } catch (_: Exception) {
            }
            if (isCurrent(current)) fail(PcConnectionFailure.SavedAccessUnavailable, current, auth = true)
            return
        }

        val saved = orderedSaved()
        if (!isCurrent(current)) return
        if (!requestPermission()) {
            if (isCurrent(current)) set(PcConnectionState.PermissionDenied(saved, retry = pc))
            return
        }
        if (!isCurrent(current)) return
        val availability = transport.availability()
        if (!isCurrent(current)) return
        if (availability != PcBluetoothAvailability.Ready) {
            set(availabilityState(availability, saved, retry = pc))
            return
        }
        if (locationServicesOff()) {
            set(PcConnectionState.LocationOff(saved, retry = pc))
            return
        }

        val savedDesktop = with(PcList) { pc.toDesktop() }
        set(PcConnectionState.Connecting(savedDesktop))
        diagnostics.add(PcDiagnosticEvent.Connecting)
        try {
            val resolved = transport.resolveAndConnect(pc.desktopId)
            if (!isCurrent(current)) return
            connectDesktop(resolved, current, announced = true, transportConnected = true, savedToken = savedToken)
        } catch (_: Exception) {
            if (!isCurrent(current)) return
            fail(PcConnectionFailure.NotFoundNearby, current)
        }
    }

    private suspend fun connectPreferredNow() {
        preferredConnect?.let { return it.await() }
        val attempt = scope.async(start = CoroutineStart.UNDISPATCHED) {
            disconnecting?.await()
            when (_state.value) {
                is PcConnectionState.Connected,
                is PcConnectionState.Connecting,
                is PcConnectionState.Pairing,
                is PcConnectionState.Reconnecting,
                is PcConnectionState.Scanning -> return@async
                else -> Unit
            }
            val sourceOperation = operation
            val saved = orderedSaved()
            if (sourceOperation != operation) return@async
            if (saved.isEmpty()) {
                set(PcConnectionState.Idle(emptyList()))
                return@async
            }
            connectSavedNow(saved.first())
        }
        preferredConnect = attempt
        try {
            attempt.await()
        } finally {
            if (preferredConnect === attempt) preferredConnect = null
        }
    }

    private suspend fun connectDesktop(
        desktop: PcDiscoveredDesktop,
        current: Int,
        announced: Boolean = false,
        transportConnected: Boolean = false,
        savedToken: String? = null
    ) {
        set(PcConnectionState.Connecting(desktop))
        if (!announced) diagnostics.add(PcDiagnosticEvent.Connecting)
        try {
            if (!transportConnected) transport.connect(desktop.peripheralId)
            if (!isCurrent(current)) return
            val activeClient = startClient(desktop, current) ?: return
            client = activeClient
            val accessToken = if (desktop.desktopId in invalidSavedDesktopIds) {
                null
            } else {
                savedToken ?: storage.token(desktop.desktopId)
            }
            if (!isCurrent(current)) return
            if (accessToken != null) {
                deviceId = storage.deviceId()
                if (!isCurrent(current)) return
                authenticate(desktop, accessToken, current)
            } else {
                pair(desktop, current)
            }
        } catch (error: Exception) {
            if (!isCurrent(current)) return
            when (error) {
                is PcInvalidSavedAccessException -> fail(PcConnectionFailure.SavedAccessInvalid, current, auth = true)
                is PcPairingException -> fail(error.failure, current)
                else -> fail(PcConnectionFailure.CouldNotConnect, current)
            }
        }
    }

    private suspend fun startClient(desktop: PcDiscoveredDesktop, current: Int): PcProtocolClient? {
        disconnectStop = transport.subscribeDisconnect { launchConfined { unexpectedDisconnect(desktop, current) } }
        val newClient = PcProtocolClient(transport, scope)
        newClient.start { launchConfined { unexpectedDisconnect(desktop, current) } }
        if (!isCurrent(current)) {
            newClient.close()
            return null
        }
        return newClient
    }

    private suspend fun beginDisconnect(record: Boolean) {
        disconnecting?.let { return it.await() }
        preferredConnect = null
        val attempt = scope.async(start = CoroutineStart.UNDISPATCHED) { performDisconnect(record) }
        disconnecting = attempt
        try {
            attempt.await()
        } finally {
            if (disconnecting === attempt) disconnecting = null
        }
    }

    private suspend fun disconnectNow(record: Boolean) {
        switchIntent += 1
        beginDisconnect(record)
    }

    private suspend fun performDisconnect(record: Boolean) {
        val current = ++operation
        stopScanning()
        client?.cancelOutstanding()
        cleanups.toList().forEach { cleanup ->
            try {
                cleanup()
            } catch (_: Exception) {
            }
        }
        teardownConnection()
        if (!isCurrent(current)) return
        if (record) {
            diagnostics.add(PcDiagnosticEvent.CleanupComplete)
            diagnostics.add(PcDiagnosticEvent.Disconnected)
        }
        val saved = orderedSaved()
        if (isCurrent(current)) set(PcConnectionState.Idle(saved))
    }

    private suspend fun requestNow(command: PcCommand, responseMode: PcResponseMode): PcResponse? =
        requestResultNow(command, responseMode).response

    private suspend fun requestResultNow(command: PcCommand, responseMode: PcResponseMode): PcRequestResult {
        healthProbe?.await()
        val activeChannel = channel
        val sourceOperation = operation
        val desktop = (_state.value as? PcConnectionState.Connected)?.desktop
        if (activeChannel == null || token == null || deviceId == null || desktop == null) return PcRequestResult(null)
        cancelHealthTimer()
        protocolOperations += 1
        var healthyActivity = false
        var shouldProbe = false
        var timedOut = false
        try {
            val response = activeChannel.request(command, responseMode)
            healthyActivity = true
            if (responseMode == PcResponseMode.None) return PcRequestResult(response)
            when {
                response is PcResponse.Ack -> {
                    applyPointerSpeed(command)
                    return PcRequestResult(response)
                }
                response is PcResponse.SwitchProfileCatalog || response is PcResponse.PointerProfile -> return PcRequestResult(response)
                isSavedAccessRejected(response) ->
                    fail(PcConnectionFailure.AccessRevoked, operation, auth = true)
                response is PcResponse.Error && response.code == NAME_UPDATE_FAILED -> {
                    diagnostics.add(PcDiagnosticEvent.RemoteNameSyncFailed, PcDiagnosticLevel.Warning)
                    return PcRequestResult(response)
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            if (error is PcProtocolWriteException || responseMode == PcResponseMode.None) {
                launchNow { unexpectedDisconnect(desktop, sourceOperation) }
            } else {
                shouldProbe = true
                timedOut = error is PcProtocolResponseTimeoutException
            }
        } finally {
            protocolOperations -= 1
            if (protocolOperations == 0 && isCurrent(sourceOperation) && _state.value is PcConnectionState.Connected) {
                scheduleHealth(if (healthyActivity) HEALTH_INTERVAL_MS else if (shouldProbe) 0 else HEALTH_INTERVAL_MS)
            }
        }
        diagnostics.add(PcDiagnosticEvent.CommandFailed, PcDiagnosticLevel.Warning)
        return PcRequestResult(null, unconfirmed = timedOut && isCurrent(sourceOperation))
    }

    private fun applyPointerSpeed(command: PcCommand) {
        if (command.type != PcCommandTypes.POINTER_SPEED_SET) return
        val scalePercent = (command.payload["scalePercent"] as? Number)?.toDouble() ?: return
        val current = _state.value as? PcConnectionState.Connected ?: return
        val profile = current.profile ?: return
        val capabilities = profile.capabilities
        set(
            current.copy(
                profile = profile.copy(
                    capabilities = capabilities.copy(
                        pointerSpeed = capabilities.pointerSpeed.copy(scalePercent = scalePercent)
                    )
                )
            )
        )
    }

    private suspend fun pair(desktop: PcDiscoveredDesktop, current: Int) {
        val activeClient = client ?: throw PcBluetoothException(NOT_CONNECTED)
        val activeDeviceId = storage.deviceIdForPairing()
        if (!isCurrent(current)) return
        deviceId = activeDeviceId
        val requestId = requestIds.nextId()
        val nonce = nonces.nextId()
        val pairing = PcConnectionState.Pairing(
            desktop,
            PcVerificationCode.derive(desktop.desktopId, activeDeviceId, nonce)
        )
        set(pairing)
        diagnostics.add(PcDiagnosticEvent.PairingRequested)
        val published = publishPairingIntent(desktop.desktopId, activeDeviceId, nonce)
        if (!isCurrent(current)) return
        if (published) {
            diagnostics.add(PcDiagnosticEvent.PairingIntentPublished)
            set(pairing.copy(accountApprovalExpected = true))
        } else {
            diagnostics.add(PcDiagnosticEvent.PairingIntentNotPublished)
        }
        val deviceName = remoteName()
        if (!isCurrent(current)) return
        val message = PcMessages.pairingRequest(requestId, activeDeviceId, deviceName, desktop.desktopId, nonce)
        val response = try {
            activeClient.request(message, requestId, PAIRING_TIMEOUT_MS)
        } catch (_: PcProtocolResponseTimeoutException) {
            if (!isCurrent(current)) return
            throw PcPairingException(PcConnectionFailure.PairingExpired)
        }
        if (!isCurrent(current)) return
        if (response !is PcResponse.PairingComplete ||
            response.desktopId != desktop.desktopId ||
            response.deviceId != activeDeviceId
        ) {
            if (response is PcResponse.Error) diagnostics.add(PcDiagnosticEvent.PairingRejected, PcDiagnosticLevel.Warning)
            throw PcPairingException(pairingFailure(response))
        }
        storage.save(savedPc(desktop), response.token)
        if (!isCurrent(current)) return
        authenticate(desktop, response.token, current)
    }

    private suspend fun publishPairingIntent(desktopId: String, deviceId: String, nonce: String): Boolean = try {
        withTimeoutOrNull(PAIRING_INTENT_TIMEOUT_MS) { pairingIntents.publish(desktopId, deviceId, nonce) } == true
    } catch (_: Exception) {
        currentCoroutineContext().ensureActive()
        false
    }

    private suspend fun authenticate(desktop: PcDiscoveredDesktop, accessToken: String, current: Int) {
        val deviceName = remoteName()
        if (!isCurrent(current)) return
        val activeClient = client ?: throw PcBluetoothException(NOT_CONNECTED)
        val activeDeviceId = deviceId ?: throw PcBluetoothException(NOT_CONNECTED)
        val pingId = requestIds.nextId()
        val ping = PcMessages.authenticatedCommand(
            id = pingId,
            deviceId = activeDeviceId,
            token = accessToken,
            timestamp = clock.nowMillis(),
            command = PcCommands.ping(deviceName)
        )
        val response = activeClient.request(ping, pingId)
        if (!isCurrent(current)) return
        val nameUpdateFailed = response is PcResponse.Error && response.code == NAME_UPDATE_FAILED
        if (response !is PcResponse.Ack && !nameUpdateFailed) {
            if (isSavedAccessRejected(response)) {
                invalidSavedDesktopIds += desktop.desktopId
                try {
                    storage.remove(desktop.desktopId)
                } catch (_: Exception) {
                }
                throw PcInvalidSavedAccessException()
            }
            throw PcBluetoothException("Authentication failed.")
        }
        if (nameUpdateFailed) diagnostics.add(PcDiagnosticEvent.RemoteNameSyncFailed, PcDiagnosticLevel.Warning)
        token = accessToken
        channel = PcAuthenticatedCommandChannel(
            client = activeClient,
            credentials = PcCredentials(activeDeviceId, accessToken),
            clock = clock,
            requestIds = requestIds,
            requestTimeoutMs = COMMAND_TIMEOUT_MS
        )
        val profile = requestPointerProfile(accessToken, desktop, current)
        if (!isCurrent(current)) return
        storage.save(savedPc(desktop), accessToken)
        invalidSavedDesktopIds -= desktop.desktopId
        if (!isCurrent(current)) return
        diagnostics.add(PcDiagnosticEvent.Connected)
        if (profile != null) {
            set(PcConnectionState.Connected(desktop, profile, PcProfileStatus.Ready))
            scheduleHealth()
        } else {
            set(PcConnectionState.Connected(desktop, null, PcProfileStatus.Recovering))
            diagnostics.add(PcDiagnosticEvent.ProfileRecoveryStarted)
            launchNow { recoverPointerProfile(accessToken, desktop, current) }
        }
    }

    private suspend fun requestPointerProfile(accessToken: String, desktop: PcDiscoveredDesktop, current: Int): PcPointerProfile? {
        repeat(INITIAL_PROFILE_ATTEMPTS) {
            requestPointerProfileAttempt(accessToken, desktop, current)?.let { return it }
            if (!isCurrent(current)) return null
        }
        return null
    }

    private suspend fun recoverPointerProfile(accessToken: String, desktop: PcDiscoveredDesktop, current: Int) {
        val deadline = scope.launch(unexpectedErrors) {
            delay(PROFILE_RECOVERY_DEADLINE_MS)
            markProfileUnavailable(current)
        }
        profileRecoveryDeadline = deadline
        try {
            for (wait in PROFILE_RECOVERY_DELAYS_MS) {
                if (!waitForProfileRecovery(wait, current)) return
                if (!probeHealth(desktop, current, false).await() || !profileRecovering(current)) return
                val profile = requestPointerProfileAttempt(accessToken, desktop, current)
                if (!isCurrent(current)) return
                if (profile != null) {
                    val state = _state.value
                    if (state is PcConnectionState.Connected && state.profileStatus == PcProfileStatus.Recovering) {
                        set(state.copy(profile = profile, profileStatus = PcProfileStatus.Ready))
                        diagnostics.add(PcDiagnosticEvent.ProfileRecovered)
                        scheduleHealth()
                    }
                    return
                }
                if (!probeHealth(desktop, current, false).await()) return
                if (!profileRecovering(current)) return
            }
            markProfileUnavailable(current)
        } finally {
            if (profileRecoveryDeadline === deadline) {
                deadline.cancel()
                profileRecoveryDeadline = null
            }
            val state = _state.value
            if (isCurrent(current) && state is PcConnectionState.Connected && state.profileStatus == PcProfileStatus.Unavailable) {
                scheduleHealth()
            }
        }
    }

    private suspend fun requestPointerProfileAttempt(
        accessToken: String,
        desktop: PcDiscoveredDesktop,
        current: Int
    ): PcPointerProfile? {
        if (!isCurrent(current)) return null
        val activeClient = client ?: return null
        val activeDeviceId = deviceId ?: return null
        val id = requestIds.nextId()
        val response = try {
            activeClient.request(
                PcMessages.authenticatedCommand(id, activeDeviceId, accessToken, clock.nowMillis(), PcCommands.pointerProfile()),
                id,
                PROFILE_TIMEOUT_MS
            )
        } catch (error: Exception) {
            if (error is PcProtocolWriteException && isCurrent(current)) launchNow { unexpectedDisconnect(desktop, current) }
            null
        }
        if (!isCurrent(current)) return null
        return (response as? PcResponse.PointerProfile)?.profile
    }

    private fun profileRecovering(current: Int): Boolean {
        val state = _state.value
        return isCurrent(current) && state is PcConnectionState.Connected && state.profileStatus == PcProfileStatus.Recovering
    }

    private fun markProfileUnavailable(current: Int) {
        val state = _state.value
        if (!isCurrent(current) || state !is PcConnectionState.Connected || state.profileStatus != PcProfileStatus.Recovering) return
        set(state.copy(profileStatus = PcProfileStatus.Unavailable))
        diagnostics.add(PcDiagnosticEvent.ProfileRecoveryExhausted, PcDiagnosticLevel.Warning)
    }

    private suspend fun waitForProfileRecovery(milliseconds: Long, current: Int): Boolean {
        val signal = CompletableDeferred<Boolean>()
        profileRecoveryWaits += signal
        val timer = scope.launch(unexpectedErrors) {
            delay(milliseconds)
            profileRecoveryWaits -= signal
            signal.complete(isCurrent(current))
        }
        return try {
            signal.await()
        } finally {
            timer.cancel()
            profileRecoveryWaits -= signal
        }
    }

    private fun cancelProfileRecovery() {
        profileRecoveryDeadline?.cancel()
        profileRecoveryDeadline = null
        val waits = profileRecoveryWaits.toList()
        profileRecoveryWaits.clear()
        waits.forEach { it.complete(false) }
    }

    private suspend fun unexpectedDisconnect(desktop: PcDiscoveredDesktop, sourceOperation: Int) {
        val state = _state.value
        if (!isCurrent(sourceOperation) ||
            state is PcConnectionState.Idle ||
            state is PcConnectionState.Reconnecting ||
            state is PcConnectionState.Failed
        ) {
            return
        }
        val current = ++operation
        cancelHealthTimer()
        set(PcConnectionState.Reconnecting(desktop, 1))
        diagnostics.add(PcDiagnosticEvent.ConnectionLost, PcDiagnosticLevel.Warning)
        teardownConnection()
        val savedToken = try {
            storage.token(desktop.desktopId)
        } catch (_: Exception) {
            null
        }
        if (savedToken == null || !isCurrent(current)) {
            if (isCurrent(current)) fail(PcConnectionFailure.ConnectionLost, current)
            return
        }
        for (attempt in 1..RECONNECT_ATTEMPTS) {
            if (!isCurrent(current)) return
            set(PcConnectionState.Reconnecting(desktop, attempt))
            reconnectDelay(attempt * RECONNECT_BASE_DELAY_MS)
            if (!isCurrent(current)) return
            try {
                val resolved = transport.resolveAndConnect(desktop.desktopId)
                if (!isCurrent(current)) return
                client = startClient(resolved, current) ?: return
                deviceId = storage.deviceId()
                if (!isCurrent(current)) return
                authenticate(resolved, savedToken, current)
                if (isCurrent(current) && _state.value is PcConnectionState.Connected) return
            } catch (error: Exception) {
                if (!isCurrent(current)) return
                if (error is PcInvalidSavedAccessException) {
                    fail(PcConnectionFailure.SavedAccessInvalid, current, auth = true)
                    return
                }
                teardownConnection()
            }
        }
        if (isCurrent(current)) fail(PcConnectionFailure.ConnectionLost, current)
    }

    private suspend fun fail(failure: PcConnectionFailure, current: Int, auth: Boolean = false) {
        if (!isCurrent(current)) return
        val saved = orderedSaved()
        if (!isCurrent(current)) return
        teardownConnection()
        if (!isCurrent(current)) return
        if (auth) diagnostics.add(PcDiagnosticEvent.AuthenticationFailed, PcDiagnosticLevel.Error)
        set(PcConnectionState.Failed(failure, saved))
    }

    private suspend fun teardownConnection() {
        cancelProfileRecovery()
        cancelHealthTimer()
        disconnectStop?.unsubscribe()
        disconnectStop = null
        val activeClient = client
        client = null
        channel = null
        token = null
        activeClient?.close()
        try {
            transport.disconnect()
        } catch (_: Exception) {
        }
    }

    private fun stopScanning() {
        scanStop?.unsubscribe()
        scanStop = null
    }

    private fun scheduleHealth(delayMs: Long = HEALTH_INTERVAL_MS) {
        cancelHealthTimer()
        val state = _state.value
        if (state !is PcConnectionState.Connected || state.profileStatus == PcProfileStatus.Recovering) return
        val current = operation
        val desktop = state.desktop
        healthTimer = scope.launch(unexpectedErrors) {
            delay(delayMs)
            healthTimer = null
            if (!isCurrent(current) || _state.value !is PcConnectionState.Connected) return@launch
            if (protocolOperations > 0 || healthProbe != null) {
                scheduleHealth()
                return@launch
            }
            probeHealth(desktop, current, true)
        }
    }

    private fun probeHealth(desktop: PcDiscoveredDesktop, current: Int, scheduleOnSuccess: Boolean): Deferred<Boolean> {
        healthProbe?.let { return it }
        val probe = scope.async(start = CoroutineStart.UNDISPATCHED) {
            val healthy = try {
                transport.verifyConnection(desktop.desktopId)
            } catch (_: Exception) {
                false
            }
            if (!isCurrent(current) || _state.value !is PcConnectionState.Connected) return@async false
            if (healthy) {
                if (scheduleOnSuccess) scheduleHealth()
                return@async true
            }
            diagnostics.add(PcDiagnosticEvent.ConnectionHealthFailed, PcDiagnosticLevel.Warning)
            launchNow { unexpectedDisconnect(desktop, current) }
            false
        }
        healthProbe = probe
        probe.invokeOnCompletion { if (healthProbe === probe) healthProbe = null }
        return probe
    }

    private fun cancelHealthTimer() {
        healthTimer?.cancel()
        healthTimer = null
    }

    private suspend fun orderedSaved(): List<PcSavedPc> {
        val saved = storage.list().filter { it.desktopId !in invalidSavedDesktopIds }
        val defaultId = try {
            storage.defaultDesktopId()
        } catch (_: Exception) {
            null
        }
        return if (defaultId != null) saved.sortedByDescending { it.desktopId == defaultId } else saved
    }

    private suspend fun handleScanFailure(current: Int, saved: List<PcSavedPc>) {
        if (!isCurrent(current)) return
        val availability = try {
            transport.availability()
        } catch (_: Exception) {
            PcBluetoothAvailability.PoweredOff
        }
        if (!isCurrent(current)) return
        diagnostics.add(PcDiagnosticEvent.ScanFailed, PcDiagnosticLevel.Error)
        set(
            if (availability == PcBluetoothAvailability.Ready) {
                PcConnectionState.Failed(PcConnectionFailure.DiscoveryFailed, saved)
            } else {
                availabilityState(availability, saved)
            }
        )
    }

    private fun availabilityState(
        availability: PcBluetoothAvailability,
        saved: List<PcSavedPc>,
        retry: PcSavedPc? = null
    ): PcConnectionState =
        when (availability) {
            PcBluetoothAvailability.Unauthorized -> PcConnectionState.PermissionDenied(saved, retry)
            PcBluetoothAvailability.Unsupported -> PcConnectionState.Unsupported(saved)
            else -> PcConnectionState.BluetoothOff(saved)
        }

    private fun savedPc(desktop: PcDiscoveredDesktop) = PcSavedPc(
        desktopId = desktop.desktopId,
        displayName = desktop.displayName,
        platform = desktop.platform,
        peripheralId = desktop.peripheralId,
        lastConnectedAt = clock.nowMillis()
    )

    private fun pairingFailure(response: PcResponse): PcConnectionFailure {
        val error = response as? PcResponse.Error ?: return PcConnectionFailure.CouldNotConnect
        return when {
            error.message == PAIRING_REJECTED || error.code == PAIRING_REJECTED -> PcConnectionFailure.PairingRejected
            error.message == PAIRING_EXPIRED || error.code == PAIRING_EXPIRED -> PcConnectionFailure.PairingExpired
            else -> PcConnectionFailure.CouldNotConnect
        }
    }

    private fun handleUnexpectedError(error: Throwable) {
        reportUnexpectedError(error)
        scope.launch(reportOnly) { recoverFromUnexpectedError() }
    }

    private fun reportUnexpectedError(error: Throwable) {
        try {
            onUnexpectedError(error)
        } catch (_: Exception) {
        }
    }

    private suspend fun recoverFromUnexpectedError() {
        val failure = when (_state.value) {
            is PcConnectionState.Reconnecting -> PcConnectionFailure.ConnectionLost
            is PcConnectionState.Connecting, is PcConnectionState.Pairing -> PcConnectionFailure.CouldNotConnect
            else -> return
        }
        val current = ++operation
        val saved = try {
            orderedSaved()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        try {
            teardownConnection()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
        if (isCurrent(current)) set(PcConnectionState.Failed(failure, saved))
    }

    private fun isSavedAccessRejected(response: PcResponse): Boolean =
        response is PcResponse.Error && (response.code == INVALID_AUTH || response.code == UNKNOWN_DEVICE)

    private fun isCurrent(value: Int) = value == operation

    private fun set(next: PcConnectionState) {
        val previous = _state.value
        _state.value = next
        if (previous is PcConnectionState.Connected && next !is PcConnectionState.Connected) {
            val reason = when (next) {
                is PcConnectionState.Reconnecting -> PcSessionEndReason.ConnectionLost
                is PcConnectionState.Failed -> PcSessionEndReason.Failed
                else -> PcSessionEndReason.Disconnected
            }
            sessionEndListeners.forEach { listener ->
                try {
                    listener.onSessionEnded(reason)
                } catch (_: Exception) {
                }
            }
        }
    }

    private suspend fun <T> operate(block: suspend () -> T): T = scope.async { block() }.await()

    private fun launchNow(block: suspend () -> Unit) {
        scope.launch(unexpectedErrors, start = CoroutineStart.UNDISPATCHED) { block() }
    }

    private fun launchConfined(block: suspend () -> Unit) {
        scope.launch(unexpectedErrors) { block() }
    }

    companion object {
        const val PC_PAIRING_EXPIRY_MS = 120_000L
        const val PAIRING_GRACE_MS = 5_000L
        const val PAIRING_TIMEOUT_MS = PC_PAIRING_EXPIRY_MS + PAIRING_GRACE_MS
        const val PAIRING_INTENT_TIMEOUT_MS = 3_000L
        const val COMMAND_TIMEOUT_MS = 5_000L
        const val PROFILE_TIMEOUT_MS = 5_000L
        const val HEALTH_INTERVAL_MS = 5_000L
        const val PROFILE_RECOVERY_DEADLINE_MS = 22_000L
        val PROFILE_RECOVERY_DELAYS_MS = listOf(1_000L, 2_000L, 4_000L)
        const val INITIAL_PROFILE_ATTEMPTS = 2
        const val RECONNECT_ATTEMPTS = 3
        const val RECONNECT_BASE_DELAY_MS = 500L
        const val INVALID_AUTH = "invalid_auth"
        const val UNKNOWN_DEVICE = "unknown_device"
        const val NAME_UPDATE_FAILED = "name_update_failed"
        const val PAIRING_REJECTED = "pairing_rejected"
        const val PAIRING_EXPIRED = "pairing_request_expired"
        const val COMMAND_FAILED = "command_failed"
        private const val NOT_CONNECTED = "No PC is connected."
    }
}
