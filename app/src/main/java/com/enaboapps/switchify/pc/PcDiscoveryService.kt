package com.enaboapps.switchify.pc

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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

class PcDiscoveryService(context: Context) : PcDiscovery {
    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val discovered = linkedMapOf<String, DiscoveredPc>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _pcs = MutableStateFlow<List<DiscoveredPc>>(emptyList())
    override val pcs: StateFlow<List<DiscoveredPc>> = _pcs

    private val _status = MutableStateFlow(PcDiscoveryStatus.Idle)
    override val status: StateFlow<PcDiscoveryStatus> = _status

    override fun startDiscovery() {
        stopDiscovery()
        discovered.clear()
        _pcs.value = emptyList()
        _status.value = PcDiscoveryStatus.Searching

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                _status.value = PcDiscoveryStatus.Searching
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SWITCHIFY_SERVICE_TYPE) return
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
        nsdManager.discoverServices(SWITCHIFY_SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    override fun stopDiscovery() {
        val listener = discoveryListener ?: return
        runCatching { nsdManager.stopServiceDiscovery(listener) }
        discoveryListener = null
    }

    private fun resolve(serviceInfo: NsdServiceInfo) {
        nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit

            override fun onServiceResolved(resolvedService: NsdServiceInfo) {
                val pc = parse(resolvedService) ?: return
                discovered[pc.desktopId] = pc
                publish()
            }
        })
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
            val hostAddress = serviceInfo.host?.hostAddress.orEmpty()
            return PcDiscoveryParser.parse(
                PcServiceRecord(
                    serviceName = serviceInfo.serviceName.orEmpty(),
                    attributes = attributes,
                    hostAddresses = listOf(hostAddress),
                    port = serviceInfo.port
                )
            )
        }
    }
}
