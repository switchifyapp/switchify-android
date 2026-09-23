package com.enaboapps.switchify.service.switches

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SwitchProfileMenuActionsTest {
    @Test
    fun menuCallbacksRunThroughUiDispatcher() = runTest {
        val calls = mutableListOf<String>()
        val actions = SwitchProfileMenuActions(
            dispatcher = StandardTestDispatcher(testScheduler),
            openConfirmationMenu = { calls += "open:$it" },
            dismissConfirmationMenu = { calls += "dismiss" },
            closeMenuHierarchy = { calls += "close" }
        )

        launch(start = CoroutineStart.UNDISPATCHED) {
            actions.open("Driving") { true }
            actions.dismiss()
            actions.closeAll()
        }

        assertTrue(calls.isEmpty())
        runCurrent()
        assertEquals(listOf("open:Driving", "dismiss", "close"), calls)
    }

    @Test
    fun cancelledActivationDoesNotOpenStaleMenu() = runTest {
        var current = true
        var opened = false
        var result: Boolean? = null
        val actions = SwitchProfileMenuActions(
            dispatcher = StandardTestDispatcher(testScheduler),
            openConfirmationMenu = { opened = true },
            dismissConfirmationMenu = {},
            closeMenuHierarchy = {}
        )

        launch(start = CoroutineStart.UNDISPATCHED) {
            result = actions.open("Driving") { current }
        }
        current = false
        runCurrent()

        assertFalse(opened)
        assertEquals(false, result)
    }
}
