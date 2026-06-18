package com.enaboapps.switchify.pc

import kotlinx.coroutines.flow.StateFlow

const val PC_PROTOCOL_VERSION = 1

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
