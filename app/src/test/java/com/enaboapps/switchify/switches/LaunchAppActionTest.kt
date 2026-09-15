package com.enaboapps.switchify.switches

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class LaunchAppActionTest {
    @Test fun pressAndHoldTargetsSurviveSerialization() {
        val event = SwitchEvent(name = "Apps", code = "24",
            pressAction = SwitchAction(19, "com.android.settings"),
            holdActions = listOf(SwitchAction(19, "com.android.chrome")))
        assertEquals(event, Gson().fromJson(Gson().toJson(event), SwitchEvent::class.java))
        assertEquals(event.pressAction, SwitchAction.fromMap(mapOf("id" to 19.0, "package_name" to "com.android.settings")))
        assertEquals("com.android.chrome", event.holdActions.single().toMap()["package_name"])
    }

    @Test fun legacyActionsRemainCompatibleAndLaunchIsAvailableInBothModes() {
        assertEquals(SwitchAction(1), Gson().fromJson("{\"id\":1}", SwitchAction::class.java))
        assertEquals(19, SwitchAction.ACTION_LAUNCH_APP)
        listOf("manual", "auto").forEach {
            assertTrue(SupportedActionsPolicy.supportedActionIdsForMode(it).contains(19))
        }
    }
}
