package com.enaboapps.switchify.service.utils

import android.content.Context

data class ContinuousLineSpeedPreset(
    val displayName: String,
    val representativeLevel: Int,
    val minLegacyLevel: Int,
    val maxLegacyLevel: Int,
    val linearSpeedDpPerSecond: Float,
    val radarAngularSpeedDegreesPerSecond: Float
)

object ContinuousLineSpeedUtils {

    const val UPDATE_PERIOD_MS = 33L

    private val defaultPreset = ContinuousLineSpeedPreset(
        displayName = "Medium",
        representativeLevel = 13,
        minLegacyLevel = 11,
        maxLegacyLevel = 15,
        linearSpeedDpPerSecond = 120f,
        radarAngularSpeedDegreesPerSecond = 30f
    )

    private val presets = listOf(
        ContinuousLineSpeedPreset(
            displayName = "Very slow",
            representativeLevel = 3,
            minLegacyLevel = 1,
            maxLegacyLevel = 5,
            linearSpeedDpPerSecond = 45f,
            radarAngularSpeedDegreesPerSecond = 12f
        ),
        ContinuousLineSpeedPreset(
            displayName = "Slow",
            representativeLevel = 8,
            minLegacyLevel = 6,
            maxLegacyLevel = 10,
            linearSpeedDpPerSecond = 75f,
            radarAngularSpeedDegreesPerSecond = 18f
        ),
        defaultPreset,
        ContinuousLineSpeedPreset(
            displayName = "Fast",
            representativeLevel = 18,
            minLegacyLevel = 16,
            maxLegacyLevel = 20,
            linearSpeedDpPerSecond = 180f,
            radarAngularSpeedDegreesPerSecond = 45f
        ),
        ContinuousLineSpeedPreset(
            displayName = "Very fast",
            representativeLevel = 23,
            minLegacyLevel = 21,
            maxLegacyLevel = 25,
            linearSpeedDpPerSecond = 270f,
            radarAngularSpeedDegreesPerSecond = 60f
        )
    )

    fun getDefaultSpeedLevel(): Int = defaultPreset.representativeLevel

    fun getPresetForStoredLevel(level: Int): ContinuousLineSpeedPreset {
        return presets.firstOrNull { level in it.minLegacyLevel..it.maxLegacyLevel } ?: defaultPreset
    }

    fun getPresetOptions(): List<ContinuousLineSpeedPreset> = presets

    fun getDisplayName(level: Int): String = getPresetForStoredLevel(level).displayName

    fun getRepresentativeLevel(level: Int): Int = getPresetForStoredLevel(level).representativeLevel

    fun getLinearSpeedPxPerSecond(context: Context, level: Int): Float {
        return getPresetForStoredLevel(level).linearSpeedDpPerSecond * context.resources.displayMetrics.density
    }

    fun getRadarAngularSpeedDegreesPerSecond(level: Int): Float {
        return getPresetForStoredLevel(level).radarAngularSpeedDegreesPerSecond
    }

    fun getSpeedLevelDescription(speedLevel: Int): String = getDisplayName(speedLevel)

    fun isValidSpeedLevel(speedLevel: Int): Boolean = speedLevel in 1..25
}
