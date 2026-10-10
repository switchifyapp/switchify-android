package com.enaboapps.switchify.pc.storage

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DeviceProtectedPcKeyValueStoreTest {
    private val directory: File = Files.createTempDirectory("pc-store").toFile()
    private val store = DeviceProtectedPcKeyValueStore(directory, "pairings.json")

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun createsTheFileOnFirstWrite() = runTest {
        store.put("a", "1")
        assertEquals("1", store.get("a"))
    }

    @Test
    fun anUnreadableFileFailsReadsButNotRemovals() = runTest {
        val file = File(directory, "pairings.json")
        file.writeText("not json")
        try {
            store.get("a")
            fail("Expected an unreadable file to fail reads.")
        } catch (_: Exception) {
        }
        store.remove("a")
        assertEquals("not json", file.readText())
    }
}
