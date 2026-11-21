package com.enaboapps.switchify.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TipMessageTest {

    @Test
    fun parsesACompleteTip() {
        val tip = TipMessage.from(
            mapOf(
                "tip_id" to "42",
                "title" to " Try the radar ",
                "body" to "Radar scanning reaches any point on screen.",
                "url" to "https://android.theswitchify.app/using/scanning/"
            )
        )

        assertEquals(
            TipMessage(
                id = "42",
                title = "Try the radar",
                body = "Radar scanning reaches any point on screen.",
                url = "https://android.theswitchify.app/using/scanning/"
            ),
            tip
        )
    }

    @Test
    fun rejectsATipWithoutATitleOrBody() {
        assertNull(TipMessage.from(mapOf("body" to "Only a body")))
        assertNull(TipMessage.from(mapOf("title" to "Only a title")))
        assertNull(TipMessage.from(mapOf("title" to "  ", "body" to "Blank title")))
    }

    @Test
    fun dropsLinksThatAreNotHttps() {
        listOf(
            "http://android.theswitchify.app",
            "intent://scan/#Intent;scheme=zxing;end",
            "file:///sdcard/secret",
            "switchify://pc-control",
            "https://",
            "not a url"
        ).forEach { url ->
            val tip = TipMessage.from(mapOf("title" to "Title", "body" to "Body", "url" to url))
            assertNull("Expected $url to be dropped", tip?.url)
            assertEquals("Title", tip?.title)
        }
    }

    @Test
    fun treatsABlankIdAsMissing() {
        val tip = TipMessage.from(mapOf("tip_id" to " ", "title" to "Title", "body" to "Body"))

        assertNull(tip?.id)
    }
}
