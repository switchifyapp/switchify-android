package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcDiscoveryParserTest {
    @Test
    fun acceptsValidSwitchifyPc() {
        val pc = PcDiscoveryParser.parse(validRecord())

        assertEquals("desktop-1", pc?.desktopId)
        assertEquals(listOf("192.168.1.20"), pc?.hostAddresses)
        assertEquals(listOf("ws://192.168.1.20:7347"), pc?.websocketUrls)
    }

    @Test
    fun rejectsInvalidTxtRecords() {
        assertNull(PcDiscoveryParser.parse(validRecord(attributes = validAttributes() + (PcTxtKeys.KIND to "other"))))
        assertNull(PcDiscoveryParser.parse(validRecord(attributes = validAttributes() + (PcTxtKeys.VERSION to "2"))))
        assertNull(PcDiscoveryParser.parse(validRecord(attributes = validAttributes() + (PcTxtKeys.PROTOCOL_VERSION to "2"))))
        assertNull(PcDiscoveryParser.parse(validRecord(attributes = validAttributes() + (PcTxtKeys.DESKTOP_ID to ""))))
    }

    @Test
    fun rejectsInvalidEndpoint() {
        assertNull(PcDiscoveryParser.parse(validRecord(hostAddresses = emptyList())))
        assertNull(PcDiscoveryParser.parse(validRecord(port = 0)))
    }

    private fun validRecord(
        attributes: Map<String, String> = validAttributes(),
        hostAddresses: List<String> = listOf("192.168.1.20"),
        port: Int = 7347
    ) = PcServiceRecord("Switchify PC", attributes, hostAddresses, port)

    private fun validAttributes() = mapOf(
        PcTxtKeys.KIND to "switchify.pc",
        PcTxtKeys.VERSION to "1",
        PcTxtKeys.DESKTOP_ID to "desktop-1",
        PcTxtKeys.PROTOCOL_VERSION to "1",
        PcTxtKeys.PAIRING to "approval"
    )
}
