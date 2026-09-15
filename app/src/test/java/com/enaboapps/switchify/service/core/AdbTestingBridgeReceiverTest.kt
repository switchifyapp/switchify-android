package com.enaboapps.switchify.service.core

import org.junit.Assert.*
import org.junit.Test

class AdbTestingBridgeReceiverTest {
    @Test fun retiredLaunchCommandIsUnavailable() {
        assertNull(AdbTestingBridgeReceiver.actionNameToId["pc_switch_forwarding"])
        assertEquals(1, AdbTestingBridgeReceiver.actionNameToId["select"])
    }
}
