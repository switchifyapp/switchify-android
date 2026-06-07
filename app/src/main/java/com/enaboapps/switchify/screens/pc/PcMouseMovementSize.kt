package com.enaboapps.switchify.screens.pc

import android.content.Context
import androidx.annotation.StringRes
import com.enaboapps.switchify.R
import com.enaboapps.switchify.backend.preferences.PreferenceManager
import com.enaboapps.switchify.pc.PcPointerMovementProfile

enum class PcMouseMovementSize(
    val preferenceValue: String,
    @param:StringRes val labelResId: Int
) {
    Small("small", R.string.pc_mouse_movement_small),
    Medium("medium", R.string.pc_mouse_movement_medium),
    Large("large", R.string.pc_mouse_movement_large);

    companion object {
        fun fromPreferenceValue(value: String): PcMouseMovementSize {
            return entries.firstOrNull { it.preferenceValue == value } ?: Small
        }
    }
}

data class PcMouseMovementSteps(
    val small: Int,
    val medium: Int,
    val large: Int
) {
    fun stepFor(size: PcMouseMovementSize): Int {
        return when (size) {
            PcMouseMovementSize.Small -> small
            PcMouseMovementSize.Medium -> medium
            PcMouseMovementSize.Large -> large
        }
    }
}

interface PcMouseMovementSizeStore {
    fun getSelectedSize(): PcMouseMovementSize
    fun setSelectedSize(size: PcMouseMovementSize)
}

class PcMouseMovementPreferenceStore(context: Context) : PcMouseMovementSizeStore {
    private val preferenceManager = PreferenceManager(context.applicationContext)

    override fun getSelectedSize(): PcMouseMovementSize {
        return PcMouseMovementSize.fromPreferenceValue(
            preferenceManager.getStringValue(PreferenceManager.PREFERENCE_KEY_PC_MOUSE_MOVEMENT_SIZE)
        )
    }

    override fun setSelectedSize(size: PcMouseMovementSize) {
        preferenceManager.setStringValue(
            PreferenceManager.PREFERENCE_KEY_PC_MOUSE_MOVEMENT_SIZE,
            size.preferenceValue
        )
    }
}

fun PcPointerMovementProfile.toMouseMovementSteps(): PcMouseMovementSteps {
    return PcMouseMovementSteps(
        small = recommendedDeltas.small.coerceIn(1, maxDelta),
        medium = recommendedDeltas.medium.coerceIn(1, maxDelta),
        large = recommendedDeltas.large.coerceIn(1, maxDelta)
    )
}
