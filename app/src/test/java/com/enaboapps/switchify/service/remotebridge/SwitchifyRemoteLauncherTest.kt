package com.enaboapps.switchify.service.remotebridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SwitchifyRemoteLauncherTest {
    @Test fun remotePackageIsStable() {
        assertEquals("com.enaboapps.switchify.remote", SwitchifyRemoteLauncher.REMOTE_PACKAGE)
    }

    @Test fun preservedActionsRouteToPackagePinnedRemoteSurfaces() {
        assertEquals("switchify-remote://remote?surface=mouse", SwitchifyRemoteLauncher.remoteUri("mouse"))
        assertEquals("switchify-remote://remote?surface=forwarding", SwitchifyRemoteLauncher.remoteUri("forwarding"))
    }
}
