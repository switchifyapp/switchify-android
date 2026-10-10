package com.enaboapps.switchify.service.remotebridge

enum class SwitchForwardingOwner {
    SwitchifyRemote,
    InApp
}

interface InAppSwitchForwardingListener {
    fun onSnapshot(captureAvailable: Boolean, externalSwitches: List<Pair<Int, String>>)

    fun onSwitchEdge(
        generation: Long,
        sequence: Long,
        keyCode: Int,
        down: Boolean,
        downTimeMs: Long,
        eventTimeMs: Long,
        cancelled: Boolean
    )

    fun onForwardingRevoked(generation: Long)
}
