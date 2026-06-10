package com.enaboapps.switchify.pc

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.charset.StandardCharsets

enum class PcDiscoveryStatus {
    Idle,
    Searching,
    Found,
    Empty,
    Failed
}

interface PcDiscovery {
    val pcs: StateFlow<List<DiscoveredPc>>
    val status: StateFlow<PcDiscoveryStatus>
    fun startDiscovery()
    fun stopDiscovery()
}

/**
 * Holds a Wi-Fi multicast lock while discovery is active so mDNS responses
 * are not dropped by the Wi-Fi driver's multicast filter.
 */
interface PcMulticastLock {
    fun acquire()
    fun release()
}

/**
 * Idempotent acquire/release wrapper so discovery restarts and repeated stops
 * never unbalance the underlying lock.
 */
class PcMulticastLockSession(private val lock: PcMulticastLock) {
    private var held = false

    fun acquire() {
        if (held) return
        runCatching { lock.acquire() }
        held = true
    }

    fun release() {
        if (!held) return
        runCatching { lock.release() }
        held = false
    }
}

private class WifiMulticastLock(context: Context) : PcMulticastLock {
    private val multicastLock = (context.getSystemService(Context.WIFI_SERVICE) as WifiManager)
        .createMulticastLock("switchify_pc_discovery")
        .apply { setReferenceCounted(false) }

    override fun acquire() {
        multicastLock.acquire()
    }

    override fun release() {
        if (multicastLock.isHeld) multicastLock.release()
    }
}

class PcDiscoveryService(
    context: Context,
    multicastLock: PcMulticastLock = WifiMulticastLock(context.applicationContext)
) : PcDiscovery {
    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val multicastLockSession = PcMulticastLockSession(multicastLock)
    private val discovered = linkedMapOf<String, DiscoveredPc>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val resolveSequencer = PcResolveSequencer<NsdServiceInfo>()
    private val retriedServiceNames = mutableSetOf<String>()

    private val _pcs = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    override val pcs: StateFlow<List<DiscoveredPc>> = _pcs

    private val _status = MutableStateFlow(PcDiscoveryStatus.Idle)
    override val status: StateFlow<PcDiscoveryStatus> = _status

    override fun startDiscovery() {
        stopDiscovery()
        discovered.clear()
        synchronized(retriedServiceNames) { retriedServiceNames.clear() }
        _pcs.value = emptyList()
        _status.value = PcDiscoveryStatus.Searching

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _status.value = PcDiscoveryStatus.Searching
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!isSwitchifyServiceType(serviceInfo.serviceType)) return
                resolve(serviceInfo)
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discovered.values.firstOrNull { it.serviceName == serviceInfo.serviceName }?.let {
                    discovered.remove(it.desktopId)
                    publish()
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                if (_pcs.value.isEmpty()) _status.value = PcDiscoveryStatus.Empty
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                _status.value = PcDiscoveryStatus.Failed
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                discoveryListener = null
            }
        }
        discoveryListener = listener
        multicastLockSession.acquire()
        nsdManager.discoverServices(SWITCHIFY_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun stopDiscovery() {
        resolveSequencer.clear()
        multicastLockSession.release()
        val listener = discoveryListener ?: return
        runCatching { nsdManager.stopServiceDiscovery(listener) }
        discoveryListener = null
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        resolveSequencer.enqueue(serviceInfo)?.let(::startResolve)
    }

    private fun startResolve(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(failedService: NsdServiceInfo, errorCode: Int) {
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && shouldRetry(failedService)) {
                    resolveSequencer.enqueue(failedService)
                }
                resolveSequencer.finish()?.let(::startResolve)
            }

            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                val pc = parse(resolvedService)
                if (pc != null) {
                    discovered[pc.desktopId] = pc
                    publish()
                }
                resolveSequencer.finish()?.let(::startResolve)
            }
        })
    }

    private fun shouldRetry(serviceInfo: NsdServiceInfo): Boolean {
        val name = serviceInfo.serviceName.orEmpty()
        return synchronized(retriedServiceNames) { retriedServiceNames.add(name) }
    }

    private fun publish() {
        _pcs.value = discovered.values.toList()
        _status.value = if (discovered.isEmpty()) PcDiscoveryStatus.Empty else PcDiscoveryStatus.Found
    }

    companion object {
        fun parse(serviceInfo: NsdServiceInfo): DiscoveredPc? {
            val attributes = serviceInfo.attributes.mapValues { (_, value) ->
                String(value, StandardCharsets.UTF_8)
            }
            val hostAddresses = if (Build.VERSION.SDK_INT >= 34) {
                serviceInfo.hostAddresses.mapNotNull { it.hostAddress }
            } else {
                emptyList()
            }.ifEmpty { listOf(serviceInfo.host?.hostAddress.orEmpty()) }
            return PcDiscoveryParser.parse(
                PcServiceRecord(
                    serviceName = serviceInfo.serviceName.orEmpty(),
                    attributes = attributes,
                    hostAddresses = hostAddresses,
                    port = serviceInfo.port
                )
            )
        }

        private fun isSwitchifyServiceType(serviceType: String?): Boolean {
            return serviceType == SWITCHIFY_SERVICE_TYPE ||
                    serviceType == "_switchify._tcp.local" ||
                    serviceType == "_switchify._tcp.local."
        }
    }
}
