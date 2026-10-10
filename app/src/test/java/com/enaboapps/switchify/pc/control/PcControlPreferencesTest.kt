package com.enaboapps.switchify.pc.control

import com.enaboapps.switchify.pc.connection.PcRemoteName
import com.enaboapps.switchify.pc.connection.PcRemoteNameError
import com.enaboapps.switchify.pc.connection.PcRemoteNameStore
import com.enaboapps.switchify.pc.remote.PcRemoteSurface
import com.enaboapps.switchify.pc.remote.PcTypingMode
import com.enaboapps.switchify.pc.remote.PersistedPcRemotePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PcControlPreferencesTest {
    @Test
    fun surfaceAndTypingModePersistWithRemoteCompatibleKeys() {
        val storage = InMemoryPcPreferenceStorage()
        val preferences = PersistedPcRemotePreferences(storage)
        assertEquals(PcRemoteSurface.Mouse, preferences.surface.value)
        assertEquals(PcTypingMode.Live, preferences.typingMode.value)

        preferences.setSurface(PcRemoteSurface.Forwarding)
        preferences.setTypingMode(PcTypingMode.Draft)
        assertEquals("forwarding", storage.values[PersistedPcRemotePreferences.SURFACE_KEY])
        assertEquals("draft", storage.values[PersistedPcRemotePreferences.TYPING_MODE_KEY])

        val reloaded = PersistedPcRemotePreferences(storage)
        assertEquals(PcRemoteSurface.Forwarding, reloaded.surface.value)
        assertEquals(PcTypingMode.Draft, reloaded.typingMode.value)
    }

    @Test
    fun unknownStoredValuesFallBackToDefaults() {
        val storage = InMemoryPcPreferenceStorage()
        storage.values[PersistedPcRemotePreferences.SURFACE_KEY] = "keyboard"
        storage.values[PersistedPcRemotePreferences.TYPING_MODE_KEY] = "stream"
        val preferences = PersistedPcRemotePreferences(storage)
        assertEquals(PcRemoteSurface.Mouse, preferences.surface.value)
        assertEquals(PcTypingMode.Live, preferences.typingMode.value)
    }

    @Test
    fun onlyForwardingSkipsTheLayoutEditor() {
        assertEquals(listOf(PcRemoteSurface.Mouse, PcRemoteSurface.Typing, PcRemoteSurface.Window), PcRemoteSurface.entries.filter { it.usesLayouts })
    }

    @Test
    fun remoteNameDefaultsToTheDeviceModelAndPersistsAnEdit() {
        val storage = InMemoryPcPreferenceStorage()
        val names = PcRemoteNameStore(storage, "Pixel 9")
        assertNull(names.savedName())
        assertEquals("Pixel 9", names.resolvedName())

        names.save("  Owen's phone  ")
        assertEquals("Owen's phone", storage.values[PcRemoteNameStore.KEY])
        assertEquals("Owen's phone", PcRemoteNameStore(storage, "Pixel 9").resolvedName())

        names.save(null)
        assertNull(storage.values[PcRemoteNameStore.KEY])
        assertEquals("Pixel 9", names.resolvedName())
        assertEquals(PcRemoteName.FALLBACK, PcRemoteNameStore(storage, " ").resolvedName())
    }

    @Test
    fun invalidRemoteNamesAreRejectedAndNeverStored() {
        val storage = InMemoryPcPreferenceStorage()
        val names = PcRemoteNameStore(storage, "Pixel 9")
        listOf("", "   ", "line\nbreak", "x".repeat(PcRemoteName.MAX_CHARACTERS + 1)).forEach { invalid ->
            try {
                names.save(invalid)
                fail("Expected $invalid to be rejected")
            } catch (_: IllegalArgumentException) {
            }
        }
        assertTrue(storage.values.isEmpty())
        storage.values[PcRemoteNameStore.KEY] = "bad\u0007name"
        assertNull(names.savedName())
        assertEquals("Pixel 9", names.resolvedName())
    }

    @Test
    fun remoteNameErrorsMatchRemoteValidation() {
        assertEquals(PcRemoteNameError.Empty, PcRemoteName.error(" "))
        assertEquals(PcRemoteNameError.ControlCharacters, PcRemoteName.error("a\tb"))
        assertEquals(PcRemoteNameError.TooLong, PcRemoteName.error("😀".repeat(41)))
        assertNull(PcRemoteName.error("😀".repeat(40)))
        assertFalse(PcRemoteName.validate(" ok ") == null)
    }
}
