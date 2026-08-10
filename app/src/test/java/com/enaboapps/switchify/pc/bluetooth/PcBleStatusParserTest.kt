package com.enaboapps.switchify.pc.bluetooth

import com.enaboapps.switchify.pc.PcPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PcBleStatusParserTest {
    @Test
    fun parsesValidStatusJson() {
        val endpoint = PcBleStatusParser.parse(
            deviceAddress = "AA:BB:CC:DD:EE:FF",
            deviceName = "Switchify PC",
            rawStatus = """{"protocolVersion":1,"displayName":"Office PC","desktopId":"desktop-1"}"""
        )

        assertEquals("desktop-1", endpoint?.desktopId)
        assertEquals("Office PC", endpoint?.displayName)
        assertEquals("AA:BB:CC:DD:EE:FF", endpoint?.deviceAddress)
        assertNull(endpoint?.platform)
    }

    @Test
    fun parsesSupportedPlatforms() {
        val windows = PcBleStatusParser.parse(
            "AA",
            null,
            """{"protocolVersion":1,"desktopId":"desktop-1","platform":"windows"}"""
        )
        val macOS = PcBleStatusParser.parse(
            "BB",
            "Mac",
            """{"protocolVersion":1,"displayName":"Owen’s Mac Studio","desktopId":"desktop-2","platform":"macos"}"""
        )

        assertEquals(PcPlatform.Windows, windows?.platform)
        assertEquals(PcPlatform.MacOS, macOS?.platform)
        assertEquals("Owen’s Mac Studio", macOS?.displayName)
        assertEquals("Mac", macOS?.deviceName)
    }

    @Test
    fun ignoresUnknownPlatform() {
        val endpoint = PcBleStatusParser.parse(
            "AA",
            null,
            """{"protocolVersion":1,"desktopId":"desktop-1","platform":"future-os"}"""
        )

        assertEquals("desktop-1", endpoint?.desktopId)
        assertNull(endpoint?.platform)
    }

    @Test
    fun rejectsUnsupportedProtocolVersion() {
        assertNull(PcBleStatusParser.parse("AA", null, """{"protocolVersion":2,"desktopId":"desktop-1"}"""))
    }

    @Test
    fun rejectsMissingDesktopId() {
        assertNull(PcBleStatusParser.parse("AA", null, """{"protocolVersion":1}"""))
        assertNull(PcBleStatusParser.parse("AA", null, """{"protocolVersion":1,"desktopId":" "}"""))
    }

    @Test
    fun fallsBackToSwitchifyPcDisplayName() {
        assertEquals(
            "Switchify PC",
            PcBleStatusParser.parse("AA", null, """{"protocolVersion":1,"desktopId":"desktop-1"}""")?.displayName
        )
        assertEquals(
            "Switchify PC",
            PcBleStatusParser.parse("AA", null, """{"protocolVersion":1,"displayName":" ","desktopId":"desktop-1"}""")?.displayName
        )
    }
}
