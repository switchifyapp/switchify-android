package com.enaboapps.switchify.service.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousLineSpeedUtilsTest {

    @Test
    fun `legacy levels map to expected presets`() {
        (1..5).forEach { level ->
            assertEquals("Very slow", ContinuousLineSpeedUtils.getDisplayName(level))
        }
        (6..10).forEach { level ->
            assertEquals("Slow", ContinuousLineSpeedUtils.getDisplayName(level))
        }
        (11..15).forEach { level ->
            assertEquals("Medium", ContinuousLineSpeedUtils.getDisplayName(level))
        }
        (16..20).forEach { level ->
            assertEquals("Fast", ContinuousLineSpeedUtils.getDisplayName(level))
        }
        (21..25).forEach { level ->
            assertEquals("Very fast", ContinuousLineSpeedUtils.getDisplayName(level))
        }
    }

    @Test
    fun `invalid levels map to medium`() {
        assertEquals("Medium", ContinuousLineSpeedUtils.getDisplayName(0))
        assertEquals("Medium", ContinuousLineSpeedUtils.getDisplayName(26))
    }

    @Test
    fun `default level is medium representative`() {
        assertEquals(13, ContinuousLineSpeedUtils.getDefaultSpeedLevel())
    }

    @Test
    fun `representative levels are stable`() {
        val representativeLevels = ContinuousLineSpeedUtils.getPresetOptions()
            .map { it.representativeLevel }

        assertEquals(listOf(3, 8, 13, 18, 23), representativeLevels)
    }

    @Test
    fun `linear speed increases by preset`() {
        val speeds = ContinuousLineSpeedUtils.getPresetOptions()
            .map { it.linearSpeedDpPerSecond }

        speeds.zipWithNext().forEach { (previous, next) ->
            assertTrue(next > previous)
        }
    }

    @Test
    fun `radar angular speed increases by preset`() {
        val speeds = ContinuousLineSpeedUtils.getPresetOptions()
            .map { it.radarAngularSpeedDegreesPerSecond }

        speeds.zipWithNext().forEach { (previous, next) ->
            assertTrue(next > previous)
        }
    }
}
