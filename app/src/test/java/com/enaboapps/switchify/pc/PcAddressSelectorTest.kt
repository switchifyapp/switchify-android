package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Test

class PcAddressSelectorTest {
    @Test
    fun ordersPrivateLanAddressesBeforeOtherAddresses() {
        val ordered = PcAddressSelector.orderAddresses(
            listOf("8.8.8.8", "172.20.1.5", "10.0.0.5", "192.168.1.20")
        )

        assertEquals(listOf("192.168.1.20", "10.0.0.5", "172.20.1.5", "8.8.8.8"), ordered)
    }

    @Test
    fun loopbackIsOnlyUsedWhenNoOtherAddressExists() {
        val ordered = PcAddressSelector.orderAddresses(listOf("127.0.0.1", "192.168.1.20"))

        assertEquals(listOf("192.168.1.20"), ordered)
    }

    @Test
    fun loopbackIsReturnedWhenItIsTheOnlyAddress() {
        val ordered = PcAddressSelector.orderAddresses(listOf("127.0.0.1"))

        assertEquals(listOf("127.0.0.1"), ordered)
    }

    @Test
    fun formatsWebSocketUrls() {
        val urls = PcAddressSelector.websocketUrls(listOf("10.0.0.5"), 7347)

        assertEquals(listOf("ws://10.0.0.5:7347"), urls)
    }
}
