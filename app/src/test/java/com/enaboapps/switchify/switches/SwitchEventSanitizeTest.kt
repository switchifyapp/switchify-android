package com.enaboapps.switchify.switches

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SwitchEventSanitizeTest {
    private val gson = Gson()

    @Test
    fun legacyJsonWithoutHoldActionsOrTypeGetsDefaults() {
        val json = """{"name":"Enter","code":"66","press_action":{"id":1}}"""

        val event = gson.fromJson(json, SwitchEvent::class.java).sanitized()

        assertNotNull(event)
        assertEquals(SWITCH_EVENT_TYPE_EXTERNAL, event!!.type)
        assertEquals(emptyList<SwitchAction>(), event.holdActions)
        assertEquals(1, event.pressAction.id)
    }

    @Test
    fun completeJsonIsUnchanged() {
        val json =
            """{"type":"camera","name":"Smile","code":"smile","press_action":{"id":1},"hold_actions":[{"id":4}]}"""

        val event = gson.fromJson(json, SwitchEvent::class.java).sanitized()

        assertEquals(
            SwitchEvent("camera", "Smile", "smile", SwitchAction(1), listOf(SwitchAction(4))),
            event
        )
    }

    @Test
    fun legacyArrayParsesAsListAndSanitizes() {
        val json = """[{"name":"Enter","code":"66","press_action":{"id":1}},{"name":"Space","code":"62","press_action":{"id":4}}]"""
        val type = object : TypeToken<List<SwitchEvent>>() {}.type

        val events: List<SwitchEvent?> = gson.fromJson(json, type)
        val sanitized = events.filterNotNull().mapNotNull { it.sanitized() }.distinct()

        assertEquals(listOf("66", "62"), sanitized.map { it.code })
        assertEquals(listOf(SWITCH_EVENT_TYPE_EXTERNAL, SWITCH_EVENT_TYPE_EXTERNAL), sanitized.map { it.type })
    }

    @Test
    fun eventWithoutPressActionIsDropped() {
        val json = """{"name":"Broken","code":"66"}"""

        assertNull(gson.fromJson(json, SwitchEvent::class.java).sanitized())
    }
}
