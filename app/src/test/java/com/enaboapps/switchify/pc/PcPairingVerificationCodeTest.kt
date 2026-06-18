package com.enaboapps.switchify.pc

import org.junit.Assert.assertEquals
import org.junit.Test

class PcPairingVerificationCodeTest {
    @Test
    fun matchesDesktopFixtureForSimpleIds() {
        assertEquals(
            "215918",
            createPairingVerificationCode(
                desktopId = "desktop-1",
                deviceId = "device-1",
                requestNonce = "nonce-1"
            )
        )
    }

    @Test
    fun matchesDesktopFixtureForDisplayStyleDesktopId() {
        assertEquals(
            "735258",
            createPairingVerificationCode(
                desktopId = "0:0:1280:720:1.5",
                deviceId = "android-device-id",
                requestNonce = "random-request-nonce"
            )
        )
    }

    @Test
    fun matchesDesktopFixtureForShortValues() {
        assertEquals(
            "548354",
            createPairingVerificationCode(
                desktopId = "abc",
                deviceId = "def",
                requestNonce = "ghi"
            )
        )
    }

    @Test
    fun alwaysReturnsSixDigits() {
        val code = createPairingVerificationCode(
            desktopId = "desktop",
            deviceId = "device",
            requestNonce = "nonce"
        )

        assertEquals(PAIRING_VERIFICATION_CODE_LENGTH, code.length)
    }

    @Test
    fun padsLeadingZeros() {
        assertEquals(
            "028314",
            createPairingVerificationCode(
                desktopId = "desktop",
                deviceId = "device",
                requestNonce = "nonce-14"
            )
        )
    }
}
