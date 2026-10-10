package com.enaboapps.switchify.pc.transport

import com.enaboapps.switchify.pc.protocol.PcBleUuids
import com.enaboapps.switchify.pc.protocol.PcPlatform
import com.enaboapps.switchify.pc.protocol.PcResponseTransport
import com.enaboapps.switchify.pc.protocol.PcResponses
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class PcBleTransport(
    private val adapter: PcGattAdapter,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
    private val nativeTimeoutMs: Long = DEFAULT_NATIVE_TIMEOUT_MS,
    private val stageObserver: PcConnectionStageObserver? = null
) : PcTransport {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    @Volatile
    private var device: PcGattConnection? = null

    @Volatile
    private var operation = 0

    @Volatile
    private var writePoisoned = false

    @Volatile
    private var readReplies = false

    @Volatile
    private var responsePoller: PcReadResponsePoller? = null

    private val scanDevices = LinkedHashMap<String, Job>()
    private val scanKeys = LinkedHashSet<String>()
    private val scanTasks = mutableSetOf<Job>()
    private val activeJobs = mutableSetOf<Job>()
    private val writeJobs = mutableSetOf<Job>()
    private val connectMutex = Mutex()
    private var resolutionCancel: (suspend (Throwable) -> Unit)? = null

    private val cancellationTimeoutMs: Long get() = minOf(MAX_CANCELLATION_TIMEOUT_MS, nativeTimeoutMs)

    override suspend fun availability(): PcBluetoothAvailability = when (adapter.state()) {
        PcGattAdapterState.PoweredOn -> PcBluetoothAvailability.Ready
        PcGattAdapterState.Unauthorized -> PcBluetoothAvailability.Unauthorized
        PcGattAdapterState.Unsupported -> PcBluetoothAvailability.Unsupported
        PcGattAdapterState.PoweredOff -> PcBluetoothAvailability.PoweredOff
    }

    override fun scan(onDesktop: (PcDiscoveredDesktop) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe {
        val session = ScanSession(onDesktop, onError)
        scope.launch { startScan(session) }
        return PcUnsubscribe { scope.launch { stopScan(session) } }
    }

    override suspend fun connect(peripheralId: String) = withContext(dispatcher) {
        disconnectConfined()
        val connectOperation = ++operation
        val job = scope.async { connectMutex.withLock { connectConfined(peripheralId, connectOperation) } }
        awaitActive(job, "Bluetooth connection was cancelled.")
    }

    override suspend fun resolveAndConnect(desktopId: String): PcDiscoveredDesktop = withContext(dispatcher) {
        disconnectConfined()
        val resolveOperation = ++operation
        recordStage(PcConnectionStage.Resolution, PcConnectionStageOutcome.Started, resolveOperation)
        val resolution = Resolution(desktopId, resolveOperation)
        resolution.start()
        try {
            resolution.result.await()
        } finally {
            if (!resolution.result.isCompleted) {
                withContext(NonCancellable) {
                    resolution.cancel(PcBluetoothException("Bluetooth operation was cancelled."))
                }
            }
        }
    }

    override suspend fun disconnect() = withContext(dispatcher) { disconnectConfined() }

    override fun maxWriteValueBytes(): Int =
        ((requireDevice().mtu ?: DEFAULT_ATT_MTU) - ATT_HEADER_BYTES).coerceIn(0, MAX_WRITE_VALUE_BYTES)

    override suspend fun writeFrame(frame: ByteArray) = withContext(dispatcher) {
        val connection = requireDevice()
        if (writePoisoned) throw PcBluetoothException("Bluetooth writes are unavailable until reconnect.")
        val write = scope.async { connection.write(PcBleUuids.receive, frame) }
        writeJobs += write
        try {
            withTimeout(nativeTimeoutMs) { write.await() }
        } catch (_: TimeoutCancellationException) {
            currentCoroutineContext().ensureActive()
            abandonWrite(write)
            throw PcBluetoothException("Bluetooth write timed out.")
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw PcBluetoothException("Bluetooth write was cancelled.", error)
        } catch (error: PcBluetoothException) {
            throw error
        } catch (error: Exception) {
            throw PcBluetoothException("Bluetooth write failed.", error)
        } finally {
            if (!write.isCompleted) abandonWrite(write)
            writeJobs -= write
        }
    }

    override suspend fun cancelPendingWrites() = withContext(dispatcher) { cancelPendingWritesConfined() }

    override suspend fun verifyConnection(desktopId: String): Boolean = withContext(dispatcher) {
        try {
            val connection = requireDevice()
            bounded(minOf(HEALTH_CHECK_TIMEOUT_MS, nativeTimeoutMs)) {
                if (!connection.isConnected()) return@bounded false
                val value = connection.read(PcBleUuids.status) ?: return@bounded false
                PcResponses.parseStatus(value.toString(Charsets.UTF_8))?.desktopId == desktopId
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    override fun subscribe(onFrame: (ByteArray) -> Unit, onError: (Throwable) -> Unit): PcUnsubscribe {
        val connection = requireDevice()
        val subscribeOperation = operation
        if (readReplies) {
            if (responsePoller != null || writePoisoned) {
                throw PcBluetoothException("Reconnect before starting response reads again.")
            }
            val poller = PcReadResponsePoller(
                scope = scope,
                read = { connection.read(PcBleUuids.response) },
                cancelRead = {},
                onFrame = { frame -> if (subscribeOperation == operation) onFrame(frame) },
                onError = { error ->
                    if (subscribeOperation == operation) {
                        writePoisoned = true
                        onError(error)
                    }
                },
                timeoutMs = nativeTimeoutMs
            )
            responsePoller = poller
            return PcUnsubscribe {
                poller.stop()
                if (responsePoller === poller) writePoisoned = true
            }
        }
        var active = true
        var failed = false
        val recordFailure = {
            if (active && !failed) {
                recordStage(PcConnectionStage.Notifications, PcConnectionStageOutcome.Failed, subscribeOperation)
            }
            failed = true
        }
        recordStage(PcConnectionStage.Notifications, PcConnectionStageOutcome.Started, subscribeOperation)
        try {
            val subscription = connection.monitor(
                PcBleUuids.transmit,
                onValue = onFrame,
                onError = { error ->
                    recordFailure()
                    onError(error)
                }
            )
            if (!failed) {
                recordStage(PcConnectionStage.Notifications, PcConnectionStageOutcome.Succeeded, subscribeOperation)
            }
            return PcUnsubscribe {
                active = false
                subscription.unsubscribe()
            }
        } catch (error: Exception) {
            recordFailure()
            throw error
        }
    }

    override suspend fun notificationsReady() = withContext(dispatcher) {
        if (readReplies) {
            val poller = responsePoller ?: throw PcBluetoothException("Bluetooth response reader has not started.")
            poller.ready.await()
            return@withContext
        }
        stage(PcConnectionStage.NotificationReady, operation) {
            val value = bounded {
                requireDevice().readDescriptor(PcBleUuids.transmit, PcBleUuids.clientCharacteristicConfiguration)
            }
            if (value == null || !value.contentEquals(ENABLE_NOTIFICATION_VALUE)) {
                throw PcBluetoothException("Bluetooth notifications could not be enabled.")
            }
        }
    }

    override fun subscribeDisconnect(onDisconnect: () -> Unit): PcUnsubscribe =
        requireDevice().onDisconnected(onDisconnect)

    private inner class ScanSession(
        val onDesktop: (PcDiscoveredDesktop) -> Unit,
        val onError: (Throwable) -> Unit
    ) {
        var active = true
        var operation = 0
        val waiting = LinkedHashMap<String, PcGattAdvertisement>()
    }

    private fun startScan(session: ScanSession) {
        if (!session.active) return
        session.operation = ++operation
        try {
            adapter.startScan(
                PcBleUuids.service,
                onAdvertisement = { advertisement -> scope.launch { onScanAdvertisement(session, advertisement) } },
                onError = { error ->
                    scope.launch { if (session.active && session.operation == operation) session.onError(error) }
                }
            )
        } catch (error: Exception) {
            session.onError(error)
        }
    }

    private fun onScanAdvertisement(session: ScanSession, advertisement: PcGattAdvertisement) {
        val scanOperation = session.operation
        if (!session.active || scanOperation != operation) return
        val key = scanKey(advertisement.peripheralId)
        if (scanDevices.containsKey(advertisement.peripheralId) || key in scanKeys) return
        if (scanDevices.size >= MAX_CONCURRENT_PROBES) {
            if (session.waiting.size < MAX_WAITING_PROBES) session.waiting[advertisement.peripheralId] = advertisement
            return
        }
        session.waiting.remove(advertisement.peripheralId)
        scanKeys += key
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            var retainCompletedKey = false
            try {
                val desktop = readStatus(advertisement)
                retainCompletedKey = desktop?.platform == PcPlatform.Windows
                val current = session.active && scanOperation == operation
                if (current && retainCompletedKey && scanKeys.size > MAX_RETAINED_SCAN_KEYS) {
                    val probing = scanDevices.keys.map(::scanKey).toSet()
                    scanKeys.firstOrNull { it !in probing }?.let(scanKeys::remove)
                }
                if (current && desktop != null) session.onDesktop(desktop)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
            } finally {
                if (scanOperation == operation) {
                    scanDevices.remove(advertisement.peripheralId)
                    if (!retainCompletedKey) scanKeys.remove(key)
                    session.waiting.values.firstOrNull()?.let { onScanAdvertisement(session, it) }
                }
                scanTasks -= job
            }
        }
        scanDevices[advertisement.peripheralId] = job
        scanTasks += job
        job.start()
    }

    private fun stopScan(session: ScanSession) {
        if (!session.active) return
        session.active = false
        session.waiting.clear()
        if (session.operation == operation) operation += 1
        adapter.stopScan()
        cancelProbes()
    }

    private inner class Resolution(private val desktopId: String, private val resolveOperation: Int) {
        val result = CompletableDeferred<PcDiscoveredDesktop>()
        private var active = true
        private var claimedPeripheralId: String? = null
        private var cancellation: Job? = null
        private var timer: Job? = null
        private val waiting = LinkedHashMap<String, PcGattAdvertisement>()
        private val cancelHandle: suspend (Throwable) -> Unit = { error -> cancel(error) }

        fun start() {
            resolutionCancel = cancelHandle
            timer = scope.launch {
                delay(nativeTimeoutMs)
                cancel(PcBluetoothException("Saved PC discovery timed out."), PcConnectionStageOutcome.TimedOut)
            }
            try {
                adapter.startScan(
                    PcBleUuids.service,
                    onAdvertisement = { advertisement -> scope.launch { onAdvertisement(advertisement) } },
                    onError = { scope.launch { cancel(PcBluetoothException("Saved PC discovery failed.")) } }
                )
            } catch (_: Exception) {
                scope.launch { cancel(PcBluetoothException("Saved PC discovery failed.")) }
            }
        }

        private fun succeed(desktop: PcDiscoveredDesktop) {
            if (!active) return
            recordStage(PcConnectionStage.Resolution, PcConnectionStageOutcome.Succeeded, resolveOperation)
            active = false
            waiting.clear()
            timer?.cancel()
            adapter.stopScan()
            scanKeys.clear()
            if (resolutionCancel === cancelHandle) resolutionCancel = null
            result.complete(desktop)
        }

        suspend fun cancel(error: Throwable, outcome: PcConnectionStageOutcome = PcConnectionStageOutcome.Failed) {
            if (!active) {
                cancellation?.join()
                return
            }
            recordStage(PcConnectionStage.Resolution, outcome, resolveOperation)
            active = false
            waiting.clear()
            timer?.cancel()
            adapter.stopScan()
            val probes = scanDevices.values.toList()
            val retained = device
            device = null
            scanDevices.clear()
            scanKeys.clear()
            cancelActiveJobs()
            probes.forEach(Job::cancel)
            val cleanup = scope.launch {
                withContext(NonCancellable) {
                    retained?.let { disconnectQuietly(it) }
                    withTimeoutOrNull(cancellationTimeoutMs) { probes.joinAll() }
                }
                if (resolutionCancel === cancelHandle) resolutionCancel = null
                result.completeExceptionally(error)
            }
            cancellation = cleanup
            cleanup.join()
        }

        private fun onAdvertisement(advertisement: PcGattAdvertisement) {
            if (!active || resolveOperation != operation || claimedPeripheralId != null) return
            val key = scanKey(advertisement.peripheralId)
            if (scanDevices.containsKey(advertisement.peripheralId) || key in scanKeys) return
            if (scanDevices.size >= MAX_CONCURRENT_PROBES) {
                if (waiting.size < MAX_WAITING_PROBES) waiting[advertisement.peripheralId] = advertisement
                return
            }
            waiting.remove(advertisement.peripheralId)
            scanKeys += key
            lateinit var job: Job
            job = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val desktop = readStatus(advertisement) { candidate -> claim(advertisement, candidate) }
                    if (!active || resolveOperation != operation || desktop?.desktopId != desktopId ||
                        claimedPeripheralId != advertisement.peripheralId
                    ) {
                        return@launch
                    }
                    scanDevices.remove(advertisement.peripheralId)
                    adapter.stopScan()
                    val otherProbes = scanDevices.values.toList()
                    scanDevices.clear()
                    otherProbes.forEach(Job::cancel)
                    val connected = requireDevice()
                    requestHighPriority(connected, resolveOperation)
                    ensureResolving()
                    stage(PcConnectionStage.Mtu, resolveOperation) { bounded { connected.requestMtu(REQUESTED_MTU) } }
                    ensureResolving()
                    stage(PcConnectionStage.Services, resolveOperation) { bounded { connected.discoverServices() } }
                    ensureResolving()
                    readReplies = desktop.responseTransport == PcResponseTransport.ReadV1
                    writePoisoned = false
                    succeed(desktop)
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    if (device?.peripheralId == advertisement.peripheralId) {
                        cancel(error as? PcBluetoothException ?: PcBluetoothException("Saved PC discovery failed."))
                    }
                } finally {
                    if (resolveOperation == operation) {
                        scanDevices.remove(advertisement.peripheralId)
                        scanKeys.remove(key)
                        waiting.values.firstOrNull()?.let(::onAdvertisement)
                    }
                    scanTasks -= job
                }
            }
            scanDevices[advertisement.peripheralId] = job
            scanTasks += job
            job.start()
        }

        private fun claim(advertisement: PcGattAdvertisement, candidate: PcDiscoveredDesktop): Boolean {
            if (!active || resolveOperation != operation) return false
            val matches = candidate.desktopId == desktopId
            recordStage(
                PcConnectionStage.SelectedMatch,
                if (matches) PcConnectionStageOutcome.Succeeded else PcConnectionStageOutcome.NotMatched,
                resolveOperation
            )
            if (!matches || claimedPeripheralId != null) return false
            claimedPeripheralId = advertisement.peripheralId
            waiting.clear()
            return true
        }

        private fun ensureResolving() {
            if (!active || resolveOperation != operation) throw PcBluetoothException("Bluetooth connection was cancelled.")
        }
    }

    private suspend fun connectConfined(peripheralId: String, connectOperation: Int) {
        var connection: PcGattConnection? = null
        try {
            ensureOperation(connectOperation)
            connection = stage(PcConnectionStage.Connect, connectOperation) { bounded { adapter.connect(peripheralId) } }
            ensureOperation(connectOperation)
            device = connection
            requestHighPriority(connection, connectOperation)
            ensureOperation(connectOperation)
            stage(PcConnectionStage.Mtu, connectOperation) { bounded { connection.requestMtu(REQUESTED_MTU) } }
            ensureOperation(connectOperation)
            stage(PcConnectionStage.Services, connectOperation) { bounded { connection.discoverServices() } }
            ensureOperation(connectOperation)
            val statusValue = bounded { connection.read(PcBleUuids.status) }
            ensureOperation(connectOperation)
            val status = statusValue?.let { PcResponses.parseStatus(it.toString(Charsets.UTF_8)) }
                ?: throw PcBluetoothException("Bluetooth discovery status is invalid.")
            readReplies = status.responseTransport == PcResponseTransport.ReadV1
            writePoisoned = false
        } catch (error: Throwable) {
            connection?.let { withContext(NonCancellable) { disconnectQuietly(it) } }
            if (connectOperation == operation) device = null
            throw error
        }
    }

    private suspend fun disconnectConfined() {
        operation += 1
        responsePoller?.stop()
        responsePoller = null
        readReplies = false
        resolutionCancel?.invoke(PcBluetoothException("Bluetooth operation was cancelled."))
        cancelActiveJobs()
        cancelPendingWritesConfined()
        adapter.stopScan()
        cancelProbes()
        scanTasks.toList().joinAll()
        val current = device
        device = null
        current?.let { disconnectQuietly(it) }
    }

    private fun cancelPendingWritesConfined() {
        writeJobs.toList().forEach(::abandonWrite)
    }

    private fun abandonWrite(write: Job) {
        if (write.isCompleted) return
        write.cancel()
        writePoisoned = true
    }

    private fun cancelProbes() {
        val probes = scanDevices.values.toList()
        scanDevices.clear()
        scanKeys.clear()
        probes.forEach(Job::cancel)
    }

    private fun cancelActiveJobs() {
        activeJobs.toList().forEach(Job::cancel)
    }

    private suspend fun <T> awaitActive(job: Deferred<T>, cancelledMessage: String): T {
        activeJobs += job
        try {
            return job.await()
        } catch (error: CancellationException) {
            currentCoroutineContext().ensureActive()
            throw PcBluetoothException(cancelledMessage, error)
        } finally {
            if (!job.isCompleted) job.cancel()
            activeJobs -= job
        }
    }

    private suspend fun readStatus(
        advertisement: PcGattAdvertisement,
        retain: (PcDiscoveredDesktop) -> Boolean = { false }
    ): PcDiscoveredDesktop? {
        val probeOperation = operation
        var connection: PcGattConnection? = null
        var retained = false
        try {
            val probe = stage(PcConnectionStage.ProbeConnect, probeOperation) {
                bounded { adapter.connect(advertisement.peripheralId) }
            }
            connection = probe
            stage(PcConnectionStage.ProbeServices, probeOperation) { bounded { probe.discoverServices() } }
            val value = stage(PcConnectionStage.StatusRead, probeOperation) { bounded { probe.read(PcBleUuids.status) } }
            recordStage(PcConnectionStage.StatusParse, PcConnectionStageOutcome.Started, probeOperation)
            val status = value?.let { PcResponses.parseStatus(it.toString(Charsets.UTF_8)) }
            recordStage(
                PcConnectionStage.StatusParse,
                if (status != null) PcConnectionStageOutcome.Succeeded else PcConnectionStageOutcome.Failed,
                probeOperation
            )
            val desktop = status?.let {
                PcDiscoveredDesktop(
                    desktopId = it.desktopId,
                    displayName = PcDesktopDisplayName.desktopDisplayName(it, advertisement.name, advertisement.localName),
                    platform = it.platform,
                    responseTransport = it.responseTransport,
                    peripheralId = advertisement.peripheralId,
                    rssi = advertisement.rssi
                )
            }
            if (desktop != null && retain(desktop)) {
                device = probe
                retained = true
            }
            return desktop
        } finally {
            if (!retained) connection?.let { withContext(NonCancellable) { disconnectQuietly(it) } }
        }
    }

    private suspend fun requestHighPriority(connection: PcGattConnection, priorityOperation: Int) {
        try {
            stage(PcConnectionStage.Priority, priorityOperation) { bounded { connection.requestHighPriority() } }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
        }
    }

    private suspend fun disconnectQuietly(connection: PcGattConnection) {
        withTimeoutOrNull(cancellationTimeoutMs) {
            try {
                connection.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    private fun ensureOperation(expected: Int) {
        if (expected != operation) throw PcBluetoothException("Bluetooth connection was cancelled.")
    }

    private fun requireDevice(): PcGattConnection = device ?: throw PcBluetoothException("No PC is connected.")

    private fun recordStage(stage: PcConnectionStage, outcome: PcConnectionStageOutcome, stageOperation: Int) {
        if (stageOperation != operation) return
        try {
            stageObserver?.onConnectionStage(stage, outcome)
        } catch (_: Exception) {
        }
    }

    private suspend fun <T> stage(stage: PcConnectionStage, stageOperation: Int, action: suspend () -> T): T {
        recordStage(stage, PcConnectionStageOutcome.Started, stageOperation)
        try {
            val result = action()
            recordStage(stage, PcConnectionStageOutcome.Succeeded, stageOperation)
            return result
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            recordStage(stage, PcConnectionStageOutcome.Failed, stageOperation)
            throw error
        }
    }

    private suspend fun <T> bounded(timeoutMs: Long = nativeTimeoutMs, action: suspend () -> T): T = try {
        withTimeout(timeoutMs) { action() }
    } catch (_: TimeoutCancellationException) {
        currentCoroutineContext().ensureActive()
        throw PcBluetoothException("Bluetooth operation timed out.")
    }

    private fun scanKey(peripheralId: String) = "id:$peripheralId"

    companion object {
        const val REQUESTED_MTU = 517
        const val ATT_HEADER_BYTES = 3
        const val DEFAULT_ATT_MTU = 23
        const val MAX_WRITE_VALUE_BYTES = 512
        const val DEFAULT_NATIVE_TIMEOUT_MS = 10_000L
        const val HEALTH_CHECK_TIMEOUT_MS = 4_000L
        const val MAX_CANCELLATION_TIMEOUT_MS = 1_000L
        const val MAX_CONCURRENT_PROBES = 4
        const val MAX_WAITING_PROBES = 32
        const val MAX_RETAINED_SCAN_KEYS = 256
        val ENABLE_NOTIFICATION_VALUE = byteArrayOf(0x01, 0x00)
    }
}
