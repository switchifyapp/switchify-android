package com.enaboapps.switchify.service.window.overlay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SurfaceControlOverlayBackendTest {
    @Test
    fun missingHostTokenRejectsSurfaceAttachment() {
        assertFalse(SurfaceControlOverlayBackend.hasRequiredHostToken(null))
    }

    @Test
    fun presentHostTokenAllowsSurfaceAttachmentPrerequisite() {
        val token = object : android.os.IBinder {
            override fun getInterfaceDescriptor(): String = "test"
            override fun pingBinder(): Boolean = true
            override fun isBinderAlive(): Boolean = true
            override fun queryLocalInterface(descriptor: String): android.os.IInterface? = null
            override fun dump(fd: java.io.FileDescriptor, args: Array<out String>?) = Unit
            override fun dumpAsync(fd: java.io.FileDescriptor, args: Array<out String>?) = Unit
            override fun transact(
                code: Int,
                data: android.os.Parcel,
                reply: android.os.Parcel?,
                flags: Int
            ): Boolean = false

            override fun linkToDeath(recipient: android.os.IBinder.DeathRecipient, flags: Int) = Unit
            override fun unlinkToDeath(
                recipient: android.os.IBinder.DeathRecipient,
                flags: Int
            ): Boolean = true
        }

        assertTrue(SurfaceControlOverlayBackend.hasRequiredHostToken(token))
    }
}
