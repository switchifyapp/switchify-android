package com.enaboapps.switchify.pc

object PcAddressSelector {
    fun orderAddresses(addresses: List<String>): List<String> {
        val distinct = addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val nonLoopback = distinct.filterNot(::isLoopback)
        val loopback = distinct.filter(::isLoopback)
        val candidates = nonLoopback.ifEmpty { loopback }
        return candidates.sortedWith(compareBy(::rank, { it }))
    }

    fun websocketUrls(addresses: List<String>, port: Int): List<String> {
        if (port <= 0) return emptyList()
        return orderAddresses(addresses).map { address ->
            val host = if (address.contains(":") && !address.startsWith("[")) "[$address]" else address
            "ws://$host:$port"
        }
    }

    private fun rank(address: String): Int {
        return when {
            address.startsWith("192.168.") -> 0
            address.startsWith("10.") -> 1
            is172Private(address) -> 2
            !isLoopback(address) -> 3
            else -> 4
        }
    }

    private fun is172Private(address: String): Boolean {
        val parts = address.split(".")
        if (parts.size < 2 || parts[0] != "172") return false
        val second = parts[1].toIntOrNull() ?: return false
        return second in 16..31
    }

    private fun isLoopback(address: String): Boolean {
        return address == "localhost" ||
                address.startsWith("127.") ||
                address == "::1" ||
                address == "0:0:0:0:0:0:0:1"
    }
}
