package com.enaboapps.switchify.service.scanning.preferences

import com.enaboapps.switchify.backend.preferences.PreferenceManager

internal enum class ScanPreferenceEffect {
    REFRESH_APP_RULES,
    RELOAD_TECHNIQUE,
    RESET_SCAN_MODE,
    REFRESH_ITEM_STRUCTURE,
    REFRESH_ITEM_TIMING,
    REFRESH_POINT_STRUCTURE,
    REFRESH_POINT_TIMING,
    RESET_RADAR_ORIGIN,
    REFRESH_HIGHLIGHT,
    LIVE_READ
}

internal data class ScanPreferenceUpdatePlan(
    val effects: Set<ScanPreferenceEffect>
) {
    val isEmpty: Boolean
        get() = effects.isEmpty()

    fun contains(effect: ScanPreferenceEffect): Boolean = effect in effects
}

internal object ScanPreferencePolicy {
    private val effectsByKey = mapOf(
        PreferenceManager.PREFERENCE_KEY_APP_SCAN_TECHNIQUES to ScanPreferenceEffect.REFRESH_APP_RULES,
        PreferenceManager.PREFERENCE_KEY_ACCESS_TECHNIQUE to ScanPreferenceEffect.RELOAD_TECHNIQUE,
        PreferenceManager.PREFERENCE_KEY_SCAN_MODE to ScanPreferenceEffect.RESET_SCAN_MODE,
        PreferenceManager.PREFERENCE_KEY_ROW_COLUMN_SCAN to ScanPreferenceEffect.REFRESH_ITEM_STRUCTURE,
        PreferenceManager.PREFERENCE_KEY_GROUP_SCAN to ScanPreferenceEffect.REFRESH_ITEM_STRUCTURE,
        PreferenceManager.PREFERENCE_KEY_SCAN_RATE to ScanPreferenceEffect.REFRESH_ITEM_TIMING,
        PreferenceManager.PREFERENCE_KEY_CURSOR_MODE to ScanPreferenceEffect.REFRESH_POINT_STRUCTURE,
        PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_COUNT to ScanPreferenceEffect.REFRESH_POINT_STRUCTURE,
        PreferenceManager.PREFERENCE_KEY_CURSOR_BLOCK_SCAN_RATE to ScanPreferenceEffect.REFRESH_POINT_TIMING,
        PreferenceManager.PREFERENCE_KEY_RADAR_STARTING_POSITION to ScanPreferenceEffect.RESET_RADAR_ORIGIN,
        PreferenceManager.PREFERENCE_KEY_SCAN_CYCLES to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_POINT_SCAN_LINE_SPEED_LEVEL to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_RADAR_SPEED_LEVEL to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_RADAR_SLOW_DOWN_THEN_SELECT to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_AUTOMATICALLY_START_SCAN_AFTER_SELECTION to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_PAUSE_ON_FIRST_ITEM_DELAY to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_AUTO_SELECT to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_AUTO_SELECT_DELAY to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_DIRECTLY_SELECT_KEYBOARD_KEYS to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_ASSISTED_SELECTION to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_MOVE_REPEAT_DELAY to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_ITEM_SCAN_SPEECH to ScanPreferenceEffect.LIVE_READ,
        PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_TYPE to ScanPreferenceEffect.REFRESH_HIGHLIGHT,
        PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_MOVEMENT to ScanPreferenceEffect.REFRESH_HIGHLIGHT,
        PreferenceManager.PREFERENCE_KEY_SCAN_HIGHLIGHT_COUNTDOWN to ScanPreferenceEffect.REFRESH_HIGHLIGHT,
        PreferenceManager.PREFERENCE_KEY_SCAN_COLOR_SET to ScanPreferenceEffect.REFRESH_HIGHLIGHT
    )

    val supportedKeys: Set<String> = effectsByKey.keys

    fun effectFor(key: String): ScanPreferenceEffect? = effectsByKey[key]

    fun reduce(keys: Set<String>): ScanPreferenceUpdatePlan {
        val effects = keys.mapNotNull(::effectFor).toMutableSet()
        if (ScanPreferenceEffect.RESET_SCAN_MODE in effects) {
            effects.remove(ScanPreferenceEffect.REFRESH_ITEM_STRUCTURE)
            effects.remove(ScanPreferenceEffect.REFRESH_ITEM_TIMING)
            effects.remove(ScanPreferenceEffect.REFRESH_POINT_STRUCTURE)
            effects.remove(ScanPreferenceEffect.REFRESH_POINT_TIMING)
            effects.remove(ScanPreferenceEffect.RESET_RADAR_ORIGIN)
        } else {
            if (ScanPreferenceEffect.REFRESH_ITEM_STRUCTURE in effects) {
                effects.remove(ScanPreferenceEffect.REFRESH_ITEM_TIMING)
            }
            if (ScanPreferenceEffect.REFRESH_POINT_STRUCTURE in effects) {
                effects.remove(ScanPreferenceEffect.REFRESH_POINT_TIMING)
            }
        }
        return ScanPreferenceUpdatePlan(effects)
    }
}
