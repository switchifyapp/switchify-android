package com.enaboapps.switchify.tips

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TipStoreTest {

    private fun tip(id: String?, title: String = "Title $id", url: String? = null) =
        TipMessage(id = id, title = title, body = "Body of $title\nSecond line", url = url)

    @Test
    fun findsATipThatWasRemembered() {
        val linked = tip("1", url = "https://android.theswitchify.app/using/settings/#pause")
        val stored = TipStore.remember(TipStore.remember(null, linked), tip("2"))

        assertEquals(linked, TipStore.find(stored, TipStore.key(linked)))
        assertEquals(tip("2"), TipStore.find(stored, TipStore.key(tip("2"))))
    }

    @Test
    fun keysATipWithoutAnIdByItsTitle() {
        val untracked = tip(null, title = "No id")
        val stored = TipStore.remember(null, untracked)

        assertEquals(untracked, TipStore.find(stored, TipStore.key(untracked)))
    }

    @Test
    fun replacesATipRememberedTwice() {
        val first = tip("1")
        val second = first.copy(body = "Updated")
        val stored = TipStore.remember(TipStore.remember(null, first), second)

        assertEquals(second, TipStore.find(stored, TipStore.key(first)))
    }

    @Test
    fun keepsOnlyTheNewestTips() {
        var stored: String? = null
        repeat(TipStore.MAX_TIPS + 1) { stored = TipStore.remember(stored, tip("$it")) }

        assertNull(TipStore.find(stored, TipStore.key(tip("0"))))
        assertEquals(tip("1"), TipStore.find(stored, TipStore.key(tip("1"))))
        assertEquals(tip("${TipStore.MAX_TIPS}"), TipStore.find(stored, TipStore.key(tip("${TipStore.MAX_TIPS}"))))
    }

    @Test
    fun returnsNothingForAnUnknownKeyOrUnreadableStore() {
        assertNull(TipStore.find(null, "1"))
        assertNull(TipStore.find("not json", "1"))
        assertNull(TipStore.find(TipStore.remember(null, tip("1")), "12345"))
    }

    @Test
    fun dropsAStoredLinkThatIsNotASwitchifySite() {
        val key = TipStore.key(tip("1"))
        val stored = """[{"key":"$key","id":"1","title":"Title","body":"Body","url":"https://example.com/"}]"""

        assertEquals(TipMessage("1", "Title", "Body", null), TipStore.find(stored, key))
    }

    @Test
    fun acceptsOnlyNumericKeys() {
        assertTrue(TipStore.isKey(TipStore.key(tip("1"))))
        assertFalse(TipStore.isKey(null))
        assertFalse(TipStore.isKey("../Home"))
    }
}
