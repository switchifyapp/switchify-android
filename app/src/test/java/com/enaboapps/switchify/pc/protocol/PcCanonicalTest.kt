package com.enaboapps.switchify.pc.protocol

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PcCanonicalTest {
    @Test
    fun matchesTheSharedCanonicalAuthenticationFixture() {
        assertEquals("{\"a\":true,\"z\":1}", PcCanonical.stableStringify(linkedMapOf("z" to 1, "a" to true)))
        assertEquals(
            "98bZHKWHa3ooOYZyXBuYpzOdbPWGW5FV04fEjxAl9sI",
            PcCanonical.authProof("req-1", "device-1", 1000L, "connection.ping", emptyMap(), TOKEN)
        )
    }

    @Test
    fun matchesTheDesktopAndroidSlashEscapingFixtures() {
        listOf(
            Triple("apostrophe-1", "'", "W0OLnbhllDOCd0Gf_00WLpHRvfidYjHeY69nbcmTFYA"),
            Triple("slash-1", "</", "70F2Z7SU6ur1gSYzR3Q9t1y5D02z_OuXfst15lQ_3yg"),
            Triple("plain-slash-1", "/", "ckTEIQrJqsQSd7zoFkIaMzs0wuGvsDeaZwGvFnnU50E")
        ).forEach { (id, text, proof) ->
            assertEquals(
                id,
                proof,
                PcCanonical.authProof(id, "android-1", PC_NOW, "keyboard.typeText", linkedMapOf("text" to text), TOKEN)
            )
        }
    }

    @Test
    fun escapesEverySlashInTheCanonicalPayload() {
        assertEquals("{\"text\":\"https:\\/\\/a\\/b\"}", PcCanonical.stableStringify(linkedMapOf("text" to "https://a/b")))
        assertEquals(
            "XwOd8hMkM8h-xc8m3GW_C8r0mPn0eeoOljur_7OkPSY",
            PcCanonical.authProof("text-url", "device-1", 1000L, "keyboard.typeText", linkedMapOf("text" to "https://a/b"), TOKEN)
        )
    }

    @Test
    fun sortsNestedKeysRecursivelyAndKeepsArrayOrder() {
        val payload = PcCommands.repeatStart(PcRepeatCommand.Move(64.0, -64.0)).payload
        assertEquals(
            "{\"command\":{\"payload\":{\"dx\":64,\"dy\":-64},\"type\":\"mouse.move\"}}",
            PcCanonical.stableStringify(payload)
        )
        assertEquals(
            "lzNAqUmrhxN0BPoiTYdsPHMLbDzJobX_KWK9m9DVovc",
            PcCanonical.authProof("repeat-1", "device-1", 1000L, "mouse.repeat.start", payload, TOKEN)
        )
        assertEquals(
            "dQ6U2TLm_Rdyf0chqx25fWIqFe6BFnN6vqIjY4oLros",
            PcCanonical.authProof("shortcut-1", "device-1", 1000L, "keyboard.shortcut", PcCommands.shortcut(listOf("Ctrl", "C")).payload, TOKEN)
        )
    }

    @Test
    fun includesTheNoAckResponseModeInTheProof() {
        assertEquals(
            "uMjPQZGMGXXbQuVdbAwz-KG3sHYyiyuTbl8TYx9jl0E",
            PcCanonical.authProof("move-1", "device-1", 1000L, "mouse.move", PcCommands.move(10.0, -5.0).payload, TOKEN, PcResponseMode.None)
        )
    }

    @Test
    fun serializesStringsAndNumbersLikeJavaScript() {
        assertEquals("\"a\\u0001\\n\\\"\\\\ \"", PcJson.stringify("a\u0001\n\"\\ "))
        assertEquals("\"\\b\\f\\r\\t\"", PcJson.stringify("\b\u000C\r\t"))
        assertEquals("\"😀\\ud83d\"", PcJson.stringify("😀\uD83D"))
        assertEquals("[1,1.5,0,0,125,null,false]", PcJson.stringify(listOf(1, 1.5, -0.0, 0L, 125.0, null, false)))
        assertEquals("\"a/b\"", PcJson.stringify("a/b"))
    }

    @Test
    fun authenticatedCommandsCarryTheProofButNeverTheToken() {
        val raw = PcMessages.authenticatedCommand("profile-1", "device-1", TOKEN, 1000L, PcCommands.pointerProfile())
        val json = JSONObject(raw)
        assertEquals(1, json.getInt("version"))
        assertEquals("profile-1", json.getString("id"))
        assertEquals("device-1", json.getString("deviceId"))
        assertEquals("pointer.profile", json.getString("type"))
        assertEquals("lgHsjgfWbqbrE-ugPTIDzjxm5MjEdNRpbHQV9B4ZNPM", json.getString("auth"))
        assertFalse(json.has("responseMode"))
        assertFalse(raw.contains(TOKEN))
    }

    private companion object {
        const val TOKEN = "shared-token"
        const val PC_NOW = 1_724_000_000_000L
    }
}
