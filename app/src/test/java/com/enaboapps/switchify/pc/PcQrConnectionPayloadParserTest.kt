package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcQrConnectionPayloadParserTest {
    @Test
    fun parsesValidPayloadWithIpv4AndIpv6Urls() {
        val pc = PcQrConnectionPayloadParser.parse(validPayload()).getOrThrow()

        assertEquals("desktop-1", pc.desktopId)
        assertEquals("Switchify PC", pc.serviceName)
        assertEquals(
            listOf(
                "ws://[2001:bb6:a61:3700:574c:69d2:25ce:505]:7347",
                "ws://192.168.1.180:7347"
            ),
            pc.websocketUrls
        )
        assertEquals(
            listOf(
                "2001:bb6:a61:3700:574c:69d2:25ce:505",
                "192.168.1.180"
            ),
            pc.hostAddresses
        )
        assertEquals(7347, pc.port)
    }

    @Test
    fun preservesUrlOrder() {
        val pc = PcQrConnectionPayloadParser.parse(
            validPayload(
                urls = listOf(
                    "ws://192.168.1.180:7347",
                    "ws://[2001:bb6:a61:3700:574c:69d2:25ce:505]:7347"
                )
            )
        ).getOrThrow()

        assertEquals(
            listOf(
                "ws://192.168.1.180:7347",
                "ws://[2001:bb6:a61:3700:574c:69d2:25ce:505]:7347"
            ),
            pc.websocketUrls
        )
    }

    @Test
    fun deduplicatesDuplicateUrls() {
        val pc = PcQrConnectionPayloadParser.parse(
            validPayload(
                urls = listOf(
                    "ws://192.168.1.180:7347",
                    "ws://192.168.1.180:7347"
                )
            )
        ).getOrThrow()

        assertEquals(listOf("ws://192.168.1.180:7347"), pc.websocketUrls)
    }

    @Test
    fun usesDisplayNameAsServiceName() {
        val pc = PcQrConnectionPayloadParser.parse(validPayload(displayName = "Office PC")).getOrThrow()

        assertEquals("Office PC", pc.serviceName)
    }

    @Test
    fun fallsBackToSwitchifyPcWhenDisplayNameMissing() {
        val pc = PcQrConnectionPayloadParser.parse(validPayload(includeDisplayName = false)).getOrThrow()

        assertEquals("Switchify PC", pc.serviceName)
    }

    @Test
    fun fallsBackToSwitchifyPcWhenDisplayNameBlank() {
        val pc = PcQrConnectionPayloadParser.parse(validPayload(displayName = " ")).getOrThrow()

        assertEquals("Switchify PC", pc.serviceName)
    }

    @Test
    fun rejectsNonJsonInput() {
        assertInvalid("not json")
    }

    @Test
    fun rejectsWrongType() {
        assertInvalid(validPayload(type = "other"))
    }

    @Test
    fun rejectsUnsupportedVersion() {
        assertInvalid(validPayload(version = 2))
    }

    @Test
    fun rejectsMissingDesktopId() {
        assertInvalid(
            """
            {
              "type": "switchify.pc.connect",
              "version": 1,
              "urls": ["ws://192.168.1.180:7347"]
            }
            """.trimIndent()
        )
    }

    @Test
    fun rejectsBlankDesktopId() {
        assertInvalid(validPayload(desktopId = " "))
    }

    @Test
    fun rejectsEmptyUrls() {
        assertInvalid(validPayload(urls = emptyList()))
    }

    @Test
    fun rejectsHttpUrls() {
        assertInvalid(validPayload(urls = listOf("http://192.168.1.180:7347")))
    }

    @Test
    fun rejectsTopLevelToken() {
        assertInvalid(
            """
            {
              "type": "switchify.pc.connect",
              "version": 1,
              "desktopId": "desktop-1",
              "displayName": "Switchify PC",
              "urls": ["ws://192.168.1.180:7347"],
              "token": "secret"
            }
            """.trimIndent()
        )
    }

    @Test
    fun extractsIpv4HostAddress() {
        val pc = PcQrConnectionPayloadParser.parse(
            validPayload(urls = listOf("ws://192.168.1.180:7347"))
        ).getOrThrow()

        assertEquals(listOf("192.168.1.180"), pc.hostAddresses)
    }

    @Test
    fun extractsBracketedIpv6HostAddress() {
        val pc = PcQrConnectionPayloadParser.parse(
            validPayload(urls = listOf("ws://[2001:bb6:a61:3700:574c:69d2:25ce:505]:7347"))
        ).getOrThrow()

        assertEquals(listOf("2001:bb6:a61:3700:574c:69d2:25ce:505"), pc.hostAddresses)
    }

    @Test
    fun acceptsHostnameUrls() {
        val pc = PcQrConnectionPayloadParser.parse(
            validPayload(urls = listOf("ws://switchify-pc.local:7347"))
        ).getOrThrow()

        assertEquals(listOf("switchify-pc.local"), pc.hostAddresses)
    }

    private fun assertInvalid(payload: String) {
        val result = PcQrConnectionPayloadParser.parse(payload)

        assertTrue(result.isFailure)
        assertEquals(PcQrConnectionPayloadParser.INVALID_MESSAGE, result.exceptionOrNull()?.message)
    }

    private fun validPayload(
        type: String = "switchify.pc.connect",
        version: Int = 1,
        desktopId: String = "desktop-1",
        displayName: String = "Switchify PC",
        includeDisplayName: Boolean = true,
        urls: List<String> = listOf(
            "ws://[2001:bb6:a61:3700:574c:69d2:25ce:505]:7347",
            "ws://192.168.1.180:7347"
        )
    ): String {
        val displayNameLine = if (includeDisplayName) {
            """"displayName": "$displayName","""
        } else {
            ""
        }
        return """
            {
              "type": "$type",
              "version": $version,
              "desktopId": "$desktopId",
              $displayNameLine
              "urls": [${urls.joinToString(",") { "\"$it\"" }}]
            }
        """.trimIndent()
    }
}
