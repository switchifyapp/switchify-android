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
    fun keepsHttpsLinksToSwitchifySites() {
        listOf(
            "https://theswitchify.app",
            "https://www.theswitchify.app/privacy",
            "https://ANDROID.theswitchify.app/using/scanning/#radar",
            "https://android.theswitchify.app/pc-control/pairing/?from=tip&x=1#step-2",
            "https://a-b.c1.theswitchify.app/a_b/c.d~e/%20f+g"
        ).forEach { url ->
            val tip = TipMessage.from(mapOf("title" to "Title", "body" to "Body", "url" to url))
            assertEquals(url, tip?.url)
        }
    }

    @Test
    fun dropsLinksThatAreNotHttpsOrNotSwitchifySites() {
        listOf(
            "http://android.theswitchify.app",
            "https://example.com/",
            "https://eviltheswitchify.app/",
            "https://theswitchify.app.example.com/",
            "https://theswitchify.app@example.com/",
            "https://example.com/?next=theswitchify.app",
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

    @Test
    fun remembersEachTipOnceAndForgetsTheOldest() {
        assertEquals(listOf("a", "b"), TipPreferences.rememberShown(listOf("a"), "b"))
        assertNull(TipPreferences.rememberShown(listOf("a", "b"), "a"))

        val full = (1..50).map { it.toString() }
        val updated = TipPreferences.rememberShown(full, "51")

        assertEquals(50, updated?.size)
        assertEquals("2", updated?.first())
        assertEquals("51", updated?.last())
    }
}
