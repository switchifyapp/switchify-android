package com.enaboapps.switchify.service.gestures

import org.junit.Assert.assertEquals
import org.junit.Test

class GestureStateListenerRegistryTest {
    @Test
    fun distinctListenersReceiveTheSameEvent() {
        val registry = GestureStateListenerRegistry()
        val received = mutableListOf<String>()

        registry.add("first", listener { event, _ -> received.add("first:$event") })
        registry.add("second", listener { event, _ -> received.add("second:$event") })

        registry.notify("ended", emptyMap())

        assertEquals(setOf("first:ended", "second:ended"), received.toSet())
    }

    @Test
    fun sameIdReplacesPreviousListener() {
        val registry = GestureStateListenerRegistry()
        val received = mutableListOf<String>()

        registry.add("role", listener { _, _ -> received.add("old") })
        registry.add("role", listener { _, _ -> received.add("new") })

        registry.notify("ended", emptyMap())

        assertEquals(listOf("new"), received)
    }

    @Test
    fun removingOneIdPreservesOtherListeners() {
        val registry = GestureStateListenerRegistry()
        val received = mutableListOf<String>()

        registry.add("first", listener { _, _ -> received.add("first") })
        registry.add("second", listener { _, _ -> received.add("second") })
        registry.remove("first")

        registry.notify("ended", emptyMap())

        assertEquals(listOf("second"), received)
    }

    @Test
    fun failingListenerDoesNotBlockOtherListeners() {
        val registry = GestureStateListenerRegistry()
        val received = mutableListOf<String>()

        registry.add("failing", listener { _, _ -> error("failure") })
        registry.add("working", listener { _, _ -> received.add("working") })

        registry.notify("ended", emptyMap())

        assertEquals(listOf("working"), received)
    }

    private fun listener(
        onEvent: (String, Map<String, Any>) -> Unit
    ): GestureStateManager.GestureStateListener {
        return object : GestureStateManager.GestureStateListener {
            override fun onStateChanged(event: String, data: Map<String, Any>) {
                onEvent(event, data)
            }
        }
    }
}
